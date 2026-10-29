package com.openggf.game;

import com.openggf.i18n.UiMessages;
import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.graphics.PixelFont;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Local controller path picker; browsing never creates or modifies files. */
public final class MenuPathBrowser {
    private static final int ROWS = 9;
    private static final int ROW_HEIGHT = 13;
    private static volatile Path lastDirectory;
    private Path directory;
    private final Path previousDirectory = lastDirectory;
    private final boolean filesOnly;
    private final boolean foldersOnly;
    private final String title;
    private final String instruction;
    private MasterTitlePointer pointer;
    private InputHandler menuInput;
    private int width = 320;
    private int shortcut = -1;
    private record Entry(Path path, boolean directory) { }
    private record Listing(Path directory, List<Entry> entries, String error) { }
    private List<Entry> entries = List.of();
    private MenuLoadTask<Listing> load;
    private int row;
    private String error;
    private String selected;
    private boolean cancelled;
    private MenuDetailsScreen details;

    public MenuPathBrowser(String initial) {
        this(initial, false, UiMessages.text("menu.path.browse_path_caps"), UiMessages.text("menu.path.choose_a_file_or_folder"));
    }
    public MenuPathBrowser(String initial, boolean filesOnly, String title, String instruction) {
        this(initial, filesOnly, false, title, instruction);
    }
    public static MenuPathBrowser folder(String initial, String title, String instruction) {
        return new MenuPathBrowser(initial, false, true, title, instruction);
    }
    private MenuPathBrowser(String initial, boolean filesOnly, boolean foldersOnly, String title, String instruction) {
        this.filesOnly = filesOnly;
        this.foldersOnly = foldersOnly;
        this.title = title;
        this.instruction = instruction;
        load = new MenuLoadTask<>(() -> {
            Path candidate = Path.of(initial == null || initial.isBlank() ? System.getProperty("user.home") : initial)
                    .toAbsolutePath().normalize();
            while (candidate != null && !Files.isDirectory(candidate)) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                candidate = candidate.getParent();
            }
            return read(candidate == null ? Path.of(".").toAbsolutePath().normalize() : candidate);
        });
    }
    static boolean likelyRom(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".gen") || name.endsWith(".bin") || name.endsWith(".md") || name.endsWith(".smd");
    }
    static String friendlyPath(Path path) {
        if (path == null) return "";
        Path normalized = path.toAbsolutePath().normalize();
        Path home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        if (normalized.startsWith(home))
            return "~" + (normalized.equals(home) ? "" : "/" + home.relativize(normalized));
        Path temporary = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        for (Path root : List.of(temporary, Path.of("/tmp").toAbsolutePath(), Path.of("/var/tmp").toAbsolutePath())) {
            if (normalized.startsWith(root)) {
                String relative = root.relativize(normalized).toString();
                return UiMessages.text("menu.path.temporary_location", java.util.Map.of("suffix", relative.isEmpty() ? "" : " / " + relative.replace('\\', '/')));
            }
        }
        if (normalized.getFileName() == null) return UiMessages.text("menu.path.computer");
        Path parent = normalized.getParent();
        String location = parent == null || parent.getFileName() == null ? UiMessages.text("menu.path.computer") : parent.getFileName().toString();
        return location + " / " + normalized.getFileName();
    }
    private Listing read(Path directory) {
        try (var stream = Files.list(directory)) {
            List<Entry> found = stream.map(path -> new Entry(path, Files.isDirectory(path)))
                    .filter(entry -> !foldersOnly || entry.directory())
                    .sorted(Comparator.comparing(Entry::directory).reversed()
                            .thenComparing(entry -> filesOnly && likelyRom(entry.path()) ? 0 : 1)
                            .thenComparing(e -> e.path().getFileName().toString(), String.CASE_INSENSITIVE_ORDER)).toList();
            return new Listing(directory, found, null);
        } catch (IOException | SecurityException e) { return new Listing(directory, List.of(), UiMessages.text("menu.path.cannot_read_folder")); }
    }
    private void refresh(Path next) {
        if (load != null) load.cancel();
        row = 0;
        error = null;
        load = new MenuLoadTask<>(() -> read(next));
    }
    private void consumeLoad() {
        if (load == null || !load.done()) return;
        try {
            Listing result = load.result();
            directory = result.directory(); entries = result.entries(); error = result.error();
            if (error == null) lastDirectory = directory;
        } catch (Exception e) { error = UiMessages.text("menu.path.read_error", java.util.Map.of("error", String.valueOf(e.getCause() == null ? e.getMessage() : e.getCause().getMessage()))); }
        load = null;
    }
    public boolean loading() { return load != null; }
    /** Stop outstanding reads when the owning menu closes. */
    public void cancel() {
        if (load != null) { load.cancel(); load = null; }
        selected = null;
        details = null;
        pointer = null;
        cancelled = true;
    }
    public void pointer(int x, int y, boolean moved, boolean click, boolean backClick, int wheel) {
        pointer = new MasterTitlePointer(x, y, moved, click, backClick, wheel);
    }
    private int prefixRows() { return filesOnly ? 1 : 2; }
    private Path shortcutPath(int index) {
        Path home = Path.of(System.getProperty("user.home"));
        return switch (index) { case 0 -> home; case 1 -> home.resolve("Downloads"); default -> previousDirectory; };
    }
    private boolean openShortcut(int index) {
        Path next = shortcutPath(index);
        if (next == null || !Files.isDirectory(next)) { error = UiMessages.text("menu.path.folder_is_not_available"); return false; }
        refresh(next);
        MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
        return true;
    }
    public void update(InputHandler input) {
        MasterTitlePointer mouse = pointer;
        pointer = null;
        if (input == null || selected != null || cancelled) return;
        menuInput = input;
        if (details != null) { if (details.update(input) || mouse != null && mouse.backClick()) details = null; return; }
        if (MenuInput.textBack(input) || mouse != null && (mouse.backClick()
                || mouse.click() && mouse.over(0, MenuChrome.FOOTER_Y, width, MenuChrome.FOOTER_H))) {
            cancel(); MenuFeedback.emit(MenuFeedback.Cue.CANCEL); return;
        }
        consumeLoad();
        if (MenuInput.textDetails(input)) {
            String full = directory == null ? "" : load != null || row < prefixRows() || row - prefixRows() >= entries.size()
                    ? directory.toString() : entries.get(row - prefixRows()).path().toString();
            details = new MenuDetailsScreen(UiMessages.text("menu.path.path_caps"), error == null ? full : error + "\n" + full); return;
        }
        // Shortcuts remain available while a slow folder is being read.
        if (mouse != null && mouse.click()) {
            for (int i = 0; i < 3; i++) {
                if (mouse.over(8 + i * (width - 16) / 3, 187, (width - 16) / 3 - 3, 14)) {
                    shortcut = i; openShortcut(i); return;
                }
            }
        }
        if (MenuInput.textLeft(input) || MenuInput.textRight(input)) {
            boolean left = MenuInput.textLeft(input);
            shortcut = shortcut < 0 ? (left ? 2 : 0) : Math.floorMod(shortcut + (left ? -1 : 1), 3);
            MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE);
        }
        if (shortcut >= 0 && MenuInput.textAccept(input)) { openShortcut(shortcut); return; }
        if (load != null || directory == null) return;
        int count = entries.size() + prefixRows();
        boolean clicked = false;
        if (mouse != null) {
            if (mouse.wheel() != 0) { row = Math.clamp(row - mouse.wheel(), 0, count - 1); shortcut = -1; }
            int first = row / visibleRows() * visibleRows();
            for (int i = first; i < Math.min(count, first + visibleRows()); i++) {
                if (mouse.over(8, 52 + (i - first) * rowHeight(), width - 16, rowHeight()) && (mouse.moved() || mouse.click())) {
                    row = i; shortcut = -1; clicked = mouse.click();
                }
            }
        }
        if (MenuInput.textUp(input)) { if (shortcut < 0) row = Math.floorMod(row - 1, count); shortcut = -1; MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE); }
        if (MenuInput.textDown(input)) { if (shortcut < 0) row = (row + 1) % count; shortcut = -1; MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE); }
        if (MenuInput.textAccept(input) || clicked) {
            if (!filesOnly && row == 0) selected = directory.toString();
            else if (row == prefixRows() - 1) { if (directory.getParent() != null) refresh(directory.getParent()); }
            else {
                Entry entry = entries.get(row - prefixRows());
                if (entry.directory()) refresh(entry.path()); else selected = entry.path().toString();
            }
            MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
        }
    }
    public String selected() { return selected; }
    public boolean cancelled() { return cancelled; }
    private static int rowHeight() { return Math.max(ROW_HEIGHT, MenuChrome.compactLineHeight() + 4); }
    private static int visibleRows() { return Math.min(ROWS, Math.max(1, (169 - 52) / rowHeight())); }

    public void render(PixelFont font, int width) {
        if (font == null) return;
        if (details != null) { details.render(font, width); return; }
        font.beginMegaBatch();
        try { renderContents(font, Math.max(320, width)); }
        finally { font.endMegaBatch(); }
    }
    private void renderContents(PixelFont font, int width) {
        this.width = width;
        MenuChrome.background(font, width);
        MenuChrome.header(font, width, title, null);
        String path = friendlyPath(directory);
        path = MenuChrome.fitCompact(path, width - 20);
        MenuChrome.compact(font, MenuDetailsScreen.readable(path), 10, 35, MenuChrome.CYAN);
        if (load != null || directory == null) {
            MenuChrome.compact(font, load != null ? UiMessages.text("menu.path.loading_folder") : UiMessages.text("menu.path.cannot_open_path"), 10, 60, MenuChrome.AMBER);
        } else {
            int first = row / visibleRows() * visibleRows();
            for (int i = first; i < Math.min(entries.size() + prefixRows(), first + visibleRows()); i++) {
                int y = 52 + (i - first) * rowHeight();
                if (i == row && shortcut < 0) MenuStyle.focusLabel(font, 8, y, width - 16, rowHeight());
                String name = !filesOnly && i == 0 ? UiMessages.text("menu.path.use_this_folder") : i == prefixRows() - 1 ? UiMessages.text("menu.path.parent_folder") :
                        (entries.get(i - prefixRows()).directory() ? "/ " : "") + entries.get(i - prefixRows()).path().getFileName();
                boolean rom = filesOnly && i >= prefixRows() && !entries.get(i - prefixRows()).directory() && likelyRom(entries.get(i - prefixRows()).path());
                MenuChrome.compact(font, MenuChrome.fitCompact(MenuDetailsScreen.readable(name), width - (rom ? 58 : 26)), 13, y + 3, MenuChrome.TEXT);
                if (rom) MenuChrome.compact(font, UiMessages.text("menu.path.rom_caps"), width - 40, y + 3, MenuChrome.CYAN);
            }
        }
        String status = error == null ? instruction : error;
        MenuChrome.compact(font, MenuChrome.fitCompact(status, width - 20), 10, 175,
                error == null ? MenuChrome.DIM : MenuChrome.AMBER);
        String[] shortcuts = {UiMessages.text("menu.path.home"), UiMessages.text("menu.path.downloads"), UiMessages.text("menu.path.last_folder")};
        for (int i = 0; i < shortcuts.length; i++) {
            int x = 8 + i * (width - 16) / 3, w = (width - 16) / 3 - 3;
            MenuChrome.panel(font, x, 187, w, 14);
            if (shortcut == i) MenuChrome.outline(font, x, 187, w, 14, MenuChrome.GOLD, 1, 1);
            MenuChrome.compact(font, MenuChrome.fitCompact(shortcuts[i], w - 6), x + 3, 190,
                    shortcutPath(i) == null ? MenuChrome.DIM : MenuChrome.TEXT);
        }
        MenuChrome.footer(font, width, null, List.of(MenuChrome.hint(MenuChrome.detailsKey(menuInput), UiMessages.text("menu.path.details_caps")),
                MenuChrome.hint(MenuChrome.confirmKey(menuInput), shortcut < 0 ? UiMessages.text("menu.path.choose_caps") : UiMessages.text("menu.path.open_caps")),
                MenuChrome.hint(MenuChrome.backKey(menuInput), UiMessages.text("menu.path.back_caps"))), MenuChrome.controller(menuInput));
    }
}
