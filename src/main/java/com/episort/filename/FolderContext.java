package com.episort.filename;

import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the folders around a file say about it.
 *
 * <p>A file named {@code E02.mkv} is unparseable on its own and perfectly clear
 * inside {@code Breaking Bad/Season 01/}; a file named {@code sgi-hkyu01.mkv}
 * only becomes "Haikyu S01E01" thanks to its
 * {@code Haikyu.S01.MULTi.1080p.BluRay.x264-GRP} folder. The parser therefore
 * never looks at a name in isolation when folders are available.
 *
 * <p>Folder names are release names too, so they go through the same tag
 * masking as file names — otherwise the series title would come out as
 * "Haikyu S01 MULTi 1080p BluRay x264-GRP".
 */
public record FolderContext(String parentFolder, String grandParentFolder) {
    private static final FolderContext EMPTY = new FolderContext("", "");
    private static final Pattern SEASON_FOLDER = Pattern.compile(
            "(?i)^(?:season|saison|series|s|staffel|temporada)[\\s._-]?(\\d{1,3})$");
    private static final Pattern SPECIALS_FOLDER = Pattern.compile(
            "(?i)^(?:specials?|saison[\\s._-]?0+|season[\\s._-]?0+|extras?|bonus)$");
    /**
     * Folders that describe a library, not a title. Using one of these as a
     * series name would silently rename files after the user's storage layout.
     */
    private static final Set<String> GENERIC_FOLDERS = Set.of(
            "media", "medias", "video", "videos", "movie", "movies", "film", "films", "serie", "series",
            "tv", "tvshows", "tv shows", "shows", "anime", "animes", "download", "downloads",
            "torrent", "torrents", "complete", "incomplete", "new", "temp", "tmp", "input", "output",
            "desktop", "documents", "users", "public", "home", "data", "plex", "jellyfin", "emby");
    private static final Pattern SEASON_MARKER = Pattern.compile(
            "(?i)(?<![a-z0-9])(?:s|season|saison|staffel|temporada)[\\s._-]{0,2}(\\d{1,3})(?!\\d)");

    public FolderContext {
        parentFolder = parentFolder == null ? "" : parentFolder.trim();
        grandParentFolder = grandParentFolder == null ? "" : grandParentFolder.trim();
    }

    public static FolderContext none() {
        return EMPTY;
    }

    public static FolderContext of(Path file) {
        if (file == null) {
            return EMPTY;
        }
        Path parent = file.getParent();
        if (parent == null) {
            return EMPTY;
        }
        return new FolderContext(name(parent), name(parent.getParent()));
    }

    /** Season number carried by the folders, if any. */
    public OptionalInt season() {
        Matcher exact = SEASON_FOLDER.matcher(parentFolder);
        if (exact.matches()) {
            return parseInt(exact.group(1));
        }
        if (SPECIALS_FOLDER.matcher(parentFolder).matches()) {
            return OptionalInt.of(0);
        }
        return analyze(parentFolder).season();
    }

    /**
     * Series title implied by the folders: the parent folder when it carries a
     * title, otherwise the grandparent when the parent is a bare
     * {@code Season 01} / {@code Specials} folder.
     */
    public Optional<String> seriesTitle() {
        if (isSeasonOnlyFolder(parentFolder)) {
            return analyze(grandParentFolder).title();
        }
        return analyze(parentFolder).title();
    }

    /**
     * True when the folders state a season. Such a folder outranks the file name
     * for the series title: release folders carry the full title, files inside
     * them are often abbreviated.
     */
    public boolean carriesSeason() {
        return season().isPresent();
    }

    /** True when the parent folder is a specials/extras folder. */
    public boolean specials() {
        return SPECIALS_FOLDER.matcher(parentFolder).matches();
    }

