import React, { useEffect, useMemo, useState } from 'react';
import { Button, Drawer, Popconfirm, Select, Space, Table, Tag, Upload, message } from 'antd';
import type { UploadProps } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import * as adminApi from '@/api/admin';
import type { KnowledgeFileVO, KnowledgeChunkVO } from '@/api/admin';
import * as catalogApi from '@/api/catalog';
import { getCinemaIdFromAccessToken, useAuthStore } from '@/stores/auth';
import type { CinemaVO } from '@/types';
import { formatDateTime } from '@/utils/format';

const { Dragger } = Upload;

/** admin 在 Select 中选择"系统知识库"时用的占位值，不映射到任何真实 cinemaId。 */
const SYSTEM_SCOPE = '__system__';

/**
 * 知识库管理：
 * - admin：默认管理系统知识库；可通过影院选择器切换到任意影院知识库（读/删），
 *   上传仍只能系统级
 * - staff：锁定本院知识库，不可切换
 */
const KnowledgePage: React.FC = () => {
  const user = useAuthStore((s) => s.user);
  const isAdmin = user?.role === 'admin';
  const staffCinemaId = user?.role === 'staff' ? user.cinemaId || getCinemaIdFromAccessToken() : undefined;

  const [cinemas, setCinemas] = useState<CinemaVO[]>([]);
  const [selectedScope, setSelectedScope] = useState<string>(staffCinemaId || SYSTEM_SCOPE);
  const [files, setFiles] = useState<KnowledgeFileVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [chunksDrawer, setChunksDrawer] = useState<{
    open: boolean;
    filename: string;
    chunks: KnowledgeChunkVO[];
    loading: boolean;
  }>({ open: false, filename: '', chunks: [], loading: false });

  const cinemaNameById = useMemo(() => {
    const map: Record<string, string> = {};
    cinemas.forEach((c) => { map[c.cinemaId] = c.name; });
    return map;
  }, [cinemas]);

  /** 当前选中的 cinemaId：系统库时为 undefined，选中影院时为该影院 ID。 */
  const activeCinemaId = selectedScope === SYSTEM_SCOPE ? undefined : selectedScope || undefined;

  const load = async () => {
    setLoading(true);
    try {
      const list = await adminApi.listKnowledgeFiles(activeCinemaId);
      setFiles(list || []);
    } catch {
      // 请求层已统一提示，保留空列表占位
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void catalogApi
      .listCinemas({ sort: 'price', page: 1, size: 50 })
      .then((r) => {
        const items = staffCinemaId
          ? r.items.filter((c) => c.cinemaId === staffCinemaId)
          : r.items;
        setCinemas(items);
        if (staffCinemaId) setSelectedScope(staffCinemaId);
        else if (!selectedScope) setSelectedScope(SYSTEM_SCOPE);
      })
      .catch(() => setCinemas([]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [staffCinemaId]);

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedScope]);

  const beforeUpload: UploadProps['beforeUpload'] = async (file) => {
    setUploading(true);
    try {
      await adminApi.uploadKnowledgeFile(file, activeCinemaId);
      message.success('知识库文件上传成功');
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '上传失败');
    } finally {
      setUploading(false);
    }
    return false;
  };

  const onDelete = async (filename: string) => {
    try {
      await adminApi.deleteKnowledgeFile(filename, activeCinemaId);
      message.success(`已删除 ${filename}`);
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '删除失败');
    }
  };

  const onViewChunks = async (filename: string) => {
    setChunksDrawer({ open: true, filename, chunks: [], loading: true });
    try {
      const list = await adminApi.getFileChunks(filename, activeCinemaId);
      setChunksDrawer({ open: true, filename, chunks: list || [], loading: false });
    } catch {
      setChunksDrawer({ open: true, filename, chunks: [], loading: false });
      message.error('加载切块失败');
    }
  };

  const scopeOptions = useMemo(() => {
    const opts = cinemas.map((c) => ({
      value: c.cinemaId,
      label: `${c.name}（${c.cinemaId}）`,
    }));
    if (!staffCinemaId) {
      opts.unshift({ value: SYSTEM_SCOPE, label: '系统知识库' });
    }
    return opts;
  }, [cinemas, staffCinemaId]);

  const scopeLabel = useMemo(() => {
    if (selectedScope === SYSTEM_SCOPE) {
      return '所有用户对话时均会检索系统知识库。';
    }
    const name = cinemaNameById[selectedScope] || selectedScope;
    if (isAdmin) {
      return `查看影院「${name}」的知识库。管理员只读/删，**不可向影院知识库上传文档**。`;
    }
    return `仅可管理本影院（${name}）的知识；涉及本影院的问答会检索此知识库。`;
  }, [selectedScope, cinemaNameById, isAdmin]);

  const columns = [
    {
      title: '文件名',
      dataIndex: 'filename',
      key: 'filename',
      render: (v: string) => <code>{v}</code>,
    },
    {
      title: '分块数',
      dataIndex: 'chunkCount',
      key: 'chunkCount',
      width: 100,
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 220,
      render: (v?: string) => formatDateTime(v, true) || '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_: unknown, record: KnowledgeFileVO) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => onViewChunks(record.filename)}>
            查看分块
          </Button>
          <Popconfirm
            title={`确认删除「${record.filename}」？`}
            description="将同时删除该文档的向量与磁盘原文，不可恢复。"
            onConfirm={() => onDelete(record.filename)}
            okText="删除"
            cancelText="取消"
          >
            <Button type="link" danger size="small">
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 18 }} wrap>
        <Select
          placeholder="选择知识库"
          style={{ width: 280 }}
          value={selectedScope || undefined}
          disabled={!!staffCinemaId}
          options={scopeOptions}
          onChange={setSelectedScope}
          showSearch
          optionFilterProp="label"
        />
      </Space>

      <div style={{ marginBottom: 18 }}>
        <h2 style={{ margin: 0, color: 'var(--color-text-primary)' }}>
          {selectedScope === SYSTEM_SCOPE ? '系统知识库' : '影院知识库'}
        </h2>
        <p style={{ margin: '8px 0 0', color: '#77808d', fontSize: 13 }}>{scopeLabel}</p>
      </div>

      <Dragger
        accept=".md,.markdown"
        maxCount={1}
        showUploadList={false}
        disabled={uploading || (isAdmin && selectedScope !== SYSTEM_SCOPE)}
        beforeUpload={beforeUpload}
        style={{ marginBottom: 20 }}
      >
        <p className="ant-upload-drag-icon">
          <InboxOutlined />
        </p>
        <p className="ant-upload-text">点击或拖拽 Markdown 文档到此处上传</p>
        <p className="ant-upload-hint">
          {selectedScope !== SYSTEM_SCOPE
            ? '影院知识库仅限对应 staff 账号上传，管理员只读。'
            : '支持 .md / .markdown，单文件 ≤ 1MB，UTF-8 编码；建议用 `##` 二级标题分章节，切块更聚焦。'}
        </p>
      </Dragger>

      <Table
        rowKey="filename"
        dataSource={files}
        columns={columns}
        loading={loading}
        pagination={false}
        locale={{ emptyText: '暂无知识库文档，上传后此处会展示分块情况' }}
      />

      <Drawer
        open={chunksDrawer.open}
        onClose={() => setChunksDrawer((p) => ({ ...p, open: false }))}
        title={`切块明细 · ${chunksDrawer.filename}`}
        width={640}
        loading={chunksDrawer.loading}
      >
        {chunksDrawer.chunks.length === 0 && !chunksDrawer.loading ? (
          <p style={{ color: '#999' }}>该文档无切块数据。</p>
        ) : (
          chunksDrawer.chunks.map((c) => (
            <div
              key={c.id}
              style={{
                marginBottom: 16,
                padding: 12,
                background: 'var(--color-bg-elevated, #fafafa)',
                borderRadius: 6,
                border: '1px solid var(--color-border, #eee)',
              }}
            >
              <div style={{ marginBottom: 6, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                <Tag color="blue">块 {c.chunkIndex}</Tag>
                <Tag>{c.charCount} 字</Tag>
                {c.section ? <Tag color="green">{c.section}</Tag> : null}
              </div>
              <pre
                style={{
                  margin: 0,
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                  fontSize: 13,
                  lineHeight: 1.6,
                  color: 'var(--color-text, #333)',
                }}
              >
                {c.text}
              </pre>
            </div>
          ))
        )}
      </Drawer>
    </div>
  );
};

export default KnowledgePage;
