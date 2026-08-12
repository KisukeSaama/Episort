package com.episort.ui.scan;

import com.episort.matching.MediaMatchProposal;
import com.episort.matching.MediaMatchType;
import java.util.Objects;
import java.util.Optional;

/** Resolves whether a movie identity mismatch still requires user attention. */
final class TmdbMovieAlertPolicy {
    private TmdbMovieAlertPolicy() {
    }

    static Optional<String> blockingAlert(
            boolean identityConfirmedByUser,
            MediaMatchProposal proposal) {
        Objects.requireNonNull(proposal, "proposal");
        if (identityConfirmedByUser || proposal.type() != MediaMatchType.UNMATCHED) {
            return Optional.empty();
        }
        return Optional.ofNullable(proposal.reason()).filter(reason -> !reason.isBlank());
    }
}
