package com.episort.planning;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * A blocking problem attached to one planned operation.
 *
 * @param detail        human-readable context; contains paths only, never credentials
 * @param duplicateOf   the file already holding this episode or movie, when the
 *                      conflict is about a duplicate the library already has.
 *                      Resolving in favour of the planned file has to remove it,
 *                      so the decision needs to name it.
 */
public record PlanConflict(PlanConflictType type, String detail, Optional<Path> duplicateOf) {
    public PlanConflict {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(duplicateOf, "duplicateOf");
    }

    public static PlanConflict of(PlanConflictType type, String detail) {
        return new PlanConflict(type, detail, Optional.empty());
    }

    /** A conflict that names the existing copy the planned file duplicates. */
    public static PlanConflict duplicateOf(PlanConflictType type, String detail, Path existing) {
        return new PlanConflict(type, detail, Optional.of(existing));
    }
}
