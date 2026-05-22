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
    private String thousandSuffix;
    private String millionSuffix;
    private String billionSuffix;
    private String trillionSuffix;

    private DecimalFormat exactFormat;
    private DecimalFormat roundedFormat;
    private DecimalFormat compactFormat;

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

        String thousandSeparator = config.getString("number-format.thousand-separator", ",");
        String decimalSeparator  = config.getString("number-format.decimal-separator", ".");
        int    decimalPlaces     = Math.max(0, Math.min(12, config.getInt("number-format.decimal-places", 2)));

        this.thousandSuffix = config.getString("number-format.compact-suffixes.thousand", "K");
        this.millionSuffix  = config.getString("number-format.compact-suffixes.million", "M");
        this.billionSuffix  = config.getString("number-format.compact-suffixes.billion", "B");
        this.trillionSuffix = config.getString("number-format.compact-suffixes.trillion", "T");

        DecimalFormatSymbols exactSymbols = new DecimalFormatSymbols(Locale.US);
        exactSymbols.setGroupingSeparator(thousandSeparator.isEmpty() ? '\0' : thousandSeparator.charAt(0));
        exactSymbols.setDecimalSeparator(decimalSeparator.charAt(0));
        StringBuilder exactPattern = new StringBuilder(thousandSeparator.isEmpty() ? "###0" : "#,##0");
        if (decimalPlaces > 0) exactPattern.append(".").append("0".repeat(decimalPlaces));
        this.exactFormat = new DecimalFormat(exactPattern.toString(), exactSymbols);
        this.exactFormat.setGroupingUsed(!thousandSeparator.isEmpty());

        if (!thousandSeparator.isEmpty()) {
            DecimalFormatSymbols roundedSymbols = new DecimalFormatSymbols(Locale.US);
            roundedSymbols.setGroupingSeparator(thousandSeparator.charAt(0));
            this.roundedFormat = new DecimalFormat("#,###", roundedSymbols);
        } else {
            this.roundedFormat = null;
        }

        if (decimalPlaces > 0) {
            DecimalFormatSymbols compactSymbols = new DecimalFormatSymbols(Locale.US);
            compactSymbols.setDecimalSeparator(decimalSeparator.charAt(0));
            StringBuilder compactPattern = new StringBuilder("0.").append("#".repeat(decimalPlaces));
            this.compactFormat = new DecimalFormat(compactPattern.toString(), compactSymbols);
        } else {
            this.compactFormat = null;
        }
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
        return exactFormat.format(value);
    }

    @NotNull
    private String formatRounded(double value) {
        long rounded = Math.round(value);
        return roundedFormat != null ? roundedFormat.format(rounded) : String.valueOf(rounded);
    }

    @NotNull
    private String formatCompact(double value, boolean includeDecimals) {
        CompactResult result = getCompactValue(value);

        if (includeDecimals && compactFormat != null) {
            return compactFormat.format(result.value) + result.suffix;
        } else {
            return Math.round(result.value) + result.suffix;
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