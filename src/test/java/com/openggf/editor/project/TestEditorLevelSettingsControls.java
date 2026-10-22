package com.openggf.editor.project;

import com.openggf.editor.EditorCommand;
import com.openggf.editor.LevelEditorController;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameId;
import com.openggf.game.GameModule;
import com.openggf.game.GameplayLaunchTeam;
import com.openggf.game.modzone.ModLevelSettings;
import com.openggf.game.modzone.ModLevelSettingsProvider;
import com.openggf.game.session.EditorCursorState;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** ROM-free settings commands use the real workspace/document/history and host capability catalogue. */
class TestEditorLevelSettingsControls {
    @TempDir Path directory;

    @Test
    void combinedSettingsEditIsExactlyOneUndoRedoAndNoOpCreatesNoEntry() throws Exception {
        var host = new Host(new Sonic2GameModule());
        var document = TestEditorLevelSettingsIO.document(ModLevelSettings.DEFAULT);
        new EditorProjectStore().save(directory.resolve("saved.ogfproject"), document);
        host.workspace.openProject(document);
        var settings = new ModLevelSettings(host.workspace.backgroundOptions().getFirst().source(), 192,
                new GameplayLaunchTeam(CharacterKey.SONIC, List.of(CharacterKey.TAILS)));
        host.workspace.applySettings(document, settings);
        assertEquals(settings, document.metadata().settings());
        assertTrue(document.isModified());
        assertEquals(1, host.executed);
        assertEquals(1, host.metadataChanges);
        host.controller.undo();
        assertEquals(ModLevelSettings.DEFAULT, document.metadata().settings());
        assertFalse(document.isModified(), "undo restores the complete metadata savepoint");
        assertFalse(host.controller.hasUndoHistory(), "all three settings were one command");
        host.controller.redo();
        assertEquals(settings, document.metadata().settings());
        host.workspace.applySettings(document, settings);
        assertEquals(1, host.executed, "applying unchanged settings does not consume history");
        host.controller.undo();
        assertFalse(host.controller.hasUndoHistory());
        assertEquals(ModLevelSettings.DEFAULT, document.metadata().settings());
    }

    @Test
    void directSettingRowsCycleBackgroundLiquidHeightAndAllowedTeam() {
        var host = new Host(new Sonic2GameModule());
        host.workspace.openProject(TestEditorLevelSettingsIO.document(ModLevelSettings.DEFAULT));
        var workspace = host.workspace;
        assertEquals(List.of("Background", "Liquid", "Water height", "Allowed team"),
                workspace.settingsRows().stream().map(EditorProjectWorkspace.SettingRow::label).toList());
        assertEquals(List.of("Flat", "Off", "Disabled", "Any team"),
                workspace.settingsRows().stream().map(EditorProjectWorkspace.SettingRow::value).toList());
        workspace.stepSetting(0, 1);
        assertEquals(workspace.backgroundOptions().getFirst().source(), workspace.document().metadata().settings().background());
        assertEquals(workspace.backgroundOptions().getFirst().label(), workspace.settingsRows().getFirst().value());
        workspace.stepSetting(0, -1);
        assertNull(workspace.document().metadata().settings().background());
        workspace.stepSetting(0, -1);
        assertEquals(workspace.backgroundOptions().getLast().source(), workspace.document().metadata().settings().background());
        workspace.stepSetting(1, 1);
        assertEquals(Integer.valueOf(192), workspace.document().metadata().settings().waterHeight());
        assertEquals("Water", workspace.settingsRows().get(1).value());
        workspace.stepSetting(2, 1);
        assertEquals("208", workspace.settingsRows().get(2).value());
        workspace.stepSetting(2, -1);
        assertEquals(Integer.valueOf(192), workspace.document().metadata().settings().waterHeight());
        workspace.stepSetting(1, -1);
        assertNull(workspace.document().metadata().settings().waterHeight());
        workspace.stepSetting(3, 1);
        assertEquals("Sonic", workspace.settingsRows().get(3).value());
        workspace.stepSetting(3, 1);
        assertEquals("Sonic and Tails", workspace.settingsRows().get(3).value());
        assertEquals(List.of(CharacterKey.TAILS), workspace.document().metadata().settings().team().sidekicks());
        host.controller.undo();
        assertEquals("Sonic", workspace.settingsRows().get(3).value());
    }

