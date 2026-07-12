package com.openggf.game.sonic2.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.save.SaveReason;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.level.ParallaxManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS2SczTornadoMultiSidekick {

    @Test
    void finishChoreographyIsMainDrivenAndControlsEveryLiveTeamMember() {
        TestablePlayableSprite main = player("sonic", 0x1500);
        TestablePlayableSprite first = player("tails", 0x1600);
        TestablePlayableSprite second = player("knuckles", 0x15C0);
        TestablePlayableSprite third = player("sonic-2", 0x1580);
        TornadoServices services = new TornadoServices(main, List.of(first, second, third));
        TornadoObjectInstance tornado = tornado(services);

        tornado.update(1, first);

        assertEquals(0, services.transitionRequests,
                "a sidekick beyond the finish must not advance SCZ while the main player is behind");
        assertForcedRight(main, first, second, third);

        services.camera.setX((short) 0x13FF);
        tornado.update(2, second);
        assertReleased(main, first, second, third);

        services.camera.setX((short) 0x1400);
        tornado.update(3, first);
        assertForcedRight(main, first, second, third);

        main.setCentreX((short) 0x1568);
        tornado.update(4, third);
        tornado.update(5, second);

        assertEquals(1, services.transitionRequests,
                "the main player crossing the finish should request exactly one transition");
        assertEquals(Sonic2ZoneConstants.ZONE_WFZ, services.requestedZone);
        assertEquals(0, services.requestedAct);
        assertTrue(services.deactivateLevelNow);
        assertEquals(1, services.saveRequests);

        third.setDead(true);
        tornado.onUnload();
        assertReleased(main, first, second, third);
    }

    @Test
    void rewindRestoredControlOwnershipCleansUpTheReplacementRoster() {
        TestablePlayableSprite capturedMain = player("sonic", 0x1500);
        TestablePlayableSprite capturedFirst = player("tails", 0x1510);
        TestablePlayableSprite capturedSecond = player("knuckles", 0x1520);
        TestablePlayableSprite capturedThird = player("sonic-2", 0x1530);
        TornadoServices services = new TornadoServices(
                capturedMain, List.of(capturedFirst, capturedSecond, capturedThird));
        TornadoObjectInstance tornado = tornado(services);

        tornado.update(1, capturedMain);
        assertForcedRight(capturedMain, capturedFirst, capturedSecond, capturedThird);
        var beforeUnload = tornado.captureRewindState();
        tornado.onUnload();
        assertReleased(capturedMain, capturedFirst, capturedSecond, capturedThird);

        TestablePlayableSprite restoredMain = player("restored-sonic", 0x1500);
        TestablePlayableSprite restoredFirst = player("restored-tails", 0x1510);
        TestablePlayableSprite restoredSecond = player("restored-knuckles", 0x1520);
        TestablePlayableSprite restoredThird = player("restored-sonic-2", 0x1530);
        services.replaceRoster(restoredMain, List.of(restoredFirst, restoredSecond, restoredThird));
        forceRight(restoredMain, restoredFirst, restoredSecond, restoredThird);

        tornado.restoreRewindState(beforeUnload);
        tornado.onUnload();

        assertReleased(restoredMain, restoredFirst, restoredSecond, restoredThird);
        assertReleased(capturedMain, capturedFirst, capturedSecond, capturedThird);
    }

    private static TornadoObjectInstance tornado(TornadoServices services) {
        TornadoObjectInstance tornado = new TornadoObjectInstance(
                new ObjectSpawn(0x1500, 0x300, 0xB2, 0x50, 0, false, 0));
        tornado.setServices(services);
        return tornado;
    }

    private static TestablePlayableSprite player(String code, int x) {
        return new TestablePlayableSprite(code, (short) x, (short) 0x300);
    }

    private static void assertForcedRight(TestablePlayableSprite... players) {
        for (TestablePlayableSprite player : players) {
            assertTrue(player.isControlLocked(),
                    player.getCode() + " should be controlled by SCZ finish choreography");
            assertEquals(TestablePlayableSprite.INPUT_RIGHT, player.getForcedInputMask(),
                    player.getCode() + " should receive the native forced-right input");
        }
    }

    private static void assertReleased(TestablePlayableSprite... players) {
        for (TestablePlayableSprite player : players) {
            assertFalse(player.isControlLocked(), player.getCode() + " should be released when Tornado unloads");
            assertEquals(0, player.getForcedInputMask(), player.getCode() + " should not retain forced input");
        }
    }

    private static void forceRight(TestablePlayableSprite... players) {
        for (TestablePlayableSprite player : players) {
            player.setControlLocked(true);
            player.setForcedInputMask(TestablePlayableSprite.INPUT_RIGHT);
        }
    }

    private static final class TornadoServices extends StubObjectServices {
        private final Camera camera = new Camera();
        private final ParallaxManager parallax = new ParallaxManager();
        private final ObjectPlayerQuery playerQuery;
        private PlayableEntity main;
        private List<? extends PlayableEntity> sidekicks;
        private int transitionRequests;
        private int requestedZone = -1;
        private int requestedAct = -1;
        private boolean deactivateLevelNow;
        private int saveRequests;

        private TornadoServices(PlayableEntity main, List<? extends PlayableEntity> sidekicks) {
            camera.setX((short) 0x1400);
            this.main = main;
            this.sidekicks = sidekicks;
            playerQuery = new ObjectPlayerQuery(() -> this.main, () -> this.sidekicks);
        }

        private void replaceRoster(PlayableEntity main, List<? extends PlayableEntity> sidekicks) {
            this.main = main;
            this.sidekicks = sidekicks;
        }

        @Override
        public Camera camera() {
            return camera;
        }

        @Override
        public ParallaxManager parallaxManager() {
            return parallax;
        }

        @Override
        public ObjectPlayerQuery playerQuery() {
            return playerQuery;
        }

        @Override
        public void requestSessionSave(SaveReason reason) {
            saveRequests++;
        }

        @Override
        public void requestZoneAndAct(int zone, int act, boolean deactivateLevelNow) {
            transitionRequests++;
            requestedZone = zone;
            requestedAct = act;
            this.deactivateLevelNow = deactivateLevelNow;
        }
    }
}
