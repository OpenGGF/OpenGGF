package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.S3kFormTier;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kSuperStateController;
import com.openggf.game.zone.ZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.PoweredScreenAttackSpecial;
import com.openggf.level.objects.PoweredScreenAttackable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.Tails;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.withSettings;

class TestSuperTailsFlickyFlockRuntime {

    @Test
    void rendersExactlyFourBirdsFromStandaloneRomArt() {
        Fixture fixture = new Fixture(List.of(), null);
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        when(renderer.isReady()).thenReturn(true);
        ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        when(renderManager.getRenderer(Sonic3kObjectArtKeys.SUPER_TAILS_BIRDS))
                .thenReturn(renderer);
        fixture.flock.setServices(fixture.services.withRenderManager(renderManager));

        fixture.flock.appendRenderCommands(new ArrayList<GLCommand>());

        verify(renderer, times(4)).drawFrameIndex(anyInt(), anyInt(), anyInt(),
                anyBoolean(), anyBoolean());
    }

    @Test
    void activeSuperTailsMovesAllFourBirdsAndRepeatWrapIsApplied() throws Exception {
        ZoneRuntimeState runtime = mock(ZoneRuntimeState.class);
        when(runtime.levelRepeatOffset()).thenReturn(0x20);
        Fixture fixture = new Fixture(List.of(), runtime);
        int[] before = fixture.fixedX();

        fixture.flock.update(1, fixture.owner);

        int[] after = fixture.fixedX();
        for (int bird = 0; bird < 4; bird++) {
            int velocity = fixture.intField("xv" + bird);
            assertEquals(((before[bird] + velocity) >> 8) - 0x20, after[bird] >> 8,
                    "MoveSprite2 integrates signed 8.8 velocity before Level_repeat_offset");
            assertNotEquals(0, velocity);
        }
    }

    @Test
    void leavingSuperTailsReleasesReservationsAndFliesAwayUntilOffscreen() throws Exception {
        Fixture fixture = new Fixture(List.of(), null);
        fixture.setField("target0", ObjectRefId.dynamic(4, 1, 20));
        when(fixture.controller.getActiveFormTier()).thenReturn(S3kFormTier.NORMAL);

        fixture.flock.update(1, fixture.owner);

        assertTrue(fixture.booleanField("flyingAway"));
        assertNull(fixture.field("target0"));
        assertEquals(120, fixture.intField("delay0"));
    }

    @Test
    void normalEnemyUsesPoweredDestructionAndAttributesNativeP2() {
        ObjectInstance target = target(0x01, 0,
                PoweredScreenAttackable.class);
        AtomicBoolean destroyed = new AtomicBoolean();
        when(target.isDestroyed()).thenAnswer(ignored -> destroyed.get());
        doAnswer(ignored -> {
            destroyed.set(true);
            return null;
        }).when((PoweredScreenAttackable) target).onPoweredScreenAttack(any());
        Fixture fixture = new Fixture(List.of(target), null);

        fixture.flock.update(1, fixture.owner);

        SuperTailsFlickyFlockObjectInstance.BirdRuntimeState bird =
                fixture.flock.birdRuntimeState(0);
        assertNull(bird.target(), "a colliding reserved target is released after the hit");
        assertEquals(120, bird.searchDelay());
        assertTrue(Integer.compareUnsigned(
                bird.x() - target.getX() + 0x0C, 0x18) < 0);
        assertTrue(Integer.compareUnsigned(
                bird.y() - target.getY() + 0x0C, 0x18) < 0);
        verify((PoweredScreenAttackable) target, times(1))
                .onPoweredScreenAttack(fixture.p2);
        assertEquals(fixture.flock.getX(), fixture.p2.getCentreX());
        assertTrue(fixture.p2.getAir());
        assertTrue(fixture.p2.getRolling());
        assertEquals(2, fixture.p2.getAnimationId());
    }

