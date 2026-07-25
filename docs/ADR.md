# ADR

## 1. Estructura inicial del proyecto
- Fecha: 2026-07-24
- Estado: Aceptado

### Contexto
Se requiere una base organizada para un proyecto de agentes con capacidades RAG, tests y una app mínima.

### Decisión
Crear una estructura modular con carpetas para agentes, RAG, herramientas, memoria, prompts, utilidades y una aplicación web inicial.

### Consecuencias
- Facilita el crecimiento del proyecto.
- Separa responsabilidades de forma clara.
- Permite incorporar pruebas y CI con menor fricción.

---

## 2. Integración ChromaDB Cloud
- Fecha: 2026-07-25
- Estado: Aceptado

### Contexto
Se necesita un vector store para almacenar y consultar embeddings en el pipeline RAG. ChromaDB Cloud fue elegido por su simplicidad, API nativa en Python y modo cloud gestionado.

### Decisión
- Usar `chromadb.CloudClient` con autenticación vía API key.
- Centralizar credenciales en `.env` (`CHROMADB_API_KEY`, `CHROMADB_TENANT`, `CHROMADB_DATABASE`).
- Crear el módulo `src/rag/chroma_client.py` que exporta el cliente ya instanciado y una función `get_chroma_client()`.
- Exponer la configuración desde `src/utils/config.py`.

### Consecuencias
- El cliente se conecta al tenant `288e45a4-59fb-4a5e-aefa-bba7d4845bd1` y base de datos `DBSICK`.
- Cualquier módulo puede importar el cliente vía `from src.rag import chroma_client`.
- Las credenciales no quedan hardcodeadas en el código fuente.
- Para development local se puede cambiar a `chromadb.Client()` sin afectar el resto del sistema.

---

## 3. Scraper de manuales SICK (manuals.plus)
- Fecha: 2026-07-25
- Estado: Aceptado

### Contexto
Se necesita un pipeline de extracción de documentación técnica de SICK desde manuals.plus para poblar el vector store. El sitio usa Cloudflare como protección anti-bot.

### Decisión
- Usar **Playwright** en modo headed (navegador real) para evadir Cloudflare en las páginas de listado.
- Para descargar PDFs, usar `fetch` + `FileReader` dentro del contexto del navegador (hereda las cookies de Cloudflare).
- Extraer texto de PDFs con **PyMuPDF** (fitz).
- Estructurar el scraper en `src/scrapers/manuals_plus.py` con tres fases: listado → parseo → descarga.
- No perseguir páginas de detalle `/es/sick/<slug>` (bloqueadas por Cloudflare incluso en modo headed). Solo se extrae descripción del listado.
- Para URLs de tipo `/es/m/<hash>`: construir PDF URL como `https://manuals.plus/m/<hash>.pdf`.
- Para URLs `/es/asin/` y `/es/ae/`: omitir (enlaces externos a Amazon/AliExpress).

### Consecuencias
- El scraper requiere una sesión gráfica (no corre en CI headless).
- Produce JSON listo para Chroma: `{title, model, date, description, source_url, pdf_url, type, text}`.
- 12 de 38 manuales por página tienen PDF descargable (~31%).
- Dependencias añadidas: `playwright`, `beautifulsoup4`, `PyMuPDF`.
- Para correr sin interfaz gráfica (Linux), usar `xvfb-run python -m src.scrapers.manuals_plus`.

---

## 4. SICK Sensor Orchestrator: Plan-and-Execute con Routing Condicional
- Fecha: 2026-07-25
- Estado: Aceptado
- Documento de referencia: `docs/system_contract.md`

### Contexto
Necesitamos un motor de decisión para recomendación de sensores SICK que:
- Reciba consultas en lenguaje natural
- Valide requerimientos ANTES de hacer recomendaciones
- Evite recomendaciones sin evidencia o con baja confianza
- Escale explícitamente cuando falten datos críticos o haya ambigüedad

### Decisión
Adoptar arquitectura **Plan-and-Execute** con **routing condicional** en LangGraph:

1. **State**: TypedDict con requerimientos, candidatos, recomendaciones finales
2. **Nodos**: 10+ nodos especializados (parse, validate, analyze, retrieve, match, check, suggest, evaluate, recommend)
3. **Subagentes**: 5-7 agentes LLM especializados (requirement analyzer, DB retriever, spec matcher, feasibility checker, alternative suggester)
4. **Edges**: Condicionales (CONTINUE si completo, ACLARAR si incompleto, ESCALAR si ambiguo)
5. **Límites**: 
   - Máx 3 iteraciones de aclaración
   - Confianza mínima 70 para recomendar
   - Campos obligatorios: tipo de sensor, rango, precisión, ambiente

### Salida (Output Contract)
```json
{
  "status": "recommended" | "escalated" | "needs_clarification",
  "shortlist": [{model, type, range, precision, reasoning}],
  "discards": [{model, reason}],
  "reasons": {methodology, criteria_applied},
  "sources": [{model, datasheet, section}],
  "confidence": {score, rationale},
  "escalation": null | "reason"
}
```

### Consecuencias
- ✅ Auditoría completa: cada decisión está justificada y citada
- ✅ Predecible: flujo fijo vs. ReAct exploratorio
- ✅ Escalada explícita: nunca recomendación indefinida
- ✅ Validación obligatoria: imposible recomendar sin completitud
- ✅ Trazabilidad: todas las fuentes en BD documentadas
- ⚠️ Requiere: base de datos de specs SICK bien poblada
- ⚠️ Requiere: definición clara de campos críticos por dominio
- ⚠️ Límite: máx 3 aclaraciones (después escala a humano)
