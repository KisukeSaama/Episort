package com.episort.config;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import java.util.Arrays;
import java.util.List;

final class WindowsDpapiCredentialProtector implements CredentialProtector {
    private static final String FORMAT = "windows-dpapi-v1";
    private static final int CRYPTPROTECT_UI_FORBIDDEN = 0x1;

    @Override
    public String format() {
        return FORMAT;
    }

    @Override
    public byte[] protect(byte[] plaintext) {
        return transform(plaintext, true);
    }

    @Override
    public byte[] unprotect(byte[] protectedData) {
        return transform(protectedData, false);
    }

    private byte[] transform(byte[] input, boolean protect) {
        DataBlob inputBlob = new DataBlob(input);
        DataBlob outputBlob = new DataBlob();
        try {
            boolean success = protect
                    ? Crypt32.INSTANCE.CryptProtectData(
                            inputBlob,
                            new WString("Episort TMDB credentials"),
                            null,
                            null,
                            null,
                            CRYPTPROTECT_UI_FORBIDDEN,
                            outputBlob)
                    : Crypt32.INSTANCE.CryptUnprotectData(
                            inputBlob,
                            null,
                            null,
                            null,
                            null,
                            CRYPTPROTECT_UI_FORBIDDEN,
                            outputBlob);
            if (!success) {
                throw new SettingsStoreException(
                        "Windows could not protect the TMDB credentials (error " + Native.getLastError() + ").",
                        null);
            }
            outputBlob.read();
            return outputBlob.toByteArray();
        } catch (UnsatisfiedLinkError | RuntimeException exception) {
            if (exception instanceof SettingsStoreException settingsStoreException) {
                throw settingsStoreException;
            }
            throw new SettingsStoreException("Windows credential protection is unavailable.", exception);
        } finally {
            inputBlob.clearOwnedMemory();
            outputBlob.clearAndFree();
        }
    }

    @Structure.FieldOrder({"cbData", "pbData"})
    public static final class DataBlob extends Structure {
        public int cbData;
        public Pointer pbData;
        private Memory ownedMemory;

        public DataBlob() {
        }

        public DataBlob(byte[] data) {
            cbData = data.length;
            if (data.length > 0) {
                ownedMemory = new Memory(data.length);
                ownedMemory.write(0, data, 0, data.length);
                pbData = ownedMemory;
            }
            write();
        }

        byte[] toByteArray() {
            return cbData == 0 || pbData == null
                    ? new byte[0]
                    : pbData.getByteArray(0, cbData);
        }

        void clearOwnedMemory() {
            if (ownedMemory != null) {
                ownedMemory.clear();
            }
        }

        void clearAndFree() {
            if (pbData == null) {
                return;
            }
            pbData.setMemory(0, cbData, (byte) 0);
            if (ownedMemory == null) {
                Kernel32.INSTANCE.LocalFree(pbData);
            }
            pbData = null;
            cbData = 0;
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("cbData", "pbData");
        }
    }

    private interface Crypt32 extends Library {
        Crypt32 INSTANCE = Native.load("Crypt32", Crypt32.class);

        boolean CryptProtectData(
                DataBlob dataIn,
                WString description,
                DataBlob optionalEntropy,
                Pointer reserved,
                Pointer prompt,
                int flags,
                DataBlob dataOut);

        boolean CryptUnprotectData(
                DataBlob dataIn,
                Pointer description,
                DataBlob optionalEntropy,
                Pointer reserved,
                Pointer prompt,
                int flags,
                DataBlob dataOut);
    }

    private interface Kernel32 extends Library {
        Kernel32 INSTANCE = Native.load("Kernel32", Kernel32.class);

        Pointer LocalFree(Pointer memory);
    }
}
