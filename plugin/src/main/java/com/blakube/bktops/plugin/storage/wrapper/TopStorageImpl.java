package com.blakube.bktops.plugin.storage.wrapper;

import com.blakube.bktops.api.storage.TopStorage;
import com.blakube.bktops.api.top.TopEntry;
import com.blakube.bktops.plugin.storage.database.dao.TopStorageDAO;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class TopStorageImpl<K> implements TopStorage<K> {

    private final String topId;
    private final TopStorageDAO<K> dao;

    public TopStorageImpl(@NotNull String topId, @NotNull TopStorageDAO.IdentifierSerializer<K> serializer) {
        this.topId = Objects.requireNonNull(topId, "topId cannot be null");
        this.dao = new TopStorageDAO<>(topId, serializer);
    }

    @Override
    public void initialize() {}

    @Override
    public void close() {}

    @Override
    @NotNull
    public List<TopEntry<K>> load(@NotNull String topId) {
        return dao.loadAll();
    }

    @Override
    public boolean save(@NotNull String topId,
                        @NotNull K identifier,
                        @NotNull String displayName,
                        double value,
                        int maxSize) {

        int currentSize = dao.getSize();

        if (currentSize >= maxSize) {
            Double minValue = dao.getMinValue();
            if (minValue != null && value <= minValue && !dao.exists(identifier)) {
                return false;
            }
        }

        boolean saved = dao.save(identifier, displayName, value);

        if (saved && dao.getSize() > maxSize) {
            dao.removeLowest();
        }

        return saved;
    }

    @Override
    public void saveBatch(@NotNull String topId,
                          @NotNull List<TopEntry<K>> entries,
                          int maxSize) {
        List<TopStorageDAO.BatchEntry<K>> batchEntries = new ArrayList<>();

        for (TopEntry<K> entry : entries) {
            batchEntries.add(new TopStorageDAO.BatchEntry<>(
                    entry.getIdentifier(),
                    entry.getDisplayName(),
                    entry.getValue()
            ));
        }

        dao.saveBatch(batchEntries);

        while (dao.getSize() > maxSize) {
            dao.removeLowest();
        }
    }

    public void saveBatch(@NotNull List<TopStorageDAO.BatchEntry<K>> entries) {
        dao.saveBatch(entries);
    }

    @Override
    @Nullable
    public Double getMinValue(@NotNull String topId) {
        return dao.getMinValue();
    }

    @Override
    public int getSize(@NotNull String topId) {
        return dao.getSize();
    }

    @Override
    @NotNull
    public Optional<TopEntry<K>> getEntry(@NotNull String topId, @NotNull K identifier) {
        return dao.get(identifier);
    }

    @Override
    public int getPosition(@NotNull String topId, @NotNull K identifier) {
        return dao.getPosition(identifier);
    }

    @Override
    public boolean remove(@NotNull String topId, @NotNull K identifier) {
        return dao.delete(identifier);
    }

    @Override
    public void clear(@NotNull String topId) {
        dao.clear();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    @NotNull
    public String getType() {
        return "TopStorage-Optimized";
    }

    public TopStorageDAO<K> getDao() {
        return dao;
    }
}