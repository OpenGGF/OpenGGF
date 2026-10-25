package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Declared component-state inlets for HCZ's subtype-5 Obj_RobotnikShip2 leaves.
 * Native owners: sonic3k.asm Obj_RobotnikShip2Wait, Obj_RobotnikShipReady and
 * Obj_RobotnikShipEscape (136375-136482). Expected vectors come from those word
 * operations, rather than from a route frame or trace-derived coordinate.
 *
 * <p>Actual Head/Flame dispatch and graph coverage live in their maintained component
 * and world-graph tests. Knuckles art and whole-route behavior remain separate.
 */
class TestHczEndBossRobotnikShip {
    @Test
    void shipTwoWaitKeepsSubtypeFiveFrameWhenItStartsRisingAndEscaping() {
        Fixture fixture = Fixture.create(0x100);
        HczEndBossRobotnikShip ship = fixture.ship();
        seedReady(ship, 0x140, 0x1234);
        setInt(ship, "routine", 4);
        setField(ship, "boss", fixture.boss());
        fixture.boss().getState().routine = 15;
        ship.update(0, null);
        assertEquals(4, getInt(ship, "routine"));
        assertEquals(5, getInt(ship, "shipFrame"));

        fixture.boss().getState().routine = 16;
        ship.update(1, null);
        assertEquals(6, getInt(ship, "routine"));
        assertEquals(5, getInt(ship, "shipFrame"),
                "Obj_RobotnikShip2Wait writes 5, unlike Obj_RobotnikShipWait's $A");
        ship.update(2, null);
        assertEquals(8, getInt(ship, "routine"));
        assertEquals(5, getInt(ship, "shipFrame"));
    }

    @Test
    void onePixelAboveTargetDecrementsWithoutStartingEscapeUntilNextDispatch() {
        Fixture fixture = Fixture.create(0x100);
        HczEndBossRobotnikShip ship = fixture.ship();
        seedReady(ship, 0x141, 0xBEEF);
        ship.update(0, null);
        assertEquals(0x140, ship.getY());
        assertEquals(0x0140BEEF, getInt(ship, "yFixed"));
        assertEquals(6, getInt(ship, "routine"),
                "the pre-decrement cmp has already selected this dispatch's return");
        assertEquals(0, getInt(ship, "timer"));

        ship.update(1, null);
        assertEquals(0x140, ship.getY());
        assertEquals(0x0140BEEF, getInt(ship, "yFixed"));
        assertEscapeStarted(ship);
    }

    @Test
    void equalTargetStartsEscapeWithoutMovingOrClearingTheFraction() {
        Fixture fixture = Fixture.create(0x100);
        seedReady(fixture.ship(), 0x140, 0x9ABC);
        fixture.ship().update(0, null);
        assertEquals(0x140, fixture.ship().getY());
        assertEquals(0x01409ABC, getInt(fixture.ship(), "yFixed"));
        assertEscapeStarted(fixture.ship());
    }

    @Test
    void alreadyAboveTargetStartsEscapeWithoutClampingItsPosition() {
        Fixture fixture = Fixture.create(0x100);
        seedReady(fixture.ship(), 0x13E, 0x5678);
        fixture.ship().update(0, null);
        assertEquals(0x13E, fixture.ship().getY(), "bhs selects escape without a y_pos write");
        assertEquals(0x013E5678, getInt(fixture.ship(), "yFixed"));
        assertEscapeStarted(fixture.ship());
    }

