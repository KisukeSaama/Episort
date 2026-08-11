package com.episort.workflow;

import com.episort.tmdb.TmdbIdentity;
import com.episort.tmdb.TmdbMediaType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class TmdbIdentitySelectionService {
    private final Map<String, TmdbIdentity> selectionsByGroupKey = new LinkedHashMap<>();

    public void select(String groupKey, TmdbIdentity identity) {
        selectionsByGroupKey.put(requireText(groupKey, "groupKey"), Objects.requireNonNull(identity, "identity"));
    }

    public Optional<TmdbIdentity> selectedIdentity(String groupKey) {
        return Optional.ofNullable(selectionsByGroupKey.get(requireText(groupKey, "groupKey")));
    }

    public void clear(String groupKey) {
        selectionsByGroupKey.remove(requireText(groupKey, "groupKey"));
    }

    public List<List<String>> mergeableGroupKeys() {
        Map<String, List<String>> keysByIdentity = new LinkedHashMap<>();
        selectionsByGroupKey.forEach((groupKey, identity) ->
                keysByIdentity.computeIfAbsent(identity.mediaType() + ":" + identity.id(), ignored -> new ArrayList<>()).add(groupKey));
        return keysByIdentity.values().stream().filter(keys -> keys.size() > 1).map(List::copyOf).toList();
    }

    public boolean canChangeBeforePatternValidation(String groupKey, TmdbIdentity nextIdentity) {
        TmdbIdentity current = selectionsByGroupKey.get(requireText(groupKey, "groupKey"));
        return current == null || current.mediaType() == Objects.requireNonNull(nextIdentity, "nextIdentity").mediaType();
    }

    public boolean selectionAuthorizesValidationOrExecution() {
        return false;
    }

    public boolean hasSelectionForType(String groupKey, TmdbMediaType mediaType) {
        return selectedIdentity(groupKey).map(identity -> identity.mediaType() == mediaType).orElse(false);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return trimmed;
    }
}
