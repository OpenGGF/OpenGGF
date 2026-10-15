package com.openggf.game;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.graphics.PixelFont;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Local controller path picker; browsing never creates or modifies files. */
public final class MenuPathBrowser {
    private static final int ROWS = 7;
    private Path directory;
    private final boolean filesOnly;
    private final String title;
    private final String instruction;
    private MasterTitlePointer pointer;
    private int width = 320;
    private record Entry(Path path, boolean directory) { }
    private record Listing(Path directory, List<Entry> entries, String error) { }
    private List<Entry> entries = List.of();
    private MenuLoadTask<Listing> load;
    private int row;
    private String error;
    private String selected;
    private boolean cancelled;
    private MenuDetailsScreen details;
    private String acceptHint = "Enter", backHint = "Esc", detailsHint = "F1", directionsHint = "Arrows";

    public MenuPathBrowser(String initial) {
        this(initial, false, "BROWSE PATH", "type in editor for a new path");
    }
    public MenuPathBrowser(String initial, boolean filesOnly, String title, String instruction) {
        this.filesOnly = filesOnly;
        this.title = title;
        this.instruction = instruction;
        load = new MenuLoadTask<>(() -> {
            Path candidate = Path.of(initial.isBlank() ? "." : initial).toAbsolutePath().normalize();
            while (candidate != null && !Files.isDirectory(candidate)) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                candidate = candidate.getParent();
            }
            return read(candidate == null ? Path.of(".").toAbsolutePath().normalize() : candidate);
        });
    }
    private static Listing read(Path directory) {
        try (var stream = Files.list(directory)) {
            List<Entry> entries = stream.map(path -> new Entry(path, Files.isDirectory(path)))
                    .sorted(Comparator.comparing(Entry::directory).reversed()
                            .thenComparing(e -> e.path().getFileName().toString(), String.CASE_INSENSITIVE_ORDER)).toList();
            return new Listing(directory, entries, null);
        } catch (IOException | SecurityException e) { return new Listing(directory, List.of(), "Cannot read folder: " + e.getMessage()); }
    }
    private void refresh(Path next) {
        row = 0;
        error = null;
        load = new MenuLoadTask<>(() -> read(next));
    }
    private void consumeLoad() {
        if (load == null || !load.done()) return;
        try {
            Listing result = load.result();
            directory = result.directory(); entries = result.entries(); error = result.error();
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
    public void update(InputHandler input) {
        MasterTitlePointer mouse = pointer;
        pointer = null;
        if (input == null || selected != null || cancelled) return;
        acceptHint = MenuInput.confirmLabel(input); backHint = MenuInput.backLabel(input); detailsHint = MenuInput.detailsLabel(input); directionsHint = MenuInput.directionLabel(input);
        if (details != null) { if (details.update(input) || mouse != null && mouse.backClick()) details = null; return; }
        if (MenuInput.textBack(input) || mouse != null && (mouse.backClick()
                || mouse.click() && mouse.over(0, 207, width, 17))) { cancel(); MenuFeedback.emit(MenuFeedback.Cue.CANCEL); return; }
        consumeLoad();
        if (load != null) return;
        if (MenuInput.textDetails(input)) {
            String full = directory == null ? "" : row < prefixRows() ? directory.toString() : entries.get(row - prefixRows()).path().toString();
            details = new MenuDetailsScreen("PATH", error == null ? full : error + "\n" + full); return;
        }
        if (directory == null) return;
        int count = entries.size() + prefixRows();
        boolean clicked = false;
        if (mouse != null) {
            if (mouse.wheel() != 0) row = Math.clamp(row - mouse.wheel(), 0, count - 1);
            int first = row / ROWS * ROWS;
            for (int i = first; i < Math.min(count, first + ROWS); i++) {
                if (mouse.over(8, 52 + (i - first) * 19, width - 16, 18)
                        && (mouse.moved() || mouse.click())) {
                    row = i;
                    clicked = mouse.click();
                }
            }
        }
        if (MenuInput.textUp(input)) { row = Math.floorMod(row - 1, count); MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE); }
        if (MenuInput.textDown(input)) { row = (row + 1) % count; MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE); }
        if (MenuInput.textAccept(input) || clicked) {
            if (!filesOnly && row == 0) selected = directory.toString();
            else if (row == prefixRows() - 1) { if (directory.getParent() != null) { refresh(directory.getParent()); } }
            else {
                Entry entry = entries.get(row - prefixRows());
                if (entry.directory()) refresh(entry.path());
                else selected = entry.path().toString();
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
        MenuChrome.background(font, width);
        MenuChrome.header(font, width, title, null);
        MenuStyle.text(font, MenuDetailsScreen.readable(directory == null ? "" : directory.toString()), 10, 34, width - 20, .6f, .8f, 1);
        if (load != null || directory == null) {
            MenuStyle.text(font, load != null ? "Loading folder..." : "Cannot open path", 10, 60, width - 20, 1, .8f, .4f);
            MenuStyle.footer(font, width, error == null ? "" : error, backHint + " Back  " + detailsHint + " Details");
            return;
        }
        int first = row / ROWS * ROWS;
        for (int i = first; i < Math.min(entries.size() + prefixRows(), first + ROWS); i++) {
            int y = 52 + (i - first) * 19;
            if (i == row) MenuStyle.focusLabel(font, 8, y, width - 16, 18);
            String name = !filesOnly && i == 0 ? "Use this folder" : i == prefixRows() - 1 ? ".. Parent folder" :
                    (entries.get(i - prefixRows()).directory() ? "/ " : "") + entries.get(i - prefixRows()).path().getFileName();
            MenuStyle.label(font, MenuDetailsScreen.readable(name), 13, y, width - 26, 1, 1, 1);
        }
        MenuStyle.footer(font, width, detailsHint + " Details; " + (error == null ? instruction : error),
                acceptHint + " Choose  " + backHint + " Back  " + directionsHint);
    }
}
