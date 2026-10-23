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
import com.openggf.game.sonic1.objects.Sonic1SeesawObjectInstance;
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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** REV01 spikeball endpoint contact and collision-before-physics checks, without route inputs. */
@RequiresRom(SonicGame.SONIC_1)
class TestS1SlzSpikeballNativeContact {
    private static final int BOSS_HITBOX = 0x197D2;
    private static final int BALL_HITBOX = 0x197D6;
    private RomByteReader rom;

    @BeforeEach
    void setUp() throws Exception {
        GraphicsManager.getInstance().initHeadless();
        rom = RomByteReader.fromRom(TestEnvironment.currentRom());
    }

    @AfterEach
    void tearDown() { GraphicsManager.getInstance().resetState(); }

    @Test
    void romUsesSignedCumulativeOffsetsAndUnsignedStrictlyBelowRejectionBeforeObjectFall() {
        assertBytes(BOSS_HITBOX, 0xE8, 0x30, 0xE8, 0x30, 8, 0xF0, 8, 0xF0);
        assertBytes(0x19680, 0x45, 0xFA, 1, 0x50, 0x47, 0xFA, 1, 0x50);
        assertBytes(0x19694, 0xB4, 0x40, 0x65, 0x50); // CMP.W bossLeft,ballRight / BLO
        assertBytes(0x196A4, 0xB0, 0x42, 0x65, 0x40); // CMP.W ballLeft,bossRight / BLO
        assertBytes(0x196B4, 0xB6, 0x41, 0x65, 0x30);
        assertBytes(0x196C4, 0xB2, 0x43, 0x65, 0x20);
        assertBytes(0x196D4, 0x53, 0x29, 0, 0x21, 0x66, 0x0E); // subtract hit count
        assertBytes(0x196E0, 0x42, 0x68, 0, 0x10, 0x42, 0x68, 0, 0x12); // killing hit clears velocities
        assertBytes(0x196EE, 0x4E, 0xB9, 0, 0, 0xDC, 0x40); // only then ObjectFall
    }

    @Test
    void eachTouchingEdgeAndCornerHitsButOnePixelOutsideDoesNot() throws Exception {
        Harness h = harness();
        int[] offsets = {-33, -32, -31, 0, 31, 32, 33};
        for (int dx : offsets) {
            for (int dy : offsets) {
                position(h, 0x2180, 0x260, 0x2180 + dx, 0x260 + dy);
                assertEquals(Math.abs(dx) <= 32 && Math.abs(dy) <= 32, contact(h.ball()),
                        "native contact at offset " + dx + "," + dy);
            }
        }
    }

    @Test
    void wordWrappedEndpointsAndSignedCentreRepresentationsMatchTheRomBranches() throws Exception {
        Harness h = harness();
        int[] centres = {0, 7, 20, 24, 0x7FF0, 0x8000, 0xFFF0, 0xFFFF};
        int[] offsets = {-33, -32, -1, 0, 1, 32, 33};
        for (int centre : centres) {
            for (int delta : offsets) {
                for (int axis = 0; axis < 2; axis++) {
                    int bossX = axis == 0 ? centre : 0x2180;
                    int bossY = axis == 1 ? centre : 0x260;
                    int ballX = (bossX + (axis == 0 ? delta : 0)) & 0xFFFF;
                    int ballY = (bossY + (axis == 1 ? delta : 0)) & 0xFFFF;
                    boolean expected = romAccepts(bossX, bossY, ballX, ballY);
                    position(h, bossX, bossY, ballX, ballY);
                    assertEquals(expected, contact(h.ball()), "unsigned word centres");
                    position(h, (short) bossX, (short) bossY, ballX, ballY);
                    assertEquals(expected, contact(h.ball()), "signed representation of the same centres");
                }
            }
        }
        position(h, 0x8000, 0x260, 0x8020, 0x260);
        assertTrue(contact(h.ball()), "the sign bit does not separate nearby native word coordinates");
        position(h, 20, 0x260, 52, 0x260);
        assertFalse(contact(h.ball()), "native unsigned endpoint wrap can reject a distance of32");
    }

