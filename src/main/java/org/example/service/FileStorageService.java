package org.example.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    private static final long MAX_TOTAL_SIZE = 5 * 1024 * 1024;
    private static final int MAX_FILES = 10;
    private static final List<String> ALLOWED_EXTENSIONS = List.of(".pdf", ".txt", ".doc", ".docx");

    public List<String> saveQuizMaterials(MultipartFile[] files, Long quizId) throws IOException {
        if (files == null || files.length == 0) {
            return new ArrayList<>();
        }

        if (files.length > MAX_FILES) {
            throw new IllegalArgumentException("Максимальное количество файлов: " + MAX_FILES);
        }

        long totalSize = 0;
        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                totalSize += file.getSize();
            }
        }

        if (totalSize > MAX_TOTAL_SIZE) {
            throw new IllegalArgumentException("Общий размер файлов не должен превышать 5 МБ");
        }

        Path quizDir = Paths.get(uploadDir, "quizzes", String.valueOf(quizId));
        Files.createDirectories(quizDir);

        List<String> savedFileUrls = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                continue;
            }

            String extension = getFileExtension(originalFilename);
            if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
                throw new IllegalArgumentException("Неподдерживаемый формат файла: " + extension + 
                        ". Разрешенные форматы: pdf, txt, doc, docx");
            }

            String uniqueFilename = UUID.randomUUID().toString() + extension;
            Path targetPath = quizDir.resolve(uniqueFilename);

            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            String fileUrl = "/uploads/quizzes/" + quizId + "/" + uniqueFilename;
            savedFileUrls.add(fileUrl);
        }

        return savedFileUrls;
    }

    public Path resolveMaterialUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new IllegalArgumentException("Путь к материалу пустой");
        }
        String prefix = "/uploads/";
        if (!fileUrl.startsWith(prefix)) {
            throw new IllegalArgumentException("Некорректный путь к материалу: " + fileUrl);
        }
        String relativePath = fileUrl.substring(prefix.length()).replace("/", java.io.File.separator);
        Path resolved = Paths.get(uploadDir).resolve(relativePath).normalize();
        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path absoluteResolved = resolved.toAbsolutePath().normalize();
        if (!absoluteResolved.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Некорректный путь к материалу: " + fileUrl);
        }
        return absoluteResolved;
    }

    public void deleteQuizMaterials(Long quizId) throws IOException {
        Path quizDir = Paths.get(uploadDir, "quizzes", String.valueOf(quizId));
        if (Files.exists(quizDir)) {
            Files.walk(quizDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.debug("Не удалось удалить файл {}", path, e);
                        }
                    });
        }
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDotIndex);
    }

    public void init() {
        try {
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать директорию для загрузки файлов", e);
        }
    }
}
