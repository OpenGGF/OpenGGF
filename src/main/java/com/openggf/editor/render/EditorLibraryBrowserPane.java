package com.openggf.editor.render;

import com.openggf.editor.EditorStockObjectPalette;
import com.openggf.level.MutableLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Pure browse/filter/select model shared by keyboard input and the editor library renderer. */
@com.openggf.game.ModApi
public final class EditorLibraryBrowserPane {
    @com.openggf.game.ModApi
    public enum Kind { BLOCK, CHUNK, OBJECT }

    @com.openggf.game.ModApi
    public record Entry(Kind kind, int index, Integer stockObjectId, String objectKey, String label,
                        String previewArtKey) {
        public Entry { Objects.requireNonNull(kind); Objects.requireNonNull(label); }
    }

    private List<Entry> entries = List.of();
    private String filter = "";
    private int cursor;
    private int columns = 3;
    private int pageSize = 12;

    public void showBlocks(MutableLevel level) {
        ArrayList<Entry> built = new ArrayList<>(level.getBlockCount());
        for (int i=0;i<level.getBlockCount();i++) built.add(new Entry(Kind.BLOCK,i,null,null,"Block " + i,null));
        replace(built);
    }

    public void showChunks(MutableLevel level) {
        ArrayList<Entry> built = new ArrayList<>(level.getChunkCount());
        for (int i=0;i<level.getChunkCount();i++) built.add(new Entry(Kind.CHUNK,i,null,null,"Chunk " + i,null));
        replace(built);
    }

    public void showObjects(EditorStockObjectPalette palette) {
        List<Entry> built = palette.entries().stream().map(entry -> new Entry(Kind.OBJECT,
                entry.stockObjectId() == null ? -1 : entry.stockObjectId(), entry.stockObjectId(),
                entry.objectKey(), entry.label(),entry.previewArtKey())).toList();
        replace(built);
    }

    private void replace(List<Entry> replacement) {
        entries = List.copyOf(replacement);
        cursor = 0;
        clampCursor();
    }

    public void setFilter(String filter) {
        this.filter = Objects.requireNonNull(filter, "filter").strip();
        cursor = 0;
        clampCursor();
    }

    public String filter() { return filter; }

    public List<Entry> visibleEntries() {
        if (filter.isEmpty()) return entries;
        String needle=filter.toLowerCase(Locale.ROOT);
        return entries.stream().filter(entry -> entry.label().toLowerCase(Locale.ROOT).contains(needle)).toList();
    }

    public void move(int delta) {
        List<Entry> visible=visibleEntries();
        cursor=visible.isEmpty()?0:Math.floorMod(cursor+delta,visible.size());
    }

    public void move2d(int dx, int dy) { move(dx + dy * columns); }
    public void page(int pages) { move(pages * pageSize); }
    public void setLayout(int columns, int pageSize) {
        if(columns<1||pageSize<1)throw new IllegalArgumentException("library layout must be positive");
        this.columns=columns;this.pageSize=pageSize;
    }

    public Entry selected() {
        List<Entry> visible=visibleEntries();
        return visible.isEmpty()?null:visible.get(cursor);
    }

    public int cursor() { return cursor; }

    private void clampCursor() {
        int size=visibleEntries().size();
        if(size==0)cursor=0; else cursor=Math.min(cursor,size-1);
    }
}
