package com.blakube.bktops.plugin.formatter;

import com.blakube.bktops.plugin.TestConfigContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NumberFormatterTest {

    private NumberFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new NumberFormatter(new TestConfigContainer(Map.of(
                "number-format.mode", "EXACT",
                "number-format.thousand-separator", ",",
                "number-format.decimal-separator", ".",
                "number-format.decimal-places", 2,
                "number-format.compact-suffixes.thousand", "K",
                "number-format.compact-suffixes.million", "M",
                "number-format.compact-suffixes.billion", "B",
                "number-format.compact-suffixes.trillion", "T"
        )));
    }

    @Test
    void exact() {
        assertEquals("1,234,567.00", formatter.format(1_234_567));
        assertEquals("0.00",         formatter.format(0));
        assertEquals("1,000.50",     formatter.format(1000.5));
    }

    @Test
    void rounded() {
        assertEquals("1,234,567", formatter.format(1_234_567.4, NumberFormatter.FormatMode.ROUNDED));
        assertEquals("1,234,568", formatter.format(1_234_567.6, NumberFormatter.FormatMode.ROUNDED));
        assertEquals("0",         formatter.format(0,           NumberFormatter.FormatMode.ROUNDED));
    }

    @Test
    void compact() {
        assertEquals("1.23M",  formatter.format(1_234_567,   NumberFormatter.FormatMode.COMPACT));
        assertEquals("3.45K",  formatter.format(3_450,       NumberFormatter.FormatMode.COMPACT));
        assertEquals("1.23B",  formatter.format(1_234_567_890, NumberFormatter.FormatMode.COMPACT));
        assertEquals("500",    formatter.format(500,          NumberFormatter.FormatMode.COMPACT));
    }

    @Test
    void compactRounded() {
        assertEquals("1M",  formatter.format(1_234_567, NumberFormatter.FormatMode.COMPACT_ROUNDED));
        assertEquals("3K",  formatter.format(3_450,     NumberFormatter.FormatMode.COMPACT_ROUNDED));
        assertEquals("500", formatter.format(500,       NumberFormatter.FormatMode.COMPACT_ROUNDED));
    }

    @Test
    void noThousandSeparator() {
        NumberFormatter f = new NumberFormatter(new TestConfigContainer(Map.of(
                "number-format.mode", "EXACT",
                "number-format.thousand-separator", "",
                "number-format.decimal-separator", ".",
                "number-format.decimal-places", 0,
                "number-format.compact-suffixes.thousand", "K",
                "number-format.compact-suffixes.million", "M",
                "number-format.compact-suffixes.billion", "B",
                "number-format.compact-suffixes.trillion", "T"
        )));
        assertEquals("1234567", f.format(1_234_567));
    }
}
