package org.example.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileStorageServiceTest {
    @TempDir Path uploadDir;

    @Test
    void copyGetsItsOwnFileAndCannotResolveAnotherQuizMaterial() throws Exception {
        FileStorageService storage = new FileStorageService();
        ReflectionTestUtils.setField(storage, "uploadDir", uploadDir.toString());
        MockMultipartFile material = new MockMultipartFile("files", "notes.pdf", "application/pdf",
                "%PDF-1.7\nsource material".getBytes());

        String originalUrl = storage.saveQuizMaterials(new MockMultipartFile[]{material}, 10L).get(0);
        String copiedUrl = storage.copyQuizMaterial(10L, 20L, originalUrl);

        assertNotEquals(originalUrl, copiedUrl);
        assertArrayEquals(Files.readAllBytes(storage.resolveQuizMaterialUrl(10L, originalUrl)),
                Files.readAllBytes(storage.resolveQuizMaterialUrl(20L, copiedUrl)));
        assertThrows(IllegalArgumentException.class,
                () -> storage.resolveQuizMaterialUrl(20L, originalUrl));
        assertThrows(IllegalArgumentException.class,
                () -> storage.resolveQuizMaterialUrl(10L, "/uploads/quizzes/10/../20/other.pdf"));
    }

    @Test
    void invalidFileDoesNotLeaveEarlierFilesOnDisk() throws Exception {
        FileStorageService storage = new FileStorageService();
        ReflectionTestUtils.setField(storage, "uploadDir", uploadDir.toString());
        MockMultipartFile valid = new MockMultipartFile("files", "notes.txt", "text/plain",
                "source material".getBytes());
        MockMultipartFile invalid = new MockMultipartFile("files", "broken.pdf", "application/pdf",
                "not a pdf".getBytes());

        assertThrows(IllegalArgumentException.class,
                () -> storage.saveQuizMaterials(new MockMultipartFile[]{valid, invalid}, 11L));

        Path quizDir = uploadDir.resolve("quizzes").resolve("11");
        if (Files.exists(quizDir)) {
            try (var files = Files.list(quizDir)) {
                assertTrue(files.findAny().isEmpty());
            }
        }
    }
}
