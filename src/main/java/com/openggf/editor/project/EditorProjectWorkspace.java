package com.openggf.editor.project;

import com.openggf.i18n.UiMessages;
import java.util.Map;



import com.openggf.control.InputHandler;
import com.openggf.editor.EditorCommand;
import com.openggf.editor.EditorCommandPalette;
import com.openggf.editor.persistence.FullLevelExporter;
import com.openggf.game.GameId;
import com.openggf.game.GameModule;
import com.openggf.game.MusicReference;
import com.openggf.game.modzone.ModObjectZoneSet;
import com.openggf.game.session.EditorCursorState;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.game.sonic3k.constants.S3kZoneSet;
import com.openggf.game.MenuTextEditor;
import com.openggf.io.DirectoryAccess;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.level.Level;
import com.openggf.mods.code.BakedLevelRef;
import com.openggf.mods.code.ModLevelDefinitionParser;
import com.openggf.mods.code.ModLevelDefinition;
import com.openggf.mods.code.ModLevelExportStagingValidator;
import com.openggf.mods.code.ModZoneLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Owns project workflow independently of disposable editor/playtest runtime managers. */
public final class EditorProjectWorkspace {
    public interface Host {
        GameModule selectGame(GameId game) throws IOException;
        GameModule currentModule();
        Level loadLibrary(int zone, int act) throws IOException;
        String sourceFingerprint();
        EditorCursorState cursor();
        void finishPendingEdits();
        void showDocument(EditorProjectDocument document) throws IOException;
        void playtest(EditorProjectDocument document, boolean fromCursor) throws IOException;
        void closeDocument();
        void menuClosed(EditorProjectDocument document);
        void execute(EditorCommand command);
        void status(String message);
        default void metadataChanged(EditorProjectDocument document) { }
        default boolean isPaletteCellEditable(int line, int color) { return false; }
        default void refreshPalette(int line) { }
        default void artImported(EditorProjectDocument document, List<Integer> patternIndices) { }
        default boolean nativeLayoutAvailable() { return false; }
        default boolean nativeLayoutModified() { return false; }
        default void saveNativeLayout() throws IOException { }
        default void cloneNativeLayout() throws IOException { }
    }

    private final Host host;
    private final EditorProjectStore store = new EditorProjectStore();
    private final EditorProjectMenu menu = new EditorProjectMenu();
    private EditorProjectDocument document;

    public EditorProjectWorkspace(Host host) { this.host = Objects.requireNonNull(host); }
    public EditorProjectDocument document() { return document; }
    public EditorProjectMenu menu() { return menu; }

    public List<com.openggf.game.modzone.ModLevelSettingsProvider.StockScrollOption> backgroundOptions() {
        return host.currentModule().getModLevelSettingsProvider().stockScrollSources();
    }
    public boolean supportsWater() { return host.currentModule().getModLevelSettingsProvider().supportsWater(); }
    public List<EditorLevelTeams.Choice> teamChoices() {
        boolean sidekicks = host.currentModule().supportsSidekick();
        return EditorLevelTeams.choices().stream().filter(choice -> sidekicks
                || choice.team() == null || choice.team().sidekicks().isEmpty()).toList();
    }

    /** One settings edit is one ordinary undo command; a queued edit cannot cross documents. */
    public void applySettings(EditorProjectDocument expected, com.openggf.game.modzone.ModLevelSettings settings) {
        var current = requireDocument();
        if (current != expected) throw new IllegalStateException(UiMessages.text("editor.editor_project_workspace.the_edited_project_changed_before_applying_settings_miv0nd"));
        com.openggf.game.modzone.ModLevelSettingsValidation.validate(host.currentModule(), settings);
        var previous = current.metadata();
        var replacement = previous.withSettings(settings);
        if (previous.equals(replacement)) return;
        host.finishPendingEdits();
        host.execute(new EditorCommand() {
            @Override public void apply() { changeMetadata(current, replacement); }
            @Override public void undo() { changeMetadata(current, previous); }
        });
        host.status(UiMessages.text("editor.editor_project_workspace.level_settings_updated_playtest_to_preview_the_runtime_1kftc2p"));
    }

    public record SettingRow(String label, String value) { }
    public List<SettingRow> settingsRows() {
        var settings = requireDocument().metadata().settings();
        String background = (settings.background() == null ? UiMessages.text("editor.editor_project_workspace.flat_1azkp") : backgroundOptions().stream().filter(option -> option.source().equals(settings.background())).map(option -> option.label()).findFirst().orElse(UiMessages.text("editor.menu.editor_project_workspace.unavailable_source")));
        return List.of(new SettingRow(UiMessages.text("editor.editor_project_workspace.background_axpbv2"), background),
                new SettingRow(UiMessages.text("editor.editor_project_workspace.liquid_11n8b4s"), (settings.waterHeight() == null ? UiMessages.text("editor.editor_project_workspace.off_1p3j") : UiMessages.text("editor.editor_project_workspace.water_1dmhvb"))),
                new SettingRow(UiMessages.text("editor.editor_project_workspace.water_height_1lbg5tc"), (settings.waterHeight() == null ? UiMessages.text("editor.editor_project_workspace.disabled_5jsqzg") : settings.waterHeight().toString())),
                new SettingRow(UiMessages.text("editor.editor_project_workspace.allowed_team_1v8wstx"), EditorLevelTeams.label(settings.team())));
    }

