package com.openggf.game.sonic2.competition.interlace;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.HardwareBoundaryDispatch;
import com.openggf.game.RuntimeArtCoordinator;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.NemesisPlcQueueSnapshot;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.competition.Sonic2CompetitionLevelProfile;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.TileLoadRequest;
import com.openggf.level.resources.PlcParser;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Native commands/physical VRAM are compared to raw REV01 ROM bytes, independently of renderer plans. */
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2InterlaceVramService {
    @Test void theSameProductionServiceOwnsPreparationAndActualThreeSixPatternProgress() throws Exception {
        var rom = TestEnvironment.currentRom();
        var module = new Sonic2GameModule(); module.createGame(rom);
        var plc = module.getGameService(Sonic2PlcService.class);
        var service = new Sonic2InterlaceVramService(rom, plc);
        assertSame(plc, service.plcService());
        assertSame(service.plcLifecycle(), service.plcLifecycle());
        plc.append(Sonic2Constants.PLC_STD1);
        var submitted = plc.capture();
        service.resetForNativeEntry();
        assertEquals(submitted, plc.capture(), "native VRAM entry reset must not clear production PLC");
        service.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertArrayEquals(new byte[65536], service.current().bytes(), "unprepared head cannot write");
        for (var phase : PlcLifecyclePhase.values())
            assertEquals(plc.hasPreparationBoundary(phase), service.hasPreparationBoundary(phase));
        service.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
        var active = plc.capture().activeEntry(); assertNotNull(active);
        byte[] decoded = decode(rom, active);
        assertTrue(active.totalPatterns() > 9);
        var pristine = service.current();
        service.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        byte[] expected = new byte[65536];
        copy(expected, active.destinationTile() * 32, decoded, 0, 96);
        assertArrayEquals(expected, service.current().bytes());
        assertEquals(active.totalPatterns() - 3, plc.capture().activeEntry().remainingPatterns());
        assertArrayEquals(new byte[65536], pristine.bytes(), "retained VRAM image is immutable");
        var held = service.capture();
        service.prepareAfterLoop(PlcLifecyclePhase.LAG);
        service.serviceVBlank(PlcLifecyclePhase.LAG);
        assertEquals(held, service.capture());
        service.serviceVBlank(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        copy(expected, active.destinationTile() * 32, decoded, 0, 288);
        assertArrayEquals(expected, service.current().bytes());
        assertEquals(active.totalPatterns() - 9, plc.capture().activeEntry().remainingPatterns());
    }

    @Test void rawTwoHumanBodyAndTailCommandsStayPendingUntilTheActualVblankClaim() throws Exception {
        var h = harness(); var service = h.service();
        var irrelevantViewPlan = new SpriteDplcFrame(List.of(new TileLoadRequest(0xFFF, 1)));
        service.playerLoadPlc(h.sonic(), 1, irrelevantViewPlan);
        service.playerLoadPlc(h.tails(), 1, null);
        service.tailsTailLoadPlc(9, null);
        assertEquals(List.of(
                new Sonic2InterlaceVramService.DmaTransfer(0x50000, 0xF000, 64),
                new Sonic2InterlaceVramService.DmaTransfer(0x50080, 0xF080, 64),
                new Sonic2InterlaceVramService.DmaTransfer(0x50100, 0xF100, 96),
                new Sonic2InterlaceVramService.DmaTransfer(0x501C0, 0xF1C0, 96),
                new Sonic2InterlaceVramService.DmaTransfer(0x64320, 0xF400, 96),
                new Sonic2InterlaceVramService.DmaTransfer(0x643E0, 0xF4C0, 96),
                new Sonic2InterlaceVramService.DmaTransfer(0x64C20, 0xF600, 128)), service.capture().pending());
        assertArrayEquals(new byte[65536], service.current().bytes());
        var timing = new HardwareTimingService();
        for (var boundary : HardwareServiceBoundary.values())
            HardwareBoundaryDispatch.serviceBoundary(boundary, service, timing, null);
        assertArrayEquals(new byte[65536], service.current().bytes(), "timing callbacks cannot invent DMA service");
        service.serviceVBlank(PlcLifecyclePhase.LAG);
        service.serviceVBlank(PlcLifecyclePhase.PALETTE_FADE);
        service.serviceVBlank(PlcLifecyclePhase.SPECIAL_STAGE_PAUSE);
        assertEquals(7, service.capture().pending().size());
        var lifecycle = new PlcFrameLifecycleCoordinator(service.plcLifecycle());
        var frame = lifecycle.latchBeforeFadeUpdate();
        assertTrue(frame.claim(PlcLifecyclePhase.ORDINARY_LEVEL));
        byte[] expected = new byte[65536];
        applyFrame(expected, h.rom(), 0x714E0, 0x50000, 0xF000, 1);
        applyFrame(expected, h.rom(), 0x7446C, 0x64320, 0xF400, 1);
        applyFrame(expected, h.rom(), 0x7446C, 0x64320, 0xF600, 9);
        assertArrayEquals(expected, service.current().bytes());
        assertTrue(service.capture().pending().isEmpty());
        assertFalse(frame.claim(PlcLifecyclePhase.ORDINARY_LEVEL), "one physical VBlank has one claim");
        frame.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
        frame.finish();
        assertArrayEquals(expected, service.current().bytes());
    }

    @Test void laterPlcPartialWordsWinDmaOverlapAndKeepTheOtherHalfOfAnInterlaceCell() throws Exception {
        var h = harness(); var service = h.service();
        var entry = stdHead(h.rom(), 0xF600 / 32);
        h.plc().restore(new NemesisPlcQueueSnapshot(entry, List.of()));
        service.playerLoadPlc(h.tails(), 0x39, null); // real 28-tile body overlaps tail destination
        service.tailsTailLoadPlc(9, null);
        byte[] expected = new byte[65536];
        applyFrame(expected, h.rom(), 0x7446C, 0x64320, 0xF400, 0x39);
        applyFrame(expected, h.rom(), 0x7446C, 0x64320, 0xF600, 9);
        byte[] oldOtherHalf = Arrays.copyOfRange(expected, 0xF660, 0xF680);
        byte[] plc = decode(h.rom(), entry);
        copy(expected, 0xF600, plc, 0, 96);
        service.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertArrayEquals(expected, service.current().bytes());
        assertArrayEquals(oldOtherHalf, Arrays.copyOfRange(service.current().bytes(), 0xF660, 0xF680),
                "three ordinary patterns update only one half of the second 64-byte cell");
        assertFalse(Arrays.equals(Arrays.copyOfRange(expected, 0xF600, 0xF620),
                h.rom().readBytes(0x64C20, 32)), "overlap must discriminate DMA-first from PLC-first");
    }

    @Test void baseZonePatchPreservesTheRetiredHighPlcAndNeverResetsQueueOrDplcState() throws Exception {
        var h = harness(); var service = h.service();
        h.plc().append(Sonic2Constants.PLC_STD1);
        int iterations = 0;
        while (h.plc().isBusy()) {
            assertTrue(++iterations < 1000);
            service.prepareAfterLoop(PlcLifecyclePhase.LEVEL_TITLE_CARD);
            service.serviceVBlank(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        }
        byte[] retired = service.current().bytes();
        assertTrue(Arrays.stream(new int[]{0xD000, 0xD800, 0xE000, 0xF000}).anyMatch(start ->
                nonzero(retired, start, Math.min(65536, start + 0x800)) > 0));
        service.playerLoadPlc(h.sonic(), 1, null);
        var before = service.capture();
        var profile = Sonic2CompetitionLevelProfile.load(h.rom(), 0, 0);
        byte[] zone = profile.decodeBaseTerrain(h.rom()).patternBytes();
        service.loadZoneArt(profile);
        assertArrayEquals(zone, Arrays.copyOf(service.current().bytes(), zone.length));
        assertArrayEquals(Arrays.copyOfRange(retired, zone.length, 65536),
                Arrays.copyOfRange(service.current().bytes(), zone.length, 65536));
        assertEquals(before.pending(), service.capture().pending());
        assertEquals(before.plc(), service.capture().plc());
        assertEquals(before.sonicLast(), service.capture().sonicLast());
    }

    @Test void lastBytesGateFrameIdentityIncludingEmptyAliasedAndDroppedCommands() throws Exception {
        var h = harness(); var service = h.service();
        assertEquals(0, service.capture().sonicLast());
        service.playerLoadPlc(h.sonic(), 0, null);
        service.playerLoadPlc(new TestablePlayableSprite("sonic", (short) 0, (short) 0), 1, null);
        assertTrue(service.capture().pending().isEmpty(), "cold frame zero and ghost do not submit");
        service.playerLoadPlc(h.sonic(), 1, null);
        var once = service.capture();
        service.playerLoadPlc(h.sonic(), 257, null); // byte-width comparison, not an unbounded Java frame
        h.sonic().setMappingFrame(1);
        service.playerLoadPlcOwnedByObject(h.sonic());
        assertEquals(once, service.capture());
        service.serviceDmaQueueVBlank();
        var previousPixels = service.current();
        service.playerLoadPlc(h.sonic(), 0, null);
        assertEquals(0, service.capture().sonicLast());
        assertEquals(previousPixels, service.current());
        service.tailsTailLoadPlc(0x49, null); service.tailsTailLoadPlc(0x81, null);
        assertEquals(2, service.capture().pending().size());
        assertEquals(service.capture().pending().getFirst(), service.capture().pending().getLast(),
                "different raw frame bytes submit again even when the ROM payload aliases");
        service.resetForNativeEntry();
        for (int i = 0; i < 18; i++) assertTrue(service.queueDmaTransfer(0x50000, 0x1000, 1));
        assertFalse(service.queueDmaTransfer(0x50000, 0x1000, 1));
        service.playerLoadPlc(h.sonic(), 1, null);
        assertEquals(1, service.capture().sonicLast());
        assertEquals(18, service.capture().pending().size());
        service.serviceDmaQueueVBlank();
        service.playerLoadPlc(h.sonic(), 1, null);
        assertTrue(service.capture().pending().isEmpty(), "a full-buffer drop still latches the frame");
        service.invalidatePlayerDplcs();
        assertEquals(255, service.capture().sonicLast()); assertEquals(255, service.capture().tailsLast());
        assertEquals(255, service.capture().tailLast());
        service.playerLoadPlc(h.sonic(), 1, null);
        assertEquals(4, service.capture().pending().size());
    }

    @Test void s2OwnedMappingNoticeStillUsesTheActualStoredNativeBodyFrame() {
        var h = harness();
        h.sonic().setMappingFrame(1); h.tails().setMappingFrame(1);
        h.service().playerLoadPlcOwnedByObject(h.sonic());
        h.service().playerLoadPlcOwnedByObject(h.tails());
        assertEquals(1, h.service().capture().sonicLast());
        assertEquals(1, h.service().capture().tailsLast());
        assertEquals(6, h.service().capture().pending().size());
        h.sonic().setMappingFrame(0); // empty cue latches without canceling already admitted work
        h.service().playerLoadPlcOwnedByObject(h.sonic());
        assertEquals(0, h.service().capture().sonicLast());
        assertEquals(6, h.service().capture().pending().size());
    }

    @Test void actualDmaWordsWrapAtTheirSourceWindowAndVramBoundaryWithoutBankClipping() throws Exception {
        var h = harness(); var service = h.service();
        service.queueDmaTransfer(0x5FFF0, 0xFFF0, 32);
        service.serviceDmaQueueVBlank();
        byte[] expected = new byte[65536];
        copy(expected, 0xFFF0, h.rom().readBytes(0x5FFF0, 16), 0, 16);
        copy(expected, 0, h.rom().readBytes(0x40000, 48), 0, 48);
        assertArrayEquals(expected, service.current().bytes());
        service.queueDmaTransfer(0x40000, 0, 0);
        service.serviceDmaQueueVBlank();
        assertArrayEquals(h.rom().readBytes(0x50000, 65536), service.current().bytes(),
                "zero VDP length transfers 65536 words, wrapping VRAM twice");
    }

    @Test void actualLevelSelectMenuVintDrainsDmaWhileSpecialStagePauseDoesNot() throws Exception {
        var h = harness();
        h.service().queueDmaTransfer(0x50000, 0x1000, 1);
        h.service().serviceVBlank(PlcLifecyclePhase.SPECIAL_STAGE_PAUSE);
        assertEquals(1, h.service().capture().pending().size());
        // LevelSelect_Main / LevelSelect2P_Main select VintID_Menu, not Vint_Title.
        h.service().serviceVBlank(PlcLifecyclePhase.LEVEL_SELECT);
        assertArrayEquals(h.rom().readBytes(0x50000, 2), Arrays.copyOfRange(h.service().current().bytes(), 0x1000, 0x1002));
        assertTrue(h.service().capture().pending().isEmpty());
    }

    @Test void compositeDestructiveRestoreReplaysQueueBytesBindingsAndPendingDplcsWithoutResubmission() throws Exception {
        var h = harness(); var service = h.service();
        h.plc().append(Sonic2Constants.PLC_STD1);
        service.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
        service.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        service.playerLoadPlc(h.sonic(), 1, null); service.playerLoadPlc(h.tails(), 0x39, null);
        service.tailsTailLoadPlc(9, null);
        var registry = new RewindRegistry(); service.registerRewindAdapters(registry);
        var saved = registry.capture();
        forward(service, h.sonic()); var expected = service.capture();
        service.resetForNativeEntry(); h.plc().resetForMissingSnapshot();
        service.bindPlayers(new TestablePlayableSprite("sonic", (short) 0, (short) 0),
                new TestablePlayableSprite("tails", (short) 0, (short) 0));
        assertNotEquals(expected, service.capture());
        registry.restore(saved);
        assertEquals(saved.get(service.key()), service.capture(), "restore neither publishes nor resubmits");
        forward(service, h.sonic());
        assertEquals(expected, service.capture());
        service.deregisterRewindAdapters(registry);
        assertFalse(registry.capture().containsKey(service.key()));
    }

    @Test void unreadableRomAndInvalidRestoreLeaveThePhysicalAndProductionQueueTransactionIntact() throws Exception {
        var h = harness(); var service = h.service();
        var invalid = new NemesisPlcQueueSnapshot.Entry((int) h.rom().getSize() - 1, 0, 1, 1);
        h.plc().restore(new NemesisPlcQueueSnapshot(invalid, List.of()));
        service.queueDmaTransfer(0x50000, 0x1000, 16);
        var before = service.capture();
        service.serviceVBlank(PlcLifecyclePhase.LAG); // actual service does not even decode this head
        assertEquals(before, service.capture());
        assertThrows(RuntimeException.class, () -> service.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL));
        assertEquals(before, service.capture());
        var invalidRestore = new Sonic2InterlaceVramService.Snapshot(
                new Sonic2InterlaceVram(new byte[65536]), new NemesisPlcQueueSnapshot(
                new NemesisPlcQueueSnapshot.Entry(0, 0, 1, 2), List.of()), List.of(), null, null, 0, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> service.restore(invalidRestore));
        assertEquals(before, service.capture());
        h.plc().resetForMissingSnapshot(); service.resetForNativeEntry();
        service.queueDmaTransfer(0x400000, 0, 16);
        before = service.capture();
        assertThrows(IndexOutOfBoundsException.class, service::serviceDmaQueueVBlank);
        assertEquals(before, service.capture());
        try (var truncated = Rom.fromReader(RomByteReader.fromBytes(new byte[32]), "unreadable DPLC test")) {
            var bad = new Sonic2InterlaceVramService(truncated, new Sonic2PlcService(truncated));
            bad.bindPlayers(h.sonic(), h.tails()); var initial = bad.capture();
            assertThrows(IndexOutOfBoundsException.class, () -> bad.playerLoadPlc(h.sonic(), 1, null));
            assertEquals(initial, bad.capture());
        }
    }

    @Test void optionalCoordinatorKeepsItsOwnBoundaryAndRewindAdaptersWithoutPublishingNewJobs() throws Exception {
        var rom = TestEnvironment.currentRom(); var delegate = new CountingDelegate();
        var service = new Sonic2InterlaceVramService(rom, new Sonic2PlcService(rom), delegate);
        var registry = new RewindRegistry(); service.registerRewindAdapters(registry);
        var timing = new HardwareTimingService();
        for (var boundary : HardwareServiceBoundary.values())
            HardwareBoundaryDispatch.serviceBoundary(boundary, service, timing, null);
        assertEquals(6, delegate.count);
        var saved = registry.capture();
        service.afterTimingService(HardwareServiceBoundary.VINT_SERVICE);
        registry.restore(saved);
        assertEquals(6, delegate.count);
        service.deregisterRewindAdapters(registry);
        assertFalse(registry.capture().containsKey(delegate.key()));
    }

    @Test void lifecycleAndRuntimeRolesExposeEachQueueDiagnosticExactlyOnce() throws Exception {
        var h = harness();
        h.plc().append(Sonic2Constants.PLC_STD1);
        var diagnostics = new java.util.ArrayList<>(h.service().plcLifecycle().captureQueueDiagnostics());
        diagnostics.addAll(h.service().captureQueueDiagnostics());
        assertEquals(h.plc().captureQueueDiagnostics(), diagnostics);
        assertEquals(1, diagnostics.size(), "the production session rejects duplicate queue diagnostic kinds");
    }

    @Test void replacingAnActKeepsEachLifecycleBoundToItsProductionSourceAndRestoresTheSelectedOwner() throws Exception {
        var rom = TestEnvironment.currentRom();
        var module = new Sonic2GameModule(); module.createGame(rom);
        var firstPlc = module.getGameService(Sonic2PlcService.class);
        var first = new Sonic2InterlaceVramService(rom, firstPlc);
        var firstLifecycle = first.plcLifecycle();
        var sonic = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        var tails = new TestablePlayableSprite("tails", (short) 0, (short) 0);
        first.bindPlayers(sonic, tails);
        firstPlc.append(Sonic2Constants.PLC_STD1);
        firstLifecycle.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
        firstLifecycle.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        first.playerLoadPlc(sonic, 1, null);
        var registry = new RewindRegistry(); first.registerRewindAdapters(registry);
        assertFalse(registry.capture().containsKey(Sonic2PlcService.REWIND_KEY),
                "the composite owner already captures the production FIFO");
        var firstSaved = registry.capture(); var firstState = first.capture();

        // A new act binding must capture the new module service, never a mutable module lookup.
        module.createGame(rom);
        var secondPlc = module.getGameService(Sonic2PlcService.class);
        assertNotSame(firstPlc, secondPlc);
        var delegate = new CountingDelegate();
        var second = new Sonic2InterlaceVramService(rom, secondPlc, delegate);
        var secondLifecycle = second.plcLifecycle();
        var secondEntry = PlcParser.parse(rom, Sonic2Constants.ART_LOAD_CUES_ADDR,
                Sonic2Constants.PLC_STD1).entries().get(1);
        int patterns = rom.read16BitAddr(secondEntry.romAddr()) & 0x7FFF;
        var head = new NemesisPlcQueueSnapshot.Entry(secondEntry.romAddr(), 0xF000 / 32, patterns, patterns);
        secondPlc.restore(new NemesisPlcQueueSnapshot(head, List.of()));
        second.queueDmaTransfer(0x64320, 0x1000, 16);
        first.deregisterRewindAdapters(registry); second.registerRewindAdapters(registry);
        var secondSaved = registry.capture();
        secondLifecycle.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        byte[] expected = new byte[65536];
        copy(expected, 0x1000, rom.readBytes(0x64320, 32), 0, 32);
        byte[] decoded = decode(rom, head);
        copy(expected, 0xF000, decoded, 0, Math.min(3, patterns) * 32);
        assertArrayEquals(expected, second.current().bytes());
        assertEquals(firstState, first.capture(), "new-act VInt must not clock the former owner");
        assertSame(firstPlc, first.plcService()); assertSame(firstLifecycle, first.plcLifecycle());
        assertSame(secondPlc, second.plcService()); assertSame(secondLifecycle, second.plcLifecycle());
        assertEquals(firstPlc.captureQueueDiagnostics(), firstLifecycle.captureQueueDiagnostics());
        assertEquals(secondPlc.captureQueueDiagnostics(), secondLifecycle.captureQueueDiagnostics());
        // Diagnostic v1 deliberately hides active source/destination; the real
        // FIFO snapshot owns identity and waiting descriptors expose fingerprints.
        var diagnostic = firstLifecycle.captureQueueDiagnostics().getFirst();
        assertEquals(-1, diagnostic.activeSource());
        assertEquals(firstState.plc().activeEntry().remainingPatterns(), diagnostic.activeRemainingWork());
        assertEquals(firstState.plc().queuedEntries().stream().map(entry ->
                com.openggf.game.resources.QueueDiagnosticSnapshot.fingerprint(diagnostic.kind(),
                        entry.sourceAddress(), entry.destinationTile(), entry.totalPatterns())).toList(),
                diagnostic.queuedFingerprints());
        assertNotEquals(firstState.plc().activeEntry().sourceAddress(), head.sourceAddress());
        assertEquals(delegate.captureQueueDiagnostics(), second.captureQueueDiagnostics());
        assertEquals(1, secondLifecycle.captureQueueDiagnostics().size());
        assertEquals(1, second.captureQueueDiagnostics().size());

        var secondForward = second.capture();
        second.resetForMissingSnapshot(); registry.restore(secondSaved);
        secondLifecycle.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertEquals(secondForward, second.capture(), "selected new act restores and replays its own ROM source");
        second.deregisterRewindAdapters(registry); first.registerRewindAdapters(registry);
        registry.restore(firstSaved);
        firstLifecycle.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        var firstForward = first.capture();
        first.resetForMissingSnapshot(); registry.restore(firstSaved);
        firstLifecycle.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertEquals(firstForward, first.capture(), "reselect the retained act owner before restoring its snapshot");
        assertEquals(secondForward, second.capture(), "restoring the old act leaves the replacement untouched");
    }

    @Test void missingCompositeSnapshotClearsFuturePlcAndPendingDmaUnlikeNativeEntryReset() throws Exception {
        var h = harness(); var registry = new RewindRegistry();
        h.service().registerRewindAdapters(registry);
        h.plc().append(Sonic2Constants.PLC_STD1);
        h.service().prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
        h.service().playerLoadPlc(h.sonic(), 1, null);
        assertTrue(h.plc().isBusy());
        registry.restore(new CompositeSnapshot(Map.of()));
        assertFalse(h.plc().isBusy());
        assertTrue(h.service().capture().pending().isEmpty());
        assertEquals(0, h.service().capture().sonicLast());
        h.service().prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
        h.service().serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertArrayEquals(new byte[65536], h.service().current().bytes());
    }

    private static void forward(Sonic2InterlaceVramService service, TestablePlayableSprite sonic) {
        service.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        service.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
        service.playerLoadPlc(sonic, 2, null); service.tailsTailLoadPlc(0, null);
        service.serviceVBlank(PlcLifecyclePhase.LEVEL_TITLE_CARD);
    }

    private static Harness harness() {
        var rom = TestEnvironment.currentRom(); var plc = new Sonic2PlcService(rom);
        var service = new Sonic2InterlaceVramService(rom, plc);
        var sonic = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        var tails = new TestablePlayableSprite("tails", (short) 0, (short) 0);
        service.bindPlayers(sonic, tails); return new Harness(rom, plc, service, sonic, tails);
    }

    private record Harness(Rom rom, Sonic2PlcService plc, Sonic2InterlaceVramService service,
                           TestablePlayableSprite sonic, TestablePlayableSprite tails) { }

    private static NemesisPlcQueueSnapshot.Entry stdHead(Rom rom, int destination) throws Exception {
        var raw = PlcParser.parse(rom, Sonic2Constants.ART_LOAD_CUES_ADDR, Sonic2Constants.PLC_STD1).entries().getFirst();
        int count = rom.read16BitAddr(raw.romAddr()) & 0x7FFF;
        return new NemesisPlcQueueSnapshot.Entry(raw.romAddr(), destination, count, count);
    }

    private static byte[] decode(Rom rom, NemesisPlcQueueSnapshot.Entry entry) throws Exception {
        return PlcParser.decompressEntryRaw(rom, new PlcParser.PlcEntry(entry.sourceAddress(), entry.destinationTile()));
    }

    /** Independent ROM-word oracle; never consult the service's pending commands or an engine DPLC plan. */
    static void applyFrame(byte[] expected, Rom rom, int table, int art, int destination, int frame) throws Exception {
        int address = table + (short) rom.read16BitAddr(table + frame * 2);
        int count = rom.read16BitAddr(address);
        for (int i = 0; i < count; i++) {
            int cue = rom.read16BitAddr(address + 2 + i * 2);
            int length = (cue / 4096 + 1) * 32;
            copy(expected, destination, rom.readBytes(art + cue % 4096 * 32, length), 0, length);
            destination = (destination + length) % 65536;
        }
    }

    private static void copy(byte[] destination, int offset, byte[] source, int from, int length) {
        for (int i = 0; i < length; i++) destination[(offset + i) & 65535] = source[from + i];
    }
    private static int nonzero(byte[] bytes, int first, int end) {
        int count = 0; for (int i = first; i < end; i++) if (bytes[i] != 0) count++; return count;
    }
    private static final class CountingDelegate implements RuntimeArtCoordinator, RewindSnapshottable<Integer> {
        int count;
        @Override public void beforeTimingService(HardwareServiceBoundary boundary) { count++; }
        @Override public void afterTimingService(HardwareServiceBoundary boundary) { count++; }
        @Override public String key() { return "test-vram-delegate"; }
        @Override public Integer capture() { return count; }
        @Override public void restore(Integer snapshot) { count = snapshot; }
        @Override public void resetForMissingSnapshot() { count = 0; }
        @Override public List<com.openggf.game.resources.QueueDiagnosticSnapshot> captureQueueDiagnostics() {
            return List.of(com.openggf.game.resources.QueueDiagnosticSnapshot.idle(
                    com.openggf.game.resources.QueueDiagnosticSnapshot.Kind.S3K_KOS_DIRECT, List.of()));
        }
        @Override public void registerRewindAdapters(RewindRegistry registry) { registry.register(this); }
        @Override public void deregisterRewindAdapters(RewindRegistry registry) { registry.deregister(key()); }
    }
}
