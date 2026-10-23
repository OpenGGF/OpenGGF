package com.openggf.editor.project;

import com.openggf.Engine;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomManager;
import com.openggf.editor.EditorCommand;
import com.openggf.editor.LevelEditorController;
import com.openggf.game.GameId;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.common.CommonObjectPlacementEncoding;
import com.openggf.game.session.EditorCursorState;
import com.openggf.game.session.SessionManager;
import com.openggf.level.Level;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static com.openggf.editor.project.TestEditorProjectLifecycle.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual ROM documents with deterministic host I/O faults; gameplay is never mocked. */
@Isolated
class TestEditorProjectWorkspace {
    @TempDir Path temporary;
    private Fixture fixture;

    @AfterEach
    void cleanup() throws Exception {
        SessionManager.clear();
        if (fixture != null) fixture.close();
        RomManager.getInstance().setRom(null);
        Engine.clearGlobalInstance();
        TestEnvironment.resetAll();
        SonicConfigurationService.getInstance().resetToDefaults();
        GameModuleRegistry.reset();
    }

    @ParameterizedTest(name = "{0}: failed open/game switch/save leave the active document and savepoint intact")
    @EnumSource(value = GameId.class, names = {"S1", "S2", "S3K"})
    void failedOpenSwitchAndSaveDoNotPublishOrDiscardCurrentDocument(GameId game) throws Exception {
        var host = start(game);
        var workspace = host.workspace;
        var original = workspace.document();
        Path saved = temporary.resolve("original.ogfproject");
        saveAs(workspace, saved);
        byte[] savedBytes = Files.readAllBytes(saved);
        var content = original.snapshot();

        Path corrupt = temporary.resolve("corrupt.ogfproject");
        Files.writeString(corrupt, "{not a project}");
        workspace.openMenu();
        choose(workspace, fixture.input, 1);
        workspace.menu().setValue("path", corrupt.toString());
        workspace.menu().submit();
        assertNotNull(workspace.menu().error());
        assertSame(original, workspace.document());
        assertSame(original, host.shown);
        assertSame(original.level(), host.controller.currentLevel());
        assertFalse(original.isModified());
        assertTrue(content.contentEquals(original.level()));

        host.failSelection = true;
        workspace.openMenu();
        choose(workspace, fixture.input, 1);
        workspace.menu().setValue("path", saved.toString());
        workspace.menu().submit();
        assertTrue(workspace.menu().error().contains("host selection failed"));
        assertSame(original, workspace.document());
        assertSame(original, host.shown);
        assertEquals(saved.toAbsolutePath(), original.path());
        host.failSelection = false;

        workspace.properties();
        workspace.menu().setValue("name", "Unsaved author change");
        workspace.menu().submit();
        assertNull(workspace.menu().error());
        assertTrue(original.isModified());
        assertTrue(host.controller.isModified(), "metadata-only edits must appear in the editor dirty indicator");
        var metadata = original.metadata();
        Path blocker = temporary.resolve("not-a-directory");
        Files.writeString(blocker, "parent blocked");
        workspace.saveAs();
        workspace.menu().setValue("path", blocker.resolve("cannot-save.ogfproject").toString());
        workspace.menu().submit();
        assertNotNull(workspace.menu().error());
        assertTrue(workspace.menu().isOpen());
        assertSame(original, workspace.document());
        assertSame(original, host.shown);
        assertEquals(saved.toAbsolutePath(), original.path());
        assertEquals(metadata, original.metadata());
        assertTrue(original.isModified());
        assertArrayEquals(savedBytes, Files.readAllBytes(saved), "a failed Save As cannot replace the previous save");
        assertTrue(content.contentEquals(original.level()));
        host.controller.undo();
        assertFalse(original.isModified(), "undoing metadata returns to the complete document savepoint");
        assertFalse(host.controller.isModified());
        assertTrue(host.finishCount > 0, "project boundaries must finalize pending edits");
    }

