package com.openggf.graphics.text;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class TestHostTextTruncationWarnings {
    private static final TextLanguage EN = TextLanguage.of("en");

    @Test
    void ellipsisWarnsWithLanguageAndMeasuredSpaceWithoutRepeatingEachFrame() {
        var text = new HostText(FontRegistry.bundled());
        String value = "Centred to native viewport";
        try (var logs = new Warnings()) {
            String fitted = text.fit(value, 80, TextFace.COMPACT, EN);
            assertTrue(fitted.endsWith("..."));
            assertNotEquals(value, fitted);
            assertTrue(text.width(fitted, TextFace.COMPACT, EN) <= 80);
            for (int frame = 0; frame < 120; frame++)
                assertEquals(fitted, text.fit(value, 80, TextFace.COMPACT, EN));
            text.fit(value, 60, TextFace.COMPACT, EN);
            assertEquals(1, logs.records.size(), "Repeated frames and resizing must not flood warnings");
            var record = logs.records.getFirst();
            assertEquals(Level.WARNING, record.getLevel());
            String warning = new SimpleFormatter().formatMessage(record);
            assertTrue(warning.contains("language=en"), warning);
            assertTrue(warning.contains("face=COMPACT"), warning);
            assertTrue(warning.contains("available=80px"), warning);
            assertTrue(warning.contains("required=" + text.width(value, TextFace.COMPACT, EN) + "px"), warning);
            assertTrue(warning.contains(value), warning);
            text.fit(value, 80, TextFace.LABEL, EN);
            text.fit(value, 80, TextFace.COMPACT, TextLanguage.of("fr"));
            assertEquals(3, logs.records.size(), "Different faces and languages still expose their overflow");
        }
    }

    @Test
    void preservedTextDoesNotWarnButShortenedEllipsisDoes() {
        var text = new HostText(FontRegistry.bundled());
        try (var logs = new Warnings()) {
            assertEquals("Exact", text.fit("Exact", 30, TextFace.COMPACT, EN));
            assertEquals("Wait...", text.fit("Wait...", 60, TextFace.COMPACT, EN));
            assertEquals("", text.fit("", 30, TextFace.COMPACT, EN));
            assertTrue(logs.records.isEmpty(), "An existing literal ellipsis is not a fitting warning");
            assertEquals("..", text.fit("Long label", text.width("..", TextFace.COMPACT, EN), TextFace.COMPACT, EN));
            assertEquals(1, logs.records.size());
        }
    }

    @Test
    void disabledWarningsDoNotSuppressLaterEnabledDiagnostics() {
        var text = new HostText(FontRegistry.bundled());
        try (var logs = new Warnings()) {
            logs.logger.setLevel(Level.OFF);
            text.fit("Long label", 30, TextFace.COMPACT, EN);
            assertTrue(logs.records.isEmpty());
            logs.logger.setLevel(Level.WARNING);
            text.fit("Long label", 30, TextFace.COMPACT, EN);
            assertEquals(1, logs.records.size());
        }
    }

    @Test
    void longMultilineTextKeepsWarningsBoundedAndOnOneLine() {
        var text = new HostText(FontRegistry.bundled());
        try (var logs = new Warnings()) {
            text.fit("A\r\nB\t" + "C".repeat(300), 30, TextFace.COMPACT, EN);
            assertEquals(1, logs.records.size());
            String warning = new SimpleFormatter().formatMessage(logs.records.getFirst());
            assertTrue(warning.contains("A\\r\\nB\\t"), warning);
            assertFalse(warning.contains("\n"), warning);
            assertFalse(warning.contains("C".repeat(160)), warning);
            assertTrue(warning.endsWith("...\""), warning);
        }
    }

    @Test void customElisionSharesThePrefixWarningIdentityAndSpaceDiagnostics() {
        var text = new HostText(FontRegistry.bundled());
        try (var logs = new Warnings()) {
            text.reportEllipsis("Folder anchor and long nearest folder", 40, TextFace.COMPACT, EN);
            text.fit("Folder anchor and long nearest folder", 30, TextFace.COMPACT, EN);
            assertEquals(1, logs.records.size());
            assertTrue(logs.records.getFirst().getMessage().contains("available=40px"));
            text.reportEllipsis("Fits", 100, TextFace.COMPACT, EN);
            assertEquals(1, logs.records.size());
        }
    }

    private static final class Warnings extends Handler implements AutoCloseable {
        private final Logger logger = Logger.getLogger(HostText.class.getName());
        private final Level previousLevel = logger.getLevel();
        private final boolean previousParents = logger.getUseParentHandlers();
        private final List<LogRecord> records = new ArrayList<>();
        private Warnings() {
            logger.setLevel(Level.ALL);
            logger.setUseParentHandlers(false);
            logger.addHandler(this);
        }
        @Override public void publish(LogRecord record) { records.add(record); }
        @Override public void flush() { }
        @Override public void close() {
            logger.removeHandler(this);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousParents);
        }
    }
}
