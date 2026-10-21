package com.openggf.game.mode;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.physics.Direction;
import com.openggf.sprites.NativePositionOps;
import com.openggf.stageplay.StageplayTimeline.ActorPose;
import com.openggf.stageplay.StageplayTimeline.CameraPose;
import com.openggf.stageplay.runtime.StageplayNativeBinding;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Actual native player/camera direction, independent of the staged sprite renderer. */
@RequiresRom(SonicGame.SONIC_3K)
class TestStageplayNativeBinding {
    private SharedLevel bootstrap;
    private com.openggf.game.session.GameplayModeContext runtime;

    @AfterEach void close() {
        if (bootstrap != null) bootstrap.dispose();
        SonicConfigurationService.getInstance().clearSessionOverrides();
    }

    private CourseControl course() throws Exception {
        bootstrap = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        runtime = HeadlessTestFixture.builder().withZoneAndAct(0, 0).build().runtime();
        var player = runtime.getCamera().getFocusedSprite();
        player.setControlLocked(false);
        player.clearQueuedControlState();
        return new CourseControl(runtime);
    }

    private static ActorPose pose(double x, double y) {
        return new ActorPose("hero", "s3k", "sonic", x, y, 0, 12, 1, true, true, 1);
    }

    @Test void directsTheRealNativePlayerAndCancelRestoresItsEntryWithoutRestoringWorldOrAudio() throws Exception {
        var course = course();
        var player = runtime.getCamera().getFocusedSprite();
        NativePositionOps.writeXPosResetSubpixel(player, 400);
        NativePositionOps.writeYPosResetSubpixel(player, 300);
        player.setXSpeed((short) 123);
        player.setYSpeed((short) -44);
        var original = player.captureRewindState();
        var view = runtime.getCamera().capture();
        var audio = GameServices.audio().captureLogicalSnapshot();
        var tails = GameServices.module().getPlayableCharacterRegistry()
                .find(com.openggf.game.CharacterKey.parsePersisted("tails")).orElseThrow()
                .spriteFactory().create("tails", (short) 100, (short) 100);
        runtime.getSpriteManager().addSprite(tails);
        var binding = new StageplayNativeBinding("hero");
        binding.captureEntry(course);
        binding.apply(course, pose(500, 350), new CameraPose(340, 240, 1), 1);
        assertEquals(500, player.getCentreX());
        assertEquals(350, player.getCentreY());
        assertEquals(1, player.getMappingFrame());
        assertEquals(Direction.LEFT, player.getDirection());
        assertTrue(player.isObjectMappingFrameControl());
        assertTrue(player.isControlLocked());
        assertEquals(0, player.getXSpeed());
        assertEquals(340, runtime.getCamera().getX());
        assertEquals(240, runtime.getCamera().getYCopy());
        NativePositionOps.writeXPosResetSubpixel(tails, 777);
        binding.cancel(course);
        assertEquals(original.playerExtra().xPixel(), player.getX());
        assertEquals(original.playerExtra().yPixel(), player.getY());
        assertEquals(123, player.getXSpeed());
        assertEquals(-44, player.getYSpeed());
        assertEquals(original.playerExtra().mappingFrame(), player.getMappingFrame());
        assertFalse(player.isControlLocked());
        assertEquals(view, runtime.getCamera().capture());
        assertEquals(audio.presentation().activeMusic(), GameServices.audio().captureLogicalSnapshot().presentation().activeMusic());
        assertEquals(777, tails.getCentreX(), "player handover never restores the other actors");
        assertNull(binding.capture().entry());
    }

