from fastapi import FastAPI

app = FastAPI(title="AgentSprint ReshapeX YouAreBugs")

@app.get("/")
def read_root():
    return {"message": "Proyecto base listo para desarrollar agentes y RAG"}
