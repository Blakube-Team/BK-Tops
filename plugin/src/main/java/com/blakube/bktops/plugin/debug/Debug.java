package com.blakube.bktops.plugin.debug;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;
import java.util.logging.Logger;

















public final class Debug {

    private static final String PREFIX = "[BK-Tops] [DEBUG] ";

    private static volatile boolean enabled = false;
    private static volatile Logger logger;

    private Debug() {}

    
    public static void init(@NotNull Logger pluginLogger, boolean debugEnabled) {
        logger = pluginLogger;
        enabled = debugEnabled;
    }

    
    public static void setEnabled(boolean debugEnabled) {
        enabled = debugEnabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    



    public static void log(@Nullable String message) {
        if (!enabled) return;
        emit(message);
    }

    



    public static void log(@NotNull Supplier<String> message) {
        if (!enabled) return;
        emit(message.get());
    }

    




    public static void log(@NotNull String template, @NotNull Object... args) {
        if (!enabled) return;
        emit(format(template, args));
    }

    
    public static void log(@Nullable String message, @NotNull Throwable throwable) {
        if (!enabled) return;
        Logger l = logger;
        if (l != null) {
            l.log(java.util.logging.Level.WARNING, PREFIX + message, throwable);
        } else {
            System.out.println(PREFIX + message);
            throwable.printStackTrace();
        }
    }

    private static void emit(@Nullable String message) {
        Logger l = logger;
        if (l != null) {
            l.info(PREFIX + message);
        } else {
            
            System.out.println(PREFIX + message);
        }
    }

    private static String format(@NotNull String template, @NotNull Object... args) {
        if (args.length == 0) return template;
        StringBuilder sb = new StringBuilder(template.length() + args.length * 8);
        int argIndex = 0;
        int i = 0;
        while (i < template.length()) {
            if (argIndex < args.length
                    && i + 1 < template.length()
                    && template.charAt(i) == '{'
                    && template.charAt(i + 1) == '}') {
                sb.append(String.valueOf(args[argIndex++]));
                i += 2;
            } else {
                sb.append(template.charAt(i++));
            }
        }
        return sb.toString();
    }
}
