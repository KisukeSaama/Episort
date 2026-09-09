package com.episort.workflow;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What an {@code episort://} link asks for: a folder of the workspace, and
 * optionally some of the files in it.
 *
 * <pre>
 * episort://open?volume=Media&amp;path=Series%2FSome+Show&amp;file=ep1.mkv&amp;file=ep2.mkv
 * </pre>
 *
 * <p>The link never carries an absolute path. It names a volume the way Umbra
 * labels it and a list of folder names below that volume's root, which
 * Episort resolves under its own workspace root: the two are configured to be
 * the same folder on the server. Every name is refused if it could step out of
 * that folder, and a request that fails any check is dropped whole rather than
 * partly honoured.
 *
 * @param volume the volume label, informational
 * @param folder folder names below the root, outermost first
 * @param files  file names inside that folder, empty for the whole folder
 */
public record LaunchRequest(String volume, List<String> folder, List<String> files) {
    public static final String SCHEME = "episort";
    public static final String ACTION_OPEN = "open";
    /** The same bound Umbra puts on a batch; a longer link is not one a browser sends anyway. */
    public static final int MAX_FILES = 200;

    public LaunchRequest {
        volume = Objects.requireNonNull(volume, "volume");
        folder = List.copyOf(folder);
        files = List.copyOf(files);
        for (String name : folder) {
            requireSafeName(name);
        }
        for (String name : files) {
            requireSafeName(name);
        }
        if (files.size() > MAX_FILES) {
            throw new IllegalArgumentException("Too many files in one request");
        }
    }

    /** The first {@code episort://} argument, parsed; empty when there is none or it is malformed. */
    public static Optional<LaunchRequest> fromArguments(List<String> arguments) {
        if (arguments == null) {
            return Optional.empty();
        }
        return arguments.stream()
                .filter(Objects::nonNull)
                .filter(argument -> argument.regionMatches(true, 0, SCHEME + ":", 0, SCHEME.length() + 1))
                .findFirst()
                .flatMap(LaunchRequest::parse);
    }

    public static Optional<LaunchRequest> parse(String link) {
        URI uri;
        try {
            uri = new URI(link.trim());
        } catch (URISyntaxException | NullPointerException exception) {
            return Optional.empty();
        }
        if (!SCHEME.equalsIgnoreCase(uri.getScheme()) || !ACTION_OPEN.equalsIgnoreCase(uri.getHost())) {
            return Optional.empty();
        }
        String volume = "";
        List<String> folder = new ArrayList<>();
        List<String> files = new ArrayList<>();
        String query = uri.getRawQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                if (pair.isEmpty()) {
                    continue;
                }
                int separator = pair.indexOf('=');
                String key = decode(separator < 0 ? pair : pair.substring(0, separator));
                String value = separator < 0 ? "" : decode(pair.substring(separator + 1));
                switch (key) {
                    case "volume" -> volume = value;
                    case "path" -> {
                        for (String segment : value.split("/")) {
                            if (!segment.isEmpty()) {
                                folder.add(segment);
                            }
                        }
                    }
                    case "file" -> files.add(value);
                    default -> {
                        // Unknown keys are ignored so a newer Umbra can add hints.
                    }
                }
            }
        }
        try {
            return Optional.of(new LaunchRequest(volume, folder, files));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /** The requested folder under a workspace root. */
    public Path folderUnder(Path root) {
        Path resolved = root;
        for (String name : folder) {
            resolved = resolved.resolve(name);
        }
        return resolved;
    }

    /** The requested files under a workspace root, in link order. */
    public List<Path> filesUnder(Path root) {
        Path base = folderUnder(root);
        return files.stream().map(base::resolve).toList();
    }

    public boolean hasFiles() {
        return !files.isEmpty();
    }

    /** {@code Media/Series/Some Show}, for messages. */
    public String describe() {
        StringBuilder text = new StringBuilder(volume);
        for (String name : folder) {
            if (!text.isEmpty()) {
                text.append('/');
            }
            text.append(name);
        }
        return text.toString();
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void requireSafeName(String name) {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..")
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Unsafe name in launch request");
        }
    }
}