    @Test
    void bossDispatchesTargetOwnedHitAndSpecialOrsPropertyTwo() {
        ObjectInstance boss = target(0x01, 3, TouchResponseAttackable.class);
        AtomicInteger bossFlags = new AtomicInteger(0x01);
        when(((TouchResponseProvider) boss).getCollisionFlags())
                .thenAnswer(ignored -> bossFlags.get());
        doAnswer(ignored -> {
            // ROM .enemy boss path writes collision_flags = 0 before returning.
            bossFlags.set(0);
            return null;
        }).when((TouchResponseAttackable) boss).onPlayerAttack(any(), any());
        Fixture bossFixture = new Fixture(List.of(boss), null);
        bossFixture.flock.update(1, bossFixture.owner);
        // The target lock is cleared, but the boss hit path also clears
        // collision_flags, so later birds reject it until its owner rearms it.
        verify((TouchResponseAttackable) boss, times(1))
                .onPlayerAttack(eq(bossFixture.p2), any());

        ObjectInstance special = target(0xC1, 0, PoweredScreenAttackSpecial.class);
        TouchResponseProvider specialProvider = (TouchResponseProvider) special;
        TouchResponseProfile specialProfile = mockSpecialProfile();
        when(specialProvider.getTouchResponseProfile()).thenReturn(specialProfile);
        Fixture specialFixture = new Fixture(List.of(special), null);
        specialFixture.flock.update(1, specialFixture.owner);
        // Touch_Special ORs property bit 1 but leaves the object targetable;
        // lock release permits all four birds to repeat the idempotent OR.
        verify((PoweredScreenAttackSpecial) special, times(4)).orCollisionProperty(2);
    }

    @Test
    void missingReservedIdentityIsReleasedAndSnapshotRecreationContinues() throws Exception {
        Fixture fixture = new Fixture(List.of(), null);
        fixture.setField("target0", ObjectRefId.dynamic(7, 2, 70));
        fixture.setField("angle0", 0x34);
        var snapshot = fixture.flock.captureRewindState(
                fixture.manager.captureIdentityContext());

        SuperTailsFlickyFlockObjectInstance restored =
                fixture.flock.recreateForRewind(new RewindRecreateContext(
                        fixture.flock.getSpawn(), snapshot, fixture.services, fixture.manager, null));
        restored.setServices(fixture.services);
        restored.restoreRewindState(snapshot, fixture.manager.captureIdentityContext());
        restored.update(2, fixture.owner);

        assertNull(read(restored, "target0"));
        assertEquals(120, readInt(restored, "delay0"));
        assertEquals(0x36, readInt(restored, "angle0"));
    }

    @Test
    void liveReservedTargetRebindsAndContinuesWithoutDuplicateReservation() throws Exception {
        ObjectInstance target = target(0x01, 0, PoweredScreenAttackable.class);
        Fixture fixture = new Fixture(List.of(target), null);
        when(target.getX()).thenReturn(fixture.owner.getCentreX() + 0x80);
        when(target.getY()).thenReturn(
                fixture.owner.getCentreY() - 0xC0);

        fixture.flock.update(1, fixture.owner);
        ObjectRefId reserved = fixture.flock.birdRuntimeState(0).target();
        assertNotNull(reserved);
        assertSame(target, fixture.identities.resolve(reserved));
        assertNull(fixture.flock.birdRuntimeState(1).target(),
                "the live ObjectRefId reservation excludes the same target from later birds");

        var snapshot = fixture.flock.captureRewindState(
                fixture.manager.captureIdentityContext());
        fixture.flock.update(2, fixture.owner);
        SuperTailsFlickyFlockObjectInstance.BirdRuntimeState expected =
                fixture.flock.birdRuntimeState(0);

        SuperTailsFlickyFlockObjectInstance restored =
                fixture.flock.recreateForRewind(new RewindRecreateContext(
                        fixture.flock.getSpawn(), snapshot, fixture.services, fixture.manager, null));
        restored.setServices(fixture.services);
        restored.restoreRewindState(snapshot, fixture.manager.captureIdentityContext());
        assertEquals(reserved, restored.birdRuntimeState(0).target());
        assertSame(target, fixture.identities.resolve(restored.birdRuntimeState(0).target()));
        assertNull(restored.birdRuntimeState(1).target());

        restored.update(2, fixture.owner);

        assertEquals(expected, restored.birdRuntimeState(0),
                "restored motion and reservation must continue identically");
        assertNull(restored.birdRuntimeState(1).target());
        verify((PoweredScreenAttackable) target, never()).onPoweredScreenAttack(any());
    }

    @Test
    void verticalAccelerationMatchesRomWrapAndTurnaroundBranches() {
        assertEquals(0x20,
                SuperTailsFlickyFlockObjectInstance.verticalAcceleration(4, false, 0));
        assertEquals(0x80,
                SuperTailsFlickyFlockObjectInstance.verticalAcceleration(4, false, -1));
        assertEquals(-0x20,
                SuperTailsFlickyFlockObjectInstance.verticalAcceleration(4, false, 0x1000));
        assertEquals(-0x80,
                SuperTailsFlickyFlockObjectInstance.verticalAcceleration(0x500, false, 1));
        assertEquals(0x80,
                SuperTailsFlickyFlockObjectInstance.verticalAcceleration(-0x500, true, -0x1000));
    }

