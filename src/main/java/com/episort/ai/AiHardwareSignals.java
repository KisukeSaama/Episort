package com.episort.ai;

public record AiHardwareSignals(boolean gpuAvailable, int vramMegabytes) {
    public static final int MINIMUM_VRAM_MEGABYTES = 4_096;

    public boolean minimumVramAvailable() {
        return vramMegabytes >= MINIMUM_VRAM_MEGABYTES;
    }
}
