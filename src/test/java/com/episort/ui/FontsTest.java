package com.episort.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class FontsTest {
    @Test
    void shipsEveryInstrumentSansWeightUsedByTheInterface() {
        assertNotNull(Fonts.class.getResource("/fonts/InstrumentSans-Regular.ttf"));
        assertNotNull(Fonts.class.getResource("/fonts/InstrumentSans-Medium.ttf"));
        assertNotNull(Fonts.class.getResource("/fonts/InstrumentSans-SemiBold.ttf"));
        assertNotNull(Fonts.class.getResource("/fonts/InstrumentSans-Bold.ttf"));
        assertNotNull(Fonts.class.getResource("/fonts/OFL-InstrumentSans.txt"));
    }

    @Test
    void noLongerShipsThePreviousInterfaceFonts() {
        assertNull(Fonts.class.getResource("/fonts/Inter-Regular.ttf"));
        assertNull(Fonts.class.getResource("/fonts/JetBrainsMono-Regular.ttf"));
    }
}
