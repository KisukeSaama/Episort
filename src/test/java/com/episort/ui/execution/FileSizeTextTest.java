package com.episort.ui.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSizeTextTest {

    @TempDir
    Path tempDir;

    @Test
    void formatsBinarySizesForBothLanguages() throws Exception {
        Path file = tempDir.resolve("episode.mkv");
        Files.write(file, new byte[1536]);

        assertEquals("1.5 KiB", FileSizeText.forPath(file, AppLanguage.ENGLISH));
        assertEquals("1,5 Kio", FileSizeText.forPath(file, AppLanguage.FRENCH));
    }

    @Test
    void missingFilesRenderTheSharedEmptyValue() {
        assertEquals(UiText.EMPTY, FileSizeText.forPath(tempDir.resolve("missing.mkv"), AppLanguage.FRENCH));
    }
}