    @Test
    void cameraAdditionWrapsAsAWordAndTheComparisonTreatsShipYAsUnsigned() {
        Fixture wrapped = Fixture.create(0xFFF0);
        seedReady(wrapped.ship(), 0x31, 0x1357);
        wrapped.ship().update(0, null);
        assertEquals(0x30, wrapped.ship().getY(), "$FFF0+$40 wraps to $0030");
        assertEquals(0x00301357, getInt(wrapped.ship(), "yFixed"));
        assertEquals(6, getInt(wrapped.ship(), "routine"));
        wrapped.ship().update(1, null);
        assertEscapeStarted(wrapped.ship());

        Fixture unsigned = Fixture.create(0x7FBF);
        seedReady(unsigned.ship(), 0x8000, 0x2468);
        unsigned.ship().update(0, null);
        assertEquals(0x7FFF, unsigned.ship().getY(),
                "$8000 is unsigned-above the $7FFF target, so subq.w must run");
        assertEquals(0x7FFF2468, getInt(unsigned.ship(), "yFixed"));
        assertEquals(6, getInt(unsigned.ship(), "routine"));
    }

    @Test
    void escapeUnderflowSelectsDeletionAndStillDrawsUntilTheNextDispatch() {
        Fixture fixture = Fixture.create(0x100);
        HczEndBossRobotnikShip ship = fixture.ship();
        seedEscape(ship, 0x100, 0x4000BEEF);
        for (int dispatch = 0; dispatch < 0x100; dispatch++) {
            ship.update(dispatch, null);
            assertFalse(ship.isDestroyed(), "zero has not taken the native bmi branch");
        }
        assertEquals(0, getInt(ship, "timer"));
        assertEquals(0x4300, ship.getX());
        assertEquals(0x4300BEEF, getInt(ship, "xFixed"));
        ship.update(0x100, null);
        assertEquals(-1, getInt(ship, "timer"));
        assertEquals(0x4303, ship.getX(), "MoveSprite2 precedes the timer/delete branch");
        assertEquals(0x4303BEEF, getInt(ship, "xFixed"));
        assertFalse(ship.isDestroyed(), "Go_Delete_Sprite_2 selects the NEXT code pointer");
        assertTrue(getBoolean(ship, "pendingDelete"));
        ship.update(0x101, null);
        assertTrue(ship.isDestroyed());
        assertEquals(0x4303BEEF, getInt(ship, "xFixed"), "delete dispatch does not move again");
    }

    @Test
    void escapeCountdownSubtractionUsesTheSignedResultOfAWordOperation() {
        Fixture fixture = Fixture.create(0x100);
        seedEscape(fixture.ship(), 0x8000, 0x4000ABCD);
        fixture.ship().update(0, null);
        assertEquals(0x7FFF, getInt(fixture.ship(), "timer"),
                "subq.w turns $8000 into positive $7FFF before bmi tests N");
        assertFalse(fixture.ship().isDestroyed());
    }

    @Test
    void readyBoundaryCapturesRecreatesAndRepeatsTheSameNativeForwardVectorTwice() {
        Fixture fixture = Fixture.create(0x100);
        seedReady(fixture.ship(), 0x142, 0xBEEF);
        fixture.ship().update(0, null); // Reach the declared target+1 capture boundary.
        RewindCaptureContext context = fixture.manager().captureIdentityContext();
        PerObjectRewindSnapshot snapshot = fixture.ship().captureRewindState(context);
        List<ShipStep> expected = List.of(
                new ShipStep(0x4000, 0x140, 0x40001234, 0x0140BEEF, 6, 0, false),
                new ShipStep(0x4000, 0x140, 0x40001234, 0x0140BEEF, 8, 0x100, false),
                new ShipStep(0x4003, 0x140, 0x40031234, 0x0140BEEF, 8, 0xFF, false),
                new ShipStep(0x4006, 0x140, 0x40061234, 0x0140BEEF, 8, 0xFE, false));
        for (int cycle = 0; cycle < 2; cycle++) {
            HczEndBossRobotnikShip restored = recreate(fixture, snapshot, context);
            for (int dispatch = 1; dispatch <= expected.size(); dispatch++) {
                restored.update(dispatch, null);
                assertEquals(expected.get(dispatch - 1), step(restored),
                        "captured READY boundary forward dispatch " + dispatch);
            }
        }
    }

