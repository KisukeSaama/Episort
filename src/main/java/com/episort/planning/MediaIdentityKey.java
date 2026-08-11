package com.episort.planning;

import com.episort.filename.FilenameParser;
import com.episort.filename.FolderContext;
import com.episort.filename.ParsedFilename;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * What makes two files "the same episode" or "the same movie", regardless of how
 * they are named.
 *
 * <p>Comparing destination paths is not enough: the very same episode carries a
 * different file name depending on whether TMDB gave up an episode title, so two
 * copies of one episode can each get a valid, distinct destination and land side
 * by side — doubling the space the library takes. This key is what the planner
 * compares instead.
 *
 * <p>Identity is always scoped to the folder the file lives in, which already
 * encodes the series and the season for episodes, and is the library root for
 * movies. Two files only ever compare equal when they would sit in the same
 * folder.
 */
record MediaIdentityKey(String value) {

    /** The identity a planned item will have once it reaches its destination. */
    static Optional<MediaIdentityKey> of(PlanSourceItem item, Path destination) {
        Path folder = destination.getParent();
        if (folder == null || !item.hasCompleteIdentity()) {
            return Optional.empty();
        }
        return switch (item.kind()) {
            case SERIES_EPISODE, SPECIAL -> item.episodeNumber().isPresent()
                    ? Optional.of(episode(folder, item.seasonNumber().orElse(0), item.episodeNumber().orElseThrow()))
                    : Optional.empty();
            case MOVIE -> Optional.of(movie(folder, item.movieTitle().orElseThrow(), item.movieYear()));
        };
    }

    /**
     * The identity of a file already sitting in the library, read with the shared
     * parser — the same one the rest of the application uses, so a name Episort
     * itself wrote is read back the way it was written.
     *
     * @return empty when the name says nothing usable; an unidentifiable file is
     *         never treated as a duplicate of anything
     */
    static Optional<MediaIdentityKey> ofExistingFile(Path file) {
        Path folder = file.getParent();
        if (folder == null) {
            return Optional.empty();
        }
        String name = file.getFileName().toString();
        ParsedFilename parsed = FilenameParser.parse(name, FolderContext.of(file));
        if (parsed.season().isPresent() && parsed.firstEpisode().isPresent()) {
            return Optional.of(episode(
                    folder, parsed.season().getAsInt(), parsed.firstEpisode().getAsInt()));
        }
        // No season/episode: the only other thing it can be is a movie, and a
        // movie without a readable title is not something to match on.
        return parsed.title()
                .map(MediaIdentityKey::normalize)
                .filter(title -> !title.isEmpty())
                .map(ignored -> movie(folder, parsed.title().orElseThrow(), parsed.year()));
    }

    private static MediaIdentityKey episode(Path folder, int season, int episode) {
        return new MediaIdentityKey(
                folderKey(folder) + "|episode|" + season + "|" + episode);
    }

    /**
     * Movies of the same name are only the same movie when their years agree. An
     * unknown year stays its own identity rather than matching every homonym: a
     * wrong merge here would mean deleting a film the user meant to keep.
     */
    private static MediaIdentityKey movie(Path folder, String title, OptionalInt year) {
        return new MediaIdentityKey(folderKey(folder) + "|movie|" + normalize(title)
                + "|" + (year.isPresent() ? String.valueOf(year.getAsInt()) : "?"));
    }

    private static String folderKey(Path folder) {
        return folder.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
    }

    /** Punctuation and case are release-group noise, not identity. */
    private static String normalize(String title) {
        return title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
