package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.ObjectSolidExecutionContext;
import com.openggf.game.solid.PlayerStandingState;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.physics.Direction;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tools.Sonic2ObjectProfile;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestOOZPlacedObjectGaps {

    @Test
    void registryAndDiscoveryProfileCoverPlacedOozObjectIds() {
        Sonic2ObjectRegistry registry = new Sonic2ObjectRegistry();

        assertEquals(0x43, Sonic2ObjectIds.SLIDING_SPIKE);
        assertEquals(0x45, Sonic2ObjectIds.OOZ_SPRING);
        assertFalse(registry.hasRegisteredFactory(0x46),
                "Obj46 is an unused beta leftover, not the child allocated by Obj45");
        assertTrue(registry.hasRegisteredFactory(0x43));
        assertTrue(registry.hasRegisteredFactory(0x45));

        Sonic2ObjectProfile profile = new Sonic2ObjectProfile();
        assertTrue(profile.getImplementedIds().contains(0x43));
        assertTrue(profile.getImplementedIds().contains(0x45));
        assertFalse(profile.getImplementedIds().contains(0x46),
                "Obj46 should stay out of implemented placed-object coverage unless its unused beta routine is ported");
    }

    @Test
    void slidingSpikeSubtypesUseRomOffsetsAndOnePixelMotion() throws Exception {
        ObjectInstance subtype00 = newSlidingSpike(0x1000, 0x0500, 0x00);
        assertEquals(0x1000, subtype00.getX());
        assertEquals(0x0F98, intField(subtype00, "minX"));
        assertEquals(0x1068, intField(subtype00, "maxX"));

        ObjectInstance subtype06 = newSlidingSpike(0x1000, 0x0500, 0x06);
        assertEquals(0x0FE8, subtype06.getX(), "Obj43 subtype 06 parent starts at -$18 from origin");
        assertEquals(0x0FE8, intField(subtype06, "minX"));
        assertEquals(0x1018, intField(subtype06, "maxX"));
        assertInstanceOf(TouchResponseProvider.class, subtype06);
        assertEquals(0xA5, ((TouchResponseProvider) subtype06).getCollisionFlags());

        subtype06.update(0, playerAt(0x1000, 0x0500));
        assertEquals(0x0FE7, subtype06.getX(), "Obj43 moves one pixel toward its left bound when direction is clear");

        ObjectInstance subtype0C = newSlidingSpike(0x1000, 0x0500, 0x0C);
        assertEquals(0x0FA8, subtype0C.getX(), "Obj43 subtype 0C parent starts at -$58 from origin");
        assertEquals(0x0FA8, intField(subtype0C, "minX"));
        assertEquals(0x1058, intField(subtype0C, "maxX"));
    }

    @Test
    void oozPressureSpringSubtypesAreHorizontalStrongSprings() throws Exception {
        ObjectInstance subtype10 = newOOZSpring(0x1000, 0x0500, 0x10);
        ObjectInstance subtype30 = newOOZSpring(0x1000, 0x0500, 0x30);

        assertInstanceOf(SolidObjectProvider.class, subtype10);
        assertInstanceOf(SolidObjectProvider.class, subtype30);
        SolidObjectParams params = ((SolidObjectProvider) subtype10).getSolidParams();
        assertEquals(31, params.halfWidth());
        assertEquals(12, params.airHalfHeight());
        assertEquals(13, params.groundHalfHeight());
        assertEquals(SolidExecutionMode.MANUAL_CHECKPOINT,
                ((SolidObjectProvider) subtype10).solidExecutionMode(),
                "Obj45 runs SolidObject before its release logic in the ROM routine");

        assertTrue(booleanField(subtype10, "horizontal"));
        assertTrue(booleanField(subtype30, "horizontal"),
                "Subtype $30 depends on the ROM's out-of-table fall-through into horizontal init");
        assertEquals(-0x1000, intField(subtype10, "strength"));
        assertEquals(-0x1000, intField(subtype30, "strength"));
        assertEquals(0x0A, intField(subtype10, "mappingFrame"));
    }

    @Test
    void horizontalOozPressureSpringCompressesOnePixelBeforeReleaseLaunch() throws Exception {
        ObjectInstance spring = newOOZSpring(0x1000, 0x0500, 0x10);
        TestablePlayableSprite player = playerAt(0x101C, 0x0500);
        player.setDirection(Direction.LEFT);
        player.setGSpeed((short) -0x40);

        ((SolidObjectListener) spring).onSolidContact(player,
                new SolidContact(false, true, false, false, true, 1, true), 0);

        assertEquals(0x0FFF, spring.getX());
        assertEquals(0x0B, intField(spring, "mappingFrame"));

        spring.update(1, player);

        assertEquals(0x1000, spring.getX(), "released Obj45 snaps four pixels toward origin and clamps at base");
        assertEquals(0x0A, intField(spring, "mappingFrame"));
        assertEquals(0x0500, player.getXSpeed() & 0xFFFF,
                "release strength is (compression+$A)<<7, directed by the spring facing");
        assertEquals(0x0500, player.getGSpeed() & 0xFFFF);
        assertFalse(player.getPushing());
    }

    @Test
    void verticalOozPressureSpringAppliesFlipPlaneAndBit7SubtypeEffects() throws Exception {
        ObjectInstance spring = newOOZSpring(0x1000, 0x0500, 0x85);
        TestablePlayableSprite player = playerAt(0x1000, 0x04EC);
        player.setDirection(Direction.LEFT);
        player.setXSpeed((short) 0x0320);
        player.setHurt(true);
        setIntField(spring, "mappingFrame", 9);

        ((SolidObjectListener) spring).onSolidContact(player,
                new SolidContact(true, false, false, false, false), 0);

        assertEquals(0xF000, player.getYSpeed() & 0xFFFF);
        assertEquals(0, player.getXSpeed());
        assertTrue(player.getAir());
        assertFalse(player.isOnObject());
        assertFalse(player.isHurt(), "Obj45 writes routine #2 on vertical launch, clearing the hurt routine");
        assertEquals(Sonic2AnimationIds.WALK.id(), player.getAnimationId());
        assertEquals(0xFF, player.getFlipAngle());
        assertEquals(1, player.getFlipsRemaining());
        assertEquals(4, player.getFlipSpeed());
        assertEquals(0xFFFF, player.getGSpeed() & 0xFFFF);
        assertEquals(0x0C, player.getTopSolidBit() & 0xFF);
        assertEquals(0x0D, player.getLrbSolidBit() & 0xFF);
    }

    @Test
    void verticalOozPressureSpringUsesWeakStrengthAndAlternatePlaneBits() throws Exception {
        ObjectInstance spring = newOOZSpring(0x1000, 0x0500, 0x0A);
        TestablePlayableSprite player = playerAt(0x1000, 0x04EC);
        setIntField(spring, "mappingFrame", 9);

        ((SolidObjectListener) spring).onSolidContact(player,
                new SolidContact(true, false, false, false, false), 0);

        assertEquals(0xF600, player.getYSpeed() & 0xFFFF);
        assertEquals(0x0E, player.getTopSolidBit() & 0xFF);
        assertEquals(0x0F, player.getLrbSolidBit() & 0xFF);
    }

    @Test
    void verticalOozPressureSpringCompressesOnlyOnceWhenBothNativePlayersWereStanding() throws Exception {
        ObjectInstance spring = newObject("com.openggf.game.sonic2.objects.OOZSpringObjectInstance",
                new ObjectSpawn(0x1000, 0x0500, 0x45, 0x00, 0, false, 0));
        TestablePlayableSprite sonic = playerAt(0x1000, 0x04EC);
        TestablePlayableSprite tails = playerAt(0x1004, 0x04EC);
        ((AbstractObjectInstance) spring).setServices(new TestObjectServices()
                .withSidekicks(List.of(tails))
                .withSolidExecutionRegistry(new BothPlayersStandingRegistry()));

        spring.update(1, sonic);

        assertEquals(1, intField(spring, "mappingFrame"),
                "Obj45 tests standing_mask once before SolidObject45, so two riders compress one frame");
    }

    @Test
    void horizontalOozPressureSpringAppliesFlipSubtypeAfterReleaseLaunch() throws Exception {
        ObjectInstance spring = newOOZSpring(0x1000, 0x0500, 0x11);
        TestablePlayableSprite player = playerAt(0x101C, 0x0500);
        player.setDirection(Direction.LEFT);
        player.setGSpeed((short) -0x40);

        ((SolidObjectListener) spring).onSolidContact(player,
                new SolidContact(false, true, false, false, true, 1, true), 0);
        spring.update(1, player);

        assertEquals(0x0500, player.getXSpeed() & 0xFFFF);
        assertEquals(1, player.getGSpeed(), "Obj45 bit 0 overwrites inertia with flip inertia after x_vel");
        assertEquals(1, player.getFlipAngle());
        assertEquals(3, player.getFlipsRemaining());
        assertEquals(8, player.getFlipSpeed());
        assertEquals(Sonic2AnimationIds.WALK.id(), player.getAnimationId());
    }

    @Test
    void flippedHorizontalOozPressureSpringLaunchesLeftAndCanClearYSpeedAndSwitchPlane() throws Exception {
        ObjectInstance spring = newOOZSpring(0x1000, 0x0500, 0x99, 1);
        TestablePlayableSprite player = playerAt(0x0FE4, 0x0500);
        player.setDirection(Direction.RIGHT);
        player.setGSpeed((short) 0x40);
        player.setYSpeed((short) 0x0340);

        ((SolidObjectListener) spring).onSolidContact(player,
                new SolidContact(false, true, false, false, true, -1, true), 0);
        spring.update(1, player);

        assertEquals(0xFB00, player.getXSpeed() & 0xFFFF);
        assertEquals(0, player.getYSpeed());
        assertEquals(Direction.LEFT, player.getDirection());
        assertEquals(0xFF, player.getFlipAngle());
        assertEquals(3, player.getFlipsRemaining());
        assertEquals(8, player.getFlipSpeed());
        assertEquals(0x0E, player.getTopSolidBit() & 0xFF);
        assertEquals(0x0F, player.getLrbSolidBit() & 0xFF);
    }

    private static ObjectInstance newSlidingSpike(int x, int y, int subtype) throws Exception {
        ObjectInstance object = newObject("com.openggf.game.sonic2.objects.SlidingSpikeObjectInstance",
                new ObjectSpawn(x, y, 0x43, subtype, 0, false, 0));
        ((AbstractObjectInstance) object).setServices(new TestObjectServices());
        return object;
    }

    private static ObjectInstance newOOZSpring(int x, int y, int subtype) throws Exception {
        return newOOZSpring(x, y, subtype, 0);
    }

    private static ObjectInstance newOOZSpring(int x, int y, int subtype, int renderFlags) throws Exception {
        ObjectInstance object = newObject("com.openggf.game.sonic2.objects.OOZSpringObjectInstance",
                new ObjectSpawn(x, y, 0x45, subtype, renderFlags, false, 0));
        ((AbstractObjectInstance) object).setServices(new TestObjectServices());
        return object;
    }

    private static ObjectInstance newObject(String className, ObjectSpawn spawn) throws Exception {
        Class<?> type = Class.forName(className);
        Constructor<?> ctor = type.getConstructor(ObjectSpawn.class, String.class);
        return (ObjectInstance) ctor.newInstance(spawn, "test");
    }

    private static TestablePlayableSprite playerAt(int x, int y) {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) x, (short) y);
        player.setCentreX((short) x);
        player.setCentreY((short) y);
        player.setAir(false);
        return player;
    }

    private static int intField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static boolean booleanField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static void setIntField(Object target, String fieldName, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static final class BothPlayersStandingRegistry implements SolidExecutionRegistry {
        @Override public void beginFrame(int frameCounter, List<? extends com.openggf.game.PlayableEntity> players) {}
        @Override public void beginObject(ObjectInstance object, ObjectSolidExecutionContext.Resolver resolver) {}
        @Override public ObjectSolidExecutionContext currentObject() { return ObjectSolidExecutionContext.inert(); }
        @Override public PlayerStandingState previousStanding(ObjectInstance object, com.openggf.game.PlayableEntity player) {
            return new PlayerStandingState(ContactKind.TOP, true, false);
        }
        @Override public void publishCheckpoint(SolidCheckpointBatch batch) {}
        @Override public void endObject(ObjectInstance object) {}
        @Override public void finishFrame() {}
        @Override public void clearTransientState() {}
    }
}
