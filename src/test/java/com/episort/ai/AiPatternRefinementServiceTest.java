package com.episort.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.scanner.InventoryGroup;
import com.episort.scanner.InventoryGroupType;
import com.episort.scanner.InventoryItem;
import com.episort.scanner.InventoryItemType;
import com.episort.scanner.InventoryScanResult;
import com.episort.scanner.InventorySummary;
import com.episort.workflow.AiWorkflowGate;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiPatternRefinementServiceTest {

    @Test
    void skipsRefinementWhenAiPrerequisitesAreUnavailable() {
        AiWorkflowGate blockedGate = new AiWorkflowGate(new AiPrerequisiteService(
                new UnavailableLocalAiRuntimeProbe()));
        AiPatternAssistant assistant = request -> {
            throw new AssertionError("Assistant must not be called when gate blocks AI workflow.");
        };
        AiPatternRefinementService service = new AiPatternRefinementService(blockedGate, assistant);

        AiPatternRefinementResult result = service.refine(sampleInventory());

        assertFalse(result.refined());
        assertTrue(result.suggestions().isEmpty());
        assertTrue(result.skipReason().isPresent());
        assertEquals("AI_PREREQUISITES_UNAVAILABLE", result.skipReason().orElseThrow().code());
    }

    @Test
    void emitsAdvisorySuggestionPerCandidateGroupWhenGateAllows() throws Exception {
        try (FakeLlamaServer fake = FakeLlamaServer.start()) {
            AiWorkflowGate openGate = new AiWorkflowGate(new AiPrerequisiteService(fake.probe()));
            AiPatternRefinementService service = new AiPatternRefinementService(
                    openGate, fake.patternAssistant());

            AiPatternRefinementResult result = service.refine(sampleInventory());

            assertTrue(result.refined());
            assertTrue(result.skipReason().isEmpty());
            assertEquals(2, result.suggestions().size());
            for (AiGroupSuggestion suggestion : result.suggestions()) {
                assertTrue(suggestion.suggestion().advisoryOnly());
                assertFalse(suggestion.suggestion().validationAuthority());
                assertFalse(suggestion.suggestion().executionAuthority());
            }
        }
    }

    @Test
    void bundledAssistantParsesStructuredFilePatternsAndIgnoresMalformedJson() throws Exception {
        try (FakeLlamaServer fake = FakeLlamaServer.start()) {
            fake.setNextContent("""
                    {"mediaType":"series","confidence":0.88,"patterns":["SxxExx"],"files":[{"filename":"Show.S01E02.mkv","pattern":"SxxExx","tokens":[{"role":"SERIES","rawValue":"Show.","normalizedValue":"Show","start":0,"end":5},{"role":"SEASON","rawValue":"01","normalizedValue":"01","start":6,"end":8},{"role":"EPISODE","rawValue":"02","normalizedValue":"02","start":9,"end":11},{"role":"EXTENSION","rawValue":"mkv","normalizedValue":"mkv","start":12,"end":15}],"normalizedOrder":"S01E02","confidence":0.92}],"explanation":"parsed"}
                    """);

            AiPatternSuggestion structured = fake.patternAssistant()
                    .suggestPattern(new AiPatternSuggestionRequest(List.of("Show.S01E02.mkv"), ""));

            assertEquals(1, structured.fileParses().size());
            assertEquals("Show.S01E02.mkv", structured.fileParses().get(0).filename());
            assertEquals("S01E02", structured.fileParses().get(0).normalizedOrder().orElseThrow());

            fake.setNextContent("not json");
            AiPatternSuggestion malformed = fake.patternAssistant()
                    .suggestPattern(new AiPatternSuggestionRequest(List.of("Show.S01E02.mkv"), ""));

            assertTrue(malformed.fileParses().isEmpty());
            assertTrue(malformed.suggestedPatterns().isEmpty());
        }
    }

    @Test
    void aiSuggestionCannotBeConstructedWithValidationOrExecutionAuthority() {
        assertThrows(IllegalArgumentException.class, () -> new AiGroupSuggestion(
                "Show",
                InventoryGroupType.LIKELY_SERIES,
                new AiPatternSuggestion("x", List.of("SxxExx"), List.of("a.mkv"), false, true, false)));
    }

    @Test
    void constructorRejectsNullCollaborators() {
        assertThrows(NullPointerException.class, () -> new AiPatternRefinementService(null, request -> null));
        AiWorkflowGate gate = new AiWorkflowGate(new AiPrerequisiteService(new UnavailableLocalAiRuntimeProbe()));
        assertThrows(NullPointerException.class, () -> new AiPatternRefinementService(gate, null));
    }

    private InventoryScanResult sampleInventory() {
        InventoryItem ep1 = new InventoryItem(
                Path.of("Show.S01E01.mkv"), "Show.S01E01.mkv", "mkv",
                Path.of("Show"), InventoryItemType.SUPPORTED_VIDEO, true);
        InventoryItem ep2 = new InventoryItem(
                Path.of("Show.S01E02.mkv"), "Show.S01E02.mkv", "mkv",
                Path.of("Show"), InventoryItemType.SUPPORTED_VIDEO, true);
        InventoryItem movie = new InventoryItem(
                Path.of("Movie (2023).mkv"), "Movie (2023).mkv", "mkv",
                Path.of("Movie"), InventoryItemType.SUPPORTED_VIDEO, true);
        InventoryItem sidecar = new InventoryItem(
                Path.of("notes.nfo"), "notes.nfo", "nfo",
                Path.of("Show"), InventoryItemType.SIDECAR, false);
        InventoryGroup seriesGroup = new InventoryGroup(
                InventoryGroupType.LIKELY_SERIES, "Show", List.of(ep1, ep2), false);
        InventoryGroup movieGroup = new InventoryGroup(
                InventoryGroupType.LIKELY_MOVIE, "Movie", List.of(movie), false);
        InventoryGroup sidecarGroup = new InventoryGroup(
                InventoryGroupType.SIDECAR, "Sidecars", List.of(sidecar), false);
        return new InventoryScanResult(
                List.of(ep1, ep2, movie, sidecar),
                List.of(seriesGroup, movieGroup, sidecarGroup),
                new InventorySummary(3, 1, 0, 0, 1, 1, 0, false, false));
    }
}