    @Test
    void killingEdgeContactHappensBeforeMovementAndPreservesBothFractions() throws Exception {
        Harness h = harness();
        position(h, 0x2180, 0x260, 0x2178, 0x280);
        write(h.ball(), "xVel", 0x114);
        write(h.ball(), "yVel", -0x400);
        int x = integer(h.ball(), "xPos");
        int y = integer(h.ball(), "yPos");
        h.ball().update(1, null);

        assertTrue(h.boss().getState().defeated);
        assertEquals(0, h.boss().getState().hitCount);
        assertEquals("EXPLODING", field(h.ball(), "currentState").toString());
        assertEquals(0, integer(h.ball(), "subtypeCounter"));
        assertEquals(x, integer(h.ball(), "xPos"));
        assertEquals(y, integer(h.ball(), "yPos"));
        assertEquals(0, integer(h.ball(), "xVel"));
        assertEquals(0x38, integer(h.ball(), "yVel"), "ObjectFall adds gravity after the killing hit clears velocity");

        Harness approaching = harness();
        position(approaching, 0x2180, 0x260, 0x2178, 0x281);
        write(approaching.ball(), "yVel", -0x100);
        approaching.ball().update(1, null);
        assertFalse(approaching.boss().getState().defeated, "moving into contact cannot hit until the next pass");
        assertEquals(0x280, approaching.ball().getY());
        approaching.ball().update(2, null);
        assertTrue(approaching.boss().getState().defeated);
    }

    @Test
    void recreatedGraphReplaysTheEdgeHitAndFollowingExplosionPasses() throws Exception {
        Harness h = harness();
        position(h, 0x2180, 0x260, 0x2178, 0x280);
        write(h.ball(), "xVel", 0x114);
        write(h.ball(), "yVel", -0x400);
        h.manager.setRewindInPlaceRestoreEnabledForTest(false);
        Sonic1SLZBossSpikeball original = h.ball();
        RewindRegistry rewind = new RewindRegistry();
        rewind.register(h.manager.rewindSnapshottable());
        rewind.register(h.rng);
        CompositeSnapshot snapshot = rewind.capture();
        List<String> expected = forward(h, 5);
        assertTrue(h.boss().getState().defeated);
        rewind.restore(snapshot);
        assertNotSame(original, h.ball());
        assertSame(h.boss(), field(h.ball(), "boss"));
        assertSame(h.seesaw(), h.ball().getTargetSeesaw());
        assertEquals(expected, forward(h, 5));
    }

    private List<String> forward(Harness h, int passes) throws Exception {
        List<String> result = new ArrayList<>();
        for (int pass = 0; pass < passes; pass++) {
            h.manager.update(0, null, List.of(), 0, false);
            h.manager.refreshPostCameraRenderState();
            var boss = h.boss().getState();
            var ball = h.ball();
            result.add(boss.hitCount + ":" + boss.defeated + ":" + boss.routineSecondary + ":"
                    + integer(h.boss(), "timer") + ":" + ball.getSlotIndex() + ":"
                    + field(ball, "currentState") + ":" + integer(ball, "xPos") + ":" + integer(ball, "yPos")
                    + ":" + integer(ball, "xVel") + ":" + integer(ball, "yVel") + ":"
                    + integer(ball, "subtypeCounter") + ":" + integer(ball, "explosionAnimFrame")
                    + ":" + integer(ball, "explosionAnimDuration") + ":" + h.manager.getAllocatedSlotCount());
        }
        return result;
    }

    private boolean romAccepts(int bossX, int bossY, int ballX, int ballY) {
        return romAxisAccepts(bossX, ballX, 0) && romAxisAccepts(bossY, ballY, 2);
    }

