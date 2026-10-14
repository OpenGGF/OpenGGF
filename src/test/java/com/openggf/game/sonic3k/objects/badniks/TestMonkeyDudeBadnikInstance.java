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
    void unflippedBodyKeepsItsNativeXAndOrientationWhenPlayerCrossesBothSides() {
        bodyKeepsNativeSpawnOrientationAcrossPlayerSidesAndReplay(0);
    }

    @Test
    void flippedBodyKeepsItsNativeXAndOrientationWhenPlayerCrossesBothSides() {
        bodyKeepsNativeSpawnOrientationAcrossPlayerSidesAndReplay(1);
    }

    private static void bodyKeepsNativeSpawnOrientationAcrossPlayerSidesAndReplay(int flip) {
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
        MonkeyDudeBadnikInstance monkey = new MonkeyDudeBadnikInstance(
                new ObjectSpawn(100, 100, 0x8E, 0x10, flip, false, 0));
        TestPlayer player = player(flip == 0 ? 140 : 60, 100);
        runVisibleInitFrames(monkey, player);
        var before = monkey.captureRewindState();
        for (int cycle = 0; cycle < 2; cycle++) {
            if (cycle != 0) monkey.restoreRewindState(before);
            for (int side : new int[]{140, 60, 140}) {
                player.setCentreX((short) side);
                for (int frame = 0; frame < 24; frame++) {
                    monkey.updateMovement(frame, player);
                    // Body WAIT/ACTIVE have no Find_SonicTails or render_flags
                    // writer; native Touch_Width uses the unchanged x_pos.
                    assertEquals(100, monkey.getX(), "native body SST X");
                    assertEquals(100, monkey.getCollisionX(), "touch origin stays at x_pos");
                    assertEquals(100, monkey.getRenderAnchorX(), "Draw_Sprite origin stays at x_pos");
                    assertEquals(flip == 0, monkey.badnikFacingLeft(),
                            "literal spawn render_flags bit0 is independent of the player's side");
                    assertEquals(flip, monkey.getSpawn().renderFlags());
                }
            }
        }
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
        var h=new TestMonkeyDudeNativeSstGraph.Harness();
        h.body(4,0x1E58,1);
        h.step(0);h.step(0);
        for(int frame=1;frame<=50;frame++)h.step(frame);
        assertEquals(0,projectiles(h).size());
        var rewind=h.manager.rewindSnapshottable();
        var snapshot=rewind.capture();var rng=h.services.rng().capture();
        for(int replay=0;replay<2;replay++) {
            if(replay!=0){rewind.restore(snapshot);h.services.rng().restore(rng);}
            for(int frame=51;frame<=60;frame++)h.step(frame);
            assertEquals(0,projectiles(h).size());
            h.step(61);assertEquals(1,projectiles(h).size());
            var coconut=projectiles(h).getFirst();
            // Native hand allocates from its previous fractional chain position.
            // Its higher-slot projectile has already moved later in this same pass.
            assertEquals(0x1E64,coconut.getX());
            assertEquals(0x46A,coconut.getY());
            var seen=java.util.Collections.newSetFromMap(
                    new java.util.IdentityHashMap<S3kBadnikProjectileInstance,Boolean>());
            seen.add(coconut);
            for(int frame=62;frame<=400;frame++) {
                h.step(frame);seen.addAll(projectiles(h));
            }
            // Count actual admitted projectile identities even after normal expiry.
            assertEquals(1,seen.size(),"hand bit0 never rearms during later body cycles");
        }
    }

    @Test
    void armDoesNotStartAttackAtTheHorizontalRangeBoundary() {
        var h=new TestMonkeyDudeNativeSstGraph.Harness();
        h.body(4,0x1E58,1);
        h.main.setCentreX((short)(0x1E58+0xE+0x80));
        h.step(0);h.step(0);
        for(int frame=1;frame<=400;frame++)h.step(frame);
        assertEquals(5,h.arms().size(),"the native linked chain still exists at the range boundary");
        assertEquals(0,projectiles(h).size(),"only coconut attack admission is withheld at distance$80");
    }

    private static java.util.List<S3kBadnikProjectileInstance> projectiles(TestMonkeyDudeNativeSstGraph.Harness h) {
        return h.manager.getActiveObjects().stream().filter(o->!o.isDestroyed())
                .filter(S3kBadnikProjectileInstance.class::isInstance)
                .map(S3kBadnikProjectileInstance.class::cast).toList();
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
