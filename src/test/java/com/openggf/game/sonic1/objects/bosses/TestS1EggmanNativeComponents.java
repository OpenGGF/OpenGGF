package com.openggf.game.sonic1.objects.bosses;

import com.openggf.camera.Camera;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameStateManager;
import com.openggf.game.GameRng;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic1.objects.Sonic1ObjectRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_1)
class TestS1EggmanNativeComponents {
    private static final int[] IDS = {0x3D, 0x73, 0x75, 0x77, 0x7A};
    private static final int[] TABLES = {0x17EC2, 0x189F8, 0x198B6, 0x185F8, 0x19080};
    private static final int[] STRIDES = {2, 3, 3, 2, 3};
    private static final int[] COUNTS = {3, 4, 4, 3, 4};

    @BeforeEach void setup() { GraphicsManager.getInstance().initHeadless(); }
    @AfterEach void cleanup() { GraphicsManager.getInstance().resetState(); }

    @Test
    void firstObjectPassBuildsTheRomTableInOrderAndIgnoresLowerHoles() throws Exception {
        RomByteReader rom = RomByteReader.fromRom(TestEnvironment.currentRom());
        for (int i = 0; i < IDS.length; i++) {
            Harness h = new Harness(IDS[i], 60);
            assertEquals(1, h.manager.getAllocatedSlotCount(), "constructor must not allocate children");
            assertEquals(0, h.boss.getCollisionFlags(), "native routine 0 has not enabled ship collision yet");
            h.step();
            assertNotEquals(0, h.boss.getCollisionFlags(), "first ship update enables native collision");
            List<AbstractObjectInstance> objects = h.objects();
            assertEquals(COUNTS[i], objects.size(), "native table size for " + IDS[i]);
            for (int n = 0; n < objects.size(); n++) {
                assertEquals(60 + n, objects.get(n).getSlotIndex(), "FindNext must ignore lower holes");
                int tuple = TABLES[i] + n * STRIDES[i];
                assertEquals(2 + n * 2, rom.readU8(tuple), "independent native routine");
                int priority = STRIDES[i] == 3 ? rom.readU8(tuple + 2) : IDS[i] == 0x3D ? 3 : 4;
                assertEquals(priority, objects.get(n).getPriorityBucket(), "native component priority");
            }
            assertEquals(S1EggmanVisualComponent.FACE, ((S1EggmanVisualComponent) objects.get(1)).getComponent());
            assertEquals(S1EggmanVisualComponent.FLAME, ((S1EggmanVisualComponent) objects.get(2)).getComponent());
            if (IDS[i] == 0x75) assertInstanceOf(SYZBossSpike.class, objects.get(3));
            assertEquals(COUNTS[i], h.manager.getAllocatedSlotCount(), "every native component consumes a real slot");
        }
    }

    @Test
    void fullRamKeepsOnlyTheSuccessfullyAllocatedPrefixAndNeverRetries() {
        for (int i = 0; i < IDS.length; i++) {
            for (int freeAfter = 0; freeAfter < COUNTS[i] - 1; freeAfter++) {
                int firstSlot = 127 - freeAfter;
                Harness h = new Harness(IDS[i], firstSlot);
                h.step();
                assertEquals(1 + freeAfter, h.objects().size(), "full RAM retains successful table prefix");
                assertEquals(1 + freeAfter, h.manager.getAllocatedSlotCount());
                assertTrue(h.objects().stream().allMatch(o -> o.getSlotIndex() >= firstSlot),
                        "must not fall back to free lower SST slots");
                if (freeAfter > 0) h.manager.removeDynamicObject(h.objects().get(1));
                int survivors = h.objects().size();
                h.step();
                assertEquals(survivors, h.objects().size(), "routine 0 never retries failed components");
            }
        }
    }

