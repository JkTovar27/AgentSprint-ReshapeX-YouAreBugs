import pytest

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
            reglas_pasadas=3,
            reglas_falladas=0,
            reglas_no_evaluadas=0,
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
            reglas_pasadas=3,
            reglas_falladas=0,
            reglas_no_evaluadas=0,
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
            reglas_pasadas=1,
            reglas_falladas=0,
            reglas_no_evaluadas=3,
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


def test_calidad_evidencia_usa_top_n_no_promedio_total():
    # Un fragmento muy relevante (1.0) y varios irrelevantes (0.3, 0.1, 0.05,
    # 0.05). El promedio total sería 0.3, pero el top-2 por defecto
    # (1.0 y 0.3) da 0.65: el ruido no debe diluir la mejor evidencia.
    evidencia = [
        {"fuente": "a.pdf", "texto": "...", "score": 1.0},
        {"fuente": "b.pdf", "texto": "...", "score": 0.3},
        {"fuente": "c.pdf", "texto": "...", "score": 0.1},
        {"fuente": "d.pdf", "texto": "...", "score": 0.05},
        {"fuente": "e.pdf", "texto": "...", "score": 0.05},
    ]

    resultado = calcular_confianza(requisitos_completos(), evidencia, evaluaciones=[])

    assert resultado.desglose["calidad_evidencia"] == pytest.approx(0.65)


def test_calidad_evidencia_con_menos_fragmentos_que_top_n():
    evidencia = [{"fuente": "a.pdf", "texto": "...", "score": 0.8}]

    resultado = calcular_confianza(
        requisitos_completos(), evidencia, evaluaciones=[], top_n_evidencia=2
    )

    assert resultado.desglose["calidad_evidencia"] == pytest.approx(0.8)


def test_calidad_evidencia_lista_vacia_es_cero():
    resultado = calcular_confianza(
        requisitos_completos(), evidencia=[], evaluaciones=[], top_n_evidencia=2
    )

    assert resultado.desglose["calidad_evidencia"] == 0.0


def test_evidencia_sin_clave_score_cuenta_como_cero():
    evidencia = [
        {"fuente": "a.pdf", "texto": "..."},  # sin "score"
        {"fuente": "b.pdf", "texto": "...", "score": 0.8},
    ]

    resultado = calcular_confianza(requisitos_completos(), evidencia, evaluaciones=[])

    # top-2 por defecto: [0.8, 0.0] -> promedio 0.4
    assert resultado.desglose["calidad_evidencia"] == pytest.approx(0.4)


def test_scores_fuera_de_rango_se_recortan_a_0_1():
    evidencia = [
        {"fuente": "a.pdf", "texto": "...", "score": 5.0},
        {"fuente": "b.pdf", "texto": "...", "score": -2.0},
    ]

    resultado = calcular_confianza(requisitos_completos(), evidencia, evaluaciones=[])

    # ambos scores se recortan a [0, 1] antes de promediar: (1.0 + 0.0) / 2
    assert resultado.desglose["calidad_evidencia"] == pytest.approx(0.5)
    assert 0.0 <= resultado.confianza <= 1.0


def test_peso_negativo_lanza_value_error():
    pesos = {"completitud": -0.5, "calidad_evidencia": 0.5, "certeza_reglas": 1.0}

    with pytest.raises(ValueError, match="negativos"):
        calcular_confianza(
            requisitos_completos(), evidencia=[], evaluaciones=[], pesos=pesos
        )


def test_peso_total_cero_lanza_value_error():
    pesos = {"completitud": 0.0, "calidad_evidencia": 0.0, "certeza_reglas": 0.0}

    with pytest.raises(ValueError, match="mayor que cero"):
        calcular_confianza(
            requisitos_completos(), evidencia=[], evaluaciones=[], pesos=pesos
        )
