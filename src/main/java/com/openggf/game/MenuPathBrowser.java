package com.openggf.game;

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
    private static final int ROWS = 7;
    private static final int ROW_TOP = 70;
    private static final int ROW_HEIGHT = 14;
    private static final int SHORTCUT_Y = 187;
    private static final int SHORTCUT_H = 14;
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
    private int frame;
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
        this(initial, false, "BROWSE PATH", "Choose a file or folder");
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
                return "Temporary files" + (relative.isEmpty() ? "" : " / " + relative.replace('\\', '/'));
            }
        }
        if (normalized.getFileName() == null) return "Computer";
        Path parent = normalized.getParent();
        String location = parent == null || parent.getFileName() == null ? "Computer" : parent.getFileName().toString();
        return location + " / " + normalized.getFileName();
    }
    /** Preserve the location anchor and nearest folders; shorten names in their middle. */
    static String breadcrumb(Path path, int maxWidth) {
        String[] parts = MenuDetailsScreen.readable(friendlyPath(path).replace('\\', '/')).split("\\s*/\\s*");
        int cells = Math.max(0, maxWidth / MenuChrome.COMPACT_ADVANCE);
        String full = String.join(" / ", parts);
        if (full.length() <= cells) return full;
        if (parts.length < 2) return elideMiddle(full, cells);
        String anchor = parts[0];
        String tail = parts[parts.length - 1];
        if (parts.length == 2) {
            if (anchor.length() + 8 > cells) return elideMiddle(full, cells);
            return anchor + " / " + elideMiddle(tail, cells - anchor.length() - 3);
        }
        for (int i = parts.length - 2; i > 0; i--) {
            String candidate = parts[i] + " / " + tail;
            String shown = anchor + (i == 1 ? " / " : " / ... / ") + candidate;
            if (shown.length() <= cells) tail = candidate;
            else break;
        }
        String prefix = anchor + " / ... / ";
        // Very narrow callers still receive a bounded string rather than a clipped anchor.
        if (prefix.length() + 5 > cells) return elideMiddle(full, cells);
        return prefix + elideMiddle(tail, cells - prefix.length());
    }
    private static String elideMiddle(String text, int cells) {
        if (text.length() <= cells) return text;
        if (cells < 5) return MenuChrome.fitCompact(text, Math.max(0, cells) * MenuChrome.COMPACT_ADVANCE);
        int end = Math.max(2, (cells - 3) / 3);
        return text.substring(0, cells - 3 - end) + "..." + text.substring(text.length() - end);
    }
    private int shortcutX(int index) { return 10 + index * (width - 14) / 3; }
    private int shortcutWidth() { return (width - 14) / 3 - 6; }
    private Listing read(Path directory) {
        try (var stream = Files.list(directory)) {
            List<Entry> found = stream.map(path -> new Entry(path, Files.isDirectory(path)))
                    .filter(entry -> !foldersOnly || entry.directory())
                    .sorted(Comparator.comparing(Entry::directory).reversed()
                            .thenComparing(entry -> filesOnly && likelyRom(entry.path()) ? 0 : 1)
                            .thenComparing(e -> e.path().getFileName().toString(), String.CASE_INSENSITIVE_ORDER)).toList();
            return new Listing(directory, found, null);
        } catch (IOException | SecurityException e) { return new Listing(directory, List.of(), "Cannot read folder"); }
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
        } catch (Exception e) { error = "Cannot read path: " + (e.getCause() == null ? e.getMessage() : e.getCause().getMessage()); }
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
        if (next == null || !Files.isDirectory(next)) { error = "Folder is not available"; return false; }
        refresh(next);
        MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
        return true;
    }
    public void update(InputHandler input) {
        MasterTitlePointer mouse = pointer;
        pointer = null;
        if (input == null || selected != null || cancelled) return;
        menuInput = input;
        frame++;
        if (details != null) { if (details.update(input) || mouse != null && mouse.backClick()) details = null; return; }
        if (MenuInput.textBack(input) || mouse != null && (mouse.backClick()
                || mouse.click() && mouse.over(0, MenuChrome.FOOTER_Y, width, MenuChrome.FOOTER_H))) {
            cancel(); MenuFeedback.emit(MenuFeedback.Cue.CANCEL); return;
        }
        consumeLoad();
        if (MenuInput.textDetails(input)) {
            String full = directory == null ? "" : load != null || row < prefixRows() || row - prefixRows() >= entries.size()
                    ? directory.toString() : entries.get(row - prefixRows()).path().toString();
            details = new MenuDetailsScreen("PATH", error == null ? full : error + "\n" + full); return;
        }
        // Shortcuts remain available while a slow folder is being read.
        if (mouse != null && mouse.click()) {
            for (int i = 0; i < 3; i++) {
                if (mouse.over(shortcutX(i), SHORTCUT_Y, shortcutWidth(), SHORTCUT_H)) {
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
            int first = row / ROWS * ROWS;
            for (int i = first; i < Math.min(count, first + ROWS); i++) {
                if (mouse.over(12, ROW_TOP + (i - first) * ROW_HEIGHT, width - 36, ROW_HEIGHT) && (mouse.moved() || mouse.click())) {
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
    public void render(PixelFont font, int width) {
        if (font == null) return;
        if (details != null) { details.render(font, width); return; }
        font.beginMegaBatch();
        try { renderContents(font, Math.max(320, width)); }
        finally { font.endMegaBatch(); }
    }
    private void renderContents(PixelFont font, int width) {
        this.width = width;
        MenuChrome.page(font, width, title, null, null);
        MenuChrome.panel(font, 8, 34, width - 16, 18);
        MenuChrome.icon(font, MenuChrome.Icon.FOLDER, 14, 39, MenuChrome.GOLD);
        MenuChrome.compact(font, directory == null ? "Location" : breadcrumb(directory, width - 42),
                27, 39, MenuChrome.CYAN);
        MenuChrome.compact(font, "CONTENTS", 10, 57, MenuChrome.MUTED);
        if (load == null && directory != null && error == null) {
            String count = entries.size() + (entries.size() == 1 ? " ITEM" : " ITEMS");
            MenuChrome.compact(font, count, width - 10 - MenuChrome.compactWidth(count), 57, MenuChrome.DIM);
        }
        MenuChrome.panel(font, 8, ROW_TOP - 3, width - 16, ROWS * ROW_HEIGHT + 5);
        if (load != null || directory == null) {
            emptyState(font, load != null ? "Loading folder..." : "Cannot open path",
                    load != null ? "Places below remain available." : "Open Details for the full path error.", MenuChrome.AMBER);
        } else {
            int total = entries.size() + prefixRows();
            int first = row / ROWS * ROWS;
            for (int i = first; i < Math.min(total, first + ROWS); i++) renderRow(font, i, ROW_TOP + (i - first) * ROW_HEIGHT);
            MenuChrome.scrollbar(font, width - 16, ROW_TOP, ROWS * ROW_HEIGHT - 2,
                    Math.min(first, Math.max(0, total - ROWS)), ROWS, total);
            if (entries.isEmpty()) {
                emptyState(font, error != null ? error : foldersOnly ? "No subfolders" : "Folder is empty",
                        foldersOnly ? "Use this folder or choose another." : "Try another folder or a place below.",
                        error == null ? MenuChrome.TEXT : MenuChrome.AMBER);
            }
        }
        MenuChrome.compact(font, MenuChrome.fitCompact(error == null ? instruction : error, width - 20), 10, 175,
                error == null ? MenuChrome.MUTED : MenuChrome.AMBER);
        String[] shortcuts = {"Home", "Downloads", "Last folder"};
        for (int i = 0; i < shortcuts.length; i++) {
            MenuChrome.button(font, shortcutX(i), SHORTCUT_Y, shortcutWidth(), SHORTCUT_H,
                    shortcuts[i], shortcut == i, false, frame);
            if (shortcutPath(i) == null) {
                MenuChrome.fill(font, shortcutX(i) + 1, SHORTCUT_Y + 1, shortcutWidth() - 2, SHORTCUT_H - 2, MenuChrome.INK, .55f);
            }
        }
        MenuChrome.footer(font, width, null, List.of(MenuChrome.leftRight(menuInput, "PLACES"),
                MenuChrome.hint(MenuChrome.detailsKey(menuInput), "DETAILS"),
                MenuChrome.hint(MenuChrome.confirmKey(menuInput), confirmLabel()),
                MenuChrome.hint(MenuChrome.backKey(menuInput), "BACK")), MenuChrome.controller(menuInput));
    }
    private void renderRow(PixelFont font, int index, int y) {
        boolean focused = index == row && shortcut < 0;
        int rowWidth = width - 36;
        if (focused) MenuChrome.focus(font, 12, y + 1, rowWidth, ROW_HEIGHT - 2, frame);
        boolean useFolder = !filesOnly && index == 0;
        boolean parent = index == prefixRows() - 1;
        Entry entry = index < prefixRows() ? null : entries.get(index - prefixRows());
        boolean folder = entry != null && entry.directory();
        boolean rom = filesOnly && entry != null && !folder && likelyRom(entry.path());
        float[] color = focused ? MenuChrome.WHITE : entry == null || folder || rom ? MenuChrome.TEXT : MenuChrome.MUTED;
        String name = useFolder ? "Use this folder" : parent ? "Parent folder" : entry.path().getFileName().toString();
        if (parent) MenuChrome.triangle(font, 15, y + 4, 4, 'U', MenuChrome.CYAN, 1);
        else MenuChrome.icon(font, useFolder ? MenuChrome.Icon.CHECK : folder ? MenuChrome.Icon.FOLDER : MenuChrome.Icon.FILE,
                15, y + 4, useFolder || folder ? MenuChrome.GOLD : rom ? MenuChrome.CYAN : MenuChrome.DIM);
        String tag = parent ? directory.getParent() == null ? "ROOT" : "UP" : useFolder ? "USE" : rom ? "ROM?" : null;
        int reserved = tag == null ? 12 : MenuChrome.tagWidth(tag) + 8;
        MenuChrome.compact(font, MenuChrome.fitCompact(MenuDetailsScreen.readable(name), rowWidth - 17 - reserved),
                27, y + 4, color);
        if (tag != null) MenuChrome.tag(font, 12 + rowWidth - MenuChrome.tagWidth(tag) - 3, y + 2, tag,
                parent || rom ? MenuChrome.CYAN : MenuChrome.GOLD);
        else if (folder) MenuChrome.triangle(font, 12 + rowWidth - 7, y + 4, 3, 'R', MenuChrome.DIM, 1);
    }
    private void emptyState(PixelFont font, String heading, String detail, float[] color) {
        int y = ROW_TOP + 49;
        MenuChrome.compact(font, heading, (width - MenuChrome.compactWidth(heading)) / 2, y, color);
        MenuChrome.compact(font, detail, (width - MenuChrome.compactWidth(detail)) / 2, y + 15, MenuChrome.MUTED);
    }
    private String confirmLabel() {
        if (shortcut >= 0) return "OPEN";
        if (load != null || directory == null) return "CHOOSE";
        if (!filesOnly && row == 0) return "USE";
        if (row < prefixRows() || entries.get(row - prefixRows()).directory()) return "OPEN";
        return "SELECT";
    }
}
