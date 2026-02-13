package com.blakube.bktops.plugin.service.time;

import com.blakube.bktops.api.config.ConfigType;
import com.blakube.bktops.plugin.service.config.ConfigService;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.time.Duration;

public final class TimeFormatService {

    private final ConfigService configService;

    public TimeFormatService(ConfigService configService) {
        this.configService = configService;
    }

    public String formatTwoUnits(@NotNull Duration duration) {
        String split= configService.provide(ConfigType.LANG).getString("time-format.split", " ");

        String[] candidates = getStrings(duration);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String c : candidates) {
            if (c != null) {
                if (count > 0) sb.append(split);
                sb.append(c);
                count++;
                if (count == 2) break;
            }
        }

        if (count == 0) {
            return "0s";
        }
        return sb.toString();
    }

    private String @NonNull [] getStrings(@NonNull Duration duration) {
        long totalSeconds = Math.max(0, duration.getSeconds());

        long days = totalSeconds / 86_400;
        long rem = totalSeconds % 86_400;
        long hours = rem / 3_600;
        rem = rem % 3_600;
        long minutes = rem / 60;
        long seconds = rem % 60;

        String daySuf = configService.provide(ConfigType.LANG).getString("time-format.day", "d");
        String hourSuf = configService.provide(ConfigType.LANG).getString("time-format.hour", "h");
        String minSuf = configService.provide(ConfigType.LANG).getString("time-format.minute", "m");
        String secSuf = configService.provide(ConfigType.LANG).getString("time-format.second", "s");

        String dStr = days > 0 ? days + daySuf : null;
        String hStr = hours > 0 ? hours + hourSuf : null;
        String mStr = minutes > 0 ? minutes + minSuf : null;
        String sStr = seconds > 0 ? seconds + secSuf : (totalSeconds == 0 ? "0s" : null);

        return new String[] { dStr, hStr, mStr, sStr };
    }
}
