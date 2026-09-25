from __future__ import annotations

import json
import re
from typing import Any


def try_parse_json(text: str) -> dict[str, Any] | None:
    candidates = [text.strip()]
    fenced_match = re.search(r'```(?:json)?\s*(.*?)\s*```', text, flags=re.DOTALL | re.IGNORECASE)
    if fenced_match:
        candidates.append(fenced_match.group(1).strip())

    for candidate in candidates:
        if not candidate:
            continue
        try:
            parsed = json.loads(candidate)
            if isinstance(parsed, dict):
                return parsed
        except json.JSONDecodeError:
            continue

    return None


def normalize_message_content(content: Any) -> str:
    if content is None:
        return ''
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts: list[str] = []
        for item in content:
            if isinstance(item, dict):
                text = item.get('text') or item.get('content')
                if text:
                    parts.append(str(text))
            else:
                text = getattr(item, 'text', None)
                if text:
                    parts.append(str(text))
                else:
                    parts.append(str(item))
        return '\n'.join(parts)
    return str(content)