    @Test
    void zeroCountdownCapturesRecreatesAndRepeatsMovementThenDeletionTwice() {
        Fixture fixture = Fixture.create(0x100);
        seedEscape(fixture.ship(), 1, 0x4000BEEF);
        fixture.ship().update(0, null);
        assertEquals(0, getInt(fixture.ship(), "timer"));
        assertFalse(fixture.ship().isDestroyed());
        RewindCaptureContext context = fixture.manager().captureIdentityContext();
        PerObjectRewindSnapshot snapshot = fixture.ship().captureRewindState(context);
        for (int cycle = 0; cycle < 2; cycle++) {
            HczEndBossRobotnikShip restored = recreate(fixture, snapshot, context);
            assertEquals(new ShipStep(0x4003, 0x140, 0x4003BEEF, 0x01405678, 8, 0, false),
                    step(restored));
            restored.update(1, null);
            assertEquals(new ShipStep(0x4006, 0x140, 0x4006BEEF, 0x01405678, 8, -1, false),
                    step(restored));
            assertTrue(getBoolean(restored, "pendingDelete"));
            restored.update(2, null);
            assertTrue(restored.isDestroyed());
        }
    }

    @Test
    void pendingDeleteSnapshotRecreatesTheDrawTailAndDeletesWithoutMovingTwice() {
        Fixture fixture = Fixture.create(0x100);
        seedEscape(fixture.ship(), 0, 0x4000BEEF);
        fixture.ship().update(0, null);
        RewindCaptureContext context = fixture.manager().captureIdentityContext();
        PerObjectRewindSnapshot snapshot = fixture.ship().captureRewindState(context);
        for (int cycle = 0; cycle < 2; cycle++) {
            HczEndBossRobotnikShip restored = recreate(fixture, snapshot, context);
            assertTrue(getBoolean(restored, "visible"));
            assertTrue(getBoolean(restored, "pendingDelete"));
            assertFalse(restored.isDestroyed());
            restored.update(1, null);
            assertTrue(restored.isDestroyed());
            assertEquals(0x4003BEEF, getInt(restored, "xFixed"));
        }
    }

    private static void assertEscapeStarted(HczEndBossRobotnikShip ship) {
        assertEquals(8, getInt(ship, "routine"));
        assertEquals(0x300, getInt(ship, "xVel"));
        assertEquals(0, getInt(ship, "yVel"));
        assertEquals(0x100, getInt(ship, "timer"));
        assertTrue(getBoolean(ship, "shipFacingRight"));
        assertFalse(ship.isDestroyed());
    }

    static void seedReady(HczEndBossRobotnikShip ship, int yWord, int fraction) {
        ship.setPosition(0x4000, yWord);
        setField(ship, "boss", null);
        setInt(ship, "xFixed", 0x40001234);
        setInt(ship, "yFixed", (yWord << 16) | fraction);
        setInt(ship, "yVel", -0x100);
        setInt(ship, "routine", 6);
    }

    static void seedEscape(HczEndBossRobotnikShip ship, int countdown, int xFixed) {
        ship.setPosition(xFixed >>> 16, 0x140);
        setField(ship, "boss", null);
        setInt(ship, "xFixed", xFixed);
        setInt(ship, "yFixed", 0x01405678);
        setInt(ship, "xVel", 0x300);
        setInt(ship, "timer", countdown);
        setInt(ship, "routine", 8);
    }

