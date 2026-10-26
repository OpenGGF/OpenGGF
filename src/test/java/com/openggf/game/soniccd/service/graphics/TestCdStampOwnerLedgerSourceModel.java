package com.openggf.game.soniccd.service.graphics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.soniccd.device.CdStampGraphics;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

import static com.openggf.game.soniccd.service.graphics.CdStampGraphicsService.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt in with -Dsoniccd.stamp.ownerLedger=/absolute/native/owner-ledger. No process is launched.
 * This is a comparison-only consumer of the pinned R4 source-owner events. Each graphics start
 * becomes an actual production service submission HERE IN TEST ONLY. Production has no ledger
 * reader and no recorder/fixture-selected behavior. No frame or operation ordinal selects a job.
 *
 * All 1,052 operations, both widths and three priorities are compared, including 1,800 explicit
 * CD rebases. Synthetic zero RAM exercises production rendering/cursor progress, not native pixel
 * equality. The six independent captured RAM continuations remain partial, are not loaded here,
 * and must never be silently restarted as full native submissions.
 *
 * R4 observes starts, end-of-frame state and the final before/after rendering pair, not every
 * gfx_update. Between observations the TEST deliberately batches to the source-model pre-final
 * line boundary. At completion aux0 is gfx_update's signed elapsed LOCAL VARIABLE, not a global
 * timestamp. Adding it to the independently modeled pre-final counter reconstructs that one
 * source call argument, which must equal the observed scheduler or sub-CPU residual. This checks
 * cost, remaining lines, rebase arithmetic and phase/cursor order, not every native service call,
 * poll state, native pixel content, physical timing, a stock game module, or CD-05 closure.
 */
class TestCdStampOwnerLedgerSourceModel {
    private static final WordRamAccess SUB = new WordRamAccess(WordRamMode.TWO_M, WordRamOwner.SUB);
    private static final CompletionSignals QUIET = new CompletionSignals(false, false);
    private static final CompletionSignals IRQ_ONLY = new CompletionSignals(true, false);
    private static final int EVENT_BYTES = 256;
    private static final int CD_BEFORE = 5, CD_AFTER = 6, RESET_AFTER = 13;
    private static final int START = 17, BUSY_CLEAR = 18, PIXELS_DONE = 19;
    private static final int RESET_EPOCH = 8, CD_EPOCH = 10, OPERATION = 13, SUB_CYCLES = 15, SCHEDULER_CYCLES = 16;
    private static final int GFX_CYCLES = 17, COST = 18, WORD_MODE = 27;
    private static final int MODE58 = 29, MAP5A = 30, CELLS5C = 31, IMAGE5E = 32;
    private static final int OFFSET60 = 33, WIDTH62 = 34, HEIGHT64 = 35, TRACE66 = 36;
    private static final int TRACE = 37, MAP = 38, IMAGE = 39, IRQ_PENDING = 44, AUX0 = 51;

    @Test
    void comparesEveryR4SubmissionRemainingCountCostRebaseAndBothCompletionPhases() throws IOException {
        String configured = System.getProperty("soniccd.stamp.ownerLedger");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(),
                "Set soniccd.stamp.ownerLedger for the external R4 comparison");
        Path root = Path.of(configured).toRealPath();
        ObjectMapper json = new ObjectMapper();
        JsonNode inventory = json.readTree(pinned(root, "source-r4/inventory.json", 65536,
                "a7274052b63acefccdd53e011115139e34c68dc85579700db96046f3bd716a2a"));
        JsonNode manifest = json.readTree(pinned(root, "owner-controls-r4/manifest.json", 100000,
                "d9ea3d679224aa7c97293f17a144f7ef180a9caad544d8fd675d0ff6f01db9a7"));
        assertEquals("soniccd.native-owner-source-snapshot.v2", inventory.path("schema").asText());
        assertEquals("7d9ef07172a299309db8b95a440af1ac03f97bed80fd19e67b2b5675fa26e5e4",
                inventory.path("source_fingerprint").asText());
        assertEquals("soniccd.native-owner-controls.v1", manifest.path("schema").asText());
        assertEquals("82af02e7080426f11d5618221a7b05d62565504cbe4caa698cf687d76077b515",
                manifest.path("instrumented_build_identity").path("compressed_sha256").asText());
        assertEquals(manifest.path("instrumented_build_identity").path("compressed_sha256"),
                inventory.path("final_build").path("compressed_sha256"));
        assertTrue(manifest.path("official_source_av_views_restore_equal").asBoolean());
        assertEquals(0, manifest.path("live_sources_changed_since_snapshot").size());
        assertEquals(0, manifest.path("summaries").path("enabled-1").path("overflow_fault").asInt(-1));
        assertEquals(0, manifest.path("summaries").path("enabled-1").path("overflow_dropped").asInt(-1));

