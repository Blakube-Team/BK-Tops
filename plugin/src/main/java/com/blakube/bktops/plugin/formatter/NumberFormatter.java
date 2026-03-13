package com.blakube.bktops.plugin.formatter;

import com.blakube.bktops.api.config.ConfigContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class NumberFormatter {

    private final ConfigContainer config;
    private FormatMode mode;
    private String thousandSeparator;
    private String decimalSeparator;
    private int decimalPlaces;
    private String thousandSuffix;
    private String millionSuffix;
    private String billionSuffix;
    private String trillionSuffix;

    public NumberFormatter(@NotNull ConfigContainer config) {
        this.config = config;
        reload();
    }

    public void reload() {
        String modeStr = config.getString("number-format.mode", "EXACT");
        try {
            this.mode = FormatMode.valueOf(modeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.mode = FormatMode.EXACT;
        }

        this.thousandSeparator = config.getString("number-format.thousand-separator", ",");
        this.decimalSeparator = config.getString("number-format.decimal-separator", ".");
        this.decimalPlaces = Math.max(0, Math.min(12, config.getInt("number-format.decimal-places", 2)));

        this.thousandSuffix = config.getString("number-format.compact-suffixes.thousand", "K");
        this.millionSuffix = config.getString("number-format.compact-suffixes.million", "M");
        this.billionSuffix = config.getString("number-format.compact-suffixes.billion", "B");
        this.trillionSuffix = config.getString("number-format.compact-suffixes.trillion", "T");
    }

    @NotNull
    public String format(double value) {
        return format(value, null);
    }

    @NotNull
    public String format(double value, @Nullable FormatMode modeOverride) {
        FormatMode effectiveMode = modeOverride != null ? modeOverride : this.mode;

        return switch (effectiveMode) {
            case EXACT -> formatExact(value);
            case ROUNDED -> formatRounded(value);
            case COMPACT -> formatCompact(value, true);
            case COMPACT_ROUNDED -> formatCompact(value, false);
        };
    }

    @NotNull
    private String formatExact(double value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(thousandSeparator.isEmpty() ? '\0' : thousandSeparator.charAt(0));
        symbols.setDecimalSeparator(decimalSeparator.charAt(0));

        StringBuilder pattern = new StringBuilder("#");
        if (!thousandSeparator.isEmpty()) {
            pattern.append(",###");
        } else {
            pattern.append("###");
        }

        if (decimalPlaces > 0) {
            pattern.append(".");
            pattern.append("0".repeat(decimalPlaces));
        }

        DecimalFormat df = new DecimalFormat(pattern.toString(), symbols);
        df.setGroupingUsed(!thousandSeparator.isEmpty());

        return df.format(value);
    }

    @NotNull
    private String formatRounded(double value) {
        long rounded = Math.round(value);

        if (thousandSeparator.isEmpty()) {
            return String.valueOf(rounded);
        }

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(thousandSeparator.charAt(0));

        DecimalFormat df = new DecimalFormat("#,###", symbols);
        return df.format(rounded);
    }

    @NotNull
    private String formatCompact(double value, boolean includeDecimals) {
        CompactResult result = getCompactValue(value);

        if (includeDecimals && decimalPlaces > 0) {
            DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
            symbols.setDecimalSeparator(decimalSeparator.charAt(0));

            StringBuilder pattern = new StringBuilder("0");
            if (decimalPlaces > 0) {
                pattern.append(".");
                pattern.append("#".repeat(decimalPlaces));
            }

            DecimalFormat df = new DecimalFormat(pattern.toString(), symbols);
            return df.format(result.value) + result.suffix;
        } else {
            long rounded = Math.round(result.value);
            return rounded + result.suffix;
        }
    }

    @NotNull
    private CompactResult getCompactValue(double value) {
        double absValue = Math.abs(value);
        double sign = value < 0 ? -1 : 1;

        if (absValue >= 1_000_000_000_000L) {
            return new CompactResult(sign * (absValue / 1_000_000_000_000.0), trillionSuffix);
        } else if (absValue >= 1_000_000_000L) {
            return new CompactResult(sign * (absValue / 1_000_000_000.0), billionSuffix);
        } else if (absValue >= 1_000_000L) {
            return new CompactResult(sign * (absValue / 1_000_000.0), millionSuffix);
        } else if (absValue >= 1_000L) {
            return new CompactResult(sign * (absValue / 1_000.0), thousandSuffix);
        } else {
            return new CompactResult(value, "");
        }
    }

    public enum FormatMode {
        EXACT,
        ROUNDED,
        COMPACT,
        COMPACT_ROUNDED
    }

    private record CompactResult(double value, String suffix) {}
}