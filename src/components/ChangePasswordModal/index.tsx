import React, { useEffect, useState } from 'react';
import { Form, Input, Modal, message } from 'antd';
import { changePassword } from '@/api/auth';
import { PASSWORD_MAX_LEN, PASSWORD_MIN_LEN } from '@/constants/account';
import type { ChangePasswordForm, ChangePasswordModalProps } from './interface';
import styles from './index.less';

const PASSWORD_RULE = {
  min: PASSWORD_MIN_LEN,
  max: PASSWORD_MAX_LEN,
  message: `密码长度 ${PASSWORD_MIN_LEN}–${PASSWORD_MAX_LEN}`,
};

const ChangePasswordModal: React.FC<ChangePasswordModalProps> = ({
  open,
  account,
  onClose,
  onChanged,
}) => {
  const [form] = Form.useForm<ChangePasswordForm>();
  const [error, setError] = useState('');

  useEffect(() => {
    if (open) {
      form.resetFields();
      setError('');
    }
  }, [open, form]);

  const handleFinish = async (values: ChangePasswordForm) => {
    setError('');
    try {
      await changePassword(
        {
          account: account || undefined,
          oldPassword: values.oldPassword,
          newPassword: values.newPassword,
        },
        { silent: true },
      );
      message.success('密码已修改，请重新登录');
      form.resetFields();
      onChanged?.();
      onClose();
    } catch (err) {
      // silent:true 已抑制全局 toast，这里内联展示后端返回的具体原因
      setError(err instanceof Error ? err.message : '修改失败，请稍后重试');
    }
  };

  return (
    <Modal
      title="修改密码"
      open={open}
      onOk={() => form.submit()}
      onCancel={onClose}
      width={400}
      destroyOnHidden
    >
      <Form form={form} layout="vertical" onFinish={handleFinish}>
        <Form.Item
          name="oldPassword"
          label="旧密码"
          rules={[{ required: true, message: '请输入旧密码' }, PASSWORD_RULE]}
        >
          <Input.Password placeholder="请输入旧密码" autoComplete="current-password" />
        </Form.Item>
        <Form.Item
          name="newPassword"
          label="新密码"
          dependencies={['oldPassword']}
          rules={[
            { required: true, message: '请输入新密码' },
            PASSWORD_RULE,
            ({ getFieldValue }) => ({
              validator: (_, value) =>
                !value || value !== getFieldValue('oldPassword')
                  ? Promise.resolve()
                  : Promise.reject(new Error('新密码不能与旧密码相同')),
            }),
          ]}
        >
          <Input.Password placeholder="8–64 位" autoComplete="new-password" />
        </Form.Item>
        <Form.Item
          name="confirmPassword"
          label="确认新密码"
          dependencies={['newPassword']}
          rules={[
            { required: true, message: '请再次输入新密码' },
            ({ getFieldValue }) => ({
              validator: (_, value) =>
                !value || value === getFieldValue('newPassword')
                  ? Promise.resolve()
                  : Promise.reject(new Error('两次输入的密码不一致')),
            }),
          ]}
        >
          <Input.Password placeholder="再次输入新密码" autoComplete="new-password" />
        </Form.Item>
        {error ? <div className={styles.error}>{error}</div> : null}
      </Form>
    </Modal>
  );
};

export default ChangePasswordModal;
