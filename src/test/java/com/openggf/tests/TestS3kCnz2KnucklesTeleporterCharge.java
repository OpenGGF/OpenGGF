package com.openggf.tests;

import com.openggf.LevelFrameResult;
import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kCNZEvents;
import com.openggf.game.sonic3k.objects.CnzEggCapsuleInstance;
import com.openggf.game.sonic3k.objects.CnzTeleporterBeamInstance;
import com.openggf.game.sonic3k.objects.CnzTeleporterInstance;
import com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance;
import com.openggf.game.sonic3k.objects.TeleporterBeamObjectInstance;
import com.openggf.game.sonic3k.objects.IczTeleporterInstance;
import com.openggf.game.sonic3k.objects.IczSnowboardIntroInstance;
import com.openggf.game.sonic3k.runtime.IczZoneRuntimeState;
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
 * CNZ2 Knuckles' ordinary capsule release -> teleporter charging prefix.
 *
 * <p>Origin: bounded CNZ2 gameplay completion work, 2026-10-20. The inherited
 * public positioned-button inlet is identical in purpose to
 * {@link TestS3kCnz2KnucklesCapsuleHandoff}: ordinary movement must press the
 * actual event-admitted capsule, and its real results owner must exit. After
 * that inlet this test supplies ordinary Right input, with a jump when grounded
 * movement is obstructed; graph replay uses that same sequence. It never writes
 * a route flag, object phase, clock, SST slot, art readiness or captured pose.
 * It stops at beam progress $11, before the separate $18 hide/camera-rise/ICZ
 * contract. All clocks below are actual Level_frame_counter values.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCnz2KnucklesTeleporterCharge {
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
    void ordinaryReleasedKnucklesAdmitsSharedBeamAndFollowsNativeChargeLift() throws Exception {
        verifyNativeChargePrefix(releaseFromActualCapsule(), true);
    }

    @Test
    void positionedNativeGateAdmitsSharedBeamAndReplaysCaptureAndLiftTwice() throws Exception {
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .startPosition((short) 0x48C0, (short) 0x0B40)
                .startPositionIsCentre().build();
        idle(fixture);
        assertTrue(cnzEvents().isKnucklesTeleporterRouteActive());
        // DECLARED COMPONENT INLET: actual event admits the parent/capsule;
        // only P1's public pose at the native arm gate is positioned here.
        // This independent beam/control/rewind check does not certify travel
        // from the capsule and cannot rescue that route test's prerequisite.
        NativePositionOps.writeXPosPreserveSubpixel(fixture.sprite(), 0x4A38);
        NativePositionOps.writeYPosPreserveSubpixel(fixture.sprite(), 0x0A2C);
        fixture.sprite().setAir(false);
        fixture.sprite().setXSpeed((short) 0);
        fixture.sprite().setYSpeed((short) 0);
        fixture.sprite().setGSpeed((short) 0);
        fixture.camera().updatePosition(true);
        // Split component dispatch: this inlet deliberately omits the real
        // results-driven camera opening, so a player pass would apply the old
        // right boundary before this positioned native gate could be read.
        GameServices.level().getObjectManager().activeObjectsOfType(CnzTeleporterInstance.class)
                .getFirst().update(0, fixture.sprite());
        assertTrue(fixture.sprite().isControlLocked());
        verifyNativeChargePrefix(fixture, false);
    }

    @Test
    void readyGroundedParentRetriesRealAfterCurrentAllocationWithoutPublishingEffects() throws Exception {
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .startPosition((short) 0x48C0, (short) 0x0B40)
                .startPositionIsCentre().build();
        idle(fixture);
        var objects = GameServices.level().getObjectManager();
        var parent = objects.activeObjectsOfType(CnzTeleporterInstance.class).getFirst();
        var player = fixture.sprite();
        // Public component arm, followed by real ordinary KosM service.
        NativePositionOps.writeXPosPreserveSubpixel(player, 0x4A38);
        NativePositionOps.writeYPosPreserveSubpixel(player, 0x0A2C);
        player.setAir(false);
        parent.update(0, player);
        var art = assertInstanceOf(Sonic3kObjectArtProvider.class,
                GameServices.module().getObjectArtProvider());
        // Keep Main airborne while the actual provider drains its request.
        NativePositionOps.writeYPosPreserveSubpixel(player, 0x0900);
        player.setAir(true);
        for (int i = 0; i < 128 && !art.isCnzTeleporterArtComplete(); i++) idle(fixture);
        assertTrue(art.isCnzTeleporterArtComplete());
        var sheet = art.getSheet(Sonic3kObjectArtKeys.CNZ_TELEPORTER);
        assertNotNull(sheet);
        assertEquals(16, sheet.getPatterns().length, "ArtKosM_CNZTeleport's ROM header is $0200 bytes");
        assertTrue(sheet.getFrame(0).pieces().isEmpty(), "native CNZ parent draws no HPZ/SSZ pad");
        for (int frame = 1; frame <= 8; frame++) {
            assertFalse(sheet.getFrame(frame).pieces().isEmpty());
            for (var piece : sheet.getFrame(frame).pieces()) {
                assertEquals(frame <= 2 ? 0 : 4, piece.tileIndex(),
                        "$24EE plus mapping $12/$16 resolves into the module at $500");
                assertTrue(piece.tileIndex() + piece.widthTiles() * piece.heightTiles()
                        <= sheet.getPatterns().length, "every actual CNZ beam piece has resident ROM art");
            }
        }
        // This sheet also supports the shared beam's contraction terminal
        // mapping; the actual CNZ route below never requests contraction.
        assertEquals(8, sheet.getFrame(9).pieces().size(), "native word_46CA2 has eight terminal pieces");
        for (var piece : sheet.getFrame(9).pieces()) {
            assertEquals(0, piece.tileIndex(), "terminal mapping $12 resolves to local tile 0");
            assertEquals(1, piece.widthTiles());
            assertEquals(4, piece.heightTiles());
            assertTrue(piece.tileIndex() + piece.widthTiles() * piece.heightTiles()
                    <= sheet.getPatterns().length, "the terminal frame uses resident CNZ ROM art");
        }
        assertTrue(sheet.getFrame(10).pieces().isEmpty(), "the other HPZ/SSZ pad remains absent in CNZ");
        assertFalse(cnzEvents().isTeleporterBeamSpawned());
        player.setAir(false);
        var admitted = new ArrayList<CnzEggCapsuleInstance>();
        for (int i = 0; i <= objects.getObjectSlotCapacity(); i++) {
            var object = objects.createDynamicObject(() -> new CnzEggCapsuleInstance(
                    new ObjectSpawn(0, 0, 0, 0, 0, false, 0)));
            if (object == null) break;
            admitted.add(object);
        }
        assertEquals(objects.getObjectSlotCapacity(), objects.getActiveObjectSlotCount());
        var before = pose(player);
        int parentX = parent.getX();
        int soundBefore = GameServices.audio().captureLogicalSnapshot().commandEntryCount();
        parent.update(0, player);
        assertEquals(before, pose(player));
        assertEquals(parentX, parent.getX());
        assertEquals(soundBefore, GameServices.audio().captureLogicalSnapshot().commandEntryCount());
        assertFalse(cnzEvents().isTeleporterBeamSpawned());
        assertTrue(objects.activeObjectsOfType(TeleporterBeamObjectInstance.class).isEmpty());
        var released = admitted.removeLast();
        int releasedSlot = released.getSlotIndex();
        assertTrue(releasedSlot > parent.getSlotIndex());
        objects.removeDynamicObject(released);
        parent.update(0, player);
        var beam = objects.activeObjectsOfType(TeleporterBeamObjectInstance.class).getFirst();
        assertEquals(releasedSlot, beam.getSlotIndex());
        assertTrue(cnzEvents().isTeleporterBeamSpawned());
        assertEquals(before, pose(player), "successful Main allocation does not snap P1 X");
        assertEquals(0x4A40, parent.getX());
        assertEquals(soundBefore + 1, GameServices.audio().captureLogicalSnapshot().commandEntryCount());
    }

    @Test
    void ordinaryReleasedKnucklesHidesRisesAndLoadsTheNativeIczArrivalThroughTheRealLoop() throws Exception {
        GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        var fixture = releaseFromActualCapsule();
        // This is the unchanged real button/results -> ordinary-controller
        // prefix, including its two complete-world cycles. No pose, speed,
        // clock, phase, slot, readiness or destination is seeded after results.
        verifyNativeChargePrefix(fixture, true);
        var level = GameServices.level();
        var registry = fixture.gameplayMode().getRewindRegistry();
        int lives = GameServices.gameState().getLives();
        var input = new InputHandler();
        input.setLogicalOverride(LogicalInputSnapshot.neutral());
        var loop = new GameLoop(input);
        loop.setGameplayMode(fixture.gameplayMode());
        assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
        try {
            for (int entry = 0; entry < 64 && nativeBeam().progress() < 0x18; entry++) {
                advanceLoop(loop);
                assertFalse(fixture.sprite().isObjectMappingFrameControl(),
                        "the lower parent has not yet observed this child's new $18");
                assertNotEquals(0, fixture.sprite().getCentreY() & 0xFFFF);
            }
            assertEquals(0x18, nativeBeam().progress());
            var beforeHide = registry.capture();
            var beforePose = pose(fixture.sprite());
            int beforeHideY = fixture.camera().getY() & 0xFFFF;
            int beforeSound = GameServices.audio().captureLogicalSnapshot().commandEntryCount();
            advanceLoop(loop);
            assertEquals(beforePose.withPosition(beforePose.x(), 0), pose(fixture.sprite()),
                    "loc_5238E writes only P1's Y word; fractions, velocity and radii survive");
            assertNativeDisappearance(fixture.sprite());
            assertEquals(beforeHideY, fixture.camera().getY() & 0xFFFF,
                    "the hide dispatch installs loc_523CA without executing it immediately");
            assertTrue(fixture.camera().getFrozen(), "st Scroll_lock skips normal camera following");
            assertEquals(beforeSound + 1, GameServices.audio().captureLogicalSnapshot().commandEntryCount(),
                    "the native disappearance publishes one transporter SFX");
            var afterHide = registry.capture();
            for (int entry = 0; entry < 3; entry++) {
                int previousY = fixture.camera().getY() & 0xFFFF;
                advanceLoop(loop);
                assertEquals((previousY - 0x10) & 0xFFFF, fixture.camera().getY() & 0xFFFF,
                        "loc_523CA subtracts a word on every own dispatch, with no LF divider");
                assertNativeDisappearance(fixture.sprite());
            }
            var duringRise = registry.capture();
            for (int replay = 0; replay < 2; replay++) {
                registry.restore(beforeHide);
                assertCompleteWorld(beforeHide, registry.capture(), "native hide restore " + replay);
                advanceLoop(loop);
                assertCompleteWorld(afterHide, registry.capture(), "native hide replay " + replay);
                for (int entry = 0; entry < 3; entry++) advanceLoop(loop);
                assertCompleteWorld(duringRise, registry.capture(), "native rise replay " + replay);
                registry.restore(afterHide);
                assertCompleteWorld(afterHide, registry.capture(), "native rise phase restore " + replay);
                for (int entry = 0; entry < 3; entry++) advanceLoop(loop);
                assertCompleteWorld(duringRise, registry.capture(), "native rise phase replay " + replay);
            }

            long fadeBefore = GameServices.audio().musicFadeOutCount();
            int riseEntries = 0;
            while (riseEntries < 256 && !level.isLevelInactiveForTransition()) {
                int previousY = fixture.camera().getY() & 0xFFFF;
                int nextY = (previousY - 0x10) & 0xFFFF;
                advanceLoop(loop);
                assertEquals(nextY, fixture.camera().getY() & 0xFFFF);
                assertEquals(nextY < 0x780, level.isLevelInactiveForTransition(),
                        "StartNewLevel is reached only after the new camera word is unsigned below $780");
                assertNativeDisappearance(fixture.sprite());
                riseEntries++;
            }
            assertTrue(level.isLevelInactiveForTransition(), "the real camera must reach StartNewLevel $0500");
            assertEquals(Sonic3kZoneIds.ZONE_ICZ, level.getRequestedZone());
            assertEquals(0, level.getRequestedAct());
            assertEquals(fadeBefore + 1, GameServices.audio().musicFadeOutCount(),
                    "the parent issues exactly one native Play_Music cmd_FadeOut before the loop consumes its request");
            int exitCameraY = fixture.camera().getY() & 0xFFFF;
            int sourceLf = level.getFrameCounter();
            var outgoingLevel = level.getCurrentLevel();
            var outgoingHistory = fixture.gameplayMode().getRewindController();
            assertNotNull(outgoingHistory, "the real GameLoop records live source history");
            int outgoingFrames = outgoingHistory.currentFrame();
            assertTrue(outgoingFrames > 10, "outgoing history must be nonempty before the actual load");

            // The next real loop consumes the request. Native Level itself also
            // submits cmd_FadeOut (Demo_mode_flag >= 0), independently of the
            // object's earlier Play_Music call; the common host owner matches it.
            advanceLoop(loop);
            assertTrue(GameServices.fade().isActive());
            assertEquals(fadeBefore + 2, GameServices.audio().musicFadeOutCount());
            assertEquals(exitCameraY, fixture.camera().getY() & 0xFFFF);
            assertEquals(sourceLf, level.getFrameCounter(), "fade service must not execute source gameplay");

            boolean sawTitle = false;
            boolean sawFreshAssembly = false;
            boolean sawArrival = false;
            boolean sawHistoryRetired = false;
            boolean observedIncoming = false;
            boolean released = false;
            for (int entry = 0; entry < 1800 && !released; entry++) {
                advanceLoop(loop);
                assertEquals(lives, GameServices.gameState().getLives(), "the handoff does not consume a life");
                if (level.getCurrentZone() != Sonic3kZoneIds.ZONE_ICZ) {
                    assertEquals(sourceLf, level.getFrameCounter(), "source LF stays parked during the fade");
                    assertEquals(exitCameraY, fixture.camera().getY() & 0xFFFF);
                    continue;
                }
                if (!observedIncoming) {
                    var incomingHistory = fixture.gameplayMode().getRewindController();
                    sawHistoryRetired = incomingHistory == null || incomingHistory.currentFrame() < outgoingFrames;
                    assertTrue(sawHistoryRetired, "the actual load retires nonempty source history immediately");
                    observedIncoming = true;
                }
                assertEquals(0, level.getCurrentAct());
                assertNotSame(outgoingLevel, level.getCurrentLevel(), "actual ROM destination replaces loaded level data");
                var state = assertInstanceOf(IczZoneRuntimeState.class, GameServices.zoneRuntimeRegistry().current());
                assertEquals(0, state.actIndex());
                assertEquals(PlayerCharacter.KNUCKLES, state.playerCharacter());
                assertTrue(level.getObjectManager().activeObjectsOfType(CnzTeleporterInstance.class).isEmpty());
                assertTrue(level.getObjectManager().activeObjectsOfType(IczSnowboardIntroInstance.class).isEmpty(),
                        "Knuckles takes ICZ's teleporter path, not Sonic's snowboard intro");
                var p1 = assertInstanceOf(Knuckles.class, GameServices.sprites().getMainPlayable());
                assertFalse(p1.getDead());
                sawTitle |= loop.getCurrentGameMode() == GameMode.TITLE_CARD;
                sawFreshAssembly |= level.hasPendingFreshLevelTransitionBoundary();
                sawArrival |= !level.getObjectManager().activeObjectsOfType(IczTeleporterInstance.class).isEmpty();
                var title = GameServices.module().getTitleCardProvider();
                released = sawArrival && loop.getCurrentGameMode() == GameMode.LEVEL
                        && (title == null || title.isComplete())
                        && !GameServices.fade().isActive() && !GameServices.fade().hasPendingCompletion()
                        && !level.hasPendingFreshLevelTransitionBoundary()
                        && !p1.isObjectControlled() && !p1.isControlLocked();
            }
            assertTrue(released, () -> "actual Knuckles ICZ arrival must release through title/fade/assembly; zone="
                    + level.getCurrentZone() + " act=" + level.getCurrentAct() + " mode=" + loop.getCurrentGameMode());
            assertTrue(sawTitle, "the real title owner must run during entry");
            assertTrue(sawFreshAssembly, "the native fresh assembly boundary must be observed");
            assertTrue(sawArrival, "ICZ1_ScreenInit must admit its actual Knuckles teleporter owner");
            assertTrue(sawHistoryRetired, "history retirement is checked at load, before the incoming world advances");
            assertEquals(0x3640, GameServices.sprites().getMainPlayable().getCentreX() & 0xFFFF,
                    "the native Knuckles ICZ route releases at its teleporter, not Sonic's left start");
            assertSame(GameServices.sprites().getMainPlayable(), fixture.camera().getFocusedSprite());
            assertFalse(GameServices.sprites().getMainPlayable().isObjectMappingFrameControl());
            assertFalse(GameServices.sprites().getMainPlayable().isHidden());
            assertFalse(level.getCheckpointState().isActive());
            var incoming = registry.capture();
            for (int entry = 0; entry < 45; entry++) advanceLoop(loop);
            var incomingForward = registry.capture();
            for (int replay = 0; replay < 2; replay++) {
                registry.restore(incoming);
                assertCompleteWorld(incoming, registry.capture(), "ICZ released restore " + replay);
                for (int entry = 0; entry < 45; entry++) advanceLoop(loop);
                assertCompleteWorld(incomingForward, registry.capture(), "ICZ released replay " + replay);
                assertSame(GameServices.sprites().getMainPlayable(), fixture.camera().getFocusedSprite());
                assertFalse(GameServices.sprites().getMainPlayable().getDead());
                assertEquals(lives, GameServices.gameState().getLives());
            }
            System.out.println("CNZ2 actual Knuckles ICZ handoff riseEntries=" + riseEntries
                    + " exitCameraY=" + exitCameraY + " sourceLF=" + sourceLf
                    + " outgoingHistory=" + outgoingFrames + " incomingKeys=" + incoming.entries().size()
                    + " nativeHideRiseWorldCycles=2 incomingWorldCycles=2");
        } finally {
            loop.closePresence();
        }
    }

    private static TeleporterBeamObjectInstance nativeBeam() {
        var beams = GameServices.level().getObjectManager().activeObjectsOfType(TeleporterBeamObjectInstance.class);
        assertEquals(1, beams.size());
        return beams.getFirst();
    }

    private static void assertNativeDisappearance(AbstractPlayableSprite player) {
        assertEquals(0, player.getCentreY() & 0xFFFF);
        assertFalse(player.isHidden(), "native disappearance never sets the Java renderer-hidden flag");
        assertTrue(player.isObjectControlled());
        assertTrue(player.isObjectControlAllowsCpu());
        assertTrue(player.isObjectControlSuppressesMovement());
        assertTrue(player.isObjectMappingFrameControl(), "native object_control bit1 owns mapping_frame");
        assertFalse(player.isTouchResponseSuppressedByObjectControl());
        assertEquals(0, player.getAnimationId());
        assertEquals(0, player.getMappingFrame());
    }

    private static void advanceLoop(GameLoop loop) {
        loop.step();
        GameServices.graphics().runPendingRenderThreadTasks();
    }

    private void verifyNativeChargePrefix(HeadlessTestFixture fixture, boolean ordinaryTravel) throws Exception {
        var player = fixture.sprite();
        var level = GameServices.level();
        var objects = level.getObjectManager();
        var registry = fixture.gameplayMode().getRewindRegistry();
        var released = registry.capture();
        int releasedX = player.getCentreX() & 0xFFFF;

        int walkEntries = 0;
        var jumpInputs = new ArrayList<Boolean>();
        var jumpXs = new ArrayList<Integer>();
        int priorX = releasedX;
        while (walkEntries < 256 && !player.isControlLocked()) {
            int currentX = player.getCentreX() & 0xFFFF;
            // Jump follows a real grounded obstruction, not a fitted world X
            // or a phase/clock seed. The emitted buttons are replayed verbatim.
            boolean jump = ordinaryTravel && walkEntries > 0 && !player.getAir()
                    && currentX == priorX && (jumpInputs.isEmpty() || !jumpInputs.getLast());
            priorX = currentX;
            jumpInputs.add(jump);
            if (jump) jumpXs.add(currentX);
            right(fixture, jump);
            assertFalse(player.getDead(), "prerequisite: ordinary travel must retain live Knuckles");
            walkEntries++;
        }
        assertTrue(player.isControlLocked(),
                () -> "prerequisite: ordinary Right/grounded-obstruction Jump did not reach the teleporter gate; x="
                        + (player.getCentreX() & 0xFFFF) + " y=" + (player.getCentreY() & 0xFFFF)
                        + " air=" + player.getAir());
        if (ordinaryTravel) {
            assertTrue((player.getCentreX() & 0xFFFF) > releasedX,
                    "the released lane is crossed by original player movement, not a post-results position seed");
        }
        assertTrue((player.getCentreX() & 0xFFFF) >= 0x4A38);
        if (player.getAir()) {
            assertEquals(0x4A40, player.getCentreX() & 0xFFFF,
                    "Obj_CNZTeleporter $522B4 clamps an actual airborne arrival");
        }
        assertFalse(player.isObjectControlled(), "arming locks input but does not acquire object control");
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getGSpeed());
        var art = assertInstanceOf(Sonic3kObjectArtProvider.class,
                GameServices.module().getObjectArtProvider());
        assertTrue(art.isCnzTeleporterArtPending(),
                "the arming dispatch queues art and returns before the next ordinary art service");
        assertFalse(art.isCnzTeleporterArtComplete());

        int admissionWaitEntries = 0;
        while (admissionWaitEntries < 128 && !cnzEvents().isTeleporterBeamSpawned()) {
            idle(fixture);
            assertFalse(player.getDead(), "prerequisite: ordinary art/landing wait must retain live P1");
            admissionWaitEntries++;
        }
        assertTrue(cnzEvents().isTeleporterBeamSpawned(),
                "prerequisite: original art-ready and grounded checks must admit a beam");
        var shared = objects.activeObjectsOfType(TeleporterBeamObjectInstance.class);
        assertEquals(1, shared.size(),
                () -> "ordinary CNZ admission must use the shared native beam; synthetic CNZ beams="
                        + objects.activeObjectsOfType(CnzTeleporterBeamInstance.class).size());
        assertTrue(objects.activeObjectsOfType(CnzTeleporterBeamInstance.class).isEmpty());
        var beam = shared.getFirst();
        var parents = objects.activeObjectsOfType(CnzTeleporterInstance.class);
        assertEquals(1, parents.size());
        assertTrue(beam.getSlotIndex() > parents.getFirst().getSlotIndex(),
                "$52312 AllocateObjectAfterCurrent admits the real child after its parent");
        assertEquals(0x4A40, beam.getX());
        assertEquals(0x0A38, beam.getY());
        assertEquals(1, beam.getPriorityBucket(), "native priority word $80 is bucket1");
        assertFalse(beam.isHighPriority(), "CNZ art word $24EE has no high-priority bit");
        assertTrue(art.isCnzTeleporterArtComplete());
        assertFalse(player.getAir());
        assertFalse(player.isObjectControlled());
        assertEquals(0, beam.progress());
        int admissionLf = level.getFrameCounter();
        var admitted = registry.capture();

        // $458F2/$45916: timer $10 includes the same-pass initialization
        // entry. Fifteen more own entries only decrement the remaining timer.
        for (int entry = 1; entry <= 15; entry++) {
            idle(fixture);
            assertEquals(0, beam.progress(), "the first wait is not an elapsed-progress counter");
            assertFalse(player.isObjectControlled());
        }

        CompositeSnapshot duringBuild = null;
        int buildLf = -1;
        int lastBuildCompletionLf = -1;
        int completedSegmentPairs = 0;
        for (int entry = 0; entry < 96 && beam.progress() <= 0; entry++) {
            int prior = beam.progress();
            idle(fixture);
            int progress = beam.progress();
            int lf = level.getFrameCounter();
            assertTrue(progress == -1 || progress == 0 || progress == 1,
                    "the signed build byte toggles 0/$FF before the first expansion increment");
            if (progress != prior) {
                assertEquals(1, lf & 1, "build/expand writes are gated by odd Level_frame_counter");
            }
            if (progress == -1 && duringBuild == null) {
                duringBuild = registry.capture();
                buildLf = lf;
            }
            if (prior == -1 && progress == 0) {
                completedSegmentPairs++;
                lastBuildCompletionLf = lf;
            }
            assertFalse(player.isObjectControlled(), "signed $FF is below the capture threshold8");
        }
        assertNotNull(duringBuild, "the original beam must execute its signed build toggles");
        assertEquals(6, completedSegmentPairs, "six additions grow the initialized one segment to seven");
        assertEquals(1, beam.progress());
        assertEquals(3, level.getFrameCounter() & 3,
                "$45A4A increments expansion only when Level_frame_counter bits0+1 are set");
        int expansionWait = level.getFrameCounter() - lastBuildCompletionLf;
        assertTrue(expansionWait == 16 || expansionWait == 18,
                "the second $10 wait precedes the next odd LF with bit1 set");

        for (int entry = 0; entry < 64 && beam.progress() < 8; entry++) {
            int prior = beam.progress();
            idle(fixture);
            int delta = beam.progress() - prior;
            assertEquals((level.getFrameCounter() & 3) == 3 ? 1 : 0, delta);
            assertFalse(player.isObjectControlled(),
                    "the lower-slot parent reads the child's prior progress before its same-pass update");
        }
        assertEquals(8, beam.progress());
        int beforeCaptureLf = level.getFrameCounter();
        var beforeCapture = registry.capture();
        var capturePose = pose(player);
        idle(fixture);
        assertEquals(capturePose, pose(player),
                "$523B6 changes control/status/animation, not positions, subpixels, velocities or radii");
        assertNativeCapture(player);
        int captureLf = level.getFrameCounter();
        var captured = registry.capture();

        CompositeSnapshot duringLift = null;
        int duringLiftLf = -1;
        int liftEntries = 0;
        while (liftEntries < 64 && beam.progress() < 0x11) {
            int priorProgress = beam.progress();
            var priorPose = pose(player);
            int beforeLf = level.getFrameCounter();
            idle(fixture);
            int lf = level.getFrameCounter();
            assertEquals(beforeLf + 1, lf, "each admitted ordinary loop advances the level clock once");
            int expectedX = priorPose.x();
            int expectedY = priorPose.y();
            if (priorProgress > 8) {
                if ((lf & 1) != 0) expectedY--;
                if ((lf & 3) == 3 && expectedX != 0x4A40) {
                    expectedX += expectedX < 0x4A40 ? 1 : -1;
                }
            }
            assertEquals(priorPose.withPosition(expectedX, expectedY), pose(player),
                    "$52364 lifts on odd LF and centers X only when both low LF bits are set");
            assertNativeCapture(player);
            assertEquals((lf & 3) == 3 ? 1 : 0, beam.progress() - priorProgress);
            assertFalse(player.isHidden(), "this prefix stops before the independent $18 hide action");
            assertFalse(beam.isDestroyed(), "CNZ does not impose an invented $20-entry beam lifetime");
            if (beam.progress() == 0x0C && duringLift == null) {
                duringLift = registry.capture();
                duringLiftLf = lf;
            }
            liftEntries++;
        }
        assertEquals(0x11, beam.progress());
        assertNotNull(duringLift);
        assertTrue(player.getCentreY() < capturePose.y(), "the native prefix visibly lifts actual P1");
        assertFalse(level.isLevelInactiveForTransition(), "hide/camera-rise/ICZ is outside this test");
        int endLf = level.getFrameCounter();
        var lifted = registry.capture();

        // Real producer and graph replay: restore every registered key, then
        // repeat controller input/ordinary steps. Measured replay lengths select
        // test boundaries only; they never write a gameplay clock or phase.
        for (int replay = 0; replay < 2; replay++) {
            registry.restore(released);
            assertCompleteWorld(released, registry.capture(), "released restore " + replay);
            for (boolean jump : jumpInputs) right(fixture, jump);
            for (int i = 0; i < admissionWaitEntries; i++) idle(fixture);
            assertCompleteWorld(admitted, registry.capture(), "real beam admission replay " + replay);
            for (int i = admissionLf; i < buildLf; i++) idle(fixture);
            assertCompleteWorld(duringBuild, registry.capture(), "build replay " + replay);
            registry.restore(duringBuild);
            assertCompleteWorld(duringBuild, registry.capture(), "build graph restore " + replay);
            for (int i = buildLf; i < beforeCaptureLf; i++) idle(fixture);
            assertCompleteWorld(beforeCapture, registry.capture(), "capture boundary replay " + replay);
            idle(fixture);
            assertCompleteWorld(captured, registry.capture(), "capture replay " + replay);
            for (int i = captureLf; i < duringLiftLf; i++) idle(fixture);
            assertCompleteWorld(duringLift, registry.capture(), "lift prefix replay " + replay);
            registry.restore(duringLift);
            assertCompleteWorld(duringLift, registry.capture(), "lift graph restore " + replay);
            for (int i = duringLiftLf; i < endLf; i++) idle(fixture);
            assertCompleteWorld(lifted, registry.capture(), "lift completion replay " + replay);
        }
        System.out.println("CNZ2 native charge prefix ordinaryTravel=" + ordinaryTravel
                + " travelEntries=" + walkEntries + " jumpXs=" + jumpXs
                + " artLandingEntries=" + admissionWaitEntries + " admissionLF=" + admissionLf
                + " buildLF=" + buildLf + " captureLF=" + captureLf + " liftEndLF=" + endLf
                + " registeredKeys=" + lifted.entries().size() + " worldReplayCycles=2");
    }

    private HeadlessTestFixture releaseFromActualCapsule() throws Exception {
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .startPosition((short) 0x48C0, (short) 0x0B40)
                .startPositionIsCentre().build();
        var player = assertInstanceOf(Knuckles.class, fixture.sprite());
        var objects = GameServices.level().getObjectManager();
        assertEquals(Sonic3kCNZEvents.FG_ACT2_ENTRY, cnzEvents().getForegroundRoutine());
        idle(fixture);
        assertEquals(Sonic3kCNZEvents.FG_ACT2_KNUCKLES_ROUTE, cnzEvents().getForegroundRoutine());
        assertEquals(1, objects.activeObjectsOfType(CnzTeleporterInstance.class).size());
        var capsules = objects.activeObjectsOfType(CnzEggCapsuleInstance.class);
        assertEquals(1, capsules.size());
        var capsule = capsules.getFirst();
        assertEquals(0x4980, capsule.getX());
        assertEquals(0x0A20, capsule.getY());

        // INHERITED PUBLIC POSITIONED-BUTTON INLET, before any results owner:
        // original movement/SolidObjectFull must land on the actual button.
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
        for (int frame = 0; frame < 32 && !objects.isRidingObject(player, capsule); frame++) idle(fixture);
        assertTrue(objects.isRidingObject(player, capsule),
                "prerequisite: real falling movement must land on the event-admitted capsule");
        assertEquals(1, objects.getRidingPieceIndex(player), "the contacted solid is the real button");
        assertFalse(player.getAir());
        for (int frame = 0; frame < 96
                && objects.activeObjectsOfType(S3kResultsScreenObjectInstance.class).isEmpty(); frame++) {
            idle(fixture);
        }
        var results = objects.activeObjectsOfType(S3kResultsScreenObjectInstance.class);
        assertEquals(1, results.size(), "prerequisite: actual button/delay/grounded checks must create results");
        assertTrue(player.isObjectControlled());
        assertTrue(player.isControlLocked());
        var result = results.getFirst();
        for (int frame = 0; frame < 2000 && !result.isDestroyed(); frame++) {
            idle(fixture);
            assertFalse(player.getDead(), "prerequisite: real results execution retains live P1");
        }
        assertTrue(result.isDestroyed(), "prerequisite: original results tally/slides/waits must exit");
        assertTrue(objects.activeObjectsOfType(S3kResultsScreenObjectInstance.class).isEmpty());
        assertEquals(Sonic3kCNZEvents.FG_ACT2_NORMAL, cnzEvents().getForegroundRoutine());
        assertEquals(0x49A0, fixture.camera().getMaxXTarget() & 0xFFFF);
        assertFalse(player.isObjectControlled());
        assertFalse(player.isControlLocked());
        assertTrue(GameServices.gameState().isEndOfLevelFlag());
        return fixture;
    }

    private static void assertNativeCapture(AbstractPlayableSprite player) {
        assertTrue(player.isObjectControlled());
        assertTrue(player.isObjectControlAllowsCpu(), "native object_control=1 leaves the bit7 gate clear");
        assertTrue(player.isObjectControlSuppressesMovement());
        assertFalse(player.isTouchResponseSuppressedByObjectControl(),
                "Knuckles' native object_control mask $A0 still permits touch for control byte1");
        assertTrue(player.isControlLocked());
        assertTrue(player.getRolling());
        assertEquals(Sonic3kAnimationIds.ROLL.id(), player.getAnimationId());
    }

    private static Pose pose(AbstractPlayableSprite player) {
        return new Pose(player.getCentreX(), player.getCentreY(), player.getXSubpixelRaw(),
                player.getYSubpixelRaw(), player.getXSpeed(), player.getYSpeed(), player.getGSpeed(),
                player.getXRadius(), player.getYRadius(), player.getSkidding(),
                player.getSkidDustTimer(), player.isFixedSkidDustActive());
    }

    private record Pose(int x, int y, int xSubpixel, int ySubpixel, int xSpeed, int ySpeed,
                        int gSpeed, int xRadius, int yRadius, boolean skidding,
                        int skidDustTimer, boolean fixedSkidDustActive) {
        Pose withPosition(int x, int y) {
            return new Pose(x, y, xSubpixel, ySubpixel, xSpeed, ySpeed, gSpeed, xRadius, yRadius,
                    skidding, skidDustTimer, fixedSkidDustActive);
        }
    }

    private static Sonic3kCNZEvents cnzEvents() {
        return assertInstanceOf(Sonic3kLevelEventManager.class,
                GameServices.module().getLevelEventProvider()).getCnzEvents();
    }

    private static void assertCompleteWorld(CompositeSnapshot expected, CompositeSnapshot actual, String context) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), context + " all registered keys");
        for (String key : expected.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.keyEquals(key, expected.entries().get(key), actual.entries().get(key)),
                    () -> context + " " + RewindSnapshotDiff.diffKey(
                            key, expected.entries().get(key), actual.entries().get(key)));
        }
    }

    private static void right(HeadlessTestFixture fixture, boolean jump) {
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME,
                fixture.runner().stepFrame(false, false, false, true, jump));
    }

    private static void idle(HeadlessTestFixture fixture) {
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME,
                fixture.runner().stepFrame(false, false, false, false, false));
    }
}
