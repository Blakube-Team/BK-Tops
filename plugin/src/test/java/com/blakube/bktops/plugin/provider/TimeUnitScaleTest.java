package com.blakube.bktops.plugin.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeUnitScaleTest {

    @Test
    void convertsBareValuesToSeconds() {
        assertEquals(3_600d, TimeUnitScale.HOURS.toSeconds(1));
        assertEquals(60d, TimeUnitScale.MINUTES.toSeconds(1));
        assertEquals(86_400d, TimeUnitScale.DAYS.toSeconds(1));
        assertEquals(1d, TimeUnitScale.TICKS.toSeconds(20));
        assertEquals(1d, TimeUnitScale.MILLIS.toSeconds(1000));
        assertEquals(5d, TimeUnitScale.SECONDS.toSeconds(5));
    }

    @Test
    void explicitConfigWins() {
        assertEquals(TimeUnitScale.MINUTES,
                TimeUnitScale.resolve("minutes", "%statistic_hours_played%"));
        assertEquals(TimeUnitScale.TICKS,
                TimeUnitScale.resolve(" Ticks ", "%whatever%"));
    }

    @Test
    void infersUnitFromPlaceholderWhenUnset() {
        assertEquals(TimeUnitScale.HOURS, TimeUnitScale.resolve(null, "%statistic_hours_played%"));
        assertEquals(TimeUnitScale.MINUTES, TimeUnitScale.resolve(null, "%afk_minutes%"));
        assertEquals(TimeUnitScale.TICKS, TimeUnitScale.resolve(null, "%statistic_play_time_ticks%"));
        assertEquals(TimeUnitScale.DAYS, TimeUnitScale.resolve(null, "%plugin_days_online%"));
    }

    @Test
    void fallsBackToSecondsForUnknownInput() {
        assertEquals(TimeUnitScale.SECONDS, TimeUnitScale.resolve(null, "%vault_eco_balance%"));
        assertEquals(TimeUnitScale.SECONDS, TimeUnitScale.resolve("nonsense", "%vault_eco_balance%"));
    }
}
