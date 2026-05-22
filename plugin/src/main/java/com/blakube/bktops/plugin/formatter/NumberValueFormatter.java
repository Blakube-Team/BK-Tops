package com.blakube.bktops.plugin.formatter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NumberValueFormatter implements ValueFormatter {

    private final NumberFormatter.FormatMode mode;

    public NumberValueFormatter(@Nullable NumberFormatter.FormatMode mode) {
        this.mode = mode;
    }

    @Override
    public @NotNull String format(double value) {
        return NumberFormatterProvider.getInstance().format(value, mode);
    }
}
