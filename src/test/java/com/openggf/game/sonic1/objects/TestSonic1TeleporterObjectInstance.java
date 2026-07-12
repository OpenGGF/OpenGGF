package com.openggf.game.sonic1.objects;

import org.junit.jupiter.api.Test;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.CompactFieldCapturer;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.rewind.schema.RewindObjectStateBlob;
import com.openggf.game.sonic1.constants.Sonic1AnimationIds;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSonic1TeleporterObjectInstance {

    @Test
    public void holdsRollAnimationWhileTeleportingAndClearsOnRelease() {
        Sonic1TeleporterObjectInstance teleporter = createTeleporter(0x00, 0, 0x300, 0x300);
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x300);
        player.setCentreY((short) 0x300);

        // Capture on first update.
        teleporter.update(1, player);
        assertTrue(player.isObjectControlled());
        assertFalse(player.isObjectControlAllowsCpu());
        assertTrue(player.isObjectControlSuppressesMovement());
        assertTrue(player.isTouchResponseSuppressedByObjectControl());
        assertTrue(player.isControlLocked());
        assertEquals(Sonic1AnimationIds.ROLL.id(), player.getForcedAnimationId());

        // Continue until release; roll animation must remain forced during transport.
        int frame = 2;
        while (player.isObjectControlled() && frame < 800) {
            teleporter.update(frame, player);
            if (player.isObjectControlled()) {
                assertEquals(Sonic1AnimationIds.ROLL.id(), player.getForcedAnimationId());
            }
            frame++;
        }

        assertFalse(player.isObjectControlled(), "teleporter should release within expected frame budget");
        assertFalse(player.isObjectControlAllowsCpu());
        assertFalse(player.isObjectControlSuppressesMovement());
        assertFalse(player.isTouchResponseSuppressedByObjectControl());
        assertFalse(player.isControlLocked());
        assertEquals(-1, player.getForcedAnimationId());
    }

    @Test
    public void unloadWhileActiveReleasesControlAndForcedAnimation() {
        Sonic1TeleporterObjectInstance teleporter = createTeleporter(0x00, 0, 0x300, 0x300);
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x300);
        player.setCentreY((short) 0x300);

        teleporter.update(1, player);
        assertTrue(player.isObjectControlled());
        assertTrue(player.isTouchResponseSuppressedByObjectControl());
        assertEquals(Sonic1AnimationIds.ROLL.id(), player.getForcedAnimationId());

        teleporter.onUnload();

        assertFalse(player.isObjectControlled());
        assertFalse(player.isObjectControlAllowsCpu());
        assertFalse(player.isObjectControlSuppressesMovement());
        assertFalse(player.isTouchResponseSuppressedByObjectControl());
        assertFalse(player.isControlLocked());
        assertEquals(-1, player.getForcedAnimationId());
    }

    @Test
    void extraSidekickCanOwnTransportWithoutRedirectingControlToMainPlayer() throws Exception {
        TestPlayableSprite main = new TestPlayableSprite();
        main.setCentreX((short) 0x200);
        main.setCentreY((short) 0x300);
        TestPlayableSprite firstSidekick = new TestPlayableSprite();
        firstSidekick.setCentreX((short) 0x200);
        firstSidekick.setCentreY((short) 0x300);
        TestPlayableSprite extraSidekick = new TestPlayableSprite();
        extraSidekick.setCentreX((short) 0x300);
        extraSidekick.setCentreY((short) 0x300);
        Sonic1TeleporterObjectInstance teleporter = createTeleporter(
                0x00, 0, 0x300, 0x300, main, List.of(firstSidekick, extraSidekick));

        teleporter.update(1, main);

        assertSame(extraSidekick, readField(teleporter, "controlledPlayer"));
        assertTrue(extraSidekick.isObjectControlled());
        assertFalse(main.isObjectControlled());
        assertFalse(firstSidekick.isObjectControlled());

        int mainX = main.getCentreX();
        int mainY = main.getCentreY();
        for (int frame = 2; frame < 800 && extraSidekick.isObjectControlled(); frame++) {
            teleporter.update(frame, main);
        }

        assertFalse(extraSidekick.isObjectControlled(), "captured extension sidekick must be released");
        assertEquals(mainX, main.getCentreX(), "active transport must not move the update-argument main player");
        assertEquals(mainY, main.getCentreY(), "active transport must not move the update-argument main player");
    }

    @Test
    void simultaneousEntryKeepsMainPlayerFirstInParticipationOrder() throws Exception {
        TestPlayableSprite main = new TestPlayableSprite();
        main.setCentreX((short) 0x300);
        main.setCentreY((short) 0x300);
        TestPlayableSprite extraSidekick = new TestPlayableSprite();
        extraSidekick.setCentreX((short) 0x300);
        extraSidekick.setCentreY((short) 0x300);
        TestPlayableSprite secondSidekick = new TestPlayableSprite();
        secondSidekick.setCentreX((short) 0x300);
        secondSidekick.setCentreY((short) 0x300);
        Sonic1TeleporterObjectInstance teleporter = createTeleporter(
                0x00, 0, 0x300, 0x300, main, List.of(extraSidekick, secondSidekick));

        teleporter.update(1, main);

        assertSame(main, readField(teleporter, "controlledPlayer"));
        assertTrue(main.isObjectControlled());
        assertFalse(extraSidekick.isObjectControlled());
        assertFalse(secondSidekick.isObjectControlled());
    }

    @Test
    void deadCapturedSidekickIsReleasedForRespawn() throws Exception {
        TestPlayableSprite main = new TestPlayableSprite();
        main.setCentreX((short) 0x200);
        main.setCentreY((short) 0x300);
        TestPlayableSprite extraSidekick = new TestPlayableSprite();
        extraSidekick.setCentreX((short) 0x300);
        extraSidekick.setCentreY((short) 0x300);
        Sonic1TeleporterObjectInstance teleporter = createTeleporter(
                0x00, 0, 0x300, 0x300, main, List.of(extraSidekick));
        teleporter.update(1, main);
        extraSidekick.setDead(true);

        teleporter.update(2, main);

        assertFalse(extraSidekick.isObjectControlled());
        assertFalse(extraSidekick.isControlLocked());
        assertEquals(-1, extraSidekick.getForcedAnimationId());
        assertNull(readField(teleporter, "controlledPlayer"));
    }

    @Test
    void unloadReleasesLaterExtensionSidekickOwner() {
        TestPlayableSprite main = playerAt(0x200, 0x300);
        TestPlayableSprite firstSidekick = playerAt(0x200, 0x300);
        TestPlayableSprite secondSidekick = playerAt(0x300, 0x300);
        Sonic1TeleporterObjectInstance teleporter = createTeleporter(
                0x00, 0, 0x300, 0x300, main, List.of(firstSidekick, secondSidekick));
        teleporter.update(1, main);

        teleporter.onUnload();

        assertFalse(secondSidekick.isObjectControlled());
        assertFalse(secondSidekick.isControlLocked());
        assertEquals(-1, secondSidekick.getForcedAnimationId());
    }

    @Test
    void laterExtensionSidekickOwnerRestoresThroughPlayerRefId() throws Exception {
        TestPlayableSprite capturedMain = playerAt(0x200, 0x300);
        TestPlayableSprite capturedFirstSidekick = playerAt(0x200, 0x300);
        TestPlayableSprite capturedSecondSidekick = playerAt(0x300, 0x300);
        Sonic1TeleporterObjectInstance teleporter = createTeleporter(
                0x00, 0, 0x300, 0x300, capturedMain,
                List.of(capturedFirstSidekick, capturedSecondSidekick));
        teleporter.update(1, capturedMain);
        RewindObjectStateBlob blob = CompactFieldCapturer.capture(
                teleporter,
                rewindContext(capturedMain, capturedFirstSidekick, capturedSecondSidekick));

        TestPlayableSprite restoredMain = new TestPlayableSprite();
        TestPlayableSprite restoredFirstSidekick = new TestPlayableSprite();
        TestPlayableSprite restoredSecondSidekick = new TestPlayableSprite();
        CompactFieldCapturer.restore(
                teleporter,
                blob,
                rewindContext(restoredMain, restoredFirstSidekick, restoredSecondSidekick));

        assertSame(restoredSecondSidekick, readField(teleporter, "controlledPlayer"));
    }

    private static Sonic1TeleporterObjectInstance createTeleporter(int subtype, int renderFlags, int x, int y) {
        Sonic1TeleporterObjectInstance instance = new Sonic1TeleporterObjectInstance(
                new ObjectSpawn(x, y, 0x72, subtype, renderFlags, false, 0));
        instance.setServices(new TestObjectServices());
        return instance;
    }

    private static Sonic1TeleporterObjectInstance createTeleporter(
            int subtype,
            int renderFlags,
            int x,
            int y,
            TestPlayableSprite main,
            List<TestPlayableSprite> sidekicks) {
        Sonic1TeleporterObjectInstance instance = new Sonic1TeleporterObjectInstance(
                new ObjectSpawn(x, y, 0x72, subtype, renderFlags, false, 0));
        instance.setServices(new TestObjectServices() {
            private final ObjectPlayerQuery query = new ObjectPlayerQuery(() -> main, () -> sidekicks);

            @Override
            public ObjectPlayerQuery playerQuery() {
                return query;
            }
        });
        return instance;
    }

    private static Object readField(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static TestPlayableSprite playerAt(int x, int y) {
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) x);
        player.setCentreY((short) y);
        return player;
    }

    private static RewindCaptureContext rewindContext(
            TestPlayableSprite main,
            TestPlayableSprite firstSidekick,
            TestPlayableSprite secondSidekick) {
        RewindIdentityTable table = new RewindIdentityTable();
        table.registerPlayer(main, PlayerRefId.mainPlayer());
        table.registerPlayer(firstSidekick, PlayerRefId.sidekick(0));
        table.registerPlayer(secondSidekick, PlayerRefId.sidekick(1));
        return RewindCaptureContext.withIdentityTable(table);
    }

}
