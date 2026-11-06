package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.S3kDezGravityTubeObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Component inlets for the tube's native anim/prev_anim word; no trace state is loaded. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezGravityTubeAnimation {
    private static final int X = 0x1200;
    private static final int Y = 0x0800;

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @ParameterizedTest
    @CsvSource({"true,72", "false,72", "true,152", "false,152"})
    void mountWritesBothNativeAnimationBytes(boolean mainTails, int subtype) {
        var player = player(mainTails);
        var tube = tube(subtype);
        player.setAnimationId(0x0B);
        player.getAnimationManager().publishPreviousAnimationId(7);
        player.setAir(false);
        tube.update(0, GameServices.sprites().getMainPlayable());
        assertTrue(tube.isRidingForTest(mainTails));
        assertAnimationWord(player);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void zeroSpeedRideDefersPushClearToAnimationAndReplaysAfterRecreation(boolean mainTails) {
        var player = player(mainTails);
        var tube = tube(0x48);
        player.setAir(false);
        tube.update(0, GameServices.sprites().getMainPlayable());
        assertTrue(tube.isRidingForTest(mainTails));
        player.setAnimationId(0);
        player.getAnimationManager().publishPreviousAnimationId(0);
        player.setGSpeed((short) 0);
        player.setPushing(true);
        var manager = GameServices.level().getObjectManager();
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        int slot = tube.getSlotIndex();
        var held = registry.capture();
        armRide(player, tube);
        var pending = registry.capture();
        consumeAndContinue(player, tube);
        var continued = registry.capture();

        manager.setRewindInPlaceRestoreEnabledForTest(false);
        manager.removeDynamicObject(tube);
        registry.restore(held);
        var recreated = manager.activeObjectsOfType(S3kDezGravityTubeObjectInstance.class).stream()
                .filter(t -> t.getSlotIndex() == slot).findFirst().orElseThrow();
        assertNotSame(tube, recreated);
        assertSame(recreated, player.getLatchedSolidObjectInstance());
        assertTrue(recreated.isRidingForTest(mainTails));
        assertTrue(player.getPushing());
        assertEquals(0, player.getGSpeed());
        armRide(player, recreated);
        consumeAndContinue(player, recreated);
        var replayed = registry.capture();
        assertEquals(continued.entries().keySet(), replayed.entries().keySet());
        for (String key : continued.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.keyEquals(key, continued.get(key), replayed.get(key)),
                    () -> key + ": " + RewindSnapshotDiff.diffKey(key, continued.get(key), replayed.get(key)));
        }
        // Restore after the object has written the word, before Animate consumes it.
        manager.removeDynamicObject(recreated);
        registry.restore(pending);
        var pendingTube = manager.activeObjectsOfType(S3kDezGravityTubeObjectInstance.class).stream()
                .filter(t -> t.getSlotIndex() == slot).findFirst().orElseThrow();
        assertNotSame(recreated, pendingTube);
        assertAnimationWord(player);
        assertTrue(player.getPushing());
        consumeAndContinue(player, pendingTube);
        var pendingReplay = registry.capture();
        for (String key : continued.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.keyEquals(key, continued.get(key), pendingReplay.get(key)),
                    () -> key + ": " + RewindSnapshotDiff.diffKey(key, continued.get(key), pendingReplay.get(key)));
        }
    }

    private void armRide(AbstractPlayableSprite player, S3kDezGravityTubeObjectInstance tube) {
        tube.update(1, GameServices.sprites().getMainPlayable());
        assertEquals(1, player.getGSpeed(), "zero-speed sentinel belongs to the tube");
        assertAnimationWord(player);
        assertTrue(player.getPushing(), "the object must retain push until the next Animate pass");
    }

    private void consumeAndContinue(AbstractPlayableSprite player, S3kDezGravityTubeObjectInstance tube) {
        player.getAnimationManager().update(2);
        assertFalse(player.getPushing(), "Animate_Tails consumes anim != prev_anim");

        // Nonzero ground_vel takes loc_4904A without either animation-byte write.
        player.setGSpeed((short) 0x200);
        player.setAnimationId(0x0B);
        player.getAnimationManager().publishPreviousAnimationId(7);
        player.setPushing(true);
        tube.update(3, GameServices.sprites().getMainPlayable());
        assertEquals(0x0B, player.getAnimationId());
        assertEquals(7, player.getAnimationManager().captureRewindState().lastAnimationId());
        assertTrue(player.getPushing());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectedMountLeavesBothAnimationBytesAlone(boolean mainTails) {
        var player = player(mainTails);
        var tube = tube(0x48);
        NativePositionOps.writeXPosResetSubpixel(player, X + 0x40); // exclusive subtype edge
        player.setAnimationId(0x0B);
        player.getAnimationManager().publishPreviousAnimationId(7);
        tube.update(0, GameServices.sprites().getMainPlayable());
        assertFalse(tube.isRidingForTest(mainTails));
        assertEquals(0x0B, player.getAnimationId());
        assertEquals(7, player.getAnimationManager().captureRewindState().lastAnimationId());
    }

    private void assertAnimationWord(AbstractPlayableSprite player) {
        // sub_48F12, loc_49022, loc_49120 all write move.w #1,anim(a1):
        // sonic3k.asm:95292-95296,95359-95365,95448-95454.
        assertEquals(0, player.getAnimationId(), "high byte of the native word is anim=0");
        assertEquals(1, player.getAnimationManager().captureRewindState().lastAnimationId(),
                "low byte is prev_anim=1; retain its next-dispatch side effect");
    }

    private AbstractPlayableSprite player(boolean mainTails) {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, mainTails ? "tails" : "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, mainTails ? "" : "tails");
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(11, 0).build();
        var player = mainTails ? fixture.sprite() : GameServices.sprites().getRegisteredSidekicks().getFirst();
        if (!mainTails) NativePositionOps.writeXPosResetSubpixel(fixture.sprite(), X - 0x200);
        NativePositionOps.writeXPosResetSubpixel(player, X);
        NativePositionOps.writeYPosResetSubpixel(player, Y);
        return player;
    }

    private S3kDezGravityTubeObjectInstance tube(int subtype) {
        var tube = new S3kDezGravityTubeObjectInstance(new ObjectSpawn(X, Y, 0x5A, subtype, 0, false, Y, -1));
        GameServices.level().getObjectManager().addDynamicObject(tube);
        return tube;
    }
}
