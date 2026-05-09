package com.episort.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

/**
 * End-to-end wiring of the bundled local AI runtime, prerequisite gate, pattern
 * assistant, and refinement service across a small representative inventory.
 *
 * <p>Uses a synthetic but realistic mini-inventory (one series with two episodes,
 * one movie). Runs entirely in-process against the bundled CPU-only runtime — no
 * cloud calls, no large fixtures, no real media folders.
 */
class BundledAiPatternRefinementIntegrationTest {

    @Test
    void bundledRuntimeRefinesRepresentativeInventoryWithAdvisorySuggestions() throws Exception {
        try (FakeLlamaServer fake = FakeLlamaServer.start()) {
            // Series group → Ollama returns SxxExx hint.
            fake.setNextContent("{\"patterns\":[\"SxxExx\"],\"explanation\":\"S01E0x naming\"}");
            AiPrerequisiteService prerequisiteService = new AiPrerequisiteService(fake.probe());
            AiWorkflowGate gate = new AiWorkflowGate(prerequisiteService);
            AiPatternRefinementService service = new AiPatternRefinementService(
                    gate, fake.patternAssistant());

            AiPatternRefinementResult result = service.refine(buildRepresentativeInventory());

            assertTrue(result.refined());
            assertTrue(result.skipReason().isEmpty());
            assertEquals(2, result.suggestions().size());
            for (AiGroupSuggestion suggestion : result.suggestions()) {
                assertTrue(suggestion.suggestion().advisoryOnly());
                assertFalse(suggestion.suggestion().validationAuthority());
                assertFalse(suggestion.suggestion().executionAuthority());
                assertTrue(suggestion.suggestion().suggestedPatterns().contains("SxxExx"));
            }
        }
    }

    @Test
    void unavailableRuntimeBlocksAiButPreservesNonAiWorkflows() {
        AiPrerequisiteService prerequisiteService = new AiPrerequisiteService(new UnavailableLocalAiRuntimeProbe());
        AiWorkflowGate gate = new AiWorkflowGate(prerequisiteService);
        AiPatternAssistant tripWire = request -> {
            throw new AssertionError("Assistant must not be invoked when AI runtime is unavailable.");
        };
        AiPatternRefinementService service = new AiPatternRefinementService(gate, tripWire);

        AiPatternRefinementResult result = service.refine(buildRepresentativeInventory());

        assertFalse(result.refined());
        assertTrue(result.skipReason().isPresent());
        assertTrue(prerequisiteService.check().nonAiWorkflowsAvailable());
    }

    private InventoryScanResult buildRepresentativeInventory() {
        Path showFolder = Path.of("Show.Name");
        InventoryItem ep1 = new InventoryItem(
                showFolder.resolve("Show.Name.S01E01.mkv"),
                "Show.Name.S01E01.mkv", "mkv", showFolder,
                InventoryItemType.SUPPORTED_VIDEO, true);
        InventoryItem ep2 = new InventoryItem(
                showFolder.resolve("Show.Name.S01E02.mkv"),
                "Show.Name.S01E02.mkv", "mkv", showFolder,
                InventoryItemType.SUPPORTED_VIDEO, true);
        Path movieFolder = Path.of("Movies");
        InventoryItem movie = new InventoryItem(
                movieFolder.resolve("The Movie (2023).mkv"),
                "The Movie (2023).mkv", "mkv", movieFolder,
                InventoryItemType.SUPPORTED_VIDEO, true);
        InventoryGroup seriesGroup = new InventoryGroup(
                InventoryGroupType.LIKELY_SERIES, "Show.Name", List.of(ep1, ep2), false);
        InventoryGroup movieGroup = new InventoryGroup(
                InventoryGroupType.LIKELY_MOVIE, "The Movie (2023)", List.of(movie), false);
        return new InventoryScanResult(
                List.of(ep1, ep2, movie),
                List.of(seriesGroup, movieGroup),
                new InventorySummary(3, 0, 0, 0, 1, 1, 0, false, false));
    }
}