    @ParameterizedTest(name = "{0}: a dropped project obeys cancel, discard and host publication barriers")
    @EnumSource(value = GameId.class, names = {"S1", "S2", "S3K"})
    void preparedProjectCannotReplaceUnsavedWorkUntilTheAuthorChooses(GameId game) throws Exception {
        var host = start(game);
        var workspace = host.workspace;
        var original = workspace.document();
        Path incomingFile = temporary.resolve("incoming.ogfproject");
        new EditorProjectStore().save(incomingFile,
                EditorProjectDocument.create(original.metadata().withName("Dropped project"), original.snapshot()));
        var incoming = new EditorProjectStore().open(incomingFile);
        assertTrue(original.isModified());

        workspace.openProject(incoming);
        assertEquals("Unsaved project", workspace.menu().title());
        assertSame(original, workspace.document());
        choose(workspace, fixture.input, 0);
        assertSame(original, workspace.document(), "Continue editing keeps the original authored document");

        host.failPresentation = true;
        workspace.openProject(incoming);
        choose(workspace, fixture.input, 2);
        assertTrue(workspace.menu().error().contains("presentation failed"));
        assertSame(original, workspace.document());
        assertSame(original, host.shown);
        assertTrue(original.isModified());

        host.failPresentation = false;
        workspace.openProject(incoming);
        choose(workspace, fixture.input, 2);
        assertSame(incoming, workspace.document());
        assertSame(incoming, host.shown);
        assertEquals(incomingFile.toAbsolutePath(), incoming.path());
        assertFalse(incoming.isModified());
    }

    @ParameterizedTest(name = "{0}: rejected document presentation leaves content, history and path intact")
    @EnumSource(value = GameId.class, names = {"S1", "S2", "S3K"})
    void structuralChangesPublishOnlyAfterTheHostAcceptsTheReplacement(GameId game) throws Exception {
        var host = start(game);
        var workspace = host.workspace;
        var original = workspace.document();
        Path path = temporary.resolve("structural.ogfproject");
        saveAs(workspace, path);
        var before = original.snapshot();
        var metadata = original.metadata();
        var level = original.level();
        host.failPresentation = true;

        workspace.properties();
        workspace.menu().setValue("maxX", Integer.toString(metadata.maxX() - 16));
        workspace.menu().submit();
        assertTrue(workspace.menu().error().contains("presentation failed"));
        assertSame(original, workspace.document());
        assertSame(original, host.shown);
        assertSame(level, host.controller.currentLevel());
        assertEquals(metadata, original.metadata());
        assertFalse(original.isModified());
        assertTrue(before.contentEquals(original.level()));

        workspace.resize();
        workspace.menu().setValue("width", "9");
        workspace.menu().submit();
        assertTrue(workspace.menu().error().contains("presentation failed"));
        assertSame(original, workspace.document());
        assertSame(level, host.controller.currentLevel());
        assertEquals(path.toAbsolutePath(), original.path());
        assertFalse(original.isModified());
        assertTrue(before.contentEquals(original.level()));
        host.controller.undo();
        assertEquals(0, original.level().getRings().size(), "failed presentation cannot clear the active undo stack");
        host.controller.redo();
        assertTrue(before.contentEquals(original.level()));
        assertFalse(original.isModified());

        host.failPresentation = false;
        workspace.resize();
        workspace.menu().setValue("width", "9");
        workspace.menu().submit();
        assertNull(workspace.menu().error());
        var resized = workspace.document();
        assertNotSame(original, resized);
        assertSame(resized, host.shown);
        assertSame(resized.level(), host.controller.currentLevel());
        assertEquals(9, resized.level().getMap().getWidth());
        assertEquals(path.toAbsolutePath(), resized.path());
        assertTrue(resized.isModified());
        var resizedContent = resized.snapshot();
        host.controller.undo();
        assertTrue(resizedContent.contentEquals(resized.level()), "a structural replacement begins its documented new checkpoint");
        workspace.save();
        assertFalse(resized.isModified());
        assertFalse(host.controller.isModified());
        assertEquals(9, new EditorProjectStore().open(path).level().getMap().getWidth());
    }

