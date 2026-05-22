package com.blakube.bktops.plugin.formatter;

import org.jetbrains.annotations.NotNull;

public interface ValueFormatter {
    @NotNull String format(double value);
}
