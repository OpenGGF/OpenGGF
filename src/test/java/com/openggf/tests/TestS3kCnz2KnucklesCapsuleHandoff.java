package com.openggf.tests;

import com.openggf.LevelFrameResult;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kCNZEvents;
import com.openggf.game.sonic3k.objects.CnzEggCapsuleInstance;
import com.openggf.game.sonic3k.objects.CnzTeleporterInstance;
import com.openggf.game.sonic3k.objects.S3kIncLevelEndXGradualInstance;
import com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CNZ2 Knuckles' production capsule/results -> screen-event handoff.
 *
 * <p>Origin: bounded CNZ2 gameplay completion work, 2026-10-20. This declares
 * one public above-button movement inlet after ordinary event admission. It
 * does not seed a capsule/results routine, completion flag, SST slot or timer,
 * and stops before the separately incomplete teleporter/ICZ route.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCnz2KnucklesCapsuleHandoff {
    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var config = GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, 320);
        config.setSessionOverride(SonicConfiguration.SCREEN_HEIGHT_PIXELS, 224);
    }

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    void ordinaryButtonAndResultsReleaseP1IntoTheGraduallyOpenedLane() throws Exception {
        var entry = pressActualAdmittedCapsule();
        var fixture = entry.fixture();
        var player = fixture.sprite();
        var objects = GameServices.level().getObjectManager();
        var registry = fixture.gameplayMode().getRewindRegistry();
        var beforeCompletion = registry.capture();
        int exitEntries = runToResultsExit(entry);
        assertHandoff(fixture);
        var afterHandoff = registry.capture();

        // Obj_IncLevEndXGradual ($84A48): accumulator += $4000 on each own
        // entry, add its high word, clamp unsigned at the stored $49A0 target.
        for (int entryIndex = 1; entryIndex <= 8; entryIndex++) {
            idle(fixture);
            int expected = 0x48E0;
            for (int i = 1; i <= entryIndex; i++) expected += i / 4;
            assertEquals(expected, fixture.camera().getMaxX() & 0xFFFF,
                    "only the worker, not generic camera easing or the teleporter, moves max X");
            assertEquals(1, objects.activeObjectsOfType(S3kIncLevelEndXGradualInstance.class).size());
        }
        var duringOpening = registry.capture();
        for (int entryIndex = 9; entryIndex <= 41; entryIndex++) idle(fixture);
        assertEquals(0x49A0, fixture.camera().getMaxX() & 0xFFFF);
        assertTrue(objects.activeObjectsOfType(S3kIncLevelEndXGradualInstance.class).isEmpty());
        var afterOpening = registry.capture();
        for (int replay = 0; replay < 2; replay++) {
            registry.restore(beforeCompletion);
            assertCompleteWorld(beforeCompletion, registry.capture(), "before completion restore " + replay);
            for (int i = 0; i < exitEntries; i++) idle(fixture);
            assertCompleteWorld(afterHandoff, registry.capture(), "normal results replay " + replay);
            for (int i = 0; i < 8; i++) idle(fixture);
            assertCompleteWorld(duringOpening, registry.capture(), "opening prefix replay " + replay);
            registry.restore(duringOpening);
            assertCompleteWorld(duringOpening, registry.capture(), "during opening restore " + replay);
            for (int i = 8; i < 41; i++) idle(fixture);
            assertCompleteWorld(afterOpening, registry.capture(), "opening completion replay " + replay);
        }
        int beforeWalk = player.getCentreX() & 0xFFFF;
        for (int i = 0; i < 16; i++) {
            assertEquals(LevelFrameResult.GAMEPLAY_FRAME,
                    fixture.runner().stepFrame(false, false, false, true, false));
        }
        assertTrue((player.getCentreX() & 0xFFFF) > beforeWalk,
                "a normal input-driven player pass must move after the real control release");
        assertTrue((player.getCentreX() & 0xFFFF) < 0x4A38,
                "the independent teleporter arm/beam feature is outside this test");
        assertTrue(objects.activeObjectsOfType(
                com.openggf.game.sonic3k.objects.CnzTeleporterBeamInstance.class).isEmpty());
    }

    @Test
    void genuineCompletionRetriesAfterPublicPoolExhaustionAndRestoresP1RatherThanFocusedP2() throws Exception {
        GameServices.configuration().setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        var entry = pressActualAdmittedCapsule();
        var fixture = entry.fixture();
        var player = fixture.sprite();
        var level = GameServices.level();
        var objects = level.getObjectManager();
        var events = cnzEvents();
        var registry = fixture.gameplayMode().getRewindRegistry();
        assertEquals(1, GameServices.sprites().getRegisteredSidekicks().size());
        var p2 = GameServices.sprites().getRegisteredSidekicks().getFirst();
        var beforeCompletion = registry.capture();
        int exitEntries = runToResultsExit(entry);
        assertHandoff(fixture);

        registry.restore(beforeCompletion);
        for (int i = 1; i < exitEntries; i++) idle(fixture);
        var results = objects.activeObjectsOfType(S3kResultsScreenObjectInstance.class);
        assertEquals(1, results.size(), "the actual results owner is still live before its final pass");
        // DECLARED PUBLIC COMPONENT INLET: one ordinary object-manager pass
        // produces the original results flag before the separately invoked
        // ScreenEvent consumer. No result phase, clock, slot or flag is seeded.
        objects.update(fixture.camera().getX() & 0xFFFF, player,
                GameServices.sprites().getRegisteredSidekicks(), level.getFrameCounter());
        assertTrue(results.getFirst().isDestroyed(),
                "prerequisite: this original object pass must execute the real results exit");
        assertTrue(GameServices.gameState().isEndOfLevelFlag());
        assertEquals(Sonic3kCNZEvents.FG_ACT2_KNUCKLES_ROUTE, events.getForegroundRoutine());
        assertTrue(player.isObjectControlled());

        // Exhaust capacity through real production admission. No reserved-slot
        // helper, assigned slot numbers, fake object or completion writer.
        var admitted = new ArrayList<CnzEggCapsuleInstance>();
        for (int i = 0; i <= objects.getObjectSlotCapacity(); i++) {
            var object = objects.createDynamicObject(() -> new CnzEggCapsuleInstance(
                    new ObjectSpawn(0, 0, Sonic3kObjectIds.EGG_CAPSULE, 0, 0, false, 0)));
            if (object == null) break;
            admitted.add(object);
        }
        assertFalse(admitted.isEmpty());
        assertEquals(objects.getObjectSlotCapacity(), objects.getActiveObjectSlotCount());
        var p1Before = projection(player);
        var p2Before = projection(p2);
        int storedBefore = events.getCameraStoredMaxXPos() & 0xFFFF;
        int targetBefore = fixture.camera().getMaxXTarget() & 0xFFFF;
        var audioBefore = GameServices.audio().captureLogicalSnapshot();
        fixture.camera().setFocusedSprite(p2);
        events.update(1, level.getFrameCounter());
        assertAll("loc_521D8 allocation refusal leaves the completed handoff pending",
                () -> assertEquals(Sonic3kCNZEvents.FG_ACT2_KNUCKLES_ROUTE, events.getForegroundRoutine()),
                () -> assertEquals(storedBefore, events.getCameraStoredMaxXPos() & 0xFFFF),
                () -> assertEquals(targetBefore, fixture.camera().getMaxXTarget() & 0xFFFF),
                () -> assertEquals(p1Before, projection(player)),
                () -> assertEquals(p2Before, projection(p2)),
                () -> assertEquals(audioBefore.commandEntryCount(),
                        GameServices.audio().captureLogicalSnapshot().commandEntryCount()),
                () -> assertTrue(GameServices.gameState().isEndOfLevelFlag()),
                () -> assertTrue(objects.activeObjectsOfType(S3kIncLevelEndXGradualInstance.class).isEmpty()));

        var released = admitted.removeLast();
        int freeSlot = released.getSlotIndex();
        objects.removeDynamicObject(released);
        events.update(1, level.getFrameCounter());
        assertHandoff(fixture);
        assertSame(p2, fixture.camera().getFocusedSprite());
        assertEquals(p2Before, projection(p2), "Restore_PlayerControl addresses P1 alone");
        assertEquals(p1Before.xSpeed(), player.getXSpeed());
        assertEquals(p1Before.ySpeed(), player.getYSpeed());
        assertEquals(p1Before.gSpeed(), player.getGSpeed());
        assertEquals(p1Before.rolling(), player.getRolling());
        var worker = objects.activeObjectsOfType(S3kIncLevelEndXGradualInstance.class).getFirst();
        assertEquals(freeSlot, worker.getSlotIndex(), "AllocateObject uses the real newly freed lowest slot");
        int audioAfter = GameServices.audio().captureLogicalSnapshot().commandEntryCount();
        events.update(1, level.getFrameCounter());
        assertEquals(audioAfter, GameServices.audio().captureLogicalSnapshot().commandEntryCount());
        assertSame(worker, objects.activeObjectsOfType(S3kIncLevelEndXGradualInstance.class).getFirst(),
                "FG8 does not reallocate or restore music again while the flag remains set");
    }

    private ResultsEntry pressActualAdmittedCapsule() throws Exception {
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .startPosition((short) 0x48C0, (short) 0x0B40)
                .startPositionIsCentre().build();
        var player = assertInstanceOf(Knuckles.class, fixture.sprite());
        var level = GameServices.level();
        var objects = level.getObjectManager();
        var events = assertInstanceOf(Sonic3kLevelEventManager.class,
                GameServices.module().getLevelEventProvider()).getCnzEvents();
        assertNotNull(events);
        assertEquals(Sonic3kCNZEvents.FG_ACT2_ENTRY, events.getForegroundRoutine());
        idle(fixture);
        assertEquals(Sonic3kCNZEvents.FG_ACT2_KNUCKLES_ROUTE, events.getForegroundRoutine());
        assertEquals(1, objects.activeObjectsOfType(CnzTeleporterInstance.class).size());
        var capsules = objects.activeObjectsOfType(CnzEggCapsuleInstance.class);
        assertEquals(1, capsules.size(), "ordinary CNZ2_ScreenEvent must admit the actual capsule");
        var capsule = capsules.getFirst();
        assertEquals(0x4980, capsule.getX());
        assertEquals(0x0A20, capsule.getY());
        assertFalse(player.isControlLocked());

        // Declared component movement inlet above the actual registered button.
        // Original movement + inline SolidObjectFull checkpoint must make contact.
        int buttonTop = capsule.getPieceY(1) - capsule.getPieceParams(1).airHalfHeight();
        NativePositionOps.writeXPosPreserveSubpixel(player, capsule.getPieceX(1));
        NativePositionOps.writeYPosPreserveSubpixel(player, buttonTop - player.getYRadius() - 8);
        player.setAir(true);
        player.setJumping(false);
        player.setRolling(false);
        player.setXSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setYSpeed((short) 0x100);
        fixture.camera().updatePosition(true);
        assertFalse(player.getDead());
        assertFalse(player.isObjectControlled());
        for (int frame = 0; frame < 32 && !objects.isRidingObject(player, capsule); frame++) {
            idle(fixture);
        }
        assertTrue(objects.isRidingObject(player, capsule),
                "prerequisite: ordinary falling movement must land on the admitted capsule button");
        assertFalse(player.getAir());
        assertEquals(1, objects.getRidingPieceIndex(player),
                "ordinary solid contact is with the actual button, not the lower capsule body");

        for (int frame = 0; frame < 96
                && objects.activeObjectsOfType(S3kResultsScreenObjectInstance.class).isEmpty(); frame++) {
            idle(fixture);
        }
        var resultsObjects = objects.activeObjectsOfType(S3kResultsScreenObjectInstance.class);
        assertEquals(1, resultsObjects.size(),
                "prerequisite: real button/open/$40-underflow/grounded checks must produce results");
        var results = resultsObjects.getFirst();
        assertTrue(player.isObjectControlled());
        assertTrue(player.isControlLocked());
        assertEquals(Sonic3kCNZEvents.FG_ACT2_KNUCKLES_ROUTE, events.getForegroundRoutine());

        return new ResultsEntry(fixture, results);
    }

    private static int runToResultsExit(ResultsEntry entry) {
        // The fresh timer/rings give the stock 50000 time bonus. Its 100-point
        // tally, entry/exit slides and native 90-entry waits fit within this
        // conservative budget; no result phase/timer is overwritten.
        int entries = 0;
        while (entries < 2000 && !entry.results().isDestroyed()) {
            idle(entry.fixture());
            assertFalse(entry.fixture().sprite().getDead(), "results support retains the live actual P1");
            entries++;
        }
        assertTrue(entry.results().isDestroyed(),
                "prerequisite: the original results owner must reach its own normal exit");
        assertTrue(GameServices.level().getObjectManager()
                .activeObjectsOfType(S3kResultsScreenObjectInstance.class).isEmpty());
        return entries;
    }

    private static Sonic3kCNZEvents cnzEvents() {
        return assertInstanceOf(Sonic3kLevelEventManager.class,
                GameServices.module().getLevelEventProvider()).getCnzEvents();
    }

    private static void assertHandoff(HeadlessTestFixture fixture) {
        var player = fixture.sprite();
        var objects = GameServices.level().getObjectManager();
        var events = cnzEvents();
        // Native loc_521D2 runs after this object pass and consumes the genuine
        // results publication. This is the semantic old-red boundary.
        assertAll("ordinary results must reach the allocation-gated CNZ2 FG4 handoff",
                () -> assertEquals(Sonic3kCNZEvents.FG_ACT2_NORMAL, events.getForegroundRoutine()),
                () -> assertEquals(0x49A0, fixture.camera().getMaxXTarget() & 0xFFFF),
                () -> assertEquals(0x48E0, fixture.camera().getMaxX() & 0xFFFF,
                        "a screen-event allocation cannot run the worker before the next object pass"),
                () -> assertEquals(1, objects.activeObjectsOfType(S3kIncLevelEndXGradualInstance.class).size()),
                () -> assertFalse(player.isObjectControlled()),
                () -> assertFalse(player.isControlLocked()),
                () -> assertEquals(Sonic3kAnimationIds.WAIT.id(), player.getAnimationId()),
                () -> assertTrue(GameServices.gameState().isEndOfLevelFlag()));

        assertEquals(Sonic3kAnimationIds.WAIT.id(),
                player.getAnimationManager().captureRewindState().lastAnimationId());
        assertEquals(0, player.getAnimationFrameIndex());
        assertEquals(0, player.getAnimationTick());
        assertEquals(0x49A0, events.getCameraStoredMaxXPos() & 0xFFFF);
    }

    private static void assertCompleteWorld(CompositeSnapshot expected, CompositeSnapshot actual, String context) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), context + " all registered keys");
        for (String key : expected.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.keyEquals(key, expected.entries().get(key), actual.entries().get(key)),
                    () -> context + " " + RewindSnapshotDiff.diffKey(
                            key, expected.entries().get(key), actual.entries().get(key)));
        }
    }

    private static PlayerProjection projection(AbstractPlayableSprite player) {
        return new PlayerProjection(player.getCentreX(), player.getCentreY(), player.getXSpeed(),
                player.getYSpeed(), player.getGSpeed(), player.getAir(), player.getRolling(),
                player.isObjectControlled(), player.isControlLocked(), player.getAnimationId(),
                player.getAnimationFrameIndex(), player.getAnimationTick());
    }

    private record ResultsEntry(HeadlessTestFixture fixture, S3kResultsScreenObjectInstance results) { }
    private record PlayerProjection(int x, int y, int xSpeed, int ySpeed, int gSpeed,
                                    boolean air, boolean rolling, boolean controlled, boolean locked,
                                    int animation, int animationFrame, int animationTick) { }

    private static void idle(HeadlessTestFixture fixture) {
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME,
                fixture.runner().stepFrame(false, false, false, false, false));
    }
}
