package com.blakube.bktops.plugin.top.factory;

import com.blakube.bktops.api.factory.TopFactory;
import com.blakube.bktops.api.provider.ValueProvider;
import com.blakube.bktops.api.resolver.NameResolver;
import com.blakube.bktops.api.storage.TopStorage;
import com.blakube.bktops.api.storage.config.TopConfig;
import com.blakube.bktops.api.timed.ResetSchedule;
import com.blakube.bktops.api.timed.TimedTop;
import com.blakube.bktops.api.top.Top;
import com.blakube.bktops.plugin.storage.database.dao.SnapshotDAO;
import com.blakube.bktops.plugin.storage.database.dao.TopStorageDAO;
import com.blakube.bktops.plugin.provider.TimedValueProvider;
import com.blakube.bktops.plugin.top.DefaultTimedTop;
import com.blakube.bktops.plugin.top.DefaultTop;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class DefaultTopFactory<K> implements TopFactory<K> {

    private final TopStorageDAO.IdentifierSerializer<K> serializer;

    public DefaultTopFactory(@NotNull TopStorageDAO.IdentifierSerializer<K> serializer) {
        this.serializer = Objects.requireNonNull(serializer, "serializer cannot be null");
    }

    @Override
    @NotNull
    public Top<K> createTop(@NotNull String id,
                           @NotNull TopConfig config,
                           @NotNull ValueProvider<K> valueProvider,
                           @NotNull NameResolver<K> nameResolver,
                           @NotNull TopStorage<K> storage) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(config, "config cannot be null");
        Objects.requireNonNull(valueProvider, "valueProvider cannot be null");
        Objects.requireNonNull(nameResolver, "nameResolver cannot be null");
        Objects.requireNonNull(storage, "storage cannot be null");

        return new DefaultTop<>(id, config, valueProvider, nameResolver, storage);
    }

    @Override
    @NotNull
    public TimedTop<K> createTimedTop(@NotNull String id,
                                     @NotNull TopConfig config,
                                     @NotNull ValueProvider<K> valueProvider,
                                     @NotNull NameResolver<K> nameResolver,
                                     @NotNull TopStorage<K> storage,
                                     @NotNull ResetSchedule resetSchedule) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(config, "config cannot be null");
        Objects.requireNonNull(valueProvider, "valueProvider cannot be null");
        Objects.requireNonNull(nameResolver, "nameResolver cannot be null");
        Objects.requireNonNull(storage, "storage cannot be null");
        Objects.requireNonNull(resetSchedule, "resetSchedule cannot be null");

        TimedValueProvider<K> timedProvider;
        SnapshotDAO<K> snapshotDAO;

        if (valueProvider instanceof TimedValueProvider) {
            timedProvider = (TimedValueProvider<K>) valueProvider;
            snapshotDAO = timedProvider.getSnapshotDAO();
        } else {
            snapshotDAO = new SnapshotDAO<>(id, serializer);
            timedProvider = new TimedValueProvider<>(id, valueProvider, snapshotDAO);
        }

        return new DefaultTimedTop<>(id, config, timedProvider, nameResolver, storage, resetSchedule, snapshotDAO);
    }
}