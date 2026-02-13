package com.blakube.bktops.plugin.schedule;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Minimal 5-field cron implementation: "m h dom mon dow"
 * - Supports: "*", single numbers, comma lists (e.g., 0,15,30), ranges (e.g., 1-5), and step values (for example every 5 minutes or 10-50 with step 10).
 * - Month is 1-12, Day of week is 0-6 (0 = Sunday) to align with common cron; also accepts 7 as Sunday.
 * - This is intentionally minimal and should cover common schedules like "0 0 * * *" (every day at midnight).
 */
public final class CronExpression {

    private final Field minutes;   // 0-59
    private final Field hours;     // 0-23
    private final Field dom;       // 1-31
    private final Field month;     // 1-12
    private final Field dow;       // 0-6 (0=Sunday)

    private CronExpression(Field minutes, Field hours, Field dom, Field month, Field dow) {
        this.minutes = minutes;
        this.hours = hours;
        this.dom = dom;
        this.month = month;
        this.dow = dow;
    }

    public static @NotNull CronExpression parse(@NotNull String expr) {
        String[] parts = expr.trim().split("\\s+");
        if (parts.length != 5) {
            throw new IllegalArgumentException("Cron must have 5 fields: m h dom mon dow");
        }
        Field minutes = Field.parse(parts[0], 0, 59);
        Field hours = Field.parse(parts[1], 0, 23);
        Field dom = Field.parse(parts[2], 1, 31);
        Field month = Field.parse(parts[3], 1, 12);
        Field dow = Field.parse(parts[4], 0, 7); // allow 7 for Sunday too
        return new CronExpression(minutes, hours, dom, month, dow);
    }

    /**
     * Returns the next execution strictly after the provided instant, or null if none can be found within a reasonable horizon.
     */
    public Instant nextExecutionAfter(@NotNull Instant instant) {
        // We iterate minutes forward up to a horizon (e.g., 5 years) to avoid infinite loops
        ZonedDateTime zdt = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).plusMinutes(1).withSecond(0).withNano(0);

        for (int i = 0; i < 60 * 24 * 366 * 5; i++) { // up to ~5 years
            int m = zdt.getMinute();
            int h = zdt.getHour();
            int day = zdt.getDayOfMonth();
            int mon = zdt.getMonthValue();
            int dowVal = zdt.getDayOfWeek().getValue(); // 1=Mon..7=Sun
            dowVal = (dowVal == 7) ? 0 : dowVal; // convert to 0=Sun..6=Sat

            if (minutes.contains(m)
                && hours.contains(h)
                && month.contains(mon)
                && (dom.contains(day) || dowContains(dowVal))) {
                return zdt.toInstant();
            }

            zdt = zdt.plusMinutes(1);
        }
        return null;
    }

    private boolean dowContains(int dowVal) {
        // allow 7 as Sunday as well as 0
        if (dow.contains(dowVal)) return true;
        if (dowVal == 0) return dow.contains(7);
        return false;
    }

    private static final class Field {
        private final Set<Integer> allowed = new TreeSet<>();
        private final int min;
        private final int max;

        private Field(int min, int max) {
            this.min = min;
            this.max = max;
        }

        static Field parse(String token, int min, int max) {
            Field f = new Field(min, max);
            if ("*".equals(token)) {
                for (int i = min; i <= max; i++) f.allowed.add(i);
                return f;
            }

            String[] parts = token.split(",");
            for (String part : parts) {
                parsePart(f, part.trim());
            }
            if (f.allowed.isEmpty()) {
                throw new IllegalArgumentException("Invalid cron field: " + token);
            }
            return f;
        }

        private static void parsePart(Field f, String part) {
            // step pattern a-b/x or */x or a/x
            String base = part;
            int step = 1;
            if (part.contains("/")) {
                String[] sx = part.split("/");
                base = sx[0];
                step = Integer.parseInt(sx[1]);
                if (step <= 0) throw new IllegalArgumentException("step must be > 0");
            }

            List<Integer> values = new ArrayList<>();
            if ("*".equals(base) || base.isEmpty()) {
                for (int i = f.min; i <= f.max; i++) values.add(i);
            } else if (base.contains("-")) {
                String[] ab = base.split("-");
                int a = Integer.parseInt(ab[0]);
                int b = Integer.parseInt(ab[1]);
                if (a > b) throw new IllegalArgumentException("range a-b must have a<=b");
                for (int i = a; i <= b; i++) values.add(i);
            } else {
                values.add(Integer.parseInt(base));
            }

            for (int v : values) {
                if (v < f.min || v > f.max) continue;
                int start = values.isEmpty() ? f.min : values.get(0);
                if (((v - start) % step) == 0) {
                    f.allowed.add(v);
                }
            }
        }

        boolean contains(int value) {
            return allowed.contains(value);
        }
    }
}