    public void stepSetting(int row, int direction) {
        var current = requireDocument();
        var value = current.metadata().settings();
        switch (row) {
            case 0 -> {
                var choices = backgroundOptions();
                int index = -1;
                for (int i = 0; i < choices.size(); i++) if (choices.get(i).source().equals(value.background())) index = i;
                index = Math.floorMod(index + 1 + Integer.signum(direction), choices.size() + 1) - 1;
                value = value.withBackground(index < 0 ? null : choices.get(index).source());
            }
            case 1 -> value = value.withWaterHeight(value.waterHeight() == null
                    ? Math.clamp(current.metadata().startY() + 128, (int) Short.MIN_VALUE, (int) Short.MAX_VALUE) : null);
            case 2 -> value = value.withWaterHeight(Math.clamp((value.waterHeight() == null
                    ? current.metadata().startY() : value.waterHeight()) + direction * 16,
                    (int) Short.MIN_VALUE, (int) Short.MAX_VALUE));
            case 3 -> {
                var choices = teamChoices();
                int index = 0;
                for (int i = 0; i < choices.size(); i++) if (Objects.equals(choices.get(i).team(), value.team())) index = i;
                value = value.withTeam(choices.get(Math.floorMod(index + Integer.signum(direction), choices.size())).team());
            }
            default -> throw new IllegalArgumentException(UiMessages.text("editor.editor_project_workspace.unknown_level_setting_xsx8am"));
        }
        applySettings(current, value);
    }

    /** Import sources are bounded to the selected folder and never become runtime dependencies. */
    public void importArt(EditorProjectDocument expected, Path folder, Path png, Path sheet,
            boolean importPalette) throws IOException {
        var current = requireDocument();
        if (current != expected) throw new IllegalStateException(UiMessages.text("editor.editor_project_workspace.the_edited_project_changed_before_importing_art_1gmxf4q"));
        host.finishPendingEdits();
        var prepared = current.prepareArtImport(folder, png, sheet, importPalette,
                host::isPaletteCellEditable, host::refreshPalette, indices -> host.artImported(current, indices));
        host.execute(new EditorCommand() {
            private void requireCurrent() {
                if (document != current) throw new IllegalStateException(UiMessages.text("editor.editor_project_workspace.the_imported_project_is_no_longer_active_9qayf4"));
            }
            @Override public void apply() { requireCurrent(); prepared.command().apply(); }
            @Override public void undo() { requireCurrent(); prepared.command().undo(); }
        });
        host.status(UiMessages.text("editor.editor_project_workspace.imported_tiles_from_frames_first_tile_awoxi9", Map.of("value0", String.valueOf(prepared.patternIndices().size()), "value1", String.valueOf(prepared.frameCount()), "value2", String.valueOf(prepared.patternIndices().getFirst()))));
    }

    public void importArtForm() {
        var current = requireDocument();
        String folder = current.path() == null ? "projects/" : current.path().getParent().toString();
        menu.showForm(UiMessages.text("editor.editor_project_workspace.import_art_1w396dk"), List.of(path("folder", UiMessages.text("editor.editor_project_workspace.art_folder_v5pa7f"), folder),
                path("png", UiMessages.text("editor.editor_project_workspace.png_within_folder_15zby0c"), "art.png"), path("sheet", UiMessages.text("editor.editor_project_workspace.sheet_yaml_within_folder_18zxggb"), "sheet.yaml"),
                integer("palette", UiMessages.text("editor.editor_project_workspace.import_palette_0_1_1kn28an"), 0)), UiMessages.text("editor.editor_project_workspace.import_tiles_fpixka"), values -> io(() -> {
                    int palette = number(values, "palette");
                    if (palette != 0 && palette != 1) throw new IllegalArgumentException(UiMessages.text("editor.editor_project_workspace.import_palette_must_be_0_or_1_cmoh8q"));
                    importArt(current, Path.of(values.get("folder")), Path.of(values.get("png")),
                            Path.of(values.get("sheet")), palette == 1);
                    menu.close();
                }));
        menu.setSubtitle(UiMessages.text("editor.editor_project_workspace.png_and_sheet_yaml_must_both_be_inside_the_chosen_folde_1wj2uyj"));
    }

    /** A shared import can stage another host before validation; cancellation restores this document's view. */
    public void restoreView() { if (!menu.isOpen()) host.menuClosed(document); }

    public boolean update(InputHandler input) {
        boolean wasOpen = menu.isOpen();
        boolean owned = menu.update(input);
        if (wasOpen && !menu.isOpen()) host.menuClosed(document);
        return owned;
    }

    /** Every project command, including its own two playtest entry points. */
    public List<EditorCommandPalette.HostCommand> commands() {
        var commands = new java.util.ArrayList<>(fileCommands());
        commands.add(commands.size() - 1, new EditorCommandPalette.HostCommand(UiMessages.text("editor.editor_project_workspace.playtest_from_level_start_qbtxze"), () -> playtest(false)));
        commands.add(commands.size() - 1, new EditorCommandPalette.HostCommand(UiMessages.text("editor.editor_project_workspace.playtest_from_cursor_1o3u636"), () -> playtest(true)));
        return List.copyOf(commands);
    }

    /**
     * The project's file commands, without playtest: the editor menu's Playtest group already reaches
     * these playtests through the host's playtest actions, which delegate to {@link #playtest} while a
     * project is open.
     */
    public List<EditorCommandPalette.HostCommand> fileCommands() {
        return List.of(new EditorCommandPalette.HostCommand(UiMessages.text("editor.editor_project_workspace.editor_projects_stvulp"), this::openMenu),
                new EditorCommandPalette.HostCommand(UiMessages.text("editor.editor_project_workspace.save_project_as_1nvrvuk"), this::saveAs),
                new EditorCommandPalette.HostCommand(UiMessages.text("editor.editor_project_workspace.level_properties_j6i9nz"), this::properties),
                new EditorCommandPalette.HostCommand(UiMessages.text("editor.editor_project_workspace.resize_level_qdn41k"), this::resize),
                new EditorCommandPalette.HostCommand(UiMessages.text("editor.editor_project_workspace.campaign_levels_jo1x7j"), this::campaign),
                new EditorCommandPalette.HostCommand(UiMessages.text("editor.editor_project_workspace.export_playable_campaign_e6xafq"), this::exportCampaign),
                new EditorCommandPalette.HostCommand(UiMessages.text("editor.editor_project_workspace.import_art_1w396dk"), this::importArtForm),
                new EditorCommandPalette.HostCommand(UiMessages.text("editor.editor_project_workspace.close_project_qnwtld"), this::requestClose));
    }

