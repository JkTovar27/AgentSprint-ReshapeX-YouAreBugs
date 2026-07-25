from src.tools.confianza import calcular_confianza
from src.tools.reglas import ResultadoEvaluacion
from src.tools.requisitos import RequisitosSensor


def requisitos_completos() -> RequisitosSensor:
    return RequisitosSensor(
        tipo_objeto="caja de cartón",
        distancia_mm=10,
        ambiente=["polvo"],
        material_superficie="opaco",
        montaje="fijo",
    )


def test_caso_alto_confianza_no_escala():
    evidencia = [
        {"fuente": "a.pdf", "texto": "...", "score": 0.9},
        {"fuente": "b.pdf", "texto": "...", "score": 0.95},
        {"fuente": "c.pdf", "texto": "...", "score": 0.85},
    ]
    evaluaciones = [
        ResultadoEvaluacion(
            veredicto="viable",
            razones=["distancia_mm ok", "ambiente ok", "material ok"],
            reglas_no_evaluables=[],
        )
    ]

    resultado = calcular_confianza(requisitos_completos(), evidencia, evaluaciones)

    assert resultado.desglose["completitud"] == 1.0
    assert resultado.desglose["certeza_reglas"] == 1.0
    assert resultado.confianza > 0.6
    assert resultado.debe_escalar is False


def test_caso_bajo_por_evidencia_vacia():
    evaluaciones = [
        ResultadoEvaluacion(
            veredicto="viable",
            razones=["distancia_mm ok", "ambiente ok", "material ok"],
            reglas_no_evaluables=[],
        )
    ]
    pesos = {"completitud": 0.2, "calidad_evidencia": 0.6, "certeza_reglas": 0.2}

    resultado = calcular_confianza(
        requisitos_completos(), evidencia=[], evaluaciones=evaluaciones, pesos=pesos
    )

    assert resultado.desglose["calidad_evidencia"] == 0.0
    assert resultado.confianza < 0.6
    assert resultado.debe_escalar is True


def test_caso_bajo_por_muchas_reglas_no_evaluables():
    evaluaciones = [
        ResultadoEvaluacion(
            veredicto="ambigua",
            razones=["distancia_mm ok"],
            reglas_no_evaluables=[
                "ambiente no evaluable",
                "material no evaluable",
                "montaje no evaluable",
            ],
        )
    ]
    pesos = {"completitud": 0.2, "calidad_evidencia": 0.2, "certeza_reglas": 0.6}
    evidencia = [{"fuente": "a.pdf", "texto": "...", "score": 1.0}]

    resultado = calcular_confianza(
        requisitos_completos(), evidencia=evidencia, evaluaciones=evaluaciones, pesos=pesos
    )

    assert resultado.desglose["certeza_reglas"] == 0.25
    assert resultado.confianza < 0.6
    assert resultado.debe_escalar is True


def test_debe_escalar_respeta_umbral_en_limite_exacto():
    # 3 de 5 campos presentes -> completitud == 3/5 == 0.6 exacto en float.
    req = RequisitosSensor(
        tipo_objeto="caja",
        distancia_mm=10,
        ambiente=["polvo"],
    )  # faltan material_superficie y montaje
    pesos = {"completitud": 1.0, "calidad_evidencia": 0.0, "certeza_reglas": 0.0}

    resultado = calcular_confianza(req, evidencia=[], evaluaciones=[], pesos=pesos)

    assert resultado.confianza == 0.6
    assert resultado.debe_escalar is False

    resultado_bajo_umbral = calcular_confianza(
        req, evidencia=[], evaluaciones=[], pesos=pesos, umbral_escalar=0.60001
    )
    assert resultado_bajo_umbral.debe_escalar is True
