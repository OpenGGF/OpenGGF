package com.openggf.tests;

import com.openggf.GameLoop;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindController;
import com.openggf.game.sonic3k.objects.Sonic3kSSEntryFlashObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kSSEntryRingObjectInstance;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.GameLoopRewindTestAccess.owner;

/**
 * Independent local MHZ1 forced-ring → sanctuary → Saved2 origin lifecycle.
 * Physical contacts are declared scene setup; this is not a cold route claim.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSanctuaryOriginReturnLifecycle {
    private final CanonicalEntryMatrixSupport origin = support("MHZ1", 7, 0);
    private final CanonicalEntryMatrixSupport hub = support("HPZ-sanctuary", 0x17, 1);
    private static final Bk2FrameInput NEUTRAL = new Bk2FrameInput(0, 0, 0, false, "");

    @BeforeEach void saveConfiguration() { origin.saveConfiguration(); hub.saveConfiguration(); }
    @AfterEach void restoreConfiguration() { origin.restoreConfiguration(); }

    private static CanonicalEntryMatrixSupport support(String label, int zone, int act) {
        return new CanonicalEntryMatrixSupport() {
            @Override TestS3kCanonicalEntryLifecycle.Act binding() {
                return new TestS3kCanonicalEntryLifecycle.Act(label, zone, act);
            }
        };
    }

    @Test
    void actualPlacedForcedRingAndExitPadRestoreSaved2OriginAndIsolateBothTimelines()
            throws Exception {
        assertOriginReturn(new CanonicalEntryMatrixSupport.Row(
                "sonic", WidescreenAspect.NATIVE_4_3, "off", ""));
    }

    static Stream<CanonicalEntryMatrixSupport.Row> representativeRows() {
        return Stream.of(
                new CanonicalEntryMatrixSupport.Row("sonic", WidescreenAspect.NATIVE_4_3, "off", ""),
                new CanonicalEntryMatrixSupport.Row("tails", WidescreenAspect.NATIVE_4_3, "off", ""),
                new CanonicalEntryMatrixSupport.Row("knuckles", WidescreenAspect.NATIVE_4_3, "off", ""),
                new CanonicalEntryMatrixSupport.Row("sonic", WidescreenAspect.NATIVE_4_3, "s1", ""),
                new CanonicalEntryMatrixSupport.Row("sonic", WidescreenAspect.NATIVE_4_3, "s2", ""),
                new CanonicalEntryMatrixSupport.Row("tails", WidescreenAspect.NATIVE_4_3, "s2", ""),
                new CanonicalEntryMatrixSupport.Row("knuckles", WidescreenAspect.SUPER_32_9, "off",
                        "tails,tails,knuckles,sonic,knuckles,sonic,tails"),
                new CanonicalEntryMatrixSupport.Row("sonic", WidescreenAspect.SUPER_32_9, "s1",
                        "sonic,sonic,sonic,sonic,sonic,sonic,sonic"));
    }

    @ParameterizedTest(name = "actual Saved2 inlet/exit representative {0}")
    @MethodSource("representativeRows")
    void configuredRepresentativeUsesTheActualSaved2OwnersAndIncomingWorldReplay(
            CanonicalEntryMatrixSupport.Row row) throws Exception {
        assertOriginReturn(row);
    }

    static Stream<CanonicalEntryMatrixSupport.Row> wideRows() {
        return representativeRows().filter(row -> row.aspect() == WidescreenAspect.SUPER_32_9);
    }

    @ParameterizedTest(name = "actual wide Saved2 owner observation {0}")
    @MethodSource("wideRows")
    void wideContactAndReturnObserveTheActualLoadAndReleasedActors(CanonicalEntryMatrixSupport.Row row)
            throws Exception {
        assertOriginReturn(row);
    }

    static Stream<CanonicalEntryMatrixSupport.Row> configurations() {
        return CanonicalEntryMatrixSupport.configurations();
    }

    @ParameterizedTest(name = "actual Saved2 supported product {0}")
    @MethodSource("configurations")
    @org.junit.jupiter.api.Tag("exhaustive-audit")
    void everySupportedProductUsesRealInletExitAndBothIncomingWorlds(
            CanonicalEntryMatrixSupport.Row row) throws Exception {
        assertOriginReturn(row);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> nativeOutcomes() {
        return Stream.of("sonic", "tails", "knuckles").flatMap(main ->
                Stream.of(SanctuaryResultsLifecycleProbe.Outcome.values()).map(outcome ->
                        org.junit.jupiter.params.provider.Arguments.of(
                                new CanonicalEntryMatrixSupport.Row(main, WidescreenAspect.NATIVE_4_3,
                                        "off", ""), outcome)));
    }

    @Test
    void donatedTailsNativeWidthResultsReturnPilot() throws Exception {
        assertOriginReturn(new CanonicalEntryMatrixSupport.Row("tails", WidescreenAspect.NATIVE_4_3,
                "s2", ""), SanctuaryResultsLifecycleProbe.Outcome.SUCCESS);
    }

    @Test
    void nativeKnucklesWideMixedTeamResultsReturnPilot() throws Exception {
        assertOriginReturn(new CanonicalEntryMatrixSupport.Row("knuckles", WidescreenAspect.SUPER_32_9,
                "off", "tails,tails,knuckles,sonic,knuckles,sonic,tails"), SanctuaryResultsLifecycleProbe.Outcome.FAILURE);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> supportedOutcomes() {
        return configurations().flatMap(row -> Stream.of(SanctuaryResultsLifecycleProbe.Outcome.values())
                .map(outcome -> org.junit.jupiter.params.provider.Arguments.of(row, outcome)));
    }

    @ParameterizedTest(name = "actual native results/Saved2 {0}/{1}")
    @MethodSource("nativeOutcomes")
    void nativeResultOutcomeOwnersReturnToTheActualSaved2Origin(CanonicalEntryMatrixSupport.Row row,
            SanctuaryResultsLifecycleProbe.Outcome outcome) throws Exception {
        assertOriginReturn(row, outcome);
    }

    @ParameterizedTest(name = "actual supported results/Saved2 {0}/{1}")
    @MethodSource("supportedOutcomes")
    @org.junit.jupiter.api.Tag("exhaustive-audit")
    void everySupportedOutcomeUsesTheRealPedestalResultsAndOriginOwners(
            CanonicalEntryMatrixSupport.Row row, SanctuaryResultsLifecycleProbe.Outcome outcome) throws Exception {
        assertOriginReturn(row, outcome);
    }

    private void assertOriginReturn(CanonicalEntryMatrixSupport.Row row) throws Exception {
        assertOriginReturn(row, null);
    }

    private void assertOriginReturn(CanonicalEntryMatrixSupport.Row row,
                                   SanctuaryResultsLifecycleProbe.Outcome outcome) throws Exception {
        var fixture = origin.boot(row);
        // Declared persistent progress precedes every native controller dispatch.
        GameServices.gameState().restoreS3kEmeraldProgress(outcome == null
                ? List.of(0, 0, 0, 0, 0, 0, 0) : outcome.initialProgress(), outcome != null);
        var input = new InputHandler();
        input.setLogicalOverride(RecordedInputSnapshots.fromBk2(NEUTRAL, NEUTRAL));
        var loop = new GameLoop(input);
        loop.setGameplayMode(fixture.gameplayMode());
        loop.setGameMode(GameMode.LEVEL);
        int lives = GameServices.gameState().getLives();
        try {
            assertFalse(GameServices.level().getTransitions().isLastStarPostHitSet(), "native fresh entry flag");
            var sourceObjects = GameServices.level().getObjectManager();
            var ringSpawn = GameServices.level().getCurrentLevel().getObjects().stream()
                    .filter(spawn -> spawn.objectId() == 0x85 && spawn.subtype() == 0x81)
                    .findFirst().orElseThrow();
            // ROM MHZ Object Pos/1.bin offset30 is the actual forced sanctuary inlet.
            assertEquals(448, ringSpawn.x());
            assertEquals(1664, ringSpawn.y());
            tick(loop, lives, 8);
            var main = GameServices.sprites().getMainPlayable();
            var jump = new Bk2FrameInput(0, AbstractPlayableSprite.INPUT_JUMP, 1, false, "");
            input.setLogicalOverride(RecordedInputSnapshots.fromBk2(jump, NEUTRAL));
            tick(loop, lives, 1);
            input.setLogicalOverride(RecordedInputSnapshots.fromBk2(NEUTRAL, jump));
            tick(loop, lives, 1);

            // Earn a real placed ring before entry. The further same-height
            // cluster is beyond MHZ1's native Knuckles encounter and therefore
            // cannot certify this inlet by a coordinate-only local stimulus.
            var stageRing = GameServices.level().getCurrentLevel().getRings().stream()
                    .min(java.util.Comparator.comparingInt(spawn ->
                            Math.abs(spawn.x() - ringSpawn.x()) + Math.abs(spawn.y() - ringSpawn.y())))
                    .orElseThrow();
            assertEquals(496, stageRing.x());
            assertEquals(1328, stageRing.y());
            int beforeRings = main.getRingCount();
            placeForLocalContact(main, stageRing.x(), stageRing.y());
            for (int frame = 0; frame < 8 && main.getRingCount() == beforeRings; frame++) {
                tick(loop, lives, 1);
            }
            assertTrue(main.getRingCount() > beforeRings, "actual placed ring pickup before Saved2: " + row
                    + " P1=" + main.getCentreX() + "/" + main.getCentreY()
                    + " ctrl=" + main.isObjectControlled() + " hidden=" + main.isHidden()
                    + " role=" + main.getGameRules().playerCapability()
                    + " camera=" + GameServices.camera().getX() + "/" + GameServices.camera().getY()
                    + " writers=" + GameServices.sprites().getRegisteredSidekicks().stream()
                        .map(player -> player.getCode() + ":" + player.getCpuController().getState()
                            + "/" + player.getTailsCarryController().isCarryingMainCharacter()).toList());
            // Let the actual render/offscreen owner observe the higher pickup.
            // An untouched Obj_WaitOffscreen owner remains valid. Only an inlet
            // already released at native Knuckles' lower start can now carry a
            // real delayed retirement; that phase must clear before reentry.
            tick(loop, lives, 2);
            var outgoingRing = sourceObjects.activeObjectsOfType(Sonic3kSSEntryRingObjectInstance.class).stream()
                    .filter(object -> object.getSpawn().equals(ringSpawn)).findFirst().orElse(null);
            if (outgoingRing != null && !ringIsMain(outgoingRing)) {
                assertTrue(sourceObjects.activeObjectsOfType(Sonic3kSSEntryFlashObjectInstance.class).isEmpty(),
                        "the higher ring pickup did not enter the inlet");
                for (int frame = 0; frame < 96 && !outgoingRing.isDestroyed(); frame++) tick(loop, lives, 1);
                assertTrue(outgoingRing.isDestroyed(), "observed offscreen retirement really clears");
                assertTrue(outgoingRing.isDestroyedRespawnable(), "uncollected inlet retains native reentry");
            }
            // Load_Sprites' native Y pass exposes one128px strip on each
            // coarse crossing. Reenter after retirement in at-most128px local
            // increments; the actual loader decides admission independently.
            int approachY = ringSpawn.y() - 8;
            for (int y = stageRing.y(); y < approachY; y = Math.min(y + 0x80, approachY)) {
                placeForLocalContact(main, ringSpawn.x() - 40, y);
                tick(loop, lives, 1);
            }
            // Stay outside the +/-24 horizontal touch box during formation.
            placeForLocalContact(main, ringSpawn.x() - 40, approachY);
            Sonic3kSSEntryRingObjectInstance ring = null;
            for (int frame = 0; frame < 96; frame++) {
                tick(loop, lives, 1);
                ring = sourceObjects.activeObjectsOfType(Sonic3kSSEntryRingObjectInstance.class).stream()
                        .filter(object -> object.getSpawn().equals(ringSpawn)).findFirst().orElse(null);
                if (ring != null && (ringMapping(ring) >= 8
                        || !sourceObjects.activeObjectsOfType(Sonic3kSSEntryFlashObjectInstance.class).isEmpty())) break;
            }
            assertNotNull(ring, "actual placed $85 admission");
            assertTrue(ringMapping(ring) >= 8, "native formation reached its collision frame");
            assertFalse(GameServices.level().hasBigRingReturn());
            if (sourceObjects.activeObjectsOfType(Sonic3kSSEntryFlashObjectInstance.class).isEmpty()) {
                assertFalse(main.isObjectControlled(), "entry has not been seeded");
                placeForLocalContact(main, ringSpawn.x() + 8, ringSpawn.y() - 4);
                tick(loop, lives, 1);
            }
            // An ordinary glide may already reach the touch box on formation's
            // final pass; its actual flash is equally authoritative contact.

            assertTrue(main.isObjectControlled(), "real giant-ring touch owns control");
            assertTrue(main.isHidden(), "real capture owns hidden presentation");
            assertEquals(1, sourceObjects.activeObjectsOfType(Sonic3kSSEntryFlashObjectInstance.class).size());
            var sourceController = requireHistory(loop, "the origin records before its actual ring exit");
            int sourceHistory = sourceController.currentFrame();
            assertTrue(sourceHistory > 10);
            for (int frame = 0; frame < 320 && GameServices.level().getObjectManager() == sourceObjects; frame++) {
                tick(loop, lives, 1);
                sourceHistory = Math.max(sourceHistory, sourceController.currentFrame());
            }
            assertNotSame(sourceObjects, GameServices.level().getObjectManager(), "actual flash destination load");
            assertRetiredHistory(loop, sourceController, "the actual hub load retires origin replay");
            var saved = GameServices.level().getBigRingReturn();
            assertNotNull(saved, "flash Save_Level_Data2 survives hub assembly");
            assertEquals(ringSpawn.x(), saved.playerX());
            assertEquals(ringSpawn.y(), saved.playerY(), "Save_Level_Data2 owns flash/ring position");
            assertEquals(7, saved.originZone());
            assertEquals(0, saved.originAct());
            assertTrue(saved.rings() > 0);
            waitForReleasedWorld(loop, lives, 0x17, 1, row);
            hub.assertOwners(row);
            assertTrue(GameServices.level().getLevelGamestate().isTimerPaused());
            assertTrue(requireHistory(loop, "released hub owns its incoming timeline").currentFrame() < sourceHistory,
                    "actual hub load retires outgoing origin history");
            for (int frame = 0; frame < 220 && GameServices.sprites().getMainPlayable().isObjectControlled(); frame++) {
                tick(loop, lives, 1);
            }
            assertFalse(GameServices.sprites().getMainPlayable().isObjectControlled());
            replayTwice(loop, fixture, hub, row, lives);
            assertEquals(saved, GameServices.level().getBigRingReturn(), "hub replay preserves Saved2");

            int hubHistory;
            if (outcome != null) {
                var provider = TestS3kSanctuaryPedestalLifecycle.selectAndReplayIncomingStage(
                        loop, input, fixture, hub, row, 6, lives);
                assertEquals(saved, GameServices.level().getBigRingReturn(), "stage entry preserves Saved2");
                hubHistory = SanctuaryResultsLifecycleProbe.completeAndReturn(
                        loop, provider, fixture, row, outcome, lives, saved);
                // Results can legitimately award score lives; they may never consume one.
                assertTrue(GameServices.gameState().getLives() >= lives);
                lives = GameServices.gameState().getLives();
            } else {
                var hubObjects = GameServices.level().getObjectManager();
                main = GameServices.sprites().getMainPlayable();
                input.setLogicalOverride(RecordedInputSnapshots.fromBk2(new Bk2FrameInput(0,
                        AbstractPlayableSprite.INPUT_RIGHT, 0, false, ""), NEUTRAL));
                for (int frame = 0; frame < 64 && main.getCentreX() < 0x1640 + 24; frame++) {
                    tick(loop, lives, 1);
                }
                assertTrue(main.getCentreX() >= 0x1640 + 24, "ordinary movement arms native exit band");
                input.setLogicalOverride(RecordedInputSnapshots.fromBk2(new Bk2FrameInput(0,
                        AbstractPlayableSprite.INPUT_LEFT, 0, false, ""), NEUTRAL));
                var hubController = requireHistory(loop, "the hub records before its actual pad exit");
                hubHistory = hubController.currentFrame();
                for (int frame = 0; frame < 320 && GameServices.level().getObjectManager() == hubObjects; frame++) {
                    tick(loop, lives, 1);
                    hubHistory = Math.max(hubHistory, hubController.currentFrame());
                    if (GameServices.level().isLevelInactiveForTransition()) {
                        // The actual exit owner has published; destination gets
                        // neutral input rather than another walking command.
                        input.setLogicalOverride(RecordedInputSnapshots.fromBk2(NEUTRAL, NEUTRAL));
                    }
                }
                input.setLogicalOverride(RecordedInputSnapshots.fromBk2(NEUTRAL, NEUTRAL));
                assertNotSame(hubObjects, GameServices.level().getObjectManager(), "real pad/exit band loads origin");
                assertRetiredHistory(loop, hubController, "the actual pad exit retires hub replay");
            }
            assertEquals(saved.playerX(), GameServices.sprites().getMainPlayable().getCentreX(),
                    "actual Saved2 assembly position before ordinary destination movement");
            // Results' actual SpawnLevelMainSprites return owner applies the
            // literal Player_mode2 +4 to P1 (loc_6B42/asm8388). Pad exits use
            // the distinct existing ordinary reload path above.
            int returnSpawnY = saved.playerY()
                    + (outcome != null && row.main().equals("tails") ? 4 : 0);
            assertEquals(returnSpawnY, GameServices.sprites().getMainPlayable().getCentreY());
            waitForReleasedWorld(loop, lives, 7, 0, row);
            origin.assertOwners(row);
            assertFalse(com.openggf.sprites.playable.PlayableSpriteInternalAccess.objectOwnsMappingFrame(
                    GameServices.sprites().getMainPlayable()), "Saved2 origin releases native mapping ownership");
            assertFalse(GameServices.level().hasBigRingReturn(), "Saved2 consumed only after destination restore");
            assertTrue(GameServices.level().getTransitions().isLastStarPostHitSet(), "native saved eligibility survives consumption");
            assertEquals(saved.rings(), GameServices.sprites().getMainPlayable().getRingCount());
            assertEquals(saved.playerX(), GameServices.sprites().getMainPlayable().getCentreX(),
                    "actual released Saved2 origin position");
            assertFalse(GameServices.level().getLevelGamestate().isTimerPaused(), "origin clock resumes");
            assertTrue(GameServices.level().getLevelGamestate().getTimerFrames()
                    >= Math.max(0, saved.timerFrames() / 60 * 60 - 1), "native Saved2 second/frame bank restored");
            assertTrue(GameServices.camera().isLevelStarted(), "ordinary origin HUD owner resumes");
            if (outcome == null) {
                assertTrue(requireHistory(loop, "released origin owns its incoming timeline").currentFrame() < hubHistory,
                        "origin reload retires outgoing hub history");
            }
            replayTwice(loop, fixture, origin, row, lives);
        } finally {
            loop.closePresence();
        }
    }

    private static boolean ringIsMain(Sonic3kSSEntryRingObjectInstance ring) throws Exception {
        var method = Sonic3kSSEntryRingObjectInstance.class.getDeclaredMethod("isMainState");
        method.setAccessible(true);
        return (boolean) method.invoke(ring);
    }

    private static int ringMapping(Sonic3kSSEntryRingObjectInstance ring) throws Exception {
        var method = Sonic3kSSEntryRingObjectInstance.class.getDeclaredMethod("getMappingFrame");
        method.setAccessible(true);
        return (int) method.invoke(ring);
    }

    private static void placeForLocalContact(AbstractPlayableSprite main, int x, int y) {
        NativePositionOps.writeXPosPreserveSubpixel(main, x);
        NativePositionOps.writeYPosPreserveSubpixel(main, y);
        main.setXSpeed((short) 0);
        main.setGSpeed((short) 0);
        main.setYSpeed((short) 0);
        main.setAir(true);
        main.setJumping(false);
        GameServices.camera().updatePosition(true);
    }

    private static void tick(GameLoop loop, int lives, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            loop.step();
            GameServices.graphics().runPendingRenderThreadTasks();
            assertFalse(GameServices.sprites().getMainPlayable().getDead(), "no P1 death during actual transition");
            assertEquals(lives, GameServices.gameState().getLives(), "no hidden reload");
        }
    }

    static void waitForReleasedWorld(GameLoop loop, int lives, int zone, int act,
                                            CanonicalEntryMatrixSupport.Row row) throws Exception {
        boolean awaitingHistory = owner(loop).getRewindController() == null;
        for (int frame = 0; frame < 400 && (loop.getCurrentGameMode() != GameMode.LEVEL
                || GameServices.fade().isActive()
                || GameServices.fade().hasPendingCompletion()
                || GameServices.level().hasPendingFreshLevelTransitionBoundary()); frame++) {
            tick(loop, lives, 1);
            if (awaitingHistory && owner(loop).getRewindController() != null) {
                var incoming = requireHistory(loop, "completed destination assembly installs history");
                assertEquals(0, incoming.earliestAvailableFrame());
                assertTrue(incoming.currentFrame() <= 1,
                        "the first incoming production controller starts at frame zero");
                awaitingHistory = false;
            }
        }
        assertEquals(zone, GameServices.level().getCurrentZone());
        assertEquals(act, GameServices.level().getCurrentAct());
        assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
        assertFalse(GameServices.fade().isActive());
        assertFalse(GameServices.fade().hasPendingCompletion());
        assertFalse(GameServices.level().hasPendingFreshLevelTransitionBoundary());
        requireHistory(loop, "released destination requires production live history");
    }

    static RewindController requireHistory(GameLoop loop, String reason) {
        var context = owner(loop);
        assertNotNull(context, "the production loop owns a gameplay visit");
        var controller = context.getRewindController();
        assertNotNull(controller, reason);
        return controller;
    }

    static void assertRetiredHistory(GameLoop loop, RewindController outgoing, String reason) {
        var context = owner(loop);
        assertNotSame(outgoing, context.getRewindController(), reason);
        int historyFrame = outgoing.currentFrame();
        int worldFrame = context.getLevelManager().getFrameCounter();
        var objects = context.getLevelManager().getObjectManager();
        outgoing.step();
        outgoing.seekTo(0);
        assertFalse(outgoing.stepBackward(), reason);
        assertEquals(historyFrame, outgoing.currentFrame(), reason);
        assertEquals(worldFrame, context.getLevelManager().getFrameCounter(), reason);
        assertSame(objects, context.getLevelManager().getObjectManager(), reason);
    }

    static void replayTwice(GameLoop loop, HeadlessTestFixture fixture,
                                    CanonicalEntryMatrixSupport owner, CanonicalEntryMatrixSupport.Row row,
                                    int lives) {
        owner.assertOwners(row);
        assertFalse(GameServices.fade().hasPendingCompletion());
        var registry = owner(loop).getRewindRegistry();
        var before = registry.capture();
        tick(loop, lives, 45);
        owner.assertOwners(row);
        var after = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before);
            TestS3kCanonicalEntryLifecycle.assertSameWorld(before, registry.capture(), "Saved2 incoming restore", 45);
            tick(loop, lives, 45);
            owner.assertOwners(row);
            TestS3kCanonicalEntryLifecycle.assertSameWorld(after, registry.capture(), "Saved2 incoming replay", 45);
        }
    }
}
