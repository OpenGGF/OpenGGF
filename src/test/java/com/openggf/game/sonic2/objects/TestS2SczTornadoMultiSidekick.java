package com.openggf.game.sonic2.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.save.SaveReason;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.level.ParallaxManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

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
        var beforeUnload = tornado.captureRewindState(rewindContext(
                capturedMain, capturedFirst, capturedSecond, capturedThird));
        tornado.onUnload();
        assertReleased(capturedMain, capturedFirst, capturedSecond, capturedThird);

        TestablePlayableSprite restoredMain = player("restored-sonic", 0x1500);
        TestablePlayableSprite restoredFirst = player("restored-tails", 0x1510);
        TestablePlayableSprite restoredSecond = player("restored-knuckles", 0x1520);
        TestablePlayableSprite restoredThird = player("restored-sonic-2", 0x1530);
        services.replaceRoster(restoredMain, List.of(restoredFirst, restoredSecond, restoredThird));
        forceJump(restoredMain, restoredFirst, restoredSecond, restoredThird);

        tornado.restoreRewindState(beforeUnload, rewindContext(
                restoredMain, restoredFirst, restoredSecond, restoredThird));
        Map<?, ?> restoredOwners = controlledPlayers(tornado);
        for (TestablePlayableSprite restored : List.of(
                restoredMain, restoredFirst, restoredSecond, restoredThird)) {
            assertTrue(restoredOwners.containsKey(restored),
                    restored.getCode() + " must restore through its PlayerRefId");
        }
        for (TestablePlayableSprite captured : List.of(
                capturedMain, capturedFirst, capturedSecond, capturedThird)) {
            assertFalse(restoredOwners.containsKey(captured),
                    captured.getCode() + " must not survive as a stale captured instance");
        }
        tornado.onUnload();

        assertReleased(restoredMain, restoredFirst, restoredSecond, restoredThird);
        assertReleased(capturedMain, capturedFirst, capturedSecond, capturedThird);
    }

    @Test
    void invalidSidekicksAreReleasedInsteadOfRelockedDuringFinishUpdate() {
        TestablePlayableSprite main = player("sonic", 0x1500);
        TestablePlayableSprite dead = player("dead", 0x1510);
        TestablePlayableSprite hurt = player("hurt", 0x1520);
        TestablePlayableSprite debug = player("debug", 0x1530);
        TornadoServices services = new TornadoServices(main, List.of(dead, hurt, debug));
        TornadoObjectInstance tornado = tornado(services);

        tornado.update(1, main);
        assertForcedRight(main, dead, hurt, debug);

        dead.setDead(true);
        hurt.setHurt(true);
        debug.setDebugMode(true);
        tornado.update(2, main);

        assertForcedRight(main);
        assertReleased(dead, hurt, debug);
    }

    @Test
    void replacementBeforeCleanupReleasesOnlyTheOriginalOwnedIdentities() {
        TestablePlayableSprite originalMain = player("sonic", 0x1500);
        TestablePlayableSprite originalFirst = player("tails", 0x1510);
        TestablePlayableSprite originalSecond = player("knuckles", 0x1520);
        TestablePlayableSprite originalThird = player("sonic-2", 0x1530);
        TornadoServices services = new TornadoServices(
                originalMain, List.of(originalFirst, originalSecond, originalThird));
        TornadoObjectInstance tornado = tornado(services);
        tornado.update(1, originalMain);
        assertForcedRight(originalMain, originalFirst, originalSecond, originalThird);

        TestablePlayableSprite replacementMain = player("replacement-main", 0x1500);
        TestablePlayableSprite replacementFirst = player("replacement-first", 0x1510);
        TestablePlayableSprite replacementSecond = player("replacement-second", 0x1520);
        TestablePlayableSprite replacementThird = player("replacement-third", 0x1530);
        services.replaceRoster(replacementMain,
                List.of(replacementFirst, replacementSecond, replacementThird));
        forceJump(replacementMain, replacementFirst, replacementSecond, replacementThird);

        tornado.onUnload();

        assertReleased(originalMain, originalFirst, originalSecond, originalThird);
        assertForcedJump(replacementMain, replacementFirst, replacementSecond, replacementThird);
    }

    @Test
    void cameraBacktrackReleasesOriginalOwnersWithoutClearingReplacementControl() {
        TestablePlayableSprite originalMain = player("sonic", 0x1500);
        TestablePlayableSprite originalSidekick = player("tails", 0x1510);
        TornadoServices services = new TornadoServices(originalMain, List.of(originalSidekick));
        TornadoObjectInstance tornado = tornado(services);
        tornado.update(1, originalMain);
        assertForcedRight(originalMain, originalSidekick);

        TestablePlayableSprite replacementMain = player("replacement-main", 0x1500);
        TestablePlayableSprite replacementSidekick = player("replacement-sidekick", 0x1510);
        services.replaceRoster(replacementMain, List.of(replacementSidekick));
        forceJump(replacementMain, replacementSidekick);
        services.camera.setX((short) 0x13FF);

        tornado.update(2, replacementMain);

        assertReleased(originalMain, originalSidekick);
        assertForcedJump(replacementMain, replacementSidekick);
    }

    @Test
    void invalidNeverOwnedSidekickRetainsUnrelatedForcedControl() {
        TestablePlayableSprite main = player("sonic", 0x1500);
        TestablePlayableSprite invalidSidekick = player("invalid", 0x1510);
        invalidSidekick.setDead(true);
        forceJump(invalidSidekick);
        TornadoServices services = new TornadoServices(main, List.of(invalidSidekick));
        TornadoObjectInstance tornado = tornado(services);

        tornado.update(1, main);

        assertForcedRight(main);
        assertForcedJump(invalidSidekick);
    }

    private static TornadoObjectInstance tornado(TornadoServices services) {
        TornadoObjectInstance tornado = new TornadoObjectInstance(
                new ObjectSpawn(0x1500, 0x300, 0xB2, 0x50, 0, false, 0));
        tornado.setServices(services);
        tornado.consumePendingInitRoutine();
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

    private static void forceJump(TestablePlayableSprite... players) {
        for (TestablePlayableSprite player : players) {
            player.setControlLocked(true);
            player.setForcedInputMask(TestablePlayableSprite.INPUT_JUMP);
        }
    }

    private static void assertForcedJump(TestablePlayableSprite... players) {
        for (TestablePlayableSprite player : players) {
            assertTrue(player.isControlLocked());
            assertEquals(TestablePlayableSprite.INPUT_JUMP, player.getForcedInputMask());
        }
    }

    private static RewindCaptureContext rewindContext(
            TestablePlayableSprite main,
            TestablePlayableSprite... sidekicks) {
        RewindIdentityTable identities = new RewindIdentityTable();
        identities.registerPlayer(main, PlayerRefId.mainPlayer());
        for (int index = 0; index < sidekicks.length; index++) {
            identities.registerPlayer(sidekicks[index], PlayerRefId.sidekick(index));
        }
        return RewindCaptureContext.withIdentityTable(identities);
    }

    private static Map<?, ?> controlledPlayers(TornadoObjectInstance tornado) {
        try {
            Field field = TornadoObjectInstance.class.getDeclaredField("controlledPlayers");
            field.setAccessible(true);
            return (Map<?, ?>) field.get(tornado);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
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