    /** Execute the ROM's two cumulative byte offsets and CMP.W/BLO pairs for one axis. */
    private boolean romAxisAccepts(int boss, int ball, int axisOffset) {
        int bossEdge = (boss + (byte) rom.readU8(BOSS_HITBOX + axisOffset)) & 0xFFFF;
        int ballEdge = (ball + (byte) rom.readU8(BALL_HITBOX + axisOffset)) & 0xFFFF;
        if (ballEdge < bossEdge) return false;
        bossEdge = (bossEdge + (byte) rom.readU8(BOSS_HITBOX + axisOffset + 1)) & 0xFFFF;
        ballEdge = (ballEdge + (byte) rom.readU8(BALL_HITBOX + axisOffset + 1)) & 0xFFFF;
        return bossEdge >= ballEdge;
    }

    private static void position(Harness h, int bossX, int bossY, int ballX, int ballY) throws Exception {
        var boss = h.boss().getState();
        boss.x = bossX;
        boss.y = bossY;
        boss.xFixed = bossX << 16;
        boss.yFixed = bossY << 16;
        write(h.ball(), "xPos", (ballX << 16) | 0x4567);
        write(h.ball(), "yPos", (ballY << 16) | 0x89AB);
    }

    private Harness harness() throws Exception {
        ObjectManager[] owner = new ObjectManager[1];
        Camera camera = new Camera() {
            @Override public short getX() { return 0x2100; }
            @Override public short getY() { return 0x210; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
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
                new ObjectSpawn(0x2180, 0x260, Sonic1ObjectIds.SLZ_BOSS, 0, 0, false, 0)), 0x21);
        Sonic1SeesawObjectInstance seesaw = manager.createDynamicObjectAtSlot(() -> new Sonic1SeesawObjectInstance(
                new ObjectSpawn(0x2180, 0x2D4, Sonic1ObjectIds.SEESAW, 1, 0, false, 1)), 0x25);
        boss.getState().routineSecondary = 4; // Countdown owns no movement before the flying ball's later slot.
        boss.getState().xVel = boss.getState().yVel = 0;
        write(boss, "timer", 39); // $28 is the native spawn pass; initialize visuals after that pass.
        manager.update(0, null, List.of(), 0, false);
        manager.refreshPostCameraRenderState();
        boss.getState().hitCount = 1;
        Sonic1SLZBossSpikeball ball = manager.createDynamicObjectAtSlot(
                () -> new Sonic1SLZBossSpikeball(boss, seesaw, 0x2178, 0x280), 0x2A);
        Field state = declared(ball, "currentState");
        state.set(ball, Enum.valueOf(state.getType().asSubclass(Enum.class), "FLYING"));
        write(ball, "frameToggleDuration", 10);
        write(ball, "frameToggleTimer", 10);
        assertEquals(6, manager.getAllocatedSlotCount(), "ship, face, flame, tube, seesaw and the single flying ball");
        return new Harness(manager, rng);
    }

    private record Harness(ObjectManager manager, GameRng rng) {
        Sonic1SLZBossInstance boss() { return only(Sonic1SLZBossInstance.class); }
        Sonic1SLZBossSpikeball ball() { return only(Sonic1SLZBossSpikeball.class); }
        Sonic1SeesawObjectInstance seesaw() { return only(Sonic1SeesawObjectInstance.class); }
        private <T> T only(Class<T> type) {
            List<T> objects = manager.getActiveObjects().stream().filter(type::isInstance).map(type::cast).toList();
            assertEquals(1, objects.size(), "one live " + type.getSimpleName());
            return objects.getFirst();
        }
    }

    private static boolean contact(Sonic1SLZBossSpikeball ball) throws Exception {
        Method method = Sonic1SLZBossSpikeball.class.getDeclaredMethod("checkBossCollision");
        method.setAccessible(true);
        return (boolean) method.invoke(ball);
    }
    private void assertBytes(int address, int... expected) {
        for (int offset = 0; offset < expected.length; offset++)
            assertEquals(expected[offset], rom.readU8(address + offset), "ROM byte at " + Integer.toHexString(address + offset));
    }
    private static Object field(Object object, String name) throws Exception { return declared(object, name).get(object); }
    private static int integer(Object object, String name) throws Exception { return declared(object, name).getInt(object); }
    private static void write(Object object, String name, int value) throws Exception { declared(object, name).setInt(object, value); }
    private static Field declared(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