    @Test
    void waterHeightSteppingSaturatesNativeSignedBoundsWithoutExtraUndoEntries() {
        var host = new Host(new Sonic2GameModule());
        var document = TestEditorLevelSettingsIO.document(ModLevelSettings.DEFAULT);
        host.workspace.openProject(document);
        host.workspace.applySettings(document, ModLevelSettings.DEFAULT.withWaterHeight((int) Short.MAX_VALUE));
        host.workspace.stepSetting(2, 1);
        assertEquals(Integer.valueOf(Short.MAX_VALUE), document.metadata().settings().waterHeight());
        assertEquals(1, host.executed);
        host.workspace.applySettings(document, ModLevelSettings.DEFAULT.withWaterHeight((int) Short.MIN_VALUE));
        host.workspace.stepSetting(2, -1);
        assertEquals(Integer.valueOf(Short.MIN_VALUE), document.metadata().settings().waterHeight());
        assertEquals(2, host.executed);
    }

    @Test
    void unavailableHostSettingsAndRequiredCharactersFailBeforeHistoryOrMetadataMutation() {
        var module = mock(GameModule.class);
        when(module.getModLevelSettingsProvider()).thenReturn(ModLevelSettingsProvider.UNSUPPORTED);
        when(module.getPlayableCharacterRegistry()).thenReturn(new Sonic2GameModule().getPlayableCharacterRegistry());
        var host = new Host(module);
        var document = TestEditorLevelSettingsIO.document(ModLevelSettings.DEFAULT);
        host.workspace.openProject(document);
        assertFalse(host.workspace.supportsWater());
        assertTrue(host.workspace.backgroundOptions().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> host.workspace.stepSetting(1, 1));
        assertThrows(IllegalArgumentException.class, () -> host.workspace.applySettings(document,
                ModLevelSettings.DEFAULT.withBackground(new ModLevelSettings.StockScrollSource(0, 0))));
        var missing = CharacterKey.mod("missing", "runner");
        assertThrows(GameplayTeamBootstrap.UnavailableRequiredCharacter.class, () -> host.workspace.applySettings(document,
                ModLevelSettings.DEFAULT.withTeam(new GameplayLaunchTeam(missing, List.of()))));
        assertEquals(ModLevelSettings.DEFAULT, document.metadata().settings());
        assertEquals(0, host.executed);
        assertFalse(host.controller.hasUndoHistory());
    }

    @Test
    void staleDocumentIdentityCannotEditReplacementProject() throws Exception {
        var host = new Host(new Sonic2GameModule());
        var original = TestEditorLevelSettingsIO.document(ModLevelSettings.DEFAULT);
        new EditorProjectStore().save(directory.resolve("original.ogfproject"), original);
        host.workspace.openProject(original);
        var replacement = TestEditorLevelSettingsIO.document(ModLevelSettings.DEFAULT);
        replacement.setMetadata(replacement.metadata().withName("Replacement"));
        host.workspace.openProject(replacement);
        assertSame(replacement, host.workspace.document());
        var previous = replacement.metadata();
        assertThrows(IllegalStateException.class, () -> host.workspace.applySettings(original,
                ModLevelSettings.DEFAULT.withWaterHeight(192)));
        assertEquals(previous, replacement.metadata());
        assertEquals(ModLevelSettings.DEFAULT, original.metadata().settings());
        assertEquals(0, host.executed);
        assertFalse(host.controller.hasUndoHistory());
    }

    @Test
    void failedHostMetadataRefreshRestoresSettingsAndDoesNotPublishHistoryEntry() {
        var host = new Host(new Sonic2GameModule());
        var document = TestEditorLevelSettingsIO.document(ModLevelSettings.DEFAULT);
        host.workspace.openProject(document);
        host.failMetadataRefresh = true;
        var failure = assertThrows(IllegalStateException.class, () -> host.workspace.applySettings(document,
                ModLevelSettings.DEFAULT.withWaterHeight(192)));
        assertEquals("metadata refresh failed", failure.getMessage());
        assertEquals(ModLevelSettings.DEFAULT, document.metadata().settings());
        assertFalse(host.controller.hasUndoHistory());
        assertEquals(2, host.metadataChanges, "host receives the change and the rollback refresh");
    }

