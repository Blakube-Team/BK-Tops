package com.blakube.bktops.plugin.service.time;

import com.blakube.bktops.api.TopAPI;
import com.blakube.bktops.api.TopAPIProvider;
import com.blakube.bktops.api.timed.TimedTop;
import com.blakube.bktops.api.top.Top;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Optional;

public final class ObtainTimeService {

    @NotNull
    public Optional<Duration> getTimeUntilReset(@NotNull String topId) {
        TopAPI api = TopAPIProvider.getInstance();
        Top<?> top = api.getTop(topId);
        if (top instanceof TimedTop) {
            TimedTop<?> timed = (TimedTop<?>) top;
            return Optional.ofNullable(timed.getTimeUntilReset());
        }
        return Optional.empty();
    }
}
