package com.blakube.bktops.api.storage.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.blakube.bktops.api.storage.config.ConditionSet.EMPTY;

public final class TopConfig {

    private final int size;
    private final boolean enableOnlineQueue;
    private final int onlineQueueInterval;
    private final boolean enableRotativeQueue;
    private final int rotativeQueueSize;
    private final int rotativeQueueInterval;
    private final int batchSize;
    private final int tickDelay;
    private final String displayName;
    private final ConditionSet conditionSet;
    private final String valueFormat;
    private final boolean allowZeroValues;

    private TopConfig(@NotNull Builder builder) {
        this.size = builder.size;
        this.enableOnlineQueue = builder.enableOnlineQueue;
        this.onlineQueueInterval = builder.onlineQueueInterval;
        this.enableRotativeQueue = builder.enableRotativeQueue;
        this.rotativeQueueSize = builder.rotativeQueueSize;
        this.rotativeQueueInterval = builder.rotativeQueueInterval;
        this.batchSize = builder.batchSize;
        this.tickDelay = builder.tickDelay;
        this.displayName = builder.displayName;
        this.conditionSet = builder.conditionSet;
        this.valueFormat = builder.valueFormat;
        this.allowZeroValues = builder.allowZeroValues;
    }

    public int getSize() {
        return size;
    }

    @Nullable
    public String getDisplayName() {
        return displayName;
    }

    @Nullable
    public String getValueFormat() {
        return valueFormat;
    }

    @NotNull
    public ConditionSet getConditionSet() {
        return conditionSet;
    }

    public boolean isEnableOnlineQueue() {
        return enableOnlineQueue;
    }

    public int getOnlineQueueInterval() {
        return onlineQueueInterval;
    }

    public boolean isEnableRotativeQueue() {
        return enableRotativeQueue;
    }

    public int getRotativeQueueSize() {
        return rotativeQueueSize;
    }

    public int getRotativeQueueInterval() {
        return rotativeQueueInterval;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public int getTickDelay() {
        return tickDelay;
    }

    public boolean isAllowZeroValues() {
        return allowZeroValues;
    }

    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int size = 10;
        private boolean enableOnlineQueue = false;
        private int onlineQueueInterval = 288000;
        private boolean enableRotativeQueue = false;
        private int rotativeQueueSize = 20;
        private int rotativeQueueInterval = 288000;
        private int batchSize = 5;
        private int tickDelay = 20;
        private String displayName = null;
        private ConditionSet conditionSet = EMPTY;
        private String valueFormat = null;
        private boolean allowZeroValues = false;

        private Builder() {
        }

        @NotNull
        public Builder size(int size) {
            if (size <= 0) {
                throw new IllegalArgumentException("size must be positive");
            }
            this.size = size;
            return this;
        }

        @NotNull
        public Builder enableOnlineQueue(boolean enable) {
            this.enableOnlineQueue = enable;
            return this;
        }

        @NotNull
        public Builder onlineQueueInterval(int interval) {
            if (interval <= 0) {
                throw new IllegalArgumentException("interval must be positive");
            }
            this.onlineQueueInterval = interval;
            return this;
        }

        @NotNull
        public Builder enableRotativeQueue(boolean enable) {
            this.enableRotativeQueue = enable;
            return this;
        }

        @NotNull
        public Builder rotativeQueueSize(int size) {
            if (size <= 0) {
                throw new IllegalArgumentException("rotativeQueueSize must be positive");
            }
            this.rotativeQueueSize = size;
            return this;
        }

        @NotNull
        public Builder rotativeQueueInterval(int interval) {
            if (interval <= 0) {
                throw new IllegalArgumentException("rotativeQueueInterval must be positive");
            }
            this.rotativeQueueInterval = interval;
            return this;
        }

        @NotNull
        public Builder batchSize(int size) {
            if (size <= 0) {
                throw new IllegalArgumentException("batchSize must be positive");
            }
            this.batchSize = size;
            return this;
        }

        @NotNull
        public Builder tickDelay(int delay) {
            if (delay < 0) {
                throw new IllegalArgumentException("tickDelay cannot be negative");
            }
            this.tickDelay = delay;
            return this;
        }

        @NotNull
        public Builder displayName(@Nullable String displayName) {
            this.displayName = displayName;
            return this;
        }

        @NotNull
        public Builder conditionSet(@NotNull ConditionSet conditionSet) {
            this.conditionSet = conditionSet;
            return this;
        }

        @NotNull
        public Builder valueFormat(@Nullable String valueFormat) {
            this.valueFormat = valueFormat;
            return this;
        }

        @NotNull
        public Builder allowZeroValues(boolean allow) {
            this.allowZeroValues = allow;
            return this;
        }

        @NotNull
        public TopConfig build() {
            return new TopConfig(this);
        }
    }

    @Override
    public String toString() {
        return "TopConfig{" +
                "size=" + size +
                ", enableOnlineQueue=" + enableOnlineQueue +
                ", onlineQueueInterval=" + onlineQueueInterval +
                ", enableRotativeQueue=" + enableRotativeQueue +
                ", rotativeQueueSize=" + rotativeQueueSize +
                ", batchSize=" + batchSize +
                ", tickDelay=" + tickDelay +
                ", displayName='" + displayName + '\'' +
                '}';
    }
}