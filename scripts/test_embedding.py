"""fastembed 冒烟测试：确认本地中文 Embedding 模型可加载、可向量化。

首次运行会联网下载模型权重（约百 MB），请耐心等待。
用法： python scripts/test_embedding.py
"""
from __future__ import annotations

import sys

if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")


def main() -> None:
    from fastembed import TextEmbedding

    # 列出官方支持的中文模型，便于确认可用名称
    print("=== fastembed 支持的含 zh 的模型 ===")
    for m in TextEmbedding.list_supported_models():
        if "zh" in m["model"].lower() or "chinese" in str(m).lower():
            print(f"- {m['model']}  (dim={m.get('dim')})")

    model_name = "BAAI/bge-small-zh-v1.5"
    print(f"\n=== 加载模型 {model_name}（首次需下载，请稍候）===")
    embedder = TextEmbedding(model_name=model_name)

    docs = ["开场前1小时以上可全额退票", "今天天气怎么样"]
    print("\n=== 向量化 ===")
    vectors = list(embedder.embed(docs))
    for doc, vec in zip(docs, vectors):
        print(f"- 「{doc}」 → 维度 {len(vec)}，前5维 {vec[:5].round(4).tolist()}")

    # 简单相似度：退票句 vs 天气句 与 query 的余弦相似度
    import numpy as np

    query = "怎么退票"
    q = np.array(list(embedder.embed([query]))[0])
    print(f"\n=== 相似度（query=「{query}」）===")
    for doc, vec in zip(docs, vectors):
        v = np.array(vec)
        sim = float(q @ v / (np.linalg.norm(q) * np.linalg.norm(v)))
        print(f"- 与「{doc}」 相似度 {sim:.4f}")

    print("\n[OK] embedding 模型工作正常")


if __name__ == "__main__":
    main()
