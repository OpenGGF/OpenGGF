package com.openggf.tests;

import com.openggf.audio.AudioManager;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kPlcArtRegistry;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.S3kDezConveyorBeltObjectInstance;
import com.openggf.game.sonic3k.objects.S3kDezTorpedoLauncherObjectInstance;
import com.openggf.game.sonic3k.objects.S3kDezTorpedoObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezTorpedoHeadless {
    @AfterEach void reset() {
        AudioManager.getInstance().setRequestObserver(null);
        AbstractObjectInstance.resetCameraBoundsForTests();
        SessionManager.clear();
    }

    @Test
    void unsignedSubtypeIntervalPausesOffscreenAndFiresAfterSignedExpiry() {
        boot();
        for (int subtype : new int[] {0, 1, 0xFF}) {
            var launcher = launcher(subtype, 0);
            for (int i = 0; i < 9; i++) launcher.update(i, null);
            assertEquals(subtype * 4, launcher.countdownForTest());
            show(launcher);
            for (int i = 0; i < subtype * 4; i++) {
                launcher.update(i, null);
                assertEquals(0, launcher.mappingFrameForTest());
            }
            launcher.update(0, null);
            assertEquals(8, launcher.mappingFrameForTest());
            assertEquals(subtype * 4, launcher.countdownForTest());
        }
    }

    @Test
    void firingFallsIntoThirtyOneTickFirstPoseThenEightTickRecoilPoses() {
        boot();
        var launcher = launcher(0, 0);
        launcher.update(0, null);
        show(launcher);
        launcher.update(1, null);
        assertEquals(8, launcher.mappingFrameForTest());
        // Recoil does not pause when the previous renderer flag becomes clear.
        AbstractObjectInstance.updateCameraBounds(0x1000, 0, 0x1140, 224, 0x800);
        launcher.refreshPostCameraRenderState();
        for (int tick = 1; tick <= 87; tick++) {
            launcher.update(tick, null);
            int expected = tick < 31 ? 8 : 7 - (tick - 31) / 8;
            assertEquals(expected, launcher.mappingFrameForTest(), "recoil tick=" + tick);
        }
        launcher.update(88, null);
        assertEquals(0, launcher.mappingFrameForTest(), "offscreen countdown stays frozen after recoil");
    }

    @Test
    void fullNativeSlotPoolStillRecoilsWithoutSoundOrProjectile() {
        boot();
        var manager = GameServices.level().getObjectManager();
        var launcher = launcher(0, 0);
        manager.addDynamicObject(launcher);
        boolean full = false;
        for (int i = 0; i < 160; i++) {
            var filler = new S3kDezConveyorBeltObjectInstance(spawn(0, 0));
            manager.addDynamicObject(filler);
            if (filler.isDestroyed()) { full = true; break; }
        }
        assertTrue(full, "exercise the actual exhausted SST pool");
        List<Integer> sounds = new ArrayList<>();
        AudioManager.getInstance().setRequestObserver((kind, id) -> sounds.add(id));
        launcher.update(0, null);
        show(launcher);
        launcher.update(1, null);
        assertEquals(8, launcher.mappingFrameForTest());
        assertTrue(sounds.isEmpty());
        assertTrue(manager.getActiveObjects().stream().noneMatch(o -> o instanceof S3kDezTorpedoObjectInstance));
    }

    @Test
    void projectilesMoveInBothDirectionsThenRetireOnTheCarriedRenderFlag() {
        boot();
        for (int flip = 0; flip < 2; flip++) {
            var projectile = new S3kDezTorpedoObjectInstance(spawn(0, flip));
            projectile.setServices(TestEnvironment.objectServices());
            projectile.update(0, null);
            assertEquals(flip == 0 ? 96 : 104, projectile.getX());
            assertTrue(projectile.publishesTouchResponseListEntryThisFrame());
            assertEquals(0x9B, projectile.getCollisionFlags());
            assertTrue(projectile.isPersistent(), "no independent coarse-X retirement tail");
            AbstractObjectInstance.updateCameraBounds(0x1000, 0, 0x1140, 224, 0x800);
            projectile.refreshPostCameraRenderState();
            projectile.update(1, null);
            assertTrue(projectile.isDestroyed());
            assertFalse(projectile.publishesTouchResponseListEntryThisFrame());
        }
    }

    @Test
    void launcherAndIndependentProjectileRestoreAndReplay() {
        boot();
        var manager = GameServices.level().getObjectManager();
        var launcher = launcher(0, 1);
        manager.addDynamicObject(launcher);
        launcher.update(0, null);
        show(launcher);
        launcher.update(1, null);
        var projectile = manager.getActiveObjects().stream().filter(o -> o instanceof S3kDezTorpedoObjectInstance)
                .map(o -> (S3kDezTorpedoObjectInstance) o).findFirst().orElseThrow();
        assertTrue(projectile.getSlotIndex() > launcher.getSlotIndex());
        projectile.update(1, null);
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var before = registry.capture();
        List<String> first = advance(launcher, projectile);
        registry.restore(before);
        var restoredLauncher = manager.getActiveObjects().stream().filter(o -> o instanceof S3kDezTorpedoLauncherObjectInstance)
                .map(o -> (S3kDezTorpedoLauncherObjectInstance) o).findFirst().orElseThrow();
        var restoredProjectile = manager.getActiveObjects().stream().filter(o -> o instanceof S3kDezTorpedoObjectInstance)
                .map(o -> (S3kDezTorpedoObjectInstance) o).findFirst().orElseThrow();
        assertEquals(first, advance(restoredLauncher, restoredProjectile));
    }

    @Test
    void productionSlotWalkMovesTheNewProjectileAndReplaysPlayerDamage() {
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 0)
                .startPosition((short) 0x730, (short) 0x700).startPositionIsCentre().build();
        fixture.sprite().setRingCount(7);
        fixture.sprite().setAir(true);
        int launcherX = fixture.sprite().getCentreX() - 0x20;
        var launcher = new S3kDezTorpedoLauncherObjectInstance(new ObjectSpawn(
                launcherX, fixture.sprite().getCentreY(), 0x4D, 0, 1, false, 0));
        var manager = GameServices.level().getObjectManager();
        manager.addDynamicObject(launcher);
        S3kDezTorpedoObjectInstance projectile = null;
        for (int i = 0; i < 8 && projectile == null; i++) {
            fixture.stepIdleFrames(1);
            projectile = manager.getActiveObjects().stream().filter(o -> o instanceof S3kDezTorpedoObjectInstance)
                    .map(o -> (S3kDezTorpedoObjectInstance) o).findFirst().orElse(null);
        }
        assertNotNull(projectile, "visible launcher must fire through the production object walk");
        assertTrue(projectile.getSlotIndex() > launcher.getSlotIndex());
        assertEquals(launcherX + 4, projectile.getX(), "new later-slot projectile moves on the firing pass");
        assertFalse(fixture.sprite().isHurt());
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var before = registry.capture();
        List<String> first = damageFrames(fixture);
        assertTrue(first.stream().anyMatch(row -> row.contains("hurt=true")));
        assertFalse(fixture.sprite().getDead());
        registry.restore(before);
        assertEquals(first, damageFrames(fixture), "projectile pointer, player hit and recoil replay together");
    }

    private List<String> damageFrames(HeadlessTestFixture fixture) {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            fixture.stepIdleFrames(1);
            var p = fixture.sprite();
            rows.add(p.getCentreX() + "," + p.getCentreY() + ",hurt=" + p.isHurt() + ",rings=" + p.getRingCount());
        }
        return rows;
    }

    @Test
    void tenFrameRomMappingUsesSeparateLauncherAndProjectilePalettes() throws Exception {
        boot();
        var frames = S3kSpriteDataLoader.loadMappingFrames(TestEnvironment.objectServices().romReader(), 0x472A8, 10);
        assertArrayEquals(new int[] {3,3,3,3,3,3,3,3,3,1}, frames.stream().mapToInt(f -> f.pieces().size()).toArray());
        var closedMuzzle = frames.get(0).pieces().get(2);
        var openMuzzle = frames.get(8).pieces().get(2);
        var missile = frames.get(9).pieces().getFirst();
        assertEquals(-8, closedMuzzle.xOffset());
        assertEquals(0, openMuzzle.xOffset());
        assertEquals(2, closedMuzzle.tileIndex());
        assertEquals(1, closedMuzzle.paletteIndex());
        assertEquals(-8, missile.xOffset());
        assertEquals(-8, missile.yOffset());
        assertEquals(2, missile.widthTiles());
        assertEquals(2, missile.heightTiles());
        assertEquals(2, missile.tileIndex());
        assertEquals(0, missile.paletteIndex());
        for (int act = 0; act < 2; act++) {
            for (String key : List.of(Sonic3kObjectArtKeys.DEZ_TORPEDO_LAUNCHER, Sonic3kObjectArtKeys.DEZ_TORPEDO)) {
                var entry = Sonic3kPlcArtRegistry.getPlan(11, act).levelArt().stream()
                        .filter(e -> e.key().equals(key)).findFirst().orElseThrow();
                assertEquals(0x472A8, entry.mappingAddr());
                assertEquals(0x373, entry.artTileBase());
                assertEquals(key.equals(Sonic3kObjectArtKeys.DEZ_TORPEDO) ? 1 : 0, entry.palette());
                assertEquals(10, entry.mappingFrameCount());
            }
        }
    }

    private List<String> advance(S3kDezTorpedoLauncherObjectInstance launcher, S3kDezTorpedoObjectInstance projectile) {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            launcher.update(i, null);
            projectile.update(i, null);
            rows.add(launcher.mappingFrameForTest() + "," + launcher.countdownForTest() + "," + projectile.getX());
        }
        return rows;
    }
    private void show(S3kDezTorpedoLauncherObjectInstance launcher) {
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0x800);
        launcher.refreshPostCameraRenderState();
    }
    private S3kDezTorpedoLauncherObjectInstance launcher(int subtype, int flags) {
        var object = new S3kDezTorpedoLauncherObjectInstance(spawn(subtype, flags));
        object.setServices(TestEnvironment.objectServices());
        return object;
    }
    private ObjectSpawn spawn(int subtype, int flags) { return new ObjectSpawn(100, 100, 0x4D, subtype, flags, false, 100); }
    private void boot() {
        TestEnvironment.activeGameplayMode();
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 0).build();
    }
}