    @Test
    void romScriptsAdvanceAtDelayPlusOneAndResetOnAnimationChange() throws Exception {
        Harness h = new Harness(0x3D, 60);
        h.step();
        S1EggmanVisualComponent face = h.part(S1EggmanVisualComponent.FACE);
        S1EggmanVisualComponent flame = h.part(S1EggmanVisualComponent.FLAME);
        for (int t = 0; t < 24; t++) {
            assertEquals((t / 6 & 1) == 0 ? 1 : 2, face.getMappingFrame(), "normal face native delay 5");
            assertEquals(10, flame.getMappingFrame(), "ROM blank mapping, not an invented omitted frame");
            h.step();
        }
        h.boss.getState().invulnerable = true;
        face.update(999, null);
        assertEquals(5, face.getMappingFrame(), "new animation loads immediately");
        for (int t = 1; t <= 32; t++) {
            face.update(999, null); // AnimateSprite counts executions, not V-int value changes.
            assertEquals(t < 32 ? 5 : 1, face.getMappingFrame(), "native hit delay 31");
        }
        h.boss.getState().invulnerable = false;
        h.boss.getState().xVel = 0x100;
        for (int t = 0; t < 12; t++) {
            flame.update(999, null);
            assertEquals((t / 4 & 1) == 0 ? 8 : 9, flame.getMappingFrame(), "flame delay 3");
        }
        h.boss.getState().routineSecondary = 12;
        int[] escape = {9, 8, 11, 12, 11, 12, 9, 8, 9, 8, 9, 8};
        for (int expected : escape) {
            for (int tick = 0; tick < 3; tick++) {
                flame.update(999, null);
                assertEquals(expected, flame.getMappingFrame(), "escape loops final two frames after its prefix");
            }
        }
        RomByteReader rom = RomByteReader.fromRom(TestEnvironment.currentRom());
        assertEquals(0x050102FF, rom.readU32BE(rom.readPointer16(0x18468, 1)));
    }

    @Test
    void labyrinthUsesTheShippedFaceLocalFlagAndDeadNormalFlameBranch() throws Exception {
        Harness h = new Harness(0x77, 60);
        h.step();
        h.boss.getState().hitCount = 1;
        h.boss.onPlayerAttack(null, null);
        S1EggmanVisualComponent face = h.part(1), flame = h.part(2);
        face.update(99, null);
        assertEquals(5, face.getAnimation(), "ship early-defeat flag is not face-local $3D");
        h.boss.getState().xVel = 0x400;
        flame.update(99, null);
        assertEquals(7, flame.getAnimation(), "normal velocity test is unreachable in shipped ROM");
        h.boss.getState().routineSecondary = 14;
        face.update(99, null);
        flame.update(99, null);
        assertEquals(6, face.getAnimation());
        assertEquals(11, flame.getAnimation());
        RomByteReader rom = RomByteReader.fromRom(TestEnvironment.currentRom());
        assertEquals(0x4A28003D, rom.readU32BE(0x18932), "native a0 flag read");
        assertEquals(0x661A, rom.readU16BE(0x18984), "native branch skips normal flames");
    }

    @Test
    void componentsReleaseTheirOwnSlotsWhileOffscreenShipStillExpandsCamera() {
        for (int id : IDS) {
            Harness h = new Harness(id, 60);
            h.step();
            h.boss.getState().routineSecondary = h.boss.getNativeEscapeRoutine();
            h.boss.getState().xFixed = (h.camera.getX() + 600) << 16;
            h.boss.getState().x = h.camera.getX() + 600;
            h.camera.setMaxX((short) 0);
            h.step(); // Children had an on-screen previous render, and first follow offscreen.
            h.step(); // Their own previous flag is now clear.
            assertFalse(h.boss.isDestroyed(), "ship must keep expanding its end boundary");
            assertEquals(id == 0x75 ? 1 : 0, h.parts().size(), "SYZ face uses ID, other visual children self-delete");
            assertEquals(id == 0x75 ? 2 : 1, h.manager.getAllocatedSlotCount(), "released children restore actual capacity");
            if (id == 0x75) {
                h.boss.setDestroyed(true);
                h.step();
                assertTrue(h.parts().isEmpty(), "SYZ face deletes when parent ID disappears");
            }
        }
    }

