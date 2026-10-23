package com.openggf.editor;

import com.openggf.Engine;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.editor.persistence.EditorSaveManager;
import com.openggf.game.GameMode;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.session.EditorCursorState;
import com.openggf.game.session.EditorPlaytestStash;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.AbstractLevel;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.Map;
import com.openggf.level.MutableLevel;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.level.SolidTile;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;
import static org.lwjgl.glfw.GLFW.*;

/** Engine input/rendering integration over valid authored geometry; native playtests use the configured ROM. */
@Isolated
class TestEditorToggleIntegration {
    @TempDir Path tempSaves;
    private Rom fixtureRom;

    @BeforeEach
    void setUp() {
        RomManager.getInstance().setRom(null);
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() throws Exception {
        SessionManager.clear();
        if (fixtureRom != null) fixtureRom.close();
        RomManager.getInstance().setRom(null);
        Engine.clearGlobalInstance();
        TestEnvironment.resetAll();
        SonicConfigurationService.getInstance().resetToDefaults();
        GameModuleRegistry.reset();
    }

    @Test
    void enterEditorFromCurrentPlayer_whenEditorDisabled_rejectsActivation() {
        var engine = new Engine();
        createGameplayMode(engine);
        var failure = assertThrows(IllegalStateException.class, () -> enter(engine, 100, 200));
        assertEquals("Level editor is disabled by configuration.", failure.getMessage());
        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        assertNull(SessionManager.getCurrentEditorMode());
        assertNotNull(SessionManager.getCurrentGameplayMode());
    }

    @Test
    void shiftTabInGameplay_whenEditorDisabled_doesNotEnterEditor() {
        var engine = new Engine();
        createGameplayMode(engine);
        var input = inputFor(engine);
        press(engine, input, GLFW_KEY_LEFT_SHIFT, GLFW_KEY_TAB);
        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        assertNull(SessionManager.getCurrentEditorMode());
        assertNotNull(SessionManager.getCurrentGameplayMode());
    }

    @Test
    void createGameplayMode_createsRuntimeBeforeConstructingPlayer() {
        var engine = new Engine();
        var mode = assertDoesNotThrow(() -> createGameplayMode(engine));
        assertSame(mode, SessionManager.getCurrentGameplayMode());
        assertNotNull(mode.getSpriteManager().getSprite("sonic"));
        assertNotNull(mode.getLevelManager().getCurrentLevel());
    }

    @Test
    void nativeEditorEntryCreatesIndependentLevelAndDiscardsParkedGameplayStash() {
        enableEditor();
        var engine = new Engine();
        var gameplay = createGameplayMode(engine);
        var source = gameplay.getLevelManager().getCurrentLevel();
        var world = gameplay.getWorldSession();
        var stash = new EditorPlaytestStash(100, 200, 9, -3, true, 47, 1);
        engine.enterEditorFromCurrentPlayer(stash, 100, 200);
        assertEquals(GameMode.EDITOR, engine.getCurrentGameMode());
        var authored = engine.getLevelEditorController().currentLevel();
        assertNotNull(authored);
        assertNull(engine.getEditorProjectWorkspace().document(), "Native entry does not implicitly create a portable project");
        assertNotSame(source, authored);
        assertNotSame(world, SessionManager.getCurrentWorldSession());
        assertSame(authored, SessionManager.getCurrentWorldSession().getCurrentLevel());
        assertNull(SessionManager.getCurrentGameplayMode());
        assertEquals(new EditorCursorState(100, 200), SessionManager.getCurrentEditorMode().getCursor());
        assertNull(SessionManager.getCurrentEditorMode().getPlaytestStash());
        assertFalse(authored.isModifiedSinceLastSave(), "Entering native editing establishes its initial save baseline");
    }

    @Test
    void explicitNativeCloneCreatesIndependentModifiedProjectDocument() {
        enableEditor();
        var engine = new Engine();
        var gameplay = createGameplayMode(engine);
        var source = gameplay.getLevelManager().getCurrentLevel();
        enter(engine, 100, 200);
        var nativeLevel = engine.getLevelEditorController().currentLevel();
        engine.getLevelEditorController().placeBlock(0, 2, 2, 1);
        var nativeWorld = SessionManager.getCurrentWorldSession();
        var input = inputFor(engine);
        engine.getEditorProjectWorkspace().openMenu();
        press(engine, input, GLFW_KEY_DOWN);
        press(engine, input, GLFW_KEY_DOWN);
        press(engine, input, GLFW_KEY_DOWN);
        press(engine, input, GLFW_KEY_ENTER);
        var document = engine.getEditorProjectWorkspace().document();
        assertNotNull(document, engine.getLevelEditorController().statusText());
        assertTrue(document.isModified());
        assertNotSame(source, document.level());
        assertNotSame(nativeLevel, document.level());
        assertNotSame(nativeWorld, SessionManager.getCurrentWorldSession());
        assertSame(document.level(), SessionManager.getCurrentWorldSession().getCurrentLevel());
        assertSame(document.level(), engine.getLevelEditorController().currentLevel());
        assertNull(SessionManager.getCurrentGameplayMode());
        assertEquals(1, document.level().getMap().getValue(0, 2, 2) & 0xFF);
        engine.getLevelEditorController().placeBlock(0, 2, 2, 0);
        assertEquals(1, nativeLevel.getMap().getValue(0, 2, 2) & 0xFF);
        assertEquals(0, source.getMap().getValue(0, 2, 2) & 0xFF);
        engine.getLevelEditorController().undo();
        assertEquals(1, document.level().getMap().getValue(0, 2, 2) & 0xFF);
    }

    @Test
    void resumePlaytestUsesIndependentAuthoredSnapshotAndFreshCounters() throws Exception {
        requireS2Rom();
        enableEditor();
        var engine = new Engine();
        var originalGameplay = createGameplayMode(engine);
        var originalLevel = originalGameplay.getLevelManager().getCurrentLevel();
        originalGameplay.getGameStateManager().addScore(7777);
        enter(engine, 100, 200);
        var authored = engine.getLevelEditorController().currentLevel();
        engine.getLevelEditorController().placeBlock(0, 2, 2, 1);
        engine.getLevelEditorController().setWorldCursor(new EditorCursorState(320, 448));
        engine.resumePlaytestFromEditor();
        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        var runtime = SessionManager.getCurrentGameplayMode();
        assertNotSame(originalGameplay, runtime);
        assertNotSame(originalGameplay.getWorldSession(), runtime.getWorldSession());
        assertNotSame(authored, runtime.getLevelManager().getCurrentLevel());
        assertEquals(1, runtime.getLevelManager().getCurrentLevel().getMap().getValue(0, 2, 2) & 0xFF);
        assertEquals(0, originalLevel.getMap().getValue(0, 2, 2) & 0xFF);
        assertTrue(runtime.getResumeStash().isEmpty());
        assertEquals(0, runtime.getGameStateManager().getScore());
        var player = (Sonic) runtime.getSpriteManager().getSprite("sonic");
        assertEquals(320, player.getCentreX());
        assertEquals(448, player.getCentreY());
        assertEquals(0, player.getRingCount());
        assertSame(player, runtime.getCamera().getFocusedSprite());
        assertFalse(Files.exists(new EditorSaveManager(tempSaves).editPath(
                com.openggf.game.GameId.S2, 0, 0)), "playtesting must not auto-publish a gameplay sidecar");
        var requestExit = Engine.class.getDeclaredMethod("requestApplicationExit");
        requestExit.setAccessible(true);
        requestExit.invoke(engine);
        assertTrue(engine.getEditorProjectWorkspace().menu().isOpen(),
                "Unsaved native edits remain protected while a detached playtest runs");
        press(engine, inputFor(engine), GLFW_KEY_ESCAPE);
        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        assertSame(runtime, SessionManager.getCurrentGameplayMode());
        ((MutableLevel) runtime.getLevelManager().getCurrentLevel()).setBlockInMap(0, 2, 2, 0);
        assertEquals(1, authored.getMap().getValue(0, 2, 2) & 0xFF,
                "Gameplay terrain mutations must not change authored terrain");
        engine.toggleEditorPlaytestMode();
        assertEquals(GameMode.EDITOR, engine.getCurrentGameMode());
        assertNull(engine.getEditorProjectWorkspace().document());
        assertSame(authored, engine.getLevelEditorController().currentLevel());
        engine.startGameplayFromBeginning();
        var fresh = SessionManager.getCurrentGameplayMode();
        assertNotSame(runtime, fresh);
        assertNotSame(authored, fresh.getLevelManager().getCurrentLevel());
        assertEquals(1, fresh.getLevelManager().getCurrentLevel().getMap().getValue(0, 2, 2) & 0xFF);
        assertEquals(nativeStart()[0], player(fresh).getCentreX());
        assertEquals(nativeStart()[1], player(fresh).getCentreY());
        engine.toggleEditorPlaytestMode();
        assertSame(authored, engine.getLevelEditorController().currentLevel());
        assertTrue(authored.isModifiedSinceLastSave(), "Playtesting must not mark native edits saved");
        assertFalse(Files.exists(new EditorSaveManager(tempSaves).editPath(
                com.openggf.game.GameId.S2, 0, 0)));
        engine.getLevelEditorController().undo();
        assertEquals(0, authored.getMap().getValue(0, 2, 2) & 0xFF);
    }

    @Test
    void enteringEditorPreservesPriorTerrainInItsCopyAndRetiresOldRuntime() {
        enableEditor();
        var engine = new Engine();
        var gameplay = createGameplayMode(engine);
        var source = (MutableLevel) gameplay.getLevelManager().getCurrentLevel();
        source.setBlockInMap(0, 0, 0, 1);
        var oldSprites = gameplay.getSpriteManager();
        enter(engine, 100, 200);
        var authored = engine.getLevelEditorController().currentLevel();
        assertEquals(1, authored.getMap().getValue(0, 0, 0) & 0xFF);
        assertNotSame(source, authored);
        authored.setBlockInMap(0, 0, 0, 0);
        assertEquals(1, source.getMap().getValue(0, 0, 0) & 0xFF);
        assertNull(gameplay.getSpriteManager());
        assertDoesNotThrow(oldSprites::drawLowPriority);
    }

    @Test
    void enterEditorRestoresIndependentRenderableViewWithoutGameplayObjects() throws Exception {
        enableEditor();
        var engine = new Engine();
        var gameplay = createGameplayMode(engine);
        var source = gameplay.getLevelManager().getCurrentLevel();
        enter(engine, 100, 128);
        var view = engineLevelManager(engine);
        var authored = engine.getLevelEditorController().currentLevel();
        assertNull(SessionManager.getCurrentGameplayMode());
        assertNotSame(source, authored);
        assertSame(authored, engine.getLevelEditorController().currentLevel());
        assertSame(authored, view.getCurrentLevel());
        assertNotNull(view.getTilemapManager());
        assertNull(view.getObjectManager());
        assertNull(view.getRingManager());
        assertSame(authored.getBlock(0), lookupBlock(view, (byte) 0, 0, 0));
    }

    @Test
    void nativeEntryPreservesCollisionDelegationAndNativeBounds() {
        enableEditor();
        var engine = new Engine();
        var gameplay = createGameplayMode(engine);
        var source = spy(gameplay.getLevelManager().getCurrentLevel());
        doReturn(1500).when(source).getMaxX();
        doReturn(1).when(source).resolveCollisionBlockIndex(0, 2, 2);
        gameplay.getLevelManager().setLevel(source);
        enter(engine, 100, 200);
        assertEquals(GameMode.EDITOR, engine.getCurrentGameMode());
        assertNull(engine.getEditorProjectWorkspace().document());
        var authored = engine.getLevelEditorController().currentLevel();
        assertNotSame(source, authored);
        assertEquals(1500, authored.getMaxX(), "Native bounds are not flattened to the portable map domain");
        assertEquals(1, authored.resolveCollisionBlockIndex(0, 2, 2));
    }

    @Test
    void editorDrawPathFlushesMutableLevelDirtyRegionsBeforeRendering() throws Exception {
        var engine = new Engine();
        var levelManager = mock(LevelManager.class);
        var sprites = mock(SpriteManager.class);
        var sentinel = new RuntimeException("dirty-region flush reached");
        doThrow(sentinel).when(levelManager).processDirtyRegions();
        setPrivateField(engine, "levelManager", levelManager);
        setPrivateField(engine, "spriteManager", sprites);
        engine.getGameLoop().setGameMode(GameMode.EDITOR);
        assertSame(sentinel, assertThrows(RuntimeException.class, engine::draw));
        verify(levelManager).processDirtyRegions();
        verify(levelManager, never()).drawWithSpritePriority(sprites);
    }

    @Test
    void syncEditorStateKeepsSessionCursorAlignedWithControllerCursor() {
        enableEditor();
        var engine = new Engine();
        createGameplayMode(engine);
        enter(engine, 100, 200);
        engine.getLevelEditorController().setWorldCursor(new EditorCursorState(160, 224));
        engine.syncEditorState();
        assertEquals(new EditorCursorState(160, 224), SessionManager.getCurrentEditorMode().getCursor());
    }

    @Test
    void editorInputStepSynchronizesCursorWithoutRendering() {
        enableEditor();
        var engine = new Engine();
        createGameplayMode(engine);
        var input = inputFor(engine);
        enter(engine, 100, 200);
        press(engine, input, GLFW_KEY_RIGHT);
        assertEquals(new EditorCursorState(103, 200), engine.getLevelEditorController().worldCursor());
        assertEquals(new EditorCursorState(103, 200), SessionManager.getCurrentEditorMode().getCursor());
    }

    @Test
    void f5EditorInputInvokesItsInstalledFreshStartHandler() {
        var engine = new Engine();
        createGameplayMode(engine);
        var input = inputFor(engine);
        int[] starts = {0};
        engine.getGameLoop().setEditorFreshStartHandler(() -> starts[0]++);
        engine.getGameLoop().setGameMode(GameMode.EDITOR);
        press(engine, input, GLFW_KEY_F5);
        assertEquals(1, starts[0]);
        assertFalse(input.isKeyPressed(GLFW_KEY_F5));
    }

    @Test
    void f5StartsAtAuthoredLevelStartWithNoParkedStash() throws Exception {
        requireS2Rom();
        enableEditor();
        var engine = new Engine();
        var oldGameplay = createGameplayMode(engine);
        var input = inputFor(engine);
        enter(engine, 100, 200);
        var authored = engine.getLevelEditorController().currentLevel();
        engine.getLevelEditorController().setWorldCursor(new EditorCursorState(320, 448));
        press(engine, input, GLFW_KEY_F5);
        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        var gameplay = SessionManager.getCurrentGameplayMode();
        assertNotSame(oldGameplay, gameplay);
        assertEquals(nativeStart()[0], player(gameplay).getCentreX());
        assertEquals(nativeStart()[1], player(gameplay).getCentreY());
        assertTrue(gameplay.getResumeStash().isEmpty());
        assertNotSame(authored, gameplay.getLevelManager().getCurrentLevel());
    }

    @Test
    void explicitRomFixtureReplacesLeakedGarbageBeforeHostedFreshStart() throws Exception {
        Path garbage = tempSaves.resolve("garbage.gen");
        Files.write(garbage, new byte[512 * 1024]);
        try (var leaked = new Rom()) {
            assertTrue(leaked.open(garbage.toString()));
            RomManager.getInstance().setRom(leaked);
            requireS2Rom();
            enableEditor();
            var engine = new Engine();
            createGameplayMode(engine);
            enter(engine, 100, 200);
            var authored = engine.getLevelEditorController().currentLevel();
            assertDoesNotThrow(engine::startGameplayFromBeginning);
            assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
            assertNotSame(authored, SessionManager.getCurrentGameplayMode().getLevelManager().getCurrentLevel());
            assertEquals(nativeStart()[0], player(SessionManager.getCurrentGameplayMode()).getCentreX());
            assertEquals(nativeStart()[1], player(SessionManager.getCurrentGameplayMode()).getCentreY());
        }
    }

    @Test
    void f5OutsideEditorModeDoesNotInvokeItsFreshStartHandler() {
        var engine = new Engine();
        createGameplayMode(engine);
        var input = inputFor(engine);
        int[] starts = {0};
        engine.getGameLoop().setEditorFreshStartHandler(() -> starts[0]++);
        engine.getGameLoop().pause();
        press(engine, input, GLFW_KEY_F5);
        assertEquals(0, starts[0]);
    }

    @Test
    void keyboardOwnedWorldNavigationCentersCameraWithinAuthoredBounds() {
        enableEditor();
        var engine = new Engine();
        createGameplayMode(engine);
        enter(engine, 100, 200);
        engine.getLevelEditorController().setWorldCursor(new EditorCursorState(512, 384));
        engine.syncEditorState();
        var camera = engineCamera(engine);
        assertEquals(512, camera.getX() + camera.getWidth() / 2);
        assertEquals(384, camera.getY() + camera.getHeight() / 2);
    }

    @Test
    void cameraNavigationClampsAtAuthoredMinimum() {
        enableEditor();
        var engine = new Engine();
        createGameplayMode(engine);
        enter(engine, 100, 200);
        engine.getLevelEditorController().setWorldCursor(new EditorCursorState(64, 64));
        engine.syncEditorState();
        assertEquals(0, engineCamera(engine).getX());
        assertEquals(0, engineCamera(engine).getY());
    }

    @Test
    void unsafeProgrammaticCursorIsRejectedWithoutLosingNativeEdits() throws Exception {
        enableEditor();
        var engine = new Engine();
        createGameplayMode(engine);
        enter(engine, 100, 200);
        var authored = engine.getLevelEditorController().currentLevel();
        engine.getLevelEditorController().placeBlock(0, 2, 2, 1);
        setPrivateField(engine.getLevelEditorController(), "worldCursor", new EditorCursorState(9999, -99));
        engine.resumePlaytestFromEditor();
        assertEquals(GameMode.EDITOR, engine.getCurrentGameMode());
        assertNull(engine.getEditorProjectWorkspace().document());
        assertSame(authored, engine.getLevelEditorController().currentLevel());
        assertTrue(engine.getEditorProjectWorkspace().menu().isOpen());
        assertTrue(engine.getEditorProjectWorkspace().menu().error().contains("bounds"));
        assertNull(SessionManager.getCurrentGameplayMode());
        assertEquals(1, authored.getMap().getValue(0, 2, 2) & 0xFF);
        engine.getLevelEditorController().undo();
        assertEquals(0, authored.getMap().getValue(0, 2, 2) & 0xFF);
        assertFalse(Files.exists(new EditorSaveManager(tempSaves).editPath(
                com.openggf.game.GameId.S2, 0, 0)));
    }

    @Test
    void shiftTabAndKeyboardMovementReturnThroughIndependentCursorPlaytest() throws Exception {
        requireS2Rom();
        enableEditor();
        var engine = new Engine();
        createGameplayMode(engine, (short) 100, (short) 200);
        var input = inputFor(engine);
        press(engine, input, GLFW_KEY_LEFT_SHIFT, GLFW_KEY_TAB);
        assertEquals(GameMode.EDITOR, engine.getCurrentGameMode());
        press(engine, input, GLFW_KEY_RIGHT, GLFW_KEY_DOWN);
        var cursor = engine.getLevelEditorController().worldCursor();
        var authored = engine.getLevelEditorController().currentLevel();
        press(engine, input, GLFW_KEY_LEFT_SHIFT, GLFW_KEY_TAB);
        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        var gameplay = SessionManager.getCurrentGameplayMode();
        assertEquals(cursor.x(), player(gameplay).getCentreX());
        assertEquals(cursor.y(), player(gameplay).getCentreY());
        assertTrue(gameplay.getResumeStash().isEmpty());
        assertNotSame(authored, gameplay.getLevelManager().getCurrentLevel());
        press(engine, input, GLFW_KEY_LEFT_SHIFT, GLFW_KEY_TAB);
        assertEquals(GameMode.EDITOR, engine.getCurrentGameMode());
        assertNull(engine.getEditorProjectWorkspace().document());
        assertSame(authored, engine.getLevelEditorController().currentLevel());
        assertEquals(cursor, engine.getLevelEditorController().worldCursor());
    }

    @Test
    void worldMovementClampsCursorToTheActualNativeLevelBounds() {
        enableEditor();
        var engine = new Engine();
        var gameplay = createGameplayMode(engine);
        var source = spy(gameplay.getLevelManager().getCurrentLevel());
        doReturn(512).when(source).getMaxX();
        doReturn(512).when(source).getMaxY();
        gameplay.getLevelManager().setLevel(source);
        var input = inputFor(engine);
        enter(engine, 100, 200);
        var level = engine.getLevelEditorController().currentLevel();
        int maxX = level.getMap().getWidth() * level.getBlockPixelSize() - 1;
        int maxY = level.getMap().getHeight() * level.getBlockPixelSize() - 1;
        engine.getLevelEditorController().setWorldCursor(new EditorCursorState(maxX, maxY));
        press(engine, input, GLFW_KEY_RIGHT, GLFW_KEY_DOWN);
        assertEquals(new EditorCursorState(maxX, maxY), SessionManager.getCurrentEditorMode().getCursor(),
                "Authoring reaches the real map edges independently of native camera-origin limits");
    }

    @Test
    void absentLoadedLevelFailsVisiblyWithoutCreatingAnIncompleteDocument() {
        enableEditor();
        var engine = new Engine();
        createGameplayMode(engine, (short) 100, (short) 200, false);
        enter(engine, 100, 200);
        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        assertNull(engine.getEditorProjectWorkspace().document());
        assertNotNull(SessionManager.getCurrentGameplayMode());
        assertTrue(engine.getLevelEditorController().statusText().startsWith("Project error:"));
    }

    @Test
    void editorMoveEyedropApplyCursorPlaytestAndFreshStartRemainConnected() throws Exception {
        requireS2Rom();
        enableEditor();
        var engine = new Engine();
        var original = createGameplayMode(engine, (short) 125, (short) 125);
        var source = original.getLevelManager().getCurrentLevel();
        var input = inputFor(engine);
        press(engine, input, GLFW_KEY_LEFT_SHIFT, GLFW_KEY_TAB);
        var authored = engine.getLevelEditorController().currentLevel();
        assertEquals(new EditorCursorState(125, 125), engine.getLevelEditorController().worldCursor());
        press(engine, input, GLFW_KEY_RIGHT, GLFW_KEY_DOWN);
        assertEquals(new EditorCursorState(128, 128), engine.getLevelEditorController().worldCursor());
        press(engine, input, GLFW_KEY_E);
        assertEquals(1, engine.getLevelEditorController().selection().selectedBlock());
        engine.getLevelEditorController().setWorldCursor(new EditorCursorState(253, 253));
        press(engine, input, GLFW_KEY_RIGHT, GLFW_KEY_DOWN);
        press(engine, input, GLFW_KEY_SPACE);
        assertEquals(1, authored.getMap().getValue(0, 2, 2) & 0xFF);
        assertEquals(0, source.getMap().getValue(0, 2, 2) & 0xFF);
        press(engine, input, GLFW_KEY_LEFT_SHIFT, GLFW_KEY_TAB);
        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        assertEquals(256, player(SessionManager.getCurrentGameplayMode()).getCentreX());
        assertEquals(256, player(SessionManager.getCurrentGameplayMode()).getCentreY());
        press(engine, input, GLFW_KEY_LEFT_SHIFT, GLFW_KEY_TAB);
        assertEquals(GameMode.EDITOR, engine.getCurrentGameMode());
        press(engine, input, GLFW_KEY_F5);
        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        assertEquals(nativeStart()[0], player(SessionManager.getCurrentGameplayMode()).getCentreX());
        assertEquals(nativeStart()[1], player(SessionManager.getCurrentGameplayMode()).getCentreY());
    }

    @Test
    void engineSaveKeepsLegacyUnsupportedS3kPersistenceStatusVisible() throws Exception {
        var engine = new Engine();
        var saves = new EditorSaveManager(tempSaves);
        setPrivateField(engine, "editorSaveManager", saves);
        var gameplay = SessionManager.openGameplaySession(new Sonic3kGameModule());
        gameplay.getWorldSession().setCurrentZone(1);
        gameplay.getWorldSession().setCurrentAct(0);
        engine.getLevelEditorController().attachLevel(MutableLevel.snapshot(new SyntheticLevel()));
        var method = Engine.class.getDeclaredMethod("saveCurrentEditorLevel");
        method.setAccessible(true);
        assertEquals(true, method.invoke(engine));
        assertEquals(EditorSaveManager.ApplyResult.UNSUPPORTED, engine.getLevelEditorController().persistenceStatus());
        assertTrue(Files.exists(saves.editPath(gameplay.getWorldSession().getGameModule().getGameId(), 1, 0)));
    }

    @Test
    void legacySidecarLoadingUsesTheInjectedManagerAndRetainsItsS3kGate() throws IOException {
        String manager = Files.readString(Path.of("src/main/java/com/openggf/level/LevelManager.java"));
        String save = Files.readString(Path.of("src/main/java/com/openggf/editor/persistence/EditorSaveManager.java"));
        assertFalse(manager.contains("new EditorSaveManager(Path.of(\"saves\"))"));
        assertTrue(manager.contains("editorSaveManager.tryApplyEdits("));
        assertTrue(save.contains("return gameId != GameId.S3K;"));
    }

    private GameplayModeContext createGameplayMode(Engine engine) {
        return createGameplayMode(engine, (short) 100, (short) 200);
    }

    private GameplayModeContext createGameplayMode(Engine engine, short x, short y) {
        return createGameplayMode(engine, x, y, true);
    }

    private GameplayModeContext createGameplayMode(Engine engine, short x, short y, boolean loaded) {
        try { setPrivateField(engine, "editorSaveManager", new EditorSaveManager(tempSaves)); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        var mode = SessionManager.openGameplaySession(new Sonic2GameModule());
        TestEnvironment.activeGameplayMode();
        var player = new Sonic("sonic", x, y);
        com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(player, x);
        com.openggf.sprites.NativePositionOps.writeYPosResetSubpixel(player, y);
        mode.getSpriteManager().addSprite(player);
        mode.getCamera().setFocusedSprite(player);
        mode.getCamera().setMinX((short) 0);
        mode.getCamera().setMaxX((short) 1023);
        mode.getCamera().setMinY((short) 0);
        mode.getCamera().setMaxY((short) 1023);
        if (loaded) mode.getLevelManager().setLevel(MutableLevel.snapshot(new SyntheticLevel()));
        engine.getGameLoop().setGameplayMode(mode);
        return mode;
    }

    private void requireS2Rom() throws IOException {
        var file = RomTestUtils.ensureSonic2RomAvailable();
        assumeTrue(file != null, "Configured S2 ROM required for native hosted playtest");
        fixtureRom = new Rom();
        assertTrue(fixtureRom.open(file.getAbsolutePath()), "configured S2 ROM must be readable");
        TestEnvironment.configureRomFixture(fixtureRom);
        GraphicsManager.getInstance().initHeadless();
    }

    private static int[] nativeStart() {
        return new Sonic2GameModule().getZoneRegistry().getStartPosition(0, 0);
    }

    private static Sonic player(GameplayModeContext mode) {
        return (Sonic) mode.getSpriteManager().getSprite("sonic");
    }
    private static InputHandler inputFor(Engine engine) {
        var input = new InputHandler(); engine.setInputHandler(input); return input;
    }
    private static void press(Engine engine, InputHandler input, int... keys) {
        for (int key : keys) input.handleKeyEvent(key, GLFW_PRESS);
        engine.getGameLoop().step();
        for (int key : keys) input.handleKeyEvent(key, GLFW_RELEASE);
        input.update();
    }
    private static void enter(Engine engine, int x, int y) {
        engine.enterEditorFromCurrentPlayer(new EditorPlaytestStash(x, y, 9, -3, true, 47, 1), x, y);
    }
    private static Block lookupBlock(LevelManager manager, byte layer, int x, int y) throws Exception {
        var method = LevelManager.class.getDeclaredMethod("getBlockAtPosition", byte.class, int.class, int.class);
        method.setAccessible(true); return (Block) method.invoke(manager, layer, x, y);
    }
    private static LevelManager engineLevelManager(Engine engine) { return (LevelManager) field(engine, "levelManager"); }
    private static Camera engineCamera(Engine engine) { return (Camera) field(engine, "camera"); }
    private static Object field(Object target, String name) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void setPrivateField(Object target, String name, Object value) throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static void enableEditor() {
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.EDITOR_ENABLED, true);
    }

    /** S2's actual 8x8 chunk/block contract and bounds that contain its stock source start. */
    private static final class SyntheticLevel extends AbstractLevel {
        SyntheticLevel() {
            super(0);
            patternCount = 2; patterns = new Pattern[] {new Pattern(), new Pattern()};
            patterns[1].setPixel(0, 0, (byte) 1);
            chunkCount = 2; chunks = new Chunk[] {new Chunk(), new Chunk()};
            chunks[1].setPatternDesc(0, 0, new PatternDesc(1));
            blockCount = 2; blocks = new Block[] {new Block(8), new Block(8)};
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                blocks[1].setChunkDesc(x, y, new ChunkDesc(1));
            }
            solidTileCount = 1;
            solidTiles = new SolidTile[] {new SolidTile(0, new byte[16], new byte[16], (byte) 0)};
            map = new Map(2, 8, 8); map.setValue(0, 1, 1, (byte) 1);
            palettes = new Palette[PALETTE_COUNT];
            for (int i = 0; i < PALETTE_COUNT; i++) palettes[i] = new Palette();
            objects = List.of(); rings = List.of();
            minX = 0; maxX = 1023; minY = 0; maxY = 1023;
        }
        @Override public int getChunksPerBlockSide() { return 8; }
        @Override public int getBlockPixelSize() { return 128; }
    }
}
