package com.openggf.game.sonic2.debug;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameServices;
import com.openggf.game.LevelDebugPlacement;
import com.openggf.game.LevelSelectProvider;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
public class TestSonic2NativeDebugPlacement {
    @TempDir
    Path tempDir;

    @Test
    void controllerPlacesAtCursorAndResumesWithResetState() throws Exception {
        var fixture = fixture("......B.", "........", ".....A..", "...R....", ".......C",
                "...R....", "...R....", "...R....", "...R....",
                "...R....", "...R....", "...R....", "...R....", "......B.");
        var player = fixture.sprite();
        var camera = fixture.camera();
        fixture.stepIdleFrames(2);
        enableFromSoundTest(GameServices.level().getGameModule().getLevelSelectProvider(),
                1, 9, 9, 2, 1, 1, 2, 4);
        player.setXSpeed((short) 0x400);
        short entryX = player.getCentreX();
        short entryY = player.getCentreY();
        short minY = camera.getMinY();
        short maxY = camera.getMaxYTarget();

        fixture.stepFrameFromRecording(); // B enters from player control before normal motion.
        assertEquals(entryY, player.getCentreY(), "B with the cheat must bypass normal player movement");
        fixture.stepFrameFromRecording();
        assertEquals(entryX, player.getCentreX(), "Placement keeps the cursor stationary without directions");
        fixture.stepFrameFromRecording(); // A selects the first monitor after the ring.
        fixture.stepFrameFromRecording(); // Right moves the cursor.
        short cursorX = player.getCentreX();
        short cursorY = player.getCentreY();
        assertTrue(cursorX > entryX);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var beforePlacement = registry.capture();
        fixture.stepFrameFromRecording(); // C places the monitor.
        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream().anyMatch(object ->
                object.getSpawn().objectId() == 0x26 && object.getSpawn().x() == cursorX
                        && object.getSpawn().y() == cursorY));

