from src.agents.base_agent import BaseAgent


def test_base_agent_returns_prompt_response():
    agent = BaseAgent("demo")
    response = agent.run("hola")
    assert "demo" in response
    assert "hola" in response
