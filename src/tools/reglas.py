"""Validación determinística de compatibilidad requisito vs. familia de sensor.

Sin LLM: compara número contra rango y categoría contra lista de categorías
soportadas. El modelo decide CUÁNDO llamar a evaluar_familia, pero el
veredicto siempre lo calcula este código — nunca el modelo.
"""

from __future__ import annotations

from dataclasses import dataclass, field

from src.tools.requisitos import RequisitosSensor

CLAVES_CANDIDATO = frozenset(
    {"rango_distancia_mm", "ambientes_soportados", "materiales_soportados"}
)


@dataclass
class ResultadoEvaluacion:
    """Resultado de evaluar_familia."""

    veredicto: str  # "viable" | "descartada" | "ambigua"
    razones: list[str] = field(default_factory=list)
    reglas_no_evaluables: list[str] = field(default_factory=list)
    reglas_pasadas: int = 0
    reglas_falladas: int = 0
    reglas_no_evaluadas: int = 0
    claves_desconocidas: list[str] = field(default_factory=list)


def evaluar_familia(requisitos: RequisitosSensor, candidato: dict) -> ResultadoEvaluacion:
    """Evalúa si una familia de sensores es compatible con los requisitos.

    Reglas evaluadas: distancia_mm dentro de rango_distancia_mm, cada
    condición de ambiente dentro de ambientes_soportados, y
    material_superficie dentro de materiales_soportados.

    Una regla es "no evaluable" cuando el dato falta en requisitos
    (campos_faltantes), el candidato no publica esa spec, o el candidato
    publica un dato mal formado (rango_distancia_mm con forma inesperada o
    invertido) — un dato de catálogo corrupto nunca descarta el sensor por
    sí solo. Si alguna regla falla, el veredicto es siempre "descartada"
    (aunque haya no evaluables). Si ninguna falla pero queda al menos una
    no evaluable, el veredicto es "ambigua" — nunca "viable" por defecto.

    Cualquier clave de `candidato` que no esté en CLAVES_CANDIDATO se reporta
    en `claves_desconocidas` (p. ej. "rango_distancia" en vez de
    "rango_distancia_mm") — así una clave mal nombrada en el dato del RAG se
    distingue de una spec que el candidato genuinamente no publica.
    """
    familia = candidato.get("familia", "el candidato")
    razones: list[str] = []
    no_evaluables: list[str] = []
    hay_fallo = False
    reglas_pasadas = 0
    reglas_falladas = 0
    reglas_no_evaluadas = 0
    claves_desconocidas = [
        f"clave desconocida en candidato: '{clave}' (no coincide con ninguna "
        f"spec canónica: {sorted(CLAVES_CANDIDATO)})"
        for clave in sorted(candidato)
        if clave not in CLAVES_CANDIDATO
    ]

    # Regla 1: distancia_mm dentro de rango_distancia_mm
    rango_distancia = candidato.get("rango_distancia_mm")
    if "distancia_mm" in requisitos.campos_faltantes:
        no_evaluables.append(
            "distancia_mm: el requisito no especifica la distancia de detección"
        )
        reglas_no_evaluadas += 1
    elif rango_distancia is None:
        no_evaluables.append(f"distancia_mm: {familia} no publica rango_distancia_mm")
        reglas_no_evaluadas += 1
    elif not (
        isinstance(rango_distancia, (list, tuple))
        and len(rango_distancia) == 2
        and all(
            isinstance(x, (int, float)) and not isinstance(x, bool)
            for x in rango_distancia
        )
    ):
        no_evaluables.append(
            f"distancia_mm: {familia} publica rango_distancia_mm en formato "
            f"inválido: {rango_distancia!r}"
        )
        reglas_no_evaluadas += 1
    else:
        minimo, maximo = rango_distancia
        if minimo > maximo:
            no_evaluables.append(
                f"distancia_mm: rango invertido en el dato del candidato "
                f"[{minimo}, {maximo}]"
            )
            reglas_no_evaluadas += 1
        else:
            distancia = requisitos.distancia_mm
            if minimo <= distancia <= maximo:
                razones.append(
                    f"distancia_mm: {distancia} mm está dentro del rango soportado "
                    f"[{minimo}, {maximo}] mm de {familia}"
                )
                reglas_pasadas += 1
            else:
                razones.append(
                    f"distancia_mm: {distancia} mm está FUERA del rango soportado "
                    f"[{minimo}, {maximo}] mm de {familia}"
                )
                hay_fallo = True
                reglas_falladas += 1

    # Regla 2: cada condición de ambiente del requisito debe estar soportada
    if "ambiente" in requisitos.campos_faltantes:
        no_evaluables.append(
            "ambiente: el requisito no especifica condiciones de ambiente"
        )
        reglas_no_evaluadas += 1
    elif candidato.get("ambientes_soportados") is None:
        no_evaluables.append(f"ambiente: {familia} no publica ambientes_soportados")
        reglas_no_evaluadas += 1
    else:
        soportados = candidato["ambientes_soportados"]
        ambiente_fallo = False
        for condicion in requisitos.ambiente:
            if condicion in soportados:
                razones.append(f"ambiente '{condicion}': soportado por {familia}")
            else:
                razones.append(
                    f"ambiente '{condicion}': NO soportado por {familia} "
                    f"(soporta {soportados})"
                )
                hay_fallo = True
                ambiente_fallo = True
        if ambiente_fallo:
            reglas_falladas += 1
        else:
            reglas_pasadas += 1

    # Regla 3: material_superficie soportado
    if "material_superficie" in requisitos.campos_faltantes:
        no_evaluables.append(
            "material_superficie: el requisito no especifica el material"
        )
        reglas_no_evaluadas += 1
    elif candidato.get("materiales_soportados") is None:
        no_evaluables.append(
            f"material_superficie: {familia} no publica materiales_soportados"
        )
        reglas_no_evaluadas += 1
    else:
        material = requisitos.material_superficie
        soportados = candidato["materiales_soportados"]
        if material in soportados:
            razones.append(f"material_superficie '{material}': soportado por {familia}")
            reglas_pasadas += 1
        else:
            razones.append(
                f"material_superficie '{material}': NO soportado por {familia} "
                f"(soporta {soportados})"
            )
            hay_fallo = True
            reglas_falladas += 1

    if hay_fallo:
        veredicto = "descartada"
    elif no_evaluables:
        veredicto = "ambigua"
    else:
        veredicto = "viable"

    return ResultadoEvaluacion(
        veredicto=veredicto,
        razones=razones,
        reglas_no_evaluables=no_evaluables,
        reglas_pasadas=reglas_pasadas,
        reglas_falladas=reglas_falladas,
        reglas_no_evaluadas=reglas_no_evaluadas,
        claves_desconocidas=claves_desconocidas,
    )
