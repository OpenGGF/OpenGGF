package com.openggf.game.sonic2.debug;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.LevelDebugPlacement;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sky Chase Act 1 played from its ordinary level start. SwScrl_SCZ
 * (s2.asm:17847-17867) advances the camera by the Tornado velocity unless
 * placement is active, RunDynamicLevelEvents (s2.asm:20329-20350) eases the
 * live bottom, and native placement saves and restores the vertical bounds
 * around the ordinary bottom-boundary kill.
 */
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2SkyChaseDebugPlacementGameplay {
    private static final int SKY_CHASE = 8;
    private static final int RIDE_FRAMES = 180;
    private static final int RIGHT_FRAMES = 100;
    private static final int DOWN_FRAMES = 150;

    @TempDir
    Path tempDir;

    @Test
    void placementSuspendsTheTornadoScrollAndExitResumesNormalBoundsMovementAndPitDeath() throws Exception {
        List<String> pads = new ArrayList<>(Collections.nCopies(RIDE_FRAMES, "........"));
        pads.add("......B.");                                        // enter placement
        pads.addAll(Collections.nCopies(8, "........"));
        pads.addAll(Collections.nCopies(DOWN_FRAMES, ".D......"));    // dive the cursor below the pit
        int downEnd = pads.size();
        pads.addAll(Collections.nCopies(RIGHT_FRAMES, "...R...."));   // (replayed after rewind)
        pads.add("......B.");                                        // exit placement
        pads.addAll(Collections.nCopies(600, "........"));
        HeadlessTestFixture fixture = fixture(pads);
        AbstractPlayableSprite player = fixture.sprite();
        Camera camera = fixture.camera();
        LevelDebugPlacement placement = LevelDebugPlacement.of(GameServices.gameState());

        // --- Ordinary Tornado ride: camera driven by Tornado_Velocity, no player tracking.
        int movingFrames = 0;
        for (int i = 0; i < RIDE_FRAMES; i++) {
            short x = camera.getX();
            fixture.stepFrameFromRecording();
            int dx = camera.getX() - x;
            assertTrue(dx == 0 || dx == 1, "Tornado scroll advances X by Tornado_Velocity_X, got " + dx);
            movingFrames += dx;
            assertFalse(player.getDead(), "riding the Tornado frame " + i);
        }
        assertTrue(movingFrames > RIDE_FRAMES / 2, "the Tornado scroll must be running before placement: " + movingFrames);
        short savedTop = camera.getMinY();
        short savedBottomTarget = camera.getMaxYTarget();
        short rideMaxY = camera.getMaxY();
        int rideTornadoX = tornadoX();
        assertTrue(Math.abs(player.getCentreX() - rideTornadoX) < 0x40, "Sonic starts riding the Tornado");

        var registry = fixture.gameplayMode().getRewindRegistry();
        var beforeEntry = registry.capture();
        short entryCameraX = camera.getX();
        short entryCameraY = camera.getY();
        short entryPlayerY = player.getCentreY();

        placement.enableCheat();
        fixture.stepFrameFromRecording();
        assertTrue(placement.isActive(), "B with the cheat enters placement during the Tornado ride");
        for (int i = 0; i < 8; i++) {
            short x = camera.getX();
            fixture.stepFrameFromRecording();
            assertEquals(x, camera.getX(), "a stationary cursor holds the camera; the Tornado scroll is suspended");
        }
        var placing = registry.capture();

        // --- Holding Down drives the cursor to Debug_Init's bottom clamp; the kill never runs.
        for (int i = 0; i < DOWN_FRAMES; i++) fixture.stepFrameFromRecording();
        assertEquals((short) (camera.getMaxYTarget() + 223), player.getCentreY(),
                "cursor clamps at the bottom target + 223 (loc_41BA4, s2.asm:88703-88716)");
        assertTrue(placement.isActive());
        assertFalse(player.getDead(), "placement bypasses the bottom-boundary kill");

        // --- Rewind back to the shallow cursor, then move right ahead of the Tornado and exit.
        registry.restore(placing);
        assertTrue(placement.isActive());
        for (int i = 0; i < RIGHT_FRAMES; i++) fixture.stepFrameFromRecording();
        assertTrue(player.getCentreX() > tornadoX() + 0x100,
                "cursor ahead of the Tornado: " + player.getCentreX() + " vs " + tornadoX());
        fixture.stepFrameFromRecording();
        assertFalse(placement.isActive(), "B exits placement");
        assertEquals(savedTop, camera.getMinY(), "exit restores the saved top bound");
        assertEquals(savedBottomTarget, camera.getMaxYTarget(), "exit restores the saved bottom target");
        assertEquals(0, player.getYSpeed());
        assertTrue(player.getAir());

        // --- Normal movement and Tornado scroll resume; the player falls to the live-limit kill.
        // Sonic_LevelBound (s2.asm:37233-37270) runs before ObjectMoveAndFall and
        // reads the live word that the previous frame's RunDynamicLevelEvents left.
        int resumedMoves = 0;
        int deathFrame = -1;
        for (int i = 0; i < 600 && deathFrame < 0; i++) {
            short x = camera.getX();
            short ySpeed = player.getYSpeed();
            int yBefore = player.getCentreY();
            int killLine = camera.getMaxY() + 0xE0;
            fixture.stepFrameFromRecording();
            int dx = camera.getX() - x;
            assertTrue(dx == 0 || dx == 1, "post-exit camera follows Tornado_Velocity_X again, got " + dx);
            resumedMoves += dx;
            String state = "frame " + i + " y=" + yBefore + " killLine=" + killLine;
            if (player.getDead()) {
                assertTrue(yBefore > killLine, "death only past the live bottom + $E0: " + state);
                deathFrame = i;
                break;
            }
            assertTrue(yBefore <= killLine, "alive only at or above the live bottom + $E0: " + state);
            assertTrue(player.getYSpeed() > ySpeed || player.getYSpeed() >= 0x1000,
                    "ordinary airborne gravity applies after exit: " + state);
        }
        assertTrue(deathFrame > 0, "falling from ahead of the Tornado ends in the bottom-boundary death");
        assertTrue(resumedMoves > deathFrame / 2, "Tornado scroll resumed: " + resumedMoves + "/" + deathFrame);

        // --- Rewind to the ride before entry: normal state and Tornado scroll return intact.
        registry.restore(beforeEntry);
        assertFalse(placement.isActive());
        assertFalse(player.getDead());
        assertEquals(entryCameraX, camera.getX());
        assertEquals(entryCameraY, camera.getY());
        assertEquals(entryPlayerY, player.getCentreY());
        assertEquals(savedTop, camera.getMinY());
        assertEquals(savedBottomTarget, camera.getMaxYTarget());
        assertEquals(rideMaxY, camera.getMaxY());
        int replayMoves = 0;
        for (int i = 0; i < 60; i++) {
            short x = camera.getX();
            fixture.stepIdleFrames(1);
            replayMoves += camera.getX() - x;
            assertFalse(placement.isActive());
            assertFalse(player.getDead(), "restored ride frame " + i);
        }
        assertTrue(replayMoves > 30, "restored ride keeps scrolling: " + replayMoves);
        System.out.printf("SCZ placement gameplay: rideMoves=%d deathFrame=%d resumedMoves=%d replayMoves=%d%n",
                movingFrames, deathFrame, resumedMoves, replayMoves);
    }

    private static int tornadoX() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(object -> object instanceof com.openggf.game.sonic2.objects.TornadoObjectInstance)
                .mapToInt(object -> ((com.openggf.game.sonic2.objects.TornadoObjectInstance) object).getX())
                .findFirst().orElseThrow();
    }

    private HeadlessTestFixture fixture(List<String> pads) throws Exception {
        Path path = tempDir.resolve("scz-placement.bk2");
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("Input Log.txt"));
            zip.write("[Input]\nLogKey:|UDLRSABC|\n".getBytes(StandardCharsets.UTF_8));
            for (String pad : pads) zip.write(("|" + pad + "|\n").getBytes(StandardCharsets.UTF_8));
            zip.write("[/Input]\n".getBytes(StandardCharsets.UTF_8));
        }
        return HeadlessTestFixture.builder().withZoneAndAct(SKY_CHASE, 0).withRecording(path).build();
    }
}