    public void openMenu() {
        host.finishPendingEdits();
        var actions = new ArrayList<EditorProjectMenu.Action>();
        actions.add(action(UiMessages.text("editor.editor_project_workspace.new_blank_level_12gxxco"), UiMessages.text("editor.menu.editor_project_workspace.choose_a_game_zone_and_act_to_start_from"), () -> beforeReplace(this::chooseGame)));
        actions.add(action(UiMessages.text("editor.editor_project_workspace.open_project_10m6ezn"), UiMessages.text("editor.menu.editor_project_workspace.load_a_saved_ogfproject_file"), () -> beforeReplace(this::openProjectForm)));
        actions.add(action(UiMessages.text("editor.editor_project_workspace.import_exported_level_y9qo8i"), UiMessages.text("editor.menu.editor_project_workspace.bring_back_a_level_exported_from_the_editor"), () -> beforeReplace(this::chooseImportGame)));
        if (document == null && host.nativeLayoutAvailable())
            actions.add(action(UiMessages.text("editor.editor_project_workspace.clone_native_layout_as_project_1yhjycb"), UiMessages.text("editor.menu.editor_project_workspace.copy_the_level_on_screen_into_a_new_project"), () -> io(host::cloneNativeLayout)));
        if (document != null) {
            actions.add(action(UiMessages.text("editor.editor_project_workspace.save_project_1tfh0gm"), UiMessages.text("editor.menu.editor_project_workspace.write_changes_to_the_project_file"), this::save));
            actions.add(action(UiMessages.text("editor.editor_project_workspace.save_project_as_1nvrvuk"), UiMessages.text("editor.menu.editor_project_workspace.save_to_a_new_file_and_keep_editing_it"), this::saveAs));
            actions.add(action(UiMessages.text("editor.editor_project_workspace.clone_this_layout_1r5xy89"), UiMessages.text("editor.menu.editor_project_workspace.start_a_new_project_from_a_copy_of_this_one"), () -> beforeReplace(this::cloneCurrent)));
            actions.add(action(UiMessages.text("editor.editor_project_workspace.level_properties_j6i9nz"), UiMessages.text("editor.menu.editor_project_workspace.name_start_position_and_camera_limits"), this::properties));
            actions.add(action(UiMessages.text("editor.editor_project_workspace.resize_level_qdn41k"), UiMessages.text("editor.menu.editor_project_workspace.width_and_height_in_blocks_undo_restores_it"), this::resize));
            actions.add(action(UiMessages.text("editor.editor_project_workspace.export_level_zarsqg"), UiMessages.text("editor.menu.editor_project_workspace.validate_and_write_a_playable_level_folder"), this::export));
            actions.add(action(UiMessages.text("editor.editor_project_workspace.close_project_qnwtld"), UiMessages.text("editor.menu.editor_project_workspace.asks_to_save_unsaved_changes_first"), this::requestClose));
            actions.add(action(UiMessages.text("editor.editor_project_workspace.campaign_levels_jo1x7j"), UiMessages.text("editor.menu.editor_project_workspace.order_the_levels_a_campaign_plays"), this::campaign));
            actions.add(action(UiMessages.text("editor.editor_project_workspace.export_playable_campaign_e6xafq"), UiMessages.text("editor.menu.editor_project_workspace.validate_and_package_the_campaign_as_a_mod_jar"), this::exportCampaign));
            actions.add(action(UiMessages.text("editor.editor_project_workspace.import_art_1w396dk"), UiMessages.text("editor.menu.editor_project_workspace.add_tiles_from_a_png_and_sheet_yaml"), this::importArtForm));
        }
        menu.showActions(UiMessages.text("editor.editor_project_workspace.editor_projects_stvulp"), actions);
        if (document != null) menu.setSubtitle((document.isModified() ? String.valueOf((String.valueOf((UiMessages.text("editor.editor_project_workspace.unsaved_changes_8hklc1", Map.of("value0", String.valueOf(document.metadata().name()))))))) : String.valueOf((String.valueOf((UiMessages.text("editor.editor_project_workspace.saved_1xvuzx3", Map.of("value0", String.valueOf(document.metadata().name())))))))));
    }

    /** Explicitly copies a compatible loaded layout into a portable authored project. */
    public void adoptLoadedLevel(GameModule module, Level level, int zone, int act) throws IOException {
        // First native entry uses complete ROM libraries; reopening authored data preserves its pixels.
        if (!(level instanceof com.openggf.level.MutableLevel) && !(level instanceof EditorProjectLevel))
            level = com.openggf.level.LevelObjectArt.authoringLibrary(level, module.getObjectArtProvider());
        var zones = module.getZoneRegistry();
        int[] start = zones.getStartPosition(zone, act);
        ModObjectZoneSet family = module.getGameId() == GameId.S3K
                ? ModObjectZoneSet.valueOf((level instanceof Sonic3kLevel nativeLevel
                        ? nativeLevel.getObjectZoneSet() : S3kZoneSet.forZone(level.getZoneIndex())).name()) : null;
        var metadata = new EditorProjectMetadata(module.getGameId(), UiMessages.text("editor.project.zone_act", Map.of("zone", zones.getZoneName(zone), "act", act + 1)),
                zone, act, host.sourceFingerprint(), start[0], start[1], level.getMinX(), level.getMaxX(),
                level.getMinY(), level.getMaxY(), exportMusic(zones.getMusicReference(zone, act)), family);
        replaceDocument(EditorProjectDocument.cloneLevel(metadata, level));
    }

