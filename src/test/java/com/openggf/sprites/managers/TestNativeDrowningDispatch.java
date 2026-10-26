package com.openggf.sprites.managers;

import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.SessionManager;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.sprites.playable.*;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native primary-routine versus fixed-sidecar drowning ownership; no trace input. */
class TestNativeDrowningDispatch {
    private AbstractPlayableSprite player;
    private SpriteManager sprites;
    private Object fixed;
    private LevelManager level;
    private int frame;

    @AfterEach void tearDown() { SessionManager.clear(); }

    @ParameterizedTest @EnumSource(value = SonicGame.class, names = {"SONIC_1", "SONIC_2", "SONIC_3K"})
    void controlledP1MovesBeforeGravityInItsNativeOwner(SonicGame game) throws Exception {
        setup(game, false);
        enterDrowning();
        assertTrue(player.isObjectControlSuppressesMovement(), "native object_control=$81");
        assertFalse(player.isObjectControlAllowsCpu());
        assertEquals(game == SonicGame.SONIC_3K ? 0x0C : 0x02,
                com.openggf.trace.TraceCharacterState.routineFromSprite(player));
        if (game == SonicGame.SONIC_3K) assertEquals(0x0C, NativePlayableRoutine.resolve(player).byteValue());
        assertMovementOwners(game);
    }

    @ParameterizedTest @EnumSource(value = SonicGame.class, names = {"SONIC_2", "SONIC_3K"})
    void controlledP2MovesBeforeGravityWithoutCpuSteering(SonicGame game) throws Exception {
        setup(game, true);
        enterDrowning();
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        player.setXSpeed((short) 0);
        player.clearLogicalInputState();
        assertMovementOwners(game);
        assertEquals(0, player.getXSpeed(), "CPU must not steer the sinking character");
    }

    private void assertMovementOwners(SonicGame game) throws Exception {
        int initialY = player.getCentreY();
        int initialSub = player.getYSubpixelRaw();
        playerPass();
        assertEquals(initialY, player.getCentreY(), "integrate the old zero velocity first");
        assertEquals(initialSub, player.getYSubpixelRaw());
        assertEquals(game == SonicGame.SONIC_3K ? 0x10 : 0, player.getYSpeed(),
                "only S3K routine $0C owns motion in the player slot");
        updateFixed();
        assertEquals(0x10, player.getYSpeed(), "one gravity step per frame");
        playerPass();
        updateFixed();
        assertEquals(initialY, player.getCentreY());
        assertEquals((initialSub + 0x1000) & 0xFFFF, player.getYSubpixelRaw());
        assertEquals(0x20, player.getYSpeed());
    }

