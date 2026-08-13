package com.episort.ui.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.OptionalInt;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class RenderTuningTest {

    @Test
    void matchesThePulseToTheDisplay() {
        Properties properties = new Properties();

        RenderTuning.apply(properties, OptionalInt.of(144));

        assertEquals("144", properties.getProperty(RenderTuning.PULSE_PROPERTY));
    }

    @Test
    void neverAsksForLessThanTheJavaFxDefault() {
        Properties properties = new Properties();

        RenderTuning.apply(properties, OptionalInt.of(50));

        assertEquals("60", properties.getProperty(RenderTuning.PULSE_PROPERTY));
    }

    @Test
    void capsWhatItAsksFor() {
        Properties properties = new Properties();

        RenderTuning.apply(properties, OptionalInt.of(1000));

        assertEquals(String.valueOf(RenderTuning.MAXIMUM_PULSE_HZ), properties.getProperty(RenderTuning.PULSE_PROPERTY));
    }

    @Test
    void leavesJavaFxAloneWhenTheDisplayRateIsUnknown() {
        Properties properties = new Properties();

        RenderTuning.apply(properties, OptionalInt.empty());

        assertNull(properties.getProperty(RenderTuning.PULSE_PROPERTY));
    }

    @Test
    void neverOverridesAnExplicitCommandLineChoice() {
        Properties properties = new Properties();
        properties.setProperty(RenderTuning.PULSE_PROPERTY, "90");

        RenderTuning.apply(properties, OptionalInt.of(480));

        assertEquals("90", properties.getProperty(RenderTuning.PULSE_PROPERTY));
    }
}
