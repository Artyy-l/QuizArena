package org.example.controller;

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

import org.example.dto.common.*;

import java.util.List;

public interface ApiController {

  AuthResponse register(RegisterRequest request);

  AuthResponse login(LoginRequest request);

  UserProfileDTO updateUserProfile(UpdateProfileRequest request);

  UserProfileDTO getUserProfile(Long userId);

  UserHistoryDTO getUserHistory(Long userId);

  List<QuizDTO> getCreatedQuizzes(Long userId);

  QuizResponseDTO createQuiz(CreateQuizRequest request);

  QuizSearchResponse searchPublicQuizzes(QuizSearchRequest request);

  QuizDetailsDTO getQuiz(Long quizId, Long userId);

  boolean deleteQuiz(DeleteQuizRequest request);

  QuizResponseDTO updateQuiz(UpdateQuizRequest request);

  boolean removeQuestionFromQuiz(RemoveQuestionRequest request);

  LeaderboardDTO getQuizLeaderboard(Long quizId, Long userId);

  AttemptResponse startQuizAttempt(StartAttemptRequest request);

  QuestionDTO getNextQuestion(Long attemptId);

  AnswerResponse submitAnswer(SubmitAnswerRequest request);

  QuizResultDTO finishQuizAttempt(Long attemptId);

  MultiplayerSessionDTO createMultiplayerSession(CreateMultiplayerRequest request);

  MultiplayerSessionDTO getMultiplayerSession(String sessionId);

  boolean joinMultiplayerSession(JoinMultiplayerRequest request);

  ParticipantsDTO getSessionParticipants(String sessionId);

  boolean startMultiplayerSession(StartMultiplayerRequest request);

  MultiplayerResultsDTO getMultiplayerResults(String sessionId);

  boolean cancelMultiplayerSession(CancelMultiplayerRequest request);

  QuestionGenerationResponse generateQuizQuestions(QuestionGenerationRequest request);

  ValidationResponse validateGeneratedQuestions(Long questionSetId);

  DeduplicationResponse removeDuplicateQuestions(Long questionSetId);

  GeneratedQuestionsDTO getGeneratedQuestions(Long questionSetId);
}
