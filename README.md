# AgentSprint-ReshapeX-YouAreBugs

Proyecto base para un sistema agéntico con arquitectura modular orientada a agentes, RAG y una app mínima.

## Estructura

```text
AgentSprint-ReshapeX-YouAreBugs/
├── .github/
│   └── workflows/
├── src/
│   ├── agents/
│   ├── rag/
│   ├── tools/
│   ├── memory/
│   ├── prompts/
│   └── utils/
├── data/
│   └── raw/
├── notebooks/
├── tests/
├── app/
├── docs/
│   └── ADR.md
├── .env.example
├── .env
├── .gitignore
├── requirements.txt
├── pyproject.toml
├── Makefile
└── README.md
```

## Inicio rápido

```bash
make setup
cp .env.example .env
make test
make run
```

## Variables de entorno

Editar el archivo `.env` con tus credenciales reales:

| Variable | Descripción |
|---|---|
| `OPENAI_API_KEY` | API key de OpenAI |
| `OPENAI_MODEL` | Modelo por defecto (ej. gpt-4o-mini) |
| `CHROMADB_API_KEY` | API key de ChromaDB Cloud |
| `CHROMADB_TENANT` | Tenant ID de ChromaDB Cloud |
| `CHROMADB_DATABASE` | Nombre de la base de datos en ChromaDB |

## ChromaDB Cloud

El proyecto usa ChromaDB Cloud como vector store. El cliente se inicializa automáticamente al importar:

```python
from src.rag import chroma_client
# o
from src.rag.chroma_client import get_chroma_client
client = get_chroma_client()
```

Ver `docs/ADR.md` para la justificación de la decisión arquitectónica.

## Scraper de manuales SICK

Extrae documentación técnica de SICK desde [manuals.plus/es/category/sick](https://manuals.plus/es/category/sick) y genera JSON listo para ChromaDB.

```bash
# Ver todas las opciones
python -m src.scrapers.manuals_plus --help

# Scrapear solo la primera página (sin descargar PDFs)
python -m src.scrapers.manuals_plus --max-pages 1

# Scrapear todo y descargar PDFs (requiere pantalla, en Linux usar xvfb-run)
python -m src.scrapers.manuals_plus --download

# Guardar en otra ruta
python -m src.scrapers.manuals_plus --output data/raw/sick_manuals.json
```

También desde código:

```python
from src.scrapers import scrape_sick_manuals
data = scrape_sick_manuals(max_pages=1, download_pdfs=True)
```

**Nota:** El scraper abre una ventana del navegador (Playwright headed) para evadir Cloudflare.

Ver `docs/ADR.md` (#3) para detalles técnicos.

## Ingestión en ChromaDB

Una vez generado el JSON con el scraper, se inserta en ChromaDB:

```bash
# Vista previa (no inserta nada)
python -m src.rag.ingest --input data/raw/sick_manuals.json --dry-run

# Insertar en ChromaDB (requiere OPENAI_API_KEY válida en .env)
python -m src.rag.ingest --input data/raw/sick_manuals.json
```

Desde código:

```python
from src.rag import ingest
ingest(input_path="data/raw/sick_manuals.json", dry_run=False)
```

El pipeline completo: **scrape → ingest**:

```bash
python -m src.scrapers.manuals_plus --download && \
python -m src.rag.ingest
```

## Próximos pasos

- Implementar agentes LangGraph con retrieval sobre ChromaDB.
- Agregar herramientas externas y memoria persistente.
- Desarrollar la interfaz de la app.
