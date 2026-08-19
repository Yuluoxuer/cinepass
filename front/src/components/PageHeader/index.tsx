import styles from './index.less';

export interface PageHeaderProps {
  /** 页面标题 */
  title: string;
  /** 页面描述（可选） */
  description?: string;
}

export default function PageHeader({ title, description }: PageHeaderProps) {
  return (
    <div className={styles.root}>
      <h2 className={styles.title}>
        <span className={styles.bar} />
        {title}
      </h2>
      {description && <p className={styles.desc}>{description}</p>}
    </div>
  );
}