        for (int i = 0; i < 8; i++) fixture.stepFrameFromRecording();
        short exitX = player.getCentreX();
        short exitY = player.getCentreY();
        camera.setMinY((short) (minY + 8));
        camera.setMaxYTarget((short) (maxY - 8));
        fixture.stepFrameFromRecording(); // B exits.
        assertEquals(exitX, player.getCentreX());
        assertEquals(exitY, player.getCentreY());
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getYSpeed());
        assertEquals(0, player.getGSpeed());
        assertTrue(player.getAir());
        assertFalse(player.isObjectControlled());
        assertEquals(minY, camera.getMinY());
        assertEquals(maxY, camera.getMaxYTarget());

        registry.restore(beforePlacement);
        assertTrue(LevelDebugPlacement.of(GameServices.gameState()).isActive());
        assertEquals(1, LevelDebugPlacement.of(GameServices.gameState()).capture().selected());
        assertEquals(cursorX, player.getCentreX());
        assertFalse(GameServices.level().getObjectManager().getActiveObjects().stream().anyMatch(object ->
                object.getSpawn().objectId() == 0x26 && object.getSpawn().x() == cursorX
                        && object.getSpawn().y() == cursorY));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void bEntersFromHurtOrDeadAndRestoresTheControlRoutine(boolean dead) throws Exception {
        var fixture = fixture("......B.", "........", "......B.", "........", ".....A.C", "......B.");
        var placement = LevelDebugPlacement.of(GameServices.gameState());
        fixture.stepIdleFrames(2);
        fixture.stepFrameFromRecording();
        assertFalse(placement.isActive(), "B alone never authorizes placement");
        fixture.stepFrameFromRecording();

        var player = fixture.sprite();
        placement.enableCheat();
        player.setHurt(!dead);
        player.setDead(dead);
        player.setControlLocked(true);
        fixture.stepFrameFromRecording();
        assertTrue(placement.isActive());
        assertFalse(player.isControlLocked());

        fixture.stepFrameFromRecording();
        fixture.stepFrameFromRecording();
        assertEquals(18, placement.capture().selected(), "Held A plus C wraps backward from the first row");
        fixture.stepFrameFromRecording();
        assertFalse(placement.isActive());
        assertFalse(player.isHurt());
        assertFalse(player.getDead());
    }

    @Test
    void cpzTubeDoesNotCaptureTheCursor() throws Exception {
        var fixture = fixture(1, "......B.", "........", "......B.", "........");
        fixture.stepIdleFrames(2);
        LevelDebugPlacement.of(GameServices.gameState()).enableCheat();
        fixture.stepFrameFromRecording();
        var player = fixture.sprite();
        var tube = new ObjectSpawn(player.getCentreX() - 0x10, player.getCentreY(),
                0x1E, 2, 0, false, 0);
        assertTrue(ObjectManagerInternalAccess.placeDebugObject(GameServices.level().getObjectManager(), tube));

        fixture.stepFrameFromRecording();
        assertFalse(player.isObjectControlled());
        assertEquals(tube.y(), player.getCentreY());
        fixture.stepFrameFromRecording();
        assertTrue(player.isObjectControlled(), "The same entry becomes eligible after exiting placement");
    }

    @Test
    void romIsDefaultAndEngineFreeFlyRemainsSelectable() throws Exception {
        var fixture = fixture("......B.");
        var config = GameServices.configuration();
        assertEquals("rom", config.getDefaultValue(SonicConfiguration.LEVEL_DEBUG_MODE));
        config.setConfigValue(SonicConfiguration.DEBUG_VIEW_ENABLED, true);
        var input = new InputHandler();
        input.handleKeyEvent(config.getInt(SonicConfiguration.DEBUG_MODE_KEY),
                org.lwjgl.glfw.GLFW.GLFW_PRESS);
        GameServices.sprites().update(input);
        assertFalse(fixture.sprite().isDebugMode());

        config.setConfigValue(SonicConfiguration.LEVEL_DEBUG_MODE, "engine");
        GameServices.sprites().update(input);
        assertTrue(fixture.sprite().isDebugMode());
        LevelDebugPlacement.of(GameServices.gameState()).enableCheat();
        fixture.stepFrameFromRecording();
        assertFalse(LevelDebugPlacement.of(GameServices.gameState()).isActive());
        config.setConfigValue(SonicConfiguration.LEVEL_DEBUG_MODE, "rom");
        GameServices.sprites().update(input);
        assertFalse(fixture.sprite().isDebugMode(), "Changing the setting releases free-fly");
    }

    @Test
    void debugRingUsesARewindableSlotAndCanBeCollectedAfterExit() throws Exception {
        var fixture = fixture("......B.", "........", ".......C", "........", "......B.");
        fixture.stepIdleFrames(2);
        LevelDebugPlacement.of(GameServices.gameState()).enableCheat();
        fixture.stepFrameFromRecording();
        fixture.stepFrameFromRecording();
        fixture.stepFrameFromRecording();
        var registry = fixture.gameplayMode().getRewindRegistry();
        var withRing = registry.capture();
        fixture.stepFrameFromRecording();
        assertEquals(0, fixture.sprite().getRingCount());
        fixture.stepFrameFromRecording();
        fixture.stepIdleFrames(3);
        assertEquals(1, fixture.sprite().getRingCount());
        registry.restore(withRing);
        assertTrue(LevelDebugPlacement.of(GameServices.gameState()).isActive());
        assertEquals(0, fixture.sprite().getRingCount());
        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream().anyMatch(object ->
                object instanceof com.openggf.game.sonic2.objects.Sonic2DebugRingObjectInstance));
    }

    private HeadlessTestFixture fixture(String... pads) throws Exception {
        return fixture(0, pads);
    }

    private HeadlessTestFixture fixture(int zone, String... pads) throws Exception {
        Path path = tempDir.resolve("placement.bk2");
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("Input Log.txt"));
            zip.write("[Input]\nLogKey:|UDLRSABC|\n".getBytes(StandardCharsets.UTF_8));
            for (String pad : pads) {
                zip.write(("|" + pad + "|\n").getBytes(StandardCharsets.UTF_8));
            }
            zip.write("[/Input]\n".getBytes(StandardCharsets.UTF_8));
        }
        return HeadlessTestFixture.builder().withZoneAndAct(zone, 0).withRecording(path).build();
    }

    public static void enableFromSoundTest(LevelSelectProvider select, int... code) {
        var input = new InputHandler();
        select.initialize();
        for (int i = 0; i < 100; i++) select.update(input);
        menuInput(select, input, AbstractPlayableSprite.INPUT_UP, 0, false);
        assertTrue(select.isSoundTestSelected());
        int track = 0;
        for (int sound : code) {
            while (track != sound) {
                menuInput(select, input, track < sound ? AbstractPlayableSprite.INPUT_RIGHT
                        : AbstractPlayableSprite.INPUT_LEFT, 0, false);
                track += Integer.signum(sound - track);
            }
            menuInput(select, input, 0, InputActionMasks.ACTION_C, true);
        }
        menuInput(select, input, AbstractPlayableSprite.INPUT_DOWN, 0, false);
        menuInput(select, input, 0, InputActionMasks.ACTION_A, true);
        select.reset();
        GameServices.gameState().startNewGameFromTitle();
        select.onGameplayStart();
    }

    private static void menuInput(LevelSelectProvider select, InputHandler input,
                                  int direction, int action, boolean accept) {
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(direction, direction, action, action, accept, accept),
                PlayerInputState.neutral()).withMenuPolicy(accept, false));
        select.update(input);
        input.setLogicalOverride(LogicalInputSnapshot.neutral());
        select.update(input);
    }
}
