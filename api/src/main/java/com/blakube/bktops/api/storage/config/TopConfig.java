package com.blakube.bktops.api.storage.config;

import org.jetbrains.annotations.NotNull;

/**
 * Configuration for a leaderboard.
 */
public final class TopConfig {

    private final int size;
    private final boolean enableOnlineQueue;
    private final int onlineQueueInterval;
    private final boolean enableRotativeQueue;
    private final int rotativeQueueSize;
    private final int batchSize;
    private final int tickDelay;

    private TopConfig(@NotNull Builder builder) {
        this.size = builder.size;
        this.enableOnlineQueue = builder.enableOnlineQueue;
        this.onlineQueueInterval = builder.onlineQueueInterval;
        this.enableRotativeQueue = builder.enableRotativeQueue;
        this.rotativeQueueSize = builder.rotativeQueueSize;
        this.batchSize = builder.batchSize;
        this.tickDelay = builder.tickDelay;
    }

    public int getSize() {
        return size;
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

    public int getBatchSize() {
        return batchSize;
    }

    public int getTickDelay() {
        return tickDelay;
    }

    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int size = 10;
        private boolean enableOnlineQueue = true;
        private int onlineQueueInterval = 100;
        private boolean enableRotativeQueue = true;
        private int rotativeQueueSize = 50;
        private int batchSize = 10;
        private int tickDelay = 1;

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
                '}';
    }
}