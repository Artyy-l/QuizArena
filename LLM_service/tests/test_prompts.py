import unittest

from app.prompts import SYSTEM_PROMPT, build_user_prompt
from app.schemas import GenerationRequest, QuestionType


class PromptTests(unittest.TestCase):
    def test_only_requested_question_type_is_required(self):
        request = GenerationRequest(
            topic="История России",
            number=3,
            question_types=[QuestionType.q100k1],
        )

        prompt = build_user_prompt(request)

        self.assertIn("- 100k1:", prompt)
        self.assertNotIn("- single_choice:", prompt)
        self.assertIn('у каждого вопроса type должен быть "100k1"', prompt)
        self.assertIn("Трём неподходящим вариантам назначь nominal: 0", prompt)

    def test_extracted_material_is_primary_source(self):
        request = GenerationRequest(
            topic=(
                "Уточнение пользователя:\nГлава 2\n\n"
                "Учебный материал для генерации квиза:\n"
                "--- Файл: lesson.pdf ---\nВажный текст"
            ),
            number=2,
            has_material=True,
        )

        prompt = build_user_prompt(request)

        self.assertIn("Считай его главным источником фактов", prompt)
        self.assertIn("Если текста мало, укажи ограничение в warnings", prompt)
        self.assertIn("source_reference", prompt)

    def test_material_marker_in_user_topic_does_not_fake_an_attachment(self):
        request = GenerationRequest(
            topic="Расскажи, что значит фраза Учебный материал для генерации квиза:",
            number=1,
            has_material=False,
        )

        prompt = build_user_prompt(request)

        self.assertIn("Извлечённый материал не передан", prompt)
        self.assertIn("Неправильные варианты должны быть правдоподобными", SYSTEM_PROMPT)

    def test_formulas_and_explanations_are_specified(self):
        request = GenerationRequest(topic="Алгебра", number=2)

        prompt = build_user_prompt(request)

        self.assertIn(r"\(A=B\)", prompt)
        self.assertIn("не ссылайся на служебные id или порядок вариантов", prompt)
        self.assertIn("LaTeX-разделители", SYSTEM_PROMPT)


if __name__ == "__main__":
    unittest.main()
