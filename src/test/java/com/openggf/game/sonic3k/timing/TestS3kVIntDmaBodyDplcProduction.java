package com.openggf.game.sonic3k.timing;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.FixedTransfers;
import com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.Producer;
import com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.QueuedEntry;
import com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.Record;
import com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.VideoRegion;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gameplay witness for {@code Sonic_Load_PLC}: Sonic runs and stops in HCZ1 and the ledger's body entries must equal,
 * frame by frame, what the ROM routine queues for the mapping frame the engine's own animation published. The
 * expected entries come from an independent reading of {@code PLC_Sonic} in the ROM ($148182) and the routine's rule:
 * compare with {@code Player_prev_frame}, then one {@code ($hi + 1) * $10}-word entry per piece, consecutive from
 * ArtTile_Player_1 ($D000). Controls: an unchanged frame queues nothing; drawing the sprite never queues; rewind across
 * queued and drained body DMA replays the same records.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kVIntDmaBodyDplcProduction {
    private static final int PLC_SONIC = 0x148182;

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    private static S3kVIntDmaLedger ledger() {
        return S3kRuntimeArtCoordinator.from(GameServices.level().getObjectManager().getObjectServices())
                .vintDmaLedger();
    }

    private static List<QueuedEntry> body(List<QueuedEntry> entries) {
        return entries.stream().filter(e -> e.producer() == Producer.OBJECT_DPLC
                && e.vramAddress() >= 0xD000 && e.vramAddress() < 0xD400).toList();
    }

    /** PLC_Sonic frame {@code frame}: count word, then pieces {@code cccc ssssssssssss}. */
    private static List<QueuedEntry> romPieces(com.openggf.data.RomByteReader rom, int frame) {
        int at = PLC_SONIC + rom.readU16BE(PLC_SONIC + frame * 2);
        int count = rom.readU16BE(at);
        List<QueuedEntry> entries = new ArrayList<>();
        int destination = 0xD000;
        for (int k = 0; k < count; k++) {
            int piece = rom.readU16BE(at + 2 + 2 * k);
            int words = ((piece >>> 12) + 1) * 0x10;
            entries.add(new QueuedEntry(Producer.OBJECT_DPLC, words, destination));
            destination += words * 2;
        }
        return entries;
    }

    private static boolean running(int frame) {
        return frame < 80;
    }

    @Test
    void sonicLoadPlcEntriesFollowTheEnginesOwnMappingFrames() throws IOException {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        HeadlessTestFixture fixture = HeadlessTestFixture.builder().withZoneAndAct(1, 0).build();
        GameServices.graphics().initHeadless();
        var rom = GameServices.level().getObjectManager().getObjectServices().romReader();
        var sonic = fixture.sprite();
        for (int settle = 0; settle < 60; settle++) {
            fixture.stepFrame(false, false, false, false, false);
        }
        assertEquals(List.of(), body(ledger().pendingObjectEntries()), "standing: nothing queued");
        int previous = sonic.getMappingFrame() & 0xFF;
        List<QueuedEntry> expectedDrain = List.of();
        int changes = 0;
        boolean drawControlled = false;
        com.openggf.game.rewind.CompositeSnapshot queuedCapture = null;
        int captureFrame = -1;
        List<Record> afterCapture = new ArrayList<>();
        for (int frame = 0; frame < 120; frame++) {
            long ordinal = ledger().lastRecord().ordinal();
            fixture.stepFrame(false, false, false, running(frame), false);
            Record record = ledger().lastRecord();
            assertEquals(ordinal + 1, record.ordinal(), "one VInt_8 drain per frame at " + frame);
            assertEquals(FixedTransfers.VINT_8, record.fixed());
            assertEquals(VideoRegion.NTSC, record.region(), "configured REGION default");
            assertEquals(S3kVIntDmaLedger.FreezeStatus.CONDITIONAL_ON_VINT_START, record.freezeStatus());
            assertEquals(26_855 + record.queueFreezeMclk(), record.conditionalFreezeMclk());
            assertEquals(expectedDrain, body(record.drained()), "drained body entries at frame " + frame);
            int mapping = sonic.getMappingFrame() & 0xFF;
            expectedDrain = mapping == previous ? List.of() : romPieces(rom, mapping);
            previous = mapping;
            assertEquals(expectedDrain, body(ledger().pendingObjectEntries()), "queued body entries at frame " + frame);
            if (!expectedDrain.isEmpty()) {
                changes++;
                if (!drawControlled) {
                    // Drawing other frames, twice, reloads the renderer's patterns but queues no ROM DMA.
                    var before = ledger().capture();
                    var renderer = sonic.getSpriteRenderer();
                    for (int draw : new int[] {mapping, (mapping + 1) & 0x7F, mapping}) {
                        com.openggf.level.render.SpritePresentationRenderer.prepare(GameServices.graphics(), 0, 0,
                                () -> renderer.drawFrame(draw, 0, 0, false, false));
                    }
                    assertEquals(before, ledger().capture(), "rendering is not a DMA producer");
                    drawControlled = true;
                }
                if (queuedCapture == null && frame >= 20 && frame < 40) {
                    queuedCapture = TestEnvironment.activeGameplayMode().getRewindRegistry().capture();
                    captureFrame = frame;
                }
            }
            if (queuedCapture != null && frame > captureFrame && frame <= captureFrame + 30) {
                afterCapture.add(record);
            }
        }
        assertTrue(changes >= 10, "running changes Sonic's frame often enough: " + changes);
        assertTrue(drawControlled);
        assertTrue(queuedCapture != null, "a capture with queued body DMA");

        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var end = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(queuedCapture);
            assertTrue(!body(ledger().pendingObjectEntries()).isEmpty(), "restored with body DMA still queued");
            List<Record> replay = new ArrayList<>();
            for (int frame = captureFrame + 1; frame <= captureFrame + 30; frame++) {
                fixture.stepFrame(false, false, false, running(frame), false);
                replay.add(ledger().lastRecord());
            }
            assertEquals(afterCapture, replay, "forward replay drains and queues the same DMA");
        }
        registry.restore(end);
        assertTrue(RewindSnapshotDiff.diffKey(S3kVIntDmaLedger.REWIND_KEY, end.get(S3kVIntDmaLedger.REWIND_KEY),
                registry.capture().get(S3kVIntDmaLedger.REWIND_KEY)).isEmpty());
    }
}
