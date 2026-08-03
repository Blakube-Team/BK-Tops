package com.blakube.bktops.plugin.provider;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class PlaceholderValueProviderTest {

    private Double parse(String input) throws Exception {
        Method m = PlaceholderValueProvider.class.getDeclaredMethod("parse", String.class);
        m.setAccessible(true);
        return extractValue(m.invoke(null, input));
    }

    private Double parse(String input, ValueKind hint) throws Exception {
        Method m = PlaceholderValueProvider.class.getDeclaredMethod("parse", String.class, ValueKind.class);
        m.setAccessible(true);
        return extractValue(m.invoke(null, input, hint));
    }

    private Double extractValue(Object result) throws Exception {
        if (result == null) return null;
        Method value = result.getClass().getDeclaredMethod("value");
        value.setAccessible(true);
        return (Double) value.invoke(result);
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
    void standaloneMinutesTreatedAsTime() throws Exception {
        
        assertEquals(300.0, parse("5m"));
    }

    @Test
    void standaloneMillionUppercase() throws Exception {
        
        assertEquals(5_000_000.0, parse("5M"));
    }

    @Test
    void oneMinuteIsSixtySeconds() throws Exception {
        assertEquals(60.0, parse("1m"));
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

    

    @Test
    void timeHintReadsUppercaseMAsMinutes() throws Exception {
        assertEquals(300.0, parse("5M", ValueKind.TIME));
    }

    @Test
    void timeHintReadsBareNumberAsSeconds() throws Exception {
        assertEquals(90.0, parse("90", ValueKind.TIME));
    }

    @Test
    void timeHintStillParsesDurations() throws Exception {
        assertEquals(71352.0, parse("19h 49m 12s", ValueKind.TIME));
    }

    @Test
    void numberHintReadsLowercaseMAsMillion() throws Exception {
        assertEquals(5_000_000.0, parse("5m", ValueKind.NUMBER));
    }

    @Test
    void unknownHintMatchesAutoDetection() throws Exception {
        assertEquals(300.0, parse("5m", ValueKind.UNKNOWN));
        assertEquals(5_000_000.0, parse("5M", ValueKind.UNKNOWN));
    }

    

    @Test
    void weeksOnly() throws Exception {
        assertEquals(2 * 604_800.0, parse("2w"));
    }

    @Test
    void monthsOnly() throws Exception {
        assertEquals(7 * 2_592_000.0, parse("7mo"));
    }

    @Test
    void yearsOnly() throws Exception {
        assertEquals(31_536_000.0, parse("1y"));
    }

    @Test
    void yearsMonthsWeeks() throws Exception {
        
        double expected = 31_536_000.0 + 7 * 2_592_000.0 + 2 * 604_800.0;
        assertEquals(expected, parse("1y 7mo 2w"));
    }

    @Test
    void weeksAndDays() throws Exception {
        assertEquals(4 * 604_800.0 + 5 * 86_400.0, parse("4w 5d"));
    }

    @Test
    void monthsBeatsMinutesOnSuffix() throws Exception {
        
        assertEquals(2_592_000.0, parse("1mo"));
    }

    @Test
    void durationRoundTripsThroughFormatter() {
        
        var formatter = new com.blakube.bktops.plugin.formatter.TimeValueFormatter(
                new com.blakube.bktops.plugin.TestConfigContainer(java.util.Map.of(
                        "time-format.separator", " ",
                        "time-format.significant-figures", 3)));
        String rendered = formatter.format(31_536_000L + 7 * 2_592_000L + 2 * 604_800L);
        assertEquals("1y 7mo 2w", rendered);
    }

    @Test
    void commaSeparatedUnits() throws Exception {
        assertEquals(86_400.0 + 3_600.0, parse("1d, 1h"));
    }

    @Test
    void allSevenUnits() throws Exception {
        double expected = 31_536_000.0 + 2_592_000.0 + 604_800.0 + 86_400.0 + 3_600.0 + 60.0 + 1.0;
        assertEquals(expected, parse("1y 1mo 1w 1d 1h 1m 1s"));
    }

    @Test
    void garbageBetweenUnitsIsNotParsedAsDuration() throws Exception {
        
        
        
        assertNotEquals(445_200.0, parse("5d x 3h"));
    }

    @Test
    void uppercaseMonthsStillMonths() throws Exception {
        assertEquals(2_592_000.0, parse("1MO"));
        assertEquals(2_592_000.0, parse("1Mo"));
    }

    @Test
    void weeksWithTimeHintFromBareNumber() throws Exception {
        
        assertEquals(604_800.0, parse("604800", ValueKind.TIME));
    }

    @Test
    void multiUnitUppercaseMIsMinutes() throws Exception {
        
        assertEquals(3_600.0 + 120.0, parse("1H 2M"));
    }
}
