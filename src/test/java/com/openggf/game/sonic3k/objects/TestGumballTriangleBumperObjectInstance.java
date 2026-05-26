package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.ShieldType;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestGumballTriangleBumperObjectInstance {

    @Test
    void consumedBumper_stopsRenderingAndSolidityAfterBounce() {
        GumballTriangleBumperObjectInstance bumper =
                new GumballTriangleBumperObjectInstance(new ObjectSpawn(0, 0, 0x87, 0, 0, false, 0));
        bumper.setServices(new TestObjectServices());
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setShieldStateForTest(true, ShieldType.BASIC);

        bumper.onSolidContact(player, new SolidContact(true, false, false, true, false), 0);

        assertFalse(bumper.isSolidFor(player));
        ArrayList<com.openggf.graphics.GLCommand> commands = new ArrayList<>();
        bumper.appendRenderCommands(commands);
        assertTrue(commands.isEmpty());
    }

    @Test
    void mirroredBumperFallbackBounceUsesLeftwardVelocity() {
        GumballTriangleBumperObjectInstance bumper =
                new GumballTriangleBumperObjectInstance(new ObjectSpawn(0, 0, 0x87, 0, 1, false, 0));
        bumper.setServices(new TestObjectServices());
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 8);
        player.setCentreY((short) 8);
        player.setAir(true);
        player.setYSpeed((short) 0x100);

        bumper.update(0, player);

        assertEquals(-0x300, player.getXSpeed());
        assertEquals(-0x600, player.getYSpeed());
    }

    @Test
    void queryOnlySidekickParticipatesInFallbackBounce() {
        GumballTriangleBumperObjectInstance bumper =
                new GumballTriangleBumperObjectInstance(new ObjectSpawn(0, 0, 0x87, 0, 0, false, 0));
        TestablePlayableSprite main = new TestablePlayableSprite("sonic", (short) 0x100, (short) 0x100);
        TestablePlayableSprite sidekick = new TestablePlayableSprite("tails", (short) 0, (short) 0);
        sidekick.setCentreX((short) 8);
        sidekick.setCentreY((short) 8);
        sidekick.setAir(true);
        sidekick.setYSpeed((short) 0x100);
        bumper.setServices(new QueryOnlyPlayerServices(main, List.of(sidekick)));

        bumper.update(0, main);

        assertEquals(0x300, sidekick.getXSpeed());
        assertEquals(-0x600, sidekick.getYSpeed());
        assertFalse(bumper.isSolidFor(sidekick));
    }

    @Test
    void playerQueryFailureIsNotSwallowed() {
        GumballTriangleBumperObjectInstance bumper =
                new GumballTriangleBumperObjectInstance(new ObjectSpawn(0, 0, 0x87, 0, 0, false, 0));
        bumper.setServices(new ThrowingPlayerQueryServices());

        assertThrows(IllegalStateException.class,
                () -> bumper.update(0, new TestablePlayableSprite("sonic", (short) 0, (short) 0)));
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

    private static final class ThrowingPlayerQueryServices extends TestObjectServices {
        @Override
        public ObjectPlayerQuery playerQuery() {
            throw new IllegalStateException("query unavailable");
        }
    }
}

