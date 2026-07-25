# System Contract: SICK Sensor Orchestrator

**Versión**: 1.0  
**Fecha**: 2026-07-25  
**Estado**: Activo  
**Dueño**: Agent Architect / Orchestration Lead

---

## 1. Propósito del Sistema

El SICK Sensor Orchestrator es un motor de decisión basado en LangGraph que:

- **Recibe**: Consultas en lenguaje natural sobre selección de sensores SICK
- **Procesa**: Análisis estructurado de requerimientos usando subagentes especializados
- **Entrega**: Recomendaciones técnicas fundamentadas con evidencia documental

**NO es un sistema que**:
- Elige "mágicamente" el mejor sensor
- Recomienda sin justificación técnica
- Acepta consultas incompletas sin validación
- Toma decisiones con confianza baja

---

## 2. Entradas (Input Contract)

### 2.1 Formato Aceptado

```json
{
  "user_query": "string en lenguaje natural",
  "context": {
    "budget_usd": "opcional",
    "volume": "opcional",
    "environment": "opcional"
  }
}
```

### 2.2 Ejemplos Válidos

✅ *"Necesito un sensor de distancia para un ambiente polvoriento, rango 0-10m, precisión ±5cm"*

✅ *"¿Qué opciones tengo para detectar posición en una línea de producción? Rango hasta 5 metros"*

### 2.3 Ejemplos Rechazados (Incompletos)

❌ *"¿Cuál es el mejor sensor SICK?"* (demasiado vago)

❌ *"Sensor barato"* (sin especificación técnica)

---

## 3. Salidas (Output Contract)

### 3.1 Estructura Obligatoria

```python
{
  "status": "recommended" | "escalated" | "needs_clarification",
  
  "shortlist": [
    {
      "model": "SICK S300",
      "type": "Time-of-Flight Sensor",
      "range": "0.3-8m",
      "precision": "±50mm @ 8m",
      "reasoning": "Cumple rango y precisión exacta"
    }
  ],
  
  "discards": [
    {
      "model": "SICK S100",
      "reason": "Rango máximo 5m, insuficiente para 10m requeridos"
    }
  ],
  
  "reasons": {
    "methodology": "Matching técnico basado en specs SICK + validación de viabilidad",
    "criteria_applied": ["range_match", "precision_match", "environment_rating"]
  },
  
  "sources": [
    {
      "model": "SICK S300",
      "datasheet": "SICK_S300_v2.1_2023",
      "section": "Technical Specifications"
    }
  ],
  
  "confidence": {
    "score": 95,
    "rationale": "Specs en DB coinciden perfectamente; 3 candidatos validados"
  },
  
  "escalation": null | "reason if escalated"
}
```

### 3.2 Definiciones

| Campo | Descripción |
|-------|-------------|
| **shortlist** | 1-5 sensores recomendados ordenados por relevancia |
| **discards** | Sensores descartados con motivo técnico explicado |
| **reasons** | Metodología y criterios aplicados |
| **sources** | Referencias a datasheets en la BD; trazabilidad completa |
| **confidence** | Score 0-100 + justificación del score |
| **escalation** | Si no hay recomendación, motivo de escalada (no decisión indefinida) |

### 3.3 Ejemplos de Salida

#### Recomendación Exitosa (status: "recommended")

```json
{
  "status": "recommended",
  "shortlist": [
    {
      "model": "SICK S300 Professional",
      "type": "3D Time-of-Flight Camera",
      "range": "0.3-8m",
      "precision": "±50mm @ 8m",
      "ip_rating": "IP67",
      "reasoning": "Cubre exactamente rango 0-10m (8m min garantizado); IP67 soporta ambiente polvoriento; precisión ±50mm < requisito ±5cm"
    }
  ],
  "discards": [],
  "reasons": {
    "methodology": "Búsqueda en Chroma (embeddings) + validación de specs contra requerimientos",
    "criteria_applied": ["range_match", "precision_match", "ip_rating_validation"]
  },
  "sources": [
    {
      "model": "SICK S300 Professional",
      "datasheet_id": "sick_s300_v2.1_2023",
      "section": "3. Technical Specifications",
      "pages": "12-15"
    }
  ],
  "confidence": {
    "score": 92,
    "rationale": "Especificaciones en DB alinean perfectamente con requerimientos técnicos; única solución viable encontrada"
  },
  "escalation": null
}
```

