"""Registro de herramientas de function calling para el agente Sick.

Expone evaluar_familia y calcular_confianza (src/tools/reglas.py y
src/tools/confianza.py) con su schema JSON de parámetros, derivado en
tiempo de import de la firma real de cada función — así el schema nunca
se desincroniza si la firma cambia. El modelo solo puede pedir que se
llamen; el resultado siempre lo calcula código determinístico, nunca el
propio modelo.
"""

from __future__ import annotations

import inspect
from typing import Any, Callable, get_type_hints

from pydantic import TypeAdapter

from src.tools.confianza import calcular_confianza
from src.tools.reglas import evaluar_familia


def _schema_parametros(fn: Callable) -> dict:
    """Deriva un JSON Schema de objeto a partir de la firma real de fn."""
    firma = inspect.signature(fn)
    pistas = get_type_hints(fn)

    propiedades: dict[str, Any] = {}
    requeridos: list[str] = []
    for nombre, parametro in firma.parameters.items():
        anotacion = pistas.get(nombre, Any)
        propiedades[nombre] = TypeAdapter(anotacion).json_schema()
        if parametro.default is inspect.Parameter.empty:
            requeridos.append(nombre)

    return {"type": "object", "properties": propiedades, "required": requeridos}


def _declarar_herramienta(nombre: str, fn: Callable, descripcion: str) -> dict:
    return {
        "name": nombre,
        "description": descripcion,
        "parameters": _schema_parametros(fn),
    }


REGISTRY: dict[str, dict] = {
    "evaluar_familia": {
        "fn": evaluar_familia,
        "schema": _declarar_herramienta(
            "evaluar_familia",
            evaluar_familia,
            "Compara los requisitos del usuario contra las specs estructuradas "
            "de una familia de sensores Sick y devuelve un veredicto "
            "determinístico (viable, descartada o ambigua) con sus razones. "
            "Usar SIEMPRE antes de afirmar que un sensor es compatible; "
            "nunca afirmar compatibilidad sin pasar por esta función.",
        ),
    },
    "calcular_confianza": {
        "fn": calcular_confianza,
        "schema": _declarar_herramienta(
            "calcular_confianza",
            calcular_confianza,
            "Calcula un score de confianza 0-1 sobre un veredicto de "
            "evaluar_familia, combinando completitud del requisito, calidad "
            "de la evidencia RAG y certeza de las reglas evaluadas. Usar "
            "después de evaluar_familia para decidir si el caso debe "
            "escalarse a un humano (debe_escalar) en vez de responder solo.",
        ),
    },
}


def declaraciones() -> list[dict]:
    """Schemas listos para pasar al modelo como definiciones de tools."""
    return [entrada["schema"] for entrada in REGISTRY.values()]