    private void chooseGame() {
        menu.showActions(UiMessages.text("editor.editor_project_workspace.choose_project_game_1w6dj36"), List.of(
                action(UiMessages.text("editor.editor_project_workspace.sonic_1_1t0f5st"), () -> chooseSource(GameId.S1)),
                action(UiMessages.text("editor.editor_project_workspace.sonic_2_1t0f5su"), () -> chooseSource(GameId.S2)),
                action(UiMessages.text("editor.editor_project_workspace.sonic_3_knuckles_138eayj"), () -> chooseSource(GameId.S3K))));
    }

    private void chooseSource(GameId game) {
        io(() -> {
            var module = host.selectGame(game);
            var zones = module.getZoneRegistry();
            var choices = new ArrayList<EditorProjectMenu.Action>();
            for (int z = 0; z < zones.getZoneCount(); z++) {
                int zone = z;
                choices.add(action(zones.getZoneName(zone), () -> chooseAct(module, zone)));
            }
            menu.showActions(UiMessages.text("editor.editor_project_workspace.choose_asset_library_1dj61r6"), choices);
            menu.setSubtitle(UiMessages.text("editor.editor_project_workspace.the_new_layout_uses_this_level_s_decoded_art_99swgh"));
        });
    }

    private void chooseAct(GameModule module, int zone) {
        var choices = new ArrayList<EditorProjectMenu.Action>();
        for (int a = 0; a < module.getZoneRegistry().getActCount(zone); a++) {
            int act = a;
            choices.add(action(UiMessages.text("editor.editor_project_workspace.act_1ffngk1", Map.of("value0", String.valueOf((a + 1)))), () -> newLevelForm(module, zone, act)));
        }
        menu.showActions(module.getZoneRegistry().getZoneName(zone), choices);
    }

    private void newLevelForm(GameModule module, int zone, int act) {
        menu.showForm(UiMessages.text("editor.editor_project_workspace.new_blank_level_12gxxco"), List.of(text("name", UiMessages.text("editor.editor_project_workspace.name_1fvl7"), "New Level"),
                integer("width", UiMessages.text("editor.editor_project_workspace.width_in_blocks_1693i07"), 16), integer("height", UiMessages.text("editor.editor_project_workspace.height_in_blocks_pvahc8"), 8)),
                UiMessages.text("editor.editor_project_workspace.create_level_3ua2yo"), values -> io(() -> {
                    Level library = com.openggf.level.LevelObjectArt.authoringLibrary(host.loadLibrary(zone, act), module.getObjectArtProvider());
                    var blank = EditorProjectLevel.blank(library, number(values, "width"), number(values, "height"));
                    ModObjectZoneSet family = module.getGameId() == GameId.S3K
                            ? ModObjectZoneSet.valueOf((library instanceof Sonic3kLevel nativeLevel
                                    ? nativeLevel.getObjectZoneSet() : S3kZoneSet.forZone(library.getZoneIndex())).name()) : null;
                    // Native bounds limit camera origins; the canvas includes the final viewport/runway.
                    int maxX = Math.max(0, blank.getMap().getWidth() * blank.getBlockPixelSize() - 320);
                    int maxY = Math.max(0, blank.getMap().getHeight() * blank.getBlockPixelSize() - 224);
                    var metadata = new EditorProjectMetadata(module.getGameId(), values.get("name"), zone, act,
                            host.sourceFingerprint(), Math.min(64, maxX), Math.min(64, maxY), 0, maxX, 0, maxY,
                            exportMusic(module.getZoneRegistry().getMusicReference(zone, act)), family);
                    replaceDocument(EditorProjectDocument.create(metadata, blank));
                    host.status((blank.blankLibraryNotice().isEmpty() ? UiMessages.text("editor.editor_project_workspace.created_place_terrain_and_set_the_level_start_before_pl_1x84vrm", Map.of("value0", String.valueOf(metadata.name()))) : blank.blankLibraryNotice()));
                }));
    }

    private void cloneCurrent() {
        var source = requireDocument();
        menu.showForm(UiMessages.text("editor.editor_project_workspace.clone_layout_1kiocj1"), List.of(text("name", UiMessages.text("editor.editor_project_workspace.name_1fvl7"), source.metadata().name() + " Copy")),
                UiMessages.text("editor.editor_project_workspace.create_copy_ig7fuh"), values -> io(() -> replaceDocument(source.duplicate(
                        source.metadata().withName(values.get("name"))))));
    }

    private void openProjectForm() {
        menu.showForm(UiMessages.text("editor.editor_project_workspace.open_editor_project_ewokws"), List.of(path("path", UiMessages.text("editor.editor_project_workspace.project_mexp15"), "projects/")), UiMessages.text("editor.editor_project_workspace.open_1gtii"), values -> io(() -> {
            var candidate = store.open(Path.of(values.get("path")));
            publishProject(candidate);
        }));
    }

    /** Opens an already validated import through the same unsaved-document barrier as the project menu. */
    public void openProject(EditorProjectDocument candidate) {
        Objects.requireNonNull(candidate, "candidate");
        beforeReplace(() -> io(() -> publishProject(candidate)));
    }