#### Recomendación sin Confianza (status: "escalated")

```json
{
  "status": "escalated",
  "shortlist": [],
  "discards": [],
  "reasons": {
    "methodology": "Validación de completitud de requerimientos",
    "criteria_applied": ["missing_critical_fields"]
  },
  "sources": [],
  "confidence": {
    "score": 0,
    "rationale": "Datos críticos incompletos; no hay base para recomendación"
  },
  "escalation": "Falta especificar: precisión requerida, tipo de detección (distancia vs. presencia)"
}
```

---

## 4. Campos Críticos de Validación

El sistema **DEBE** validar que la consulta incluya como mínimo:

### 4.1 Obligatorios (sin estos, ESCALA)

| Campo | Descripción | Ejemplo |
|-------|-------------|---------|
| **Tipo de Sensor** | Qué detecta (distancia, presencia, reflectancia, 3D) | "Sensor de distancia" |
| **Rango** | Rango de operación (mín-máx en metros) | "0-10m" |
| **Precisión o Resolución** | Exactitud requerida | "±5cm" o "±5%" |
| **Ambiente** | Condiciones de operación | "Polvoriento", "Exterior", "Aceitoso" |

### 4.2 Fuertemente Recomendados (sin estos, confianza ↓)

| Campo | Descripción | Impacto |
|-------|-------------|--------|
| **IP Rating** | Grado de protección necesario | Si no se especifica → confianza -15 puntos |
| **Rango de Temperatura** | Operación térmica | Si no se especifica → confianza -10 puntos |
| **Interfaz de Comunicación** | RS232, CANopen, IO-Link, etc. | Si no se especifica → confianza -10 puntos |

### 4.3 Opcionales (contexto)

| Campo | Descripción |
|-------|-------------|
| **Presupuesto** | USD o EUR |
| **Volumen de Producción** | Unidades/año |
| **Integración Existente** | PLC model, software |

---

## 5. Límites y Restricciones

### 5.1 Límites Técnicos

1. **Datos Críticos Faltantes**: Si faltan 2+ campos obligatorios → **NO RECOMIENDA**. Escala con solicitud explícita de aclaraciones.

2. **Confianza Umbral**: Si confidence < 70 → **NO RECOMIENDA**. Ofrece alternativas o escala.

3. **Iteraciones de Aclaración**: Máx 3 ciclos. Si después de 3 aclaraciones hay ambigüedad → **ESCALA**.

4. **Fuera del Dominio**: Si consulta es sobre sensores NO-SICK (ej: "¿Sensores Bosch?") → **ESCALA** con mensaje claro.

### 5.2 Lo Que El Sistema NO Hace

| NO | Razón |
|----|-------|
| ❌ No recomendó sin evidencia | Cada decisión debe estar en la BD con traza |
| ❌ No elige entre múltiples opciones válidas | Devuelve shortlist; la decisión final es del usuario |
| ❌ No negocia precios | Solo valida factibilidad técnica |
| ❌ No instala ni configura | Solo recomienda en base a specs |
| ❌ No actualiza la BD automáticamente | Las specs SICK se cargan manualmente o via cron |

---

## 6. Criterios de Éxito

### 6.1 Caso 1: Recomendación Exitosa ✅

**Condición**: Consulta completa, requerimientos validados, confianza ≥ 70  
**Salida**: status="recommended", shortlist poblado, confidence ≥ 70  
**Evidencia**: Todas las fuentes citadas en la BD

### 6.2 Caso 2: Aclaración Requerida ✅

**Condición**: Consulta incompleta pero recuperable (< 3 iteraciones)  
**Salida**: status="needs_clarification", preguntas específicas formuladas  
**Evidencia**: Se identifica exactamente qué campos faltan

### 6.3 Caso 3: Escalada Exitosa ✅

