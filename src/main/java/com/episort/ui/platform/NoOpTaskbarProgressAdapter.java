package com.episort.ui.platform;


public final class NoOpTaskbarProgressAdapter implements TaskbarProgressAdapter {
    @Override
    public boolean isSupported() {
        return false;
    }

    @Override
    public void showIndeterminate(String phase) {
    }

    @Override
    public void showProgress(String phase, double fraction) {
    }

    @Override
    public void showNotification(String title, String body) {
    }

    @Override
    public void clear() {
    }
}