    private void publishProject(EditorProjectDocument candidate) throws IOException {
        var module = host.selectGame(candidate.metadata().gameId());
        // Deserialization marks missing presentation art; a prepared document can also have a save path.
        if (candidate.needsObjectPreviewLibrary()) {
            Level library = com.openggf.level.LevelObjectArt.authoringLibrary(host.loadLibrary(candidate.metadata().sourceZone(), candidate.metadata().sourceAct()), module.getObjectArtProvider());
            candidate.attachObjectPreviewLibrary(library);
        }
        replaceDocument(candidate);
        host.status((candidate.path() == null ? UiMessages.text("editor.editor_project_workspace.opened_17wjsre", Map.of("value0", String.valueOf((candidate.metadata().name())))) : UiMessages.text("editor.editor_project_workspace.opened_17wjsre", Map.of("value0", String.valueOf((candidate.path()))))));
    }

    /** Parsed assets are self-contained; no archive or input file survives this handoff. */
    public void importLevel(ModLevelDefinition parsed, GameId game) {
        Objects.requireNonNull(parsed, "parsed");
        Objects.requireNonNull(game, "game");
        beforeReplace(() -> io(() -> publishImportedLevel(parsed, game)));
    }

    private void publishImportedLevel(ModLevelDefinition parsed, GameId game) throws IOException {
        var module = host.selectGame(game);
        var data = ModZoneLoader.prepareHostData(parsed);
        module.getModZoneAdapter().validate("openggf-editor", data);
        Level level = module.getModZoneAdapter().load("openggf-editor", data);
        var music = parsed.music() instanceof ModLevelDefinition.StockMusic stock
                ? new FullLevelExporter.ExportMusic.Stock(stock.stockId())
                : new FullLevelExporter.ExportMusic.Track(
                        ((ModLevelDefinition.TrackMusic) parsed.music()).trackKey().modId(),
                        ((ModLevelDefinition.TrackMusic) parsed.music()).trackKey().localName());
        var metadata = new EditorProjectMetadata(game, parsed.zoneName(), 0, 0, host.sourceFingerprint(),
                parsed.start().x(), parsed.start().y(), parsed.bounds().minX(), parsed.bounds().maxX(),
                parsed.bounds().minY(), parsed.bounds().maxY(), music,
                parsed.hostMetadata().map(m -> m.objectZoneSet()).orElse(null), parsed.settings());
        replaceDocument(EditorProjectDocument.cloneLevel(metadata, level));
        host.status(UiMessages.text("editor.editor_project_workspace.imported_save_it_as_a_new_editor_project_15lojpa", Map.of("value0", String.valueOf(parsed.zoneName()))));
    }

    private void chooseImportGame() {
        menu.showActions(UiMessages.text("editor.editor_project_workspace.choose_import_host_1nfbgt6"), List.of(action(UiMessages.text("editor.editor_project_workspace.sonic_1_1t0f5st"), () -> importForm(GameId.S1)),
                action(UiMessages.text("editor.editor_project_workspace.sonic_2_1t0f5su"), () -> importForm(GameId.S2)), action(UiMessages.text("editor.editor_project_workspace.sonic_3_knuckles_138eayj"), () -> importForm(GameId.S3K))));
    }

    private void importForm(GameId game) {
        menu.showForm(UiMessages.text("editor.editor_project_workspace.import_exported_level_y9qo8i"), List.of(path("path", UiMessages.text("editor.editor_project_workspace.level_directory_level_json_1fjlnea"), "exports/editor/")),
                UiMessages.text("editor.editor_project_workspace.import_10a9v45"), values -> io(() -> {
                    Path supplied = Path.of(values.get("path")).toAbsolutePath().normalize();
                    Path root = Files.isDirectory(supplied) ? supplied : supplied.getParent();
                    String entry = Files.isDirectory(supplied) ? "level.json" : supplied.getFileName().toString();
                    try (var assets = ModAssetRoot.directory(root.getParent(), root, ModInputLimits.production(), DirectoryAccess.DEVELOPMENT)) {
                        var parsed = ModLevelDefinitionParser.read(assets, new BakedLevelRef(entry));
                        publishImportedLevel(parsed, game);
                    }
                    host.status(UiMessages.text("editor.editor_project_workspace.imported_save_it_as_a_new_editor_project_15lojpa", Map.of("value0", String.valueOf(supplied))));
                }));
    }

    private void replaceDocument(EditorProjectDocument candidate) throws IOException {
        host.finishPendingEdits();
        host.showDocument(candidate);
        document = candidate;
    }

    public void save() {
        host.finishPendingEdits();
        var current = requireDocument();
        if (current.path() == null) { saveAs(); return; }
        io(() -> { store.save(current.path(), current); host.status(UiMessages.text("editor.editor_project_workspace.saved_1lyob3w", Map.of("value0", String.valueOf(current.path())))); });
    }

    public void saveAs() { saveAsThen(() -> {}); }

    private void saveAsThen(Runnable next) {
        host.finishPendingEdits();
        var current = requireDocument();
        String initial = current.path() == null ? "projects/" + slug(current.metadata().name()) + ".ogfproject" : current.path().toString();
        menu.showForm(UiMessages.text("editor.editor_project_workspace.save_project_as_1nvrvuk"), List.of(path("path", UiMessages.text("editor.editor_project_workspace.project_file_hacc3n"), initial)), UiMessages.text("editor.editor_project_workspace.save_1j2ql"), values -> {
            Path target = projectPath(values.get("path"));
            if (Files.exists(target) && !target.equals(current.path())) {
                menu.showActions(UiMessages.text("editor.editor_project_workspace.replace_existing_project_1euwzan"), List.of(action(UiMessages.text("editor.editor_project_workspace.choose_another_path_160r4a3"), UiMessages.text("editor.menu.editor_project_workspace.keep_the_existing_file"), () -> saveAsThen(next)),
                        action(UiMessages.text("editor.editor_project_workspace.replace_this_file_1t15oaa"), UiMessages.text("editor.menu.editor_project_workspace.overwrites_the_existing_file"), () -> persist(target, current, next))));
                menu.setSubtitle(target.toString());
            } else persist(target, current, next);
        });
    }

