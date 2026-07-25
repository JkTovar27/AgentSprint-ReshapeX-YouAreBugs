import json
import logging
import re
from typing import Optional, TypeVar, Type
from openai import OpenAI
from pydantic import BaseModel
from .config import OPENROUTER_API_KEY, OPENROUTER_BASE_URL, OPENROUTER_MODEL

logger = logging.getLogger(__name__)

T = TypeVar("T", bound=BaseModel)

_client: Optional[OpenAI] = None


def get_client() -> OpenAI:
    global _client
    if _client is None:
        _client = OpenAI(
            api_key=OPENROUTER_API_KEY,
            base_url=OPENROUTER_BASE_URL,
        )
    return _client


def extract_json(text: str) -> str:
    text = text.strip()
    if not text:
        return "{}"

    text = re.sub(r"^```(?:json)?\s*", "", text)
    text = re.sub(r"\s*```$", "", text)
    text = text.strip()

    if not text:
        return "{}"

    return text


def call_llm(
    system_prompt: str,
    user_message: str,
    model: Optional[str] = None,
    temperature: float = 0.1,
    max_tokens: int = 2048,
) -> str:
    client = get_client()
    resp = client.chat.completions.create(
        model=model or OPENROUTER_MODEL,
        temperature=temperature,
        max_tokens=max_tokens,
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_message},
        ],
    )
    content = resp.choices[0].message.content or ""
    logger.debug(f"LLM response ({len(content)} chars): {content[:200]}...")
    return content


def call_llm_structured(
    system_prompt: str,
    user_message: str,
    response_model: Type[T],
    model: Optional[str] = None,
    temperature: float = 0.1,
    max_tokens: int = 2048,
    retries: int = 1,
) -> T:
    schema_str = json.dumps(response_model.model_json_schema(), indent=2, ensure_ascii=False)
    prompt = (
        f"{system_prompt}\n\n"
        f"Responde ÚNICAMENTE con JSON válido que cumpla este schema:\n"
        f"{schema_str}\n\n"
        f"NO incluyas markdown, explicaciones ni texto adicional."
    )

    for attempt in range(retries + 1):
        raw = call_llm(prompt, user_message, model, temperature, max_tokens)
        cleaned = extract_json(raw)
        try:
            data = json.loads(cleaned)
            return response_model(**data)
        except (json.JSONDecodeError, Exception) as e:
            logger.warning(
                f"JSON parse error (attempt {attempt + 1}/{retries + 1}): {e}, "
                f"raw[:200]: {raw[:200]!r}"
            )
            if attempt == retries:
                return response_model(**{})
    return response_model(**{})
