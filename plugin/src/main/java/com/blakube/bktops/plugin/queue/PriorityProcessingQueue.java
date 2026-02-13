package com.blakube.bktops.plugin.queue;

import com.blakube.bktops.api.queue.Priority;
import com.blakube.bktops.api.queue.ProcessingQueue;
import com.blakube.bktops.api.queue.QueueEntry;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class PriorityProcessingQueue<K> implements ProcessingQueue<K> {

    private final Map<Priority, Queue<QueueEntry<K>>> queues;
    private final Set<K> inQueue;

    public PriorityProcessingQueue() {
        this.queues = new EnumMap<>(Priority.class);
        for (Priority priority : Priority.values()) {
            queues.put(priority, new ConcurrentLinkedQueue<>());
        }
        this.inQueue = ConcurrentHashMap.newKeySet();
    }

    @Override
    public boolean enqueue(@NotNull K identifier,
                          @NotNull Priority priority,
                          @NotNull String reason) {
        Objects.requireNonNull(identifier, "identifier cannot be null");
        Objects.requireNonNull(priority, "priority cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");

        if (!inQueue.add(identifier)) {
            return false;
        }

        queues.get(priority).offer(new QueueEntry<>(identifier, priority, reason));
        return true;
    }

    @Override
    public int enqueueAll(@NotNull Collection<K> identifiers,
                         @NotNull Priority priority,
                         @NotNull String reason) {
        Objects.requireNonNull(identifiers, "identifiers cannot be null");
        Objects.requireNonNull(priority, "priority cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");

        int count = 0;
        for (K identifier : identifiers) {
            if (enqueue(identifier, priority, reason)) {
                count++;
            }
        }
        return count;
    }

    @Override
    @NotNull
    public List<QueueEntry<K>> poll(int maxCount) {
        if (maxCount <= 0) {
            return Collections.emptyList();
        }

        List<QueueEntry<K>> result = new ArrayList<>(maxCount);

        for (Priority priority : Priority.values()) {
            Queue<QueueEntry<K>> queue = queues.get(priority);

            while (result.size() < maxCount && !queue.isEmpty()) {
                QueueEntry<K> entry = queue.poll();
                if (entry != null) {
                    inQueue.remove(entry.getIdentifier());
                    result.add(entry);
                }
            }

            if (result.size() >= maxCount) {
                break;
            }
        }

        return result;
    }

    @Override
    public boolean isEmpty() {
        return queues.values().stream().allMatch(Queue::isEmpty);
    }

    @Override
    public int size() {
        return queues.values().stream()
                .mapToInt(Queue::size)
                .sum();
    }

    @Override
    public int size(@NotNull Priority priority) {
        Objects.requireNonNull(priority, "priority cannot be null");
        return queues.get(priority).size();
    }

    @Override
    public boolean contains(@NotNull K identifier) {
        Objects.requireNonNull(identifier, "identifier cannot be null");
        return inQueue.contains(identifier);
    }

    @Override
    public void clear() {
        queues.values().forEach(Queue::clear);
        inQueue.clear();
    }

    @Override
    public void clear(@NotNull Priority priority) {
        Objects.requireNonNull(priority, "priority cannot be null");
        
        Queue<QueueEntry<K>> queue = queues.get(priority);
        queue.forEach(entry -> inQueue.remove(entry.getIdentifier()));
        queue.clear();
    }
}