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
import org.example.repository.*;
import org.example.model.UserQuizAttempt;
import org.example.service.AttemptService;
import org.example.service.JwtService;
import org.example.service.QuizService;
import org.example.service.ApiService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Controller
@Slf4j
public class PageController {
    private final ApiController apiController;
    private final QuizService quizService;
    private final UserQuizAttemptRepository attemptRepository;
    private final ApiService apiService;
    private final org.example.repository.MultiplayerSessionRepository multiplayerSessionRepository;
    private final org.example.repository.UserRepository userRepository;
    private final org.example.repository.QuizRepository quizRepository;
    private final AttemptService attemptService;
    private final JwtService jwtService;

    @Autowired
    public PageController(ApiController apiController, QuizService quizService, UserQuizAttemptRepository attemptRepository, ApiService apiService, org.example.repository.MultiplayerSessionRepository multiplayerSessionRepository, org.example.repository.UserRepository userRepository, org.example.repository.QuizRepository quizRepository, AttemptService attemptService, JwtService jwtService) {
        this.apiController = apiController;
        this.quizService = quizService;
        this.attemptRepository = attemptRepository;
        this.apiService = apiService;
        this.multiplayerSessionRepository = multiplayerSessionRepository;
        this.userRepository = userRepository;
        this.quizRepository = quizRepository;
        this.attemptService = attemptService;
        this.jwtService = jwtService;
    }

    @GetMapping("/")
    public String index() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @GetMapping("/home")
    public String home(@RequestParam(required = false) String search,
                       @RequestParam(required = false, defaultValue = "0") Integer page,
                       @RequestParam(required = false, defaultValue = "created") String sort,
                       @RequestParam(required = false, defaultValue = "desc") String direction,
                       HttpServletRequest httpRequest,
                       Model model) {
        String searchQuery = search != null ? search : "";
        int pageNumber = page != null ? page : 0;
        int pageSize = 5;
        String sortBy = normalizeHomeSort(sort);
        boolean ascending = "asc".equalsIgnoreCase(direction);
        Long userId = resolveCurrentUserId(httpRequest);

        QuizSearchRequest request = new QuizSearchRequest(searchQuery, sortBy, ascending, pageNumber, pageSize);
        QuizSearchResponse response = apiService.searchPublicQuizzes(request);
        long totalElements = response.totalElements() != null ? response.totalElements() : 0L;
        long shownFrom = totalElements > 0 ? (long) response.currentPage() * pageSize + 1L : 0L;
        long shownTo = Math.min(((long) response.currentPage() + 1L) * pageSize, totalElements);
        List<Long> quizIds = response.content().stream()
                .map(QuizDTO::id)
                .collect(Collectors.toList());
        Map<Long, Long> completedAttemptCounts = quizIds.isEmpty()
                ? Map.of()
                : attemptRepository.countCompletedByQuizIds(quizIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));

