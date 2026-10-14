package com.openggf.editor.project;

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
import com.openggf.mods.code.ModLevelExportStagingValidator;
import com.openggf.mods.code.ModZoneLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    }

    private final Host host;
    private final EditorProjectStore store = new EditorProjectStore();
    private final EditorProjectMenu menu = new EditorProjectMenu();
    private EditorProjectDocument document;

    public EditorProjectWorkspace(Host host) { this.host = Objects.requireNonNull(host); }
    public EditorProjectDocument document() { return document; }
    public EditorProjectMenu menu() { return menu; }

    public boolean update(InputHandler input) {
        boolean wasOpen = menu.isOpen();
        boolean owned = menu.update(input);
        if (wasOpen && !menu.isOpen()) host.menuClosed(document);
        return owned;
    }

    public List<EditorCommandPalette.HostCommand> commands() {
        return List.of(new EditorCommandPalette.HostCommand("Editor projects", this::openMenu),
                new EditorCommandPalette.HostCommand("Save project as", this::saveAs),
                new EditorCommandPalette.HostCommand("Level properties", this::properties),
                new EditorCommandPalette.HostCommand("Resize level", this::resize),
                new EditorCommandPalette.HostCommand("Playtest from level start", () -> playtest(false)),
                new EditorCommandPalette.HostCommand("Playtest from cursor", () -> playtest(true)),
                new EditorCommandPalette.HostCommand("Close project", this::requestClose));
    }

    public void openMenu() {
        host.finishPendingEdits();
        var actions = new ArrayList<EditorProjectMenu.Action>();
        actions.add(action("New blank level", () -> beforeReplace(this::chooseGame)));
        actions.add(action("Open project", () -> beforeReplace(this::openProjectForm)));
        actions.add(action("Import exported level", () -> beforeReplace(this::chooseImportGame)));
        if (document != null) {
            actions.add(action("Save project", this::save));
            actions.add(action("Save project as", this::saveAs));
            actions.add(action("Clone this layout", () -> beforeReplace(this::cloneCurrent)));
            actions.add(action("Level properties", this::properties));
            actions.add(action("Resize level", this::resize));
            actions.add(action("Export level", this::export));
            actions.add(action("Close project", this::requestClose));
        }
        menu.showActions("Editor projects", actions);
        if (document != null) menu.setSubtitle(document.metadata().name()
                + (document.isModified() ? " - unsaved changes" : " - saved"));
    }

    /** First entry from gameplay captures the authored layout before any runtime mode is destroyed. */
    public void adoptLoadedLevel(GameModule module, Level level, int zone, int act) throws IOException {
        var zones = module.getZoneRegistry();
        int[] start = zones.getStartPosition(zone, act);
        ModObjectZoneSet family = module.getGameId() == GameId.S3K
                ? ModObjectZoneSet.valueOf((level instanceof Sonic3kLevel nativeLevel
                        ? nativeLevel.getObjectZoneSet() : S3kZoneSet.forZone(level.getZoneIndex())).name()) : null;
        var metadata = new EditorProjectMetadata(module.getGameId(), zones.getZoneName(zone) + " Act " + (act + 1),
                zone, act, host.sourceFingerprint(), start[0], start[1], level.getMinX(), level.getMaxX(),
                level.getMinY(), level.getMaxY(), exportMusic(zones.getMusicReference(zone, act)), family);
        replaceDocument(EditorProjectDocument.cloneLevel(metadata, level));
    }

    private void chooseGame() {
        menu.showActions("Choose project game", List.of(
                action("Sonic 1", () -> chooseSource(GameId.S1)),
                action("Sonic 2", () -> chooseSource(GameId.S2)),
                action("Sonic 3 & Knuckles", () -> chooseSource(GameId.S3K))));
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
            menu.showActions("Choose asset library", choices);
            menu.setSubtitle("The new layout uses this level's decoded art.");
        });
    }

    private void chooseAct(GameModule module, int zone) {
        var choices = new ArrayList<EditorProjectMenu.Action>();
        for (int a = 0; a < module.getZoneRegistry().getActCount(zone); a++) {
            int act = a;
            choices.add(action("Act " + (a + 1), () -> newLevelForm(module, zone, act)));
        }
        menu.showActions(module.getZoneRegistry().getZoneName(zone), choices);
    }

    private void newLevelForm(GameModule module, int zone, int act) {
        menu.showForm("New blank level", List.of(text("name", "Name", "New Level"),
                integer("width", "Width in blocks", 16), integer("height", "Height in blocks", 8)),
                "Create level", values -> io(() -> {
                    Level library = host.loadLibrary(zone, act);
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
                    host.status(blank.blankLibraryNotice().isEmpty()
                            ? "Created " + metadata.name() + ". Place terrain and set the level start before playtesting."
                            : blank.blankLibraryNotice());
                }));
    }

    private void cloneCurrent() {
        var source = requireDocument();
        menu.showForm("Clone layout", List.of(text("name", "Name", source.metadata().name() + " Copy")),
                "Create copy", values -> io(() -> replaceDocument(EditorProjectDocument.create(
                        source.metadata().withName(values.get("name")), source.snapshot()))));
    }

    private void openProjectForm() {
        menu.showForm("Open editor project", List.of(path("path", "Project", "projects/")), "Open", values -> io(() -> {
            var candidate = store.open(Path.of(values.get("path")));
            host.selectGame(candidate.metadata().gameId());
            replaceDocument(candidate);
            host.status("Opened " + candidate.path());
        }));
    }

    private void chooseImportGame() {
        menu.showActions("Choose import host", List.of(action("Sonic 1", () -> importForm(GameId.S1)),
                action("Sonic 2", () -> importForm(GameId.S2)), action("Sonic 3 & Knuckles", () -> importForm(GameId.S3K))));
    }

    private void importForm(GameId game) {
        menu.showForm("Import exported level", List.of(path("path", "Level directory / level.json", "exports/editor/")),
                "Import", values -> io(() -> {
                    Path supplied = Path.of(values.get("path")).toAbsolutePath().normalize();
                    Path root = Files.isDirectory(supplied) ? supplied : supplied.getParent();
                    String entry = Files.isDirectory(supplied) ? "level.json" : supplied.getFileName().toString();
                    var module = host.selectGame(game);
                    try (var assets = ModAssetRoot.directory(root.getParent(), root, ModInputLimits.production(), DirectoryAccess.DEVELOPMENT)) {
                        var parsed = ModLevelDefinitionParser.read(assets, new BakedLevelRef(entry));
                        var data = ModZoneLoader.prepareHostData(parsed);
                        module.getModZoneAdapter().validate("openggf-editor", data);
                        Level level = module.getModZoneAdapter().load("openggf-editor", data);
                        var music = parsed.music() instanceof com.openggf.mods.code.ModLevelDefinition.StockMusic stock
                                ? new FullLevelExporter.ExportMusic.Stock(stock.stockId())
                                : new FullLevelExporter.ExportMusic.Track(
                                        ((com.openggf.mods.code.ModLevelDefinition.TrackMusic) parsed.music()).trackKey().modId(),
                                        ((com.openggf.mods.code.ModLevelDefinition.TrackMusic) parsed.music()).trackKey().localName());
                        var metadata = new EditorProjectMetadata(game, parsed.zoneName(), 0, 0, host.sourceFingerprint(),
                                parsed.start().x(), parsed.start().y(), parsed.bounds().minX(), parsed.bounds().maxX(),
                                parsed.bounds().minY(), parsed.bounds().maxY(), music,
                                parsed.hostMetadata().map(m -> m.objectZoneSet()).orElse(null));
                        replaceDocument(EditorProjectDocument.cloneLevel(metadata, level));
                    }
                    host.status("Imported " + supplied + ". Save it as a new editor project.");
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
        io(() -> { store.save(current.path(), current); host.status("Saved " + current.path()); });
    }

    public void saveAs() { saveAsThen(() -> {}); }

    private void saveAsThen(Runnable next) {
        host.finishPendingEdits();
        var current = requireDocument();
        String initial = current.path() == null ? "projects/" + slug(current.metadata().name()) + ".ogfproject" : current.path().toString();
        menu.showForm("Save project as", List.of(path("path", "Project file", initial)), "Save", values -> {
            Path target = projectPath(values.get("path"));
            if (Files.exists(target) && !target.equals(current.path())) {
                menu.showActions("Replace existing project?", List.of(action("Choose another path", () -> saveAsThen(next)),
                        action("Replace this file", () -> persist(target, current, next))));
                menu.setSubtitle(target.toString());
            } else persist(target, current, next);
        });
    }

    private void persist(Path path, EditorProjectDocument current, Runnable next) {
        io(() -> { store.save(path, current); host.status("Saved " + path); next.run(); });
    }

    public void properties() {
        var current = requireDocument();
        var m = current.metadata();
        var fields = new ArrayList<>(List.of(text("name", "Name", m.name()), integer("x", "Start X", m.startX()),
                integer("y", "Start Y", m.startY()), integer("minX", "Camera minimum X", m.minX()), integer("maxX", "Camera maximum X", m.maxX()),
                integer("minY", "Camera minimum Y", m.minY()), integer("maxY", "Camera maximum Y", m.maxY()),
                text("music", "Music: stock:id or owner:key", musicText(m.music()))));
        if (m.gameId() == GameId.S3K) fields.add(text("family", "Object family: S3KL or SKL", m.objectZoneSet().name()));
        menu.showForm("Level properties", fields, "Apply properties", values -> io(() -> {
            var replacement = new EditorProjectMetadata(m.gameId(), values.get("name"), m.sourceZone(), m.sourceAct(), m.sourceFingerprint(),
                    number(values, "x"), number(values, "y"), number(values, "minX"), number(values, "maxX"),
                    number(values, "minY"), number(values, "maxY"), parseMusic(values.get("music")),
                    m.gameId() == GameId.S3K ? ModObjectZoneSet.valueOf(values.get("family").strip().toUpperCase(java.util.Locale.ROOT)) : null);
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
            host.status(newBounds ? "Properties applied. Bounds start a new undo checkpoint." : "Properties applied.");
        }));
        menu.setSubtitle("Bounds limit the camera. Keep terrain through the final viewport for native goals.");
    }

    public void resize() {
        var current = requireDocument();
        menu.showForm("Resize level", List.of(integer("width", "Width in blocks", current.level().getMap().getWidth()),
                integer("height", "Height in blocks", current.level().getMap().getHeight()),
                text("crop", "Allow cropping: yes/no", "no")), "Resize / new undo checkpoint", values -> io(() -> {
            String crop = values.get("crop").strip().toLowerCase(java.util.Locale.ROOT);
            if (!crop.equals("yes") && !crop.equals("no")) throw new IllegalArgumentException("Crop must be yes or no");
            var candidate = current.copyForReplacement();
            candidate.resize(number(values, "width"), number(values, "height"), crop.equals("yes"));
            replaceDocument(candidate);
            host.status("Level resized. A new undo checkpoint has started; save to keep this canvas.");
        }));
    }

    public void playtest(boolean fromCursor) {
        host.finishPendingEdits();
        io(() -> host.playtest(requireDocument(), fromCursor));
    }

    public void export() {
        host.finishPendingEdits();
        var current = requireDocument();
        menu.showForm("Export playable level", List.of(path("path", "New export directory", "exports/editor/"
                + current.metadata().gameId().code() + "/" + slug(current.metadata().name()))), "Validate and export", values -> io(() -> {
            GameModule module = host.currentModule();
            var m = current.metadata();
            var exporter = new FullLevelExporter(new ModLevelExportStagingValidator(module));
            var result = exporter.export(current.level(), EditorProjectRuntimeModule.request(current,
                    Path.of(values.get("path")), m.startX(), m.startY()));
            host.status("Exported " + result.directory());
        }));
    }

    public void requestClose() { beforeReplace(() -> { host.closeDocument(); document = null; }); }

    /** Keep the document alive until the host actually tears down the application. */
    public void requestQuit(Runnable quit) {
        Objects.requireNonNull(quit, "quit");
        beforeReplace(quit, "Cancel quit", "Save and quit", "Quit without saving");
    }

    private void beforeReplace(Runnable next) {
        beforeReplace(next, "Continue editing", "Save and continue", "Discard changes and continue");
    }

    private void beforeReplace(Runnable next, String cancel, String save, String discard) {
        host.finishPendingEdits();
        if (document == null || !document.isModified()) { next.run(); return; }
        menu.showActions("Unsaved project", List.of(action(cancel, () -> {}),
                action(save, () -> {
                    if (document.path() == null) saveAsThen(next);
                    else { save(); next.run(); }
                }), action(discard, next)));
        menu.setSubtitle(document.metadata().name());
    }

    private EditorProjectDocument requireDocument() {
        if (document == null) throw new IllegalStateException("Open or create a project first");
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
        if (text.isBlank()) throw new IllegalArgumentException("Choose a project file");
        return Path.of(text.endsWith(".ogfproject") ? text : text + ".ogfproject").toAbsolutePath().normalize();
    }
    private static String slug(String text) {
        String slug = text.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-").replaceAll("^-|-$", "");
        return slug.isBlank() ? "level" : slug;
    }
    private static int number(Map<String, String> values, String key) {
        try { return Integer.parseInt(values.get(key).strip()); }
        catch (NumberFormatException failure) { throw new IllegalArgumentException("Invalid integer for " + key); }
    }
    private static FullLevelExporter.ExportMusic parseMusic(String text) {
        String[] key = text.strip().split(":", 2);
        if (key.length != 2) throw new IllegalArgumentException("Music must be stock:id or owner:key");
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
    private static EditorProjectMenu.Field text(String key, String label, String value) { return new EditorProjectMenu.Field(key, label, value, MenuTextEditor.Mode.TEXT); }
    private static EditorProjectMenu.Field integer(String key, String label, int value) { return new EditorProjectMenu.Field(key, label, Integer.toString(value), MenuTextEditor.Mode.INTEGER); }
    private static EditorProjectMenu.Field path(String key, String label, String value) { return new EditorProjectMenu.Field(key, label, value, MenuTextEditor.Mode.PATH); }
    private static void io(IoAction action) {
        try { action.run(); }
        catch (IOException failure) { throw new IllegalStateException(failure.getMessage(), failure); }
    }
    @FunctionalInterface private interface IoAction { void run() throws IOException; }
}
