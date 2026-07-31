import { Button, Space } from 'antd';
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons';
import type { FilterBarProps } from './interface';
import styles from './index.less';

export type { FilterCell, FilterBarProps } from './interface';

export default function FilterBar({
  fields,
  actions,
  onSearch,
  onReset,
  columns = 4,
}: FilterBarProps) {
  if (!fields || fields.length === 0) {
    return null;
  }

  return (
    <div className={styles.root}>
      {/* ---- 筛选项网格 ---- */}
      <div className={`${styles.filterGrid} ${styles[`cols${columns}`]}`}>
        {fields.map((field, idx) => (
          <div
            key={idx}
            className={styles.filterCell}
            style={
              field.span && field.span > 1
                ? { gridColumn: `span ${field.span}` }
                : undefined
            }
          >
            <label className={styles.filterLabel}>{field.label}</label>
            <div className={styles.filterControl}>{field.children}</div>
          </div>
        ))}
      </div>

      {/* ---- 按钮行 ---- */}
      <div className={styles.filterActions}>
        <div>{actions}</div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={onReset}>
            重置
          </Button>
          <Button type="primary" icon={<SearchOutlined />} onClick={onSearch}>
            查询
          </Button>
        </Space>
      </div>
    </div>
  );
}
