package org.example.service;

import org.example.controller.ApiController;
import org.example.dto.request.auth.*;
import org.example.dto.request.quiz.*;
import org.example.dto.request.attempt.*;
import org.example.dto.request.multiplayer.*;
import org.example.dto.request.generation.*;
import org.example.dto.response.auth.*;
import org.example.dto.response.quiz.*;
import org.example.dto.response.attempt.*;
import org.example.dto.response.multiplayer.*;
import org.example.dto.response.history.*;
import org.example.dto.response.generation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
@Transactional
public class ApiService implements ApiController {
    private final AuthService authService;
    private final UserService userService;
    private final QuizService quizService;
    private final AttemptService attemptService;
    private final MultiplayerService multiplayerService;
    private final QuestionGenerationService questionGenerationService;
    
    private static final String BASE_URL = "http://127.0.0.1:8000/question/";
    private final HttpClient httpClient;

    @Autowired
    public ApiService(AuthService authService,
                     UserService userService,
                     QuizService quizService,
                     AttemptService attemptService,
                     MultiplayerService multiplayerService,
                     QuestionGenerationService questionGenerationService) {
        this.authService = authService;
        this.userService = userService;
        this.quizService = quizService;
        this.attemptService = attemptService;
        this.multiplayerService = multiplayerService;
        this.questionGenerationService = questionGenerationService;
        this.httpClient = HttpClient.newHttpClient();
    }


    @Override
    public AuthResponse register(RegisterRequest request) {
        return authService.register(request);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        return authService.login(request);
    }


    @Override
    public UserProfileDTO updateUserProfile(UpdateProfileRequest request) {
        return userService.updateUserProfile(request);
    }

    @Override
    public UserProfileDTO getUserProfile(Long userId) {
        return userService.getUserProfile(userId);
    }

    @Override
    public UserHistoryDTO getUserHistory(Long userId) {
        return userService.getUserHistory(userId);
    }

    @Override
    public List<QuizDTO> getCreatedQuizzes(Long userId) {
        return userService.getCreatedQuizzes(userId);
    }


    @Override
    public QuizResponseDTO createQuiz(CreateQuizRequest request) {
        return quizService.createQuiz(request);
    }

    @Override
    public QuizSearchResponse searchPublicQuizzes(QuizSearchRequest request) {
        return quizService.searchPublicQuizzes(request);
    }

    @Override
    public QuizDetailsDTO getQuiz(Long quizId, Long userId) {
        return quizService.getQuiz(quizId, userId);
    }

    @Override
    public boolean deleteQuiz(DeleteQuizRequest request) {
        return quizService.deleteQuiz(request);
    }

    @Override
    public QuizResponseDTO updateQuiz(UpdateQuizRequest request) {
        return quizService.updateQuizMetadata(request);
    }

    @Override
    public boolean removeQuestionFromQuiz(RemoveQuestionRequest request) {
        return quizService.removeQuestionFromQuiz(request);
    }

    @Override
    public LeaderboardDTO getQuizLeaderboard(Long quizId, Long userId) {
        return quizService.getQuizLeaderboard(quizId, userId);
    }


    @Override
    public AttemptResponse startQuizAttempt(StartAttemptRequest request) {
        return attemptService.startQuizAttempt(request);
    }

    @Override
    public QuestionDTO getNextQuestion(Long attemptId) {
        return attemptService.getNextQuestion(attemptId);
    }

    @Override
    public AnswerResponse submitAnswer(SubmitAnswerRequest request) {
        return attemptService.submitAnswer(request);
    }

    @Override
    public QuizResultDTO finishQuizAttempt(Long attemptId) {
        return attemptService.finishQuizAttempt(attemptId);
    }


    @Override
    public MultiplayerSessionDTO createMultiplayerSession(CreateMultiplayerRequest request) {
        return multiplayerService.createMultiplayerSession(request);
    }

    @Override
    public MultiplayerSessionDTO getMultiplayerSession(String sessionId) {
        return multiplayerService.getMultiplayerSession(sessionId);
    }

    @Override
    public boolean joinMultiplayerSession(JoinMultiplayerRequest request) {
        return multiplayerService.joinMultiplayerSession(request);
    }

    @Override
    public ParticipantsDTO getSessionParticipants(String sessionId) {
        return multiplayerService.getSessionParticipants(sessionId);
    }

    @Override
    public boolean startMultiplayerSession(StartMultiplayerRequest request) {
        return multiplayerService.startMultiplayerSession(request);
    }

    @Override
    public MultiplayerResultsDTO getMultiplayerResults(String sessionId) {
        return multiplayerService.getMultiplayerResults(sessionId);
    }

    @Override
    public boolean cancelMultiplayerSession(CancelMultiplayerRequest request) {
        return multiplayerService.cancelMultiplayerSession(request);
    }


    @Override
    public QuestionGenerationResponse generateQuizQuestions(QuestionGenerationRequest request) {
        return questionGenerationService.generateQuizQuestionsKafka(request);
    }

    @Override
    public ValidationResponse validateGeneratedQuestions(Long questionSetId) {
        return questionGenerationService.validateGeneratedQuestions(questionSetId);
    }

    @Override
    public DeduplicationResponse removeDuplicateQuestions(Long questionSetId) {
        return questionGenerationService.removeDuplicateQuestions(questionSetId);
    }

    @Override
    public GeneratedQuestionsDTO getGeneratedQuestions(Long questionSetId) {
        return questionGenerationService.getGeneratedQuestions(questionSetId);
    }
  
    public String getQuestionsByPrompt(String prompt, int numberOfQuestions) throws IOException, InterruptedException {
        String url = BASE_URL + prompt + "/" + numberOfQuestions;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }
}
