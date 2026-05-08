package com.episort.workflow;

import com.episort.config.TvdbCredentials;

@FunctionalInterface
public interface TvdbConnectionTester {
    TvdbConnectionTestResult test(TvdbCredentials credentials);
}
