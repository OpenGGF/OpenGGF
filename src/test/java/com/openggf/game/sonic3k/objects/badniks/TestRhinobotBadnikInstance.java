package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRhinobotBadnikInstance {

    @Test
    void waitOffscreenUsesRomPlaceholderWidth() throws Exception {
        TestPlayer sonic = player(0x03D0, 0x0200);
        try {
            AbstractObjectInstance.updateCameraBounds(0, 0, 0x03DF, 0x0400, 0);
            RhinobotBadnikInstance outside = rhinobotWithPlayers(sonic, null);
            updateMovement(outside, sonic);
            assertFalse(readBoolean(outside, "waitOffscreenReleased"),
                    "One pixel beyond the $20 Obj_WaitOffscreen placeholder must remain dormant");

            AbstractObjectInstance.updateCameraBounds(0, 0, 0x03E0, 0x0400, 0);
            RhinobotBadnikInstance atPlaceholderEdge = rhinobotWithPlayers(sonic, null);
            updateMovement(atPlaceholderEdge, sonic);
            assertTrue(readBoolean(atPlaceholderEdge, "waitOffscreenReleased"),
                    "The $20 placeholder edge must release Obj_WaitOffscreen's saved continuation");
            assertTrue(stateName(atPlaceholderEdge).equals("PATROL"),
                    "loc_85B02 restores the continuation and returns without running Rhinobot");
            updateMovement(atPlaceholderEdge, sonic);
            assertTrue(stateName(atPlaceholderEdge).equals("PATROL"),
                    "Rhinobot_Init consumes the first restored object dispatch");
            updateMovement(atPlaceholderEdge, sonic);
            assertTrue(stateName(atPlaceholderEdge).equals("CHARGE_PREP"),
                    "Rhinobot patrol must run after the release and Init dispatches");
        } finally {
            AbstractObjectInstance.resetCameraBoundsForTests();
        }
    }

    @Test
    void chargeDetectionUsesFindSonicTailsNearestNativePlayer() throws Exception {
        TestPlayer sonic = player(0x03B6, 0x0200); // 0x4A left: facing-left charge side
        TestPlayer tails = player(0x040A, 0x0200); // 0x0A right: nearest, non-charge side
        RhinobotBadnikInstance rhinobot = rhinobotWithPlayers(sonic, tails);

        assertFalse(shouldStartCharge(rhinobot, sonic),
                "Find_SonicTails must use nearer native P2 before applying Rhinobot's facing-side test");

        RhinobotBadnikInstance soloRhinobot = rhinobotWithPlayers(sonic, null);
        assertTrue(shouldStartCharge(soloRhinobot, sonic),
                "With no native P2, the in-range player on Rhinobot's facing side should trigger charge");
    }

    private static RhinobotBadnikInstance rhinobotWithPlayers(TestPlayer main, TestPlayer nativeP2) {
        RhinobotBadnikInstance rhinobot = new RhinobotBadnikInstance(
                new ObjectSpawn(0x0400, 0x0200, 0x8D, 0, 0, false, 0));
        rhinobot.setServices(new TestObjectServices() {
            @Override
            public ObjectPlayerQuery playerQuery() {
                return new ObjectPlayerQuery(() -> main,
                        () -> nativeP2 == null ? List.of() : List.of(nativeP2));
            }
        });
        return rhinobot;
    }

    private static boolean shouldStartCharge(RhinobotBadnikInstance rhinobot, AbstractPlayableSprite player)
            throws Exception {
        Method method = RhinobotBadnikInstance.class.getDeclaredMethod(
                "shouldStartCharge", AbstractPlayableSprite.class);
        method.setAccessible(true);
        return (boolean) method.invoke(rhinobot, player);
    }

    private static void updateMovement(RhinobotBadnikInstance rhinobot, AbstractPlayableSprite player)
            throws Exception {
        Method method = RhinobotBadnikInstance.class.getDeclaredMethod(
                "updateMovement", int.class, PlayableEntity.class);
        method.setAccessible(true);
        method.invoke(rhinobot, 0, player);
    }

    private static String stateName(RhinobotBadnikInstance rhinobot) throws Exception {
        var field = RhinobotBadnikInstance.class.getDeclaredField("state");
        field.setAccessible(true);
        return ((Enum<?>) field.get(rhinobot)).name();
    }

    private static boolean readBoolean(RhinobotBadnikInstance rhinobot, String fieldName) throws Exception {
        var field = RhinobotBadnikInstance.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(rhinobot);
    }

    private static TestPlayer player(int x, int y) {
        TestPlayer player = new TestPlayer();
        player.setCentreX((short) x);
        player.setCentreY((short) y);
        return player;
    }

    private static final class TestPlayer extends AbstractPlayableSprite implements PlayableEntity {
        private TestPlayer() {
            super("test", (short) 0, (short) 0);
        }

        @Override
        public void draw() {
        }

        @Override
        protected void defineSpeeds() {
        }

        @Override
        protected void createSensorLines() {
        }
    }
}
