package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.ml.MlJobStateDTO;
import org.example.dto.ml.MlQuestionDTO;
import org.example.metrics.MetricsService;
import org.example.model.QuestionType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class FastApiClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_TOPIC = "учебный материал";

    private final String checkEthicsBaseUrl;
    private final String generateUrl;
    private final String jobsGenerateUrl;
    private final String jobsBaseUrl;
    private final HttpClient httpClient;
    private final MetricsService metricsService;

    public FastApiClient(
            @Value("${quizarena.ml.base-url:http://127.0.0.1:8000}") String mlBaseUrl,
            MetricsService metricsService
    ) {
        String normalizedBase = mlBaseUrl.endsWith("/") ? mlBaseUrl.substring(0, mlBaseUrl.length() - 1) : mlBaseUrl;
        this.checkEthicsBaseUrl = normalizedBase + "/check-ethics/";
        this.generateUrl = normalizedBase + "/generate";
        this.jobsGenerateUrl = normalizedBase + "/jobs/generate";
        this.jobsBaseUrl = normalizedBase + "/jobs/";
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.metricsService = metricsService;
    }

    public boolean checkPromptEthics(String prompt) throws IOException, InterruptedException {
        String encodedPrompt = URLEncoder.encode(prompt, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(checkEthicsBaseUrl + encodedPrompt))
                .GET()
                .build();

        long ethicsStart = System.nanoTime();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        metricsService.getFastApiEthicsTimer().record(System.nanoTime() - ethicsStart, TimeUnit.NANOSECONDS);

        if (response.statusCode() == 404) {
            return false;
        }
        if (response.statusCode() >= 500) {
            throw new GenerationRetryableException("Проверка безопасности во внешнем ML-сервисе временно недоступна");
        }
        if (response.statusCode() >= 400) {
            throw new GenerationNonRetryableException("Проверка безопасности завершилась со статусом " + response.statusCode());
        }

        try {
            JsonNode jsonNode = MAPPER.readTree(response.body());
            return jsonNode.has("unethical") && jsonNode.get("unethical").asBoolean();
        } catch (Exception e) {
            throw new GenerationNonRetryableException("ML-сервис вернул некорректный ответ проверки безопасности", e);
        }
    }

    public List<MlQuestionDTO> generateQuestionsStructured(
            String prompt,
            int numberOfQuestions,
            QuestionType preferredQuestionType
    ) throws IOException, InterruptedException, UnethicalPromptException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(generateUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(
                        buildGenerationFormBody(prompt, numberOfQuestions, preferredQuestionType),
                        StandardCharsets.UTF_8))
                .build();

        long generateStart = System.nanoTime();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        metricsService.getFastApiGenerateTimer().record(System.nanoTime() - generateStart, TimeUnit.NANOSECONDS);

        handleGenerationError(response);
        return extractFinishedQuestions(MAPPER.readValue(response.body(), MlJobStateDTO.class));
    }

    public MlJobStateDTO startGenerationJob(
            String prompt,
            int numberOfQuestions,
            QuestionType preferredQuestionType
    ) throws IOException, InterruptedException, UnethicalPromptException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(jobsGenerateUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(
                        buildGenerationFormBody(prompt, numberOfQuestions, preferredQuestionType),
                        StandardCharsets.UTF_8))
                .build();

        long generateStart = System.nanoTime();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        metricsService.getFastApiGenerateTimer().record(System.nanoTime() - generateStart, TimeUnit.NANOSECONDS);

        handleGenerationError(response);
        MlJobStateDTO job = MAPPER.readValue(response.body(), MlJobStateDTO.class);
        if (job == null || job.id() == null || job.id().isBlank()) {
            throw new GenerationRetryableException("ML-сервис не вернул идентификатор задания");
        }
        return job;
    }

    public MlJobStateDTO getGenerationJob(String mlJobId) throws IOException, InterruptedException {
        String encodedJobId = URLEncoder.encode(mlJobId, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(jobsBaseUrl + encodedJobId))
                .GET()
                .build();

        long generateStart = System.nanoTime();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        metricsService.getFastApiGenerateTimer().record(System.nanoTime() - generateStart, TimeUnit.NANOSECONDS);

        if (response.statusCode() == 404) {
            throw new GenerationNonRetryableException("Задание ML-сервиса не найдено: " + mlJobId);
        }
        handleGenerationError(response);
        return MAPPER.readValue(response.body(), MlJobStateDTO.class);
    }

    public boolean isJobFinished(MlJobStateDTO job) {
        return job != null && "finished".equalsIgnoreCase(job.status());
    }

    public boolean isJobFailed(MlJobStateDTO job) {
        return job != null && "failed".equalsIgnoreCase(job.status());
    }

    public List<MlQuestionDTO> extractFinishedQuestions(MlJobStateDTO job) {
        if (job == null) {
            return List.of();
        }
        if (isJobFailed(job)) {
            String message = job.errors() != null && !job.errors().isEmpty()
                    ? String.join("; ", job.errors())
                    : "Задание ML-сервиса завершилось с ошибкой";
            throw new GenerationNonRetryableException("Ошибка ML-сервиса: " + message);
        }
        if (job.result() == null || job.result().parsed_response() == null) {
            return List.of();
        }
        List<MlQuestionDTO> questions = job.result().parsed_response().questions();
        return questions != null ? questions : List.of();
    }

    private String buildGenerationFormBody(String prompt, int numberOfQuestions, QuestionType preferredQuestionType) {
        String normalizedPrompt = normalizeTopic(prompt);
        String questionTypes = mapQuestionType(preferredQuestionType);

        return "topic=" + URLEncoder.encode(normalizedPrompt, StandardCharsets.UTF_8)
                + "&number=" + numberOfQuestions
                + "&question_types=" + URLEncoder.encode(questionTypes, StandardCharsets.UTF_8);
    }

    private String normalizeTopic(String prompt) {
        String normalized = prompt != null ? prompt.trim() : "";
        return hasMeaningfulText(normalized) ? normalized : DEFAULT_TOPIC;
    }

    private boolean hasMeaningfulText(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.codePoints().anyMatch(Character::isLetterOrDigit);
    }

    private void handleGenerationError(HttpResponse<String> response) throws UnethicalPromptException {
        if (response.statusCode() < 400) {
            return;
        }

        try {
            if (response.statusCode() == 502 || response.statusCode() == 503 || response.statusCode() == 504) {
                throw new GenerationRetryableException(
                        "ML-сервис временно недоступен, статус " + response.statusCode()
                );
            }

            JsonNode jsonNode = MAPPER.readTree(response.body());
            if (jsonNode.has("error")) {
                String errorType = jsonNode.get("error").asText();
                String errorMessage = jsonNode.has("message")
                        ? jsonNode.get("message").asText()
                        : "Не удалось сгенерировать вопросы";
                if ("UNETHICAL_PROMPT".equals(errorType)) {
                    throw new UnethicalPromptException(errorMessage);
                }
                throw new GenerationNonRetryableException("Ошибка ML-сервиса: " + errorMessage);
            }
            if (jsonNode.has("detail")) {
                throw new GenerationNonRetryableException("Ошибка ML-сервиса: " + jsonNode.get("detail"));
            }
            throw new GenerationNonRetryableException(
                    "ML-сервис вернул статус " + response.statusCode() + ": " + response.body()
            );
        } catch (UnethicalPromptException | GenerationRetryableException | GenerationNonRetryableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new GenerationNonRetryableException(
                    "Не удалось разобрать ответ ML-сервиса со статусом " + response.statusCode() + ": " + response.body(),
                    e
            );
        }
    }

    private String mapQuestionType(QuestionType preferredQuestionType) {
        if (preferredQuestionType == null) {
            return "single_choice";
        }
        return switch (preferredQuestionType) {
            case MULTIPLE_CHOICE -> "multiple_choice";
            case TRUE_FALSE -> "true_false";
            case HUNDRED_TO_ONE -> "100k1";
            case SINGLE_CHOICE, TEXT -> "single_choice";
        };
    }
}