    private static ObjectInstance target(int flags, int property, Class<?> response) {
        ObjectInstance target = mock(ObjectInstance.class,
                withSettings().extraInterfaces(TouchResponseProvider.class, response));
        when(target.isDestroyed()).thenReturn(false);
        when(target.getX()).thenReturn(0x100);
        when(target.getY()).thenReturn(0x100 - 0xC0);
        TouchResponseProvider provider = (TouchResponseProvider) target;
        when(provider.getCollisionFlags()).thenReturn(flags);
        when(provider.getCollisionProperty()).thenReturn(property);
        when(provider.getTouchResponseProfile()).thenReturn(TouchResponseProfile.standardEnemy());
        return target;
    }

    private static TouchResponseProfile mockSpecialProfile() {
        TouchResponseProvider provider = mock(TouchResponseProvider.class);
        when(provider.usesS3kTouchSpecialPropertyResponse()).thenReturn(true);
        return TouchResponseProfile.fromProvider(provider);
    }

    private static Object read(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static int readInt(Object target, String name) throws Exception {
        return (int) read(target, name);
    }

    private static final class Fixture {
        final Tails owner = new Tails("tails", (short) 0x100, (short) 0x100);
        final Tails p2 = new Tails("tails-p2", (short) 0, (short) 0);
        final Sonic3kSuperStateController controller = mock(Sonic3kSuperStateController.class);
        final ObjectManager manager = mock(ObjectManager.class);
        final RewindIdentityTable identities = new RewindIdentityTable();
        final RuntimeServices services;
        final SuperTailsFlickyFlockObjectInstance flock;

        Fixture(List<ObjectInstance> targets, ZoneRuntimeState runtime) {
            when(controller.getActiveFormTier()).thenReturn(S3kFormTier.SUPER_TAILS);
            owner.setSuperStateController(controller);
            Camera camera = mock(Camera.class);
            when(camera.getX()).thenReturn((short) 0);
            when(camera.getY()).thenReturn((short) 0);
            when(camera.screenYWrapValue()).thenReturn(0xFFFF);
            GameStateManager gameState = mock(GameStateManager.class);
            RewindCaptureContext context = RewindCaptureContext.withIdentityTable(identities);
            identities.registerPlayer(owner, PlayerRefId.mainPlayer());
            identities.registerPlayer(p2, PlayerRefId.sidekick(0));
            when(manager.captureIdentityContext()).thenReturn(context);
            when(manager.poweredAttackTargetReadView()).thenReturn(targets);
            for (int i = 0; i < targets.size(); i++) {
                when(targets.get(i).getX()).thenReturn((int) owner.getCentreX());
                when(targets.get(i).getY()).thenReturn(
                        owner.getCentreY() - 0xC0);
                identities.registerObject(targets.get(i), ObjectRefId.dynamic(i + 2, 1, i + 20));
            }
            services = new RuntimeServices(manager, camera, gameState, runtime, p2);
            flock = new SuperTailsFlickyFlockObjectInstance(owner);
            flock.setServices(services);
        }

        int[] fixedX() throws Exception {
            return new int[]{intField("x0"), intField("x1"), intField("x2"), intField("x3")};
        }

        Object field(String name) throws Exception { return read(flock, name); }
        int intField(String name) throws Exception { return readInt(flock, name); }
        boolean booleanField(String name) throws Exception { return (boolean) field(name); }
        void setField(String name, Object value) throws Exception {
            Field field = flock.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(flock, value);
        }
    }

    private static final class RuntimeServices extends TestObjectServices {
        private final ObjectManager manager;
        private final Camera camera;
        private final GameStateManager gameState;
        private final ZoneRuntimeState runtime;
        private final List<PlayableEntity> sidekicks;
        private ObjectRenderManager renderManager;

        RuntimeServices(ObjectManager manager, Camera camera, GameStateManager gameState,
                ZoneRuntimeState runtime, PlayableEntity p2) {
            this.manager = manager;
            this.camera = camera;
            this.gameState = gameState;
            this.runtime = runtime;
            this.sidekicks = List.of(p2);
        }

        RuntimeServices withRenderManager(ObjectRenderManager renderManager) {
            this.renderManager = renderManager;
            return this;
        }

        @Override public ObjectManager objectManager() { return manager; }
        @Override public ObjectRenderManager renderManager() { return renderManager; }
        @Override public Camera camera() { return camera; }
        @Override public GameStateManager gameState() { return gameState; }
        @Override public ZoneRuntimeState zoneRuntimeState() { return runtime; }
        @Override public List<PlayableEntity> sidekicks() { return sidekicks; }
    }
}
