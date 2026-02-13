package com.blakube.bktops.plugin.serializer;

import com.blakube.bktops.plugin.storage.database.dao.TopStorageDAO;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class UUIDSerializer implements TopStorageDAO.IdentifierSerializer<UUID> {

    @Override
    @NotNull
    public String serialize(@NotNull UUID identifier) {
        return identifier.toString();
    }

    @Override
    @NotNull
    public UUID deserialize(@NotNull String serialized) {
        return UUID.fromString(serialized);
    }
}