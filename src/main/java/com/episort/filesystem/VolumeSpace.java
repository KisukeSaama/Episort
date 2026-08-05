package com.episort.filesystem;

/** Capacity reported by the logical volume that contains the workspace. */
public record VolumeSpace(long totalBytes, long usedBytes, long availableBytes) {
    public VolumeSpace {
        if (totalBytes < 0 || usedBytes < 0 || availableBytes < 0) {
            throw new IllegalArgumentException("Volume sizes cannot be negative");
        }
        if (usedBytes > totalBytes || availableBytes > totalBytes) {
            throw new IllegalArgumentException("Volume sizes cannot exceed total capacity");
        }
    }

    public double usedFraction() {
        return totalBytes == 0 ? 0 : (double) usedBytes / totalBytes;
    }
}
