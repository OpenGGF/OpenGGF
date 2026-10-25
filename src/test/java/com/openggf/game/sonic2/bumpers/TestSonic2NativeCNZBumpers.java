package com.openggf.game.sonic2.bumpers;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.CameraSnapshot;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestSonic2NativeCNZBumpers {
    static final int BASE = 0x1781A;

    @BeforeEach void setup() { TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2); }

    static CNZNativeBumperTable table(int[]... records) {
        ByteBuffer bytes = ByteBuffer.allocate((records.length + 2) * 6);
        bytes.putShort((short) 0x4EF9).putShort((short) 0).putShort((short) 0x1370);
        for (int[] record : records) {
            for (int value : record) bytes.putShort((short) value);
        }
        bytes.putShort((short) 0).putShort((short) 0xFFFF).putShort((short) 0);
        return new CNZNativeBumperTable(BASE, bytes.array());
    }

    static final class Fixture {
        final AbstractPlayableSprite[] players = {new Sonic("sonic", (short) 0, (short) 0),
                new Tails("tails", (short) 0, (short) 0)};
        final Camera[] cameras = {new Camera(SonicConfigurationService.getInstance()),
                new Camera(SonicConfigurationService.getInstance())};
        final NativeCompetitionRoster roster = mock(NativeCompetitionRoster.class);
        final List<Integer> sounds = new ArrayList<>();
        final Sonic2NativeCNZBumpers bumpers;
        Fixture(CNZNativeBumperTable table) {
            for (int slot = 0; slot < 2; slot++) {
                when(roster.player(slot)).thenReturn(players[slot]);
                when(roster.camera(slot)).thenReturn(cameras[slot]);
                players[slot].applyCustomRadii(9, 19);
                playerAt(slot, 100, 256);
            }
            bumpers = new Sonic2NativeCNZBumpers(table, roster, sounds::add);
        }
        void playerAt(int slot, int x, int y) {
            NativePositionOps.writeXPosResetSubpixel(players[slot], x);
            NativePositionOps.writeYPosResetSubpixel(players[slot], y);
        }
        void camerasAt(int first, int second) {
            cameras[0].setX((short) first); cameras[1].setX((short) second);
        }
        void move(int first, int second) { camerasAt(first, second); bumpers.updateCursors(); }
        void assertPair(int slot, int firstIndex, int endIndex) {
            assertEquals(BASE + firstIndex * 6, bumpers.startAddress(slot));
            assertEquals(BASE + endIndex * 6, bumpers.endAddress(slot));
        }
        RewindRegistry snapshots() {
            RewindRegistry registry = new RewindRegistry();
            registry.register(new RewindSnapshottable<Actors>() {
                @Override public String key() { return "players-and-cameras"; }
                @Override public Actors capture() {
                    return new Actors(players[0].captureRewindState(), players[1].captureRewindState(),
                            cameras[0].capture(), cameras[1].capture());
                }
                @Override public void restore(Actors state) {
                    players[0].restoreRewindState(state.first()); players[1].restoreRewindState(state.second());
                    cameras[0].restore(state.firstCamera(), players[0]);
                    cameras[1].restore(state.secondCamera(), players[1]);
                }
            });
            registry.register(bumpers.rewindAdapter());
            return registry;
        }
    }

    private record Actors(PerObjectRewindSnapshot first, PerObjectRewindSnapshot second,
                          CameraSnapshot firstCamera, CameraSnapshot secondCamera) { }

    @Test void requiresExplicitRosterAndNeverUsesOrdinaryPlayerDiscovery() {
        assertThrows(NullPointerException.class, () -> new Sonic2NativeCNZBumpers(table(), null, s -> { }));
        Fixture f = new Fixture(table(new int[]{4, 100, 256}));
        assertFalse(f.bumpers.checkPlayerCollision(0), "collision does not silently initialize cursors");
        assertThrows(IllegalArgumentException.class, () -> f.bumpers.checkPlayerCollision(2));
    }

    @Test void initMirrorsP1ThenMainSeparatesWithNoUnionBetweenViews() {
        Fixture f = new Fixture(table(new int[]{4, 100, 256}, new int[]{4, 600, 256},
                new int[]{4, 1000, 256}));
        clearInvocations(f.roster);
        f.move(0, 1000);
        verify(f.roster, never()).camera(1);
        f.assertPair(0, 0, 1); f.assertPair(1, 0, 1);
        clearInvocations(f.roster);
        f.bumpers.updateCursors();
        var order = inOrder(f.roster);
        order.verify(f.roster).camera(0); order.verify(f.roster).camera(1);
        f.assertPair(0, 0, 1); f.assertPair(1, 2, 3);
        f.playerAt(0, 600, 256); f.playerAt(1, 600, 256);
        assertFalse(f.bumpers.checkPlayerCollision(0)); assertFalse(f.bumpers.checkPlayerCollision(1));
        f.playerAt(0, 1000, 256); f.playerAt(1, 1000, 256);
        assertFalse(f.bumpers.checkPlayerCollision(0), "P1 cannot borrow P2's window");
        assertTrue(f.bumpers.checkPlayerCollision(1));
        assertEquals(List.of(1), f.sounds);
    }

    @Test void edgesAreHalfOpenAndDuplicateAddressesRemainDistinct() {
        Fixture f = new Fixture(table(new int[]{4, 0, 256}, new int[]{4, 1, 256},
                new int[]{4, 92, 256}, new int[]{4, 92, 256},
                new int[]{4, 427, 256}, new int[]{4, 428, 256}, new int[]{4, 428, 256}));
        f.move(100, 100);
        f.assertPair(0, 2, 5); f.assertPair(1, 2, 5);
        f.move(436, 436);
        f.assertPair(0, 5, 7);
        for (int camera : new int[]{8, 7, 0, 9}) {
            f.move(camera, camera);
            f.assertPair(0, 1, 4); f.assertPair(1, 1, 4);
        }
        f.move(0x8000, 0x8000);
        f.assertPair(0, 7, 7); // Camera words are unsigned, not negative-clamped Java shorts.
    }

    @Test void opposedMovementAndCrossingMoveEachPhysicalPairIndependently() {
        int[] xs = {1, 100, 200, 600, 900, 1000, 1200};
        int[][] records = new int[xs.length][];
        for (int i = 0; i < xs.length; i++) records[i] = new int[]{4, xs[i], 256};
        Fixture f = new Fixture(table(records));
        f.move(0, 0);
        for (int[] cameras : new int[][]{{0, 1000}, {100, 900}, {500, 500}, {900, 100}, {1000, 0}}) {
            f.move(cameras[0], cameras[1]);
            for (int slot = 0; slot < 2; slot++) {
                int left = Math.max(1, cameras[slot] - 8);
                int start = 0, end = 0;
                while (start < xs.length && xs[start] < left) start++;
                while (end < xs.length && xs[end] < left + 0x150) end++;
                f.assertPair(slot, start, end);
            }
        }
    }

    @Test void twoHumansCanBounceFromSameMapBumperWithoutConsumption() {
        Fixture f = new Fixture(table(new int[]{4, 100, 256}));
        f.move(0, 0);
        for (var player : f.players) {
            player.setAir(false); player.setPushing(true); player.setRollingJump(true); player.setJumping(true);
            player.setGSpeed((short) 0x321); player.setInvulnerableFrames(120);
            player.setXSpeed((short) 0x100); player.setYSpeed((short) 0);
        }
        assertTrue(f.bumpers.checkPlayerCollision(0));
        assertEquals(0x100, f.players[1].getXSpeed(), "P1 response must not mutate P2");
        assertTrue(f.bumpers.checkPlayerCollision(1));
        assertEquals(List.of(0, 1), f.sounds);
        for (var player : f.players) {
            assertTrue(player.getAir()); assertFalse(player.getPushing());
            assertFalse(player.getRollingJump()); assertFalse(player.isJumping());
            assertEquals(0x321, player.getGSpeed()); assertEquals(120, player.getInvulnerableFrames());
        }
    }

    @Test void firstTriangleBoxRejectReturnsWithoutSoundFlagsOrTryingLaterBumper() {
        Fixture f = new Fixture(table(new int[]{0, 100, 256}, new int[]{4, 100, 250}));
        f.move(0, 0); f.playerAt(0, 100, 230);
        f.players[0].setXSpeed((short) 0x123); f.players[0].setYSpeed((short) 0x234);
        f.players[0].setJumping(true); f.players[0].setPushing(true);
        assertFalse(f.bumpers.checkPlayerCollision(0));
        assertEquals(0x123, f.players[0].getXSpeed()); assertEquals(0x234, f.players[0].getYSpeed());
        assertTrue(f.players[0].isJumping()); assertTrue(f.players[0].getPushing());
        assertFalse(f.players[0].getAir()); assertTrue(f.sounds.isEmpty());
        Fixture secondOnly = new Fixture(table(new int[]{4, 100, 250}));
        secondOnly.move(0, 0); secondOnly.playerAt(0, 100, 230);
        assertTrue(secondOnly.bumpers.checkPlayerCollision(0), "later entry would have bounced if scanned");
    }

    @ParameterizedTest
    @CsvSource({"4, 490, -2510", "6, 490, 2510", "8, -2510, 490", "10, 2510, 490"})
    void exactCentreNarrowAnglesUseNativeZeroBranch(int id, int expectedX, int expectedY) {
        Fixture f = new Fixture(table(new int[]{id, 100, 256}));
        f.move(0, 0);
        // Incoming angle=$00 forces each native surface angle rather than reflecting.
        f.players[0].setXSpeed((short) 0x100); f.players[0].setYSpeed((short) 0);
        assertTrue(f.bumpers.checkPlayerCollision(0));
        assertEquals(expectedX, f.players[0].getXSpeed()); assertEquals(expectedY, f.players[0].getYSpeed());
    }

    @ParameterizedTest
    @CsvSource({"0, -2360, 970", "-1, -2510, -490"})
    void fullWordAngleRetains100BeforeSurfaceReflection(int dy, int expectedX, int expectedY) {
        // CalcAngle (s2.asm:4064-4072) returns WORD $100 for (+$100,-1), not byte $00.
        // loc_175EA subtracts the surface before comparing abs(delta)'s low byte.
        Fixture f = new Fixture(table(new int[]{8, 100, 256}));
        f.move(0, 0); f.playerAt(0, 100, 256 + dy);
        f.players[0].setXSpeed((short) 0x100); f.players[0].setYSpeed((short) -1);
        assertTrue(f.bumpers.checkPlayerCollision(0));
        assertEquals(expectedX, f.players[0].getXSpeed()); assertEquals(expectedY, f.players[0].getYSpeed());
    }

    @ParameterizedTest
    @CsvSource({"0, 0, 0, 0, -2560", "2, 0, 0, 1810, -1810",
            "0, 0, 32, 256, 2560", "2, 0, 32, 256, 2560",
            "0, 32, 0, 2560, 0", "2, -32, 0, -2560, 0"})
    void triangleHandlersReflectOrUseTheirNativeSideWrites(int id, int dx, int dy,
                                                          int expectedX, int expectedY) {
        Fixture f = new Fixture(table(new int[]{id, 100, 256}));
        f.move(0, 0); f.playerAt(0, 100 + dx, 256 + dy);
        f.players[0].setXSpeed((short) 0x100); f.players[0].setYSpeed((short) 0);
        assertTrue(f.bumpers.checkPlayerCollision(0));
        assertEquals(expectedX, f.players[0].getXSpeed()); assertEquals(expectedY, f.players[0].getYSpeed());
    }

    @ParameterizedTest
    @CsvSource({"4, 0, 8, 291, 2560", "6, 0, -8, 291, -2560",
            "8, 8, 0, 2560, 582", "10, -8, 0, 2560, 582",
            "8, 0, 64, 2560, 582", "10, 0, 64, 2560, 582"})
    void nativeAxisWritesRetainOtherVelocityIncludingShippedOddBranches(int id, int dx, int dy,
                                                                       int expectedX, int expectedY) {
        Fixture f = new Fixture(table(new int[]{id, 100, 256}));
        f.move(0, 0); f.playerAt(0, 100 + dx, 256 + dy);
        f.players[0].setXSpeed((short) 291); f.players[0].setYSpeed((short) 582);
        assertTrue(f.bumpers.checkPlayerCollision(0));
        assertEquals(expectedX, f.players[0].getXSpeed()); assertEquals(expectedY, f.players[0].getYSpeed());
    }

    @Test void collisionBoundsAreInclusiveAndUseDuckMappingFrameForEitherHuman() {
        Fixture f = new Fixture(table(new int[]{4, 100, 256}));
        f.move(0, 0);
        f.playerAt(0, 173, 256); assertTrue(f.bumpers.checkPlayerCollision(0));
        f.playerAt(0, 174, 256); assertFalse(f.bumpers.checkPlayerCollision(0));
        f.playerAt(1, 100, 281); // normal top265 is beyond bumper bottom264
        assertFalse(f.bumpers.checkPlayerCollision(1));
        f.playerAt(1, 100, 280); // normal top264 touches; duck raises top to276, same bottom.
        assertTrue(f.bumpers.checkPlayerCollision(1));
        f.players[1].setMappingFrame(0x4D);
        assertFalse(f.bumpers.checkPlayerCollision(1));
    }

    @Test void normalRoutineDispatchGuardsAndP1DebugDoNotBecomeInvulnerabilityGates() {
        Fixture f = new Fixture(table(new int[]{4, 100, 256}));
        f.move(0, 0);
        f.players[0].setHurt(true); assertFalse(f.bumpers.checkPlayerCollision(0));
        f.players[0].setHurt(false); f.players[0].setDead(true);
        assertFalse(f.bumpers.checkPlayerCollision(0));
        f.players[0].setDead(false); f.players[0].setObjectControlled(true);
        assertFalse(f.bumpers.checkPlayerCollision(0));
        f.players[0].setObjectControlAllowsCpu(true); // bit0 without bit7 still reaches touch
        assertTrue(f.bumpers.checkPlayerCollision(0));
        f.players[0].setDebugMode(true); assertFalse(f.bumpers.checkPlayerCollision(0));
    }

    @Test void collisionDoesNotAdvanceCursorsAheadOfNativePostObjectsPhase() {
        Fixture f = new Fixture(table(new int[]{4, 100, 256}, new int[]{4, 1000, 256}));
        f.move(0, 0); f.camerasAt(1000, 1000); f.playerAt(0, 1000, 256);
        assertFalse(f.bumpers.checkPlayerCollision(0));
        f.bumpers.updateCursors(); assertTrue(f.bumpers.checkPlayerCollision(0));
    }

    @Test void swapExchangesOnlyBothCursorPairsAndIsAnInvolution() {
        Fixture f = new Fixture(table(new int[]{4, 100, 256}, new int[]{4, 1000, 256}));
        f.move(0, 1000); f.bumpers.updateCursors();
        Object before = f.bumpers.rewindAdapter().capture();
        f.bumpers.swapNativeCompetitionSlots();
        f.assertPair(0, 1, 2); f.assertPair(1, 0, 1);
        assertEquals(0, f.cameras[0].getX()); assertEquals(1000, f.cameras[1].getX());
        assertEquals(100, f.players[0].getCentreX()); assertTrue(f.sounds.isEmpty());
        f.bumpers.swapNativeCompetitionSlots();
        assertTrue(RewindSnapshotDiff.keyEquals("bumpers", before, f.bumpers.rewindAdapter().capture()));
    }

    @Test void compositeRestoreAndForwardPreservePlayersCamerasBothPairsAndInitialization() {
        Fixture f = new Fixture(table(new int[]{4, 100, 256}, new int[]{8, 1000, 256}));
        RewindRegistry snapshots = f.snapshots();
        CompositeSnapshot uninitialized = snapshots.capture();
        f.move(0, 1000); f.bumpers.updateCursors();
        f.playerAt(1, 1000, 256);
        CompositeSnapshot start = snapshots.capture();
        List<CompositeSnapshot> expected = forward(f, snapshots);
        snapshots.restore(start); f.sounds.clear();
        List<CompositeSnapshot> replay = forward(f, snapshots);
        for (int frame = 0; frame < expected.size(); frame++) {
            for (String key : expected.get(frame).entries().keySet()) {
                assertEquals(List.of(), RewindSnapshotDiff.diffKey(key,
                        expected.get(frame).get(key), replay.get(frame).get(key)), "frame " + frame);
            }
        }
        assertEquals(List.of(0, 1, 1, 0), f.sounds);
        snapshots.restore(uninitialized);
        f.camerasAt(0, 1000); f.bumpers.updateCursors();
        f.assertPair(1, 0, 1); // Restored Init, not a Main call for camera2.
    }

    private static List<CompositeSnapshot> forward(Fixture f, RewindRegistry snapshots) {
        List<CompositeSnapshot> frames = new ArrayList<>();
        assertTrue(f.bumpers.checkPlayerCollision(0)); assertTrue(f.bumpers.checkPlayerCollision(1));
        frames.add(snapshots.capture());
        f.bumpers.swapNativeCompetitionSlots();
        // Root's full teleport owns actor/camera exchange. Emulate that ownership here.
        f.playerAt(0, 1000, 256); f.playerAt(1, 100, 256); f.camerasAt(1000, 0);
        assertTrue(f.bumpers.checkPlayerCollision(1)); assertTrue(f.bumpers.checkPlayerCollision(0));
        f.bumpers.updateCursors(); frames.add(snapshots.capture());
        f.move(500, 500); frames.add(snapshots.capture());
        return frames;
    }

    @Test void resetAndMissingAdapterSnapshotReturnToP1SeededInit() {
        Fixture f = new Fixture(table(new int[]{4, 100, 256}, new int[]{4, 1000, 256}));
        f.move(0, 1000); f.bumpers.updateCursors();
        f.bumpers.reset(); assertFalse(f.bumpers.checkPlayerCollision(0));
        f.bumpers.updateCursors(); f.assertPair(1, 0, 1);
        f.bumpers.updateCursors(); f.assertPair(1, 1, 2);
        RewindRegistry snapshots = new RewindRegistry(); snapshots.register(f.bumpers.rewindAdapter());
        snapshots.restore(new CompositeSnapshot(Map.of()));
        assertFalse(f.bumpers.checkPlayerCollision(0));
        f.bumpers.updateCursors(); f.assertPair(1, 0, 1);
    }
}
