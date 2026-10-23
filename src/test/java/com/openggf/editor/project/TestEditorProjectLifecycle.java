package com.openggf.editor.project;

import com.openggf.Engine;
import com.openggf.audio.AudioManager;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.editor.EditorSpawnEditMode;
import com.openggf.editor.LevelEditorController;
import com.openggf.editor.commands.PlaceBlockCommand;
import com.openggf.editor.persistence.EditorSaveManager;
import com.openggf.game.GameId;
import com.openggf.game.GameMode;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.StockGameDataSources;
import com.openggf.game.session.EditorCursorState;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.Sonic1Level;
import com.openggf.game.sonic2.Sonic2Level;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.graphics.GraphicsManager;
import com.openggf.io.DirectoryAccess;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.level.Level;
import com.openggf.game.LevelLoadMode;
import com.openggf.level.MutableLevel;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PlaceholderObjectInstance;
import com.openggf.mods.code.BakedLevelRef;
import com.openggf.mods.code.ModLevelDefinitionParser;
import com.openggf.mods.code.ModZoneLoader;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.glfw.GLFW.*;

/** ROM-backed authored data and the real Engine/session/physics/host boundaries. */
@Isolated
class TestEditorProjectLifecycle {
    @TempDir Path temporary;
    private Fixture fixture;
    private Engine engine;

    @AfterEach
    void cleanup() throws Exception {
        if (engine != null) engine.setInputHandler(null);
        SessionManager.clear();
        if (fixture != null) fixture.close();
        RomManager.getInstance().setRom(null);
        Engine.clearGlobalInstance();
        TestEnvironment.resetAll();
        SonicConfigurationService.getInstance().resetToDefaults();
        GameModuleRegistry.reset();
    }

