"""Schema de requisitos que SICK Select Copilot recopila del usuario.

Este schema representa el lado "requisito" del contrato de specs descrito
en la skill tools-validation: campos que después alimentan a
comparar_specs(requisito, candidato). Un campo faltante nunca se rellena
con un valor por defecto "razonable" — se registra en campos_faltantes
para que el flujo pueda pedirlo o devolver un veredicto "ambiguo".
"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field, field_validator, model_validator

AmbienteCondicion = Literal["polvo", "vibracion", "exterior"]
MaterialSuperficie = Literal[
    "opaco", "translucido", "transparente", "metalico", "textil", "liquido"
]
Montaje = Literal["fijo", "movil", "brazo_robotico", "banda_transportadora"]

CAMPOS_REQUERIDOS = (
    "tipo_objeto",
    "distancia_mm",
    "ambiente",
    "material_superficie",
    "montaje",
)


class RequisitosSensor(BaseModel):
    """Requisitos técnicos capturados antes de buscar un sensor compatible."""

    tipo_objeto: str | None = None
    distancia_mm: float | None = Field(default=None, gt=0)
    ambiente: list[AmbienteCondicion] = Field(default_factory=list)
    material_superficie: MaterialSuperficie | None = None
    montaje: Montaje | None = None
    campos_faltantes: list[str] = Field(default_factory=list)

    @field_validator("tipo_objeto")
    @classmethod
    def tipo_objeto_no_vacio(cls, v: str | None) -> str | None:
        if v is not None and not v.strip():
            raise ValueError("tipo_objeto no puede ser una cadena vacía")
        return v

    @field_validator("ambiente", mode="before")
    @classmethod
    def ambiente_como_lista(cls, v):
        if isinstance(v, str):
            return [v]
        return v

    @field_validator("ambiente")
    @classmethod
    def ambiente_sin_duplicados(cls, v: list[str]) -> list[str]:
        return sorted(set(v))

    @model_validator(mode="after")
    def calcular_campos_faltantes(self) -> "RequisitosSensor":
        faltantes = [
            campo
            for campo in CAMPOS_REQUERIDOS
            if not getattr(self, campo)
        ]
        self.campos_faltantes = faltantes
        return self

    @property
    def esta_completo(self) -> bool:
        return not self.campos_faltantes
