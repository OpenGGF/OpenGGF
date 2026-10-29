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
    private String pendingPath;

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
        pendingPath = initial == null ? "" : initial;
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
    /** Preserve the location anchor and nearest folders; shorten names in their middle. */
    static String breadcrumb(Path path, int maxWidth) {
        String[] parts = MenuDetailsScreen.readable(friendlyPath(path).replace('\\', '/')).split("\\s*/\\s*");
        maxWidth = Math.max(0, maxWidth);
        String full = String.join(" / ", parts);
        if (MenuChrome.compactWidth(full) <= maxWidth) return full;
        MenuStyle.reportEllipsis(full, maxWidth, MenuStyle.COMPACT);
        if (parts.length < 2) return elideMiddle(full, maxWidth);
        String anchor = parts[0];
        String tail = parts[parts.length - 1];
        // An elided name keeps at least about five cells of lettering.
        int shortest = 5 * MenuChrome.COMPACT_ADVANCE;
        if (parts.length == 2) {
            String head = anchor + " / ";
            if (MenuChrome.compactWidth(head) + shortest > maxWidth) return elideMiddle(full, maxWidth);
            return head + elideMiddle(tail, maxWidth - MenuChrome.compactWidth(head));
        }
        for (int i = parts.length - 2; i > 0; i--) {
            String candidate = parts[i] + " / " + tail;
            String shown = anchor + (i == 1 ? " / " : " / ... / ") + candidate;
            if (MenuChrome.compactWidth(shown) <= maxWidth) tail = candidate;
            else break;
        }
        String prefix = anchor + " / ... / ";
        // Very narrow callers still receive a bounded string rather than a clipped anchor.
        if (MenuChrome.compactWidth(prefix) + shortest > maxWidth) return elideMiddle(full, maxWidth);
        return prefix + elideMiddle(tail, maxWidth - MenuChrome.compactWidth(prefix));
    }
    /** Keep both ends of a name, about a third of the room for its end. */
    private static String elideMiddle(String text, int maxWidth) {
        return MenuStyle.elideMiddle(text, maxWidth, MenuStyle.COMPACT, 3);
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
        } catch (IOException | SecurityException e) { return new Listing(directory, List.of(), UiMessages.text("menu.path.cannot_read_folder")); }
    }
    private void refresh(Path next) {
        if (load != null) load.cancel();
        row = 0;
        pendingPath = next.toString();
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
        update(input, MenuInput.pointer(input, width, 224));
    }

    void update(InputHandler input, MenuInput.Pointer localPointer) {
        MasterTitlePointer mouse = pointer;
        pointer = null;
        if (input == null || selected != null || cancelled) return;
        menuInput = input;
        frame++;
        if (details != null) { if (details.update(input, localPointer) || mouse != null && mouse.backClick()) details = null; return; }
        if (MenuInput.textBack(input) || mouse != null && (mouse.backClick()
                || mouse.click() && mouse.over(0, MenuChrome.FOOTER_Y, width, MenuChrome.FOOTER_H))) {
            cancel(); MenuFeedback.emit(MenuFeedback.Cue.CANCEL); return;
        }
        consumeLoad();
        if (MenuInput.textDetails(input)) {
            String full = directory == null ? pendingPath : load != null || row < prefixRows() || row - prefixRows() >= entries.size()
                    ? directory.toString() : entries.get(row - prefixRows()).path().toString();
            if (shortcut >= 0 && shortcutPath(shortcut) != null) full = shortcutPath(shortcut).toString();
            details = new MenuDetailsScreen(UiMessages.text("menu.path.path_caps"), title + "\n" + instruction
                    + "\n" + (load == null ? "" : pendingPath + "\n") + (error == null ? full : error + "\n" + full)
                    + "\n" + UiMessages.text("menu.path.use_this_folder") + "\n" + UiMessages.text("menu.path.parent_folder")
                    + "\n" + UiMessages.text("menu.path.home") + "\n" + UiMessages.text("menu.path.downloads")
                    + "\n" + UiMessages.text("menu.path.last_folder") + "\n" + UiMessages.text("menu.path.rom_caps")
                    + "\n" + emptyText()); return;
        }
        // Shortcuts remain available while a slow folder is being read.
        if (mouse != null && mouse.click()) {
            for (int i = 0; i < 3; i++) {
                if (mouse.over(shortcutX(i), shortcutTop(), shortcutWidth(), shortcutHeight())) {
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
                if (mouse.over(12, listTop() + (i - first) * rowHeight(), width - 36, rowHeight()) && (mouse.moved() || mouse.click())) {
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
    private static int shortcutHeight() { return Math.max(SHORTCUT_H, MenuChrome.compactLineHeight() + 6); }
    private static int shortcutTop() { return SHORTCUT_Y - (shortcutHeight() - SHORTCUT_H); }
    private static int statusTop() { return shortcutTop() - MenuChrome.compactLineHeight() - 4; }
    private static int listTop() { return ROW_TOP + Math.max(0, MenuChrome.compactLineHeight() - 10); }
    private static int visibleRows() { return Math.min(ROWS, Math.max(1, (statusTop() - 6 - listTop()) / rowHeight())); }

    public void render(PixelFont font, int width) {
        if (font == null) return;
        if (details != null) { details.render(font, width); return; }
        font.beginMegaBatch();
        try { renderContents(font, Math.max(320, width)); }
        finally { font.endMegaBatch(); }
    }
    private void renderContents(PixelFont font, int width) {
        this.width = width;
        MenuChrome.page(font, width, MenuStyle.pageText(MenuStyle.language(font), title, width - 18, 1, frame), null, null);
        MenuChrome.panel(font, 8, 34, width - 16, 18);
        MenuChrome.icon(font, MenuChrome.Icon.FOLDER, 14, 39, MenuChrome.GOLD);
        MenuChrome.compact(font, directory == null ? UiMessages.text("menu.path.location") : breadcrumb(directory, width - 42),
                27, 39, MenuChrome.CYAN);
        MenuChrome.compact(font, UiMessages.text("menu.path.contents"), 10, 57, MenuChrome.MUTED);
        if (load == null && directory != null && error == null) {
            String count = UiMessages.text("menu.path.item_count", java.util.Map.of("count", entries.size()));
            MenuChrome.compact(font, count, width - 10 - MenuChrome.compactWidth(count), 57, MenuChrome.DIM);
        }
        MenuChrome.panel(font, 8, listTop() - 3, width - 16, visibleRows() * rowHeight() + 5);
        if (load != null || directory == null) {
            emptyState(font, load != null ? UiMessages.text("menu.path.loading_folder") : UiMessages.text("menu.path.cannot_open_path"),
                    load != null ? UiMessages.text("menu.path.places_below_remain_available") : UiMessages.text("menu.path.open_details_for_the_full_path_error"), MenuChrome.AMBER);
        } else {
            int total = entries.size() + prefixRows();
            int first = row / visibleRows() * visibleRows();
            for (int i = first; i < Math.min(total, first + visibleRows()); i++) renderRow(font, i, listTop() + (i - first) * rowHeight());
            MenuChrome.scrollbar(font, width - 16, listTop(), visibleRows() * rowHeight() - 2,
                    Math.min(first, Math.max(0, total - visibleRows())), visibleRows(), total);
            if (entries.isEmpty()) {
                emptyState(font, error != null ? error : foldersOnly ? UiMessages.text("menu.path.no_subfolders") : UiMessages.text("menu.path.folder_is_empty"),
                        foldersOnly ? UiMessages.text("menu.path.use_this_folder_or_choose_another") : UiMessages.text("menu.path.try_another_folder_or_a_place_below"),
                        error == null ? MenuChrome.TEXT : MenuChrome.AMBER);
            }
        }
        MenuChrome.compact(font, MenuStyle.pageText(MenuStyle.language(font), error == null ? instruction : error, width - 20, MenuStyle.COMPACT, frame), 10, statusTop(),
                error == null ? MenuChrome.MUTED : MenuChrome.AMBER);
        String[] shortcuts = {UiMessages.text("menu.path.home"), UiMessages.text("menu.path.downloads"), UiMessages.text("menu.path.last_folder")};
        for (int i = 0; i < shortcuts.length; i++) {
            MenuChrome.button(font, shortcutX(i), shortcutTop(), shortcutWidth(), shortcutHeight(),
                    MenuStyle.pageText(MenuStyle.language(font), shortcuts[i], shortcutWidth() - 8, MenuStyle.COMPACT, frame), shortcut == i, false, frame);
            if (shortcutPath(i) == null) {
                MenuChrome.fill(font, shortcutX(i) + 1, shortcutTop() + 1, shortcutWidth() - 2, shortcutHeight() - 2, MenuChrome.INK, .55f);
            }
        }
        MenuChrome.footer(font, width, null, List.of(MenuChrome.leftRight(menuInput, UiMessages.text("menu.path.places")),
                MenuChrome.hint(MenuChrome.confirmKey(menuInput), confirmLabel()),
                MenuChrome.hint(MenuChrome.backKey(menuInput), UiMessages.text("menu.path.back_caps")),
                MenuChrome.hint(MenuChrome.detailsKey(menuInput), UiMessages.text("menu.path.details_caps"))), MenuChrome.controller(menuInput), frame);
    }
    private void renderRow(PixelFont font, int index, int y) {
        boolean focused = index == row && shortcut < 0;
        int rowWidth = width - 36;
        if (focused) MenuChrome.focus(font, 12, y + 1, rowWidth, rowHeight() - 2, frame);
        boolean useFolder = !filesOnly && index == 0;
        boolean parent = index == prefixRows() - 1;
        Entry entry = index < prefixRows() ? null : entries.get(index - prefixRows());
        boolean folder = entry != null && entry.directory();
        boolean rom = filesOnly && entry != null && !folder && likelyRom(entry.path());
        float[] color = focused ? MenuChrome.WHITE : entry == null || folder || rom ? MenuChrome.TEXT : MenuChrome.MUTED;
        String name = useFolder ? UiMessages.text("menu.path.use_this_folder") : parent ? UiMessages.text("menu.path.parent_folder") : entry.path().getFileName().toString();
        if (parent) MenuChrome.triangle(font, 15, y + 4, 4, 'U', MenuChrome.CYAN, 1);
        else MenuChrome.icon(font, useFolder ? MenuChrome.Icon.CHECK : folder ? MenuChrome.Icon.FOLDER : MenuChrome.Icon.FILE,
                15, y + 4, useFolder || folder ? MenuChrome.GOLD : rom ? MenuChrome.CYAN : MenuChrome.DIM);
        // The ROM? tag distinguishes rows only when the visible files mix likely ROMs and other files;
        // otherwise the cyan file icon already marks them and the tag stays on the focused row.
        String tag = parent ? directory.getParent() == null ? UiMessages.text("menu.path.root") : UiMessages.text("menu.path.up") : useFolder ? UiMessages.text("menu.path.use")
                : rom && (focused || romTagsVary(index)) ? UiMessages.text("menu.path.rom_caps") : null;
        int reserved = tag == null ? 12 : MenuChrome.tagWidth(tag) + 8;
        MenuChrome.compact(font, MenuStyle.pageText(MenuStyle.language(font), MenuDetailsScreen.readable(name), rowWidth - 17 - reserved, MenuStyle.COMPACT, frame),
                27, y + 4, color);
        if (tag != null) MenuChrome.tag(font, 12 + rowWidth - MenuChrome.tagWidth(tag) - 3, y + 2, tag,
                parent || rom ? MenuChrome.CYAN : MenuChrome.GOLD);
        else if (folder) MenuChrome.triangle(font, 12 + rowWidth - 7, y + 4, 3, 'R', MenuChrome.DIM, 1);
    }
    /** Whether the page holding {@code index} shows both likely-ROM files and other files. */
    private boolean romTagsVary(int index) {
        int first = index / visibleRows() * visibleRows(), end = Math.min(entries.size() + prefixRows(), first + visibleRows());
        boolean roms = false, others = false;
        for (int i = Math.max(first, prefixRows()); i < end; i++) {
            Entry entry = entries.get(i - prefixRows());
            if (entry.directory()) continue;
            if (likelyRom(entry.path())) roms = true; else others = true;
        }
        return roms && others;
    }
    private void emptyState(PixelFont font, String heading, String detail, float[] color) {
        int y = listTop() + Math.max(22, visibleRows() * rowHeight() / 2);
        heading = MenuStyle.pageText(MenuStyle.language(font), heading, width - 40, MenuStyle.COMPACT, frame);
        MenuChrome.compact(font, heading, (width - MenuChrome.compactWidth(heading)) / 2, y, color);
        List<String> lines = MenuStyle.wrap(detail, width - 40, MenuStyle.COMPACT);
        int pitch = Math.max(15, MenuChrome.compactLineHeight() + 2);
        int capacity = Math.max(1, Math.min(2, (statusTop() - y - 3) / pitch - 1));
        int first = Math.floorMod(frame / 90, Math.max(1, (lines.size() + capacity - 1) / capacity)) * capacity;
        for (int i = first; i < Math.min(first + capacity, lines.size()); i++) {
            String line = lines.get(i);
            MenuChrome.compact(font, line, (width - MenuChrome.compactWidth(line)) / 2, y + (i - first + 1) * pitch, MenuChrome.MUTED);
        }
    }
    private String emptyText() {
        if (load != null) return UiMessages.text("menu.path.loading_folder") + "\n" + UiMessages.text("menu.path.places_below_remain_available");
        if (directory == null) return UiMessages.text("menu.path.cannot_open_path") + "\n" + UiMessages.text("menu.path.open_details_for_the_full_path_error");
        if (!entries.isEmpty()) return "";
        return UiMessages.text(foldersOnly ? "menu.path.no_subfolders" : "menu.path.folder_is_empty") + "\n"
                + UiMessages.text(foldersOnly ? "menu.path.use_this_folder_or_choose_another" : "menu.path.try_another_folder_or_a_place_below");
    }
    private String confirmLabel() {
        if (shortcut >= 0) return UiMessages.text("menu.path.open_caps");
        if (load != null || directory == null) return UiMessages.text("menu.path.choose_caps");
        if (!filesOnly && row == 0) return UiMessages.text("menu.path.use");
        if (row < prefixRows() || entries.get(row - prefixRows()).directory()) return UiMessages.text("menu.path.open_caps");
        return UiMessages.text("menu.path.select");
    }
}
