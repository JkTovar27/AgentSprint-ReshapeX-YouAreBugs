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
) -> ResultadoConfianza:
    """Combina completitud, calidad de evidencia y certeza de reglas.

    - completitud = campos presentes / campos totales del schema de requisitos.
    - calidad_evidencia = promedio de score en `evidencia`; 0.0 si está vacía.
    - certeza_reglas = reglas evaluadas / (evaluadas + no_evaluables),
      sumado sobre todas las `evaluaciones`; 0.0 si no hay ninguna regla.

    debe_escalar es True cuando confianza < umbral_escalar (estrictamente
    menor). En el límite exacto (confianza == umbral_escalar) NO se escala.
    """
    pesos = pesos or PESOS_POR_DEFECTO
    peso_total = sum(pesos.values())

    total_campos = len(CAMPOS_REQUERIDOS)
    presentes = total_campos - len(requisitos.campos_faltantes)
    completitud = presentes / total_campos

    if evidencia:
        calidad_evidencia = sum(item["score"] for item in evidencia) / len(evidencia)
    else:
        calidad_evidencia = 0.0

    evaluadas = sum(len(ev.razones) for ev in evaluaciones)
    no_evaluables = sum(len(ev.reglas_no_evaluables) for ev in evaluaciones)
    total_reglas = evaluadas + no_evaluables
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
