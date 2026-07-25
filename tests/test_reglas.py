from src.tools.reglas import evaluar_familia
from src.tools.requisitos import RequisitosSensor


def candidato_wtb4s(**overrides) -> dict:
    base = {
        "familia": "WTB4S",
        "fuente": "sick_wtb4s.pdf",
        "rango_distancia_mm": [3, 15],
        "ambientes_soportados": ["polvo", "exterior"],
        "materiales_soportados": ["opaco", "translucido"],
        "montajes": ["fijo", "movil"],
    }
    base.update(overrides)
    return base


def requisitos_completos(**overrides) -> RequisitosSensor:
    datos = dict(
        tipo_objeto="caja de cartón",
        distancia_mm=10,
        ambiente=["polvo"],
        material_superficie="opaco",
        montaje="fijo",
    )
    datos.update(overrides)
    return RequisitosSensor(**datos)


def test_viable_cuando_todo_esta_dentro_de_rango_y_soportado():
    resultado = evaluar_familia(requisitos_completos(), candidato_wtb4s())

    assert resultado.veredicto == "viable"
    assert resultado.reglas_no_evaluables == []
    assert len(resultado.razones) == 3
    assert any("distancia_mm" in r for r in resultado.razones)
    assert any("ambiente 'polvo'" in r for r in resultado.razones)
    assert any("material_superficie 'opaco'" in r for r in resultado.razones)
    assert resultado.reglas_pasadas == 3
    assert resultado.reglas_falladas == 0
    assert resultado.reglas_no_evaluadas == 0


def test_descartada_por_distancia_fuera_de_rango():
    req = requisitos_completos(distancia_mm=100)

    resultado = evaluar_familia(req, candidato_wtb4s())

    assert resultado.veredicto == "descartada"
    assert any(
        "distancia_mm" in r and "FUERA del rango" in r for r in resultado.razones
    )
    assert resultado.reglas_pasadas == 2
    assert resultado.reglas_falladas == 1
    assert resultado.reglas_no_evaluadas == 0


def test_descartada_por_ambiente_no_soportado():
    req = requisitos_completos(ambiente=["vibracion"])

    resultado = evaluar_familia(req, candidato_wtb4s())

    assert resultado.veredicto == "descartada"
    assert any(
        "ambiente 'vibracion'" in r and "NO soportado" in r for r in resultado.razones
    )
    assert resultado.reglas_pasadas == 2
    assert resultado.reglas_falladas == 1
    assert resultado.reglas_no_evaluadas == 0


def test_descartada_por_material_no_soportado():
    req = requisitos_completos(material_superficie="metalico")

    resultado = evaluar_familia(req, candidato_wtb4s())

    assert resultado.veredicto == "descartada"
    assert any(
        "material_superficie 'metalico'" in r and "NO soportado" in r
        for r in resultado.razones
    )
    assert resultado.reglas_pasadas == 2
    assert resultado.reglas_falladas == 1
    assert resultado.reglas_no_evaluadas == 0


def test_ambigua_por_requisito_incompleto():
    req = RequisitosSensor(
        tipo_objeto="caja de cartón",
        distancia_mm=10,
        ambiente=["polvo"],
        montaje="fijo",
    )  # falta material_superficie

    resultado = evaluar_familia(req, candidato_wtb4s())

    assert resultado.veredicto == "ambigua"
    assert any("material_superficie" in r for r in resultado.reglas_no_evaluables)
    assert not any("NO soportado" in r for r in resultado.razones)
    assert resultado.reglas_pasadas == 2
    assert resultado.reglas_falladas == 0
    assert resultado.reglas_no_evaluadas == 1


def test_ambigua_por_spec_ausente_en_candidato():
    candidato = candidato_wtb4s()
    del candidato["materiales_soportados"]

    resultado = evaluar_familia(requisitos_completos(), candidato)

    assert resultado.veredicto == "ambigua"
    assert any(
        "material_superficie" in r and "no publica materiales_soportados" in r
        for r in resultado.reglas_no_evaluables
    )
    assert resultado.reglas_pasadas == 2
    assert resultado.reglas_falladas == 0
    assert resultado.reglas_no_evaluadas == 1


def test_fallo_con_no_evaluables_es_descartada_no_ambigua():
    candidato = candidato_wtb4s()
    del candidato["materiales_soportados"]
    req = requisitos_completos(distancia_mm=100)

    resultado = evaluar_familia(req, candidato)

    assert resultado.veredicto == "descartada"
    assert resultado.reglas_no_evaluables != []
    assert resultado.reglas_pasadas == 1
    assert resultado.reglas_falladas == 1
    assert resultado.reglas_no_evaluadas == 1
