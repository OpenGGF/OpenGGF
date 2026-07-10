package com.openggf.game.sonic2.specialstage;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic2SpecialStagePlayerSnapshot {
    @Test
    void playerSnapshotRestoresDeterministicFieldsAndClonesControlBuffer() throws Exception {
        Sonic2SpecialStagePlayer player = new Sonic2SpecialStagePlayer(
                Sonic2SpecialStagePlayer.PlayerType.SONIC, true);
        seedPlayer(player);

        Sonic2SpecialStageSnapshot.PlayerSnapshot snapshot = player.captureRewindSnapshot();
        set(player, "ssXPos", 999);
        set(player, "ssXSub", 999);
        set(player, "ssYPos", 999);
        set(player, "ssYSub", 999);
        set(player, "ssZPos", 999);
        set(player, "anim", 99);
        set(player, "prevAnim", 99);
        set(player, "animFrame", 99);
        set(player, "animFrameDuration", 99);
        set(player, "ssInitFlipTimer", 99);
        set(player, "ssFlipTimer", 99);
        set(player, "ssLastAngleIndex", 99);
        set(player, "invulnerabilityCountdown", 0);

        player.restoreRewindSnapshot(snapshot);

        assertEquals(0x1234, get(player, "ssXPos"));
        assertEquals(0x56, get(player, "ssXSub"));
        assertEquals(0x2345, get(player, "ssYPos"));
        assertEquals(0x67, get(player, "ssYSub"));
        assertEquals(0x78, get(player, "ssZPos"));
        assertEquals(2, get(player, "anim"));
        assertEquals(1, get(player, "prevAnim"));
        assertEquals(3, get(player, "animFrame"));
        assertEquals(4, get(player, "animFrameDuration"));
        assertEquals(0x400, get(player, "ssInitFlipTimer"));
        assertEquals(5, get(player, "ssFlipTimer"));
        assertEquals(6, get(player, "ssLastAngleIndex"));
        assertEquals(30, get(player, "invulnerabilityCountdown"));
        assertEquals(0xAAAA, player.getControlRecordEntry(0));
        assertNotSame(snapshot.ctrlRecordBuf(), get(player, "ctrlRecordBuf"));
    }

    @Test
    void playerOwnedInvulnerabilityCountdownTicksInSpecialStageUpdatePath() throws Exception {
        Sonic2SpecialStagePlayer player = new Sonic2SpecialStagePlayer(
                Sonic2SpecialStagePlayer.PlayerType.SONIC, true);
        set(player, "invulnerabilityCountdown", 2);

        assertTrue(player.isInvulnerable());
        player.update(0, 0);
        assertEquals(1, player.getInvulnerabilityTicks());
        player.update(0, 0);
        assertEquals(0, player.getInvulnerabilityTicks());
    }

    @Test
    void topologySnapshotPreservesSoloAndTeamPlayerRoles() {
        Sonic2SpecialStagePlayer sonic = new Sonic2SpecialStagePlayer(
                Sonic2SpecialStagePlayer.PlayerType.SONIC, true);
        Sonic2SpecialStagePlayer tails = new Sonic2SpecialStagePlayer(
                Sonic2SpecialStagePlayer.PlayerType.TAILS, false);
        sonic.setOtherPlayer(tails);
        tails.setOtherPlayer(sonic);

        Sonic2SpecialStageSnapshot.PlayerTopologySnapshot topology =
                Sonic2SpecialStageSnapshot.PlayerTopologySnapshot.capture(
                        java.util.List.of(sonic, tails), sonic, tails);

        assertEquals(2, topology.slots().size());
        assertEquals(Sonic2SpecialStagePlayer.PlayerType.SONIC, topology.slots().get(0).type());
        assertTrue(topology.slots().get(0).mainCharacter());
        assertEquals(Sonic2SpecialStagePlayer.PlayerType.TAILS, topology.slots().get(1).type());
        assertEquals(0, topology.sonicSlotIndex());
        assertEquals(1, topology.tailsSlotIndex());
        assertTrue(topology.playersLinked());
    }

    @Test
    void restoreTopologyCoversSonicSoloTailsSoloAndTeamRelinking() throws Exception {
        assertSoloTopology(
                new Sonic2SpecialStagePlayer(Sonic2SpecialStagePlayer.PlayerType.SONIC, true),
                "sonicPlayer",
                "tailsPlayer");
        assertSoloTopology(
                new Sonic2SpecialStagePlayer(Sonic2SpecialStagePlayer.PlayerType.TAILS, true),
                "tailsPlayer",
                "sonicPlayer");

        Sonic2SpecialStageManager manager = new Sonic2SpecialStageManager();
        Sonic2SpecialStageRenderer renderer = new Sonic2SpecialStageRenderer(null);
        java.util.ArrayList<Sonic2SpecialStagePlayer> players = new java.util.ArrayList<>();
        Sonic2SpecialStagePlayer sonic = new Sonic2SpecialStagePlayer(
                Sonic2SpecialStagePlayer.PlayerType.SONIC, true);
        Sonic2SpecialStagePlayer tails = new Sonic2SpecialStagePlayer(
                Sonic2SpecialStagePlayer.PlayerType.TAILS, false);
        sonic.setOtherPlayer(tails);
        tails.setOtherPlayer(sonic);
        players.add(sonic);
        players.add(tails);
        set(manager, "players", players);
        set(manager, "sonicPlayer", sonic);
        set(manager, "tailsPlayer", tails);
        set(manager, "renderer", renderer);
        renderer.setPlayers(new java.util.ArrayList<>());

        Sonic2SpecialStageSnapshot.PlayerTopologySnapshot topology =
                Sonic2SpecialStageSnapshot.PlayerTopologySnapshot.capture(players, sonic, tails);
        java.util.List<Sonic2SpecialStageSnapshot.PlayerSnapshot> playerSnapshots =
                java.util.List.of(sonic.captureRewindSnapshot(), tails.captureRewindSnapshot());
        sonic.setOtherPlayer(null);
        tails.setOtherPlayer(null);

        manager.restorePlayerTopologyForRewind(topology, playerSnapshots);

        assertSame(sonic, get(manager, "sonicPlayer"));
        assertSame(tails, get(manager, "tailsPlayer"));
        assertSame(tails, sonic.getOtherPlayerForRewind());
        assertSame(sonic, tails.getOtherPlayerForRewind());
        assertSame(players, get(renderer, "players"));
    }

    private static void assertSoloTopology(Sonic2SpecialStagePlayer player,
                                           String presentField,
                                           String absentField) throws Exception {
        Sonic2SpecialStageManager manager = new Sonic2SpecialStageManager();
        Sonic2SpecialStageRenderer renderer = new Sonic2SpecialStageRenderer(null);
        java.util.ArrayList<Sonic2SpecialStagePlayer> players = new java.util.ArrayList<>();
        players.add(player);
        set(manager, "players", players);
        set(manager, presentField, player);
        set(manager, absentField, null);
        set(manager, "renderer", renderer);

        Sonic2SpecialStageSnapshot.PlayerTopologySnapshot topology =
                Sonic2SpecialStageSnapshot.PlayerTopologySnapshot.capture(
                        players,
                        "sonicPlayer".equals(presentField) ? player : null,
                        "tailsPlayer".equals(presentField) ? player : null);

        manager.restorePlayerTopologyForRewind(
                topology,
                java.util.List.of(player.captureRewindSnapshot()));

        assertSame(player, get(manager, presentField));
        assertNull(get(manager, absentField));
        assertNull(player.getOtherPlayerForRewind());
        assertSame(players, get(renderer, "players"));
    }

    private static void seedPlayer(Sonic2SpecialStagePlayer player) throws Exception {
        set(player, "ssXPos", 0x1234);
        set(player, "ssXSub", 0x56);
        set(player, "ssYPos", 0x2345);
        set(player, "ssYSub", 0x67);
        set(player, "ssZPos", 0x78);
        set(player, "xPos", 10);
        set(player, "yPos", 20);
        set(player, "xVel", 30);
        set(player, "yVel", 40);
        set(player, "inertia", 50);
        set(player, "angle", 60);
        set(player, "ssSlideTimer", 7);
        set(player, "ssHurtTimer", 8);
        set(player, "ssDplcTimer", 9);
        set(player, "ssInitFlipTimer", 0x400);
        set(player, "ssFlipTimer", 5);
        set(player, "ssLastAngleIndex", 6);
        set(player, "anim", 2);
        set(player, "prevAnim", 1);
        set(player, "animFrame", 3);
        set(player, "animFrameDuration", 4);
        set(player, "mappingFrame", 11);
        set(player, "globalAnimFrameTimer", 12);
        set(player, "collisionProperty", 13);
        set(player, "invulnerabilityCountdown", 30);
        int[] ctrl = (int[]) get(player, "ctrlRecordBuf");
        ctrl[0] = 0xAAAA;
        set(player, "ctrlRecordIndex", 3);
        set(player, "swapPositionsFlag", true);
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Object get(Object target, String field) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.get(target);
    }
}
