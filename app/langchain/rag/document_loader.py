"""Document loading and chunking utilities for RAG corpus."""
from pathlib import Path

from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_community.document_loaders import DirectoryLoader, PyPDFLoader, TextLoader
from langchain_core.documents import Document

LOADER_MAP = {
    ".pdf": PyPDFLoader,
    ".txt": TextLoader,
    ".md": TextLoader,
}


def load_file(file_path: str) -> list[Document]:
    suffix = Path(file_path).suffix.lower()
    loader_cls = LOADER_MAP.get(suffix)
    if loader_cls is None:
        raise ValueError(f"Unsupported type: {suffix}, supported: {list(LOADER_MAP)}")
    return loader_cls(str(file_path)).load()


def split_documents(
    docs: list[Document], chunk_size: int, chunk_overlap: int
) -> list[Document]:
    splitter = RecursiveCharacterTextSplitter(
        chunk_size=chunk_size,
        chunk_overlap=chunk_overlap,
        length_function=len,
    )
    return splitter.split_documents(docs)


def load_directory(directory: str) -> list[Document]:
    docs: list[Document] = []
    for suffix, loader_cls in LOADER_MAP.items():
        loader = DirectoryLoader(
            directory,
            glob=f"**/*{suffix}",
            loader_cls=loader_cls,
            silent_errors=True,
        )
        docs.extend(loader.load())
    return docs
