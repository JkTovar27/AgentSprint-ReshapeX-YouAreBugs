.PHONY: setup run test

setup:
	python3 -m venv .venv
	. .venv/bin/activate && pip install -r requirements.txt

run:
	. .venv/bin/activate && uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

test:
	. .venv/bin/activate && pytest -q