**Condición**: Ambigüedad irrecuperable O confianza muy baja OR fuera del dominio  
**Salida**: status="escalated", escalation_reason poblado, shortlist vacío  
**Evidencia**: Se explica POR QUÉ no hay recomendación

### 6.4 Fracaso (Lo que NO debe suceder) ❌

| Error | Descripción |
|-------|-------------|
| ❌ Recomendación sin justify | Sistema recomienda S300 sin citar spec en BD |
| ❌ Confianza falsa | confidence=95 cuando hay dudas en matching |
| ❌ Recomendación incompleta | shortlist vacío pero status="recommended" |
| ❌ Escalada sin motivo | status="escalated", escalation_reason=null |

---

## 7. Flujo de Decisión (Decisión Trees)

### 7.1 Árbol de Validación

```
START: user_query
  │
  ├─→ parse_requirements()
  │     output: structured_requirements, missing_fields
  │
  ├─→ validate_completeness()
  │     │
  │     ├─ Todos los campos obligatorios presentes?
  │     │   ├─ SÍ → CONTINUE (paso 2)
  │     │   └─ NO → ¿Refinement count < 3?
  │     │       ├─ SÍ → REQUEST_CLARIFICATION (humano provee info)
  │     │       └─ NO → ESCALATE con motivo
  │     │
  │     └─ Confianza inicial estimable?
  │         ├─ NO → ESCALATE (dominio fuera de alcance)
  │         └─ SÍ → CONTINUE
  │
  └─→ PHASE_2: Análisis y Matching
```

### 7.2 Árbol de Recomendación

```
PHASE_2: candidates = DB_RETRIEVAL(structured_requirements)
  │
  ├─ ¿Encontrados > 0 candidatos?
  │   ├─ NO → ESCALATE ("No hay sensores en BD que cumplan")
  │   └─ SÍ → CONTINUE
  │
  ├─ FEASIBILITY_CHECK(candidates)
  │   output: viable_candidates, reasons
  │
  ├─ ¿viable_candidates > 0?
  │   ├─ NO → ESCALATE ("Todos los candidatos son inviables por X")
  │   └─ SÍ → CONTINUE
  │
  ├─ confidence_score = EVALUATE(candidates, viable, reasons)
  │
  ├─ ¿confidence ≥ 70?
  │   ├─ NO → ESCALATE ("Confianza muy baja: " + reason)
  │   └─ SÍ → RECOMMEND (shortlist + discards)
  │
  └─ RETURN final_output
```

---

## 8. Garantías del Sistema

### 8.1 Garantías Positivas (Lo que SÍ garantiza)

✅ **Trazabilidad**: Toda recomendación cita fuentes en BD  
✅ **Justificación**: Cada recomendación tiene reasoning técnico  
✅ **Validación**: Requerimientos validados antes de recomendar  
✅ **Escalada Explícita**: Si no hay recomendación, se indica POR QUÉ  

### 8.2 Garantías Negativas (Lo que NO garantiza)

❌ Mejor sensor (solo opciones válidas)  
❌ Precio más bajo  
❌ Disponibilidad inmediata  
❌ Soporte técnico SICK (eso es fuera del scope)

---

## 9. Cambios Futuros a Este Contrato

Si el sistema necesita evolucionar (ej: agregar sensores no-SICK), se debe:

1. Crear nueva ADR en `docs/ADR.md`
2. Versionar este documento (e.g., 2.0)
3. Actualizar tests y documentación

---

## 10. Referencia Rápida: Checklist de Implementación

- [ ] Parser: extrae structured_requirements + missing_fields
- [ ] Validator: rechaza consultas incompletas
- [ ] Retriever: Chroma query por embeddings
- [ ] Matcher: scoring de candidatos
- [ ] Evaluator: confidence score
- [ ] Generator: output JSON según estructura
- [ ] Tests: validar cada caso (Rec, Clarif, Escal)

---

## Cambios Históricos

| Versión | Fecha | Cambio |
|---------|-------|--------|
| 1.0 | 2026-07-25 | Initial system contract formalized |