    private void persist(Path path, EditorProjectDocument current, Runnable next) {
        io(() -> { store.save(path, current); host.status(UiMessages.text("editor.editor_project_workspace.saved_1lyob3w", Map.of("value0", String.valueOf(path)))); next.run(); });
    }

    public void properties() {
        var current = requireDocument();
        var m = current.metadata();
        var fields = new ArrayList<>(List.of(text("name", UiMessages.text("editor.editor_project_workspace.name_1fvl7"), m.name()), integer("x", UiMessages.text("editor.editor_project_workspace.start_x_1v6o1kq"), m.startX()),
                integer("y", UiMessages.text("editor.editor_project_workspace.start_y_1v6o1kr"), m.startY()), integer("minX", UiMessages.text("editor.editor_project_workspace.camera_minimum_x_1tjagmj"), m.minX()), integer("maxX", UiMessages.text("editor.editor_project_workspace.camera_maximum_x_1grdkcd"), m.maxX()),
                integer("minY", UiMessages.text("editor.editor_project_workspace.camera_minimum_y_1tjagmk"), m.minY()), integer("maxY", UiMessages.text("editor.editor_project_workspace.camera_maximum_y_1grdkce"), m.maxY()),
                text("music", UiMessages.text("editor.editor_project_workspace.music_stock_id_or_owner_key_x49fgx"), musicText(m.music()))));
        if (m.gameId() == GameId.S3K) fields.add(text("family", UiMessages.text("editor.editor_project_workspace.object_family_s3kl_or_skl_1w9763"), m.objectZoneSet().name()));
        menu.showForm(UiMessages.text("editor.editor_project_workspace.level_properties_j6i9nz"), fields, UiMessages.text("editor.editor_project_workspace.apply_properties_42s405"), values -> io(() -> {
            var replacement = new EditorProjectMetadata(m.gameId(), values.get("name"), m.sourceZone(), m.sourceAct(), m.sourceFingerprint(),
                    number(values, "x"), number(values, "y"), number(values, "minX"), number(values, "maxX"),
                    number(values, "minY"), number(values, "maxY"), parseMusic(values.get("music")),
                    m.gameId() == GameId.S3K ? ModObjectZoneSet.valueOf(values.get("family").strip().toUpperCase(java.util.Locale.ROOT)) : null, m.settings(), m.campaign());
            boolean newBounds = replacement.minX() != m.minX() || replacement.maxX() != m.maxX()
                    || replacement.minY() != m.minY() || replacement.maxY() != m.maxY();
            if (newBounds) {
                var candidate = current.copyForReplacement();
                candidate.setMetadata(replacement);
                replaceDocument(candidate);
            }
            else host.execute(new EditorCommand() {
                @Override public void apply() { changeMetadata(current, replacement); }
                @Override public void undo() { changeMetadata(current, m); }
            });
            host.status((newBounds ? UiMessages.text("editor.editor_project_workspace.properties_applied_bounds_start_a_new_undo_checkpoint_19g85n4") : UiMessages.text("editor.editor_project_workspace.properties_applied_1h5chq")));
        }));
        menu.setSubtitle(UiMessages.text("editor.editor_project_workspace.bounds_limit_the_camera_keep_terrain_through_the_final_jiwqbu"));
    }

    public void resize() {
        var current = requireDocument();
        menu.showForm(UiMessages.text("editor.editor_project_workspace.resize_level_qdn41k"), List.of(integer("width", UiMessages.text("editor.editor_project_workspace.width_in_blocks_1693i07"), current.level().getMap().getWidth()),
                integer("height", UiMessages.text("editor.editor_project_workspace.height_in_blocks_pvahc8"), current.level().getMap().getHeight()),
                text("crop", UiMessages.text("editor.editor_project_workspace.allow_cropping_yes_no_rgb160"), "no")), UiMessages.text("editor.editor_project_workspace.resize_new_undo_checkpoint_1xxz1mv"), values -> io(() -> {
            String crop = values.get("crop").strip().toLowerCase(java.util.Locale.ROOT);
            if (!crop.equals("yes") && !crop.equals("no")) throw new IllegalArgumentException(UiMessages.text("editor.editor_project_workspace.crop_must_be_yes_or_no_3q8jf3"));
            var candidate = current.copyForReplacement();
            candidate.resize(number(values, "width"), number(values, "height"), crop.equals("yes"));
            replaceDocument(candidate);
            host.status(UiMessages.text("editor.editor_project_workspace.level_resized_a_new_undo_checkpoint_has_started_save_to_xvvdpq"));
        }));
    }

    public void playtest(boolean fromCursor) {
        host.finishPendingEdits();
        io(() -> host.playtest(requireDocument(), fromCursor));
    }