    @ParameterizedTest(name = "{0}: a missing keyed dependency survives save/reopen while playtest is blocked")
    @EnumSource(value = GameId.class, names = {"S1", "S2", "S3K"})
    void missingObjectDependencyIsPreservedAndCannotStartGameplay(GameId game) throws Exception {
        var host = start(game);
        var workspace = host.workspace;
        var original = workspace.document();
        var missing = new CommonObjectPlacementEncoding().createKeyed(
                120, 64, "absent:device", 7, 3, true, 909);
        original.level().addObjectSpawn(missing);
        Path file = temporary.resolve("dependency.ogfproject");
        saveAs(workspace, file);
        var reopened = new EditorProjectStore().open(file);
        assertEquals(missing, reopened.level().getObjects().stream()
                .filter(o -> "absent:device".equals(o.objectKey())).findFirst().orElseThrow());
        var before = original.snapshot();
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> workspace.playtest(false));
        assertInstanceOf(IOException.class, failure.getCause());
        assertTrue(failure.getMessage().contains("absent:device"));
        assertSame(original, workspace.document());
        assertSame(original, host.shown);
        assertSame(original.level(), host.controller.currentLevel());
        assertTrue(before.contentEquals(original.level()));
        assertFalse(original.isModified());
        assertEquals(file.toAbsolutePath(), original.path());
        assertEquals(0, host.preparedPlaytests);
        IOException independent = assertThrows(IOException.class,
                () -> EditorProjectRuntimeModule.prepare(fixture.module, reopened, 64, 64));
        assertTrue(independent.getMessage().contains("absent:device"));
        assertFalse(reopened.isModified());
    }

    @ParameterizedTest(name = "{0}: rejected replacement keeps the current document through the open form")
    @EnumSource(value = GameId.class, names = {"S1", "S2", "S3K"})
    void failedDocumentSwitchLeavesTheSavedAuthoredLevelAndDirtyIndicator(GameId game) throws Exception {
        var host = start(game);
        var workspace = host.workspace;
        var original = workspace.document();
        Path file = temporary.resolve("switch.ogfproject");
        saveAs(workspace, file);
        var before = original.snapshot();
        host.failPresentation = true;
        workspace.openMenu();
        choose(workspace, fixture.input, 1);
        workspace.menu().setValue("path", file.toString());
        workspace.menu().submit();
        assertTrue(workspace.menu().error().contains("presentation failed"));
        assertSame(original, workspace.document());
        assertSame(original, host.shown);
        assertSame(original.level(), host.controller.currentLevel());
        assertFalse(host.controller.isModified());
        assertEquals(file.toAbsolutePath(), original.path());
        assertTrue(before.contentEquals(original.level()));
        host.failPresentation = false;
        host.failClose = true;
        workspace.openMenu();
        choose(workspace, fixture.input, 9);
        assertTrue(workspace.menu().error().contains("close failed"));
        assertSame(original, workspace.document(), "failed close must retain the current document");
        assertSame(original, host.shown);
        assertSame(original.level(), host.controller.currentLevel());
        assertFalse(original.isModified());
    }

    @Test
    void quittingHonoursCancelSaveFailureSavedAndDiscardWithoutLosingTheDocument() throws Exception {
        var host = start(GameId.S2);
        var workspace = host.workspace;
        var original = workspace.document();
        var exits = new AtomicInteger();
        workspace.requestQuit(exits::incrementAndGet);
        choose(workspace, fixture.input, 0);
        assertEquals(0, exits.get());
        assertSame(original, workspace.document());
        assertTrue(original.isModified());
        assertFalse(workspace.menu().isOpen());

        Path file = temporary.resolve("quit.ogfproject");
        workspace.requestQuit(exits::incrementAndGet);
        choose(workspace, fixture.input, 1);
        assertEquals("Save project as", workspace.menu().title());
        workspace.menu().setValue("path", file.toString());
        workspace.menu().submit();
        assertNull(workspace.menu().error());
        assertEquals(1, exits.get(), "Save and quit runs only after the project is published");
        assertFalse(original.isModified());
        assertEquals(file.toAbsolutePath(), original.path());
        workspace.requestQuit(exits::incrementAndGet);
        assertEquals(2, exits.get(), "an already saved project needs no dirty-document barrier");
        assertFalse(workspace.menu().isOpen());

        workspace.properties();
        workspace.menu().setValue("name", "Save before quit");
        workspace.menu().submit();
        assertTrue(original.isModified());
        byte[] saved = Files.readAllBytes(file);
        Path backup = temporary.resolve("preceding-save.ogfproject");
        Files.move(file, backup);
        Files.createDirectory(file);
        workspace.requestQuit(exits::incrementAndGet);
        choose(workspace, fixture.input, 1);
        assertNotNull(workspace.menu().error());
        assertEquals(2, exits.get(), "a failed save must never invoke the exit continuation");
        assertSame(original, workspace.document());
        assertSame(original, host.shown);
        assertTrue(original.isModified());
        assertArrayEquals(saved, Files.readAllBytes(backup));
        Files.delete(file);
        Files.move(backup, file);

        workspace.requestQuit(exits::incrementAndGet);
        choose(workspace, fixture.input, 1);
        assertEquals(3, exits.get());
        assertFalse(original.isModified());
        assertEquals("Save before quit", new EditorProjectStore().open(file).metadata().name());
        workspace.properties();
        workspace.menu().setValue("name", "Discard this unsaved name");
        workspace.menu().submit();
        workspace.requestQuit(exits::incrementAndGet);
        choose(workspace, fixture.input, 2);
        assertEquals(4, exits.get());
        assertSame(original, workspace.document(), "quit authorization does not destroy authored data before teardown");
        assertTrue(original.isModified());
        assertEquals("Save before quit", new EditorProjectStore().open(file).metadata().name());
    }

    private FaultingHost start(GameId game) throws Exception {
        fixture = Fixture.open(game);
        var host = new FaultingHost(fixture);
        createBlank(host.workspace, fixture.input, game, "Workspace " + game.code());
        assertTrue(host.controller.isModified(), "new documents are dirty before their first save");
        authorCourse(host.workspace.document(), host.controller, game);
        return host;
    }

    @Test
    void nativeEditsSurviveCancelledOrFailedSaveBeforeQuitAndProjectReplacement() throws Exception {
        fixture = Fixture.open(GameId.S2);
        var host = new FaultingHost(fixture);
        host.nativeDirty = true;
        var exits = new AtomicInteger();
        host.workspace.requestQuit(exits::incrementAndGet);
        assertEquals("Unsaved native edits", host.workspace.menu().title());
        choose(host.workspace, fixture.input, 0);
        assertEquals(0, exits.get());
        assertTrue(host.nativeDirty);
        host.nativeSaveFails = true;
        host.workspace.requestQuit(exits::incrementAndGet);
        choose(host.workspace, fixture.input, 1);
        assertEquals("Native save failed", host.workspace.menu().error());
        assertEquals(0, exits.get());
        assertTrue(host.nativeDirty);
        host.nativeSaveFails = false;
        host.workspace.requestQuit(exits::incrementAndGet);
        choose(host.workspace, fixture.input, 1);
        assertEquals(1, exits.get());
        assertFalse(host.nativeDirty);
        host.nativeDirty = true;
        host.workspace.openMenu();
        choose(host.workspace, fixture.input, 0);
        assertEquals("Unsaved native edits", host.workspace.menu().title());
        choose(host.workspace, fixture.input, 2);
        assertEquals("Choose project game", host.workspace.menu().title());
    }

    /** Faults are at the host I/O boundary; all document libraries, codecs and validation are real. */
    private static final class FaultingHost implements EditorProjectWorkspace.Host {
        final Fixture fixture;
        final LevelEditorController controller = new LevelEditorController();
        final EditorProjectWorkspace workspace = new EditorProjectWorkspace(this);
        EditorProjectDocument shown;
        boolean failSelection;
        boolean failPresentation;
        boolean failClose;
        boolean nativeDirty, nativeSaveFails;
        int finishCount;
        int preparedPlaytests;

        FaultingHost(Fixture fixture) {
            this.fixture = fixture;
            controller.setModifiedStateSupplier(() -> workspace.document() != null && workspace.document().isModified());
            controller.setCanvasNavigationSupplier(() -> workspace.document() != null);
        }
        @Override public GameModule selectGame(GameId game) throws IOException {
            if (failSelection) throw new IOException("host selection failed");
            if (game != fixture.module.getGameId()) throw new IOException("This fixture owns only its configured ROM host");
            return fixture.module;
        }
        @Override public GameModule currentModule() { return fixture.module; }
        @Override public Level loadLibrary(int zone, int act) {
            assertEquals(0, zone); assertEquals(0, act); return fixture.library;
        }
        @Override public String sourceFingerprint() {
            return SessionManager.getCurrentWorldSession().getDataSource().identity();
        }
        @Override public EditorCursorState cursor() { return controller.worldCursor(); }
        @Override public void finishPendingEdits() { finishCount++; controller.finishPendingEdits(); }
        @Override public void showDocument(EditorProjectDocument document) throws IOException {
            if (failPresentation) throw new IOException("presentation failed");
            shown = document;
            controller.attachLevel(document.level());
            controller.configureSpawnEditing(fixture.module.createObjectRegistry(),
                    fixture.module.getObjectPlacementEncoding());
            controller.setWorldCursor(new EditorCursorState(document.metadata().startX(), document.metadata().startY()));
        }
        @Override public void playtest(EditorProjectDocument document, boolean fromCursor) throws IOException {
            var start = fromCursor ? cursor() : new EditorCursorState(document.metadata().startX(), document.metadata().startY());
            EditorProjectRuntimeModule.prepare(fixture.module, document, start.x(), start.y());
            preparedPlaytests++;
        }
        @Override public void closeDocument() {
            if (failClose) throw new IllegalStateException("close failed");
            shown = null;
        }
        @Override public void menuClosed(EditorProjectDocument document) { }
        @Override public void execute(EditorCommand command) { controller.executeCommand(command); }
        @Override public void status(String message) { controller.setStatus(message); }
        @Override public boolean nativeLayoutModified() { return nativeDirty; }
        @Override public void saveNativeLayout() throws IOException {
            if (nativeSaveFails) throw new IOException("Native save failed");
            nativeDirty = false;
        }
    }
}
