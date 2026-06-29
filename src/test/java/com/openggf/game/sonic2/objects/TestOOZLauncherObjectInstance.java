package com.openggf.game.sonic2.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestOOZLauncherObjectInstance {

    @Test
    void queriedEngineSidekicksEachUseIndependentLauncherParticipation() {
        OOZLauncherObjectInstance launcher = newLauncher();
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite nativeP2 = player("tails");
        TestablePlayableSprite extraSidekick = player("knuckles");
        main.setAnimationId(Sonic2AnimationIds.WAIT);
        nativeP2.setAnimationId(Sonic2AnimationIds.ROLL);
        nativeP2.setYSpeed((short) -0x0120);
        extraSidekick.setAnimationId(Sonic2AnimationIds.ROLL);
        launcher.setServices(new QueryOnlyPlayerServices(main, List.of(nativeP2, extraSidekick)));

        launcher.update(0, main);
        launcher.onSolidContact(nativeP2, new SolidContact(true, false, false, true, false), 0);

        assertTrue(nativeP2.getAir(),
                "Obj3D has only native P1/P2 state slots, so queried native P2 must use the Tails slot");
        assertTrue(nativeP2.getRolling());
        assertFalse(launcher.isSolidFor(main), "Rolling native P2 should break the launcher block");

        launcher.update(1, main);

        assertTrue(extraSidekick.isObjectControlled(),
                "OOZ launcher has per-player launch state and should explicitly include engine sidekicks");
        assertTrue(extraSidekick.getAir());
    }

    @Test
    void solidLandingKeepsRollingRadiusForBreakFrame() {
        OOZLauncherObjectInstance launcher = newLauncher();
        TestablePlayableSprite player = player("sonic");
        player.setRolling(true);

        assertTrue(launcher.landingPreservesRolling(player),
                "Obj3D loc_24EB8 sets roll radii after SolidObject_Landed and never runs Sonic_ResetOnFloor");
        assertEquals(player.getStandYRadius() - player.getYRadius(),
                launcher.getTopLandingSnapAdjustment(player, player.getStandYRadius()),
                "Obj3D's break-frame landing uses the live roll radius, not the standing-radius overlap surface");
    }

    @Test
    void horizontalLauncherCapturesPlayerInWindowOnFirstScan() {
        OOZLauncherObjectInstance launcher = newLauncherAt(0x1140, 0x0270, 1);
        TestablePlayableSprite player = player("sonic");
        player.setAnimationId(Sonic2AnimationIds.ROLL);
        player.setRolling(true);
        player.setYSpeed((short) 0x0418);
        launcher.setServices(new QueryOnlyPlayerServices(player, List.of()));

        launcher.update(0, player);
        launcher.onSolidContact(player, new SolidContact(true, false, false, true, false), 0);
        player.setCentreX((short) 0x114C);
        player.setCentreY((short) 0x0263);
        launcher.update(1, player);

        assertTrue(player.isObjectControlled());
        assertTrue(player.isOnObject());
        assertEquals(0x1140, player.getCentreX() & 0xFFFF);
        assertEquals((short) -0x0800, player.getYSpeed());
    }

    @Test
    void horizontalLauncherDeletesIfNeitherPlayerEntersWindow() {
        OOZLauncherObjectInstance launcher = newLauncherAt(0x1140, 0x0270, 1);
        TestablePlayableSprite player = player("sonic");
        player.setAnimationId(Sonic2AnimationIds.ROLL);
        player.setRolling(true);
        player.setYSpeed((short) 0x0418);
        launcher.setServices(new QueryOnlyPlayerServices(player, List.of()));

        launcher.update(0, player);
        launcher.onSolidContact(player, new SolidContact(true, false, false, true, false), 0);
        launcher.update(1, player);

        assertFalse(launcher.isPersistent());
        assertFalse(player.isObjectControlled());
    }

    private static OOZLauncherObjectInstance newLauncher() {
        return newLauncherAt(0x0100, 0x0100, 0);
    }

    private static OOZLauncherObjectInstance newLauncherAt(int x, int y, int subtype) {
        return new OOZLauncherObjectInstance(
                new ObjectSpawn(x, y, Sonic2ObjectIds.OOZ_LAUNCHER, subtype, 0, false, 0),
                "OOZLauncher");
    }

    private static TestablePlayableSprite player(String code) {
        TestablePlayableSprite player = new TestablePlayableSprite(code, (short) 0x0100, (short) 0x0100);
        player.setCentreX((short) 0x0100);
        player.setCentreY((short) 0x0100);
        player.setAir(false);
        return player;
    }

    private static final class QueryOnlyPlayerServices extends TestObjectServices {
        private final PlayableEntity main;
        private final List<? extends PlayableEntity> queriedSidekicks;

        private QueryOnlyPlayerServices(PlayableEntity main, List<? extends PlayableEntity> queriedSidekicks) {
            this.main = main;
            this.queriedSidekicks = List.copyOf(queriedSidekicks);
        }

        @Override
        public ObjectPlayerQuery playerQuery() {
            return new ObjectPlayerQuery(() -> main, () -> queriedSidekicks);
        }

        @Override
        public List<PlayableEntity> sidekicks() {
            return List.of();
        }
    }
}
