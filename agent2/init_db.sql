-- 数据库初始化脚本
-- 创建数据库（如果不存在）
CREATE DATABASE agent_db;

-- 创建会话表
CREATE TABLE IF NOT EXISTS sessions (
    session_id TEXT PRIMARY KEY,
    user_id TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    metadata JSONB DEFAULT '{}'
);

-- 创建消息表
CREATE TABLE IF NOT EXISTS messages (
    id SERIAL PRIMARY KEY,
    session_id TEXT REFERENCES sessions(session_id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK (role IN ('user', 'assistant', 'system')),
    content TEXT NOT NULL,
    timestamp TIMESTAMP DEFAULT NOW(),
    tool_calls JSONB
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_messages_session_id ON messages(session_id);
CREATE INDEX IF NOT EXISTS idx_messages_timestamp ON messages(timestamp);
CREATE INDEX IF NOT EXISTS idx_sessions_updated_at ON sessions(updated_at);

-- 添加注释
COMMENT ON TABLE sessions IS '会话表，存储用户会话信息';
COMMENT ON TABLE messages IS '消息表，存储会话的所有消息历史';
COMMENT ON COLUMN sessions.metadata IS '会话元数据，如authorization、latitude、longitude等';
COMMENT ON COLUMN messages.tool_calls IS '工具调用记录（JSON格式）';