    public void export() {
        host.finishPendingEdits();
        var current = requireDocument();
        menu.showForm(UiMessages.text("editor.editor_project_workspace.export_playable_level_11k1um6"), List.of(path("path", UiMessages.text("editor.editor_project_workspace.new_export_directory_1fpj4pt"), "exports/editor/"
                + current.metadata().gameId().code() + "/" + slug(current.metadata().name()))), UiMessages.text("editor.editor_project_workspace.validate_and_export_1cta3wn"), values -> io(() -> {
            GameModule module = host.currentModule();
            var m = current.metadata();
            var exporter = new FullLevelExporter(new ModLevelExportStagingValidator(module));
            var result = exporter.export(current.level(), EditorProjectRuntimeModule.request(current,
                    Path.of(values.get("path")), m.startX(), m.startY()));
            host.status(UiMessages.text("editor.editor_project_workspace.exported_6a0trk", Map.of("value0", String.valueOf(result.directory()))));
        }));
    }

    public void applyCampaign(EditorProjectDocument expected, EditorCampaign value) {
        var current = requireDocument();
        if (current != expected) throw new IllegalStateException(UiMessages.text("editor.editor_project_workspace.the_edited_project_changed_101xm5h"));
        var before = current.metadata(); var after = before.withCampaign(value);
        if (before.equals(after)) return;
        host.finishPendingEdits();
        host.execute(new EditorCommand() {
            @Override public void apply() { changeMetadata(current, after); }
            @Override public void undo() { changeMetadata(current, before); }
        });
    }

    public void campaign() {
        var current = requireDocument();
        var actions = new ArrayList<EditorProjectMenu.Action>();
        var paths = current.metadata().campaign().projectPaths();
        actions.add(action(UiMessages.text("editor.editor_project_workspace.add_level_to_sequence_1wfny2z"), () -> menu.showForm(UiMessages.text("editor.editor_project_workspace.add_campaign_level_2sku7n"), List.of(
                path("level", UiMessages.text("editor.editor_project_workspace.relative_project_path_kcm5j4"), current.path() == null ? "level.ogfproject" : current.path().getFileName().toString())),
                UiMessages.text("editor.editor_project_workspace.append_level_75vry"), values -> {
                    var list = new ArrayList<>(paths); list.add(values.get("level"));
                    applyCampaign(current, new EditorCampaign(list)); campaign();
                })));
        for (int i = 0; i < paths.size(); i++) { int index = i;
            actions.add(action((i + 1) + ". " + paths.get(i), () -> campaignLevel(current, index)));
        }
        actions.add(action(UiMessages.text("editor.editor_project_workspace.export_playable_campaign_e6xafq"), this::exportCampaign));
        menu.showActions(UiMessages.text("editor.editor_project_workspace.campaign_levels_jo1x7j"), actions);
        menu.setSubtitle(UiMessages.text("editor.editor_project_workspace.ordered_relative_ogfproject_files_empty_uses_this_level_msz5rt"));
    }
    private void campaignLevel(EditorProjectDocument current, int index) {
        var paths = current.metadata().campaign().projectPaths();
        var actions = new ArrayList<EditorProjectMenu.Action>();
        if (index > 0) actions.add(action(UiMessages.text("editor.editor_project_workspace.move_earlier_zcsp3l"), () -> {
            var list = new ArrayList<>(paths); java.util.Collections.swap(list, index, index - 1);
            applyCampaign(current, new EditorCampaign(list)); campaign();
        }));
        if (index + 1 < paths.size()) actions.add(action(UiMessages.text("editor.editor_project_workspace.move_later_1hkzul9"), () -> {
            var list = new ArrayList<>(paths); java.util.Collections.swap(list, index, index + 1);
            applyCampaign(current, new EditorCampaign(list)); campaign();
        }));
        actions.add(action(UiMessages.text("editor.editor_project_workspace.remove_level_zcx3pk"), () -> {
            var list = new ArrayList<>(paths); list.remove(index);
            applyCampaign(current, new EditorCampaign(list)); campaign();
        }));
        menu.showActions(paths.get(index), actions);
    }
    public void exportCampaign() {
        host.finishPendingEdits();
        var current = requireDocument();
        if (current.path() == null) { saveAsThen(this::exportCampaign); return; }
        menu.showForm(UiMessages.text("editor.editor_project_workspace.export_playable_campaign_e6xafq"), List.of(
                path("path", UiMessages.text("editor.editor_project_workspace.new_mod_jar_1uhiq0t"), "exports/" + slug(current.metadata().name()) + ".jar"),
                text("id", UiMessages.text("editor.editor_project_workspace.mod_id_127amft"), "editor-" + slug(current.metadata().name())),
                text("name", UiMessages.text("editor.editor_project_workspace.campaign_title_31sodk"), current.metadata().name()),
                text("version", UiMessages.text("editor.editor_project_workspace.content_version_r9qwi9"), "1.0.0"), text("author", UiMessages.text("editor.editor_project_workspace.author_wmdne3"), "Level author")),
                UiMessages.text("editor.editor_project_workspace.save_and_export_campaign_p4kxww"), values -> io(() -> {
                    if (document != current) throw new IllegalStateException(UiMessages.text("editor.editor_project_workspace.the_edited_project_changed_101xm5h"));
                    store.save(current.path(), current);
                    var request = new EditorCampaignExportService.Request(Path.of(values.get("path")), values.get("id"),
                            values.get("name"), values.get("version"), values.get("author"), "campaign");
                    var result = new EditorCampaignExportService().export(current.metadata().campaign().resolve(current.path()), request, host.currentModule());
                    host.status(UiMessages.text("editor.editor_project_workspace.exported_campaign_levels_to_jy6a7q", Map.of("value0", String.valueOf(result.levels()), "value1", String.valueOf(result.outputJar()))));
                }));
        menu.setSubtitle(UiMessages.text("editor.editor_project_workspace.other_projects_use_their_saved_state_every_level_must_u_i7hggm"));
    }

