package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.FbzDezPlayerLauncherObjectInstance;
import com.openggf.game.sonic3k.objects.S3kDezGravityTubeObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Declared component contacts, never trace hydration: execute the real CPU latch owner. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezGravityTubeInteractWord {
    private static final int X = 0x1200, Y = 0x0800;

    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @ParameterizedTest
    @ValueSource(ints = {0x48, 0x98})
    void offscreenChangedWordDespawnsAndRepeatsAfterTubeRecreation(int subtype) {
        var tails = player();
        var manager = GameServices.level().getObjectManager();
        var launcher = new FbzDezPlayerLauncherObjectInstance(
                new ObjectSpawn(X, Y, 0x78, 0, 0, false, Y, -1));
        manager.addDynamicObject(launcher);
        tails.setLatchedSolidObject(0x78, launcher);
        tails.setOnObject(true);
        tails.setRenderFlagOnScreen(true);
        tails.getCpuController().update(1);
        assertEquals(3, tails.getCpuController().getDiagnosticInteractId());
        tails.setOnObject(false);
        var tube = tube(subtype);
        position(tails);
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var beforeMount = registry.capture();
        int slot = tube.getSlotIndex();
        mount(tails, tube);
        var mounted = registry.capture();
        consumeChangedWord(tails, tube);
        var expected = registry.capture();

        manager.setRewindInPlaceRestoreEnabledForTest(false);
        manager.removeDynamicObject(tube);
        registry.restore(beforeMount);
        var recreated = manager.activeObjectsOfType(S3kDezGravityTubeObjectInstance.class).stream()
                .filter(t -> t.getSlotIndex() == slot).findFirst().orElseThrow();
        assertNotSame(tube, recreated);
        mount(tails, recreated);
        consumeChangedWord(tails, recreated);
        assertSameWorld(expected, registry.capture());

        manager.removeDynamicObject(recreated);
        registry.restore(mounted);
        var restoredMounted = manager.activeObjectsOfType(S3kDezGravityTubeObjectInstance.class).stream()
                .filter(t -> t.getSlotIndex() == slot).findFirst().orElseThrow();
        assertNotSame(recreated, restoredMounted);
        assertSame(restoredMounted, tails.getLatchedSolidObjectInstance());
        assertEquals(slot, tails.getInteractSlotIndex());
        consumeChangedWord(tails, restoredMounted);
        assertSameWorld(expected, registry.capture());
    }

    @ParameterizedTest
    @ValueSource(ints = {0x48, 0x98})
    void visibleTubeRefreshesWordAndOffscreenSameWordSurvives(int subtype) {
        var tails = player();
        var tube = tube(subtype);
        mount(tails, tube);
        tails.setRenderFlagOnScreen(true);
        tails.getCpuController().update(2);
        assertEquals(4, tails.getCpuController().getDiagnosticInteractId(),
                "sub_13EFC refreshes from the live tube only at the next CPU pass");
        assertEquals(SidekickCpuController.State.NORMAL, tails.getCpuController().getState());
        tails.setRenderFlagOnScreen(false);
        tails.getCpuController().update(3);
        assertEquals(SidekickCpuController.State.NORMAL, tails.getCpuController().getState(),
                "an offscreen same-word ride must not despawn");
        assertNotEquals(0x7F00, tails.getCentreX() & 0xFFFF);
        assertEquals(4, tails.getCpuController().getDiagnosticInteractId());
        assertEquals(1, tails.getCpuController().getDiagnosticRespawnCounter());
    }

    @ParameterizedTest
    @ValueSource(ints = {0x48, 0x98})
    void retiredTubeSlotReadsZeroAndDespawns(int subtype) {
        var tails = player();
        var tube = tube(subtype);
        mount(tails, tube);
        tails.setRenderFlagOnScreen(true);
        tails.getCpuController().update(2);
        assertEquals(4, tails.getCpuController().getDiagnosticInteractId());
        int slot = tube.getSlotIndex();
        GameServices.level().getObjectManager().removeDynamicObject(tube);
        assertEquals(slot, tails.getInteractSlotIndex());
        tails.setRenderFlagOnScreen(false);
        tails.getCpuController().update(3);
        assertEquals(0x7F00, tails.getCentreX() & 0xFFFF);
        assertEquals(4, tails.getCpuController().getDiagnosticInteractId(),
                "marker entry preserves the prior word after the live SST becomes empty");
    }

    private void consumeChangedWord(AbstractPlayableSprite tails, S3kDezGravityTubeObjectInstance tube) {
        tails.setRenderFlagOnScreen(false);
        int vx = tails.getXSpeed(), vy = tails.getYSpeed(), gs = tails.getGSpeed();
        int xs = tails.getXSubpixelRaw(), ys = tails.getYSubpixelRaw();
        tails.getCpuController().update(2);
        assertEquals(0x7F00, tails.getCentreX() & 0xFFFF,
                "sub_13EFC compares launcher word3 with tube word4 before refreshing");
        assertEquals(0, tails.getCentreY() & 0xFFFF);
        assertEquals(2, tails.getCpuController().getDiagnosticRomCpuRoutine());
        assertEquals(3, tails.getCpuController().getDiagnosticInteractId());
        assertEquals(0, tails.getCpuController().getDiagnosticRespawnCounter());
        assertEquals(vx, tails.getXSpeed());
        assertEquals(vy, tails.getYSpeed());
        assertEquals(gs, tails.getGSpeed());
        assertEquals(xs, tails.getXSubpixelRaw());
        assertEquals(ys, tails.getYSubpixelRaw());
        assertTrue(tails.getAir());
        assertFalse(tails.isOnObject());
        tube.update(3, GameServices.sprites().getMainPlayable());
        assertFalse(tube.isRidingForTest(false), "the later object pass retires its rider");
    }

    private void mount(AbstractPlayableSprite tails, S3kDezGravityTubeObjectInstance tube) {
        int priorWord = tails.getCpuController().getDiagnosticInteractId();
        tube.update(1, GameServices.sprites().getMainPlayable());
        assertSame(tube, tails.getLatchedSolidObjectInstance());
        assertEquals(tube.getSlotIndex(), tails.getInteractSlotIndex());
        assertTrue(tails.isOnObject());
        assertEquals(priorWord, tails.getCpuController().getDiagnosticInteractId(),
                "mount must not prematurely publish the CPU word");
    }

    private AbstractPlayableSprite player() {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        HeadlessTestFixture.builder().withZoneAndAct(11, 0).build();
        var leader = GameServices.sprites().getMainPlayable();
        NativePositionOps.writeXPosResetSubpixel(leader, X - 0x200);
        NativePositionOps.writeYPosResetSubpixel(leader, Y);
        var tails = GameServices.sprites().getRegisteredSidekicks().getFirst();
        tails.getCpuController().setInitialState(SidekickCpuController.State.NORMAL);
        position(tails);
        return tails;
    }

    private void position(AbstractPlayableSprite tails) {
        NativePositionOps.writeXPosResetSubpixel(tails, X);
        NativePositionOps.writeYPosResetSubpixel(tails, Y);
        tails.setSubpixelRaw(0x1234, 0x5678);
        tails.setXSpeed((short) -0x280);
        tails.setYSpeed((short) 0);
        tails.setGSpeed((short) -0x280);
        tails.setAir(false);
        tails.setOnObject(false);
        ObjectControlState.none().applyTo(tails);
    }

    private S3kDezGravityTubeObjectInstance tube(int subtype) {
        var tube = new S3kDezGravityTubeObjectInstance(new ObjectSpawn(X, Y, 0x5A, subtype, 0, false, Y, -1));
        GameServices.level().getObjectManager().addDynamicObject(tube);
        return tube;
    }

    private void assertSameWorld(com.openggf.game.rewind.CompositeSnapshot expected,
                                 com.openggf.game.rewind.CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key)),
                    () -> key + ": " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        }
    }
}
