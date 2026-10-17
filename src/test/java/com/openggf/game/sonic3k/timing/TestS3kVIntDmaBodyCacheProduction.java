package com.openggf.game.sonic3k.timing;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomByteReader;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.FreezeStatus;
import com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.Producer;
import com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.QueuedEntry;
import com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.Record;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.PlayableSpriteInternalAccess;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gameplay witnesses for the body DPLC previous-frame byte. Expected entries come from an independent reading of the
 * ROM's player DPLC tables ({@code PLC_Sonic} $148182, {@code PLC_Knuckles} $14BD0A) and the routine's rule.
 * <ul>
 * <li>Knuckles' glide frames are ability frames: {@code Knuckles_Load_PLC} still runs and queues them.</li>
 * <li>An object owning the frame ({@code object_control} bit 1) makes the byte unknown; drains fail closed until the
 * player's own routine re-establishes it.</li>
 * <li>Hurt bypasses the bit-1 test (sub_17D1E).</li>
 * <li>The real Super revert stores $FF, so the next load reloads; restore replays it.</li>
 * <li>A real death restart (Level) clears the byte, so the same non-zero frame queues again; restore replays it.</li>
 * </ul>
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kVIntDmaBodyCacheProduction {
    private static final int PLC_SONIC = 0x148182;
    private static final int PLC_KNUCKLES = 0x14BD0A;

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

    private static HeadlessTestFixture boot(String main) {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, main);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        return HeadlessTestFixture.builder().withZoneAndAct(1, 0).build();
    }

    private static RomByteReader rom() throws IOException {
        return GameServices.level().getObjectManager().getObjectServices().romReader();
    }

    private static List<QueuedEntry> pieces(RomByteReader rom, int table, int frame) {
        int at = table + rom.readU16BE(table + frame * 2);
        int count = rom.readU16BE(at);
        List<QueuedEntry> entries = new ArrayList<>();
        int destination = 0xD000;
        for (int k = 0; k < count; k++) {
            int words = ((rom.readU16BE(at + 2 + 2 * k) >>> 12) + 1) * 0x10;
            entries.add(new QueuedEntry(Producer.OBJECT_DPLC, words, destination));
            destination += words * 2;
        }
        return entries;
    }

    private static List<QueuedEntry> body(List<QueuedEntry> entries) {
        return entries.stream().filter(e -> e.producer() == Producer.OBJECT_DPLC
                && e.vramAddress() >= 0xD000 && e.vramAddress() < 0xD400).toList();
    }

    private static Record step(HeadlessTestFixture fixture, boolean left, boolean right, boolean jump) {
        long before = ledger().lastRecord() == null ? 0 : ledger().lastRecord().ordinal();
        fixture.stepFrame(false, false, left, right, jump);
        Record record = ledger().lastRecord();
        assertEquals(before + 1, record.ordinal(), "one drain per frame");
        return record;
    }

    private static AbstractPlayableSprite landedKnuckles(HeadlessTestFixture fixture) {
        AbstractPlayableSprite knuckles = fixture.sprite();
        // HCZ1 drops the player in from above: wait until Knuckles stands, then a while longer
        int landed = 0;
        for (int settle = 0; landed < 30; settle++) {
            assertTrue(settle < 900, "Knuckles never landed");
            step(fixture, false, false, false);
            landed = knuckles.getAir() ? 0 : landed + 1;
        }
        return knuckles;
    }

    @Test
    void knucklesGlideFramesAreAbilityFramesThatStillLoad() throws IOException {
        HeadlessTestFixture fixture = boot("knuckles");
        RomByteReader rom = rom();
        AbstractPlayableSprite knuckles = landedKnuckles(fixture);
        int previous = knuckles.getMappingFrame() & 0xFF;
        List<QueuedEntry> expectedDrain = List.of();
        int abilityLoads = 0;
        StringBuilder trace = new StringBuilder();
        // jump, release, then hold jump to glide while turning
        for (int frame = 0; frame < 90; frame++) {
            boolean jump = frame < 2 || frame >= 6;
            boolean left = frame >= 30 && frame < 50;
            Record record = step(fixture, left, !left, jump);
            assertEquals(expectedDrain, body(record.drained()), "drained Knuckles entries at frame " + frame + trace);
            int mapping = knuckles.getMappingFrame() & 0xFF;
            expectedDrain = mapping == previous ? List.of() : pieces(rom, PLC_KNUCKLES, mapping);
            previous = mapping;
            assertEquals(expectedDrain, body(ledger().pendingObjectEntries()), "queued at frame " + frame + trace);
            boolean ability = knuckles.isObjectMappingFrameControl()
                    && !PlayableSpriteInternalAccess.objectOwnsMappingFrame(knuckles);
            if (ability && !expectedDrain.isEmpty()) {
                abilityLoads++;
            }
            trace.append(String.format(" %d:%02X%s%s", frame, mapping, ability ? "A" : "",
                    knuckles.getAir() ? "j" : ""));
        }
        assertTrue(abilityLoads >= 2, "glide/slide frames are ability frames that load: " + abilityLoads + trace);
    }

    @Test
    void anObjectOwnedIntervalRecordsNothingAndFailsClosedUntilReestablished() {
        HeadlessTestFixture fixture = boot("knuckles");
        AbstractPlayableSprite knuckles = landedKnuckles(fixture);
        knuckles.setObjectMappingFrameControl(true);
        Record owned = null;
        for (int frame = 0; frame < 4; frame++) {
            knuckles.setMappingFrame(0x10 + frame);
            owned = step(fixture, false, false, false);
            assertEquals(List.of(), body(ledger().pendingObjectEntries()));
        }
        assertEquals(FreezeStatus.PRODUCER_STATE_UNKNOWN, owned.freezeStatus());
        assertEquals(-1, owned.conditionalFreezeMclk());
        assertEquals(-1, owned.queueFreezeMclk());
        knuckles.setObjectMappingFrameControl(false);
        Record released = step(fixture, false, false, false);
        Record afterRelease = step(fixture, false, false, false);
        assertEquals(FreezeStatus.PRODUCER_STATE_UNKNOWN, released.freezeStatus());
        assertEquals(FreezeStatus.PRODUCER_STATE_UNKNOWN, afterRelease.freezeStatus(),
                "the first own compare after the object interval is against an unknown byte");
        Record known = null;
        for (int frame = 0; frame < 3; frame++) {
            known = step(fixture, false, false, false);
        }
        assertEquals(FreezeStatus.CONDITIONAL_ON_VINT_START, known.freezeStatus(), "re-established");
    }

    @Test
    void theHurtRoutineLoadsWithoutTheObjectControlTest() throws IOException {
        HeadlessTestFixture fixture = boot("knuckles");
        RomByteReader rom = rom();
        AbstractPlayableSprite knuckles = landedKnuckles(fixture);
        int before = knuckles.getMappingFrame() & 0xFF;
        knuckles.addRings(5);
        assertTrue(knuckles.applyHurtOrDeath(knuckles.getCentreX() + 8, com.openggf.game.DamageCause.SPIKE, true));
        assertTrue(knuckles.isHurt(), "real hurt routine with knockback");
        knuckles.setObjectMappingFrameControl(true);
        knuckles.setMappingFrame(before == 0x10 ? 0x11 : 0x10);
        step(fixture, false, false, false);
        assertTrue(knuckles.isHurt(), "still in the hurt routine");
        int hurtFrame = knuckles.getMappingFrame() & 0xFF;
        assertNotEquals(before, hurtFrame, "the hurt frame differs from the standing frame");
        assertEquals(pieces(rom, PLC_KNUCKLES, hurtFrame), body(ledger().pendingObjectEntries()),
                "sub_17D1E loads although an object bit is set");
    }

    @Test
    void superRevertStoresFfAndTheNextLoadReloadsAndReplays() throws IOException {
        HeadlessTestFixture fixture = boot("sonic");
        RomByteReader rom = rom();
        AbstractPlayableSprite sonic = fixture.sprite();
        for (int i = 0; i < 7; i++) {
            GameServices.gameState().markEmeraldCollected(i);
        }
        for (int settle = 0; settle < 30; settle++) {
            step(fixture, false, false, false);
        }
        sonic.getSuperStateController().debugActivate();
        for (int frame = 0; frame < 120 && !sonic.isSuperSonic(); frame++) {
            step(fixture, false, false, false);
        }
        assertTrue(sonic.isSuperSonic(), "transformed");
        for (int settle = 0; settle < 90; settle++) {
            step(fixture, false, false, false);
        }
        sonic.addRings(-(sonic.getRingCount() - 1));
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var beforeRevert = registry.capture();
        List<Record> forward = new ArrayList<>();
        boolean reverted = false;
        for (int frame = 0; frame < 70 && !reverted; frame++) {
            boolean wasSuper = sonic.isSuperSonic();
            forward.add(step(fixture, false, false, false));
            reverted = wasSuper && !sonic.isSuperSonic();
        }
        assertTrue(reverted, "reverted on ring exhaustion");
        // The engine runs the revert in tickStatus after this frame's animation pass (ROM: before Animate_Sonic), so
        // the store is visible now and the reload happens on the next pass.
        assertEquals(0xFF, ledger().capture().player1PreviousFrame(), "SonicKnux_SuperHyper stored -1");
        forward.add(step(fixture, false, false, false));
        int reloaded = sonic.getMappingFrame() & 0xFF;
        assertEquals(pieces(rom, PLC_SONIC, reloaded), body(ledger().pendingObjectEntries()),
                "every frame differs from $FF: the normal-form art reloads");
        forward.add(step(fixture, false, false, false));
        assertEquals(pieces(rom, PLC_SONIC, reloaded), body(forward.getLast().drained()));
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(beforeRevert);
            List<Record> replay = new ArrayList<>();
            for (int k = 0; k < forward.size(); k++) {
                replay.add(step(fixture, false, false, false));
            }
            assertEquals(forward, replay, "restore before the revert replays the same records");
        }
    }

    @Test
    void inGameDeathRestartRequeuesTheSameNonZeroFrame() throws IOException {
        HeadlessTestFixture fixture = boot("sonic");
        RomByteReader rom = rom();
        // the level-start frame the fresh build queued for Sonic, held in Player_prev_frame
        int startFrame = -1;
        for (int frame = 0; frame < 120 && startFrame < 0; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            if (!body(ledger().pendingObjectEntries()).isEmpty()) {
                startFrame = GameServices.sprites().getMainPlayable().getMappingFrame() & 0xFF;
            }
        }
        assertTrue(startFrame > 0, "the fresh build queued a non-zero frame");
        fixture.stepFrame(false, false, false, false, false);
        assertEquals(startFrame, ledger().capture().player1PreviousFrame(), "the byte holds the non-zero frame");
        assertEquals(startFrame, GameServices.sprites().getMainPlayable().getMappingFrame() & 0xFF);
        // Restart_level_flag -> Level: the real death reload
        GameServices.level().restartCurrentLevelAfterDeath();
        List<Record> forward = new ArrayList<>();
        Record first = null;
        int frameAfter = -1;
        StringBuilder trace = new StringBuilder(" afterRestart p1=" + ledger().capture().player1PreviousFrame()
                + " known=" + ledger().capture().player1Known());
        // the restarted level's title card holds the player before its first own frame
        for (int frame = 0; frame < 600 && first == null; frame++) {
            Record before = ledger().lastRecord();
            fixture.stepFrame(false, false, false, false, false);
            Record after = ledger().lastRecord();
            if (after != null && after != before) {
                forward.add(after);
                if (!body(after.drained()).isEmpty()) {
                    first = after;
                }
            }
            var main = GameServices.sprites().getMainPlayable();
            if (frame % 20 == 0) {
                trace.append(String.format(" %d:%02X%s%s/%02X%s%s", frame, main.getMappingFrame() & 0xFF,
                        PlayableSpriteInternalAccess.objectOwnsMappingFrame(main) ? "O" : "",
                        main.isObjectMappingFrameControl() ? "M" : "", ledger().capture().player1PreviousFrame(),
                        ledger().capture().player1Known() ? "" : "?", after != null && after != before
                                ? after.freezeStatus().name().substring(0, 3) : "-"));
            }
            if (frameAfter < 0 && !body(ledger().pendingObjectEntries()).isEmpty()) {
                frameAfter = main.getMappingFrame() & 0xFF;
            }
        }
        assertTrue(first != null, "the restarted level queues the player's art" + trace);
        assertEquals(startFrame, frameAfter, "the same non-zero frame before and after the restart");
        assertEquals(pieces(rom, PLC_SONIC, frameAfter), body(first.drained()),
                "Level $610E cleared Player_prev_frame, so the unchanged frame queues again");
    }

    @Test
    void restoreAcrossTheRestartedLevelsFirstQueuedBodyDmaReplays() {
        HeadlessTestFixture fixture = boot("sonic");
        for (int frame = 0; frame < 60; frame++) {
            fixture.stepFrame(false, false, false, false, false);
        }
        GameServices.level().restartCurrentLevelAfterDeath();
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        com.openggf.game.rewind.CompositeSnapshot queued = null;
        for (int frame = 0; frame < 600 && queued == null; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            if (!body(ledger().pendingObjectEntries()).isEmpty()) {
                queued = registry.capture();
            }
        }
        assertTrue(queued != null, "the restarted level queued body DMA");
        List<Record> forward = new ArrayList<>();
        for (int frame = 0; frame < 20; frame++) {
            forward.add(step(fixture, false, false, false));
        }
        assertTrue(!body(forward.getFirst().drained()).isEmpty(), "the first replayed V-int drains it");
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(queued);
            assertTrue(!body(ledger().pendingObjectEntries()).isEmpty(), "restored with body DMA queued");
            List<Record> replay = new ArrayList<>();
            for (int frame = 0; frame < 20; frame++) {
                replay.add(step(fixture, false, false, false));
            }
            assertEquals(forward, replay, "restore after the restart replays the same records");
        }
    }
}