    @Test
    void sonic1FiltersUnsupportedTeamPresetsAndRejectsRequiredSidekicksBeforeHistoryOrPlaytest() throws Exception {
        var module = new com.openggf.game.sonic1.Sonic1GameModule();
        var host = new Host(module);
        var source = new TestEditorProjects.Fixture(GameId.S1);
        var document = EditorProjectDocument.create(TestEditorProjects.metadata(GameId.S1, source), source);
        document.level().restoreObjectBackedRingState(new com.openggf.level.MutableLevel.ObjectBackedRingState(
                List.of(), List.of(), java.util.Map.of()));
        TestEditorLevelSettingsIO.useGenesisPalette(document);
        for (int index = 0; index < document.level().getChunkCount(); index++)
            document.level().getChunk(index).setSolidTileAltIndex(document.level().getChunk(index).getSolidTileIndex());
        host.workspace.openProject(document);
        assertEquals(List.of("Any team", "Sonic", "Tails", "Knuckles"),
                host.workspace.teamChoices().stream().map(EditorLevelTeams.Choice::label).toList());
        var paired = ModLevelSettings.DEFAULT.withTeam(new GameplayLaunchTeam(CharacterKey.SONIC, List.of(CharacterKey.TAILS)));
        assertThrows(IllegalArgumentException.class, () -> host.workspace.applySettings(document, paired));
        assertEquals(0, host.executed);
        assertFalse(host.controller.hasUndoHistory());
        document.setMetadata(document.metadata().withSettings(paired));
        assertThrows(IllegalArgumentException.class, () -> EditorProjectRuntimeModule.prepare(module, document, 64, 64));
        document.setMetadata(document.metadata().withSettings(ModLevelSettings.DEFAULT));
        host.workspace.stepSetting(3, 1);
        assertEquals(CharacterKey.SONIC, document.metadata().settings().team().main());
        host.workspace.stepSetting(3, 1);
        assertEquals(CharacterKey.TAILS, document.metadata().settings().team().main(), "unsupported paired preset is skipped");
        assertTrue(document.metadata().settings().team().sidekicks().isEmpty());
        assertDoesNotThrow(() -> EditorProjectRuntimeModule.prepare(module, document, 64, 64));
    }

    private static final class Host implements EditorProjectWorkspace.Host {
        final LevelEditorController controller = new LevelEditorController();
        final EditorProjectWorkspace workspace = new EditorProjectWorkspace(this);
        final GameModule module;
        int executed, metadataChanges;
        boolean failMetadataRefresh;
        Host(GameModule module) { this.module = module; controller.setProjects(workspace); }
        @Override public GameModule selectGame(GameId game) { return module; }
        @Override public GameModule currentModule() { return module; }
        @Override public Level loadLibrary(int zone, int act) { throw new AssertionError("settings must not load a ROM library"); }
        @Override public String sourceFingerprint() { return "settings-test"; }
        @Override public EditorCursorState cursor() { return controller.worldCursor(); }
        @Override public void finishPendingEdits() { controller.finishPendingEdits(); }
        @Override public void showDocument(EditorProjectDocument document) { controller.attachLevel(document.level()); }
        @Override public void playtest(EditorProjectDocument document, boolean fromCursor) { throw new AssertionError("not a runtime boot test"); }
        @Override public void closeDocument() { }
        @Override public void menuClosed(EditorProjectDocument document) { }
        @Override public void execute(EditorCommand command) { executed++; controller.executeCommand(command); }
        @Override public void status(String message) { controller.setStatus(message); }
        @Override public void metadataChanged(EditorProjectDocument document) {
            metadataChanges++;
            if (failMetadataRefresh) throw new IllegalStateException("metadata refresh failed");
        }
    }
}
