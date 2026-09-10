import unittest
from unittest.mock import AsyncMock

from app.llm_client import LLMClient


class EthicsPromptTests(unittest.IsolatedAsyncioTestCase):
    async def test_attached_file_makes_file_only_request_validatable(self):
        client = LLMClient.__new__(LLMClient)
        client._ask_json_classifier = AsyncMock(
            return_value='{"valid_for_quiz": true, "reason_code": "ok"}'
        )

        result = await client.validate_quiz_request(
            "Создай вопросы по файлику", has_material=True
        )

        system = client._ask_json_classifier.await_args.kwargs["system"]
        self.assertTrue(result["valid_for_quiz"])
        self.assertIn("прикреплён учебный файл", system)
        self.assertIn("не делает его непригодным", system)

    async def test_without_file_topic_is_still_required(self):
        client = LLMClient.__new__(LLMClient)
        client._ask_json_classifier = AsyncMock(
            return_value='{"valid_for_quiz": false, "reason_code": "missing_topic"}'
        )

        result = await client.validate_quiz_request(
            "Создай вопросы по файлику", has_material=False
        )

        system = client._ask_json_classifier.await_args.kwargs["system"]
        self.assertFalse(result["valid_for_quiz"])
        self.assertIn("учебный файл не прикреплён", system)

    async def test_unsafe_request_is_rejected_even_with_file(self):
        client = LLMClient.__new__(LLMClient)
        client.moderate_topic = AsyncMock(return_value={"unsafe": True})
        client.validate_quiz_request = AsyncMock()

        self.assertTrue(await client.check_prompt_ethics("unsafe", has_material=True))
        client.validate_quiz_request.assert_not_awaited()

    async def test_long_material_is_rechecked_in_chunks_after_whole_text_false_positive(self):
        client = LLMClient.__new__(LLMClient)
        client.moderate_topic = AsyncMock(
            side_effect=[{"unsafe": True}, {"unsafe": False}, {"unsafe": False}, {"unsafe": False}]
        )

        self.assertFalse(await client.check_material_safety("а" * 12_000))
        self.assertEqual(client.moderate_topic.await_count, 4)

    async def test_unsafe_chunk_still_rejects_long_material(self):
        client = LLMClient.__new__(LLMClient)
        client.moderate_topic = AsyncMock(
            side_effect=[{"unsafe": True}, {"unsafe": False}, {"unsafe": False}, {"unsafe": True}]
        )

        self.assertTrue(await client.check_material_safety("а" * 12_000))

    async def test_classifier_prompt_avoids_common_technical_word_false_positives(self):
        client = LLMClient.__new__(LLMClient)
        client._ask_json_classifier = AsyncMock(return_value='{"unsafe": false}')

        await client.moderate_topic("Лекция по линейной регрессии", material_mode=True)
        user = client._ask_json_classifier.await_args.kwargs["user"]
        self.assertIn("опорных", user)
        self.assertIn("государственной политики", user)

    async def test_short_benign_topics_are_explicitly_valid(self):
        client = LLMClient.__new__(LLMClient)
        client._ask_json_classifier = AsyncMock(
            return_value='{"valid_for_quiz": true, "reason_code": "ok"}'
        )

        result = await client.validate_quiz_request("мемы", has_material=False)
        user = client._ask_json_classifier.await_args.kwargs["user"]

        self.assertTrue(result["valid_for_quiz"])
        self.assertIn("«мемы»", user)
        self.assertIn("Не отклоняй такой запрос как too_ambiguous", user)

    async def test_benign_memes_are_explicitly_safe(self):
        client = LLMClient.__new__(LLMClient)
        client._ask_json_classifier = AsyncMock(return_value='{"unsafe": false}')

        await client.moderate_topic("мемы")
        user = client._ask_json_classifier.await_args.kwargs["user"]

        self.assertIn("интернет-мемах", user)
        self.assertIn("'мемы'", user)


if __name__ == "__main__":
    unittest.main()
