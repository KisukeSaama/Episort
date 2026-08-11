package com.episort.filesystem;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Lightweight persisted identity for a media file without reading the whole file. */
public record MediaFileFingerprint(long size, long lastModifiedMillis, String sampleSha256) {
    private static final int SAMPLE_BYTES = 64 * 1024;

    public MediaFileFingerprint {
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
        Objects.requireNonNull(sampleSha256, "sampleSha256");
    }

    /**
     * Reads at most three 64 KiB regions: the start, middle, and end of the file.
     * The file size and sample offsets are included in the digest.
     */
    public static MediaFileFingerprint capture(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("File is missing or is not a regular file: " + path);
        }
        long size = Files.size(path);
        MessageDigest digest = sha256();
        digest.update(longBytes(size));

        Set<Long> offsets = new LinkedHashSet<>();
        offsets.add(0L);
        if (size > SAMPLE_BYTES) {
            offsets.add(Math.max(0, (size - SAMPLE_BYTES) / 2));
            offsets.add(Math.max(0, size - SAMPLE_BYTES));
        }

        try (SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
            for (long offset : offsets) {
                int length = (int) Math.min(SAMPLE_BYTES, size - offset);
                digest.update(longBytes(offset));
                digest.update(intBytes(length));
                channel.position(offset);
                ByteBuffer buffer = ByteBuffer.allocate(length);
                while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
                    // Continue until the selected region is full or EOF is reached.
                }
                if (buffer.hasRemaining()) {
                    throw new IOException("File changed while its fingerprint was being read: " + path);
                }
                digest.update(buffer.array());
            }
        }

        return new MediaFileFingerprint(
                size,
                Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis(),
                HexFormat.of().formatHex(digest.digest()));
    }

    /** Fast size rejection followed by the bounded sample comparison. */
    public boolean matches(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) != size) {
            return false;
        }
        return sampleSha256.equals(capture(path).sampleSha256());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static byte[] longBytes(long value) {
        return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
    }

    private static byte[] intBytes(int value) {
        return ByteBuffer.allocate(Integer.BYTES).putInt(value).array();
    }
}
