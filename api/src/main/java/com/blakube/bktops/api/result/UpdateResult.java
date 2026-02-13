package com.blakube.bktops.api.result;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Result of processing an identifier.
 */
public final class UpdateResult<K> {

    private final K identifier;
    private final boolean success;
    private final Double oldValue;
    private final Double newValue;
    private final Integer oldPosition;
    private final Integer newPosition;
    private final String reason;

    private UpdateResult(@NotNull K identifier,
                        boolean success,
                        @Nullable Double oldValue,
                        @Nullable Double newValue,
                        @Nullable Integer oldPosition,
                        @Nullable Integer newPosition,
                        @Nullable String reason) {
        this.identifier = Objects.requireNonNull(identifier, "identifier cannot be null");
        this.success = success;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.oldPosition = oldPosition;
        this.newPosition = newPosition;
        this.reason = reason;
    }

    @NotNull
    public K getIdentifier() {
        return identifier;
    }

    public boolean isSuccess() {
        return success;
    }

    @Nullable
    public Double getOldValue() {
        return oldValue;
    }

    @Nullable
    public Double getNewValue() {
        return newValue;
    }

    @Nullable
    public Integer getOldPosition() {
        return oldPosition;
    }

    @Nullable
    public Integer getNewPosition() {
        return newPosition;
    }

    @Nullable
    public String getReason() {
        return reason;
    }

    public boolean hasValueChanged() {
        return !Objects.equals(oldValue, newValue);
    }

    public boolean hasPositionChanged() {
        return !Objects.equals(oldPosition, newPosition);
    }

    public boolean isEnteredTop() {
        return oldPosition == null && newPosition != null;
    }

    public boolean isLeftTop() {
        return oldPosition != null && newPosition == null;
    }

    @NotNull
    public static <K> UpdateResult<K> success(@NotNull K identifier,
                                              @Nullable Double oldValue,
                                              @Nullable Double newValue,
                                              @Nullable Integer oldPosition,
                                              @Nullable Integer newPosition) {
        return new UpdateResult<>(identifier, true, oldValue, newValue, oldPosition, newPosition, null);
    }

    @NotNull
    public static <K> UpdateResult<K> failure(@NotNull K identifier, @NotNull String reason) {
        return new UpdateResult<>(identifier, false, null, null, null, null, reason);
    }

    @Override
    public String toString() {
        if (!success) {
            return "UpdateResult{identifier=" + identifier + ", success=false, reason='" + reason + "'}";
        }
        return "UpdateResult{" +
                "identifier=" + identifier +
                ", success=true" +
                ", oldValue=" + oldValue +
                ", newValue=" + newValue +
                ", oldPosition=" + oldPosition +
                ", newPosition=" + newPosition +
                '}';
    }
}