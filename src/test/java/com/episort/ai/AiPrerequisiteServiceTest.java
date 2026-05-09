package com.episort.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiPrerequisiteServiceTest {
    @Test
    void availableRuntimeAllowsAiWorkflow() {
        AiPrerequisiteService service = new AiPrerequisiteService(() -> AiRuntimeStatus.available(
                new AiHardwareSignals(true, 8_192),
                AiBundledModel.EPISORT_PATTERN_ASSISTANT_V1,
                "local-runtime"));

        AiPrerequisiteCheckResult result = service.check();

        assertTrue(result.aiWorkflowsAvailable());
        assertTrue(result.error().isEmpty());
        assertEquals(AiBundledModel.EPISORT_PATTERN_ASSISTANT_V1, result.model());
    }

    @Test
    void missingRuntimeGpuVramAndModelBlockOnlyAiWorkflowsWithRecoverableError() {
        AiPrerequisiteService service = new AiPrerequisiteService(() -> AiRuntimeStatus.unavailable(
                new AiHardwareSignals(false, 2_048),
                false,
                "C:\\Users\\private\\Videos\\Show\\episode01.mkv"));

        AiPrerequisiteCheckResult result = service.check();

        assertFalse(result.aiWorkflowsAvailable());
        assertTrue(result.nonAiWorkflowsAvailable());
        assertEquals("AI_PREREQUISITES_UNAVAILABLE", result.error().orElseThrow().code());
        assertTrue(result.error().orElseThrow().recoverable());
        assertFalse(result.error().orElseThrow().details().contains("episode01.mkv"));
        assertFalse(result.error().orElseThrow().details().contains("C:\\Users\\private"));
        assertTrue(result.missingPrerequisites().containsAll(List.of(
                AiPrerequisite.RUNTIME,
                AiPrerequisite.GPU,
                AiPrerequisite.VRAM,
                AiPrerequisite.MODEL)));
    }

    @Test
    void vramBelowMinimumBlocksAiEvenWhenGpuAndRuntimeExist() {
        AiPrerequisiteService service = new AiPrerequisiteService(() -> AiRuntimeStatus.unavailable(
                new AiHardwareSignals(true, 3_000),
                true,
                "VRAM below required minimum"));

        AiPrerequisiteCheckResult result = service.check();

        assertFalse(result.aiWorkflowsAvailable());
        assertTrue(result.missingPrerequisites().contains(AiPrerequisite.VRAM));
    }

    @Test
    void onlyOneBundledModelIsSupported() {
        assertEquals(1, AiBundledModel.values().length);
        assertEquals(AiBundledModel.EPISORT_PATTERN_ASSISTANT_V1, AiBundledModel.values()[0]);
    }

    @Test
    void defaultConfigurationUsesOnlyBundledModelAndRejectsExternalModelPath() {
        AiModelConfiguration configuration = AiModelConfiguration.bundledOnly();

        assertEquals(AiBundledModel.EPISORT_PATTERN_ASSISTANT_V1, configuration.model());
        assertTrue(configuration.externalModelPath().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> AiModelConfiguration.external(
                Path.of("C:\\Users\\private\\models\\external.gguf")));
    }

    @Test
    void runtimeDiagnosticExposesModelIdentityAndStatusWithoutPrivateMediaMetadata() {
        AiPrerequisiteService service = new AiPrerequisiteService(() -> new AiRuntimeStatus(
                true,
                new AiHardwareSignals(true, 8_192),
                java.util.Optional.of(AiBundledModel.EPISORT_PATTERN_ASSISTANT_V1),
                "local-runtime",
                "C:\\Users\\private\\Videos\\Show\\episode01.mkv"));

        AiRuntimeDiagnostic diagnostic = service.diagnostic();

        assertEquals(AiBundledModel.EPISORT_PATTERN_ASSISTANT_V1.identity(), diagnostic.modelIdentity());
        assertTrue(diagnostic.runtimeAvailable());
        assertEquals("local-runtime", diagnostic.runtimeName());
        assertFalse(diagnostic.details().contains("episode01.mkv"));
        assertFalse(diagnostic.details().contains("C:\\Users\\private"));
    }

    @Test
    void defaultLocalProbeFailsClosedWithoutCloudFallback() {
        AiPrerequisiteService service = new AiPrerequisiteService(new UnavailableLocalAiRuntimeProbe());

        AiPrerequisiteCheckResult result = service.check();

        assertFalse(result.aiWorkflowsAvailable());
        assertTrue(result.nonAiWorkflowsAvailable());
        assertTrue(result.missingPrerequisites().contains(AiPrerequisite.RUNTIME));
    }
}
