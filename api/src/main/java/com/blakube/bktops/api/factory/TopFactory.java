package com.blakube.bktops.api.factory;

import com.blakube.bktops.api.provider.ValueProvider;
import com.blakube.bktops.api.resolver.NameResolver;
import com.blakube.bktops.api.storage.TopStorage;
import com.blakube.bktops.api.storage.config.TopConfig;
import com.blakube.bktops.api.timed.ResetSchedule;
import com.blakube.bktops.api.timed.TimedTop;
import com.blakube.bktops.api.top.Top;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for creating leaderboard instances.
 */
public interface TopFactory<K> {

    @NotNull
    Top<K> createTop(@NotNull String id,
                     @NotNull TopConfig config,
                     @NotNull ValueProvider<K> valueProvider,
                     @NotNull NameResolver<K> nameResolver,
                     @NotNull TopStorage<K> storage);

    @NotNull
    TimedTop<K> createTimedTop(@NotNull String id,
                               @NotNull TopConfig config,
                               @NotNull ValueProvider<K> valueProvider,
                               @NotNull NameResolver<K> nameResolver,
                               @NotNull TopStorage<K> storage,
                               @NotNull ResetSchedule resetSchedule);
}