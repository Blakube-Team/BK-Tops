package com.blakube.bktops.plugin.cache;

import com.blakube.bktops.api.top.TopEntry;
import com.blakube.bktops.plugin.storage.cache.TopEntryCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TopEntryCacheTest {

    private TopEntryCache<UUID> cache;

    @BeforeEach
    void setUp() {
        cache = new TopEntryCache<>();
    }

    @Test
    void updateEntry_addsNewEntry() {
        UUID id = UUID.randomUUID();
        int pos = cache.updateEntry(id, "Player1", 1000.0, 10);

        assertEquals(1, pos);
        assertEquals(1, cache.size());
    }

    @Test
    void updateEntry_sortsDescending() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        cache.updateEntry(id1, "Low", 100.0, 10);
        cache.updateEntry(id2, "High", 9000.0, 10);
        cache.updateEntry(id3, "Mid", 500.0, 10);

        List<TopEntry<UUID>> entries = cache.getEntriesCopy();
        assertEquals(3, entries.size());
        assertEquals(9000.0, entries.get(0).getValue());
        assertEquals(500.0, entries.get(1).getValue());
        assertEquals(100.0, entries.get(2).getValue());
        assertEquals(1, entries.get(0).getPosition());
        assertEquals(2, entries.get(1).getPosition());
        assertEquals(3, entries.get(2).getPosition());
    }

    @Test
    void updateEntry_enforcesMaxSize() {
        UUID lowestId = UUID.randomUUID();
        cache.updateEntry(lowestId, "Lowest", 1.0, 3);
        cache.updateEntry(UUID.randomUUID(), "Second", 200.0, 3);
        cache.updateEntry(UUID.randomUUID(), "Third", 100.0, 3);

        assertEquals(3, cache.size());

        UUID newHighId = UUID.randomUUID();
        int pos = cache.updateEntry(newHighId, "New", 500.0, 3);

        assertEquals(3, cache.size());
        assertEquals(1, pos);
        assertEquals(-1, cache.getPosition(lowestId));
    }

    @Test
    void updateEntry_returnsMinusOneWhenNotQualifying() {
        cache.updateEntry(UUID.randomUUID(), "P1", 1000.0, 2);
        cache.updateEntry(UUID.randomUUID(), "P2", 900.0, 2);

        UUID lowId = UUID.randomUUID();
        int pos = cache.updateEntry(lowId, "Low", 1.0, 2);

        assertEquals(-1, pos);
        assertEquals(-1, cache.getPosition(lowId));
        assertEquals(2, cache.size());
    }

    @Test
    void updateEntry_updatesExistingEntry() {
        UUID id = UUID.randomUUID();
        cache.updateEntry(id, "Player", 100.0, 10);
        cache.updateEntry(id, "Player", 9999.0, 10);

        assertEquals(1, cache.size());
        assertEquals(9999.0, cache.getEntryAt(1).get().getValue());
    }

    @Test
    void getPosition_returnsCorrectPosition() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        cache.updateEntry(id1, "P1", 200.0, 10);
        cache.updateEntry(id2, "P2", 100.0, 10);

        assertEquals(1, cache.getPosition(id1));
        assertEquals(2, cache.getPosition(id2));
    }

    @Test
    void getPosition_returnsMinusOneForUnknown() {
        assertEquals(-1, cache.getPosition(UUID.randomUUID()));
    }

    @Test
    void setEntries_replacesAll() {
        cache.updateEntry(UUID.randomUUID(), "Old", 999.0, 10);

        UUID newId = UUID.randomUUID();
        cache.setEntries(List.of(new TopEntry<>(newId, "New", 42.0, 1)));

        assertEquals(1, cache.size());
        assertEquals(1, cache.getPosition(newId));
    }

    @Test
    void removeEntry_removesAndShiftsPositions() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        cache.updateEntry(id1, "P1", 300.0, 10);
        cache.updateEntry(id2, "P2", 200.0, 10);
        cache.updateEntry(id3, "P3", 100.0, 10);

        cache.removeEntry(id2);

        assertEquals(2, cache.size());
        assertEquals(1, cache.getPosition(id1));
        assertEquals(2, cache.getPosition(id3));
        assertEquals(-1, cache.getPosition(id2));
    }

    @Test
    void getEntryByIdentifier_returnsCorrectEntry() {
        UUID id = UUID.randomUUID();
        cache.updateEntry(id, "Diego", 113_000_000.0, 10);

        var entry = cache.getEntryByIdentifier(id);
        assertTrue(entry.isPresent());
        assertEquals("Diego", entry.get().getDisplayName());
        assertEquals(113_000_000.0, entry.get().getValue());
        assertEquals(1, entry.get().getPosition());
    }

    @Test
    void getMinValue_returnsLowest() {
        cache.updateEntry(UUID.randomUUID(), "P1", 9000.0, 10);
        cache.updateEntry(UUID.randomUUID(), "P2", 1.0, 10);
        cache.updateEntry(UUID.randomUUID(), "P3", 500.0, 10);

        assertEquals(1.0, cache.getMinValue().orElseThrow());
    }

    @Test
    void getMaxValue_returnsHighest() {
        cache.updateEntry(UUID.randomUUID(), "P1", 9000.0, 10);
        cache.updateEntry(UUID.randomUUID(), "P2", 1.0, 10);

        assertEquals(9000.0, cache.getMaxValue().orElseThrow());
    }

    @Test
    void emptyCache_minMaxEmpty() {
        assertTrue(cache.getMinValue().isEmpty());
        assertTrue(cache.getMaxValue().isEmpty());
    }
}
