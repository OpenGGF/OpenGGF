package com.openggf.game.sonic1.objects;

import org.junit.jupiter.api.Test;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import java.util.List;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.CompactFieldCapturer;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import java.lang.reflect.Field;
import java.util.Map;

public class TestSonic1RunningDiscObjectInstance {

    @Test
    public void attachesAndSetsStickToConvex() {
        Sonic1RunningDiscObjectInstance disc = createDisc(0x10, 0x200, 0x200);
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x200);
        player.setCentreY((short) 0x200);
        player.setAir(false);
        player.setGSpeed((short) 0);

        disc.update(1, player);

        // ROM: move.b #1,stick_to_convex(a1)
        assertTrue(player.isStickToConvex());
        // ROM: move.w #$400,obInertia(a1) (positive angularSpeed -> clamp rightward)
        assertEquals(0x400, player.getGSpeed());
    }

    @Test
    public void leavingDiscClearsStickToConvex() {
        Sonic1RunningDiscObjectInstance disc = createDisc(0x10, 0x200, 0x200);
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x200);
        player.setCentreY((short) 0x200);
        player.setAir(false);

        disc.update(1, player);
        assertTrue(player.isStickToConvex());

        // Outside detection square -> detach
        // ROM: clr.b stick_to_convex(a1) / clr.b disc_sonic_attached(a0)
        player.setCentreX((short) 0x300);
        player.setCentreY((short) 0x300);
        disc.update(2, player);

        assertFalse(player.isStickToConvex());
    }

    @Test
    public void airborneInsideRangeClearsAttachmentButKeepsConvex() {
        Sonic1RunningDiscObjectInstance disc = createDisc(0x10, 0x200, 0x200);
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x200);
        player.setCentreY((short) 0x200);
        player.setAir(false);

        disc.update(1, player);
        assertTrue(player.isStickToConvex());

        // In-range but airborne: ROM clears disc_sonic_attached but does NOT
        // clear stick_to_convex (only the .detach path does that).
        player.setAir(true);
        disc.update(2, player);
        assertTrue(player.isStickToConvex());

        // Out of range, but disc_sonic_attached was already cleared by the
        // airborne path, so the .detach branch skips stick_to_convex clearing
        // (ROM: tst.b disc_sonic_attached / beq.s .return). ROM-accurate.
        player.setCentreX((short) 0x300);
        player.setCentreY((short) 0x300);
        disc.update(3, player);
        assertTrue(player.isStickToConvex());
    }

    @Test
    public void mainAndThreeSidekicksAttachAndDetachIndependently() {
        Sonic1RunningDiscObjectInstance disc = createDisc(0x10, 0x200, 0x200);
        TestPlayableSprite main = groundedAt(0x200, 0x200);
        TestPlayableSprite p2 = groundedAt(0x200, 0x200);
        TestPlayableSprite p3 = groundedAt(0x200, 0x200);
        TestPlayableSprite p4 = groundedAt(0x200, 0x200);
        disc.setServices(new TestObjectServices().withSidekicks(List.of(p2, p3, p4)));

        disc.update(1, main);

        for (TestPlayableSprite player : List.of(main, p2, p3, p4)) {
            assertTrue(player.isStickToConvex());
            assertEquals(0x400, player.getGSpeed());
        }

        p3.setCentreX((short) 0x300);
        p3.setCentreY((short) 0x300);
        disc.update(2, main);

        assertFalse(p3.isStickToConvex());
        assertTrue(main.isStickToConvex());
        assertTrue(p2.isStickToConvex());
        assertTrue(p4.isStickToConvex());
    }

    @Test
    public void attachedExtensionRelinksThroughNonEmptyPlayerRefState() throws Exception {
        Sonic1RunningDiscObjectInstance disc = createDisc(0x10, 0x200, 0x200);
        TestPlayableSprite oldMain = groundedAt(0x200, 0x200);
        TestPlayableSprite oldP2 = groundedAt(0x200, 0x200);
        disc.setServices(new TestObjectServices().withSidekicks(List.of(oldP2)));
        disc.update(1, oldMain);

        RewindIdentityTable capturedIds = new RewindIdentityTable();
        capturedIds.registerPlayer(oldMain, PlayerRefId.mainPlayer());
        capturedIds.registerPlayer(oldP2, PlayerRefId.sidekick(0));
        var blob = CompactFieldCapturer.capture(
                disc, RewindCaptureContext.withIdentityTable(capturedIds));

        TestPlayableSprite newMain = groundedAt(0x200, 0x200);
        TestPlayableSprite newP2 = groundedAt(0x200, 0x200);
        RewindIdentityTable restoredIds = new RewindIdentityTable();
        restoredIds.registerPlayer(newMain, PlayerRefId.mainPlayer());
        restoredIds.registerPlayer(newP2, PlayerRefId.sidekick(0));
        disc.setServices(new TestObjectServices().withSidekicks(List.of(newP2)));
        CompactFieldCapturer.restore(
                disc, blob, RewindCaptureContext.withIdentityTable(restoredIds));
        Field nativeOwner = Sonic1RunningDiscObjectInstance.class.getDeclaredField("nativeOwner");
        nativeOwner.setAccessible(true);
        assertSame(newMain, nativeOwner.get(disc));
        Field extensions = Sonic1RunningDiscObjectInstance.class.getDeclaredField("extensionStates");
        extensions.setAccessible(true);
        assertTrue(((Map<?, ?>) extensions.get(disc)).containsKey(newP2));
    }

    private static TestPlayableSprite groundedAt(int x, int y) {
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) x);
        player.setCentreY((short) y);
        player.setAir(false);
        return player;
    }

    private static Sonic1RunningDiscObjectInstance createDisc(int subtype, int x, int y) {
        Sonic1RunningDiscObjectInstance disc = new Sonic1RunningDiscObjectInstance(
                new ObjectSpawn(x, y, 0x67, subtype, 0, false, 0));
        disc.setServices(new TestObjectServices());
        return disc;
    }

}
