package com.episort.config;

import java.util.Optional;

/**
 * Provides the project TVDB credential embedded in distributed builds.
 *
 * <p>The bytes are intentionally not stored as a readable string literal so
 * the key is not exposed by a casual source/binary scan. This is obfuscation,
 * not a cryptographic secret boundary: desktop clients that can authenticate
 * must still contain enough information to reconstruct the credential.</p>
 */
public final class EmbeddedTvdbCredentialsProvider {
    private static final int KEY_MASK = 0x5A;
    private static final int[] OBFUSCATED_API_KEY = {
            56, 105, 104, 60, 98, 109, 104, 108, 119, 98, 59, 105,
            105, 119, 110, 111, 108, 110, 119, 99, 57, 98, 57, 119,
            107, 62, 98, 62, 60, 111, 104, 98, 109, 107, 104, 108
    };

    private EmbeddedTvdbCredentialsProvider() {
    }

    public static Optional<TvdbCredentials> load() {
        String apiKey = decodeApiKey();
        return apiKey.isBlank()
                ? Optional.empty()
                : Optional.of(new TvdbCredentials(apiKey, Optional.empty()));
    }

    private static String decodeApiKey() {
        StringBuilder apiKey = new StringBuilder(OBFUSCATED_API_KEY.length);
        for (int value : OBFUSCATED_API_KEY) {
            apiKey.append((char) (value ^ KEY_MASK));
        }
        return apiKey.toString();
    }
}
