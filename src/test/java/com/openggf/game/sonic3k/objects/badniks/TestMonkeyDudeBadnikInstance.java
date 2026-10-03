package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestMonkeyDudeBadnikInstance {

    @AfterEach
    void resetCameraBounds() {
        AbstractObjectInstance.resetCameraBoundsForTests();
    }

    @Test
    void collisionAnchorFollowsFacingOffsetWhenMonkeyTurnsRightAndBack() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
        MonkeyDudeBadnikInstance monkey = new MonkeyDudeBadnikInstance(
                new ObjectSpawn(100, 100, 0x8E, 0x04, 0, false, 0));
        TestPlayer player = player(140, 100);

        runVisibleInitFrames(monkey, player);
        assertEquals(100, monkey.getCollisionX(), "Initial left-facing collision anchor");

        monkey.updateMovement(0, player);
        assertEquals(132, monkey.getCollisionX(),
                "Collision anchor must follow the rendered body when MonkeyDude turns right");

        player.setCentreX((short) 60);
        monkey.updateMovement(0, player);
        assertEquals(100, monkey.getCollisionX(),
                "Collision anchor must return with the rendered body when MonkeyDude turns left");
    }

    @Test
    void collisionAnchorFollowsFacingOffsetWhenMonkeyTurnsLeftAndBack() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
        MonkeyDudeBadnikInstance monkey = new MonkeyDudeBadnikInstance(
                new ObjectSpawn(100, 100, 0x8E, 0x04, 1, false, 0));
        TestPlayer player = player(60, 100);

        runVisibleInitFrames(monkey, player);
        assertEquals(100, monkey.getCollisionX(), "Initial right-facing collision anchor");

        monkey.updateMovement(0, player);
        assertEquals(68, monkey.getCollisionX(),
                "Collision anchor must follow the rendered body when MonkeyDude turns left");

        player.setCentreX((short) 140);
        monkey.updateMovement(0, player);
        assertEquals(100, monkey.getCollisionX(),
                "Collision anchor must return with the rendered body when MonkeyDude turns right");
    }

    @Test
    void releasedBodyKeepsAnimationCadenceOffscreenAndAcrossRewind() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
        TestObjectServices services = new TestObjectServices();
        MonkeyDudeBadnikInstance monkey = new MonkeyDudeBadnikInstance(
                new ObjectSpawn(100, 100, 0x8E, 0x10, 0, false, 0));
        monkey.setServices(services);
        TestPlayer player = player(1000, 1000);
        runVisibleInitFrames(monkey, player);
        // Move beyond the $20 release window while retaining the object in
        // the ordinary unload range. Obj_WaitOffscreen no longer owns it.
        AbstractObjectInstance.updateCameraBounds(160, 0, 480, 224, 0);

        // Obj_Wait starts at 59 and changes to ACTIVE on the 60th dispatch.
        for (int frame = 0; frame < 60; frame++) {
            monkey.updateMovement(frame, player);
        }
        assertEquals(100, monkey.getY());
        // loc_871C2 clears the animation timer. The next dispatch publishes
        // frame 2; eight dispatches later frame 0 takes the first downward step.
        for (int frame = 0; frame < 8; frame++) {
            monkey.updateMovement(frame, player);
            assertEquals(100, monkey.getY());
        }
        monkey.updateMovement(8, player);
        assertEquals(108, monkey.getY());

        var beforeWait = monkey.captureRewindState();
        var rng = services.rng().capture();
        for (int repeat = 0; repeat < 2; repeat++) {
            if (repeat != 0) {
                monkey.restoreRewindState(beforeWait);
                services.rng().restore(rng);
            }
            for (int frame = 0; frame < 60; frame++) {
                monkey.updateMovement(frame, player);
                assertEquals(108, monkey.getY());
            }
            // loc_87218 clears the timer again at the direction change;
            // loc_87204 moves up on the first active frame-2 publication.
            monkey.updateMovement(60, player);
            assertEquals(100, monkey.getY());
            for (int frame = 0; frame < 15; frame++) {
                monkey.updateMovement(frame, player);
                assertEquals(100, monkey.getY());
            }
            monkey.updateMovement(15, player);
            assertEquals(92, monkey.getY());
        }
    }

    @Test
    void handLaunchesOnceFromItsPreviousFractionalChainPositionAcrossRewind() {
        AbstractObjectInstance.updateCameraBounds(0x1E00, 0x400, 0x1F40, 0x500, 0);
        var manager = org.mockito.Mockito.mock(com.openggf.level.objects.ObjectManager.class);
        var projectiles = new java.util.ArrayList<S3kBadnikProjectileInstance>();
        var services = new TestObjectServices().withDirectObjectManager(manager);
        org.mockito.Mockito.doAnswer(call -> {
            projectiles.add(call.getArgument(0));
            return null;
        }).when(manager).addDynamicObjectAfterCurrent(org.mockito.ArgumentMatchers.any());
        var monkey = new MonkeyDudeBadnikInstance(
                new ObjectSpawn(0x1E58, 0x490, 0x8E, 0x10, 1, false, 0));
        monkey.setServices(services);
        // sub_87524 tests horizontal distance only; the vertical separation
        // deliberately exceeds the old approximation's $80 range.
        TestPlayer player = player(0x1EA6, 0x700);
        runVisibleInitFrames(monkey, player);
        for (int frame = 1; frame <= 50; frame++) monkey.updateMovement(frame, player);
        assertEquals(0, projectiles.size());
        var snapshot = monkey.captureRewindState();
        var rng = services.rng().capture();
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) {
                monkey.restoreRewindState(snapshot);
                services.rng().restore(rng);
                projectiles.clear();
            }
            for (int frame = 51; frame <= 60; frame++) monkey.updateMovement(frame, player);
            assertEquals(0, projectiles.size());
            monkey.updateMovement(61, player);
            assertEquals(1, projectiles.size());
            var coconut = projectiles.getFirst();
            // Native child dispatch: the hand tests the second follower before
            // its own circular move. GetSineCosine / ASR.L #5 retain subpixels
            // across all four followers; CreateChild2 copies the resulting words.
            assertEquals(0x1E62, coconut.getX());
            assertEquals(0x46E, coconut.getY());
            coconut.update(61, player);
            assertEquals(0x1E64, coconut.getX());
            assertEquals(0x46A, coconut.getY());
            for (int frame = 62; frame <= 400; frame++) monkey.updateMovement(frame, player);
            assertEquals(1, projectiles.size(), "hand bit 0 never rearms during later body cycles");
        }
    }

    @Test
    void armDoesNotStartAttackAtTheHorizontalRangeBoundary() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
        var manager = org.mockito.Mockito.mock(com.openggf.level.objects.ObjectManager.class);
        var services = new TestObjectServices().withDirectObjectManager(manager);
        var monkey = new MonkeyDudeBadnikInstance(new ObjectSpawn(100, 100, 0x8E, 0x10, 1, false, 0));
        monkey.setServices(services);
        TestPlayer player = player(100 + 14 + 0x80, 100);
        runVisibleInitFrames(monkey, player);
        for (int frame = 1; frame <= 400; frame++) monkey.updateMovement(frame, player);
        org.mockito.Mockito.verify(manager, org.mockito.Mockito.never())
                .addDynamicObjectAfterCurrent(org.mockito.ArgumentMatchers.any());
    }

    private static void runVisibleInitFrames(MonkeyDudeBadnikInstance monkey, TestPlayer player) {
        monkey.updateMovement(0, player);
        monkey.updateMovement(0, player);
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
