import json

from src.tools.reglas import ResultadoEvaluacion
from src.tools.registry import REGISTRY, declaraciones
from src.tools.requisitos import RequisitosSensor


def requisitos_completos() -> RequisitosSensor:
    return RequisitosSensor(
        tipo_objeto="caja de cartón",
        distancia_mm=10,
        ambiente=["polvo"],
        material_superficie="opaco",
        montaje="fijo",
    )


def test_declaraciones_son_json_valido():
    declaraciones_json = json.dumps(declaraciones())
    reconstruido = json.loads(declaraciones_json)

    assert reconstruido == declaraciones()
    assert len(reconstruido) == len(REGISTRY)


def test_declaraciones_tienen_name_description_y_parameters():
    for schema in declaraciones():
        assert set(schema.keys()) == {"name", "description", "parameters"}
        assert schema["name"] in REGISTRY
        assert isinstance(schema["description"], str) and schema["description"]
        assert schema["parameters"]["type"] == "object"
        assert "properties" in schema["parameters"]
        assert "required" in schema["parameters"]


def test_evaluar_familia_empuja_su_uso_en_la_descripcion():
    descripcion = REGISTRY["evaluar_familia"]["schema"]["description"]
    assert "Usar SIEMPRE antes de afirmar que un sensor es compatible" in descripcion
    assert "nunca afirmar compatibilidad sin pasar por esta función" in descripcion


def test_schema_evaluar_familia_refleja_su_firma_real():
    parametros = REGISTRY["evaluar_familia"]["schema"]["parameters"]
    assert set(parametros["properties"]) == {"requisitos", "candidato"}
    assert set(parametros["required"]) == {"requisitos", "candidato"}


def test_schema_calcular_confianza_refleja_su_firma_real():
    parametros = REGISTRY["calcular_confianza"]["schema"]["parameters"]
    assert set(parametros["properties"]) == {
        "requisitos",
        "evidencia",
        "evaluaciones",
        "pesos",
        "umbral_escalar",
    }
    # pesos y umbral_escalar tienen default en la firma -> no son requeridos.
    assert set(parametros["required"]) == {"requisitos", "evidencia", "evaluaciones"}


def test_cada_nombre_del_registry_es_invocable():
    fn_evaluar = REGISTRY["evaluar_familia"]["fn"]
    resultado_evaluar = fn_evaluar(
        requisitos_completos(),
        {
            "familia": "WTB4S",
            "fuente": "sick_wtb4s.pdf",
            "rango_distancia_mm": [3, 15],
            "ambientes_soportados": ["polvo", "exterior"],
            "materiales_soportados": ["opaco", "translucido"],
            "montajes": ["fijo", "movil"],
        },
    )
    assert isinstance(resultado_evaluar, ResultadoEvaluacion)
    assert resultado_evaluar.veredicto in {"viable", "descartada", "ambigua"}

    fn_confianza = REGISTRY["calcular_confianza"]["fn"]
    resultado_confianza = fn_confianza(
        requisitos_completos(),
        [{"fuente": "a.pdf", "texto": "...", "score": 0.9}],
        [resultado_evaluar],
    )
    assert 0.0 <= resultado_confianza.confianza <= 1.0
