package org.example.service;

import org.example.model.Question;
import org.example.model.AnswerOption;
import org.example.model.QuestionType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuestionParser {
    public static class ParsedQuestion {
        public Question question;
        public List<AnswerOption> answerOptions;
        
        public ParsedQuestion() {
            this.answerOptions = new ArrayList<>();
        }
    }

    public static List<ParsedQuestion> parse(String data) {
        return parse(data, Integer.MAX_VALUE);
    }

    public static List<ParsedQuestion> parse(String data, int maxBlocksToProcess) {
        List<ParsedQuestion> result = new ArrayList<>();
        
        if (data == null || data.trim().isEmpty()) {
            return result;
        }
        
        String cleaned = data.trim();
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        cleaned = cleaned.replace("\\n", "\n");
        cleaned = cleaned.replace("\\r", "");
        cleaned = cleaned.replaceAll(" +", " ");
        cleaned = cleaned.replaceAll(" *\n+ *", "\n");
        cleaned = cleaned.replaceAll("\n{3,}", "\n\n");
        
        String[] questionBlocks = cleaned.split("(?i)\\*Следующий вопрос\\*");
        
        
        if (questionBlocks.length == 1 && !cleaned.toLowerCase().contains("следующий вопрос")) {
            String[] tempBlocks = cleaned.split("(?i)(?=\\*Вопрос\\*)");
            
            if (tempBlocks.length > 1) {
                List<String> blocks = new ArrayList<>();
                String firstBlock = tempBlocks[0].trim();
                
                if (!firstBlock.isEmpty() && !firstBlock.toLowerCase().contains("*вопрос*")) {
                    if (tempBlocks.length > 1) {
                        blocks.add((firstBlock + tempBlocks[1]).trim());
                        for (int i = 2; i < tempBlocks.length; i++) {
                            String block = tempBlocks[i].trim();
                            if (!block.isEmpty()) {
                                blocks.add(block);
                            }
                        }
                    } else {
                        blocks.add(firstBlock);
                    }
                } else {
                    for (String block : tempBlocks) {
                        String trimmed = block.trim();
                        if (!trimmed.isEmpty()) {
                            blocks.add(trimmed);
                        }
                    }
                }
                questionBlocks = blocks.toArray(new String[0]);
            } else {
                questionBlocks = new String[]{cleaned};
            }
        }
        
        int limit = maxBlocksToProcess > 0 ? Math.min(questionBlocks.length, maxBlocksToProcess) : questionBlocks.length;
        
        for (int blockIndex = 0; blockIndex < limit; blockIndex++) {
            String block = questionBlocks[blockIndex].trim();
            if (block.isEmpty()) continue;
            
            ParsedQuestion pq = new ParsedQuestion();
            
            Pattern questionPattern = Pattern.compile("(?i)(?:\\*)?Вопрос(?:\\*)?[\\s\\n]+([^\\n]+?)(?=\\s*\\n\\s*\\d+\\)|\\s*\\n\\s*\\*правильный|$)", Pattern.DOTALL | Pattern.MULTILINE);
            Matcher qMatcher = questionPattern.matcher(block);
            
            String questionText = null;
            if (qMatcher.find()) {
                questionText = qMatcher.group(1).trim();
                questionText = questionText.replaceAll("^\\*+|\\*+$", "").trim();
                questionText = questionText.replaceAll(" +", " ").trim();
            } else {
                continue;
            }
            
            if (questionText == null || questionText.isEmpty()) {
                continue;
            }
            
            Question question = new Question();
            question.setText(questionText);
            question.setType(QuestionType.MULTIPLE_CHOICE);
            pq.question = question;
            
            Map<Integer, AnswerOption> optionsByNumber = new HashMap<>();
            
            int correctAnswerPos = block.toLowerCase().indexOf("*правильный ответ*");
            if (correctAnswerPos < 0) {
                correctAnswerPos = block.toLowerCase().indexOf("правильный ответ");
            }
            
            int explanationPos = block.toLowerCase().indexOf("*объяснение*");
            if (explanationPos < 0) {
                explanationPos = block.toLowerCase().indexOf("объяснение");
            }
            
            int optionsEndPos = block.length();
            if (correctAnswerPos >= 0) {
                optionsEndPos = Math.min(optionsEndPos, correctAnswerPos);
            }
            if (explanationPos >= 0) {
                optionsEndPos = Math.min(optionsEndPos, explanationPos);
            }
            
            String optionsBlock = block.substring(0, optionsEndPos);
            Pattern answerPattern = Pattern.compile("(\\d+)\\)\\s*([^\\n]+?)(?=\\s*\\n\\s*\\d+\\)|\\s*\\n\\s*\\*правильный|\\s*\\n\\s*\\*объяснение|$)", Pattern.DOTALL | Pattern.MULTILINE);
            Matcher ansMatcher = answerPattern.matcher(optionsBlock);
            
            while (ansMatcher.find()) {
                int optionNumber = Integer.parseInt(ansMatcher.group(1).trim());
                String optionText = ansMatcher.group(2).trim();
                
                if (optionText.isEmpty()) continue;
                
                optionText = optionText.replaceAll("^\\*+|\\*+$", "").trim();
                optionText = optionText.replaceAll(" +", " ").trim();
                
                boolean isPlaceholder = false;
                if (optionText.equalsIgnoreCase("вариант ответа") || 
                    optionText.matches("^\\*+$") ||
                    (optionText.length() <= 2 && !optionText.matches(".*[\\d\\w].*"))) {
                    isPlaceholder = true;
                }
                
                if (isPlaceholder) continue;
                
                if (optionText.startsWith("=")) {
                    continue;
                }
                
                AnswerOption option = new AnswerOption();
                String optionLower = optionText.toLowerCase();
                boolean hasIncorrectMarker = optionLower.contains("неправильн")
                        || optionLower.contains("неверн")
                        || optionLower.contains("не верн");
                boolean hasCorrectMarker = !hasIncorrectMarker
                        && (optionLower.contains("правильн")
                        || optionLower.contains("верн")
                        || optionLower.contains("верный"));

                option.setText(optionText);
                option.setCorrect(hasCorrectMarker);
                
                optionsByNumber.put(optionNumber, option);
                pq.answerOptions.add(option);
            }
            
            if (pq.answerOptions.size() < 2) {
                continue;
            }
            
            List<Integer> correctNums = new ArrayList<>();
            java.util.Set<Integer> correctNumsSet = new java.util.LinkedHashSet<>();

            String lowerBlock = block.toLowerCase();
            int startPos = -1;
            String[] pluralKeywords = new String[]{
                    "правильные ответы",
                    "верные ответы",
                    "правильные варианты",
                    "верные варианты"
            };
            for (String kw : pluralKeywords) {
                int pos = lowerBlock.indexOf(kw);
                if (pos >= 0) {
                    startPos = pos;
                    break;
                }
            }

            if (startPos >= 0) {
                int endPos = block.length();
                int explPos = lowerBlock.indexOf("объяснение", startPos);
                if (explPos > startPos) {
                    endPos = Math.min(endPos, explPos);
                }
                int nextQPos = lowerBlock.indexOf("следующий вопрос", startPos);
                if (nextQPos > startPos) {
                    endPos = Math.min(endPos, nextQPos);
                }

                String pluralSection = block.substring(startPos, endPos);
                Matcher numberMatcher = Pattern.compile("\\d+").matcher(pluralSection);
                while (numberMatcher.find()) {
                    parseInteger(numberMatcher.group()).ifPresent(correctNumsSet::add);
                }
            }

            correctNums.addAll(correctNumsSet);

            int correctNum = correctNums.isEmpty() ? -1 : -2;
            
            if (correctNum == -1) {
                Pattern correctPattern = Pattern.compile("(?i)\\*правильный ответ\\*\\s*\\n\\s*(\\d+)", Pattern.DOTALL | Pattern.MULTILINE);
                Matcher correctMatcher = correctPattern.matcher(block);
                if (correctMatcher.find()) {
                    correctNum = parseInteger(correctMatcher.group(1).trim()).orElse(correctNum);
                }
            
                if (correctNum < 0) {
                    correctPattern = Pattern.compile("(?i)\\*правильный ответ\\*[\\s\\n]+(\\d+)", Pattern.DOTALL | Pattern.MULTILINE);
                    correctMatcher = correctPattern.matcher(block);
                    if (correctMatcher.find()) {
                        correctNum = parseInteger(correctMatcher.group(1).trim()).orElse(correctNum);
                    }
                }
            
                if (correctNum < 0) {
                    correctPattern = Pattern.compile("(?i)правильный ответ[\\s\\n]+(\\d+)", Pattern.DOTALL | Pattern.MULTILINE);
                    correctMatcher = correctPattern.matcher(block);
                    if (correctMatcher.find()) {
                        correctNum = parseInteger(correctMatcher.group(1).trim()).orElse(correctNum);
                    }
                }
            
                if (correctNum < 0) {
                    int correctAnswerKeywordPos = block.toLowerCase().indexOf("правильный ответ");
                    if (correctAnswerKeywordPos >= 0) {
                        String afterKeyword = block.substring(correctAnswerKeywordPos);
                        Pattern numberPattern = Pattern.compile("\\d+");
                        Matcher numberMatcher = numberPattern.matcher(afterKeyword);
                        if (numberMatcher.find()) {
                            correctNum = parseInteger(numberMatcher.group()).orElse(correctNum);
                        }
                    }
                }
            }
            
            if (!correctNums.isEmpty()) {
                for (AnswerOption opt : pq.answerOptions) {
                    opt.setCorrect(false);
                }

                for (Integer num : correctNums) {
                    if (num == null) continue;
                    AnswerOption correctOption = optionsByNumber.get(num);
                    if (correctOption != null) {
                        correctOption.setCorrect(true);
                    }
                }

                boolean hasAnyCorrect = pq.answerOptions.stream().anyMatch(AnswerOption::isCorrect);
                if (!hasAnyCorrect && !pq.answerOptions.isEmpty()) {
                    pq.answerOptions.get(0).setCorrect(true);
                }
            } else {
                if (correctNum > 0) {
                    for (AnswerOption opt : pq.answerOptions) {
                        opt.setCorrect(false);
                    }
                    AnswerOption correctOption = optionsByNumber.get(correctNum);
                    if (correctOption != null) {
                        correctOption.setCorrect(true);
                    } else {
                        int pos = block.toLowerCase().indexOf("правильный ответ");
                        if (pos >= 0) {
                            int start = Math.max(0, pos - 50);
                            int end = Math.min(block.length(), pos + 100);
                        }
                        if (!pq.answerOptions.isEmpty()) {
                            pq.answerOptions.get(0).setCorrect(true);
                        }
                    }
                } else {
                    int pos = block.toLowerCase().indexOf("правильный ответ");
                    if (pos >= 0) {
                        int start = Math.max(0, pos - 50);
                        int end = Math.min(block.length(), pos + 100);
                    } else {
                    }
                    boolean hasAnyCorrect = pq.answerOptions.stream().anyMatch(AnswerOption::isCorrect);
                    if (!hasAnyCorrect && !pq.answerOptions.isEmpty()) {
                        pq.answerOptions.get(0).setCorrect(true);
                    }
                }
            }
            
            Pattern explanationPattern = Pattern.compile("(?i)(?:\\*)?объяснение(?:\\*)?[\\s\\n]+(.+?)(?=\\s*\\n\\s*\\*Следующий|$)", Pattern.DOTALL | Pattern.MULTILINE);
            Matcher expMatcher = explanationPattern.matcher(block);
            
            if (expMatcher.find()) {
                String explanation = expMatcher.group(1).trim();
                explanation = explanation.replaceAll("^\\*+|\\*+$", "").trim();
                explanation = explanation.replaceAll(" +", " ").replaceAll("\\n+", " ").trim();
                question.setExplanation(explanation);
            } else {
                question.setExplanation("Объяснение отсутствует");
            }
            
            if (question.getText() == null || question.getText().trim().isEmpty()) {
                continue;
            }
            
            if (pq.answerOptions.size() < 2) {
                continue;
            }
            
            boolean hasCorrect = pq.answerOptions.stream().anyMatch(AnswerOption::isCorrect);
            if (!hasCorrect && !pq.answerOptions.isEmpty()) {
                pq.answerOptions.get(0).setCorrect(true);
            }
            
            result.add(pq);
        }
        
        return result;
    }

    private static Optional<Integer> parseInteger(String value) {
        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
