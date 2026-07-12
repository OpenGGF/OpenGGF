package com.openggf.game.sonic1.objects;

import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.sonic1.Sonic1SwitchManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic1JunctionObjectInstance {

    @Test
    void constructorMatchesJunMainSeedState() throws Exception {
        SessionManager.clear();
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());

        Sonic1JunctionObjectInstance junction = new Sonic1JunctionObjectInstance(
                new ObjectSpawn(0x1490, 0x0170, 0x66, 0x00, 0, false, 0));
        try {
            assertEquals(1, getPrivateInt(junction, "frameDirection"));
            assertEquals(0, getPrivateInt(junction, "mappingFrame"));
            assertEquals(0, getPrivateInt(junction, "frameTimer"));
        } finally {
            SessionManager.clear();
        }
    }

    @Test
    void solidProfileKeepsRightEdgeInclusiveForS1SolidObject() {
        Sonic1JunctionObjectInstance junction = new Sonic1JunctionObjectInstance(
                new ObjectSpawn(0x1490, 0x0170, 0x66, 0x00, 0, false, 0));

        SolidRoutineProfile profile = junction.getSolidRoutineProfile();

        assertTrue(profile.inclusiveRightEdge(),
                "S1 SolidObject uses bhi on the right edge, so equality remains a side contact");
    }

    @Test
    void releaseFollowsCapturedSidekickInsteadOfUpdateArgumentMainPlayer() throws Exception {
        TestPlayableSprite main = new TestPlayableSprite();
        TestPlayableSprite extraSidekick = new TestPlayableSprite();
        TestableJunction junction = new TestableJunction(
                new ObjectSpawn(0x1490, 0x0170, 0x66, 0x00, 0, false, 0));
        junction.setServices(services(main, List.of(extraSidekick)));
        prepareLeftGapCapture(junction, extraSidekick);
        junction.update(1, main);
        setPrivateInt(junction, "mappingFrame", 4);

        junction.update(2, main);

        assertFalse(extraSidekick.isObjectControlled(), "junction must release its captured sidekick owner");
        assertFalse(extraSidekick.isControlLocked());
        assertEquals((short) 0x0800, extraSidekick.getYSpeed());
        assertEquals((short) 0, main.getYSpeed(), "main player must not inherit another player's release");
    }

    @Test
    void deadCapturedSidekickIsReleasedForRespawn() throws Exception {
        TestPlayableSprite main = new TestPlayableSprite();
        TestPlayableSprite extraSidekick = new TestPlayableSprite();
        TestableJunction junction = new TestableJunction(
                new ObjectSpawn(0x1490, 0x0170, 0x66, 0x00, 0, false, 0));
        junction.setServices(services(main, List.of(extraSidekick)));
        prepareLeftGapCapture(junction, extraSidekick);
        junction.update(1, main);
        extraSidekick.setDead(true);

        junction.update(2, main);

        assertFalse(extraSidekick.isObjectControlled());
        assertFalse(extraSidekick.isControlLocked());
        assertEquals(-1, extraSidekick.getForcedAnimationId());
    }

    @Test
    void publicUpdateKeepsMainFirstWhenMainAndTwoSidekicksAreEligible() throws Exception {
        TestPlayableSprite main = playerLeftOfJunction();
        TestPlayableSprite firstSidekick = playerLeftOfJunction();
        TestPlayableSprite secondSidekick = playerLeftOfJunction();
        TestableJunction junction = new TestableJunction(
                new ObjectSpawn(0x1490, 0x0170, 0x66, 0x00, 0, false, 0));
        junction.setServices(services(main, List.of(firstSidekick, secondSidekick)));
        prepareLeftGapCapture(junction, main, firstSidekick, secondSidekick);

        junction.update(1, main);

        assertSame(main, getPrivateObject(junction, "controlledPlayer"));
        assertTrue(main.isObjectControlled());
        assertFalse(firstSidekick.isObjectControlled());
        assertFalse(secondSidekick.isObjectControlled());
    }

    @Test
    void publicUpdateCanSelectLaterEligibleSidekickWithoutMultipleOwners() throws Exception {
        TestPlayableSprite main = playerLeftOfJunction();
        TestPlayableSprite firstSidekick = playerLeftOfJunction();
        TestPlayableSprite secondSidekick = playerLeftOfJunction();
        TestableJunction junction = new TestableJunction(
                new ObjectSpawn(0x1490, 0x0170, 0x66, 0x00, 0, false, 0));
        junction.setServices(services(main, List.of(firstSidekick, secondSidekick)));
        prepareLeftGapCapture(junction, secondSidekick);

        junction.update(1, main);

        assertSame(secondSidekick, getPrivateObject(junction, "controlledPlayer"));
        assertFalse(main.isObjectControlled());
        assertFalse(firstSidekick.isObjectControlled());
        assertTrue(secondSidekick.isObjectControlled());
    }

    @Test
    void unloadingReleasesCapturedExtensionSidekick() throws Exception {
        TestPlayableSprite main = playerLeftOfJunction();
        TestPlayableSprite firstSidekick = playerLeftOfJunction();
        TestPlayableSprite secondSidekick = playerLeftOfJunction();
        TestableJunction junction = new TestableJunction(
                new ObjectSpawn(0x1490, 0x0170, 0x66, 0x00, 0, false, 0));
        junction.setServices(services(main, List.of(firstSidekick, secondSidekick)));
        prepareLeftGapCapture(junction, secondSidekick);
        junction.update(1, main);

        junction.onUnload();

        assertFalse(secondSidekick.isObjectControlled());
        assertFalse(secondSidekick.isControlLocked());
        assertEquals(-1, secondSidekick.getForcedAnimationId());
    }

    private static int getPrivateInt(Object instance, String fieldName) throws Exception {
        Field field = Sonic1JunctionObjectInstance.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(instance);
    }

    private static void setPrivateInt(Object instance, String fieldName, int value) throws Exception {
        Field field = Sonic1JunctionObjectInstance.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(instance, value);
    }

    private static void setPrivateObject(Object instance, String fieldName, Object value) throws Exception {
        Field field = Sonic1JunctionObjectInstance.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static Object getPrivateObject(Object instance, String fieldName) throws Exception {
        Field field = Sonic1JunctionObjectInstance.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static TestPlayableSprite playerLeftOfJunction() {
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x1400);
        return player;
    }

    private static void prepareLeftGapCapture(TestableJunction junction, TestPlayableSprite... pushingPlayers)
            throws Exception {
        Map<com.openggf.game.PlayableEntity, PlayerSolidContactResult> contacts =
                new java.util.IdentityHashMap<>();
        for (TestPlayableSprite player : pushingPlayers) {
            player.setCentreX((short) 0x1400);
            contacts.put(player, pushingContact());
        }
        junction.setCheckpointBatch(new SolidCheckpointBatch(junction, contacts));
        setPrivateInt(junction, "mappingFrame", 0x0E);
        setPrivateInt(junction, "frameTimer", 1);
        setPrivateObject(junction, "childInstance",
                new Sonic1JunctionObjectInstance.Sonic1JunctionChildInstance(junction.getSpawn()));
    }

    private static PlayerSolidContactResult pushingContact() {
        return new PlayerSolidContactResult(
                ContactKind.SIDE, false, false, true, false, null, null, 0);
    }

    private static TestObjectServices services(TestPlayableSprite main, List<TestPlayableSprite> sidekicks) {
        return new TestObjectServices() {
            private final Sonic1SwitchManager switches = new Sonic1SwitchManager();
            private final ObjectPlayerQuery query = new ObjectPlayerQuery(() -> main, () -> sidekicks);

            @Override
            public ObjectPlayerQuery playerQuery() {
                return query;
            }

            @Override
            public <T> T gameService(Class<T> type) {
                return type == Sonic1SwitchManager.class ? type.cast(switches) : null;
            }
        };
    }

    private static final class TestableJunction extends Sonic1JunctionObjectInstance {
        private SolidCheckpointBatch checkpointBatch = new SolidCheckpointBatch(this, Map.of());

        private TestableJunction(ObjectSpawn spawn) {
            super(spawn);
        }

        private void setCheckpointBatch(SolidCheckpointBatch checkpointBatch) {
            this.checkpointBatch = checkpointBatch;
        }

        @Override
        protected SolidCheckpointBatch checkpointAll() {
            return checkpointBatch;
        }
    }
}
