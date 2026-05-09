package com.episort.ai;

public record AiHardwareSignals(boolean gpuAvailable, int vramMegabytes) {
    public static final int MINIMUM_VRAM_MEGABYTES = 8_192;

    public boolean minimumVramAvailable() {
        return vramMegabytes >= MINIMUM_VRAM_MEGABYTES;
    }
}
