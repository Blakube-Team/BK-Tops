package com.blakube.bktops.api.queue;

public enum Priority {
    CRITICAL(0),
    HIGH(1),
    MEDIUM(2),
    LOW(3);

    private final int level;

    Priority(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }
}