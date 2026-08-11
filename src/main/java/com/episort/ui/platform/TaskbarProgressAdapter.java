package com.episort.ui.platform;


public interface TaskbarProgressAdapter {
    boolean isSupported();

    void showIndeterminate(String phase);

    void showProgress(String phase, double fraction);

    void showNotification(String title, String body);

    void clear();
}
