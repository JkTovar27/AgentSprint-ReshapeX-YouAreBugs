import os
from dotenv import load_dotenv

load_dotenv()

APP_ENV = os.getenv("APP_ENV", "development")
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "")
OPENAI_BASE_URL = os.getenv("OPENAI_BASE_URL", "https://openrouter.ai/api/v1")
OPENAI_MODEL = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
CHROMADB_API_KEY = os.getenv("CHROMADB_API_KEY", "")
CHROMADB_TENANT = os.getenv("CHROMADB_TENANT", "")
CHROMADB_DATABASE = os.getenv("CHROMADB_DATABASE", "")
OPENCODE_API_KEY = os.getenv("OPENCODE_API_KEY", "")
OPENCODE_BASE_URL = os.getenv("OPENCODE_BASE_URL", "https://opencode.ai/zen/v1")
OPENCODE_MODEL = os.getenv("OPENCODE_MODEL", "deepseek-v4-flash-free")
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")