    /**
     * True when {@code fileTitle} and the folder title can name the same series,
     * and the folder may therefore speak for the file.
     *
     * <p>A release folder carries the full title while the files inside it are
     * routinely abbreviated, so {@code sgi-hkyu01.mkv} inside
     * {@code Haikyu.S01.MULTi...} really is Haikyu and the folder has to win.
     *
     * <p>But a folder holding two different series speaks for one of them only.
     * Letting it overwrite {@code My.Hero.Academia.Vigilantes.S01E01.mkv} with
     * "Haikyu" merged both shows into a single group, sent a single TVDB query,
     * and renamed one series after the other. When the file name states a title
     * the folder cannot plausibly abbreviate, the file name wins.
     */
    public boolean agreesWith(String fileTitle) {
        if (fileTitle == null || fileTitle.isBlank()) {
            return true;
        }
        Optional<String> folderTitle = seriesTitle();
        if (folderTitle.isEmpty()) {
            return true;
        }
        String file = normalize(fileTitle);
        String folder = normalize(folderTitle.get());
        if (file.isEmpty() || folder.isEmpty()) {
            return true;
        }
        if (file.contains(folder) || folder.contains(file)) {
            return true;
        }
        List<String> fileTokens = tokens(fileTitle);
        List<String> folderTokens = tokens(folderTitle.get());
        for (String left : fileTokens) {
            for (String right : folderTokens) {
                if (left.equals(right) || abbreviates(left, right) || abbreviates(right, left)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * True when {@code shortForm} reads as a contraction of {@code longForm}:
     * same first letter, every letter present in order. That covers the way
     * release groups shorten titles ("hkyu" for "haikyu", "bbt" for "bigbang")
     * without matching two unrelated words that merely share letters — the
     * first-letter anchor and the three-character floor do the filtering.
     */
    private static boolean abbreviates(String shortForm, String longForm) {
        if (shortForm.length() < 3 || shortForm.length() >= longForm.length()) {
            return false;
        }
        if (shortForm.charAt(0) != longForm.charAt(0)) {
            return false;
        }
        int cursor = 0;
        for (int index = 0; index < longForm.length() && cursor < shortForm.length(); index++) {
            if (longForm.charAt(index) == shortForm.charAt(cursor)) {
                cursor++;
            }
        }
        return cursor == shortForm.length();
    }

    /** Lowercase letters and digits only: punctuation and accents are noise here. */
    private static String normalize(String value) {
        String folded = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        StringBuilder builder = new StringBuilder(folded.length());
        for (char character : folded.toCharArray()) {
            if (Character.isLetterOrDigit(character)) {
                builder.append(Character.toLowerCase(character));
            }
        }
        return builder.toString();
    }

    /** Words of a title, normalized, keeping only those long enough to mean something. */
    private static List<String> tokens(String value) {
        return Arrays.stream(value.split("[^\\p{L}\\p{N}]+"))
                .map(FolderContext::normalize)
                .filter(token -> token.length() >= 3)
                .toList();
    }

    private static boolean isSeasonOnlyFolder(String folder) {
        return SEASON_FOLDER.matcher(folder).matches() || SPECIALS_FOLDER.matcher(folder).matches();
    }

    /** Splits a folder name into "title before the season marker" plus season. */
    private static Identity analyze(String folder) {
        if (folder == null || folder.isBlank() || isDriveRoot(folder)) {
            return new Identity(Optional.empty(), OptionalInt.empty());
        }
        if (isSeasonOnlyFolder(folder)) {
            Matcher exact = SEASON_FOLDER.matcher(folder);
            return new Identity(Optional.empty(),
                    exact.matches() ? parseInt(exact.group(1)) : OptionalInt.of(0));
        }
        List<ReleaseTag> tags = ReleaseTagLexer.scan(folder);
        boolean[] mask = ReleaseTagLexer.mask(folder.length(), tags);
        Matcher marker = SEASON_MARKER.matcher(folder);
        int titleEnd = folder.length();
        OptionalInt season = OptionalInt.empty();
        while (marker.find()) {
            if (isFree(mask, marker.start(), marker.end())) {
                season = parseInt(marker.group(1));
                titleEnd = marker.start();
                break;
            }
        }
        String title = FilenameParser.cleanRange(folder, 0, titleEnd, mask);
        boolean usable = !title.isBlank() && !GENERIC_FOLDERS.contains(title.toLowerCase(Locale.ROOT));
        return new Identity(usable ? Optional.of(title) : Optional.empty(), season);
    }

    private static boolean isFree(boolean[] mask, int start, int end) {
        for (int index = start; index < Math.min(end, mask.length); index++) {
            if (mask[index]) {
                return false;
            }
        }
        return true;
    }

    private static OptionalInt parseInt(String value) {
        try {
            return OptionalInt.of(Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
    }

    private static boolean isDriveRoot(String value) {
        return value.length() <= 3 && value.toLowerCase(Locale.ROOT).matches("[a-z]:\\\\?");
    }

    private static String name(Path path) {
        if (path == null) {
            return "";
        }
        Path fileName = path.getFileName();
        return fileName == null ? "" : fileName.toString();
    }

    private record Identity(Optional<String> title, OptionalInt season) {
    }
}
