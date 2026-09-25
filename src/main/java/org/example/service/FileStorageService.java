package org.example.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@Slf4j
public class FileStorageService {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    private static final long MAX_TOTAL_SIZE = 5 * 1024 * 1024;
    private static final int MAX_FILES = 10;
    private static final List<String> ALLOWED_EXTENSIONS = List.of(".pdf", ".txt", ".doc", ".docx");

    private record PendingUpload(Path temporaryPath, Path targetPath, String fileUrl) {
    }

    public List<String> saveQuizMaterials(MultipartFile[] files, Long quizId) throws IOException {
        if (files == null || files.length == 0) {
            return new ArrayList<>();
        }

        if (files.length > MAX_FILES) {
            throw new IllegalArgumentException("Максимальное количество файлов: " + MAX_FILES);
        }

        long totalSize = 0;
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                totalSize += file.getSize();
            }
        }

        if (totalSize > MAX_TOTAL_SIZE) {
            throw new IllegalArgumentException("Общий размер файлов не должен превышать 5 МБ");
        }

        List<MultipartFile> filesToSave = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                throw new IllegalArgumentException("У файла отсутствует имя");
            }

            String extension = getFileExtension(originalFilename);
            if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Неподдерживаемый формат файла: " + extension + 
                        ". Разрешенные форматы: pdf, txt, doc, docx");
            }
            validateFileContent(file, extension.toLowerCase(Locale.ROOT));
            filesToSave.add(file);
        }

        if (filesToSave.isEmpty()) {
            return new ArrayList<>();
        }

        Path quizDir = Paths.get(uploadDir, "quizzes", String.valueOf(quizId));
        Files.createDirectories(quizDir);

        List<PendingUpload> pendingUploads = new ArrayList<>();
        try {
            // Сначала копируем все файлы во временное хранилище; ссылки не публикуются,
            // пока не будут успешно скопированы все входные файлы.
            for (MultipartFile file : filesToSave) {
                String extension = getFileExtension(file.getOriginalFilename());
                String uniqueFilename = UUID.randomUUID() + extension;
                Path targetPath = quizDir.resolve(uniqueFilename);
                Path temporaryPath = Files.createTempFile(quizDir, ".upload-", ".tmp");
                PendingUpload pending = new PendingUpload(
                        temporaryPath,
                        targetPath,
                        "/uploads/quizzes/" + quizId + "/" + uniqueFilename
                );
                pendingUploads.add(pending);
                try (InputStream input = file.getInputStream()) {
                    Files.copy(input, temporaryPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            for (PendingUpload pending : pendingUploads) {
                try {
                    Files.move(pending.temporaryPath(), pending.targetPath(), StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(pending.temporaryPath(), pending.targetPath());
                }
            }

            return pendingUploads.stream().map(PendingUpload::fileUrl).toList();
        } catch (IOException | RuntimeException e) {
            // При ошибке копирования или сохранения не оставляем на диске
            // доступные файлы из незавершённой загрузки.
            for (PendingUpload pending : pendingUploads) {
                deleteIfExists(pending.temporaryPath());
                deleteIfExists(pending.targetPath());
            }
            throw e;
        }
    }

    public void validateUploadedMaterial(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл пустой");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("У файла отсутствует имя");
        }
        String extension = getFileExtension(originalFilename).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Неподдерживаемый формат файла: " + extension
                    + ". Разрешенные форматы: pdf, txt, doc, docx");
        }
        validateFileContent(file, extension);
    }

    private void validateFileContent(MultipartFile file, String extension) throws IOException {
        byte[] content;
        try (InputStream input = file.getInputStream()) {
            content = input.readNBytes((int) MAX_TOTAL_SIZE + 1);
            if (content.length > MAX_TOTAL_SIZE) {
                throw new IllegalArgumentException("Размер файла не должен превышать 5 МБ");
            }
        }

        if (content.length == 0) {
            throw new IllegalArgumentException("Файл пустой");
        }

        boolean valid = switch (extension) {
            case ".pdf" -> startsWith(content, new byte[]{'%', 'P', 'D', 'F', '-'});
            case ".doc" -> startsWith(content, new byte[]{
                    (byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0,
                    (byte) 0xA1, (byte) 0xB1, (byte) 0x1A, (byte) 0xE1
            });
            case ".docx" -> isDocxArchive(content);
            case ".txt" -> isUtf8Text(content);
            default -> false;
        };

        if (!valid) {
            throw new IllegalArgumentException("Содержимое файла не соответствует расширению " + extension);
        }
    }

    private boolean isDocxArchive(byte[] content) {
        if (!startsWith(content, new byte[]{'P', 'K', 3, 4})
                && !startsWith(content, new byte[]{'P', 'K', 5, 6})) {
            return false;
        }

        Set<String> entries = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(entry.getName());
                while (zip.read(buffer) != -1) {
                    // Читаем запись целиком, чтобы обнаружить повреждённые данные ZIP.
                }
            }
        } catch (IOException e) {
            return false;
        }
        return entries.contains("[Content_Types].xml") && entries.contains("word/document.xml");
    }

    private boolean isUtf8Text(byte[] content) {
        if (new String(content, StandardCharsets.ISO_8859_1).indexOf('\0') >= 0) {
            return false;
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(java.nio.ByteBuffer.wrap(content));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (value[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException cleanupFailure) {
            log.warn("Не удалось удалить временный файл после неудачной загрузки: {}", path, cleanupFailure);
        }
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

    public Path resolveQuizMaterialUrl(Long quizId, String fileUrl) {
        String expectedPrefix = "/uploads/quizzes/" + quizId + "/";
        if (fileUrl == null || !fileUrl.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException("Материал не принадлежит этому квизу");
        }
        Path resolved = resolveMaterialUrl(fileUrl);
        Path quizDir = Paths.get(uploadDir, "quizzes", String.valueOf(quizId)).toAbsolutePath().normalize();
        if (!quizDir.equals(resolved.getParent())) {
            throw new IllegalArgumentException("Некорректный путь к материалу");
        }
        return resolved;
    }

    public String copyQuizMaterial(Long sourceQuizId, Long targetQuizId, String fileUrl) throws IOException {
        Path source = resolveQuizMaterialUrl(sourceQuizId, fileUrl);
        if (!Files.isRegularFile(source)) {
            throw new IOException("Материал исходного квиза не найден");
        }
        String extension = getFileExtension(source.getFileName().toString());
        Path targetDir = Paths.get(uploadDir, "quizzes", String.valueOf(targetQuizId));
        Files.createDirectories(targetDir);
        String newFilename = UUID.randomUUID() + extension;
        Files.copy(source, targetDir.resolve(newFilename));
        return "/uploads/quizzes/" + targetQuizId + "/" + newFilename;
    }

    public void deleteQuizMaterials(Long quizId) throws IOException {
        Path quizDir = Paths.get(uploadDir, "quizzes", String.valueOf(quizId));
        if (Files.exists(quizDir)) {
            try (var paths = Files.walk(quizDir)) {
                paths.sorted((a, b) -> b.compareTo(a))
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.debug("Не удалось удалить файл {}", path, e);
                            }
                        });
            }
        }
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDotIndex);
    }

}
