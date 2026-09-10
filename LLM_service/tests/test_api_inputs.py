import unittest
from unittest.mock import AsyncMock, patch

from fastapi import HTTPException

from app.main import _parse_question_types, check_ethics
from app.schemas import QuestionType


class QuestionTypeInputTests(unittest.TestCase):
    def test_aliases_are_accepted(self):
        self.assertEqual(
            _parse_question_types("q100k1,hundred_to_one,100k1"),
            [QuestionType.q100k1] * 3,
        )

    def test_unknown_type_is_rejected(self):
        with self.assertRaises(HTTPException) as error:
            _parse_question_types("unknown")
        self.assertEqual(error.exception.status_code, 422)


class EthicsApiTests(unittest.IsolatedAsyncioTestCase):
    async def test_classifier_failure_does_not_approve_request(self):
        classifier = AsyncMock()
        classifier.check_prompt_ethics.side_effect = RuntimeError("provider unavailable")
        with patch("app.main.job_manager._get_client", return_value=classifier):
            with self.assertRaises(HTTPException) as error:
                await check_ethics("Тема")
        self.assertEqual(error.exception.status_code, 503)


if __name__ == "__main__":
    unittest.main()
