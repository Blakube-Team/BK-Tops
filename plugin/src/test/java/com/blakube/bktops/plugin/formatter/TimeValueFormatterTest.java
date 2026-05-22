package com.blakube.bktops.plugin.formatter;

import com.blakube.bktops.plugin.TestConfigContainer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeValueFormatterTest {

    private TimeValueFormatter formatter(int significantFigures) {
        return new TimeValueFormatter(new TestConfigContainer(Map.of(
                "time-format.separator", " ",
                "time-format.significant-figures", significantFigures,
                "time-format.suffixes.years", "y",
                "time-format.suffixes.months", "mo",
                "time-format.suffixes.weeks", "w",
                "time-format.suffixes.days", "d",
                "time-format.suffixes.hours", "h",
                "time-format.suffixes.minutes", "m",
                "time-format.suffixes.seconds", "s"
        )));
    }

    @Test
    void defaultThreeFigures() {
        TimeValueFormatter f = formatter(3);
        assertEquals("19h 49m 12s", f.format(71352));
        assertEquals("1d 2h 30m",   f.format(95400));
        assertEquals("1h 0m 30s",   f.format(3630));
    }

    @Test
    void twoFigures() {
        TimeValueFormatter f = formatter(2);
        assertEquals("19h 49m", f.format(71352));
        assertEquals("1d 2h",   f.format(95400));
        assertEquals("1h 0m",   f.format(3630));
    }

    @Test
    void oneFigure() {
        TimeValueFormatter f = formatter(1);
        assertEquals("19h", f.format(71352));
        assertEquals("1d",  f.format(95400));
        assertEquals("30s", f.format(30));
    }

    @Test
    void fourFigures() {
        TimeValueFormatter f = formatter(4);
        assertEquals("19h 49m 12s", f.format(71352));   
        assertEquals("1d 2h 30m 0s", f.format(95400));
    }

    @Test
    void largeValues() {
        TimeValueFormatter f = formatter(3);
        long oneYear = 31_536_000L;
        assertEquals("1y 0mo 0w", f.format(oneYear));

        long twoYearsThreeMonths = 2 * 31_536_000L + 3 * 2_592_000L;
        assertEquals("2y 3mo 0w", f.format(twoYearsThreeMonths));
    }

    @Test
    void zeroValue() {
        assertEquals("0s", formatter(3).format(0));
        assertEquals("0s", formatter(1).format(0));
    }

    @Test
    void exactlyOneMinute() {
        assertEquals("1m 0s", formatter(2).format(60));
        assertEquals("1m",    formatter(1).format(60));
    }

    @Test
    void customSeparator() {
        TimeValueFormatter f = new TimeValueFormatter(new TestConfigContainer(Map.of(
                "time-format.separator", ":",
                "time-format.significant-figures", 3,
                "time-format.suffixes.hours", "h",
                "time-format.suffixes.minutes", "m",
                "time-format.suffixes.seconds", "s"
        )));
        assertEquals("19h:49m:12s", f.format(71352));
    }
}
