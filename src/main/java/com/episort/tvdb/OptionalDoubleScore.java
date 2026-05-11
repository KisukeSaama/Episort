package com.episort.tvdb;

public record OptionalDoubleScore(boolean present, double value) {
    public static OptionalDoubleScore empty() {
        return new OptionalDoubleScore(false, 0.0);
    }

    public static OptionalDoubleScore of(double value) {
        return new OptionalDoubleScore(true, Math.max(0.0, Math.min(1.0, value)));
    }
}
