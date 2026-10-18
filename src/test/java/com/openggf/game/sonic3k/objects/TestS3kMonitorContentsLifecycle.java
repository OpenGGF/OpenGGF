package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RomWorldPositionedObject;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Obj_MonitorSpawnIcon allocates Obj_MonitorContents as an independent SST
 * object (docs/skdisasm/sonic3k.asm:40640-40652). The broken shell is later
 * deleted offscreen by Sprite_OnScreen_Test (loc_1D61A), while the contents
 * keep rising, award through loc_1D850 and delete only from loc_1DB2E's timer.
 */
class TestS3kMonitorContentsLifecycle {
    private static final int NEAR_CAMERA = 0;
    private static final int FAR_CAMERA = 0x2000;
    /** sub_1D820: -$300 rises by $18 for 32 moves; the 33rd pass awards. */
    private static final int UPRIGHT_AWARD_PASS = 33;
    /** loc_1D83C branches on BMI, so the zero-speed pass moves once more. */
    private static final int INVERTED_AWARD_PASS = 34;
    /** loc_1D850 sets anim_frame_timer to 29; loc_1DB2E deletes when it goes negative. */
    private static final int DELETE_AFTER_AWARD = 30;

    @BeforeEach
    void setup() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
    }

    @AfterEach
    void cleanup() {
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    @Test
    void shellUnloadedOffscreenDoesNotCancelTheRisingRingPayout() {
        Fixture f = Fixture.create(0);
        f.breakShell();
        for (int pass = 1; pass <= 10; pass++) f.update(NEAR_CAMERA, pass);
        f.update(FAR_CAMERA, 11);
        assertEquals(0, f.liveShells(), "the broken shell must have unloaded with the camera away");
        assertEquals(1, f.liveContents(), "independent contents must survive the shell unload");
        for (int pass = 12; pass < UPRIGHT_AWARD_PASS; pass++) f.update(FAR_CAMERA, pass);
        verify(f.player, never()).addRings(anyInt());
        f.update(FAR_CAMERA, UPRIGHT_AWARD_PASS);
        verify(f.player, times(1)).addRings(10);
        for (int pass = UPRIGHT_AWARD_PASS + 1; pass < UPRIGHT_AWARD_PASS + DELETE_AFTER_AWARD; pass++) {
            f.update(FAR_CAMERA, pass);
        }
        assertEquals(1, f.liveContents(), "contents wait out loc_1DB2E's timer");
        f.update(FAR_CAMERA, UPRIGHT_AWARD_PASS + DELETE_AFTER_AWARD);
        assertEquals(0, f.liveContents(), "contents delete when the award timer goes negative");
        verify(f.player, times(1)).addRings(10);
    }

    @Test
    void invertedContentsAwardAfterTheZeroSpeedPassWithTheShellUnloaded() {
        Fixture f = Fixture.create(2);
        f.breakShell();
        f.update(FAR_CAMERA, 1);
        assertEquals(0, f.liveShells(), "the broken shell must have unloaded with the camera away");
        for (int pass = 2; pass < INVERTED_AWARD_PASS; pass++) f.update(FAR_CAMERA, pass);
        verify(f.player, never()).addRings(anyInt());
        f.update(FAR_CAMERA, INVERTED_AWARD_PASS);
        verify(f.player, times(1)).addRings(10);
    }

    @Test
    void loadedShellStillPaysExactlyOnceAtTheNativePass() {
        Fixture f = Fixture.create(0);
        f.breakShell();
        for (int pass = 1; pass < UPRIGHT_AWARD_PASS; pass++) f.update(NEAR_CAMERA, pass);
        verify(f.player, never()).addRings(anyInt());
        f.update(NEAR_CAMERA, UPRIGHT_AWARD_PASS);
        for (int pass = UPRIGHT_AWARD_PASS + 1; pass <= 80; pass++) f.update(NEAR_CAMERA, pass);
        assertEquals(1, f.liveShells(), "control: the broken shell stays loaded near the camera");
        assertEquals(0, f.liveContents());
        verify(f.player, times(1)).addRings(10);
    }

    @Test
    void carriedContentsMoveTheirNativeWordsAndKeepTheRiseSubpixel() throws Exception {
        Fixture f = Fixture.create(0);
        f.breakShell();
        for (int pass = 1; pass <= 5; pass++) f.update(NEAR_CAMERA, pass);
        ObjectInstance contents = f.manager.getActiveObjects().stream()
                .filter(object -> "MonitorContents".equals(object.getName()))
                .findFirst().orElseThrow();
        assertTrue(contents instanceof RomWorldPositionedObject,
                "render_flags bit 2 contents need the native transition-offset contract");
        var subY = iconSubY(contents);
        int x = contents.getX();
        ((RomWorldPositionedObject) contents).offsetNativePositionWordsPreserveSubpixel(0x100, -0x40);
        assertEquals(x + 0x100, contents.getX());
        assertEquals((subY >> 8) - 0x40, contents.getY());
        assertEquals(subY & 0xFF, iconSubY(contents) & 0xFF, "the y subpixel byte is preserved");
        for (int pass = 6; pass < UPRIGHT_AWARD_PASS; pass++) f.update(NEAR_CAMERA, pass);
        verify(f.player, never()).addRings(anyInt());
        f.update(NEAR_CAMERA, UPRIGHT_AWARD_PASS);
        verify(f.player, times(1)).addRings(10);
    }

    private static int iconSubY(ObjectInstance contents) throws Exception {
        var field = com.openggf.level.objects.AbstractMonitorObjectInstance.class.getDeclaredField("iconSubY");
        field.setAccessible(true);
        return field.getInt(contents);
    }

    @Test
    void unloadedShellContentsRewindAndReplayTheirOwnPayout() {
        Fixture f = Fixture.create(0);
        f.breakShell();
        for (int pass = 1; pass <= 10; pass++) f.update(NEAR_CAMERA, pass);
        for (int pass = 11; pass <= 15; pass++) f.update(FAR_CAMERA, pass);
        assertEquals(0, f.liveShells());
        var adapter = f.manager.rewindSnapshottable();
        var captured = adapter.capture();
        for (int pass = 16; pass <= 70; pass++) f.update(FAR_CAMERA, pass);
        var finished = adapter.capture();
        verify(f.player, times(1)).addRings(10);
        assertEquals(0, f.liveContents());
        clearInvocations(f.player);

        adapter.restore(captured);
        assertEquals(List.of(), RewindSnapshotDiff.diffKey("object-manager", captured, adapter.capture()));
        assertEquals(1, f.liveContents(), "restore recreates the independent contents without a shell");
        for (int pass = 16; pass <= 70; pass++) f.update(FAR_CAMERA, pass);
        assertEquals(List.of(), RewindSnapshotDiff.diffKey("object-manager", finished, adapter.capture()));
        verify(f.player, times(1)).addRings(10);

        adapter.restore(captured);
        clearInvocations(f.player);
        for (int pass = 16; pass < UPRIGHT_AWARD_PASS; pass++) f.update(FAR_CAMERA, pass);
        verify(f.player, never()).addRings(anyInt());
        f.update(FAR_CAMERA, UPRIGHT_AWARD_PASS);
        verify(f.player, times(1)).addRings(10);
    }

    private record Fixture(ObjectManager manager, AbstractPlayableSprite player, ObjectSpawn spawn) {
        static Fixture create(int renderFlags) {
            var spawn = new ObjectSpawn(100, 100, 1, 3, renderFlags, false, 100, 0);
            var player = mock(AbstractPlayableSprite.class);
            when(player.getCode()).thenReturn("sonic");
            when(player.getAnimationId()).thenReturn(Sonic3kAnimationIds.ROLL.id());
            var sprites = mock(SpriteManager.class);
            when(sprites.getSprite("sonic")).thenReturn(player);
            var camera = mock(Camera.class);
            when(camera.getWidth()).thenReturn((short) 320);
            when(camera.getHeight()).thenReturn((short) 224);
            var services = new TestObjectServices().withSpriteManager(sprites).withCamera(camera);
            var registry = new ObjectRegistry() {
                @Override public ObjectInstance create(ObjectSpawn source) {
                    return new Sonic3kMonitorObjectInstance(source);
                }
                @Override public void reportCoverage(List<ObjectSpawn> sources) { }
                @Override public String getPrimaryName(int id) { return "Monitor"; }
                @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_3K; }
            };
            var manager = new ObjectManager(List.of(spawn), registry, 0, null, null, null, camera, services);
            services.withDirectObjectManager(manager);
            manager.reset(NEAR_CAMERA);
            manager.update(NEAR_CAMERA, player, List.of(), 0);
            return new Fixture(manager, player, spawn);
        }

        void breakShell() {
            var shell = manager.getActiveObjects().stream()
                    .filter(Sonic3kMonitorObjectInstance.class::isInstance)
                    .map(Sonic3kMonitorObjectInstance.class::cast)
                    .findFirst().orElseThrow();
            shell.onTouchResponse(player, new TouchResponseResult(0, 14, 14, TouchCategory.SPECIAL), 0);
            assertTrue(manager.isRemembered(spawn), "breaking marks the native respawn bit");
        }

        void update(int cameraX, int pass) {
            manager.update(cameraX, player, List.of(), pass);
        }

        long liveShells() {
            return manager.getActiveObjects().stream()
                    .filter(Sonic3kMonitorObjectInstance.class::isInstance)
                    .filter(object -> !object.isDestroyed())
                    .count();
        }

        long liveContents() {
            return manager.getActiveObjects().stream()
                    .filter(object -> "MonitorContents".equals(object.getName()))
                    .filter(object -> !object.isDestroyed())
                    .count();
        }
    }
}
