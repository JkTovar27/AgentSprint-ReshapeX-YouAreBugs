class BaseAgent:
    """Clase base para futuros agentes del proyecto."""

    def __init__(self, name: str):
        self.name = name

    def run(self, prompt: str) -> str:
        return f"[{self.name}] Recibí: {prompt}"
