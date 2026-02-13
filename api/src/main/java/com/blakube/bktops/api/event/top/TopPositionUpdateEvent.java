package com.blakube.bktops.api.event.top;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when an entry's position within a top changes.
 * This event may be called asynchronously.
 */
public class TopPositionUpdateEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String topId;
    private final Object identifier;
    private final String displayName;
    private final Double oldValue;
    private final Double newValue;
    private final Integer oldPosition;
    private final Integer newPosition;

    public TopPositionUpdateEvent(boolean async,
                                  @NotNull String topId,
                                  @NotNull Object identifier,
                                  @Nullable String displayName,
                                  @Nullable Double oldValue,
                                  @Nullable Double newValue,
                                  @Nullable Integer oldPosition,
                                  @Nullable Integer newPosition) {
        super(async);
        this.topId = topId;
        this.identifier = identifier;
        this.displayName = displayName;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.oldPosition = oldPosition;
        this.newPosition = newPosition;
    }

    @NotNull
    public String getTopId() { return topId; }

    @NotNull
    public Object getIdentifier() { return identifier; }

    @Nullable
    public String getDisplayName() { return displayName; }

    @Nullable
    public Double getOldValue() { return oldValue; }

    @Nullable
    public Double getNewValue() { return newValue; }

    @Nullable
    public Integer getOldPosition() { return oldPosition; }

    @Nullable
    public Integer getNewPosition() { return newPosition; }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
