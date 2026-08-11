package com.episort.ui.settings;

import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;
import com.episort.workflow.TmdbGatewayStatus;
import java.util.Objects;

record TmdbStatusPresentation(boolean active, String text, String dotStyleClass) {
    static TmdbStatusPresentation from(
            TmdbGatewayStatus result,
            AppLanguage language) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(language, "language");

        boolean active = result.success();
        return new TmdbStatusPresentation(
                active,
                active ? UiText.tmdbSettingsActive(language) : UiText.tmdbSettingsInactive(language),
                active ? "dot-good" : "dot-error");
    }
}