        model.addAttribute("quizzes", response.content());
        model.addAttribute("completedAttemptCounts", completedAttemptCounts);
        model.addAttribute("totalPages", response.totalPages());
        model.addAttribute("totalElements", totalElements);
        model.addAttribute("shownFrom", shownFrom);
        model.addAttribute("shownTo", shownTo);
        model.addAttribute("currentPage", response.currentPage());
        model.addAttribute("search", searchQuery);
        model.addAttribute("sort", sortBy);
        model.addAttribute("direction", ascending ? "asc" : "desc");
        if (userId != null) {
            userRepository.findById(userId)
                    .map(org.example.model.User::getLogin)
                    .ifPresent(login -> model.addAttribute("currentUsername", login));
        }
        return "home";
    }

    private String normalizeHomeSort(String sort) {
        if (sort == null) {
            return "created";
        }
        return switch (sort.toLowerCase()) {
            case "questions", "question_count", "questioncount" -> "questions";
            case "attempts", "completedattempts" -> "attempts";
            default -> "created";
        };
    }

    @GetMapping("/profile")
    public String profile(HttpServletRequest request, HttpServletResponse response, Model model) {
        Long userId = resolveCurrentUserId(request);
        if (userId == null) {
            clearAuthAndRedirectToLogin(response);
            return "redirect:/login?logout=1";
        }
        if (userRepository.findById(userId).isEmpty()) {
            clearAuthAndRedirectToLogin(response);
            return "redirect:/login?logout=1";
        }

        UserProfileDTO userProfile = apiController.getUserProfile(userId);
        UserHistoryDTO userHistory = apiController.getUserHistory(userId);
        List<QuizDTO> createdQuizzes = apiController.getCreatedQuizzes(userId);

        model.addAttribute("userProfile", userProfile);
        model.addAttribute("userHistory", userHistory);
        model.addAttribute("createdQuizzes", createdQuizzes);
        return "profile";
    }

    @GetMapping("/edit-profile")
    public String editProfile(HttpServletRequest request, HttpServletResponse response, Model model) {
        Long userId = resolveCurrentUserId(request);
        if (userId == null) {
            clearAuthAndRedirectToLogin(response);
            return "redirect:/login?logout=1";
        }
        if (userRepository.findById(userId).isEmpty()) {
            clearAuthAndRedirectToLogin(response);
            return "redirect:/login?logout=1";
        }

        try {
            UserProfileDTO userProfile = apiController.getUserProfile(userId);
            model.addAttribute("userProfile", userProfile);
            model.addAttribute("userId", userId);
            return "edit-profile";
        } catch (Exception e) {
            return "redirect:/profile";
        }
    }

    private Long resolveCurrentUserId(HttpServletRequest request) {
        return jwtService.extractUserIdFromRequest(request);
    }

    private void clearAuthAndRedirectToLogin(HttpServletResponse response) {
        Cookie tokenCookie = new Cookie("authToken", "");
        tokenCookie.setHttpOnly(true);
        tokenCookie.setPath("/");
        tokenCookie.setMaxAge(0);
        response.addCookie(tokenCookie);
    }

    private Optional<org.example.model.Quiz> findAccessibleQuiz(Long quizId, Long userId) {
        return quizRepository.findById(quizId)
                .filter(quiz -> !quiz.isPrivate()
                        || (userId != null
                        && quiz.getCreatedBy() != null
                        && quiz.getCreatedBy().getId() != null
                        && quiz.getCreatedBy().getId().equals(userId)));
    }

    private boolean canEditQuiz(Long quizId, Long userId) {
        return userId != null && quizRepository.isCreator(quizId, userId);
    }

    private boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        return userRepository.findById(userId)
                .map(user -> "admin".equalsIgnoreCase(user.getLogin()) || userId == 1L)
                .orElse(false);
    }

    private boolean isQuizCreator(Long quizId, Long userId) {
        return quizId != null && userId != null && quizRepository.isCreator(quizId, userId);
    }

    private boolean isMultiplayerHost(String sessionId, Long userId) {
        if (sessionId == null || userId == null) {
            return false;
        }
        try {
            MultiplayerSessionDTO session = apiController.getMultiplayerSession(sessionId);
            return userId.equals(session.hostUserId());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String renderNotFound(HttpServletResponse response, Model model) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        model.addAttribute("errorCode", "404");
        model.addAttribute("errorMessage", "Страница не найдена");
        return "error";
    }

    @GetMapping("/history")
    public String historyPage(HttpServletRequest request,
                              HttpServletResponse response,
                              @RequestParam(required = false) String search,
                              @RequestParam(required = false, defaultValue = "all") String visibility,
                              @RequestParam(required = false, defaultValue = "latest") String sort,
                              @RequestParam(required = false, defaultValue = "desc") String direction,
                              @RequestParam(required = false, defaultValue = "quizzes") String view,
                              @RequestParam(required = false, defaultValue = "0") Integer page,
                              Model model) {
        Long userId = resolveCurrentUserId(request);
        if (userId == null || userRepository.findById(userId).isEmpty()) {
            clearAuthAndRedirectToLogin(response);
            return "redirect:/login?logout=1";
        }
        try {
            String searchQuery = search != null ? search.trim() : "";
            String visibilityFilter = normalizeVisibilityFilter(visibility);
            String sortBy = normalizeHistorySort(sort);
            String historyView = normalizeHistoryView(view);
            boolean ascending = "asc".equalsIgnoreCase(direction);
            int pageNumber = page != null && page >= 0 ? page : 0;
            int pageSize = 5;
            List<HistoryQuizCard> allHistoryQuizzes = "attempts".equals(historyView)
                    ? List.of()
                    : buildHistoryQuizCards(userId, searchQuery, visibilityFilter, sortBy, ascending);
            List<HistoryAttemptCard> allHistoryAttempts = "attempts".equals(historyView)
                    ? buildHistoryAttemptCards(userId, searchQuery, visibilityFilter, sortBy, ascending)
                    : List.of();
            int totalElements = "attempts".equals(historyView) ? allHistoryAttempts.size() : allHistoryQuizzes.size();
            int totalPages = totalElements == 0 ? 0 : (int) Math.ceil(totalElements / (double) pageSize);
            int currentPage = totalPages > 0 ? Math.min(pageNumber, totalPages - 1) : 0;
            int fromIndex = Math.min(currentPage * pageSize, totalElements);
            int toIndex = Math.min(fromIndex + pageSize, totalElements);
            List<HistoryQuizCard> historyQuizzes = "attempts".equals(historyView)
                    ? List.of()
                    : allHistoryQuizzes.subList(fromIndex, toIndex);
            List<HistoryAttemptCard> historyAttempts = "attempts".equals(historyView)
                    ? allHistoryAttempts.subList(fromIndex, toIndex)
                    : List.of();
            long shownFrom = totalElements > 0 ? fromIndex + 1L : 0L;
            long shownTo = toIndex;

            userRepository.findById(userId)
                    .map(org.example.model.User::getLogin)
                    .ifPresent(login -> model.addAttribute("currentUsername", login));
            model.addAttribute("historyQuizzes", historyQuizzes);
            model.addAttribute("historyAttempts", historyAttempts);
            model.addAttribute("historyView", historyView);
            model.addAttribute("totalElements", totalElements);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("currentPage", currentPage);
            model.addAttribute("shownFrom", shownFrom);
            model.addAttribute("shownTo", shownTo);
            model.addAttribute("search", searchQuery);
            model.addAttribute("visibility", visibilityFilter);
            model.addAttribute("sort", sortBy);
            model.addAttribute("direction", ascending ? "asc" : "desc");
            model.addAttribute("userId", userId);

            return "history";
        } catch (Exception e) {
            model.addAttribute("historyQuizzes", List.of());
            model.addAttribute("historyAttempts", List.of());
            model.addAttribute("historyView", normalizeHistoryView(view));
            model.addAttribute("totalElements", 0);
            model.addAttribute("totalPages", 0);
            model.addAttribute("currentPage", 0);
            model.addAttribute("shownFrom", 0);
            model.addAttribute("shownTo", 0);
            model.addAttribute("search", search != null ? search.trim() : "");
            model.addAttribute("visibility", normalizeVisibilityFilter(visibility));
            model.addAttribute("sort", normalizeHistorySort(sort));
            model.addAttribute("direction", "asc".equalsIgnoreCase(direction) ? "asc" : "desc");
            model.addAttribute("userId", userId);
            return "history";
        }
    }

    private List<HistoryQuizCard> buildHistoryQuizCards(Long userId,
                                                        String searchQuery,
                                                        String visibility,
                                                        String sortBy,
                                                        boolean ascending) {
        String normalizedSearch = searchQuery != null ? searchQuery.toLowerCase() : "";
        List<UserQuizAttempt> attempts = attemptRepository.findCompletedHistoryByUserId(userId).stream()
                .filter(attempt -> normalizedSearch.isBlank()
                        || attempt.getQuiz().getName().toLowerCase().contains(normalizedSearch))
                .filter(attempt -> "all".equals(visibility)
                        || ("public".equals(visibility) && !attempt.getQuiz().isPrivate())
                        || ("private".equals(visibility) && attempt.getQuiz().isPrivate()))
                .collect(Collectors.toList());

        Map<Long, List<UserQuizAttempt>> attemptsByQuiz = attempts.stream()
                .collect(Collectors.groupingBy(
                        attempt -> attempt.getQuiz().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<HistoryQuizCard> cards = new ArrayList<>();
        for (List<UserQuizAttempt> quizAttempts : attemptsByQuiz.values()) {
            UserQuizAttempt latestAttempt = quizAttempts.get(0);
            org.example.model.Quiz quiz = latestAttempt.getQuiz();
            int questionCount = quiz.getQuestionNumber() != null ? quiz.getQuestionNumber() : 0;
            Integer totalTimeSeconds = null;
            if (quiz.getTimePerQuestion() != null && quiz.getTimePerQuestion().getSeconds() > 0 && questionCount > 0) {
                totalTimeSeconds = (int) (quiz.getTimePerQuestion().getSeconds() * questionCount);
            }
            int bestAccuracy = quizAttempts.stream()
                    .map(UserQuizAttempt::getAccuracyPercent)
                    .filter(java.util.Objects::nonNull)
                    .max(Integer::compareTo)
                    .orElse(0);
            long bestScore = quizAttempts.stream()
                    .map(UserQuizAttempt::getScore)
                    .filter(java.util.Objects::nonNull)
                    .max(Long::compareTo)
                    .orElse(0L);
            List<Integer> accuracySeries = quizAttempts.stream()
                    .sorted(Comparator.comparing(UserQuizAttempt::getFinishTime))
                    .map(attempt -> attempt.getAccuracyPercent() != null ? attempt.getAccuracyPercent() : 0)
                    .collect(Collectors.toList());

            cards.add(new HistoryQuizCard(
                    quiz.getId(),
                    quiz.getName(),
                    questionCount,
                    totalTimeSeconds,
                    !quiz.isPrivate(),
                    bestAccuracy,
                    bestScore,
                    latestAttempt.getFinishTime() != null
                            ? java.time.LocalDateTime.ofInstant(latestAttempt.getFinishTime(), java.time.ZoneId.systemDefault())
                            : null,
                    accuracySeries,
                    buildSparklinePoints(accuracySeries),
                    accuracyTone(bestAccuracy)
            ));
        }
        Comparator<HistoryQuizCard> comparator = switch (sortBy) {
            case "accuracy" -> Comparator.comparing(HistoryQuizCard::bestAccuracy);
            case "score" -> Comparator.comparing(HistoryQuizCard::bestScore);
            default -> Comparator.comparing(HistoryQuizCard::lastAttemptAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        if (!ascending) {
            comparator = comparator.reversed();
        }
        return cards.stream()
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    private List<HistoryAttemptCard> buildHistoryAttemptCards(Long userId,
                                                              String searchQuery,
                                                              String visibility,
                                                              String sortBy,
                                                              boolean ascending) {
        String normalizedSearch = searchQuery != null ? searchQuery.toLowerCase() : "";
        Comparator<HistoryAttemptCard> comparator = switch (sortBy) {
            case "accuracy" -> Comparator.comparing(HistoryAttemptCard::accuracy);
            case "score" -> Comparator.comparing(HistoryAttemptCard::score);
            default -> Comparator.comparing(HistoryAttemptCard::attemptAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        if (!ascending) {
            comparator = comparator.reversed();
        }
        return attemptRepository.findCompletedHistoryByUserId(userId).stream()
                .filter(attempt -> normalizedSearch.isBlank()
                        || attempt.getQuiz().getName().toLowerCase().contains(normalizedSearch))
                .filter(attempt -> "all".equals(visibility)
                        || ("public".equals(visibility) && !attempt.getQuiz().isPrivate())
                        || ("private".equals(visibility) && attempt.getQuiz().isPrivate()))
                .map(this::toHistoryAttemptCard)
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    private HistoryAttemptCard toHistoryAttemptCard(UserQuizAttempt attempt) {
        org.example.model.Quiz quiz = attempt.getQuiz();
        int questionCount = quiz.getQuestionNumber() != null ? quiz.getQuestionNumber() : 0;
        Integer totalTimeSeconds = null;
        if (quiz.getTimePerQuestion() != null && quiz.getTimePerQuestion().getSeconds() > 0 && questionCount > 0) {
            totalTimeSeconds = (int) (quiz.getTimePerQuestion().getSeconds() * questionCount);
        }
        int accuracy = attempt.getAccuracyPercent() != null ? attempt.getAccuracyPercent() : 0;
        long score = attempt.getScore() != null ? attempt.getScore() : 0L;
        return new HistoryAttemptCard(
                attempt.getId(),
                quiz.getId(),
                quiz.getName(),
                questionCount,
                totalTimeSeconds,
                !quiz.isPrivate(),
                accuracy,
                score,
                attempt.getFinishTime() != null
                        ? java.time.LocalDateTime.ofInstant(attempt.getFinishTime(), java.time.ZoneId.systemDefault())
                        : null,
                accuracyTone(accuracy)
        );
    }

    private String normalizeHistorySort(String sort) {
        if (sort == null) {
            return "latest";
        }
        return switch (sort.toLowerCase()) {
            case "accuracy", "bestaccuracy" -> "accuracy";
            case "score", "bestscore" -> "score";
            default -> "latest";
        };
    }

    private String normalizeHistoryView(String view) {
        return "attempts".equalsIgnoreCase(view) ? "attempts" : "quizzes";
    }

    private String buildSparklinePoints(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        int count = Math.max(values.size(), 2);
        double step = 160.0 / (count - 1);
        List<String> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int value = values.get(Math.min(i, values.size() - 1));
            double x = i * step;
            double y = 52.0 - Math.max(0, Math.min(100, value)) * 0.48;
            points.add(String.format(java.util.Locale.US, "%.1f,%.1f", x, y));
        }
        return String.join(" ", points);
    }

    private String accuracyTone(int accuracy) {
        if (accuracy >= 80) {
            return "good";
        }
        if (accuracy >= 60) {
            return "medium";
        }
        return "low";
    }

    public record HistoryQuizCard(Long quizId,
                                  String quizName,
                                  Integer questionCount,
                                  Integer timeLimit,
                                  Boolean isPublic,
                                  Integer bestAccuracy,
                                  Long bestScore,
                                  java.time.LocalDateTime lastAttemptAt,
                                  List<Integer> accuracySeries,
                                  String graphPoints,
                                  String accuracyTone) {
    }

    public record HistoryAttemptCard(Long attemptId,
                                     Long quizId,
                                     String quizName,
                                     Integer questionCount,
                                     Integer timeLimit,
                                     Boolean isPublic,
                                     Integer accuracy,
                                     Long score,
                                     java.time.LocalDateTime attemptAt,
                                     String accuracyTone) {
    }

    @GetMapping("/quiz")
    public String quizPage(
        @RequestParam(required = false) Long quizId,
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) String sessionId,
        HttpServletResponse response,
        Model model) {
        if (quizId != null && findAccessibleQuiz(quizId, userId).isEmpty()) {
            return renderNotFound(response, model);
        }

        QuizDTO quiz = null;
        LeaderboardDTO leaderboard = null;
        boolean hasQuestions = false;

        if (quizId != null) {
            try {
                QuizDetailsDTO quizDetails = quizService.getQuiz(quizId, userId);
                hasQuestions = quizDetails.questions() != null && !quizDetails.questions().isEmpty();
                quiz = new QuizDTO(
                    quizDetails.id(),
                    quizDetails.name(),
                    quizDetails.author(),
                    quizDetails.questions() != null ? quizDetails.questions().size() : 0,
                    quizDetails.timeLimit(),
                    quizDetails.timePerQuestion(),
                    quizDetails.isPublic(),
                    quizDetails.isStatic(),
                    quizDetails.createdAt()
                );
            } catch (Exception e) {
                log.debug("Не удалось загрузить карточку квиза {}", quizId, e);
            }
        }

        if (quizId != null && userId != null) {
            try {
                leaderboard = quizService.getQuizLeaderboard(quizId, userId, "solo");
            } catch (Exception e) {
                log.debug("Не удалось загрузить лидерборд квиза {}", quizId, e);
            }
        }
        List<QuizDTO> quizzes = quiz != null ? List.of(quiz) : List.of();

        boolean isAdmin = isAdmin(userId);
        boolean isCreator = isQuizCreator(quizId, userId);
        boolean isMultiplayerHost = isMultiplayerHost(sessionId, userId);

        model.addAttribute("quizzes", quizzes);
        model.addAttribute("leaderboard", leaderboard);
        model.addAttribute("quizId", quizId);
        model.addAttribute("userId", userId);
        model.addAttribute("hasQuestions", hasQuestions);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("isCreator", isCreator);
        model.addAttribute("sessionId", sessionId);
        model.addAttribute("isMultiplayerHost", isMultiplayerHost);
        if (userId != null && sessionId == null) {
            UserQuizAttempt activeAttempt = attemptRepository
                    .findTopByUserIdAndQuizIdAndSessionIdIsNullAndIsCompletedFalseOrderByIdDesc(userId, quizId);
            model.addAttribute("activeAttemptId", activeAttempt != null ? activeAttempt.getId() : null);
        }
        if (userId != null) {
            userRepository.findById(userId)
                    .map(org.example.model.User::getLogin)
                    .ifPresent(login -> model.addAttribute("currentUsername", login));
        }

        return "quiz";
    }

    @GetMapping("/quiz/{quizId}")
    public String quizPageByPath(@PathVariable Long quizId, HttpServletRequest request, HttpServletResponse response, Model model,
                                  @RequestParam(required = false) String error,
                                  @RequestParam(required = false) String sessionId) {
        Long userId = jwtService.extractUserIdFromRequest(request);
        if (findAccessibleQuiz(quizId, userId).isEmpty()) {
            return renderNotFound(response, model);
        }

        QuizDTO quiz = null;
        LeaderboardDTO leaderboard = null;
        boolean hasQuestions = false;

        try {
            QuizDetailsDTO quizDetails = quizService.getQuiz(quizId, userId);
            hasQuestions = quizDetails.questions() != null && !quizDetails.questions().isEmpty();
            quiz = new QuizDTO(
                quizDetails.id(),
                quizDetails.name(),
                quizDetails.author(),
                quizDetails.questions() != null ? quizDetails.questions().size() : 0,
                quizDetails.timeLimit(),
                quizDetails.timePerQuestion(),
                quizDetails.isPublic(),
                quizDetails.isStatic(),
                quizDetails.createdAt()
            );
        } catch (Exception e) {
            log.debug("Не удалось загрузить карточку квиза {}", quizId, e);
        }

        try {
            leaderboard = quizService.getQuizLeaderboard(quizId, userId, "solo");
        } catch (Exception e) {
            log.debug("Не удалось загрузить лидерборд квиза {}", quizId, e);
        }

        List<QuizDTO> quizzes = quiz != null ? List.of(quiz) : List.of();

        boolean isAdmin = isAdmin(userId);
        boolean isCreator = isQuizCreator(quizId, userId);
        boolean isMultiplayerHost = isMultiplayerHost(sessionId, userId);

        model.addAttribute("quizzes", quizzes);
        model.addAttribute("leaderboard", leaderboard);
        model.addAttribute("quizId", quizId);
        model.addAttribute("userId", userId);
        model.addAttribute("hasQuestions", hasQuestions);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("isCreator", isCreator);
        model.addAttribute("sessionId", sessionId);
        model.addAttribute("isMultiplayerHost", isMultiplayerHost);
        if (userId != null && sessionId == null) {
            UserQuizAttempt activeAttempt = attemptRepository
                    .findTopByUserIdAndQuizIdAndSessionIdIsNullAndIsCompletedFalseOrderByIdDesc(userId, quizId);
            model.addAttribute("activeAttemptId", activeAttempt != null ? activeAttempt.getId() : null);
        }
        if (userId != null) {
            userRepository.findById(userId)
                    .map(org.example.model.User::getLogin)
                    .ifPresent(login -> model.addAttribute("currentUsername", login));
        }


        if ("noQuestions".equals(error)) {
            model.addAttribute("errorMessage", "Этот квиз не содержит вопросов. Невозможно начать прохождение.");
        } else if ("startFailed".equals(error)) {
            model.addAttribute("errorMessage", "Ошибка при начале квиза. Попробуйте позже.");
        } else if ("accessDenied".equals(error)) {
            model.addAttribute("errorMessage", "Доступ к этому квизу запрещен. Это приватный квиз.");
        } else if ("notFound".equals(error)) {
            model.addAttribute("errorMessage", "Квиз или пользователь не найдены.");
        }

        return "quiz";
    }

    @GetMapping("/quiz/{quizId}/details")
    public String quizDetails(@PathVariable Long quizId,
                              HttpServletRequest request,
                              HttpServletResponse response,
                              Model model) {
        Long userId = jwtService.extractUserIdFromRequest(request);
        if (findAccessibleQuiz(quizId, userId).isEmpty()) {
            return renderNotFound(response, model);
        }
        QuizDetailsDTO quiz = apiService.getQuiz(quizId, userId);
        model.addAttribute("quiz", quiz);
        return "quiz-details";
    }

    @GetMapping("/my-quizzes")
    public String myQuizzes(HttpServletRequest request,
                            HttpServletResponse response,
                            @RequestParam(required = false) String search,
                            @RequestParam(required = false, defaultValue = "all") String visibility,
                            @RequestParam(required = false, defaultValue = "created") String sort,
                            @RequestParam(required = false, defaultValue = "desc") String direction,
                            @RequestParam(required = false, defaultValue = "0") Integer page,
                            Model model) {
        Long userId = resolveCurrentUserId(request);
        if (userId == null || userRepository.findById(userId).isEmpty()) {
            clearAuthAndRedirectToLogin(response);
            return "redirect:/login?logout=1";
        }
        int pageNumber = page != null && page >= 0 ? page : 0;
        int pageSize = 5;
        String searchQuery = search != null ? search.trim() : "";
        String visibilityFilter = normalizeVisibilityFilter(visibility);
        String sortBy = normalizeMyQuizSort(sort);
        boolean ascending = "asc".equalsIgnoreCase(direction);
        PageRequest pageRequest = PageRequest.of(pageNumber, pageSize, createMyQuizSort(sortBy, ascending));
        PageRequest attemptsPageRequest = PageRequest.of(pageNumber, pageSize);
        Page<org.example.model.Quiz> quizPage = "attempts".equals(sortBy)
                ? (ascending
                ? quizRepository.searchCreatedQuizzesOrderByCompletedAttemptsAsc(userId, searchQuery, visibilityFilter, attemptsPageRequest)
                : quizRepository.searchCreatedQuizzesOrderByCompletedAttemptsDesc(userId, searchQuery, visibilityFilter, attemptsPageRequest))
                : quizRepository.searchCreatedQuizzes(userId, searchQuery, visibilityFilter, pageRequest);
        List<QuizDTO> createdQuizzes = quizPage.getContent().stream()
                .map(this::toQuizDTO)
                .collect(Collectors.toList());
        List<Long> quizIds = createdQuizzes.stream()
                .map(QuizDTO::id)
                .collect(Collectors.toList());
        Map<Long, Long> completedAttemptCounts = quizIds.isEmpty()
                ? Map.of()
                : attemptRepository.countCompletedByQuizIds(quizIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));
        long totalElements = quizPage.getTotalElements();
        long shownFrom = totalElements > 0 ? (long) quizPage.getNumber() * pageSize + 1L : 0L;
        long shownTo = Math.min(((long) quizPage.getNumber() + 1L) * pageSize, totalElements);

        userRepository.findById(userId)
                .map(org.example.model.User::getLogin)
                .ifPresent(login -> model.addAttribute("currentUsername", login));
        model.addAttribute("createdQuizzes", createdQuizzes);
        model.addAttribute("completedAttemptCounts", completedAttemptCounts);
        model.addAttribute("totalPages", quizPage.getTotalPages());
        model.addAttribute("totalElements", totalElements);
        model.addAttribute("shownFrom", shownFrom);
        model.addAttribute("shownTo", shownTo);
        model.addAttribute("currentPage", quizPage.getNumber());
        model.addAttribute("search", searchQuery);
        model.addAttribute("visibility", visibilityFilter);
        model.addAttribute("sort", sortBy);
        model.addAttribute("direction", ascending ? "asc" : "desc");
        model.addAttribute("userId", userId);
        return "my-quizzes";
    }

    private Sort createMyQuizSort(String sortBy, boolean ascending) {
        Sort.Direction direction = ascending ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortField = switch (sortBy) {
            case "questions" -> "questionNumber";
            default -> "createdAt";
        };
        return Sort.by(direction, sortField);
    }

    private String normalizeMyQuizSort(String sort) {
        if (sort == null) {
            return "created";
        }
        return switch (sort.toLowerCase()) {
            case "questions", "question_count", "questioncount" -> "questions";
            case "attempts", "completedattempts" -> "attempts";
            default -> "created";
        };
    }

    private String normalizeVisibilityFilter(String visibility) {
        if (visibility == null) {
            return "all";
        }
        String value = visibility.toLowerCase();
        return switch (value) {
            case "public", "private" -> value;
            default -> "all";
        };
    }

    private QuizDTO toQuizDTO(org.example.model.Quiz quiz) {
        int questionCount = quiz.getQuestionNumber() != null ? quiz.getQuestionNumber() : 0;
        Integer timePerQuestionSeconds = null;
        Integer totalTimeSeconds = null;
        if (quiz.getTimePerQuestion() != null && quiz.getTimePerQuestion().getSeconds() > 0) {
            long secondsPerQuestion = quiz.getTimePerQuestion().getSeconds();
            timePerQuestionSeconds = (int) secondsPerQuestion;
            if (questionCount > 0) {
                totalTimeSeconds = (int) (secondsPerQuestion * questionCount);
            }
        }

        return new QuizDTO(
                quiz.getId(),
                quiz.getName(),
                quiz.getCreatedBy().getLogin(),
                questionCount,
                totalTimeSeconds,
                timePerQuestionSeconds,
                !quiz.isPrivate(),
                quiz.isStatic(),
                quiz.getCreatedAt() != null
                        ? java.time.LocalDateTime.ofInstant(quiz.getCreatedAt(), java.time.ZoneId.systemDefault())
                        : null
        );
    }

    @GetMapping("/quiz/create")
    public String createQuizPage(HttpServletRequest request,
                                 HttpServletResponse response,
                                 Model model) {
        Long userId = resolveCurrentUserId(request);
        if (userId == null || userRepository.findById(userId).isEmpty()) {
            clearAuthAndRedirectToLogin(response);
            return "redirect:/login?logout=1";
        }
        userRepository.findById(userId)
                .map(user -> user.getLogin())
                .filter(login -> login != null && !login.isBlank())
                .ifPresent(login -> model.addAttribute("currentUsername", login));
        model.addAttribute("userId", userId);
        return "create-quiz";
    }

    @GetMapping("/quiz/{quizId}/edit")
    public String editQuizPage(@PathVariable Long quizId,
                               HttpServletRequest request,
                               HttpServletResponse response,
                               Model model) {
        Long userId = resolveCurrentUserId(request);
        if (userId == null || userRepository.findById(userId).isEmpty()) {
            clearAuthAndRedirectToLogin(response);
            return "redirect:/login?logout=1";
        }
        if (!canEditQuiz(quizId, userId)) {
            return renderNotFound(response, model);
        }
        QuizDetailsDTO quiz = apiController.getQuiz(quizId, userId);

        model.addAttribute("quiz", quiz);
        model.addAttribute("quizId", quizId);
        model.addAttribute("userId", userId);
        model.addAttribute("quizName", quiz.name());
        model.addAttribute("description", quiz.description());
        model.addAttribute("questions", quiz.questions());
        model.addAttribute("maxQuestionNumber", quiz.questions() != null && !quiz.questions().isEmpty()
                ? quiz.questions().size()
                : 1);
        model.addAttribute("materials", quiz.materials());
        model.addAttribute("timeLimit", quiz.timePerQuestion());
        model.addAttribute("isPublic", quiz.isPublic());
        model.addAttribute("isStatic", quiz.isStatic());

        return "edit-quiz";
    }

    @GetMapping("/multiplayer/create")
    public String createMultiplayerSession(@RequestParam Long quizId,
                                           HttpServletRequest request,
                                           HttpServletResponse response,
                                           Model model) {
        Long userId = resolveCurrentUserId(request);
        if (userId == null || userRepository.findById(userId).isEmpty()) {
            clearAuthAndRedirectToLogin(response);
            return "redirect:/login?logout=1";
        }
        if (findAccessibleQuiz(quizId, userId).isEmpty()) {
            return renderNotFound(response, model);
        }
        try {
            CreateMultiplayerRequest createRequest = new CreateMultiplayerRequest(userId, quizId);
            MultiplayerSessionDTO session = apiController.createMultiplayerSession(createRequest);
            return "redirect:/multiplayer/session/" + session.sessionId();
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Ошибка при создании сессии: " + e.getMessage());
            return "redirect:/quiz/" + quizId;
        }
    }

    @GetMapping("/multiplayer/join")
    public String joinMultiplayerPage(@RequestParam(required = false) String sessionId,
                                      HttpServletRequest request,
                                      Model model) {
        model.addAttribute("sessionId", sessionId);
        Long userId = jwtService.extractUserIdFromRequest(request);
        if (userId != null) {
            model.addAttribute("userId", userId);
        } else {
            model.addAttribute("userId", 0L);
        }
        return "multiplayer-join";
    }

    @GetMapping("/multiplayer/session/{sessionId}")
    public String multiplayerSessionPage(@PathVariable String sessionId,
                                         HttpServletRequest request,
                                         HttpServletResponse response,
                                         Model model) {
        Long userId = resolveCurrentUserId(request);
        if (userId == null || userRepository.findById(userId).isEmpty()) {
            clearAuthAndRedirectToLogin(response);
            return "redirect:/login?logout=1";
        }
        try {
            MultiplayerSessionDTO session = apiController.getMultiplayerSession(sessionId);
            org.example.model.MultiplayerSession sessionEntity = multiplayerSessionRepository.findBySessionId(sessionId).orElse(null);
            Long quizId = sessionEntity != null ? sessionEntity.getQuiz().getId() : null;
            Long actualHostUserId = session.hostUserId();
            boolean isParticipant = session.participants() != null && session.participants().stream()
                    .anyMatch(participant -> participant.userId() != null && participant.userId().equals(userId));
            if (!isParticipant && !actualHostUserId.equals(userId)) {
                return "redirect:/multiplayer/join?sessionId=" + sessionId;
            }

            model.addAttribute("session", session);
            model.addAttribute("sessionId", sessionId);
            model.addAttribute("hostUserId", actualHostUserId);
            model.addAttribute("currentUserId", userId);
            model.addAttribute("quizName", session.quizName());
            model.addAttribute("participants", session.participants());
            model.addAttribute("status", session.status());
            model.addAttribute("joinLink", session.joinLink());
            model.addAttribute("quizId", quizId);
        } catch (IllegalArgumentException e) {
            model.addAttribute("sessionId", sessionId);
            model.addAttribute("hostUserId", userId);
            model.addAttribute("errorMessage", "Сессия не найдена: " + sessionId);
            model.addAttribute("quizName", "Сессия не найдена");
            model.addAttribute("participants", List.of());
            model.addAttribute("status", "NOT_FOUND");
            model.addAttribute("joinLink", "/multiplayer/join?sessionId=" + sessionId);
            model.addAttribute("quizId", null);
        }

        return "multiplayer-session";
    }

    @GetMapping("/quiz/attempt/{attemptId}/finish")
    public String finishQuizPage(@PathVariable Long attemptId,
                                 HttpServletRequest request,
                                 HttpServletResponse response,
                                 Model model) {
        Long userId = resolveCurrentUserId(request);
        if (userId == null || userRepository.findById(userId).isEmpty()) {
            clearAuthAndRedirectToLogin(response);
            return "redirect:/login?logout=1";
        }

        UserQuizAttempt attempt = attemptRepository.findById(attemptId).orElse(null);
        if (attempt == null || attempt.getUser() == null || !userId.equals(attempt.getUser().getId())) {
            return renderNotFound(response, model);
        }
        if (attempt.getSessionId() != null && !attempt.getSessionId().isBlank()
                && attempt.getStartTime() == null && !attempt.isCompleted()) {
            return renderNotFound(response, model);
        }

        Long quizId = attempt.getQuiz() != null ? attempt.getQuiz().getId() : null;
        if (quizId == null) {
            return renderNotFound(response, model);
        }
        String attemptSessionId = attempt.getSessionId();

        QuizResultDTO result = apiController.finishQuizAttempt(attemptId);
        Integer quizQuestionCount = result.totalQuestions();
        Integer quizTimePerQuestion = null;
        String questionTypeLabel = "Смешанные";

        String quizName = "Квиз";
        try {
            QuizDetailsDTO quiz = quizService.getQuiz(quizId, userId);
            quizName = quiz.name();
            quizQuestionCount = quiz.questionNumber() != null ? quiz.questionNumber() : result.totalQuestions();
            quizTimePerQuestion = quiz.timePerQuestion();
            if (quiz.defaultQuestionType() != null) {
                questionTypeLabel = switch (quiz.defaultQuestionType()) {
                    case SINGLE_CHOICE -> "Один ответ";
                    case MULTIPLE_CHOICE -> "Несколько ответов";
                    case HUNDRED_TO_ONE -> "100 к 1";
                    default -> "Смешанные";
                };
            }
        } catch (Exception e) {
            quizName = "Квиз";
        }

        LeaderboardDTO leaderboard = null;
        LeaderboardDTO soloLeaderboard = null;
        LeaderboardDTO multiplayerLeaderboard = null;
        if (quizId != null && userId != null) {
            try {
                soloLeaderboard = quizService.getQuizLeaderboard(quizId, userId, "solo");
                multiplayerLeaderboard = "multiplayer".equals(result.mode())
                        ? quizService.getQuizSessionLeaderboard(quizId, userId, attemptSessionId)
                        : null;
                leaderboard = "multiplayer".equals(result.mode()) ? multiplayerLeaderboard : soloLeaderboard;
            } catch (Exception e) {
                log.debug("Не удалось загрузить таблицу лидеров для результата квиза {}", quizId, e);
            }
        }

        UserQuizAttempt activeAttempt = null;
        if (quizId != null && userId != null) {
            activeAttempt = attemptRepository
                    .findTopByUserIdAndQuizIdAndSessionIdIsNullAndIsCompletedFalseOrderByIdDesc(userId, quizId);
        }

        long timeSpentSeconds = result.timeSpent() != null ? Math.max(0L, result.timeSpent()) : 0L;
        String formattedTimeSpent = String.format("%02d:%02d", timeSpentSeconds / 60, timeSpentSeconds % 60);
        int leaderboardSize = leaderboard != null && leaderboard.entries() != null ? leaderboard.entries().size() : 0;
        int totalCompletedAttempts = (int) attemptRepository.countCompletedByUserIdAndQuizId(userId, quizId);
        Integer bestAttemptNumber = null;
        if (leaderboard != null && leaderboard.entries() != null && leaderboard.userPosition() != null) {
            for (org.example.dto.common.LeaderboardEntry entry : leaderboard.entries()) {
                if (entry.position() != null && entry.position().equals(leaderboard.userPosition())) {
                    bestAttemptNumber = entry.attemptNumber();
                    break;
                }
            }
        }
        int outperformedPercent = 0;
        if (leaderboardSize > 0 && result.position() != null && result.position() > 0) {
            if (leaderboardSize == 1) {
                outperformedPercent = 100;
            } else {
                outperformedPercent = Math.min(100,
                        Math.max(0, ((leaderboardSize - result.position()) * 100) / (leaderboardSize - 1)));
            }
        }

        model.addAttribute("score", result.score());
        model.addAttribute("correctAnswers", result.correctAnswers());
        model.addAttribute("totalQuestions", result.totalQuestions());
        model.addAttribute("position", result.position());
        model.addAttribute("timeSpentSeconds", timeSpentSeconds);
        model.addAttribute("formattedTimeSpent", formattedTimeSpent);
        model.addAttribute("quizId", quizId);
        model.addAttribute("quizName", quizName);
        model.addAttribute("leaderboard", leaderboard);
        model.addAttribute("soloLeaderboard", soloLeaderboard);
        model.addAttribute("multiplayerLeaderboard", multiplayerLeaderboard);
        model.addAttribute("leaderboardMode", "multiplayer".equals(result.mode()) ? "multiplayer" : "solo");
        model.addAttribute("userPosition", leaderboard != null ? leaderboard.userPosition() : null);
        model.addAttribute("leaderboardSize", leaderboardSize);
        model.addAttribute("outperformedPercent", outperformedPercent);
        model.addAttribute("totalCompletedAttempts", totalCompletedAttempts);
        model.addAttribute("bestAttemptNumber", bestAttemptNumber);
        model.addAttribute("userId", userId);
        userRepository.findById(userId)
                .map(org.example.model.User::getLogin)
                .ifPresent(login -> model.addAttribute("currentUsername", login));
        model.addAttribute("activeAttemptId", activeAttempt != null ? activeAttempt.getId() : null);
        model.addAttribute("catStake", result.catStake());
        model.addAttribute("catStakeBonus", result.catStakeBonus());
        model.addAttribute("resultMode", result.mode());
        model.addAttribute("quizQuestionCount", quizQuestionCount);
        model.addAttribute("quizTimePerQuestion", quizTimePerQuestion);
        model.addAttribute("questionTypeLabel", questionTypeLabel);
        return "quiz-results";
    }

    @GetMapping("/quiz/{quizId}/attempt")
    public String startQuizPage(@PathVariable Long quizId,
                                @RequestParam(required = false) String sessionId,
                                HttpServletRequest request,
                                HttpServletResponse response,
                                Model model) {
        Long userId = resolveCurrentUserId(request);
        if (userId == null || userRepository.findById(userId).isEmpty()) {
            clearAuthAndRedirectToLogin(response);
            return "redirect:/login?logout=1";
        }
        if (findAccessibleQuiz(quizId, userId).isEmpty()) {
            return renderNotFound(response, model);
        }
        try {
            StartAttemptRequest startAttemptRequest = new StartAttemptRequest(userId, quizId, sessionId);
            AttemptResponse attemptResponse = apiController.startQuizAttempt(startAttemptRequest);
            model.addAttribute("attemptId", attemptResponse.attemptId());
            model.addAttribute("currentQuestion", attemptResponse.currentQuestion());
            model.addAttribute("timeRemaining", attemptResponse.timeRemaining());
            model.addAttribute("questionsRemaining", attemptResponse.questionsRemaining());
            model.addAttribute("totalQuestions", attemptResponse.totalQuestions());
            model.addAttribute("quizName", attemptResponse.quizName());
            model.addAttribute("quizId", attemptResponse.quizId());
            model.addAttribute("defaultTimeLimit", attemptResponse.timeRemaining());
            model.addAttribute("currentQuestionDeadlineEpochMs", attemptResponse.currentQuestionDeadlineEpochMs());
            if (sessionId != null) {
                model.addAttribute("sessionId", sessionId);
            }
            return "quiz-attempt";
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("ATTEMPT_COMPLETED:")) {
                String attemptIdStr = e.getMessage().substring("ATTEMPT_COMPLETED:".length());
                try {
                    Long completedAttemptId = Long.parseLong(attemptIdStr);
                    if (sessionId != null && !sessionId.isEmpty()) {
                        return "redirect:/quiz/attempt/" + completedAttemptId + "/finish";
                    }
                    return "redirect:/quiz/attempt/" + completedAttemptId + "/finish";
                } catch (Exception parseException) {
                    log.debug("Не удалось разобрать id завершённой попытки", parseException);
                }
            }
            if (e.getMessage() != null && e.getMessage().contains("не содержит вопросов")) {
                return "redirect:/quiz/" + quizId + "?error=noQuestions";
            }
            return "redirect:/quiz/" + quizId + "?error=startFailed";
        } catch (Exception e) {
            return "redirect:/quiz/" + quizId + "?error=startFailed";
        }
    }

    @PostMapping("/quiz/{quizId}/attempt/restart")
    public String restartQuizPage(@PathVariable Long quizId,
                                  HttpServletRequest request,
                                  HttpServletResponse response,
                                  Model model) {
        Long userId = resolveCurrentUserId(request);
        if (userId == null || userRepository.findById(userId).isEmpty()) {
            clearAuthAndRedirectToLogin(response);
            return "redirect:/login?logout=1";
        }
        if (findAccessibleQuiz(quizId, userId).isEmpty()) {
            return renderNotFound(response, model);
        }

        return "redirect:/quiz/" + quizId + "/attempt";
    }

    @GetMapping("/quiz/attempt/{attemptId}/question")
    public String quizQuestionPage(@PathVariable Long attemptId,
                                   @RequestParam(required = false) String sessionId,
                                   Model model) {
        try {
            QuestionDTO nextQuestion = apiController.getNextQuestion(attemptId);

            if (nextQuestion == null) {
                Long quizId = attemptRepository.findQuizIdByAttemptId(attemptId);
                if (quizId != null) {
                    return "redirect:/quiz/attempt/" + attemptId + "/finish";
                } else {
                    return "redirect:/home";
                }
            }

            Long quizId = attemptRepository.findQuizIdByAttemptId(attemptId);
            if (quizId == null) {
                return "redirect:/home";
            }

            QuizDetailsDTO quiz = quizService.getQuiz(quizId, null);
            String quizName = quiz.name();
            AttemptPageProgress progress = attemptService.getAttemptPageProgress(attemptId);

            model.addAttribute("questionsRemaining", progress.questionsRemaining());
            model.addAttribute("totalQuestions", progress.totalQuestions());
            model.addAttribute("timeRemaining", progress.timePerQuestionSeconds());
            model.addAttribute("defaultTimeLimit", progress.timePerQuestionSeconds());
            model.addAttribute("currentQuestionDeadlineEpochMs", progress.currentQuestionDeadlineEpochMs());
            model.addAttribute("attemptId", attemptId);
            model.addAttribute("currentQuestion", nextQuestion);
            model.addAttribute("quizName", quizName);
            model.addAttribute("quizId", quizId);
            if (sessionId != null) {
                model.addAttribute("sessionId", sessionId);
            }

            return "quiz-attempt";

        } catch (IllegalStateException e) {
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            return "redirect:/login";
        } catch (Exception e) {
            return "redirect:/login";
        }
    }

    @GetMapping("/multiplayer/session/{sessionId}/results")
    public String multiplayerResultsPage(@PathVariable String sessionId,
                                         HttpServletRequest request,
                                         HttpServletResponse response,
                                         Model model) {
        Long userId = resolveCurrentUserId(request);
        if (userId == null || userRepository.findById(userId).isEmpty()) {
            clearAuthAndRedirectToLogin(response);
            return "redirect:/login?logout=1";
        }
        try {
            MultiplayerResultsDTO results = apiController.getMultiplayerResults(sessionId);
            List<UserQuizAttempt> allAttempts = attemptRepository.findBySessionIdWithUser(sessionId);
            List<UserQuizAttempt> completedAttempts = allAttempts.stream()
                    .filter(UserQuizAttempt::isCompleted)
                    .collect(java.util.stream.Collectors.toList());
            List<UserQuizAttempt> notCompletedAttempts = allAttempts.stream()
                    .filter(a -> !a.isCompleted() && a.getUser() != null)
                    .collect(java.util.stream.Collectors.toList());

            boolean allCompleted = completedAttempts.size() >= 2;
            int totalParticipants = allAttempts.size();
            int completedCount = completedAttempts.size();

            List<String> notCompletedUsernames = notCompletedAttempts.stream()
                    .filter(a -> a.getUser() != null && a.getUser().getLogin() != null)
                    .map(a -> a.getUser().getLogin())
                    .collect(java.util.stream.Collectors.toList());

            model.addAttribute("results", results);
            model.addAttribute("sessionId", sessionId);
            model.addAttribute("userId", userId);
            model.addAttribute("quizName", results.quizName());
            model.addAttribute("allCompleted", allCompleted);
            model.addAttribute("totalParticipants", totalParticipants);
            model.addAttribute("completedCount", completedCount);
            model.addAttribute("notCompletedUsernames", notCompletedUsernames);
            return "multiplayer-results";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", "Сессия не найдена");
            return "redirect:/home";
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Внутренняя ошибка сервера: " + e.getMessage());
            return "redirect:/home";
        }
    }

    @GetMapping("/quiz/{quizId}/leaderboard")
    public String quizLeaderboard(@PathVariable Long quizId,
                                  HttpServletRequest request,
                                  HttpServletResponse response,
                                  Model model) {
        Long userId = resolveCurrentUserId(request);
        if (userId == null || userRepository.findById(userId).isEmpty()) {
            clearAuthAndRedirectToLogin(response);
            return "redirect:/login?logout=1";
        }
        if (findAccessibleQuiz(quizId, userId).isEmpty()) {
            return renderNotFound(response, model);
        }
        LeaderboardDTO leaderboard = quizService.getQuizLeaderboard(quizId, userId);
        model.addAttribute("leaderboard", leaderboard);
        model.addAttribute("userPosition", leaderboard.userPosition());
        model.addAttribute("quizId", quizId);
        QuizDetailsDTO quiz = quizService.getQuiz(quizId, userId);
        model.addAttribute("quizName", quiz.name());
        UserQuizAttempt activeAttempt = attemptRepository
                .findTopByUserIdAndQuizIdAndSessionIdIsNullAndIsCompletedFalseOrderByIdDesc(userId, quizId);
        model.addAttribute("activeAttemptId", activeAttempt != null ? activeAttempt.getId() : null);
        return "quiz-leaderboard";
    }

}
