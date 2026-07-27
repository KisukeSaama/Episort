package com.episort.ui.settings;

import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;
import com.episort.workflow.TvdbCredentialConfigurationResult;
import java.util.Objects;

record TvdbStatusPresentation(boolean active, String text, String dotStyleClass) {
    static TvdbStatusPresentation from(
            TvdbCredentialConfigurationResult result,
            AppLanguage language) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(language, "language");

        boolean active = result.success();
        return new TvdbStatusPresentation(
                active,
                active ? UiText.tvdbSettingsActive(language) : UiText.tvdbSettingsInactive(language),
                active ? "dot-good" : "dot-error");
    }
}
