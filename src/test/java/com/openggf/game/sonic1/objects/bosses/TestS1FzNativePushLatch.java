package com.openggf.game.sonic1.objects.bosses;

import com.openggf.camera.Camera;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameRng;
import com.openggf.game.GameStateManager;
import com.openggf.game.rules.GameRules;
import com.openggf.game.sonic1.S1SpriteDataLoader;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.game.sonic1.objects.Sonic1ObjectRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Native FZ status-bit ownership survives movement of the engine's spawn record. */
@RequiresRom(SonicGame.SONIC_1)
class TestS1FzNativePushLatch {
    private RomByteReader rom;

    @BeforeEach
    void setup() throws Exception {
        GraphicsManager.getInstance().initHeadless();
        rom = RomByteReader.fromRom(TestEnvironment.currentRom());
    }

    @AfterEach
    void cleanup() {
        GraphicsManager.getInstance().resetState();
        GameModuleRegistry.reset();
    }

    @Test
    void shippedSolidNoCollisionTestsTheObjectBitThenWritesBothAnimationBytes() {
        // BossFinal_Eggman_Crush's native SolidObject call, then that routine's
        // FixBugs=0 NoCollision: BTST object bit5, BEQ, MOVE.W #1,obAnim(a1).
        assertEquals(0x4EB9, rom.readU16BE(0x1A6EE));
        assertEquals(0x10062, rom.readU32BE(0x1A6F0));
        byte[] expected = {0x08, 0x28, 0x00, 0x05, 0x00, 0x22, 0x67, 0x12,
                0x33, 0x7C, 0x00, 0x01, 0x00, 0x1C,
                0x08, (byte) 0xA8, 0x00, 0x05, 0x00, 0x22,
                0x08, (byte) 0xA9, 0x00, 0x05, 0x00, 0x22};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i] & 0xFF, rom.readU8(0x1021E + i));
        }
    }

    @Test
    void movingControllerReleasesItsOwnPushBitAndPublishesTheNativeWord() throws Exception {
        Sonic1GameModule module = new Sonic1GameModule();
        GameModuleRegistry.setCurrent(module);
        Camera camera = new Camera();
        camera.setX((short) 0x2450);
        camera.setY((short) 0x0510);
        ObjectManager[] holder = new ObjectManager[1];
        var services = new StubObjectServices() {
            final GameRng rng = new GameRng(GameRng.Flavour.S1_S2);
            final GameStateManager gameState = new GameStateManager();
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public RomByteReader romReader() { return rom; }
            @Override public com.openggf.game.GameModule gameModule() { return module; }
            @Override public GameRng rng() { return rng; }
            @Override public GameStateManager gameState() { return gameState; }
        };
        ObjectManager manager = new ObjectManager(List.of(), new Sonic1ObjectRegistry(), 0,
                null, null, GraphicsManager.getInstance(), camera, services);
        holder[0] = manager;
        manager.reset(0);
        Sonic1FZBossInstance boss = manager.createDynamicObjectAtSlot(() -> new Sonic1FZBossInstance(
                new ObjectSpawn(0x2450, 0x0510, 0x85, 0, 0, false, 0)), 127);
        for (int slot = 32; slot < 127; slot++) manager.reserveDynamicSlot(slot);
        manager.update(0x2450, null, List.of(), 0);
        boss.getState().routineSecondary = 2;
        write(boss, "cylinderState", 0);
        write(boss, "activeCylinderCount", 1);
        boss.syncPositionFromCylinder(0x2550, 0x0590);

        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setGameRulesForTest(GameRules.SONIC_1);
        player.setAnimationSet(S1SpriteDataLoader.loadAnimationSet(rom,
                Sonic1Constants.SONIC_ANIM_DATA_ADDR, Sonic1Constants.SONIC_ANIM_SCRIPT_COUNT));
        player.setCentreX((short) (0x2550 - 0x2A));
        player.setCentreY((short) 0x0590);
        player.setAir(false);
        player.setXSpeed((short) 0x100);
        player.setAnimationId(5);
        manager.update(0x2450, player, List.of(), 1, false, true, true);
        assertTrue(player.getPushing(), "real side contact establishes this SST's push bit");
        assertTrue(manager.hasObjectPushingBit(player));
        ObjectSpawn oldPlacement = boss.getSpawn();

        // The occupied cylinder writes a new Y word into the controller. Sonic
        // has jumped away; the controller must still see its own previous push
        // bit when SolidObject reaches NoCollision after this movement.
        boss.syncPositionFromCylinder(0x2550, 0x0591);
        player.setCentreX((short) 0x24D0);
        player.setAir(true);
        player.setRolling(true);
        player.setAnimationId(2);
        player.getAnimationManager().update(1);
        int displayedMapping = player.getMappingFrame();
        manager.update(0x2450, player, List.of(), 2, false, true, true);

        assertNotEquals(oldPlacement, boss.getSpawn(), "the engine placement record really changed");
        assertFalse(player.getPushing());
        assertFalse(manager.hasObjectPushingBit(player), "no stale placement-keyed push may remain");
        assertEquals(0, player.getAnimationId(), "native word store writes Walk to obAnim");
        assertEquals(1, player.getAnimationManager().captureRewindState().lastAnimationId(),
                "the adjacent prev_anim byte receives Run");
        assertEquals(displayedMapping, player.getMappingFrame(),
                "SolidObject changes animation bytes after the current mapping was selected");
        assertTrue(player.getAir());
        assertTrue(player.getRolling(), "the shipped walk-jump write does not clear rolling status");
    }

    private static void write(Object target, String name, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(target, value);
    }
}