    @Test void finalPositionReleaseRestoresGameplayFlagsAndStartsUnsupportedWithZeroMotion() throws Exception {
        var course = course();
        var player = runtime.getCamera().getFocusedSprite();
        var binding = new StageplayNativeBinding("hero");
        binding.captureEntry(course);
        binding.apply(course, pose(544, 288), new CameraPose(384, 176, 1), 1);
        binding.finish(course, StageplayNativeBinding.FinishMode.KEEP_FINAL_POSITION);
        assertEquals(544, player.getCentreX());
        assertEquals(288, player.getCentreY());
        assertTrue(player.getAir());
        assertFalse(player.isOnObject());
        assertNull(runtime.getLevelManager().getObjectManager().getRidingObject(player));
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getYSpeed());
        assertEquals(0, player.getGSpeed());
        assertFalse(player.isControlLocked());
        assertFalse(player.isObjectMappingFrameControl());
        assertEquals(0, player.getLogicalInputState());
        assertFalse(player.isJumpJustPressed());
        assertEquals(384, runtime.getCamera().getX());
        assertEquals(176, runtime.getCamera().getY());
    }

    @Test void incompatibleTransformsAndCharacterReferencesFailBeforeNativeMutation() throws Exception {
        var course = course();
        var binding = new StageplayNativeBinding("hero");
        binding.captureEntry(course);
        var before = course.playerState();
        assertThrows(IllegalArgumentException.class, () -> binding.apply(course,
                new ActorPose("hero", "s3k", "sonic", 500, 300, 0, 0, 0, false, true, 2),
                new CameraPose(0, 0, 1), 0));
        assertThrows(IllegalArgumentException.class, () -> binding.apply(course,
                new ActorPose("hero", "s2", "sonic", 500, 300, 0, 0, 0, false, true, 1),
                new CameraPose(0, 0, 1), 0));
        assertThrows(IllegalArgumentException.class, () -> binding.apply(course, pose(100_000, 300),
                new CameraPose(0, 0, 1), 0));
        assertThrows(IllegalArgumentException.class, () -> binding.apply(course, pose(500, 300),
                new CameraPose(0, 0, 1), 65_535));
        assertEquals(before, course.playerState());
    }

    @Test void presentationZoomDoesNotChangeNativeCameraCoordinatesOrReleasePosition() throws Exception {
        var course = course();
        var binding = new StageplayNativeBinding("hero");
        binding.captureEntry(course);
        binding.apply(course, pose(500, 300), new CameraPose(340, 180, 2.5), 1);
        binding.finish(course, StageplayNativeBinding.FinishMode.KEEP_FINAL_POSITION);
        assertEquals(500, course.playerState().x());
        assertEquals(300, course.playerState().y());
        assertEquals(340, course.playerState().cameraX());
        assertEquals(180, course.playerState().cameraY());
    }

    @Test void closedFrameCapabilitiesAndReloadedLevelVisitsCannotApplyOrReleaseOldState() throws Exception {
        var course = course();
        var binding = new StageplayNativeBinding("hero");
        binding.captureEntry(course);
        course.closeFrame();
        assertThrows(IllegalStateException.class, () -> binding.apply(course, pose(500, 300), new CameraPose(0, 0, 1), 0));
        var nextFrame = new CourseControl(runtime);
        assertTrue(binding.current(nextFrame));
        nextFrame.loadLevel(0, 0);
        assertFalse(binding.current(nextFrame), "reloading the same zone is a new visit");
        var reloaded = nextFrame.playerState();
        assertThrows(IllegalArgumentException.class, () -> binding.cancel(nextFrame));
        assertEquals(reloaded, nextFrame.playerState());
        binding.abandon();
    }

    @Test void ownershipStatesAreRejectedAndSnapshotRestoreDoesNotMutateGameplay() throws Exception {
        var course = course();
        var player = runtime.getCamera().getFocusedSprite();
        var binding = new StageplayNativeBinding("hero");
        player.setControlLocked(true);
        assertThrows(IllegalStateException.class, () -> binding.captureEntry(course));
        player.setControlLocked(false);
        player.setOnObject(true);
        assertThrows(IllegalStateException.class, () -> binding.captureEntry(course));
        player.setOnObject(false);
        binding.captureEntry(course);
        var state = binding.capture();
        var lease = runtime.directedPlayerLease().capture();
        binding.apply(course, pose(500, 300), new CameraPose(340, 188, 1), 1);
        binding.cancel(course);
        var entry = course.playerState();
        binding.restore(state);
        runtime.directedPlayerLease().restore(lease);
        assertEquals(entry, course.playerState(), "rewind restores binding state without invoking gameplay owners");
        binding.apply(course, pose(500, 300), new CameraPose(340, 188, 1), 1);
        binding.cancel(course);
        assertEquals(entry, course.playerState());
    }

    @Test void unrelatedOwnerQuarantineRestoresEntryAndHistoricalFaultedLeaseCannotRelockPlayer() throws Exception {
        var course = course();
        var player = runtime.getCamera().getFocusedSprite();
        var original = course.playerState();
        var binding = new StageplayNativeBinding("hero");
        binding.captureEntry(course);
        binding.apply(course, pose(500, 300), new CameraPose(340, 188, 1), 1);
        var lease = runtime.directedPlayerLease().capture();
        var lockedPlayer = player.captureRewindState();
        var boundary = new com.openggf.mods.code.ModFaultBoundary(java.util.Map.of(),
                new com.openggf.mods.ModRuntimeFindingStore(), ignored -> new com.openggf.mods.ModStateSaveResult.Saved(),
                ignored -> { });
        assertThrows(com.openggf.mods.code.ModFaultBoundary.CallbackAborted.class,
                () -> boundary.run("unrelated-owner", () -> { throw new IllegalStateException("fixture callback failed"); }));
        assertEquals(original, course.playerState());
        assertFalse(player.isControlLocked());
        assertFalse(binding.current(course));
        assertFalse(boundary.isOwnerAvailable("unrelated-owner"));
        // Native sprite restoration follows the lease adapter; the post-pass must still release it.
        runtime.directedPlayerLease().restore(lease);
        player.restoreRewindState(lockedPlayer);
        runtime.directedPlayerLease().afterRestore();
        assertEquals(original, course.playerState());
        assertFalse(player.isControlLocked());
        assertNull(runtime.directedPlayerLease().capture().entry());
    }

    @Test void teardownAndUnexpectedControlledFrameFailureReleaseEngineOwnedCustody() throws Exception {
        var course = course();
        var player = runtime.getCamera().getFocusedSprite();
        var original = course.playerState();
        var binding = new StageplayNativeBinding("hero");
        binding.captureEntry(course);
        binding.apply(course, pose(500, 300), new CameraPose(340, 188, 1), 1);
        // Stock module has no controller: failure before creator beforeFrame must still clean up.
        assertThrows(NullPointerException.class, () -> ControlledFrameRuntime.step(runtime, null,
                com.openggf.control.LogicalInputSnapshot.neutral()));
        assertEquals(original, course.playerState());
        assertFalse(player.isControlLocked());
        binding.abandon();
        binding.captureEntry(course);
        binding.apply(course, pose(500, 300), new CameraPose(340, 188, 1), 1);
        runtime.tearDownManagers();
        assertFalse(player.isControlLocked());
        assertFalse(player.isObjectMappingFrameControl());
        assertNull(runtime.directedPlayerLease().capture().entry());
    }
}
