import argparse
import hashlib
import json
import logging
import time
from pathlib import Path
from typing import Optional

from src.rag.chroma_client import get_chroma_client
from src.utils.config import OPENAI_API_KEY, OPENAI_BASE_URL

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
logger = logging.getLogger(__name__)

COLLECTION_NAME = "sick_manuals"
LOCAL_MODEL = "paraphrase-multilingual-MiniLM-L12-v2"
OPENAI_MODEL = "text-embedding-3-small"
BATCH_SIZE = 20
MAX_DOC_BYTES = 15000  # ChromaDB Cloud free tier: ~16KB por documento


def _make_id(source_url: str, title: str = "") -> str:
    key = (source_url or "") + "||" + (title or "unknown")
    return hashlib.sha256(key.encode()).hexdigest()[:32]


def load_json(path: str) -> list[dict]:
    path = Path(path)
    if not path.exists():
        raise FileNotFoundError(f"No se encuentra {path}")
    data = json.loads(path.read_text(encoding="utf-8"))
    logger.info("Cargados %d documentos desde %s", len(data), path)
    return data


def filter_documents(data: list[dict]) -> list[dict]:
    valid = [d for d in data if d.get("text") and d["text"].strip()]
    skipped = len(data) - len(valid)
    if skipped:
        logger.info("Saltados %d documentos sin contenido de texto", skipped)
    return valid


def _embeddings_local(texts: list[str], model_name: str) -> list[list[float]]:
    from sentence_transformers import SentenceTransformer
    logger.info("Cargando modelo local %s...", model_name)
    model = SentenceTransformer(model_name)
    all_embeddings = []
    for i in range(0, len(texts), BATCH_SIZE):
        batch = texts[i : i + BATCH_SIZE]
        emb = model.encode(batch, show_progress_bar=False)
        all_embeddings.extend(emb.tolist())
        logger.info("  Embeddings generados: %d/%d", min(i + BATCH_SIZE, len(texts)), len(texts))
    return all_embeddings


def _embeddings_api(texts: list[str], model_name: str) -> list[list[float]]:
    from openai import OpenAI
    client = OpenAI(api_key=OPENAI_API_KEY, base_url=OPENAI_BASE_URL)
    all_embeddings = []
    for i in range(0, len(texts), BATCH_SIZE):
        batch = texts[i : i + BATCH_SIZE]
        resp = client.embeddings.create(input=batch, model=model_name)
        all_embeddings.extend([e.embedding for e in resp.data])
        logger.info("  Embeddings generados: %d/%d", min(i + BATCH_SIZE, len(texts)), len(texts))
        if i + BATCH_SIZE < len(texts):
            time.sleep(0.5)
    return all_embeddings


def ingest(
    input_path: str = "data/raw/sick_manuals.json",
    collection_name: str = COLLECTION_NAME,
    embedding_provider: str = "local",
    embedding_model: str = "",
    dry_run: bool = False,
):
    data = load_json(input_path)
    docs = filter_documents(data)

    if not docs:
        logger.warning("No hay documentos con texto para insertar")
        return

    ids = [_make_id(d.get("source_url", ""), d.get("title", "")) for d in docs]
    documents = []
    truncated = 0
    for d in docs:
        text = d["text"]
        encoded = text.encode("utf-8")
        if len(encoded) > MAX_DOC_BYTES:
            text = encoded[:MAX_DOC_BYTES].decode("utf-8", errors="ignore")
            truncated += 1
        documents.append(text)
    if truncated:
        logger.info("Truncados %d documentos para cumplir límite de tamaño", truncated)
    metadatas = [
        {
            "title": d.get("title", ""),
            "model": d.get("model", ""),
            "date": d.get("date", ""),
            "source_url": d.get("source_url") or d.get("title", ""),
            "pdf_url": d.get("pdf_url") or "",
            "type": d.get("type", ""),
        }
        for d in docs
    ]

    if not embedding_model:
        embedding_model = LOCAL_MODEL if embedding_provider == "local" else OPENAI_MODEL

    logger.info("Documentos a insertar: %d", len(docs))
    logger.info("Colección: %s", collection_name)
    logger.info("Provider embeddings: %s", embedding_provider)
    logger.info("Modelo embeddings: %s", embedding_model)

    if dry_run:
        logger.info("=== DRY RUN — no se inserta nada ===")
        logger.info("  IDs de ejemplo: %s", ids[:2])
        logger.info("  Documentos de ejemplo: %s", [d[:80] for d in documents[:2]])
        logger.info("  Metadatos de ejemplo: %s", metadatas[:2])
        return

    if embedding_provider == "local":
        embeddings = _embeddings_local(documents, embedding_model)
    else:
        embeddings = _embeddings_api(documents, embedding_model)

    chroma = get_chroma_client()
    collection = chroma.get_or_create_collection(name=collection_name)
    logger.info("Colección lista: %s (%d documentos actuales)", collection_name, collection.count())

    collection.upsert(
        ids=ids,
        embeddings=embeddings,
        documents=documents,
        metadatas=metadatas,
    )

    logger.info("Inserción completada. Total en colección: %d", collection.count())


def main():
    parser = argparse.ArgumentParser(
        description="Inserta manuales SICK en ChromaDB"
    )
    parser.add_argument("--input", default="data/raw/sick_manuals.json",
                        help="JSON generado por el scraper")
    parser.add_argument("--collection", default=COLLECTION_NAME,
                        help="Nombre de la colección en ChromaDB")
    parser.add_argument("--embedding-provider", choices=["local", "api"], default="local",
                        help="local = sentence-transformers (gratis), api = OpenAI-compatible")
    parser.add_argument("--embedding-model", default="",
                        help="Modelo de embeddings (default según provider)")
    parser.add_argument("--dry-run", action="store_true",
                        help="Solo mostrar qué se insertaría, sin ejecutar")
    args = parser.parse_args()

    ingest(
        input_path=args.input,
        collection_name=args.collection,
        embedding_provider=args.embedding_provider,
        embedding_model=args.embedding_model,
        dry_run=args.dry_run,
    )


if __name__ == "__main__":
    main()