    @Test
    void midScriptRewindRecreatesIdentityAndProducesTheSameForwardFramesWithoutRespawn() {
        for (int id : IDS) {
            Harness h = new Harness(id, 60);
            for (int t = 0; t < 9; t++) h.step();
            h.manager.setRewindInPlaceRestoreEnabledForTest(false);
            RewindRegistry registry = new RewindRegistry();
            registry.register(h.manager.rewindSnapshottable());
            var snapshot = registry.capture();
            List<String> expected = h.forward(20);
            registry.restore(snapshot);
            h.boss = h.objects().stream().filter(AbstractS1EggmanBossInstance.class::isInstance)
                    .map(AbstractS1EggmanBossInstance.class::cast).findFirst().orElseThrow();
            assertEquals(COUNTS[index(id)] - (id == 0x75 ? 2 : 1), h.parts().size(), "recreate every visual component");
            for (var part : h.parts()) assertSame(h.boss, part.getShipOwner(), "exact restored owner");
            assertEquals(expected, h.forward(20), "native timers/cursors/order and slot ownership survive restore");
            assertEquals(COUNTS[index(id)], h.manager.getAllocatedSlotCount(), "no duplicate components after rewind");
        }
    }

    @Test
    void previousRenderFlagComesFromThePostCameraPassAndUsesSupportedWidth() {
        for (int width : new int[]{320, 528}) {
            Harness h = new Harness(0x3D, 60, width);
            h.step();
            var flame = h.part(2);
            // The camera moves AFTER object execution. BuildSprites must clear the
            // old flag using the new camera, even though the object was visible in update().
            flame.update(99, null);
            h.camera.setX((short) (flame.getX() + 33));
            h.manager.refreshPostCameraRenderState();
            h.boss.getState().routineSecondary = 12;
            flame.update(99, null);
            assertTrue(flame.isDestroyed(), "post-camera left-edge departure deletes next pass");

            Harness wide = new Harness(0x3D, 60, width);
            wide.step();
            var face = wide.part(1);
            wide.boss.getState().x = wide.camera.getX() + width - 1;
            face.update(88, null);
            wide.manager.refreshPostCameraRenderState();
            wide.boss.getState().routineSecondary = 12;
            face.update(88, null);
            assertFalse(face.isDestroyed(), "native and extended right edge remain visible");
        }
    }

    @Test
    void specialFaceSelectorsRetainTheirNativeHitAndLaughPrecedence() throws Exception {
        Harness mz = new Harness(0x73, 60);
        mz.step();
        mz.boss.getState().routineSecondary = 2;
        mz.boss.getState().yVel = 0;
        mz.boss.getState().invulnerable = true;
        var subtype = Sonic1MZBossInstance.class.getDeclaredField("combatSubtype");
        subtype.setAccessible(true);
        subtype.setInt(mz.boss, 2);
        mz.part(1).update(90, null);
        assertEquals(4, mz.part(1).getAnimation(), "MZ waiting laugh skips hit test");

        Harness syz = new Harness(0x75, 60);
        syz.step();
        syz.boss.getState().routineSecondary = 4;
        var phase = Sonic1SYZBossInstance.class.getDeclaredField("dropSubPhase");
        phase.setAccessible(true);
        phase.setInt(syz.boss, 2);
        syz.boss.getState().invulnerable = true;
        syz.part(1).update(90, null);
        assertEquals(5, syz.part(1).getAnimation(), "SYZ hold panic still falls through hit check");
        syz.boss.getState().invulnerable = false;
        var player = new com.openggf.tests.TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setHurt(true);
        syz.part(1).update(90, player);
        assertEquals(4, syz.part(1).getAnimation(), "SYZ hurt-player laugh overrides hold panic");
    }

    @Test
    void shipBurstUsesNativeDownwardOffsetsFiveFramesAndAllocationBeforeRng() {
        Harness h = new Harness(0x3D, 60);
        h.step();
        // RandomNumber seed $100 yields $2900: X=(0>>2)-32, Y=$29>>3=5.
        h.rng.setSeed(0x100);
        h.boss.spawnDefeatExplosion();
        S1BossExplosion explosion = h.objects().stream().filter(S1BossExplosion.class::isInstance)
                .map(S1BossExplosion.class::cast).findFirst().orElseThrow();
        assertEquals((h.boss.getX() - 32) & 0xFFFF, explosion.getX());
        assertEquals(h.boss.getY() + 5, explosion.getY(), "native Y has no upward subtraction");
        explosion.update(0, null); // Expl_Main initializes without animate/display fallthrough.
        for (int pass = 1; pass < 40; pass++) {
            explosion.update(pass, null);
            assertFalse(explosion.isDestroyed(), "native five eight-pass frames retain the SST");
        }
        explosion.update(40, null);
        assertTrue(explosion.isDestroyed(), "Obj3F deletes at mapping frame 5, not S2 frame 7");

        Harness full = new Harness(0x3D, 127);
        full.step();
        full.manager.reserveAllButNFreeSlots(0);
        full.rng.setSeed(0x100);
        full.boss.spawnDefeatExplosion();
        assertEquals(0x100, full.rng.getSeed(), "failed FindFreeObj consumes no random draw");
        assertTrue(full.objects().stream().noneMatch(S1BossExplosion.class::isInstance));
    }