    private static HczEndBossRobotnikShip recreate(
            Fixture fixture, PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        HczEndBossRobotnikShip restored = ObjectConstructionContext.construct(fixture.services(),
                () -> fixture.ship().recreateForRewind(new RewindRecreateContext(
                        fixture.ship().getSpawn(), snapshot, fixture.services(), fixture.manager(), null)));
        assertNotNull(restored);
        restored.setServices(fixture.services());
        restored.restoreRewindState(snapshot, context);
        assertSame(getField(fixture.ship(), "boss"), getField(restored, "boss"),
                "the actual captured phase-owned reference hydrates after pure shell creation");
        // Forward READY may allocate a Flame, so the restored component itself
        // must be a real managed owner rather than an unregistered test clone.
        for (var flame : List.copyOf(fixture.manager().activeObjectsOfType(HczEndBossRobotnikFlame.class))) {
            fixture.manager().removeDynamicObject(flame);
        }
        for (var oldShip : List.copyOf(fixture.manager().activeObjectsOfType(HczEndBossRobotnikShip.class))) {
            fixture.manager().removeDynamicObject(oldShip);
        }
        fixture.manager().addDynamicObjectAtSlot(restored, snapshot.slotIndex());
        return restored;
    }

    private static ShipStep step(HczEndBossRobotnikShip ship) {
        return new ShipStep(ship.getX(), ship.getY(), getInt(ship, "xFixed"), getInt(ship, "yFixed"),
                getInt(ship, "routine"), getInt(ship, "timer"), ship.isDestroyed());
    }

    private record ShipStep(int x, int y, int xFixed, int yFixed, int routine, int timer,
                            boolean destroyed) {
    }

    record Fixture(TestObjectServices services, ObjectManager manager,
                           HczEndBossInstance boss, HczEndBossRobotnikShip ship) {
        static Fixture create(int cameraYWord) {
            return create(cameraYWord, null);
        }

        static Fixture create(int cameraYWord, com.openggf.level.render.PatternSpriteRenderer renderer) {
            Camera camera = mock(Camera.class);
            when(camera.getY()).thenReturn((short) cameraYWord);
            TestObjectServices services = new TestObjectServices() {
                @Override public com.openggf.level.objects.ObjectRenderManager renderManager() {
                    return new com.openggf.level.objects.ObjectRenderManager(null) {
                        @Override public com.openggf.level.render.PatternSpriteRenderer getRenderer(String key) {
                            return com.openggf.game.sonic3k.Sonic3kObjectArtKeys.ROBOTNIK_SHIP.equals(key)
                                    ? renderer : null;
                        }
                    };
                }
            }
                    .withConfiguration(SonicConfigurationService.createStandalone())
                    .withCamera(camera)
                    .withGraphicsManager(mock(com.openggf.graphics.GraphicsManager.class))
                    .withIsolatedObjectManager();
            ObjectManager manager = services.objectManager();
            var earlier = manager.createDynamicObject(() -> new com.openggf.level.objects.AbstractObjectInstance(
                    new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "earlier-slot") {
                        @Override public void appendRenderCommands(
                                List<com.openggf.graphics.GLCommand> commands) { }
                    });
            HczEndBossInstance boss = manager.createDynamicObject(() -> new HczEndBossInstance(
                    new ObjectSpawn(0x4000, 0x738, 0x9A, 0, 0, false, 0x738)));
            assertNotNull(boss);
            HczEndBossRobotnikShip ship = manager.createDynamicObject(
                    () -> new HczEndBossRobotnikShip(boss));
            assertNotNull(ship);
            manager.removeDynamicObject(earlier); // A genuine lower free slot for after-current failure cases.
            return new Fixture(services, manager, boss, ship);
        }
    }

    static int getInt(Object target, String name) {
        try {
            return field(target, name).getInt(target);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    static boolean getBoolean(Object target, String name) {
        try {
            return field(target, name).getBoolean(target);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    static Object getField(Object target, String name) {
        try {
            return field(target, name).get(target);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    static void setInt(Object target, String name, int value) {
        try {
            field(target, name).setInt(target, value);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    static void setField(Object target, String name, Object value) {
        try {
            field(target, name).set(target, value);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private static Field field(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Scalar position and once-per-dispatch bookkeeping live in the base child.
            }
        }
        throw new AssertionError("Missing fixture field " + name);
    }
}
