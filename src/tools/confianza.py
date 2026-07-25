"""Cálculo determinístico de confianza para el veredicto de compatibilidad.

Sin LLM: combina tres señales medibles en un único score 0-1 y decide si
el caso debe escalarse a un humano. El modelo nunca decide la confianza —
solo consume este número para decidir su siguiente acción.
"""

from __future__ import annotations

from dataclasses import dataclass, field

from src.tools.reglas import ResultadoEvaluacion
from src.tools.requisitos import CAMPOS_REQUERIDOS, RequisitosSensor

PESOS_POR_DEFECTO = {
    "completitud": 1 / 3,
    "calidad_evidencia": 1 / 3,
    "certeza_reglas": 1 / 3,
}


@dataclass
class ResultadoConfianza:
    """Resultado de calcular_confianza."""

    confianza: float
    desglose: dict[str, float] = field(default_factory=dict)
    debe_escalar: bool = False


def calcular_confianza(
    requisitos: RequisitosSensor,
    evidencia: list[dict],
    evaluaciones: list[ResultadoEvaluacion],
    pesos: dict[str, float] | None = None,
    umbral_escalar: float = 0.6,
    top_n_evidencia: int = 2,
) -> ResultadoConfianza:
    """Combina completitud, calidad de evidencia y certeza de reglas.

    - completitud = campos presentes / campos totales del schema de requisitos.
    - calidad_evidencia = promedio de score de los `top_n_evidencia` fragmentos
      con mayor score en `evidencia` (todos si hay menos de `top_n_evidencia`);
      0.0 si está vacía. Usar el top-N en vez del promedio total evita que
      muchos fragmentos irrelevantes diluyan un fragmento muy relevante. Un
      item sin clave "score" cuenta como 0.0, y cada score se recorta a
      [0, 1] antes de promediar, para que un score fuera de rango (dato de
      RAG corrupto) nunca empuje la confianza fuera de [0, 1].
    - certeza_reglas = reglas_evaluadas / total_reglas, sumado sobre todas las
      `evaluaciones`, donde reglas_evaluadas = reglas_pasadas + reglas_falladas
      y total_reglas = reglas_evaluadas + reglas_no_evaluadas (contando reglas,
      no strings de razones — máximo 3 por evaluación: distancia, ambiente,
      material); 0.0 si no hay ninguna regla.

    debe_escalar es True cuando confianza < umbral_escalar (estrictamente
    menor). En el límite exacto (confianza == umbral_escalar) NO se escala.

    Lanza ValueError si algún peso es negativo o si la suma de pesos no es
    positiva (evita dividir por cero o invertir el sentido de la confianza).
    """
    pesos = pesos or PESOS_POR_DEFECTO

    if any(peso < 0 for peso in pesos.values()):
        raise ValueError(f"los pesos no pueden ser negativos: {pesos}")

    peso_total = sum(pesos.values())
    if peso_total <= 0:
        raise ValueError(
            f"la suma de los pesos debe ser mayor que cero, recibido: {peso_total}"
        )

    total_campos = len(CAMPOS_REQUERIDOS)
    presentes = total_campos - len(requisitos.campos_faltantes)
    completitud = presentes / total_campos

    if evidencia:
        scores = [min(1.0, max(0.0, item.get("score", 0.0))) for item in evidencia]
        top_scores = sorted(scores, reverse=True)[:top_n_evidencia]
        calidad_evidencia = sum(top_scores) / len(top_scores)
    else:
        calidad_evidencia = 0.0

    evaluadas = sum(ev.reglas_pasadas + ev.reglas_falladas for ev in evaluaciones)
    no_evaluadas = sum(ev.reglas_no_evaluadas for ev in evaluaciones)
    total_reglas = evaluadas + no_evaluadas
    certeza_reglas = evaluadas / total_reglas if total_reglas else 0.0

    desglose = {
        "completitud": completitud,
        "calidad_evidencia": calidad_evidencia,
        "certeza_reglas": certeza_reglas,
    }

    confianza = (
        sum(pesos[senal] * valor for senal, valor in desglose.items()) / peso_total
    )

    debe_escalar = confianza < umbral_escalar

    return ResultadoConfianza(
        confianza=confianza, desglose=desglose, debe_escalar=debe_escalar
    )