        byte[] payload = pinned(root, "owner-controls-r4/enabled-1/events.bin", 32 * 1024 * 1024,
                "3e28d3e78e76ab8408a6e63304c003e834ee206e2650a5cb72b33138b7e0aeee");
        assertEquals(26533376, payload.length);
        assertEquals(0, payload.length % EVENT_BYTES);
        ByteBuffer bytes = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        var service = new CdStampGraphicsService(0);
        byte[] semanticRam = new byte[CdStampGraphics.WORD_RAM_BYTES];
        Operation operation = null;
        ServiceStep finishing = null;
        Row busy = null;
        Row beforeRebase = null;
        long sequence = 0;
        int starts = 0, clears = 0, finals = 0, rebases = 0;
        Set<String> profiles = new HashSet<>();

        for (int offset = 0; offset < payload.length; offset += EVENT_BYTES) {
            Row row = new Row(bytes, offset);
            assertEquals(++sequence, row.sequence(), "retained event sequence");
            if (busy != null) { assertEquals(PIXELS_DONE, row.kind(), "final phases are adjacent"); }
            switch (row.kind()) {
                case RESET_AFTER -> {
                    service.reset(row.u32(RESET_EPOCH), row.u32(CD_EPOCH));
                    operation = null;
                    assertEquals(row.u32(GFX_CYCLES), service.graphicsClock().cycles());
                }
                case START -> {
                    if (operation != null) { assertTrue(service.readback(operation).finalPixelsRendered()); }
                    // This route's actual starts are 2M/SUB. The test declares that access; it does
                    // not ask production to interpret or grant access from an observed mode word.
                    assertEquals(0, row.word(WORD_MODE) & 5);
                    var request = CdStampOperationRequest.fromRegisterWords(row.word(MODE58), row.word(WORD_MODE),
                            row.word(MAP5A), row.word(CELLS5C), row.word(WIDTH62), row.word(TRACE66),
                            row.word(IMAGE5E), row.word(OFFSET60), row.word(HEIGHT64));
                    operation = service.submit(request,
                            clock(row, row.u32(SUB_CYCLES)), SUB);
                    Arrays.fill(semanticRam, (byte) 0);
                    assertEquals(request.initialRenderState(), service.readback(operation).rendered());
                    assertEquals(row.u32(MAP), request.renderRequest().mapByteAddress());
                    assertMatches(row, service, operation);
                    assertTrue(service.readback(operation).busy());
                    profiles.add(request.renderRequest().widthPixels() + "/" + request.renderRequest().priority());
                    starts++;
                }
                case CD_BEFORE -> {
                    if (operation != null && !service.readback(operation).finalPixelsRendered()) {
                        var step = service.advance(operation,
                                clock(row, row.u32(SCHEDULER_CYCLES)), SUB, QUIET, 255);
                        if (step.renderTicket().isPresent()) {
                            service.render(step.renderTicket().orElseThrow(), semanticRam, SUB);
                        }
                    }
                    assertMatches(row, service, operation);
                    beforeRebase = row;
                }
                case CD_AFTER -> {
                    assertNotNull(beforeRebase);
                    assertEquals(beforeRebase.u32(AUX0), row.u32(AUX0));
                    service.rebase(new CdStampClock.Rebase(row.u32(RESET_EPOCH), service.graphicsClock().epoch(),
                            row.u32(CD_EPOCH), row.u32(AUX0)));
                    assertMatches(row, service, operation);
                    beforeRebase = null;
                    rebases++;
                }
                case BUSY_CLEAR -> {
                    assertNotNull(operation);
                    Progress current = service.readback(operation);
                    assertEquals(Phase.RUNNING, current.phase());
                    int preFinalLines = current.rendered().remainingLines() - 1;
                    assertTrue(preFinalLines >= 0);
                    long preFinalClock = (current.graphicsClock().cycles()
                            + (long) preFinalLines * current.cyclesPerLine()) & CdStampClock.UINT32_MASK;
                    var preFinal = service.advance(operation,
                            clock(row, preFinalClock), SUB, QUIET, 255);
                    assertTrue(preFinal.effects().isEmpty());
                    if (preFinal.renderTicket().isPresent()) {
                        service.render(preFinal.renderTicket().orElseThrow(), semanticRam, SUB);
                    }
                    Progress lastLine = service.readback(operation);
                    assertEquals(lastLine.request().heightLines() - 1, lastLine.rendered().completedLines(),
                            "R4's final batch is independently checked to contain exactly one line");
                    long elapsed = row.u32(AUX0);
                    assertTrue(elapsed > 0 && elapsed <= lastLine.cyclesPerLine());
                    long serviceArgument = (lastLine.graphicsClock().cycles() + elapsed) & CdStampClock.UINT32_MASK;
                    assertTrue(serviceArgument == row.u32(SCHEDULER_CYCLES)
                                    || serviceArgument == row.u32(SUB_CYCLES),
                            "gfx_update source argument has an explicit scheduler/sub residual domain");
                    finishing = service.advance(operation,
                            clock(row, serviceArgument), SUB, IRQ_ONLY, 1);
                    assertMatches(row, service, operation);
                    assertFalse(finishing.progress().busy());
                    assertFalse(finishing.progress().finalPixelsRendered());
                    assertEquals(1, finishing.progress().rendered().remainingLines());
                    assertEquals(1, finishing.progress().pendingLines());
                    assertEquals(2, finishing.effects().size());
                    assertEquals(new BusyCleared(finishing.progress()), finishing.effects().get(0));
                    assertEquals(new LevelOneInterruptRequested(), finishing.effects().get(1));
                    busy = row;
                    clears++;
                }
                case PIXELS_DONE -> {
                    assertNotNull(busy);
                    assertNotNull(finishing);
                    assertEquals(busy.sequence() + 1, row.sequence());
                    assertEquals(busy.u32(OPERATION), row.u32(OPERATION));
                    assertEquals(busy.u32(GFX_CYCLES), row.u32(GFX_CYCLES));
                    var rendered = service.render(finishing.renderTicket().orElseThrow(), semanticRam, SUB);
                    assertTrue(rendered.progress().finalPixelsRendered());
                    assertEquals(0, rendered.progress().rendered().remainingLines());
                    assertEquals(1, rendered.effects().size());
                    assertEquals(new FinalPixelsRendered(rendered.progress()), rendered.effects().getFirst());
                    assertEquals(2, row.u32(IRQ_PENDING) & 2, "observed IRQ1 pending after rendering");
                    assertMatches(row, service, operation);
                    busy = null;
                    finishing = null;
                    finals++;
                }
                default -> { /* Other native owners are comparison context, never service inputs. */ }
            }
        }
        assertNull(busy);
        assertNull(beforeRebase);
        assertEquals(1052, starts);
        assertEquals(starts, clears);
        assertEquals(starts, finals);
        assertTrue(starts > 1);
        assertEquals(1800, rebases);
        assertEquals(Set.of("304/NORMAL", "304/UNDERWRITE", "304/OVERWRITE", "256/NORMAL"), profiles);
    }

    private static void assertMatches(Row row, CdStampGraphicsService service, Operation operation) {
        assertEquals(row.u32(RESET_EPOCH), service.graphicsClock().resetEpoch());
        assertEquals(row.u32(CD_EPOCH), service.graphicsClock().epoch());
        assertEquals(row.u32(GFX_CYCLES), service.graphicsClock().cycles());
        if (operation == null) { return; }
        Progress progress = service.readback(operation);
        assertEquals(row.u32(COST), progress.cyclesPerLine());
        assertEquals(row.word(HEIGHT64) & 255, progress.reportedRemainingLines());
        assertEquals((row.word(MODE58) & 0x8000) != 0, progress.busy());
        assertEquals(row.u32(TRACE), progress.rendered().traceByteCursor());
        assertEquals(row.u32(IMAGE), progress.rendered().imageNibbleCursor());
    }

    private static CdStampClock clock(Row row, long cycles) {
        return new CdStampClock(row.u32(RESET_EPOCH), row.u32(CD_EPOCH), cycles);
    }

    private record Row(ByteBuffer bytes, int offset) {
        long u32(int field) { return Integer.toUnsignedLong(bytes.getInt(offset + field * 4)); }
        int word(int field) {
            long value = u32(field);
            assertTrue(value <= 0xffff, "native unsigned register word");
            return (int) value;
        }
        int kind() { return (int) u32(3); }
        long sequence() { return u32(0) | (u32(1) << 32); }
    }

    private static byte[] pinned(Path root, String relative, int maximum, String expected) throws IOException {
        Path file = root.resolve(relative).toRealPath();
        assertTrue(file.startsWith(root), "evidence path must stay in the explicit external root");
        byte[] bytes;
        try (var input = Files.newInputStream(file)) { bytes = input.readNBytes(maximum + 1); }
        assertTrue(bytes.length <= maximum, "bounded evidence payload");
        try {
            assertEquals(expected, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                    "pinned R4 evidence bytes: " + relative);
        } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
        return bytes;
    }
}
