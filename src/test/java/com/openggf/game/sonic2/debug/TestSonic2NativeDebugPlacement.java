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
import com.openggf.level.objects.SolidObjectPassTestSupport;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Tails;
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

    @Test
    void cursorTapsDelaysAcceleratesAndRestoresItsHoldState() throws Exception {
        var fixture = fixture("......B.");
        var player = fixture.sprite();
        LevelDebugPlacement.of(GameServices.gameState()).enableCheat();
        fixture.stepFrameFromRecording();
        fixture.stepIdleFrames(1);
        int start = cursorX(player);
        fixture.stepFrame(false, false, false, true, false);
        assertEquals(0x10000, cursorX(player) - start, "fresh right moves one pixel");
        for (int held = 1; held < 12; held++) {
            fixture.stepFrame(false, false, false, true, false);
            assertEquals(start + 0x10000, cursorX(player), "delay frame " + held);
        }
        var registry = fixture.gameplayMode().getRewindRegistry();
        var beforeRepeat = registry.capture();
        fixture.stepFrame(false, false, false, true, false);
        assertEquals(start + 0x21000, cursorX(player), "first repeat is 17/16 pixels");
        registry.restore(beforeRepeat);
        fixture.stepFrame(false, false, false, true, false);
        assertEquals(start + 0x21000, cursorX(player), "rewinding restores delay and speed");
        for (int speed = 0x12; speed <= 0x101; speed++) {
            int before = cursorX(player);
            fixture.stepFrame(false, false, false, true, false);
            assertEquals(Math.min(speed, 0x100) << 12, cursorX(player) - before);
        }
        fixture.stepIdleFrames(1);
        int beforeTap = cursorX(player);
        fixture.stepFrame(false, false, true, false, false);
        assertEquals(0x10000, beforeTap - cursorX(player));
    }

    @Test
    void skyChaseExitRestoresOnlyTheTwoSavedVerticalBounds() throws Exception {
        var fixture = fixture(8, "......B.");
        var camera = fixture.camera();
        camera.setMinX((short) 0x80);
        camera.setMaxX((short) 0x2400);
        short savedTop = camera.getMinY();
        short savedBottomTarget = camera.getMaxYTarget();
        LevelDebugPlacement.of(GameServices.gameState()).enableCheat();
        fixture.stepFrameFromRecording();
        assertEquals(0, camera.getMinX());
        assertEquals(0x3fff, camera.getMaxX());

        camera.setMaxXTarget((short) 0x3600);
        camera.setMaxYCurrent((short) 0x320);
        camera.setMinYCurrent((short) (savedTop + 8));
        camera.setMaxYTarget((short) (savedBottomTarget + 8));
        var input = new InputHandler();
        input.setLogicalOverride(pad(InputActionMasks.ACTION_B, InputActionMasks.ACTION_B));
        GameServices.sprites().update(input);
        assertEquals(savedTop, camera.getMinY());
        assertEquals(savedBottomTarget, camera.getMaxYTarget());
        assertEquals(0, camera.getMinX());
        assertEquals(0x3fff, camera.getMaxX());
        assertEquals(0x3600, camera.getMaxXTarget());
        assertEquals(0x320, camera.getMaxY());
    }

    @Test
    void soundTestUnlockSurvivesMenuReentryAndHeldAActivatesOnARegularLoad() throws Exception {
        fixture("........");
        var level = GameServices.level();
        var select = level.getGameModule().getLevelSelectProvider();
        enterSoundTestCode(select, 1, 9, 9, 2, 1, 1, 2, 4);
        select.initialize(); // Reopen the menu without running the code again.
        GameServices.gameState().startNewGameFromTitle();

        var input = new InputHandler();
        level.loadZoneAndAct(0, 0);
        input.setLogicalOverride(pad(InputActionMasks.ACTION_B, InputActionMasks.ACTION_B));
        GameServices.sprites().update(input);
        var placement = LevelDebugPlacement.of(GameServices.gameState());
        assertFalse(placement.isActive(), "a load without A leaves the cheat unarmed");
        input.setLogicalOverride(pad(InputActionMasks.ACTION_A, 0));
        GameServices.sprites().publishHeldInputForLevelEvents(input);
        level.loadZoneAndAct(0, 1);
        input.setLogicalOverride(pad(InputActionMasks.ACTION_B, InputActionMasks.ACTION_B));
        GameServices.sprites().update(input);
        assertTrue(placement.isActive(), "an ordinary act load grants the unlocked cheat with held A");
    }

    @Test
    void placingDisablesSolidContactsForTheCpuSidekick() throws Exception {
        var fixture = fixture("......B.");
        var player = fixture.sprite();
        var sidekick = new Tails("placement-sidekick", (short) 0, (short) 0);
        sidekick.setCpuControlled(true);
        GameServices.sprites().addSprite(sidekick, "tails");
        int x = player.getCentreX() + 48;
        int y = player.getCentreY() + 8;
        var manager = GameServices.level().getObjectManager();
        var spawn = new ObjectSpawn(x, y, 0x26, 0, 0, false, y);
        assertTrue(ObjectManagerInternalAccess.placeDebugObject(manager, spawn));
        var monitor = manager.getActiveObjects().stream()
                .filter(object -> object.getSpawn().equals(spawn)).findFirst().orElseThrow();

        LevelDebugPlacement.of(GameServices.gameState()).enableCheat();
        fixture.stepFrameFromRecording();
        positionForLanding(sidekick, x, y);
        assertFalse(SolidObjectPassTestSupport.participantCheckpoint(manager, monitor, sidekick, true)
                .perPlayer().get(sidekick).standingNow(), "Tails ignores the same solid while Sonic places");
        var input = new InputHandler();
        input.setLogicalOverride(pad(InputActionMasks.ACTION_B, InputActionMasks.ACTION_B));
        GameServices.sprites().update(input);
        positionForLanding(sidekick, x, y);
        assertTrue(SolidObjectPassTestSupport.participantCheckpoint(manager, monitor, sidekick, true)
                .perPlayer().get(sidekick).standingNow(), "Tails lands on the monitor after exit");
    }

    private static void positionForLanding(AbstractPlayableSprite player, int x, int y) {
        player.setCentreX((short) x);
        player.setCentreY((short) (y - 25));
        player.setAir(true);
        player.setYSpeed((short) 0x100);
    }

    private static int cursorX(AbstractPlayableSprite player) {
        return (player.getCentreX() << 16) | player.getXSubpixelRaw();
    }

    private static LogicalInputSnapshot pad(int held, int pressed) {
        return LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, held, pressed, false, false), PlayerInputState.neutral());
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
        var input = enterSoundTestCode(select, code);
        menuInput(select, input, AbstractPlayableSprite.INPUT_DOWN, 0, false);
        menuInput(select, input, 0, InputActionMasks.ACTION_A, true);
        select.reset();
        GameServices.gameState().startNewGameFromTitle();
        select.onGameplayStart();
        input.setLogicalOverride(pad(InputActionMasks.ACTION_A, 0));
        GameServices.sprites().publishHeldInputForLevelEvents(input);
        GameServices.level().initGameplayState();
        input.setLogicalOverride(LogicalInputSnapshot.neutral());
    }

    private static InputHandler enterSoundTestCode(LevelSelectProvider select, int... code) {
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
        return input;
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
