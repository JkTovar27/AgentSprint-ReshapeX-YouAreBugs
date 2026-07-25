import pytest
from pydantic import ValidationError

from src.tools.requisitos import RequisitosSensor


def test_requisitos_completos_no_tiene_campos_faltantes():
    req = RequisitosSensor(
        tipo_objeto="caja de cartón",
        distancia_mm=10,
        ambiente=["polvo", "exterior"],
        material_superficie="opaco",
        montaje="fijo",
    )
    assert req.campos_faltantes == []
    assert req.esta_completo is True


def test_requisitos_parciales_reporta_campos_faltantes():
    req = RequisitosSensor(tipo_objeto="botella", distancia_mm=50)
    assert req.esta_completo is False
    assert set(req.campos_faltantes) == {"ambiente", "material_superficie", "montaje"}


def test_requisitos_vacios_reporta_todos_los_campos():
    req = RequisitosSensor()
    assert set(req.campos_faltantes) == set(
        ("tipo_objeto", "distancia_mm", "ambiente", "material_superficie", "montaje")
    )


def test_ambiente_acepta_string_unico_y_lo_normaliza_a_lista():
    req = RequisitosSensor(
        tipo_objeto="pieza",
        distancia_mm=5,
        ambiente="vibracion",
        material_superficie="metalico",
        montaje="brazo_robotico",
    )
    assert req.ambiente == ["vibracion"]


def test_ambiente_elimina_duplicados():
    req = RequisitosSensor(ambiente=["polvo", "polvo", "exterior"])
    assert req.ambiente == ["exterior", "polvo"]


def test_ambiente_valor_invalido_lanza_error():
    with pytest.raises(ValidationError):
        RequisitosSensor(ambiente=["humedad"])


def test_ambiente_none_explicito_lanza_error():
    # ambiente=None no se coerciona silenciosamente a lista vacía: el
    # validador "antes" solo envuelve strings sueltos, y None no encaja en
    # list[AmbienteCondicion] -> Pydantic falla fuerte (fail loud), no falla
    # silencioso.
    with pytest.raises(ValidationError):
        RequisitosSensor(ambiente=None)


def test_distancia_mm_no_positiva_lanza_error():
    with pytest.raises(ValidationError):
        RequisitosSensor(distancia_mm=0)
    with pytest.raises(ValidationError):
        RequisitosSensor(distancia_mm=-5)


def test_material_superficie_invalido_lanza_error():
    with pytest.raises(ValidationError):
        RequisitosSensor(material_superficie="madera")


def test_montaje_invalido_lanza_error():
    with pytest.raises(ValidationError):
        RequisitosSensor(montaje="colgante")


def test_tipo_objeto_vacio_lanza_error():
    with pytest.raises(ValidationError):
        RequisitosSensor(tipo_objeto="   ")


def test_campos_faltantes_pasado_manualmente_es_recalculado():
    req = RequisitosSensor(
        tipo_objeto="caja",
        distancia_mm=10,
        ambiente=["polvo"],
        material_superficie="opaco",
        montaje="fijo",
        campos_faltantes=["esto_debe_ser_ignorado"],
    )
    assert req.campos_faltantes == []
