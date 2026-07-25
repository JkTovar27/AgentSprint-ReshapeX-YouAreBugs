import chromadb
from src.utils.config import CHROMADB_API_KEY, CHROMADB_TENANT, CHROMADB_DATABASE


chroma_client = chromadb.CloudClient(
    api_key=CHROMADB_API_KEY,
    tenant=CHROMADB_TENANT,
    database=CHROMADB_DATABASE,
)


def get_chroma_client() -> chromadb.CloudClient:
    return chroma_client
