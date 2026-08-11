package com.episort.ui.scan;


/**
 * One line of the identity validation screen: a detected group, how many files
 * it holds, and the TMDB identity that will name them.
 *
 * @param groupName    the seed the scanner grouped the files under
 * @param fileCount    how many files carry that identity
 * @param identityText the TMDB identity as shown to the user, or a placeholder
 * @param stateText    how the identity was obtained (automatic, suggested, manual)
 * @param resolved     false when no TMDB identity was found at all
 * @param confirmed    true when a human already stands behind this identity
 */
record GroupIdentityRow(
        String groupName,
        int fileCount,
        String identityText,
        String stateText,
        boolean resolved,
        boolean confirmed) {
}
