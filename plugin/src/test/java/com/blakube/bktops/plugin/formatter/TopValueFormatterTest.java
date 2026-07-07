package com.blakube.bktops.plugin.formatter;

import com.blakube.bktops.api.processor.TopProcessor;
import com.blakube.bktops.api.provider.ValueProvider;
import com.blakube.bktops.api.queue.Priority;
import com.blakube.bktops.api.queue.ProcessingQueue;
import com.blakube.bktops.api.storage.config.TopConfig;
import com.blakube.bktops.api.top.Top;
import com.blakube.bktops.api.top.TopEntry;
import com.blakube.bktops.plugin.TestConfigContainer;
import com.blakube.bktops.plugin.provider.DetectableValueKind;
import com.blakube.bktops.plugin.provider.ValueKind;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TopValueFormatterTest {

    private TopValueFormatter newFormatter() {
        return new TopValueFormatter(new TestConfigContainer(Map.of(
                "time-format.separator", " ",
                "time-format.significant-figures", 3,
                "number-format.mode", "EXACT"
        )));
    }

    @Test
    void autoDefaultsToNumberBeforeAnyDetection() {
        TopValueFormatter f = newFormatter();
        StubProvider provider = new StubProvider(ValueKind.UNKNOWN);
        Top<String> top = new StubTop("t1", null, provider);
        assertInstanceOf(NumberValueFormatter.class, f.resolve(top));
    }

    @Test
    void autoSwitchesToTimeOnceDetected() {
        TopValueFormatter f = newFormatter();
        StubProvider provider = new StubProvider(ValueKind.UNKNOWN);
        Top<String> top = new StubTop("t1", null, provider);

        assertInstanceOf(NumberValueFormatter.class, f.resolve(top));
        provider.detected = ValueKind.TIME;
        assertInstanceOf(TimeValueFormatter.class, f.resolve(top));
    }

    @Test
    void autoTimeIsStickyAndDoesNotRevert() {
        TopValueFormatter f = newFormatter();
        StubProvider provider = new StubProvider(ValueKind.TIME);
        Top<String> top = new StubTop("t1", null, provider);

        assertInstanceOf(TimeValueFormatter.class, f.resolve(top));
        
        provider.detected = ValueKind.NUMBER;
        
        assertInstanceOf(TimeValueFormatter.class, f.resolve(top));
    }

    @Test
    void explicitTimeFormatIsAuthoritativeRegardlessOfDetection() {
        TopValueFormatter f = newFormatter();
        StubProvider provider = new StubProvider(ValueKind.NUMBER);
        Top<String> top = new StubTop("t1", "TIME", provider);
        assertInstanceOf(TimeValueFormatter.class, f.resolve(top));
    }

    @Test
    void explicitNumberFormatIsAuthoritativeRegardlessOfDetection() {
        TopValueFormatter f = newFormatter();
        StubProvider provider = new StubProvider(ValueKind.TIME);
        Top<String> top = new StubTop("t1", "COMPACT", provider);
        assertInstanceOf(NumberValueFormatter.class, f.resolve(top));
    }

    @Test
    void unknownExplicitFormatFallsBackToDefault() {
        TopValueFormatter f = newFormatter();
        StubProvider provider = new StubProvider(ValueKind.NUMBER);
        Top<String> top = new StubTop("t1", "NONSENSE", provider);
        assertInstanceOf(NumberValueFormatter.class, f.resolve(top));
    }

    @Test
    void reloadClearsStickyTimeLock() {
        TopValueFormatter f = newFormatter();
        StubProvider provider = new StubProvider(ValueKind.TIME);
        Top<String> top = new StubTop("t1", null, provider);

        assertInstanceOf(TimeValueFormatter.class, f.resolve(top));
        provider.detected = ValueKind.NUMBER;
        f.reload();
        
        assertInstanceOf(NumberValueFormatter.class, f.resolve(top));
    }

    @Test
    void forModeLooksUpByName() {
        TopValueFormatter f = newFormatter();
        assertInstanceOf(TimeValueFormatter.class, f.forMode("time"));
        assertInstanceOf(NumberValueFormatter.class, f.forMode("COMPACT"));
        assertNull(f.forMode("does-not-exist"));
        assertNull(f.forMode(null));
    }

    

    private static final class StubProvider implements ValueProvider<String>, DetectableValueKind {
        volatile ValueKind detected;
        StubProvider(ValueKind detected) { this.detected = detected; }
        @Override public Double getValue(@NotNull String identifier) { return null; }
        @Override public @NotNull String getName() { return "stub"; }
        @Override public @NotNull ValueKind getDetectedValueKind() { return detected; }
    }

    private static final class StubTop implements Top<String> {
        private final String id;
        private final TopConfig config;
        private final ValueProvider<String> provider;

        StubTop(String id, String valueFormat, ValueProvider<String> provider) {
            this.id = id;
            this.config = TopConfig.builder().valueFormat(valueFormat).build();
            this.provider = provider;
        }

        @Override public @NotNull String getId() { return id; }
        @Override public @NotNull TopConfig getConfig() { return config; }
        @Override public @NotNull ValueProvider<String> getValueProvider() { return provider; }

        @Override public @NotNull List<TopEntry<String>> getEntries() { throw new UnsupportedOperationException(); }
        @Override public @NotNull Optional<TopEntry<String>> getEntry(int position) { throw new UnsupportedOperationException(); }
        @Override public int getPosition(@NotNull String identifier) { throw new UnsupportedOperationException(); }
        @Override public boolean isInTop(@NotNull String identifier) { throw new UnsupportedOperationException(); }
        @Override public @NotNull Optional<Double> getMinValue() { throw new UnsupportedOperationException(); }
        @Override public @NotNull Optional<Double> getMaxValue() { throw new UnsupportedOperationException(); }
        @Override public int getCurrentSize() { throw new UnsupportedOperationException(); }
        @Override public void markDirty(@NotNull String identifier, @NotNull String reason) { throw new UnsupportedOperationException(); }
        @Override public void enqueue(@NotNull Collection<String> identifiers, @NotNull Priority priority, @NotNull String reason) { throw new UnsupportedOperationException(); }
        @Override public @NotNull ProcessingQueue<String> getQueue() { throw new UnsupportedOperationException(); }
        @Override public @NotNull TopProcessor<String> getProcessor() { throw new UnsupportedOperationException(); }
        @Override public void refresh() { throw new UnsupportedOperationException(); }
        @Override public void reset() { throw new UnsupportedOperationException(); }
    }
}
