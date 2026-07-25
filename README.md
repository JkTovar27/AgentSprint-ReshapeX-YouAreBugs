# SICK Select Copilot

Copiloto de preselección de sensores Sick con **descarte explicable**.
Traduce una necesidad industrial descrita en lenguaje natural a requisitos
estructurados, consulta la documentación oficial de Sick (RAG), aplica reglas
determinísticas de compatibilidad, calcula una confianza y entrega una
shortlist de familias de sensores con la evidencia citada. Cuando falta
certeza, **escala a un humano en vez de inventar** una respuesta.

## Arquitectura

El sistema se organiza en 5 componentes, cada uno en su propia carpeta de
`src/`. El estado reportado abajo es el real de la rama `test` a la fecha de
este README — no todos los componentes están implementados todavía.

| Componente | Carpeta | Responsabilidad | Estado |
|---|---|---|---|
| Agentes | `src/agents/` | Orquestación del flujo y guardrails |  En progreso — solo existe `BaseAgent`, una clase base sin lógica de orquestación aún |
| RAG | `src/rag/` | Conocimiento: indexar y consultar documentación oficial Sick | En progreso — carpeta vacía (placeholder) |
| Tools | `src/tools/` | Validación determinística de compatibilidad (function calling) | Implementado — ver detalle abajo |
| Memoria | `src/memory/` | Persistencia de estado/conversación | En progreso — carpeta vacía (placeholder) |
| App | `app/` | Superficie de producción/demo (FastAPI) | En progreso — solo expone un endpoint placeholder |

### `src/tools/` (componente más maduro)

Sin LLM en el cálculo — el modelo solo decide *cuándo* llamar a estas
funciones; el resultado siempre lo calcula este código:

- `requisitos.py` — `RequisitosSensor`, schema Pydantic de lo que el usuario
  pide (tipo de objeto, distancia, ambiente, material, montaje), con
  detección de `campos_faltantes` sin rellenar valores por defecto.
- `reglas.py` — `evaluar_familia`: compara los requisitos contra las specs de
  una familia de sensores y devuelve un veredicto (`viable` / `descartada` /
  `ambigua`) con razones explicables. Endurecido contra datos de catálogo mal
  formados (rango invertido, formato inesperado, claves con nombre distinto
  al esperado).
- `confianza.py` — `calcular_confianza`: combina completitud del requisito,
  calidad de la evidencia RAG (top-N, con clamp a `[0, 1]`) y certeza de las
  reglas evaluadas en un score `0-1`, y decide si el caso debe escalarse a un
  humano.
- `registry.py` — expone `evaluar_familia` y `calcular_confianza` como
  herramientas de function calling, con su JSON Schema derivado
  automáticamente de la firma real de cada función.

Los demás componentes (`agents`, `rag`, `memory`, `app`) están planteados en
la estructura del repo pero su lógica todavía no existe.

## Setup

Comandos reales definidos en `Makefile` y `requirements.txt`:

```bash
make setup        # crea .venv e instala requirements.txt
cp .env.example .env   # copiar variables de entorno de ejemplo
```

Variables en `.env.example`: `OPENAI_API_KEY`, `OPENAI_MODEL`
(`gpt-4o-mini` por defecto), `APP_ENV`.

Dependencias principales (`requirements.txt` / `pyproject.toml`): `fastapi`,
`uvicorn`, `python-dotenv`, `langgraph`, `langchain`, `openai`, `pydantic`,
`pytest`.

## Cómo correr

```bash
make run
```

Esto levanta `app.main:app` con Uvicorn en `http://localhost:8000`. Hoy la
app es solo un esqueleto FastAPI: `GET /` responde un mensaje placeholder
(`"Proyecto base listo para desarrollar agentes y RAG"`). **El flujo real
del copiloto (consulta en lenguaje natural → shortlist con evidencia) todavía
no está expuesto por la API** — es la parte que falta conectar entre
`agents`, `rag` y `tools`.

Mientras tanto, la única pieza funcional y usable directamente es
`src/tools/`: se puede invocar `evaluar_familia` y `calcular_confianza` desde
Python (ver los tests en `tests/test_reglas.py` y `tests/test_confianza.py`
como ejemplos de uso).

## Tests

```bash
make test
# o
python -m pytest -q
```

Hay **46 tests** en `tests/`, todos pasando en la rama `test`:

- `test_requisitos.py` — validación del schema `RequisitosSensor`.
- `test_reglas.py` — motor de reglas de compatibilidad (`evaluar_familia`).
- `test_confianza.py` — cálculo de confianza (`calcular_confianza`).
- `test_registry.py` — schemas de function calling derivados de firmas.
- `test_base_agent.py` — smoke test del stub `BaseAgent`.

CI (`.github/workflows/ci.yml`) corre `pytest -q` en cada push/PR a
`main`/`master`.

## Estructura de ramas

- `main` — producción.
- `test` — integración de los distintos roles antes de llegar a `main`.
- `role/*` — una rama por persona/responsabilidad (p. ej. `role/tools-bebito`,
  `role/rag-samuel`, `role/orquestacion-juanka`, `role/demo-pablo`), que se
  integran contra `test`.

## Próximos pasos

- Implementar el pipeline RAG (`src/rag/`) sobre documentación oficial Sick.
- Implementar la orquestación y guardrails del agente (`src/agents/`) que
  conecte requisitos → RAG → `src/tools/` → shortlist.
- Persistencia de conversación/estado (`src/memory/`).
- Exponer el flujo completo en `app/` en vez del endpoint placeholder actual.