    @ParameterizedTest @EnumSource(value = SonicGame.class, names = {"SONIC_1", "SONIC_2", "SONIC_3K"})
    void fixedCountdownOwnsExactly120PassesAndRegisteredRewind(SonicGame game) throws Exception {
        setup(game, false);
        RewindRegistry registry = registry();
        var before = registry.capture();
        enterDrowning();
        var entered = registry.capture();
        for (int pass = 1; pass <= 119; pass++) {
            playerPass(); updateFixed();
            assertFalse(player.getDead(), "fixed countdown still positive at " + pass);
        }
        var last = registry.capture();
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) registry.restore(last);
            playerPass();
            assertFalse(player.getDead(), "player pass does not advance the fixed timer");
            updateFixed();
            assertTrue(player.getDead(), "fixed countdown writes primary routine 6");
            assertFalse(player.isDrowningPreDeath());
        }
        registry.restore(entered);
        assertTrue(player.isDrowningPreDeath());
        assertFalse(player.getDead());
        assertMovementOwners(game);
        registry.restore(before);
        assertFalse(player.isDrowningPreDeath());
        assertFalse(player.getDead());
        enterDrowning();
        assertTrue(player.isDrowningPreDeath());
    }

    @ParameterizedTest @EnumSource(value = SonicGame.class, names = {"SONIC_2", "SONIC_3K"})
    void p2FixedLifetimeReachesDeathMarkerAndRespawn(SonicGame game) throws Exception {
        setup(game, true);
        enterDrowning();
        for (int pass = 1; pass <= 120; pass++) {
            playerPass();
            assertFalse(player.getDead(), "player/CPU cannot spend the fixed lifetime at " + pass);
            updateFixed();
        }
        assertTrue(player.getDead());
        int markerX = game == SonicGame.SONIC_3K ? 0x7F00 : 0x4000;
        boolean sawMarker = false, sawRespawn = false;
        for (int pass = 0; pass < 700; pass++) {
            playerPass(sawMarker); // native manual B bypasses the controlled-leader respawn gate
            sawMarker |= (player.getCentreX() & 0xFFFF) == markerX;
            if (sawMarker && !player.getDead()
                    && player.getCpuController().getState() != SidekickCpuController.State.DEAD_FALLING
                    && (player.getCentreX() & 0xFFFF) != markerX) {
                sawRespawn = true;
                break;
            }
        }
        assertTrue(sawMarker, "primary death reaches the native offscreen marker");
        assertTrue(sawRespawn, "CPU re-enters its respawn strategy after drowning");
        assertFalse(player.isDrowningPreDeath());
        assertFalse(player.isDrowningDeath(), "respawn retires the completed drowning animation/motion state");
        if (game == SonicGame.SONIC_2) assertEquals(0x17, player.getAnimationId(),
                "S2 TailsCPU_Respawn preserves the raw drowning animation until Flying changes it");
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void nativeDrowningBurstConsumesAngleOnlyAfterSuccessfulAllocation(boolean freeSlot) throws Exception {
        setup(SonicGame.SONIC_3K, false);
        enterDrowning();
        var pool = mock(com.openggf.level.objects.ObjectManager.class);
        when(level.getObjectManager()).thenReturn(pool);
        when(pool.hasFreeDynamicSlot()).thenReturn(freeSlot);
        var rng = player.currentRng();
        long seed = rng.getSeed();
        var nativeCalls = new com.openggf.game.GameRng(rng.flavour(), seed);
        nativeCalls.nextRaw(); // interval is consumed even if AllocateObject fails
        int angle = freeSlot ? nativeCalls.nextRaw() & 0xFF : -1;
        for (int pass = 0; pass < 32 && rng.getSeed() == seed; pass++) updateFixed();
        assertEquals(nativeCalls.getSeed(), rng.getSeed());
        if (freeSlot) {
            var child = org.mockito.ArgumentCaptor.forClass(com.openggf.level.objects.ObjectInstance.class);
            verify(pool).addDynamicObject(child.capture());
            assertEquals(player.getCentreY() - 0x0C, child.getValue().getY());
            var field = child.getValue().getClass().getDeclaredField("angle"); field.setAccessible(true);
            assertEquals(angle, field.getInt(child.getValue()));
        } else verify(pool, never()).addDynamicObject(any());
    }

    @Test void primaryDrowningReleasesCarriedPlayerBeforeMotion() throws Exception {
        setup(SonicGame.SONIC_3K, true);
        var main = GameServices.camera().getFocusedSprite();
        player.getTailsCarryController().forceScriptedCarry(TailsCarryController.CarryContext.CNZ);
        assertTrue(main.isObjectControlled());
        enterDrowning();
        playerPass();
        assertFalse(main.isObjectControlled());
        assertTrue(main.getAir());
        assertFalse(player.getTailsCarryController().isCarryingMainCharacter());
        assertEquals(0x10, player.getYSpeed());
    }

    @Test void primaryDrowningBypassesNormalControlTimersInputAndTouch() throws Exception {
        setup(SonicGame.SONIC_3K, false);
        enterDrowning();
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        player.setInvulnerableFrames(10);
        player.setMoveLockTimer(7);
        player.setLogicalInputState(false, false, true, false, false);
        int logical = player.getLogicalInputState();
        clearInvocations(level);
        playerPass();
        assertEquals(10, player.getInvulnerableFrames());
        assertEquals(7, player.getMoveLockTimer());
        assertEquals(logical, player.getLogicalInputState());
        verify(level, never()).applyTouchResponses(player);
        verify(level, never()).updatePlayableWaterStateForCurrentLevel(player);
        verify(level, never()).applyPlaneSwitchers(player);
    }

    @Test void primaryDrowningIntegratesOldVelocityEvenWithoutObjectControlGate() throws Exception {
        setup(SonicGame.SONIC_3K, false);
        enterDrowning();
        ObjectControlState.none().applyTo(player);
        int y = player.getCentreY(), sub = player.getYSubpixelRaw();
        playerPass();
        assertEquals(y, player.getCentreY());
        assertEquals(sub, player.getYSubpixelRaw(), "old zero velocity must be integrated before +$10");
        assertEquals(0x10, player.getYSpeed());
    }

    @Test void ordinaryControlledWaitPreservesPositionAndDoesNotEnterDeath() throws Exception {
        setup(SonicGame.SONIC_3K, false);
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        player.setXSpeed((short) 0x100);
        player.setYSpeed((short) 0x200);
        int x = player.getCentreX(), y = player.getCentreY();
        for (int pass = 0; pass < 121; pass++) playerPass();
        assertEquals(x, player.getCentreX()); assertEquals(y, player.getCentreY());
        assertEquals(0x100, player.getXSpeed()); assertEquals(0x200, player.getYSpeed());
        assertFalse(player.isDrowningPreDeath()); assertFalse(player.getDead());
    }

    @Test void deadRoutineWinsOverAStaleDrowningMarkerBeforePlayerDispatch() throws Exception {
        setup(SonicGame.SONIC_3K, false);
        enterDrowning();
        player.setDead(true); // a death owner can replace $0C before the sidecar retires its timer
        player.setXSpeed((short) 0x123);
        player.setGSpeed((short) 0x345);
        GameServices.camera().setMaxY((short) 0x1000);
        when(level.runPolicy()).thenReturn(com.openggf.game.session.GameplayRunPolicy.stock());

        assertEquals(0x06, NativePlayableRoutine.resolve(player).byteValue());
        assertEquals(0x06, com.openggf.trace.TraceCharacterState.routineFromSprite(player));
        assertFalse(PlayableSpriteInternalAccess.hasPrimaryDrowningRoutine(player));
        playerPass();

        assertTrue(player.getDead());
        assertEquals(0, player.getXSpeed(), "routine 6 owns death motion rather than the retained drown marker");
        assertEquals(0, player.getGSpeed());
    }

    private void setup(SonicGame game, boolean sidekick) throws Exception {
        TestEnvironment.configureGameModuleFixture(game);
        sprites = GameServices.sprites();
        level = mock(LevelManager.class);
        when(level.objectsExecuteAfterPlayerPhysics()).thenReturn(true);
        TestEnvironment.activeGameplayMode().attachLevelManagers(new com.openggf.level.WaterSystem(),
                new com.openggf.level.ParallaxManager(), mock(com.openggf.physics.TerrainCollisionManager.class),
                mock(com.openggf.physics.CollisionSystem.class), sprites, level);
        var main = new Sonic("sonic", (short) 0x400, (short) 0x400);
        GameServices.camera().setFocusedSprite(main);
        GameServices.camera().setMaxX((short) 0x6000);
        GameServices.camera().setMaxY((short) 0x6000);
        GameServices.camera().setMaxYTarget((short) 0x6000);
        sprites.addSprite(main);
        player = main;
        if (sidekick) {
            ObjectControlState.nativeBit7FullControl().applyTo(main); // stable native controlled wait
            player = new Tails("tails", (short) 0x400, (short) 0x400);
            player.setCpuControlled(true);
            sprites.addSprite(player, "tails");
            var cpu = new SidekickCpuController(player, main);
            var initialized = SidekickCpuController.class.getDeclaredMethod(
                    "forceStateForTest", SidekickCpuController.State.class, int.class);
            initialized.setAccessible(true);
            initialized.invoke(cpu, SidekickCpuController.State.NORMAL, 0); // already initialized native P2
        }
        player.setInWater(true);
        String owner = switch (game) {
            case SONIC_1 -> "com.openggf.game.sonic1.events.Sonic1FixedAirCountdownManager";
            case SONIC_2 -> "com.openggf.game.sonic2.Sonic2FixedAirCountdownManager";
            default -> "com.openggf.game.sonic3k.S3kFixedAirCountdownManager";
        };
        if (game == SonicGame.SONIC_1) {
            var ctor = Class.forName(owner).getDeclaredConstructor(java.util.function.Supplier.class);
            ctor.setAccessible(true);
            fixed = ctor.newInstance((java.util.function.Supplier<AbstractPlayableSprite>) () -> main);
        } else {
            var ctor = Class.forName(owner).getDeclaredConstructor(); ctor.setAccessible(true);
            fixed = ctor.newInstance();
        }
        updateFixed(); // water-entry installation owns the initial air refill
    }

    private void enterDrowning() throws Exception {
        player.getDrowningController().setRemainingAirFromFixedCountdown(0);
        for (int i = 0; i < 61 && !player.isDrowningPreDeath(); i++) updateFixed();
        assertTrue(player.isDrowningPreDeath());
    }

    private void playerPass() { playerPass(false); }

    private void playerPass(boolean manualP2Respawn) {
        InputHandler input = new InputHandler();
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(AbstractPlayableSprite.INPUT_RIGHT, 0, 0, 0, false, false),
                manualP2Respawn ? PlayerInputState.of(AbstractPlayableSprite.INPUT_JUMP,
                        AbstractPlayableSprite.INPUT_JUMP, com.openggf.control.InputActionMasks.ACTION_B,
                        com.openggf.control.InputActionMasks.ACTION_B, false, false)
                        : PlayerInputState.neutral()));
        sprites.update(input);
        frame++;
    }

    private void updateFixed() throws Exception { call("update"); }
    private Object call(String name, Class<?> type, Object arg) throws Exception {
        Method m = fixed.getClass().getDeclaredMethod(name, type); m.setAccessible(true);
        return m.invoke(fixed, arg);
    }
    private void call(String name) throws Exception {
        Method m = fixed.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(fixed);
    }
    private RewindRegistry registry() {
        RewindRegistry registry = new RewindRegistry();
        registry.register(player.currentRng());
        registry.register(new RewindSnapshottable<PerObjectRewindSnapshot>() {
            public String key() { return "player"; }
            public PerObjectRewindSnapshot capture() { return player.captureRewindState(); }
            public void restore(PerObjectRewindSnapshot state) { player.restoreRewindState(state); }
        });
        registry.register(new RewindSnapshottable<byte[]>() {
            public String key() { return "fixed"; }
            public byte[] capture() {
                try {
                    var field = fixed.getClass().getDeclaredField("REWIND_STATE_BYTES"); field.setAccessible(true);
                    ByteBuffer buffer = ByteBuffer.allocate(field.getInt(null));
                    call("writeRewindState", ByteBuffer.class, buffer); return buffer.array();
                } catch (Exception e) { throw new AssertionError(e); }
            }
            public void restore(byte[] state) {
                try { call("readRewindState", ByteBuffer.class, ByteBuffer.wrap(state)); }
                catch (Exception e) { throw new AssertionError(e); }
            }
        });
        return registry;
    }
}
