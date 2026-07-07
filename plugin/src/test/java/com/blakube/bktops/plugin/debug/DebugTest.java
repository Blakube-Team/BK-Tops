package com.blakube.bktops.plugin.debug;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class DebugTest {

    private final List<String> captured = new ArrayList<>();

    private Logger captureLogger() {
        Logger logger = Logger.getLogger("bktops-debug-test");
        logger.setUseParentHandlers(false);
        for (Handler h : logger.getHandlers()) logger.removeHandler(h);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { captured.add(record.getMessage()); }
            @Override public void flush() {}
            @Override public void close() {}
        });
        return logger;
    }

    @AfterEach
    void reset() {
        Debug.setEnabled(false);
        captured.clear();
    }

    @Test
    void doesNotLogWhenDisabled() {
        Debug.init(captureLogger(), false);
        Debug.log("should not appear");
        assertTrue(captured.isEmpty());
    }

    @Test
    void logsWhenEnabled() {
        Debug.init(captureLogger(), true);
        Debug.log("hello");
        assertEquals(1, captured.size());
        assertTrue(captured.get(0).contains("hello"));
        assertTrue(captured.get(0).contains("[DEBUG]"));
    }

    @Test
    void supplierNotEvaluatedWhenDisabled() {
        Debug.init(captureLogger(), false);
        AtomicInteger calls = new AtomicInteger();
        Debug.log(() -> {
            calls.incrementAndGet();
            return "expensive";
        });
        assertEquals(0, calls.get(), "supplier must not run when debug is off");
    }

    @Test
    void supplierEvaluatedWhenEnabled() {
        Debug.init(captureLogger(), true);
        AtomicInteger calls = new AtomicInteger();
        Debug.log(() -> {
            calls.incrementAndGet();
            return "expensive";
        });
        assertEquals(1, calls.get());
        assertTrue(captured.get(0).contains("expensive"));
    }

    @Test
    void templateSubstitutesPlaceholdersInOrder() {
        Debug.init(captureLogger(), true);
        Debug.log("[{}] value {} for {}", "money", 42.0, "Steve");
        assertTrue(captured.get(0).contains("[money] value 42.0 for Steve"));
    }

    @Test
    void templateLeavesExtraBracesWhenNoArgs() {
        Debug.init(captureLogger(), true);
        Debug.log("no args {} here");
        assertTrue(captured.get(0).contains("no args {} here"));
    }

    @Test
    void setEnabledTogglesAtRuntime() {
        Debug.init(captureLogger(), false);
        Debug.log("first");
        Debug.setEnabled(true);
        Debug.log("second");
        assertEquals(1, captured.size());
        assertTrue(captured.get(0).contains("second"));
    }

    @Test
    void logsThrowableWithMessage() {
        Logger logger = captureLogger();
        Debug.init(logger, true);
        Debug.log("boom", new IllegalStateException("bad"));
        assertEquals(1, captured.size());
        assertTrue(captured.get(0).contains("boom"));
    }

    @Test
    void throwableNotLoggedWhenDisabled() {
        Debug.init(captureLogger(), false);
        Debug.log("boom", new IllegalStateException("bad"));
        assertTrue(captured.isEmpty());
    }

    @Test
    void isEnabledReflectsState() {
        Debug.init(captureLogger(), true);
        assertTrue(Debug.isEnabled());
        Debug.setEnabled(false);
        assertFalse(Debug.isEnabled());
    }

    @Test
    void usesWarningLevelForThrowable() {
        Logger logger = Logger.getLogger("bktops-debug-level-test");
        logger.setUseParentHandlers(false);
        List<Level> levels = new ArrayList<>();
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { levels.add(record.getLevel()); }
            @Override public void flush() {}
            @Override public void close() {}
        });
        Debug.init(logger, true);
        Debug.log("oops", new RuntimeException("x"));
        assertEquals(List.of(Level.WARNING), levels);
    }
}