    public void requestClose() { beforeReplace(() -> { host.closeDocument(); document = null; }); }

    /** Keep the document alive until the host actually tears down the application. */
    public void requestQuit(Runnable quit) {
        Objects.requireNonNull(quit, "quit");
        beforeReplace(quit, UiMessages.text("editor.editor_project_workspace.cancel_quit_mm1p1"), UiMessages.text("editor.editor_project_workspace.save_and_quit_13adesb"), UiMessages.text("editor.editor_project_workspace.quit_without_saving_roc6cj"));
    }

    private void beforeReplace(Runnable next) {
        beforeReplace(next, UiMessages.text("editor.editor_project_workspace.continue_editing_1v87oe7"), UiMessages.text("editor.editor_project_workspace.save_and_continue_1cnrwxf"), UiMessages.text("editor.editor_project_workspace.discard_changes_and_continue_12z0qjz"));
    }

    private void beforeReplace(Runnable next, String cancel, String save, String discard) {
        host.finishPendingEdits();
        if (document == null && host.nativeLayoutModified()) {
            menu.showActions(UiMessages.text("editor.editor_project_workspace.unsaved_native_edits_1n39tqq"), List.of(action(cancel, () -> {}),
                    action(save, () -> io(() -> { host.saveNativeLayout(); next.run(); })),
                    action(discard, UiMessages.text("editor.menu.editor_project_workspace.your_unsaved_layout_edits_are_lost"), next)));
            return;
        }
        if (document == null || !document.isModified()) { next.run(); return; }
        menu.showActions(UiMessages.text("editor.editor_project_workspace.unsaved_project_1jta9nr"), List.of(action(cancel, () -> {}),
                action(save, () -> {
                    if (document.path() == null) saveAsThen(next);
                    else { save(); next.run(); }
                }), action(discard, UiMessages.text("editor.menu.editor_project_workspace.your_unsaved_changes_are_lost"), next)));
        menu.setSubtitle(document.metadata().name());
    }

    private EditorProjectDocument requireDocument() {
        if (document == null) throw new IllegalStateException(UiMessages.text("editor.editor_project_workspace.open_or_create_a_project_first_19gay25"));
        return document;
    }

    private void changeMetadata(EditorProjectDocument current, EditorProjectMetadata replacement) {
        var previous = current.metadata();
        current.setMetadata(replacement);
        try { host.metadataChanged(current); }
        catch (RuntimeException failure) {
            current.setMetadata(previous);
            try { host.metadataChanged(current); }
            catch (RuntimeException restoreFailure) { failure.addSuppressed(restoreFailure); }
            throw failure;
        }
    }

    private static Path projectPath(String text) {
        if (text.isBlank()) throw new IllegalArgumentException(UiMessages.text("editor.editor_project_workspace.choose_a_project_file_1pmo80b"));
        return Path.of(text.endsWith(".ogfproject") ? text : text + ".ogfproject").toAbsolutePath().normalize();
    }
    private static String slug(String text) {
        String slug = text.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-").replaceAll("^-|-$", "");
        return slug.isBlank() ? "level" : slug;
    }
    private static int number(Map<String, String> values, String key) {
        try { return Integer.parseInt(values.get(key).strip()); }
        catch (NumberFormatException failure) { throw new IllegalArgumentException(UiMessages.text("editor.editor_project_workspace.invalid_integer_for_1r56zj9", Map.of("value0", String.valueOf(key)))); }
    }
    private static FullLevelExporter.ExportMusic parseMusic(String text) {
        String[] key = text.strip().split(":", 2);
        if (key.length != 2) throw new IllegalArgumentException(UiMessages.text("editor.editor_project_workspace.music_must_be_stock_id_or_owner_key_wexeqj"));
        return key[0].equals("stock") ? new FullLevelExporter.ExportMusic.Stock(Integer.parseInt(key[1]))
                : new FullLevelExporter.ExportMusic.Track(key[0], key[1]);
    }
    private static String musicText(FullLevelExporter.ExportMusic music) {
        return music instanceof FullLevelExporter.ExportMusic.Stock stock ? "stock:" + stock.id()
                : ((FullLevelExporter.ExportMusic.Track) music).owner() + ":" + ((FullLevelExporter.ExportMusic.Track) music).localName();
    }
    private static FullLevelExporter.ExportMusic exportMusic(MusicReference music) {
        return music instanceof MusicReference.Stock stock ? new FullLevelExporter.ExportMusic.Stock(Math.max(0, stock.musicId()))
                : new FullLevelExporter.ExportMusic.Track(((MusicReference.Namespaced) music).owner(), ((MusicReference.Namespaced) music).localName());
    }
    private static EditorProjectMenu.Action action(String label, Runnable callback) { return new EditorProjectMenu.Action(label, callback); }
    private static EditorProjectMenu.Action action(String label, String note, Runnable callback) { return new EditorProjectMenu.Action(label, note, callback); }
    private static EditorProjectMenu.Field text(String key, String label, String value) { return new EditorProjectMenu.Field(key, label, value, MenuTextEditor.Mode.TEXT); }
    private static EditorProjectMenu.Field integer(String key, String label, int value) { return new EditorProjectMenu.Field(key, label, Integer.toString(value), MenuTextEditor.Mode.INTEGER); }
    private static EditorProjectMenu.Field path(String key, String label, String value) { return new EditorProjectMenu.Field(key, label, value, MenuTextEditor.Mode.PATH); }
    private static void io(IoAction action) {
        try { action.run(); }
        catch (IOException failure) { throw new IllegalStateException(failure.getMessage(), failure); }
    }
    @FunctionalInterface private interface IoAction { void run() throws IOException; }
}
