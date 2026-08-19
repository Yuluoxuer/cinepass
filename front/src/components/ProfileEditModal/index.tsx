import React, { useEffect, useState } from 'react';
import { Form, Modal, Radio, Select, message } from 'antd';
import { useRequest } from 'ahooks';
import { union } from 'lodash';
import { getProfile, updateProfile } from '@/api/me';
import { listGenres } from '@/api/catalog';
import { PREFER_ROW_OPTIONS, PREFER_SIDE_OPTIONS } from '@/constants/preferences';
import type { ProfileEditForm, ProfileEditModalProps } from './interface';
import styles from './index.less';

const EMPTY_PROFILE: ProfileEditForm = {
  preferGenres: [],
  preferRow: undefined,
  preferSide: undefined,
};

const ProfileEditModal: React.FC<ProfileEditModalProps> = ({ open, onClose, onSaved }) => {
  const [form] = Form.useForm<ProfileEditForm>();
  const [genreOptions, setGenreOptions] = useState<Array<{ value: string; label: string }>>([]);
  const [submitting, setSubmitting] = useState(false);

  const { loading, run } = useRequest(
    async () => {
      // 个人资料与类型候选并行拉取，任一失败不阻塞另一方，保证类型候选始终可选
      const [profile, genres] = await Promise.all([
        getProfile().catch(() => null),
        listGenres().catch(() => [] as string[]),
      ]);
      const saved = profile ?? EMPTY_PROFILE;
      // 类型选项 = 当前资料已选 ∪ 后端 tag 字典表；已选优先，避免已存类型不在候选时无法显示
      const options = union(saved.preferGenres || [], genres)
        .filter(Boolean)
        .sort();
      return { saved, options };
    },
    {
      manual: true,
      onSuccess: ({ saved, options }) => {
        setGenreOptions(options.map((genre) => ({ value: genre, label: genre })));
        form.setFieldsValue({
          preferGenres: saved.preferGenres || [],
          preferRow: saved.preferRow,
          preferSide: saved.preferSide,
        });
      },
    },
  );

  useEffect(() => {
    if (open) {
      form.resetFields();
      run();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const handleFinish = async (values: ProfileEditForm) => {
    setSubmitting(true);
    try {
      await updateProfile(values);
      message.success('观影偏好已保存');
      onSaved?.();
      onClose();
    } catch {
      // 请求层已处理。
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title="观影偏好"
      open={open}
      onOk={() => form.submit()}
      confirmLoading={submitting}
      okButtonProps={{ disabled: loading }}
      onCancel={onClose}
      width={480}
      destroyOnHidden
    >
      <Form form={form} layout="vertical" onFinish={handleFinish}>
        <Form.Item name="preferGenres" label="偏好类型">
          <Select
            mode="multiple"
            allowClear
            placeholder="可多选，留空表示不偏好"
            options={genreOptions}
          />
        </Form.Item>
        <Form.Item name="preferRow" label="偏好排位">
          <Radio.Group options={PREFER_ROW_OPTIONS} />
        </Form.Item>
        <Form.Item name="preferSide" label="偏好侧向">
          <Radio.Group options={PREFER_SIDE_OPTIONS} />
        </Form.Item>
      </Form>
      <p className={styles.tip}>偏好将用于「为你推荐」等个性化服务，可随时修改。</p>
    </Modal>
  );
};

export default ProfileEditModal;
