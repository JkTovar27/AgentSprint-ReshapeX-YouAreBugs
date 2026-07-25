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

## 2. SICK Sensor Orchestrator: Plan-and-Execute con Routing Condicional
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