    @ParameterizedTest(name = "{0}: native entry, isolated playtest and undo use real ROM geometry")
    @EnumSource(value = GameId.class, names = {"S1", "S2", "S3K"})
    void nativeRomEntryPlaytestAndReturnPreserveEditsAndUndo(GameId game) throws Exception {
        start(game);
        var source = GameServices.level().getCurrentLevel();
        int[] start = fixture.module.getZoneRegistry().getStartPosition(0, 0);
        engine.enterEditorFromCurrentPlayer(null, start[0], start[1]);
        assertEquals(GameMode.EDITOR, engine.getCurrentGameMode(), engine.getLevelEditorController().statusText());
        assertNull(engine.getEditorProjectWorkspace().document());
        var controller = engine.getLevelEditorController();
        var authored = controller.currentLevel();
        int x = authored.getMap().getWidth() - 1, y = authored.getMap().getHeight() - 1;
        int before = Byte.toUnsignedInt(authored.getMap().getValue(0, x, y));
        int edited = before == 0 ? 1 : 0;
        controller.placeBlock(0, x, y, edited);
        engine.startGameplayFromBeginning();
        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode(), controller.statusText());
        var runtime = GameServices.level().getCurrentLevel();
        assertNotSame(source, runtime);
        assertNotSame(authored, runtime);
        assertEquals(edited, Byte.toUnsignedInt(runtime.getMap().getValue(0, x, y)));
        runtime.getMap().setValue(0, x, y, (byte) before);
        assertEquals(edited, Byte.toUnsignedInt(authored.getMap().getValue(0, x, y)));
        engine.toggleEditorPlaytestMode();
        assertEquals(GameMode.EDITOR, engine.getCurrentGameMode(), controller.statusText());
        assertSame(authored, controller.currentLevel());
        controller.undo();
        assertEquals(before, Byte.toUnsignedInt(authored.getMap().getValue(0, x, y)));
        assertFalse(java.nio.file.Files.exists(new EditorSaveManager(temporary.resolve("legacy-sidecars"))
                .editPath(game, 0, 0)));
    }

    @ParameterizedTest(name = "{0}: create, author, save, reopen, clone and export to its native host")
    @EnumSource(value = GameId.class, names = {"S1", "S2", "S3K"})
    void romLibraryProjectRoundTripsAndExportLoadsInSelectedHost(GameId game) throws Exception {
        start(game);
        var workspace = engine.getEditorProjectWorkspace();
        createBlank(workspace, fixture.input, game, "ROM-backed " + game.code());
        var document = workspace.document();
        assertEquals(GameMode.EDITOR, engine.getCurrentGameMode());
        assertEquals(game, document.metadata().gameId());
        assertTrue(document.level().getPatternCount() >= fixture.library.getPatternCount(),
                "authoring capacity retains every native pattern");
        assertTrue(document.level().getPatternCount() <= 2048, "import slots stay within native terrain capacity");
        for (int i = fixture.library.getPatternCount(); i < document.level().getPatternCount(); i++)
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
                assertEquals(0, document.level().getPattern(i).getPixel(x, y), "new import slots start transparent");
        assertEquals(fixture.library.getChunksPerBlockSide(), document.level().getChunksPerBlockSide());
        assertTrue(document.level().getObjects().isEmpty());
        assertTrue(document.level().getRings().isEmpty());
        assertPatternsEqual(fixture.library, document.level());

        authorCourse(document, engine.getLevelEditorController(), game);
        assertLastCanvasCellCanBeEdited(document, engine.getLevelEditorController());
        var authored = document.snapshot();
        Path project = temporary.resolve(game.code() + ".ogfproject");
        saveAs(workspace, project);
        assertFalse(document.isModified());
        assertEquals(project.toAbsolutePath(), document.path());

        assertPortableAssetCounts(game, document.level());
        workspace.export();
        Path exported = temporary.resolve(game.code() + "-export");
        workspace.menu().setValue("path", exported.toString());
        workspace.menu().submit();
        assertNull(workspace.menu().error(), "export should validate the selected native host");
        assertFalse(workspace.menu().isOpen());
        try (var assets = ModAssetRoot.directory(temporary, exported,
                ModInputLimits.production(), DirectoryAccess.DEVELOPMENT)) {
            var parsed = ModLevelDefinitionParser.read(assets, new BakedLevelRef("level.json"));
            var hostData = ModZoneLoader.prepareHostData(parsed);
            fixture.module.getModZoneAdapter().validate("openggf-editor", hostData);
            Level loaded = fixture.module.getModZoneAdapter().load("openggf-editor", hostData);
            assertNativeHost(game, loaded);
            assertEquals(document.level().getBlockPixelSize(), loaded.getBlockPixelSize());
            assertEquals(document.level().getMap().getValue(0, 2, 1), loaded.getMap().getValue(0, 2, 1));
            assertEquals(document.level().getRings().size(), loaded.getRings().size());
            assertEquals(1, loaded.getObjects().stream().filter(o -> isGoal(game, o)).count());
            assertEquals(document.metadata().startX(), parsed.start().x());
            assertEquals(document.metadata().startY(), parsed.start().y());
            assertTrue(loaded.getObjects().stream().anyMatch(o -> o.objectId() == monitorId(game)));
            assertConcreteObjects(fixture.module, loaded, game);
        }

        workspace.openMenu();
        choose(workspace, fixture.input, 1);
        workspace.menu().setValue("path", project.toString());
        workspace.menu().submit();
        assertNull(workspace.menu().error());
        var reopened = workspace.document();
        assertNotSame(document, reopened);
        assertFalse(reopened.isModified());
        assertTrue(authored.contentEquals(reopened.level()), "every authored asset and placement must reopen");
        assertEquals(document.metadata(), reopened.metadata());
        var reopenedBeforeUndo = reopened.snapshot();
        engine.getLevelEditorController().undo();
        assertTrue(reopenedBeforeUndo.contentEquals(reopened.level()), "a new document owns a new history");

        workspace.openMenu();
        choose(workspace, fixture.input, 5);
        workspace.menu().setValue("name", "Supported course copy");
        workspace.menu().submit();
        var clone = workspace.document();
        assertNotSame(reopened, clone);
        assertNull(clone.path());
        assertTrue(clone.isModified());
        assertTrue(authored.contentEquals(clone.level()));
        clone.level().setBlockInMap(0, 0, 1, clone.level().getMap().getValue(0, 0, 0) & 0xFF);
        assertTrue(authored.contentEquals(reopened.level()), "cloning cannot share mutable authored arrays");
        assertPatternsEqual(fixture.library, reopened.level());
    }

    @ParameterizedTest(name = "{0}: real physics, fresh starts and native completion return to the same document")
    @EnumSource(value = GameId.class, names = {"S1", "S2", "S3K"})
    void playtestOwnsIndependentRuntimeAndNativeGoalReturnsToEditor(GameId game) throws Exception {
        start(game);
        var workspace = engine.getEditorProjectWorkspace();
        createBlank(workspace, fixture.input, game, "Playable " + game.code());
        var document = workspace.document();
        var controller = engine.getLevelEditorController();
        int floor = authorCourse(document, controller, game);
        saveAs(workspace, temporary.resolve("playable.ogfproject"));
        var authored = document.snapshot();
        var cursor = new EditorCursorState(128, 64);
        controller.setWorldCursor(cursor);

        var startCommand = workspace.commands().stream()
                .filter(command -> command.label().equals("Playtest from level start")).findFirst().orElseThrow();
        workspace.menu().showActions("Playtest", List.of(new EditorProjectMenu.Action(startCommand.label(), startCommand.action())));
        pressEngine(GLFW_KEY_ENTER);
        assertFalse(workspace.menu().isOpen());
        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode(), "the modal closing frame cannot cancel its playtest action");
        var runtime = GameServices.level().getCurrentLevel();
        assertNotSame(document.level(), runtime);
        assertArrayEquals(document.level().getMap().getData(), runtime.getMap().getData());
        assertEquals(document.level().getChunksPerBlockSide(), runtime.getChunksPerBlockSide());
        int floorBlock = runtime.getMap().getValue(0, 0, 1) & 0xFF;
        assertArrayEquals(document.level().getBlock(floorBlock).saveState(), runtime.getBlock(floorBlock).saveState());
        int floorChunk = runtime.getBlock(floorBlock).getChunkDesc(0, 0).getChunkIndex();
        assertArrayEquals(document.level().getChunk(floorChunk).saveState(), runtime.getChunk(floorChunk).saveState());
        assertEquals(document.level().getRings().size(), runtime.getRings().size());
        assertEquals(EditorProjectRuntimeModule.LEVEL_INDEX,
                GameServices.module().getZoneRegistry().getLevelDataForZone(0).getFirst().levelIndex());
        assertEquals(document.metadata().startX(), player().getCentreX());
        assertEquals(document.metadata().startY(), player().getCentreY());
        assertEquals(0, player().getRingCount());
        for (int i = 0; i < 120; i++) engine.getGameLoop().step();
        assertFalse(player().getAir(), "ROM-derived flat collision must support the actual playable sprite");
        assertTrue(Math.abs(player().getCentreY() + player().getYRadius() - floor) <= 3,
                () -> "the real floor sensors must land at authored terrain floor=" + floor + "; " + runtimeFailureState());
        assertTrue(player().getRingCount() > 0, "an authored ring must be collected through the native manager");
        assertConcreteObjects(GameServices.module(), runtime, game);
        player().setRingCount(47);
        if (runtime instanceof MutableLevel mutable) {
            mutable.setBlockInMapForRuntimeMutation(0, 6, 0, runtime.getMap().getValue(0, 0, 1) & 0xFF);
        } else {
            runtime.getMap().setValue(0, 6, 0, runtime.getMap().getValue(0, 0, 1));
        }
        assertTrue(authored.contentEquals(document.level()), "gameplay mutation cannot enter authored data");

        pressEngine(GLFW_KEY_LEFT_SHIFT, GLFW_KEY_TAB);
        assertEquals(GameMode.EDITOR, engine.getCurrentGameMode());
        assertSame(document, workspace.document());
        assertSame(document.level(), controller.currentLevel());
        assertEquals(cursor, controller.worldCursor());
        assertFalse(document.isModified());
        controller.undo();
        assertEquals(0, document.level().getRings().size(), "return must preserve the pre-playtest undo stack");
        workspace.playtest(true);
        pressEngine(GLFW_KEY_LEFT_SHIFT, GLFW_KEY_TAB);
        controller.redo();
        assertTrue(authored.contentEquals(document.level()));
        controller.setSpawnEditMode(EditorSpawnEditMode.RINGS);
        controller.setWorldCursor(new EditorCursorState(100, floor - 20));
        controller.placeRingSpawnAtCursor();
        assertPlacementIdsUnique(document.level());
        assertEquals(2, document.level().getRings().size(), "redo then add must allocate another ring identity");
        controller.undo();
        assertTrue(authored.contentEquals(document.level()));
        controller.undo();
        assertEquals(0, document.level().getRings().size());
        controller.undo();
        assertEquals(0, document.level().getObjects().stream().filter(o -> isGoal(game, o)).count());
        workspace.playtest(true);
        pressEngine(GLFW_KEY_LEFT_SHIFT, GLFW_KEY_TAB);
        controller.redo();
        controller.redo();
        controller.setSpawnEditMode(EditorSpawnEditMode.OBJECTS);
        controller.objectPalette().setObjectId(monitorId(game));
        controller.setWorldCursor(new EditorCursorState(300, floor - 64));
        controller.placeObjectSpawnAtCursor();
        assertPlacementIdsUnique(document.level());
        controller.undo();
        controller.setSpawnEditMode(EditorSpawnEditMode.TILES);
        controller.setWorldCursor(cursor);
        assertTrue(authored.contentEquals(document.level()));
        assertFalse(document.isModified());
        assertFalse(controller.isModified());

        // Explicit project access remains available even when legacy editor activation is disabled.
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.EDITOR_ENABLED, false);
        workspace.playtest(true);
        assertEquals(cursor.x(), player().getCentreX());
        assertEquals(cursor.y(), player().getCentreY());
        assertEquals(0, player().getRingCount(), "a cursor run is a fresh runtime, not parked gameplay state");
        pressEngine(GLFW_KEY_LEFT_SHIFT, GLFW_KEY_TAB);
        assertEquals(GameMode.EDITOR, engine.getCurrentGameMode());
        assertSame(document, workspace.document());

        workspace.playtest(false);
        assertEquals(document.metadata().startX(), player().getCentreX());
        assertEquals(0, player().getRingCount());
        fixture.input.handleKeyEvent(GLFW_KEY_RIGHT, GLFW_PRESS);
        int frames = 0;
        while (engine.getCurrentGameMode() != GameMode.EDITOR && frames++ < 2400) {
            engine.getGameLoop().step();
        }
        fixture.input.handleKeyEvent(GLFW_KEY_RIGHT, GLFW_RELEASE);
        fixture.input.update();
        assertEquals(GameMode.EDITOR, engine.getCurrentGameMode(),
                () -> "crossing the authored goal must finish native results and return to the editor: " + runtimeFailureState());
        assertSame(document, workspace.document());
        assertSame(document.level(), controller.currentLevel());
        assertTrue(authored.contentEquals(document.level()));
        assertFalse(document.isModified());
        assertTrue(controller.statusText().contains("Playtest finished"));
    }

    @ParameterizedTest(name = "{0}: project modal owns Shift+Tab and F5, including text editing")
    @EnumSource(value = GameId.class, names = {"S1", "S2", "S3K"})
    void projectModalSuppressesPlaytestShortcuts(GameId game) throws Exception {
        start(game);
        var workspace = engine.getEditorProjectWorkspace();
        createBlank(workspace, fixture.input, game, "Modal " + game.code());
        var document = workspace.document();
        workspace.openMenu();
        pressEngine(GLFW_KEY_F5);
        pressEngine(GLFW_KEY_LEFT_SHIFT, GLFW_KEY_TAB);
        assertEquals(GameMode.EDITOR, engine.getCurrentGameMode());
        assertSame(document, workspace.document());
        assertTrue(workspace.menu().isOpen());
        assertNull(SessionManager.getCurrentGameplayMode());
        workspace.properties();
        pressEngine(GLFW_KEY_ENTER);
        assertTrue(workspace.menu().capturesText());
        pressEngine(GLFW_KEY_F5);
        pressEngine(GLFW_KEY_LEFT_SHIFT, GLFW_KEY_TAB);
        assertEquals(GameMode.EDITOR, engine.getCurrentGameMode());
        assertSame(document, workspace.document());
        assertTrue(workspace.menu().capturesText());
        assertNull(SessionManager.getCurrentGameplayMode());
    }

    @Test
    void s3kObjectFamilyPropertiesRefreshLabelsAndUndoToTheDocumentSavepoint() throws Exception {
        start(GameId.S3K);
        var workspace = engine.getEditorProjectWorkspace();
        createBlank(workspace, fixture.input, GameId.S3K, "Family labels");
        var document = workspace.document();
        var controller = engine.getLevelEditorController();
        saveAs(workspace, temporary.resolve("family.ogfproject"));
        controller.objectPalette().setObjectId(3);
        assertTrue(controller.objectPalette().selectedLabel().contains("AIZ Hollow Tree"));
        assertFalse(controller.isModified());
        var level = controller.currentLevel();

        workspace.properties();
        workspace.menu().setValue("family", "SKL");
        workspace.menu().submit();

        assertNull(workspace.menu().error());
        assertSame(document, workspace.document());
        assertSame(level, controller.currentLevel());
        assertEquals(com.openggf.game.modzone.ModObjectZoneSet.SKL, document.metadata().objectZoneSet());
        assertEquals(3, controller.objectPalette().selectedObjectId());
        assertTrue(controller.objectPalette().selectedLabel().contains("MHZ Twisted Vine"));
        assertTrue(controller.isModified(), "family-only metadata changes must mark the complete document dirty");
        controller.undo();
        assertEquals(com.openggf.game.modzone.ModObjectZoneSet.S3KL, document.metadata().objectZoneSet());
        assertTrue(controller.objectPalette().selectedLabel().contains("AIZ Hollow Tree"));
        assertFalse(controller.isModified());
        controller.redo();
        assertEquals(com.openggf.game.modzone.ModObjectZoneSet.SKL, document.metadata().objectZoneSet());
        assertTrue(controller.objectPalette().selectedLabel().contains("MHZ Twisted Vine"));
    }

    @Test
    void focusLossCommitsTheCurrentStrokeWithoutWaitingForAnotherInputFrame() throws Exception {
        start(GameId.S2);
        var workspace = engine.getEditorProjectWorkspace();
        createBlank(workspace, fixture.input, GameId.S2, "Focus boundary");
        var document = workspace.document();
        var controller = engine.getLevelEditorController();
        authorCourse(document, controller, GameId.S2);
        saveAs(workspace, temporary.resolve("focus.ogfproject"));
        int old = document.level().getMap().getValue(0, 1, 0) & 0xFF;
        int brush = document.level().getMap().getValue(0, 0, 1) & 0xFF;
        assertNotEquals(old, brush);
        controller.selectBlock(brush);
        GraphicsManager.getInstance().setViewport(0, 0, 320, 224);
        fixture.input.handleMouseMove(200, 80);
        fixture.input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
        engine.getGameLoop().step();
        assertEquals(old, document.level().getMap().getValue(0, 1, 0) & 0xFF,
                "the held mouse stroke is buffered before its transaction boundary");
        assertFalse(document.isModified());

        engine.getGameLoop().pause();

        assertEquals(brush, document.level().getMap().getValue(0, 1, 0) & 0xFF,
                "focus loss must commit even when no subsequent frame or mouse release arrives");
        assertTrue(document.isModified());
        controller.undo();
        assertEquals(old, document.level().getMap().getValue(0, 1, 0) & 0xFF);
        assertFalse(document.isModified());
        controller.redo();
        assertEquals(brush, document.level().getMap().getValue(0, 1, 0) & 0xFF);
        fixture.input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE);
        fixture.input.update();
    }

    @Test
    void cancellingNativeExitModalRestoresTheSameHeadlessPlaytestAndEditorView() throws Exception {
        start(GameId.S2);
        var workspace = engine.getEditorProjectWorkspace();
        createBlank(workspace, fixture.input, GameId.S2, "Quit protection");
        var document = workspace.document();
        authorCourse(document, engine.getLevelEditorController(), GameId.S2);
        workspace.playtest(false);
        var gameplay = SessionManager.getCurrentGameplayMode();
        var world = gameplay.getWorldSession();
        var runtimeLevel = gameplay.getLevelManager().getCurrentLevel();
        int x = player().getCentreX(), y = player().getCentreY();
        var camera = gameplay.getCamera();
        camera.setX((short) 120);
        camera.setY((short) 40);
        camera.setXCopy((short) 112);
        camera.setYCopy((short) 32);
        var fadeCallbacks = new java.util.concurrent.atomic.AtomicInteger();
        var fade = gameplay.getFadeManager();
        fade.startFadeToBlack(fadeCallbacks::incrementAndGet);
        var heldFade = fade.capture();
        var requestExit = Engine.class.getDeclaredMethod("requestApplicationExit");
        requestExit.setAccessible(true);

        requestExit.invoke(engine);

        assertTrue(workspace.menu().isOpen());
        assertEquals(GameMode.MASTER_TITLE_SCREEN, engine.getCurrentGameMode());
        assertSame(world, SessionManager.getCurrentWorldSession());
        assertSame(gameplay, SessionManager.getCurrentGameplayMode());
        // Title/menu screen-space setup may reset the suspended camera; cancellation must restore its snapshot.
        camera.setX((short) 0);
        camera.setY((short) 0);
        for (int frame = 0; frame < 40; frame++) engine.getGameLoop().step();
        assertSame(world, SessionManager.getCurrentWorldSession());
        assertSame(runtimeLevel, GameServices.level().getCurrentLevel());
        assertEquals(0, fadeCallbacks.get(), "the quit modal must suspend callbacks behind the native PLC/fade wrapper");
        assertEquals(heldFade, fade.capture(), "the pending native fade must not advance behind the quit modal");
        assertEquals(x, player().getCentreX());
        assertEquals(y, player().getCentreY());
        pressEngine(GLFW_KEY_ESCAPE);
        assertFalse(workspace.menu().isOpen());
        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        assertSame(world, SessionManager.getCurrentWorldSession());
        assertSame(gameplay, SessionManager.getCurrentGameplayMode());
        assertSame(runtimeLevel, GameServices.level().getCurrentLevel());
        assertSame(camera, gameplay.getCamera());
        assertEquals(120, camera.getX());
        assertEquals(40, camera.getY());
        assertEquals(112, camera.getXCopy());
        assertEquals(32, camera.getYCopy());
        assertEquals(0, fadeCallbacks.get());
        assertEquals(heldFade, fade.capture());
        assertEquals(x, player().getCentreX());
        assertEquals(y, player().getCentreY());
        assertSame(document, workspace.document());
        assertTrue(document.isModified());
        for (int frame = 0; frame < 40; frame++) engine.getGameLoop().step();
        assertEquals(1, fadeCallbacks.get(), "resuming the native run must complete its retained callback exactly once");
        for (int frame = 0; frame < 3; frame++) engine.getGameLoop().step();
        assertEquals(1, fadeCallbacks.get());

        pressEngine(GLFW_KEY_LEFT_SHIFT, GLFW_KEY_TAB);
        var editorWorld = SessionManager.getCurrentWorldSession();
        var cursor = engine.getLevelEditorController().worldCursor();
        requestExit.invoke(engine);
        assertTrue(workspace.menu().isOpen());
        pressEngine(GLFW_KEY_ESCAPE);
        assertEquals(GameMode.EDITOR, engine.getCurrentGameMode());
        assertSame(editorWorld, SessionManager.getCurrentWorldSession());
        assertSame(document, workspace.document());
        assertSame(document.level(), engine.getLevelEditorController().currentLevel());
        assertEquals(cursor, engine.getLevelEditorController().worldCursor());
        engine.getLevelEditorController().undo();
        assertEquals(0, document.level().getRings().size(), "cancelled native exit cannot discard editor history");
    }

    @Test
    void importModalOwnsEditorShortcutsAndCannotHideUnsavedQuit() throws Exception {
        start(GameId.S2);
        var workspace = engine.getEditorProjectWorkspace();
        createBlank(workspace, fixture.input, GameId.S2, "Import quit protection");
        var original = workspace.document();
        var importerField = Engine.class.getDeclaredField("contentImportScreen");
        importerField.setAccessible(true);
        var importer = (com.openggf.ContentImportScreen) importerField.get(engine);
        importer.open();
        var globalClaim = Engine.class.getDeclaredMethod("globalShortcutInputClaimed");
        globalClaim.setAccessible(true);
        assertEquals(true, globalClaim.invoke(engine), "Import owns configurable shortcuts before GameLoop dispatch");
        pressEngine(GLFW_KEY_F5);
        pressEngine(GLFW_KEY_LEFT_SHIFT, GLFW_KEY_TAB);
        assertEquals(GameMode.EDITOR, engine.getCurrentGameMode());
        assertSame(original, workspace.document());
        assertTrue(importer.isOpen());

        var requestExit = Engine.class.getDeclaredMethod("requestApplicationExit");
        requestExit.setAccessible(true);
        requestExit.invoke(engine);
        assertFalse(importer.isOpen(), "The importer must relinquish both drawing and input to the quit prompt");
        assertEquals("Unsaved project", workspace.menu().title());
        assertEquals(GameMode.MASTER_TITLE_SCREEN, engine.getCurrentGameMode());
        pressEngine(GLFW_KEY_ESCAPE);
        assertEquals(GameMode.EDITOR, engine.getCurrentGameMode());
        assertSame(original, workspace.document());
        assertTrue(original.isModified());
        assertFalse(workspace.menu().isOpen());
    }

    @Test
    void cancellingARejectedCrossGameImportRestoresTheSavedEditorDocument() throws Exception {
        start(GameId.S2);
        File sonic1 = RomTestUtils.ensureSonic1RomAvailable();
        assumeTrue(sonic1 != null, "Cross-game import restoration also needs the S1 ROM");
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.SONIC_1_ROM, sonic1.getAbsolutePath());
        var workspace = engine.getEditorProjectWorkspace();
        createBlank(workspace, fixture.input, GameId.S2, "Import restoration");
        var original = workspace.document();
        authorCourse(original, engine.getLevelEditorController(), GameId.S2);
        saveAs(workspace, temporary.resolve("retained.ogfproject"));
        workspace.export();
        Path exported = temporary.resolve("retained-export");
        workspace.menu().setValue("path", exported.toString()); workspace.menu().submit();
        assertNull(workspace.menu().error());

        var importerField = Engine.class.getDeclaredField("contentImportScreen");
        importerField.setAccessible(true);
        var importer = (com.openggf.ContentImportScreen) importerField.get(engine);
        importer.open();
        try (var inspected = new com.openggf.ContentImportService(temporary.resolve("mods")).inspect(exported)) {
            // Deliberately select an incompatible native host to exercise failure after the host switch.
            assertThrows(com.openggf.game.modzone.ModZoneRegistrationException.class,
                    () -> workspace.importLevel(inspected.level(), GameId.S1));
        }
        assertSame(original, workspace.document());
        importer.close();
        assertEquals(GameMode.EDITOR, engine.getCurrentGameMode());
        assertSame(original, workspace.document());
        assertSame(original.level(), engine.getLevelEditorController().currentLevel());
        assertFalse(original.isModified());
        assertEquals(GameId.S2, GameServices.module().getGameId());
    }

    private void start(GameId game) throws Exception {
        fixture = Fixture.open(game);
        engine = new Engine();
        Field saves = Engine.class.getDeclaredField("editorSaveManager");
        saves.setAccessible(true);
        saves.set(engine, new EditorSaveManager(temporary.resolve("legacy-sidecars")));
        engine.setInputHandler(fixture.input);
        engine.getGameLoop().setGameMode(GameMode.LEVEL);
    }

    private AbstractPlayableSprite player() {
        return (AbstractPlayableSprite) GameServices.sprites().getSprite("sonic");
    }

    private String runtimeFailureState() {
        var p = player();
        var camera = GameServices.camera();
        var state = GameServices.gameState();
        var objects = GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(object -> object.getClass().getSimpleName().matches(".*(Signpost|Results|Goal).*"))
                .limit(12)
                .map(object -> object.getClass().getSimpleName() + (object.getSpawn() == null ? "@dynamic"
                        : "@" + object.getSpawn().x() + "," + object.getSpawn().y())
                        + debugObjectField(object, "routineState") + debugObjectField(object, "resultsSpawned")
                        + debugObjectField(object, "triggered"))
                .collect(java.util.stream.Collectors.joining("; "));
        return "player=" + p.getCentreX() + "," + p.getCentreY() + " air=" + p.getAir()
                + " radiusY=" + p.getYRadius() + " gSpeed=" + p.getGSpeed() + " ySpeed=" + p.getYSpeed()
                + " locked=" + p.isControlLocked() + " objectControlled=" + p.isObjectControlled()
                + " routine=" + p.getObjectRoutineOverride() + " ground=" + p.getGroundMode()
                + " frame=" + GameServices.level().getFrameCounter()
                + " freshBoundary=" + GameServices.level().hasPendingFreshLevelTransitionBoundary()
                + " titleRequested=" + GameServices.level().isTitleCardRequested()
                + debugObjectField(GameServices.level().getObjectManager(), "updatingPlayer")
                + " camera=" + camera.getX() + "," + camera.getY() + " min/max=" + camera.getMinX() + "/" + camera.getMaxX()
                + " endActive=" + state.isEndOfLevelActive() + " endFlag=" + state.isEndOfLevelFlag()
                + " completion=" + state.isActCompletionSignalActive() + " objects=[" + objects + "]";
    }

    private static String debugObjectField(Object object, String name) {
        try {
            var field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return " " + name + "=" + field.get(object);
        } catch (ReflectiveOperationException ignored) { return ""; }
    }

    private void pressEngine(int... keys) {
        for (int key : keys) fixture.input.handleKeyEvent(key, GLFW_PRESS);
        engine.getGameLoop().step();
        for (int key : keys) fixture.input.handleKeyEvent(key, GLFW_RELEASE);
        fixture.input.update();
    }

    static void createBlank(EditorProjectWorkspace workspace, InputHandler input, GameId game, String name) {
        workspace.openMenu();
        choose(workspace, input, 0);
        choose(workspace, input, game == GameId.S1 ? 0 : game == GameId.S2 ? 1 : 2);
        choose(workspace, input, 0);
        choose(workspace, input, 0);
        assertEquals("New blank level", workspace.menu().title());
        workspace.menu().setValue("name", name);
        workspace.menu().setValue("width", "8");
        workspace.menu().setValue("height", "4");
        workspace.menu().submit();
        assertNull(workspace.menu().error());
        assertNotNull(workspace.document());
    }

    static void choose(EditorProjectWorkspace workspace, InputHandler input, int row) {
        for (int i = 0; i < row; i++) pressMenu(workspace, input, GLFW_KEY_DOWN);
        pressMenu(workspace, input, GLFW_KEY_ENTER);
    }

    private static void pressMenu(EditorProjectWorkspace workspace, InputHandler input, int key) {
        input.handleKeyEvent(key, GLFW_PRESS);
        input.refreshLogicalSnapshot();
        assertTrue(workspace.update(input));
        input.update();
        input.handleKeyEvent(key, GLFW_RELEASE);
        input.update();
    }

    static void saveAs(EditorProjectWorkspace workspace, Path file) {
        workspace.saveAs();
        workspace.menu().setValue("path", file.toString());
        workspace.menu().submit();
        assertNull(workspace.menu().error());
        assertFalse(workspace.menu().isOpen());
    }

    /** Recompose decoded assets; never fabricate physics or inject a collision result. */
    static int authorCourse(EditorProjectDocument document, LevelEditorController controller, GameId game) {
        MutableLevel level = document.level();
        int solid = -1;
        for (int i = 1; i < level.getSolidTileCount(); i++) {
            var tile = level.getSolidTile(i);
            boolean flat = tile.getAngle() == 0 || tile.getAngle() == (byte) 0xFF;
            for (byte height : tile.heights) flat &= Math.abs((int) height) == 16;
            if (flat) { solid = i; break; }
        }
        assertTrue(solid >= 0, "the configured ROM library must supply a flat solid tile");
        int pattern = -1;
        for (int i = 0; i < Math.min(2048, level.getPatternCount()) && pattern < 0; i++) {
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                if (level.getPattern(i).getPixel(x, y) != 0) pattern = i;
            }
        }
        assertTrue(pattern >= 0, "decoded ROM art must contain a visible pattern");
        int emptyBlock = level.getMap().getValue(0, 0, 0) & 0xFF;
        int block = level.getBlockCount() - 1;
        if (block == emptyBlock) block--;
        assertTrue(block >= 0, "the blank library must have an unused composition slot");
        var usedByBlank = new java.util.HashSet<Integer>();
        for (int y = 0; y < level.getChunksPerBlockSide(); y++) for (int x = 0; x < level.getChunksPerBlockSide(); x++) {
            usedByBlank.add(level.getBlock(emptyBlock).getChunkDesc(x, y).getChunkIndex());
        }
        int chunk = level.getChunkCount() - 1;
        while (chunk >= 0 && usedByBlank.contains(chunk)) chunk--;
        assertTrue(chunk >= 0, "course edits must not overwrite the blank canvas definition");
        int[] tile = {pattern | 0x4000, pattern | 0x4000, pattern | 0x4000, pattern | 0x4000, solid, solid};
        level.restoreChunkState(chunk, tile);
        int[] composition = new int[level.getChunksPerBlockSide() * level.getChunksPerBlockSide()];
        Arrays.fill(composition, chunk | 0xF000);
        level.restoreBlockState(block, composition);
        for (int x = 0; x < level.getMap().getWidth(); x++) {
            controller.executeCommand(new PlaceBlockCommand(level, 0, x, 1,
                    level.getMap().getValue(0, x, 1) & 0xFF, block));
        }
        int floor = level.getBlockPixelSize();
        controller.setSpawnEditMode(EditorSpawnEditMode.OBJECTS);
        controller.objectPalette().setObjectId(monitorId(game));
        controller.objectPalette().setSubtype(0);
        controller.setWorldCursor(new EditorCursorState(180, floor - 64));
        controller.placeObjectSpawnAtCursor();
        if (game == GameId.S3K) controller.objectPalette().setObjectKey("openggf:authored-goal");
        else controller.objectPalette().setObjectId(0x0D);
        int goalX = document.metadata().maxX() + 216;
        assertTrue(goalX < level.getMap().getWidth() * level.getBlockPixelSize(),
                "the native goal needs terrain beyond the camera boundary for its walk-off");
        controller.setWorldCursor(new EditorCursorState(goalX, floor - 32));
        controller.placeObjectSpawnAtCursor();
        assertEquals(goalX, controller.selectedObjectSpawn().x());
        controller.setSpawnEditMode(EditorSpawnEditMode.RINGS);
        controller.setWorldCursor(new EditorCursorState(64, floor - 20));
        controller.placeRingSpawnAtCursor();
        controller.setSpawnEditMode(EditorSpawnEditMode.TILES);
        controller.setWorldCursor(new EditorCursorState(document.metadata().startX(), document.metadata().startY()));
        assertEquals(1, level.getRings().size());
        assertEquals(1, level.getObjects().stream().filter(o -> isGoal(game, o)).count());
        return floor;
    }

    static int monitorId(GameId game) { return game == GameId.S3K ? 1 : 0x26; }
    static boolean isGoal(GameId game, ObjectSpawn spawn) {
        return game == GameId.S3K ? "openggf:authored-goal".equals(spawn.objectKey()) : spawn.objectId() == 0x0D;
    }

    private static void assertLastCanvasCellCanBeEdited(EditorProjectDocument document, LevelEditorController controller) {
        var level = document.level();
        int lastX = level.getMap().getWidth() - 1, lastY = level.getMap().getHeight() - 1;
        var last = new EditorCursorState(lastX * level.getBlockPixelSize() + level.getBlockPixelSize() / 2,
                lastY * level.getBlockPixelSize() + level.getBlockPixelSize() / 2);
        assertTrue(last.x() > document.metadata().maxX());
        assertTrue(last.y() > document.metadata().maxY());
        controller.setWorldCursor(last);
        assertEquals(last, controller.worldCursor(), "native camera bounds must not hide the final editable canvas cells");
        int old = level.getMap().getValue(0, lastX, lastY) & 0xFF;
        int brush = level.getMap().getValue(0, 0, 1) & 0xFF;
        assertNotEquals(old, brush);
        controller.selectBlock(brush);
        controller.focusRegion(com.openggf.editor.EditorFocusRegion.WORLD_CANVAS);
        controller.applyPrimaryAction();
        assertEquals(brush, level.getMap().getValue(0, lastX, lastY) & 0xFF);
        controller.undo();
        assertEquals(old, level.getMap().getValue(0, lastX, lastY) & 0xFF);
        controller.setWorldCursor(new EditorCursorState(document.metadata().startX(), document.metadata().startY()));
    }

    private static void assertPlacementIdsUnique(Level level) {
        assertEquals(level.getObjects().size(), level.getObjects().stream().map(ObjectSpawn::layoutIndex).distinct().count(),
                "objects, including S1 ring backing objects, need distinct stable identities");
        assertEquals(level.getRings().size(), level.getRings().stream().map(com.openggf.level.rings.RingSpawn::placementId).distinct().count());
        if (level instanceof com.openggf.level.objects.RingObjectPlacementMapping mapping) {
            for (var group : mapping.ringObjectPlacementMapping().entrySet()) {
                assertTrue(level.getObjects().contains(group.getKey()));
                assertTrue(level.getRings().containsAll(group.getValue()));
            }
        }
    }

    private static void assertPortableAssetCounts(GameId game, Level level) {
        String counts = game.code() + " decoded portable asset counts: patterns=" + level.getPatternCount()
                + ", chunks=" + level.getChunkCount() + ", blocks=" + level.getBlockCount()
                + ", collision profiles=" + level.getSolidTileCount();
        assertTrue(level.getPatternCount() <= 2048, counts);
        assertTrue(level.getChunkCount() <= 1024, counts);
        assertTrue(level.getBlockCount() <= 256, counts);
        assertTrue(level.getSolidTileCount() <= 256, counts);
    }

    private static void assertPatternsEqual(Level original, Level copied) {
        for (int i = 0; i < original.getPatternCount(); i++) for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            assertEquals(original.getPattern(i).getPixel(x, y), copied.getPattern(i).getPixel(x, y));
        }
    }

    private static void assertConcreteObjects(GameModule module, Level level, GameId game) {
        var registry = module.createObjectRegistry();
        for (ObjectSpawn spawn : level.getObjects()) {
            if (spawn.objectId() == monitorId(game) || isGoal(game, spawn)) {
                assertFalse(registry.create(spawn) instanceof PlaceholderObjectInstance,
                        "the selected native host must instantiate authored objects");
            }
        }
    }

    private static void assertNativeHost(GameId game, Level level) {
        switch (game) {
            case S1 -> assertInstanceOf(Sonic1Level.class, level);
            case S2 -> assertInstanceOf(Sonic2Level.class, level);
            case S3K -> assertInstanceOf(Sonic3kLevel.class, level);
            default -> fail("Unsupported project game");
        }
    }

    static final class Fixture implements AutoCloseable {
        final Rom rom;
        final GameModule module;
        final Level library;
        final InputHandler input = new InputHandler();

        private Fixture(Rom rom, GameModule module, Level library) {
            this.rom = rom; this.module = module; this.library = library;
        }

        static Fixture open(GameId game) throws Exception {
            File file = switch (game) {
                case S1 -> RomTestUtils.ensureSonic1RomAvailable();
                case S2 -> RomTestUtils.ensureSonic2RomAvailable();
                case S3K -> RomTestUtils.ensureSonic3kRomAvailable();
                default -> throw new IllegalArgumentException("Unsupported project game");
            };
            assumeTrue(file != null, "Project lifecycle needs a configured " + game.code() + " ROM");
            var rom = new Rom();
            assertTrue(rom.open(file.getAbsolutePath()), "a configured ROM must be readable");
            TestEnvironment.configureRomFixture(rom);
            var config = SonicConfigurationService.getInstance();
            config.setConfigValue(SonicConfiguration.AUDIO_ENABLED, false);
            config.setConfigValue(SonicConfiguration.EDITOR_ENABLED, true);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setConfigValue(SonicConfiguration.DEFAULT_ROM, game.code());
            config.setConfigValue(switch (game) {
                case S1 -> SonicConfiguration.SONIC_1_ROM;
                case S2 -> SonicConfiguration.SONIC_2_ROM;
                case S3K -> SonicConfiguration.SONIC_3K_ROM;
                default -> throw new IllegalArgumentException();
            }, file.getAbsolutePath());
            AudioManager.getInstance().setBackend(new NullAudioBackend());
            GraphicsManager.getInstance().initHeadless();
            var module = GameModuleRegistry.getCurrent();
            assertEquals(game, module.getGameId(), "configured ROM identity must match the requested host");
            GameplayModeContext gameplay = SessionManager.openGameplaySession(
                    module, module, StockGameDataSources.pinned(rom, module), null);
            GameplaySessionFactory.attachManagers(gameplay, EngineServices.current());
            var player = GameplayTeamBootstrap.registerActiveTeam(module, gameplay.getSpriteManager(), config).mainSprite();
            gameplay.getCamera().setFocusedSprite(player);
            gameplay.getCamera().setFrozen(false);
            gameplay.getLevelManager().loadZoneAndAct(0, 0, LevelLoadMode.PREVIEW_CAPTURE);
            return new Fixture(rom, module, com.openggf.level.LevelObjectArt.authoringLibrary(
                    gameplay.getLevelManager().getCurrentLevel(), module.getObjectArtProvider()));
        }

        @Override public void close() throws Exception { rom.close(); }
    }
}