    @Test
    void labyrinthDefeatUsesThisObjectPassClockRatherThanThePreviousCompletedPass() {
        Harness h = new Harness(0x77, 60);
        h.step();
        h.boss.getState().hitCount = 1;
        h.boss.onPlayerAttack(null, null);
        h.boss.update(8, null);
        long count = h.objects().stream().filter(S1BossExplosion.class::isInstance).count();
        assertEquals(1, count, "BossDefeated reads current V-blank low bits on pass 8");
        h.boss.update(9, null);
        assertEquals(count, h.objects().stream().filter(S1BossExplosion.class::isInstance).count(),
                "the previous pass's 8 must not fire again on pass 9");
    }

    private static int index(int id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i] == id) return i;
        throw new AssertionError(id);
    }

    private static final class Harness {
        final ObjectManager manager;
        final Camera camera;
        final GameRng rng = new GameRng(GameRng.Flavour.S1_S2);
        AbstractS1EggmanBossInstance boss;
        int clock;

        Harness(int id, int slot) { this(id, slot, 320); }

        Harness(int id, int slot, int width) {
            camera = new Camera() {
                @Override public short getWidth() { return (short) width; }
                @Override public short getHeight() { return 224; }
            };
            ObjectManager[] holder = new ObjectManager[1];
            var state = new GameStateManager();
            var services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
                @Override public GameRng rng() { return rng; }
                @Override public com.openggf.game.GameModule gameModule() { return new Sonic1GameModule(); }
                @Override public GameStateManager gameState() { return state; }
                @Override public RomByteReader romReader() {
                    try { return RomByteReader.fromRom(TestEnvironment.currentRom()); }
                    catch (IOException e) { throw new AssertionError(e); }
                }
            };
            manager = new ObjectManager(List.of(), new Sonic1ObjectRegistry(), 0,
                    null, null, GraphicsManager.getInstance(), camera, services);
            holder[0] = manager;
            manager.reset(0);
            ObjectSpawn spawn = new ObjectSpawn(0x2B00, 0x200, id, 0, 0, false, 0);
            boss = manager.createDynamicObjectAtSlot(() -> switch (id) {
                case 0x3D -> new Sonic1GHZBossInstance(spawn);
                case 0x73 -> new Sonic1MZBossInstance(spawn);
                case 0x75 -> new Sonic1SYZBossInstance(spawn);
                case 0x77 -> new Sonic1LZBossInstance(spawn);
                case 0x7A -> new Sonic1SLZBossInstance(spawn);
                default -> throw new AssertionError(id);
            }, slot);
            camera.setX((short) (boss.getX() - 160));
            camera.setY((short) (boss.getY() - 112));
        }

        void step() {
            manager.update(camera.getX(), null, List.of(), ++clock);
            manager.refreshPostCameraRenderState();
        }
        List<AbstractObjectInstance> objects() {
            return manager.getActiveObjects().stream().filter(AbstractObjectInstance.class::isInstance)
                    .map(AbstractObjectInstance.class::cast).sorted(Comparator.comparingInt(AbstractObjectInstance::getSlotIndex)).toList();
        }
        List<S1EggmanVisualComponent> parts() {
            return objects().stream().filter(S1EggmanVisualComponent.class::isInstance)
                    .map(S1EggmanVisualComponent.class::cast).toList();
        }
        S1EggmanVisualComponent part(int component) {
            return parts().stream().filter(p -> p.getComponent() == component).findFirst().orElseThrow();
        }
        List<String> forward(int count) {
            List<String> sequence = new ArrayList<>();
            for (int t = 0; t < count; t++) {
                step();
                sequence.add(parts().stream().map(p -> p.getSlotIndex() + ":" + p.getAnimation() + ":" + p.getMappingFrame()).toList().toString());
            }
            return sequence;
        }
    }
}
