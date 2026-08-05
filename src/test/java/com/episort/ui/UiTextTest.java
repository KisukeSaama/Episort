package com.episort.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.planning.PlanConflictType;
import com.episort.planning.PlanExclusionReason;
import com.episort.workflow.ReviewSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class UiTextTest {
    @Test
    void everyKeyExistsInBothLanguageBundles() throws IOException {
        Set<String> english = keys("/i18n/messages.properties");
        Set<String> french = keys("/i18n/messages_fr.properties");

        Set<String> missingInFrench = new TreeSet<>(english);
        missingInFrench.removeAll(french);
        Set<String> missingInEnglish = new TreeSet<>(french);
        missingInEnglish.removeAll(english);

        assertTrue(missingInFrench.isEmpty(), "Missing French translations: " + missingInFrench);
        assertTrue(missingInEnglish.isEmpty(), "Missing English translations: " + missingInEnglish);
    }

    @Test
    void everyBlockerCodeHasLocalizedText() {
        List<String> codes = List.of(
                ReviewSession.BLOCKER_BLOCKING_CONFLICTS,
                ReviewSession.BLOCKER_PATTERN_NOT_VALIDATED,
                ReviewSession.BLOCKER_EXACT_PLAN_MISSING,
                ReviewSession.BLOCKER_EXACT_PLAN_NOT_VALIDATED);

        for (String code : codes) {
            for (AppLanguage language : AppLanguage.values()) {
                String text = UiText.scanExecutionBlockers(language, List.of(code));
                assertFalse(text.contains(code), code + " leaked its raw identifier in " + language);
            }
        }
    }

    @Test
    void anEmptyBlockerListReportsThatExecutionIsAllowed() {
        assertEquals(
                UiText.scanExecutionBlockers(AppLanguage.ENGLISH, List.of()),
                UiText.scanExecutionBlockers(AppLanguage.ENGLISH, List.of("SOMETHING_UNMAPPED")));
    }

    @Test
    void everyPlanConflictTypeHasLocalizedText() {
        for (PlanConflictType type : PlanConflictType.values()) {
            for (AppLanguage language : AppLanguage.values()) {
                assertNotEquals("—", UiText.planConflict(language, type.name()),
                        type + " has no localized text in " + language);
            }
        }
    }

    @Test
    void everyExclusionReasonExceptNoneHasLocalizedText() {
        for (PlanExclusionReason reason : PlanExclusionReason.values()) {
            if (reason == PlanExclusionReason.NONE) {
                continue;
            }
            for (AppLanguage language : AppLanguage.values()) {
                assertNotEquals("—", UiText.planExclusion(language, reason.name()),
                        reason + " has no localized text in " + language);
            }
        }
    }

    @Test
    void frenchAndEnglishWorkflowTextsActuallyDiffer() {
        assertNotEquals(
                UiText.primaryActionExecute(AppLanguage.ENGLISH),
                UiText.primaryActionExecute(AppLanguage.FRENCH));
        assertNotEquals(
                UiText.planValidate(AppLanguage.ENGLISH),
                UiText.planValidate(AppLanguage.FRENCH));
    }

    @Test
    void sectionLabelsDoNotUseDecorativeSlashPrefixes() {
        for (AppLanguage language : AppLanguage.values()) {
            List<String> labels = List.of(
                    UiText.scanHeading(language),
                    UiText.historyHeading(language),
                    UiText.historyDetailSectionSummary(language),
                    UiText.historyDetailSectionMetrics(language),
                    UiText.settingsHeading(language),
                    UiText.sidebarSectionNavigation(language),
                    UiText.sidebarSectionWorkspace(language),
                    UiText.detailSectionSource(language),
                    UiText.detailSectionDetection(language),
                    UiText.detailSectionTvdb(language),
                    UiText.detailSectionDestination(language),
                    UiText.detailSectionNotes(language),
                    UiText.aboutSectionPipeline(language),
                    UiText.aboutSectionEnvironment(language),
                    UiText.aboutSectionData(language),
                    UiText.aboutSectionCredits(language));

            for (String label : labels) {
                assertFalse(label.startsWith("//"), label + " uses a decorative slash prefix");
            }
        }
    }

    /**
     * A {@code UiText} accessor whose key does not exist only fails at runtime,
     * inside the dialog that needed it. Calling every no-argument accessor in
     * both languages turns that into a build failure instead.
     */
    @Test
    void everyNoArgUiTextAccessorResolvesInBothLanguages() {
        List<java.lang.reflect.Method> accessors = Arrays.stream(UiText.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .filter(method -> java.lang.reflect.Modifier.isStatic(method.getModifiers()))
                .filter(method -> method.getParameterCount() == 1)
                .filter(method -> method.getParameterTypes()[0] == AppLanguage.class)
                .toList();

        assertFalse(accessors.isEmpty(), "no UiText accessors were discovered");
        for (java.lang.reflect.Method accessor : accessors) {
            for (AppLanguage language : AppLanguage.values()) {
                try {
                    Object value = accessor.invoke(null, language);
                    assertTrue(value != null, accessor.getName() + " returned null for " + language);
                } catch (ReflectiveOperationException exception) {
                    throw new AssertionError(
                            accessor.getName() + " failed for " + language, exception.getCause());
                }
            }
        }
    }

    private static Set<String> keys(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream stream = UiTextTest.class.getResourceAsStream(resource)) {
            properties.load(new InputStreamReader(
                    Objects.requireNonNull(stream, "Missing " + resource),
                    StandardCharsets.UTF_8));
        }
        return new TreeSet<>(properties.stringPropertyNames());
    }
}
