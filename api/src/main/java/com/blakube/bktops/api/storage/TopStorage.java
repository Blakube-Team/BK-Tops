package com.blakube.bktops.api.storage;

import com.blakube.bktops.api.top.TopEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Storage abstraction for leaderboards.
 */
public interface TopStorage<K> {

    void initialize();

    void close();

    @NotNull
    List<TopEntry<K>> load(@NotNull String topId);

    boolean save(@NotNull String topId,
                 @NotNull K identifier,
                 @NotNull String displayName,
                 double value,
                 int maxSize);

    void saveBatch(@NotNull String topId,
                   @NotNull List<TopEntry<K>> entries,
                   int maxSize);

    @Nullable
    Double getMinValue(@NotNull String topId);

    int getSize(@NotNull String topId);

    @NotNull
    Optional<TopEntry<K>> getEntry(@NotNull String topId, @NotNull K identifier);

    int getPosition(@NotNull String topId, @NotNull K identifier);

    boolean remove(@NotNull String topId, @NotNull K identifier);

    void clear(@NotNull String topId);

    boolean isAvailable();

    @NotNull
    String getType();
}