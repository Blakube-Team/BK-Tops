package com.blakube.bktops.plugin.processor;

import com.blakube.bktops.api.queue.Priority;
import com.blakube.bktops.api.queue.ProcessingQueue;
import com.blakube.bktops.api.resolver.NameResolver;
import com.blakube.bktops.api.result.UpdateResult;
import com.blakube.bktops.api.storage.TopStorage;
import com.blakube.bktops.api.storage.config.TopConfig;
import com.blakube.bktops.api.provider.ValueProvider;
import com.blakube.bktops.plugin.queue.PriorityProcessingQueue;
import com.blakube.bktops.plugin.storage.database.connection.DatabaseExecutors;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShouldBypassTest {

    private UUID playerId;
    private Player mockPlayer;
    private DefaultTopProcessor<UUID> processor;
    private List<UpdateResult<UUID>> results;

    @BeforeEach
    void setUp() {
        playerId = UUID.randomUUID();
        mockPlayer = mock(Player.class);
        results = new ArrayList<>();

        JavaPlugin plugin = mock(JavaPlugin.class);
        org.bukkit.configuration.file.FileConfiguration mockConfig = mock(org.bukkit.configuration.file.FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(mockConfig);
        when(mockConfig.getBoolean("debug", false)).thenReturn(false);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        TopConfig config = TopConfig.builder()
                .size(10)
                .batchSize(5)
                .tickDelay(20)
                .build();

        ValueProvider<UUID> provider = new ValueProvider<>() {
            @Override public Double getValue(UUID id) { return 113_000_000.0; }
            @Override public String getName() { return "test"; }
        };
        NameResolver<UUID> resolver = id -> "Diego";

        @SuppressWarnings("unchecked")
        TopStorage<UUID> storage = mock(TopStorage.class);

        ProcessingQueue<UUID> queue = new PriorityProcessingQueue<>();
        queue.enqueue(playerId, Priority.HIGH, "test");

        processor = new DefaultTopProcessor<>(
                plugin,
                "money",
                config,
                provider,
                resolver,
                storage,
                queue,
                batch -> batch.forEach(results::add),
                id -> {}
        );
    }

    @Test
    void opPlayer_withBypassPermission_isSkipped() {
        when(mockPlayer.hasPermission("bktops.bypass.money")).thenReturn(true);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(mockPlayer);
            processor.processBatch(5);
        }

        DatabaseExecutors.awaitPendingTasks();
        assertTrue(results.isEmpty(), "OP player with bypass permission should produce no results");
    }

    @Test
    void opPlayer_withoutBypassPermission_isProcessed() {
        when(mockPlayer.hasPermission("bktops.bypass.money")).thenReturn(false);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(mockPlayer);
            processor.processBatch(5);
        }

        DatabaseExecutors.awaitPendingTasks();
        assertFalse(results.isEmpty(), "Player without bypass permission should be processed");
        assertTrue(results.get(0).isSuccess());
        assertEquals(113_000_000.0, results.get(0).getNewValue());
    }

    @Test
    void offlinePlayer_isProcessed() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(null);
            processor.processBatch(5);
        }

        DatabaseExecutors.awaitPendingTasks();
        assertFalse(results.isEmpty(), "Offline player should always be processed");
        assertTrue(results.get(0).isSuccess());
    }

    @Test
    void bug_unregisteredPermissionDefaultsToOpTrue_demonstratedAndFixed() {
        when(mockPlayer.isOp()).thenReturn(true);
        when(mockPlayer.hasPermission("bktops.bypass.money")).thenReturn(true);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(mockPlayer);
            processor.processBatch(5);
        }

        DatabaseExecutors.awaitPendingTasks();
        assertTrue(results.isEmpty());
    }
}
