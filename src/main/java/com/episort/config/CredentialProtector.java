package com.episort.config;

interface CredentialProtector {
    String format();

    byte[] protect(byte[] plaintext);

    byte[] unprotect(byte[] protectedData);
}
