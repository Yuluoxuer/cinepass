import React, { useEffect, useState } from 'react';
import { Button, Popconfirm, Table, Upload, message } from 'antd';
import type { UploadProps } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import * as adminApi from '@/api/admin';
import type { KnowledgeFileVO } from '@/api/admin';
import * as catalogApi from '@/api/catalog';
import { getCinemaIdFromAccessToken, useAuthStore } from '@/stores/auth';
import { formatDateTime } from '@/utils/format';

const { Dragger } = Upload;

/**
 * 知识库管理：admin 只管理系统知识库，staff 只管理自己影院的知识库。
 * 后端按 JWT 角色/影院归属解析作用域并强制权限，本页仅做展示与操作封装。
 */
const KnowledgePage: React.FC = () => {
  const user = useAuthStore((s) => s.user);
  const [files, setFiles] = useState<KnowledgeFileVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [cinemaName, setCinemaName] = useState<string | null>(null);

  const isAdmin = user?.role === 'admin';
  const cinemaId = user?.cinemaId || getCinemaIdFromAccessToken();

  const load = async () => {
    setLoading(true);
    try {
      const list = await adminApi.listKnowledgeFiles();
      setFiles(list || []);
    } catch {
      // 请求层已统一提示，这里保留空列表占位
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
    if (!isAdmin && cinemaId) {
      catalogApi
        .getCinema(cinemaId)
        .then((c) => setCinemaName(c?.name ?? null))
        .catch(() => setCinemaName(null));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAdmin, cinemaId]);

  const beforeUpload: UploadProps['beforeUpload'] = async (file) => {
    setUploading(true);
    try {
      await adminApi.uploadKnowledgeFile(file);
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
      await adminApi.deleteKnowledgeFile(filename);
      message.success(`已删除 ${filename}`);
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '删除失败');
    }
  };

  const title = isAdmin ? '系统知识库' : '影院知识库';
  const scopeLabel = isAdmin
    ? '仅可新增系统级知识（如购票流程、平台规则）；所有用户对话时均会检索系统知识库。'
    : `仅可新增本影院（${cinemaName || cinemaId || '本院'}）的知识，如影院位置、活动信息；涉及本影院的问答会检索此知识库。`;

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
      width: 120,
      render: (_: unknown, record: KnowledgeFileVO) => (
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
      ),
    },
  ];

  return (
    <div>
      <div style={{ marginBottom: 18 }}>
        <h2 style={{ margin: 0, color: 'var(--color-text-primary)' }}>{title}</h2>
        <p style={{ margin: '8px 0 0', color: '#77808d', fontSize: 13 }}>{scopeLabel}</p>
      </div>

      <Dragger
        accept=".md,.markdown"
        maxCount={1}
        showUploadList={false}
        disabled={uploading}
        beforeUpload={beforeUpload}
        style={{ marginBottom: 20 }}
      >
        <p className="ant-upload-drag-icon">
          <InboxOutlined />
        </p>
        <p className="ant-upload-text">点击或拖拽 Markdown 文档到此处上传</p>
        <p className="ant-upload-hint">
          支持 .md / .markdown，单文件 ≤ 1MB，UTF-8 编码；建议用 `##` 二级标题分章节，切块更聚焦。
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
    </div>
  );
};

export default KnowledgePage;
