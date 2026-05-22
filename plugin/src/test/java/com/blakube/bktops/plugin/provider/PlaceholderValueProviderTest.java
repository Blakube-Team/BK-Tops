package com.blakube.bktops.plugin.provider;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class PlaceholderValueProviderTest {

    private Double parse(String input) throws Exception {
        Method m = PlaceholderValueProvider.class.getDeclaredMethod("parseNumeric", String.class);
        m.setAccessible(true);
        return (Double) m.invoke(null, input);
    }

    @Test
    void timeFormatHms() throws Exception {
        assertEquals(71352.0, parse("19h 49m 12s"));
    }

    @Test
    void timeFormatDaysAndHours() throws Exception {
        assertEquals(183600.0, parse("2d 3h"));
    }

    @Test
    void timeFormatHoursOnly() throws Exception {
        assertEquals(3600.0, parse("1h"));
    }

    @Test
    void timeFormatDaysOnly() throws Exception {
        assertEquals(86400.0, parse("1d"));
    }

    @Test
    void timeFormatNoSpaces() throws Exception {
        assertEquals(71352.0, parse("19h49m12s"));
    }

    @Test
    void timeFormatUpperCase() throws Exception {
        assertEquals(3661.0, parse("1H 1M 1S"));
    }

    @Test
    void colonFormatHhMmSs() throws Exception {
        assertEquals(71352.0, parse("19:49:12"));
    }

    @Test
    void colonFormatMmSs() throws Exception {
        assertEquals(330.0, parse("5:30"));
    }

    @Test
    void plainInteger() throws Exception {
        assertEquals(174133.0, parse("174133"));
    }

    @Test
    void withThousandSeparator() throws Exception {
        assertEquals(1234567.0, parse("1,234,567"));
    }

    @Test
    void compactK() throws Exception {
        assertEquals(3500.0, parse("3.5K"));
    }

    @Test
    void compactM() throws Exception {
        assertEquals(1_500_000.0, parse("1.5M"));
    }

    @Test
    void standaloneMinutesNotTreatedAsTime() throws Exception {
        assertEquals(5_000_000.0, parse("5m"));
    }

    @Test
    void standaloneSeconds() throws Exception {
        assertEquals(30.0, parse("30s"));
    }

    @Test
    void nullOnEmpty() throws Exception {
        assertNull(parse(""));
    }

    @Test
    void nullOnText() throws Exception {
        assertNull(parse("hello"));
    }

    @Test
    void percentage() throws Exception {
        assertEquals(0.75, parse("75%"), 0.001);
    }
}
