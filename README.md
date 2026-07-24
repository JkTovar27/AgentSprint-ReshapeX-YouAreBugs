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

Editar el archivo .env con tus credenciales reales.

## Próximos pasos

- Implementar agentes LangGraph.
- Integrar pipeline RAG con embeddings y retrieval.
- Agregar herramientas externas y memoria persistente.
- Desarrollar la interfaz de la app.
