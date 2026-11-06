package com.openggf.tests;

import com.openggf.game.PlayableEntity;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.ObjectSolidExecutionContext;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.PostContactState;
import com.openggf.game.solid.PreContactState;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.game.solid.PlayerStandingState;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tools.Sonic3kObjectProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TestS3kIczTensionPlatformObject {
    private static final int ICZ_TENSION_PLATFORM_ID = 0xBA;

    // Clear any gameplay session leaked by a prior test in this fork so the registry
    // resolves the S3KL zone set (not a leaked SKL zone). Parallel-suite flake fix.
    @BeforeEach
    void clearLeakedGameplaySession() {
        com.openggf.game.session.SessionManager.clear();
    }

    @Test
    void registryCreatesTensionPlatformAndProfileMarksS3klSlotImplemented() {
        ObjectInstance instance = new Sonic3kObjectRegistry().create(spawn(0x1000, 0x1000));

        assertNotEquals("PlaceholderObjectInstance", instance.getClass().getSimpleName());
        assertTrue(new Sonic3kObjectProfile().getImplementedIds().contains(ICZ_TENSION_PLATFORM_ID));
    }

    @Test
    void solidGeometryMatchesRomSolidObjectTopCall() {
        ObjectInstance instance = new Sonic3kObjectRegistry().create(spawn(0x1000, 0x1000));
        SolidObjectProvider solid = assertInstanceOf(SolidObjectProvider.class, instance);

        SolidObjectParams params = solid.getSolidParams();

        assertTrue(solid.isTopSolidOnly());
        assertEquals(SolidExecutionMode.MANUAL_CHECKPOINT, solid.solidExecutionMode());
        assertTrue(solid.usesGroundHalfHeightForTopSolidContact());
        assertTrue(solid.rejectsZeroDistanceTopSolidLanding());
        assertEquals(0x23, params.halfWidth());
        assertEquals(0x14, params.airHalfHeight());
        assertEquals(0x0B, params.groundHalfHeight());
        assertEquals(0x18, ((AbstractObjectInstance) instance).getBalanceWidthPixels(),
                "Sonic_Move must read ObjDat width_pixels, not SolidObjectTop's extended d1");
    }

    @Test
    void firstLandingSetsSpringTargetAndDampsPlayerVelocityIntoPlatformVelocity() throws Exception {
        ObjectInstance instance = new Sonic3kObjectRegistry().create(spawn(0x1000, 0x1000));
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x1000, (short) 0x0FE0);
        ScriptedSolidServices services = new ScriptedSolidServices(instance, null, null);
        services.withPlayerQuery(new ObjectPlayerQuery(() -> player, List::of));
        services.batch = batch(instance, player, true, false, 0x0400);
        setServices(instance, services);

        instance.update(1, player);

        assertEquals(0x1008, invokeInt(instance, "getTargetYForTesting"));
        assertEquals(0x0300, invokeInt(instance, "getYVelocityForTesting"));
        assertTrue((Boolean) instance.getClass().getMethod("isSpringActiveForTesting").invoke(instance));
    }

    @Test
    void leavingRidersConsumeSharedVelocityInNativeOrderRegardlessOfMapOrder() throws Exception {
        assertLeavingRiderOrder(2);
    }

    @Test
    void extraSidekickConsumesVelocityAfterNativeP1AndP2() throws Exception {
        assertLeavingRiderOrder(3);
    }

    @Test
    void participantMismatchFailsBeforeChangingTheSpring() throws Exception {
        ObjectInstance instance = new Sonic3kObjectRegistry().create(spawn(0x1000, 0x1000));
        var recorded = new TestablePlayableSprite("sonic", (short) 0x1000, (short) 0x0FE0);
        var different = new TestablePlayableSprite("tails", (short) 0x1000, (short) 0x0FE0);
        var services = new ScriptedSolidServices(instance, batch(instance, recorded, true, false, 0x400), null);
        services.withPlayerQuery(new ObjectPlayerQuery(() -> different, List::of));
        setServices(instance, services);
        assertThrows(IllegalStateException.class, () -> instance.update(1, different));
        assertEquals(0x1000, invokeInt(instance, "getTargetYForTesting"));
        assertEquals(0, invokeInt(instance, "getYVelocityForTesting"));
        assertEquals(false, instance.getClass().getMethod("isSpringActiveForTesting").invoke(instance));
    }

    private static void assertLeavingRiderOrder(int count) throws Exception {
        ObjectInstance instance = new Sonic3kObjectRegistry().create(spawn(0x1000, 0x1000));
        var contacts = new IdentityHashMap<PlayableEntity, PlayerSolidContactResult>();
        for (int i = 0; i < count; i++) {
            var player = new TestablePlayableSprite("player" + i, (short) 0x1000, (short) 0x0FE0);
            player.setYSpeed((short) -0x400);
            contacts.put(player, new PlayerSolidContactResult(
                    ContactKind.NONE, false, true, false, false,
                    new PreContactState((short) 0, (short) -0x400, false, 0),
                    new PostContactState((short) 0, (short) -0x400, true, false, false), 0));
        }
        var checkpoint = new SolidCheckpointBatch(instance, contacts);
        var mapOrder = List.copyOf(checkpoint.perPlayer().keySet());
        // Assign semantic P1/P2 only after the real defensive identity-map copy.
        // P1 is deliberately last in its traversal: no random allocation search
        // or assumption about a JVM's identity hashes can make this pass by chance.
        PlayableEntity p1 = mapOrder.getLast();
        List<PlayableEntity> sidekicks = mapOrder.subList(0, count - 1);
        var services = new ScriptedSolidServices(instance, checkpoint, null);
        services.withPlayerQuery(new ObjectPlayerQuery(() -> p1, () -> sidekicks));
        setServices(instance, services);

        instance.update(1, p1);

        // sub_8BA7E adds the current platform speed, then damps that shared
        // speed before the next player. Starting at zero gives $300, $540, $6F0.
        assertEquals((short) -0x400, p1.getYSpeed());
        assertEquals((short) -0x100, sidekicks.getFirst().getYSpeed());
        if (count == 3) assertEquals((short) 0x140, sidekicks.get(1).getYSpeed());
        assertEquals(count == 2 ? 0x540 : 0x6F0, invokeInt(instance, "getYVelocityForTesting"));
        assertEquals(0x1000, invokeInt(instance, "getTargetYForTesting"));
        assertEquals(true, instance.getClass().getMethod("isSpringActiveForTesting").invoke(instance));
    }

    @Test
    void firstUpdateSpawnsTwoSupportChildrenAtRomOffsets() {
        ObjectInstance instance = new Sonic3kObjectRegistry().create(spawn(0x1000, 0x1000));
        ObjectManager objectManager = mock(ObjectManager.class);
        setServices(instance, new ScriptedSolidServices(instance, emptyBatch(instance), objectManager));

        instance.update(1, new TestablePlayableSprite("sonic", (short) 0x1000, (short) 0x0FE0));

        ArgumentCaptor<AbstractObjectInstance> childCaptor = ArgumentCaptor.forClass(AbstractObjectInstance.class);
        verify(objectManager, times(2)).addDynamicObjectAfterCurrent(childCaptor.capture());
        List<AbstractObjectInstance> children = childCaptor.getAllValues();
        assertEquals(0x0FC8, children.get(0).getX());
        assertEquals(0x1000, children.get(0).getY());
        assertEquals(0x1038, children.get(1).getX());
        assertEquals(0x1000, children.get(1).getY());
    }

    private static ObjectSpawn spawn(int x, int y) {
        return new ObjectSpawn(x, y, ICZ_TENSION_PLATFORM_ID, 0, 0, false, 0);
    }

    private static SolidCheckpointBatch emptyBatch(ObjectInstance instance) {
        return new SolidCheckpointBatch(instance, Map.of());
    }

    private static SolidCheckpointBatch batch(ObjectInstance instance, PlayableEntity player,
            boolean standingNow, boolean standingLastFrame, int preContactYSpeed) {
        IdentityHashMap<PlayableEntity, PlayerSolidContactResult> map = new IdentityHashMap<>();
        map.put(player, new PlayerSolidContactResult(
                standingNow ? ContactKind.TOP : ContactKind.NONE,
                standingNow,
                standingLastFrame,
                false,
                false,
                new PreContactState((short) 0, (short) preContactYSpeed, false, 0),
                new PostContactState((short) 0, (short) 0, !standingNow, standingNow, false),
                0));
        return new SolidCheckpointBatch(instance, map);
    }

    private static void setServices(ObjectInstance instance, StubObjectServices services) {
        assertInstanceOf(AbstractObjectInstance.class, instance);
        ((AbstractObjectInstance) instance).setServices(services);
    }

    private static int invokeInt(ObjectInstance instance, String name) throws Exception {
        Method method = instance.getClass().getMethod(name);
        return (Integer) method.invoke(instance);
    }

    private static final class ScriptedSolidServices extends StubObjectServices {
        private final ObjectSolidExecutionContext context;
        private final ObjectManager objectManager;
        private SolidCheckpointBatch batch;

        ScriptedSolidServices(ObjectInstance instance, SolidCheckpointBatch batch, ObjectManager objectManager) {
            this.batch = batch;
            this.objectManager = objectManager;
            this.context = new ObjectSolidExecutionContext(new NoopSolidExecutionRegistry(), instance,
                    call -> this.batch != null ? this.batch : emptyBatch(instance));
        }

        @Override
        public ObjectSolidExecutionContext solidExecution() {
            return context;
        }

        @Override
        public ObjectManager objectManager() {
            return objectManager;
        }
    }

    private static final class NoopSolidExecutionRegistry implements SolidExecutionRegistry {
        @Override public void beginFrame(int frameCounter, List<? extends PlayableEntity> players) {}
        @Override public void beginObject(ObjectInstance object, ObjectSolidExecutionContext.Resolver resolver) {}
        @Override public ObjectSolidExecutionContext currentObject() { return ObjectSolidExecutionContext.inert(); }
        @Override public PlayerStandingState previousStanding(ObjectInstance object, PlayableEntity player) {
            return PlayerStandingState.NONE;
        }
        @Override public void publishCheckpoint(SolidCheckpointBatch batch) {}
        @Override public void endObject(ObjectInstance object) {}
        @Override public void finishFrame() {}
        @Override public void clearTransientState() {}
    }
}
