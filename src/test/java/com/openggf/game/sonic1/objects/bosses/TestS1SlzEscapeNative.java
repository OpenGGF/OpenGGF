package com.openggf.game.sonic1.objects.bosses;

import com.openggf.camera.Camera;
import com.openggf.data.RomByteReader;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameRng;
import com.openggf.game.GameStateManager;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.game.sonic1.objects.Sonic1ObjectRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Short REV01 SLZ escape movement and dispatch checks, independent of route fixtures. */
@RequiresRom(SonicGame.SONIC_1)
class TestS1SlzEscapeNative {
    private static final int SINE_TABLE = 0x29EA;
    private RomByteReader rom;

    @BeforeEach
    void setUp() throws Exception {
        GraphicsManager.getInstance().initHeadless();
        rom = RomByteReader.fromRom(TestEnvironment.currentRom());
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void romEscapeCallsBossMoveTwiceButRecoveryBranchesToMoveUpdate() {
        assertEquals(0x317C, rom.readU16BE(0x19376)); // move.w #$400,obVelX
        assertEquals(0x400, rom.readU16BE(0x19378));
        assertEquals(0x317C, rom.readU16BE(0x1937C)); // move.w #-$40,obVelY
        assertEquals(-0x40, rom.readS16BE(0x1937E));
        assertEquals(0x6100, rom.readU16BE(0x19398));
        assertEquals(0x18050, wordBranchTarget(0x19398), "Escape.flee -> BossMove");
        assertEquals(0x6000, rom.readU16BE(0x1939C));
        assertEquals(0x1917A, wordBranchTarget(0x1939C), "Escape.flee -> ShipUpdate");
        assertEquals(0x6100, rom.readU16BE(0x1917A));
        assertEquals(0x18050, wordBranchTarget(0x1917A), "ShipUpdate -> BossMove again");
        assertEquals(0x6000, rom.readU16BE(0x19372));
        assertEquals(0x1919E, wordBranchTarget(0x19372), "Recover.exit -> MoveUpdate, no Escape fallthrough");
    }

    @Test
    void escapeMovesTwiceAndAppliesOneSineWhileExpandingTheBoundaryOnce() throws Exception {
        Harness h = harness(10, 42);
        int x = h.boss().getState().xFixed;
        int y = h.boss().getState().yFixed;
        int angle = integer(h.boss(), "sineAngle");
        int maxX = h.camera.getMaxX() & 0xFFFF;
        h.step();

        var state = h.boss().getState();
        int nativeXVel = rom.readS16BE(0x19378);
        int nativeYVel = rom.readS16BE(0x1937E);
        assertEquals(x + 2 * (nativeXVel << 8), state.xFixed);
        assertEquals(y + 2 * (nativeYVel << 8), state.yFixed);
        assertEquals(state.xFixed >> 16, state.x);
        assertEquals((state.yFixed >> 16) + (rom.readS16BE(SINE_TABLE + angle * 2) >> 6), state.y);
        assertEquals((angle + 2) & 0xFF, integer(h.boss(), "sineAngle"));
        assertEquals(maxX + 2, h.camera.getMaxX() & 0xFFFF);
        assertEquals(h.camera.getMaxX(), h.camera.getMaxXTarget());
        assertEquals(h.camera.getMaxX(), h.camera.getMaxXBeforeBoundaryEasing());
    }

    @Test
    void recoverySelectsEscapeWithoutExecutingItInTheSamePass() throws Exception {
        Harness h = harness(8, 41);
        int x = h.boss().getState().xFixed;
        int y = h.boss().getState().yFixed;
        int angle = integer(h.boss(), "sineAngle");
        int maxX = h.camera.getMaxX() & 0xFFFF;
        h.step();

        assertEquals(10, h.boss().getState().routineSecondary);
        assertEquals(42, integer(h.boss(), "timer"));
        assertEquals(x, h.boss().getState().xFixed);
        assertEquals(y, h.boss().getState().yFixed);
        assertEquals(angle, integer(h.boss(), "sineAngle"));
        assertEquals(maxX, h.camera.getMaxX() & 0xFFFF);
        h.step();
        assertEquals(x + 0x80000, h.boss().getState().xFixed);
        assertEquals(y - 0x8000, h.boss().getState().yFixed);
        assertEquals(maxX + 2, h.camera.getMaxX() & 0xFFFF);
    }

    @Test
    void recreatedBossReplaysRecoveryAndBothEscapeMovesWithItsCameraBoundary() throws Exception {
        Harness h = harness(8, 39);
        h.manager.setRewindInPlaceRestoreEnabledForTest(false);
        h.step(); // Native visual components now exist; still two recovery passes before escape.
        assertEquals(4, h.manager.getAllocatedSlotCount());
        Sonic1SLZBossInstance original = h.boss();
        RewindRegistry rewind = new RewindRegistry();
        rewind.register(h.manager.rewindSnapshottable());
        rewind.register(h.camera);
        rewind.register(h.rng);
        CompositeSnapshot snapshot = rewind.capture();
        List<String> expected = forward(h, 7);
        rewind.restore(snapshot);
        assertNotSame(original, h.boss());
        assertEquals(4, h.manager.getAllocatedSlotCount());
        assertEquals(expected, forward(h, 7));
    }

    private List<String> forward(Harness h, int passes) throws Exception {
        List<String> result = new ArrayList<>();
        for (int pass = 0; pass < passes; pass++) {
            h.step();
            var state = h.boss().getState();
            result.add(state.routineSecondary + ":" + integer(h.boss(), "timer") + ":"
                    + state.xFixed + ":" + state.yFixed + ":" + state.x + ":" + state.y + ":"
                    + state.xVel + ":" + state.yVel + ":" + integer(h.boss(), "sineAngle") + ":"
                    + h.camera.getMaxX() + ":" + h.camera.getMaxXTarget() + ":"
                    + h.camera.getMaxXBeforeBoundaryEasing() + ":" + h.manager.getAllocatedSlotCount());
        }
        return result;
    }

    private Harness harness(int routine, int timer) throws Exception {
        ObjectManager[] owner = new ObjectManager[1];
        Camera camera = new Camera() {
            @Override public short getX() { return 0x2000; }
            @Override public short getY() { return 0x210; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
        camera.setMaxX((short) 0x2000);
        GameRng rng = new GameRng(GameRng.Flavour.S1_S2);
        DebugOverlayManager debug = mock(DebugOverlayManager.class);
        GameStateManager gameState = mock(GameStateManager.class);
        GameModule module = GameModuleRegistry.getCurrent();
        StubObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return owner[0]; }
            @Override public Camera camera() { return camera; }
            @Override public RomByteReader romReader() { return rom; }
            @Override public DebugOverlayManager debugOverlay() { return debug; }
            @Override public GameRng rng() { return rng; }
            @Override public GameModule gameModule() { return module; }
            @Override public GameStateManager gameState() { return gameState; }
        };
        ObjectManager manager = new ObjectManager(List.of(), new Sonic1ObjectRegistry(), 0, null,
                module.createTouchResponseTable(rom), GraphicsManager.getInstance(), camera, services);
        owner[0] = manager;
        Sonic1SLZBossInstance boss = manager.createDynamicObjectAtSlot(() -> new Sonic1SLZBossInstance(
                new ObjectSpawn(0x2110, 0x230, Sonic1ObjectIds.SLZ_BOSS, 0, 0, false, 0)), 0x40);
        var state = boss.getState();
        state.defeated = true;
        state.routineSecondary = 8;
        write(boss, "timer", 39);
        manager.update(0, null, List.of(), 0, false);
        manager.refreshPostCameraRenderState(); // BuildSprites publishes each component's previous-frame visibility.
        state.routineSecondary = routine;
        state.xFixed = 0x21101234;
        state.yFixed = 0x02305678;
        state.x = state.xFixed >> 16;
        state.y = state.yFixed >> 16;
        state.xVel = state.yVel = 0;
        write(boss, "timer", timer);
        write(boss, "sineAngle", 0x38);
        return new Harness(manager, camera, rng);
    }

    private record Harness(ObjectManager manager, Camera camera, GameRng rng) {
        void step() {
            manager.update(0, null, List.of(), 0, false);
            manager.refreshPostCameraRenderState();
        }
        Sonic1SLZBossInstance boss() {
            return manager.getActiveObjects().stream().filter(Sonic1SLZBossInstance.class::isInstance)
                    .map(Sonic1SLZBossInstance.class::cast).findFirst().orElseThrow();
        }
    }

    private int wordBranchTarget(int address) { return address + 2 + rom.readS16BE(address + 2); }
    private static int integer(Object object, String name) throws Exception {
        Field field = Sonic1SLZBossInstance.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(object);
    }
    private static void write(Object object, String name, int value) throws Exception {
        Field field = Sonic1SLZBossInstance.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(object, value);
    }
}
