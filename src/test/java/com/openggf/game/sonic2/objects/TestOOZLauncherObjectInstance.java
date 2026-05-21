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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestOOZLauncherObjectInstance {

    @Test
    void nativeP2QuerySidekickCanBreakLauncherWhenRawSidekickListIsEmpty() {
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
        assertFalse(extraSidekick.getAir(),
                "Additional engine sidekicks must not inherit the single native P2 launcher state");
    }

    private static OOZLauncherObjectInstance newLauncher() {
        return new OOZLauncherObjectInstance(
                new ObjectSpawn(0x0100, 0x0100, Sonic2ObjectIds.OOZ_LAUNCHER, 0, 0, false, 0),
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
