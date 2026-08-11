package com.episort.workflow;

import com.episort.config.JanusConfiguration;

@FunctionalInterface
public interface TmdbConnectionTester {
    TmdbConnectionTestResult test(JanusConfiguration credentials);
}
