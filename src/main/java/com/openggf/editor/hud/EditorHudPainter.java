package com.openggf.editor.hud;

import com.openggf.control.ControllerPromptStyle;
import com.openggf.editor.EditorHierarchyDepth;
import com.openggf.editor.EditorSpawnEditMode;
import com.openggf.editor.EditorStockObjectPalette;
import com.openggf.editor.LevelEditorController;
import com.openggf.editor.hud.EditorHud.Glyph;
import com.openggf.editor.hud.EditorHud.Hit;
import com.openggf.editor.hud.EditorHud.LibraryKind;
import com.openggf.editor.hud.EditorHud.Modal;
import com.openggf.editor.hud.EditorHudCanvas.Size;
import com.openggf.editor.hud.EditorHudLayout.Rect;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.MutableLevel;
import com.openggf.level.PatternDesc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.openggf.editor.hud.EditorHudTheme.*;

/** Draws the editor HUD for one frame and records pointer targets for the next update. */
final class EditorHudPainter implements AutoCloseable {
    private static final int SLOT = 40;
    private static final int SLOT_GAP = 4;
    /** Field-sheet row pitch: the light 8-unit face centred in a 16-unit selection band. */
    static final int SHEET_ROW = 18;
    static final float MENU_MIN_WIDTH = 240;
    static final float MENU_CAP_GAP = 16;
    static final float MENU_SCROLL_LANE = 6;
    private final EditorTileAtlas atlas = new EditorTileAtlas();
    private final EditorTileAtlas ghostAtlas = new EditorTileAtlas();
    private final com.openggf.editor.render.EditorObjectPreviewCache objectArt = new com.openggf.editor.render.EditorObjectPreviewCache();
    private List<Hit> hits;
    // Free span of the top bar between the breadcrumb and the chips, for status messages.
    private float topBarFreeLeft;
    private float topBarFreeRight;
    private long usageKey = Long.MIN_VALUE;
    private int usage;

    void draw(EditorHudCanvas c, EditorHudLayout layout, EditorHud hud) {
        hits = new ArrayList<>();
        LevelEditorController controller = hud.controller();
        MutableLevel level = controller.currentLevel();
        if (hud.contentChanged) {
            atlas.requestCheck();
            hud.contentChanged = false;
        }
        Integer patternRaw = controller.selectedPatternRaw();
        atlas.refresh(level, patternRaw == null ? 0 : (patternRaw >>> 13) & 3);
        objectArt.refresh(level, controller.objectPaletteOrNull());
        if (controller.objectPaletteOrNull() != null) {
            var indices = new java.util.ArrayList<Integer>();
            var entries = controller.objectPalette().entries();
            for (int i = 0; i < entries.size(); i++)
                if (hud.showInternalObjects || controller.isPlaceableObject(entries.get(i)) && (entries.get(i).objectKey() != null || objectArt.available(entries.get(i)))) indices.add(i);
            hud.setObjectLibrary(indices);
        }
        backdrop(c, layout);
        canvasFrame(c, layout.game(), hud.tick);
        if (layout.workbench() == null) placements(c, layout, controller);
        if (layout.workbench() == null && level != null && hud.modal != Modal.MENU) worldCursor(c, layout, controller, level, hud.tick);
        if (level != null && layout.workbench() == null) region(c, layout, controller, level);
        topBar(c, layout, controller);
        if (layout.workbench() != null && level != null) {
            highlightEditedBlock(c, layout, controller, level);
            workbench(c, layout.workbench(), hud, controller, level);
        }
        dock(c, layout, hud, controller, level);
        toast(c, layout, hud);
        promptBar(c, layout, hud);
        switch (hud.modal) {
            case LIBRARY -> library(c, layout, hud, level);
            case WHEEL -> wheel(c, layout, hud);
            case MENU -> menu(c, layout, hud);
            case PROPERTIES -> properties(c, layout, hud);
            case PALETTE -> palette(c, layout, hud);
            case SETTINGS -> settings(c, layout, hud);
            case NONE -> { }
        }
        hud.hits = List.copyOf(hits);
    }

    // ------------------------------------------------------------------ frame

    private static void backdrop(EditorHudCanvas c, EditorHudLayout layout) {
        Rect g = layout.game();
        float w = layout.width(), h = layout.height();
        c.rect(0, 0, w, g.y(), BACKDROP, 1);
        c.rect(0, g.bottom(), w, h - g.bottom(), BACKDROP, 1);
        c.rect(0, g.y(), g.x(), g.h(), BACKDROP, 1);
        c.rect(g.right(), g.y(), w - g.right(), g.h(), BACKDROP, 1);
        // A faint dotted grid, echoing a cutting mat, keeps the empty stage from reading as a void.
        Rect stage = layout.stage();
        for (float y = stage.y() + 8; y < stage.bottom(); y += 16) {
            for (float x = stage.x() + 8; x < stage.right(); x += 16) {
                if (!g.inset(-6).contains(x, y)) c.rect(x, y, 1, 1, LINE, .55f);
            }
        }
    }

    private static void canvasFrame(EditorHudCanvas c, Rect g, long tick) {
        c.frame(g.x() - 2, g.y() - 2, g.w() + 4, g.h() + 4, 2, SHADOW, .8f);
        c.frame(g.x() - 3, g.y() - 3, g.w() + 6, g.h() + 6, 1, LINE, 1);
        float len = 10;
        for (int corner = 0; corner < 4; corner++) {
            float x = (corner & 1) == 0 ? g.x() - 5 : g.right() + 3;
            float y = (corner & 2) == 0 ? g.y() - 5 : g.bottom() + 3;
            float hx = (corner & 1) == 0 ? x : x + 2 - len;
            float vy = (corner & 2) == 0 ? y : y + 2 - len;
            c.rect(hx, y, len, 2, ACCENT, 1);
            c.rect(x, vy, 2, len, ACCENT, 1);
        }
    }

    private void topBar(EditorHudCanvas c, EditorHudLayout layout, LevelEditorController controller) {
        Rect bar = layout.topBar();
        c.rect(0, 0, bar.w(), bar.h(), PANEL, 1);
        c.rect(0, bar.h() - 1, bar.w(), 1, LINE, 1);
        float x = 6;
        List<String> crumbs = new ArrayList<>();
        crumbs.add("World");
        if (controller.depth() != EditorHierarchyDepth.WORLD) crumbs.add("Block " + hex(controller.selection().selectedBlock()));
        if (controller.depth() == EditorHierarchyDepth.CHUNK) crumbs.add("Chunk " + hex(controller.selection().selectedChunk()));
        for (int i = 0; i < crumbs.size(); i++) {
            boolean current = i == crumbs.size() - 1;
            String text = tag(crumbs.get(i));
            float w = EditorHudCanvas.textWidth(text, Size.BODY) + 12;
            c.pill(x, 4, w, 14, current ? ACCENT : PANEL_RAISED, 1);
            c.text(text, x + 6, 6, Size.BODY, current ? 0xFFFFFF : TEXT_DIM, 1);
            x += w + 3;
            if (!current) {
                chevron(c, x + 2, 8, TEXT_FAINT);
                x += 10;
            }
        }
        topBarFreeLeft = x + 6;
        float right = bar.w() - 6;
        boolean modified = controller.isModified();
        right = chip(c, right, modified ? "Unsaved" : "Saved", modified ? GOLD : GOOD, true);
        right = chip(c, right, controller.activeLayer() == 0 ? "FG" : "BG", TEXT, false);
        right = chip(c, right, "Zoom " + controller.canvasView().zoom() + "x", TEXT, false);
        if (controller.isCollisionOverlayEnabled()) {
            right = chip(c, right, controller.collisionPath().name().startsWith("P") ? "Solid A" : "Solid B", ACCENT, false);
        }
        String mode = switch (controller.spawnEditMode()) {
            case OBJECTS -> "Objects";
            case RINGS -> "Rings";
            default -> "Terrain";
        };
        right = chip(c, right, mode, TEXT, false);
        var cursor = controller.worldCursor();
        String position = "X " + cursor.x() + "  Y " + cursor.y();
        float positionX = right - EditorHudCanvas.textWidth(position, Size.SMALL) - 4;
        c.text(position, positionX, 8, Size.SMALL, TEXT_DIM, 1);
        topBarFreeRight = positionX - 8;
    }

    /** Right-aligned chip ending at {@code right}; returns the next free right edge. */
    private static float chip(EditorHudCanvas c, float right, String label, int colour, boolean dot) {
        String text = tag(label);
        float w = EditorHudCanvas.textWidth(text, Size.BODY) + 12 + (dot ? 8 : 0);
        float x = right - w;
        c.pill(x, 4, w, 14, PANEL_RAISED, 1);
        float tx = x + 6;
        if (dot) {
            c.rect(tx, 9, 4, 4, colour, 1);
            tx += 8;
        }
        c.text(text, tx, 6, Size.BODY, dot ? TEXT : colour, 1);
        return x - 4;
    }

    // ------------------------------------------------------------------ dock

    private void dock(EditorHudCanvas c, EditorHudLayout layout, EditorHud hud, LevelEditorController controller,
                      MutableLevel level) {
        Rect dock = layout.dock();
        c.rect(0, dock.y(), dock.w(), dock.h(), PANEL, .97f);
        c.rect(0, dock.y(), dock.w(), 1, LINE, 1);
        LibraryKind kind = hud.kind();
        float barW = EditorHudHotbar.SLOTS * SLOT + (EditorHudHotbar.SLOTS - 1) * SLOT_GAP;
        float x0 = (dock.w() - barW) / 2f;
        float y0 = dock.y() + 16;
        if (kind == LibraryKind.NONE) {
            String text = "Rings mode: " + (hud.controllerPrompts ? "South" : "Space") + " places a ring at the cursor";
            c.text(text, (dock.w() - EditorHudCanvas.textWidth(text, Size.SMALL)) / 2f, dock.y() + 26, Size.SMALL, TEXT_DIM, 1);
            return;
        }
        int[] slots = hud.hotbar.slots(kind);
        int selected = hud.hotbar.selected(kind);
        for (int i = 0; i < slots.length; i++) {
            if (i == selected) continue;
            float x = x0 + i * (SLOT + SLOT_GAP);
            slot(c, kind, slots[i], x, y0, SLOT, false, hud.tick, controller);
            c.rect(x + 1, y0 + SLOT - 10, 8, 9, SHADOW, .7f);
            c.text(Integer.toString((i + 1) % 10), x + 2, y0 + SLOT - 9, Size.SMALL, TEXT_DIM, 1);
            hits.add(new Hit(new Rect(x, y0, SLOT, SLOT), Modal.NONE, EditorHud.HIT_HOTBAR, i));
        }
        float sx = x0 + selected * (SLOT + SLOT_GAP);
        slot(c, kind, slots[selected], sx - 2, y0 - 3, SLOT + 4, true, hud.tick, controller);
        hits.add(new Hit(new Rect(sx, y0, SLOT, SLOT), Modal.NONE, EditorHud.HIT_HOTBAR, selected));
        String name = entryName(kind, slots[selected], controller);
        float nw = EditorHudCanvas.textWidth(name, Size.SMALL) + 8;
        float nx = Math.max(4, Math.min(dock.w() - nw - 4, sx + SLOT / 2f - nw / 2f));
        c.pill(nx, dock.y() + 2, nw, 10, GOLD, 1);
        c.text(name, nx + 4, dock.y() + 3, Size.SMALL, 0x1A1300, 1);
        if (hud.controllerPrompts) {
            glyph(c, Glyph.LB, x0 - 31, y0 + SLOT / 2f, hud);
            glyph(c, Glyph.RB, x0 + barW + 8, y0 + SLOT / 2f, hud);
        }
        // Left: what the brush is; right: what is under the cursor.
        float sideW = x0 - 44;
        if (sideW > 60) {
            c.text(kindTitle(kind).toUpperCase(Locale.ROOT), 10, dock.y() + 14, Size.SMALL, TEXT_FAINT, 1);
            c.fittedText(name, 10, dock.y() + 25, sideW - 10, Size.BODY, TEXT, 1);
            c.fittedText(brushDetail(kind, controller), 10, dock.y() + 39, sideW - 10, Size.SMALL, TEXT_DIM, 1);
            float rx = x0 + barW + 40;
            c.text(controller.isSpawnEditing() ? "SELECTION" : "UNDER CURSOR", rx, dock.y() + 14, Size.SMALL, TEXT_FAINT, 1);
            c.fittedText(underCursor(controller, level), rx, dock.y() + 25, dock.w() - rx - 8, Size.BODY, TEXT, 1);
        }
    }

    private void slot(EditorHudCanvas c, LibraryKind kind, int value, float x, float y, float size, boolean selected,
                      long tick, LevelEditorController controller) {
        if (selected) c.rect(x + 2, y + 3, size, size, SHADOW, .45f);
        c.rect(x, y, size, size, TILE, 1);
        if (value >= 0) thumbnail(c, kind, value, x + 2, y + 2, size - 4, controller);
        if (selected) {
            float pulse = .75f + .25f * (float) Math.sin(tick * .12);
            c.frame(x - 1, y - 1, size + 2, size + 2, 2, GOLD, pulse);
        } else {
            c.frame(x, y, size, size, 1, LINE, 1);
        }
    }

    private void thumbnail(EditorHudCanvas c, LibraryKind kind, int value, float x, float y, float size,
                           LevelEditorController controller) {
        EditorTileAtlas.Kind atlasKind = switch (kind) {
            case BLOCK -> EditorTileAtlas.Kind.BLOCK;
            case CHUNK -> EditorTileAtlas.Kind.CHUNK;
            case PATTERN -> EditorTileAtlas.Kind.PATTERN;
            default -> null;
        };
        if (atlasKind != null) {
            EditorTileAtlas.Cell cell = atlas.cell(atlasKind, value);
            if (cell != null) c.image(cell.texture(), x, y, size, size, cell.u0(), cell.v0(), cell.u1(), cell.v1(), 1);
            return;
        }
        EditorStockObjectPalette palette = controller.objectPaletteOrNull();
        if (palette == null || value < 0 || value >= palette.entries().size()) return;
        var entry = palette.entries().get(value);
        boolean selected = value == (palette.selectedIsKeyed() ? -1 : palette.selectedObjectId());
        var sprite = objectArt.sprite(palette.previewSpawn(entry, selected ? palette.selectedSubtype() : 0,
                selected ? palette.renderFlags() : 0));
        if (sprite != null) {
            float scale = Math.min(3, (size - 4) / Math.max(sprite.width(), sprite.height()));
            float w = sprite.width() * scale, h = sprite.height() * scale;
            c.image(sprite.texture(), x + (size - w) / 2, y + (size - h) / 2, w, h, 0, 0, 1, 1, 1);
        } else {
            c.pill(x + 2, y + 2, size - 4, size - 4, ACCENT_DARK, 1);
            c.frame(x + 7, y + 7, size - 14, size - 14, 1, ACCENT, .7f);
            c.text(entry.objectKey() == null ? "?" : "+", x + size / 2 - 3, y + size / 2 - 5, Size.BODY, TEXT, 1);
        }
    }

    private void placements(EditorHudCanvas c, EditorHudLayout layout, LevelEditorController controller) {
        var editor = com.openggf.game.session.SessionManager.getCurrentEditorMode();
        if (editor == null) return;
        var g = layout.game();
        c.clip(g);
        try {
            float unit = layout.gameViewport()[4] / (float) layout.uiScale() * controller.canvasView().zoom();
            com.openggf.editor.render.EditorPlacementScene.draw(controller, objectArt,
                    new com.openggf.editor.render.EditorPlacementScene.Painter() {
                public void image(com.openggf.editor.render.EditorObjectPreviewCache.Sprite sprite,
                                  float x, float y, float w, float h, float alpha) {
                    c.image(sprite.texture(), x, y, w, h, 0, 0, 1, 1, alpha);
                }
                public void rectangle(float x, float y, float w, float h, int color, float alpha, boolean fill) {
                    if (fill) c.rect(x, y, w, h, color, alpha);
                    else c.frame(x, y, w, h, 1, color, alpha);
                }
                public void chip(String label, float x, float y, int color, float alpha) {
                    String text = EditorHudCanvas.fit(label, Math.min(125, g.w() - 20), Size.SMALL);
                    float w = EditorHudCanvas.textWidth(text, Size.SMALL) + 16;
                    x = Math.clamp(x, g.x() + 2, g.right() - w - 2);
                    y = Math.clamp(y, g.y() + 2, g.bottom() - 16);
                    c.pill(x, y, w, 14, PANEL, .94f * alpha);
                    c.rect(x + 4, y + 5, 3, 3, color, alpha);
                    c.text(text, x + 11, y + 3, Size.SMALL, TEXT, alpha);
                }
            }, g.x(), g.y(), g.w(), g.h(), unit, editor.getCamera().getXCopy(), editor.getCamera().getYCopy());
        } finally { c.unclip(); }
    }

    private void toast(EditorHudCanvas c, EditorHudLayout layout, EditorHud hud) {
        if (hud.toastFrames <= 0 || hud.toast == null || hud.modal != Modal.NONE) return;
        float alpha = Math.min(1f, hud.toastFrames / 30f);
        // Status sits in the top bar's free span, never over the level or the chips.
        float span = topBarFreeRight - topBarFreeLeft;
        if (span < 60) return;
        String text = EditorHudCanvas.fit(hud.toast, span - 26, Size.SMALL);
        float w = EditorHudCanvas.textWidth(text, Size.SMALL) + 26;
        float x = topBarFreeLeft + (span - w) / 2f, y = 3;
        c.pill(x, y, w, 16, ACCENT_DARK, alpha);
        c.rect(x + 8, y + 6, 4, 4, GOLD, alpha);
        c.text(text, x + 18, y + 4, Size.SMALL, TEXT, alpha);
    }

    private static void promptBar(EditorHudCanvas c, EditorHudLayout layout, EditorHud hud) {
        promptBar(c, layout.promptBar(), hud.prompts(), hud.controllerPrompts, hud.pad, 0);
    }

    /**
     * The prompt bar: key caps (or pad glyphs) with their labels, centred in the bar right of
     * {@code left} (a caller's mode chip), stepping down in size until every prompt fits.
     */
    static void promptBar(EditorHudCanvas c, Rect bar, List<EditorHud.Prompt> prompts, boolean controller,
                          EditorHudPad pad, float left) {
        c.rect(0, bar.y(), bar.w(), bar.h(), BACKDROP, 1);
        c.rect(0, bar.y(), bar.w(), 1, LINE, 1);
        float room = bar.w() - left;
        PromptFit fit = promptFit(prompts, controller, room);
        float x = left + Math.max(6, (room - fit.total()) / 2f);
        float centre = bar.y() + bar.h() / 2f;
        for (EditorHud.Prompt prompt : prompts) {
            float gw = controller ? glyph(c, prompt.glyph(), x, centre, pad) : keycap(c, prompt.key(), x, centre);
            c.text(tag(prompt.label()), x + gw + 4, centre - (fit.size() == Size.BODY ? 5 : 4), fit.size(), TEXT_DIM, 1);
            x += promptWidth(prompt, controller, fit.size()) + fit.gap();
        }
    }

    /** Prompt-bar spacing: bold labels with wide gaps, then tighter gaps, then the light face, until all fit. */
    record PromptFit(Size size, float gap, float total) { }

    static PromptFit promptFit(List<EditorHud.Prompt> prompts, boolean controller, float barWidth) {
        PromptFit fit = null;
        for (Size size : new Size[] {Size.BODY, Size.SMALL}) {
            for (float gap : new float[] {14, 8}) {
                float total = 0;
                for (EditorHud.Prompt prompt : prompts) total += promptWidth(prompt, controller, size) + gap;
                fit = new PromptFit(size, gap, total - gap);
                if (fit.total() <= barWidth - 12) return fit;
            }
        }
        return fit;
    }

    static float glyphWidthOf(Glyph glyph) { return glyphWidth(glyph); }

    private static float promptWidth(EditorHud.Prompt prompt, boolean controller, Size size) {
        float glyph = controller ? glyphWidth(prompt.glyph()) : keycapWidth(prompt.key());
        return glyph + 4 + EditorHudCanvas.textWidth(tag(prompt.label()), size);
    }

    // ------------------------------------------------------------------ glyphs

    private enum Family { XBOX, PLAYSTATION }

    private static Family family(EditorHudPad pad) {
        return "Cross".equals(ControllerPromptStyle.forName(pad.deviceName()).confirm())
                ? Family.PLAYSTATION : Family.XBOX;
    }

    private static float glyphWidth(Glyph glyph) {
        return switch (glyph) {
            case SOUTH, EAST, WEST, NORTH, DPAD, LSTICK -> 15;
            case LB, RB, VIEW, MENU -> 23;
            case LT, RT -> 19;
            case BUMPERS -> 48;
        };
    }

    /** Draws a pad glyph vertically centred on {@code centre}; returns its width. */
    static float glyph(EditorHudCanvas c, Glyph glyph, float x, float centre, EditorHud hud) {
        return glyph(c, glyph, x, centre, hud.pad);
    }

    /** As {@link #glyph(EditorHudCanvas, Glyph, float, float, EditorHud)}, for a front end with its own pad. */
    static float glyph(EditorHudCanvas c, Glyph glyph, float x, float centre, EditorHudPad pad) {
        Family family = family(pad);
        int dark = 0x39466F;
        switch (glyph) {
            case SOUTH, EAST, WEST, NORTH -> {
                float y = centre - 7.5f;
                if (family == Family.PLAYSTATION) {
                    c.mask(EditorHudGlyphs.DISC, x, y, 0x232A42, 1);
                    EditorHudGlyphs.Mask shape = switch (glyph) {
                        case SOUTH -> EditorHudGlyphs.CROSS;
                        case EAST -> EditorHudGlyphs.CIRCLE;
                        case NORTH -> EditorHudGlyphs.TRIANGLE;
                        default -> EditorHudGlyphs.SQUARE;
                    };
                    int tint = switch (glyph) {
                        case SOUTH -> 0x8DB4FF;
                        case EAST -> 0xFF7A7A;
                        case NORTH -> 0x63D99A;
                        default -> 0xFF9BDD;
                    };
                    c.mask(shape, x + 3, y + 3, tint, 1);
                } else {
                    int tint = switch (glyph) {
                        case SOUTH -> 0x4CB050;
                        case EAST -> 0xE0464E;
                        case NORTH -> 0xF0B92A;
                        default -> 0x3E86F0;
                    };
                    c.mask(EditorHudGlyphs.DISC, x, y, tint, 1);
                    String letter = switch (glyph) {
                        case SOUTH -> "A";
                        case EAST -> "B";
                        case NORTH -> "Y";
                        default -> "X";
                    };
                    c.text(letter, x + 3, y + 3, Size.BODY, glyph == Glyph.NORTH ? 0x231A00 : 0xFFFFFF, 1);
                }
                return 15;
            }
            case LB, RB -> {
                float y = centre - 5.5f;
                c.mask(EditorHudGlyphs.BUMPER, x, y, dark, 1);
                String label = family == Family.PLAYSTATION ? (glyph == Glyph.LB ? "L1" : "R1") : (glyph == Glyph.LB ? "LB" : "RB");
                c.text(label, x + 6, y + 2, Size.SMALL, TEXT, 1);
                return 23;
            }
            case LT, RT -> {
                float y = centre - 6.5f;
                c.mask(EditorHudGlyphs.TRIGGER, x, y, dark, 1);
                String label = family == Family.PLAYSTATION ? (glyph == Glyph.LT ? "L2" : "R2") : (glyph == Glyph.LT ? "LT" : "RT");
                c.text(label, x + 4, y + 3, Size.SMALL, TEXT, 1);
                return 19;
            }
            case BUMPERS -> {
                glyph(c, Glyph.LB, x, centre, pad);
                glyph(c, Glyph.RB, x + 25, centre, pad);
                return 48;
            }
            case VIEW, MENU -> {
                float y = centre - 6.5f;
                c.mask(EditorHudGlyphs.PILL, x, y, dark, 1);
                if (glyph == Glyph.VIEW) c.mask(EditorHudGlyphs.VIEW, x + 7, y + 2, TEXT, 1);
                else c.mask(EditorHudGlyphs.MENU, x + 7, y + 3, TEXT, 1);
                return 23;
            }
            case DPAD -> {
                c.mask(EditorHudGlyphs.PLUS, x, centre - 7.5f, dark, 1);
                c.rect(x + 6, centre - 1.5f, 3, 3, TEXT_DIM, 1);
                return 15;
            }
            case LSTICK -> {
                c.mask(EditorHudGlyphs.DISC, x, centre - 7.5f, dark, 1);
                c.mask(EditorHudGlyphs.RING, x, centre - 7.5f, TEXT_DIM, 1);
                c.text("L", x + 4, centre - 5, Size.BODY, TEXT, 1);
                return 15;
            }
        }
        return 0;
    }

    static float keycapWidth(String key) { return EditorHudCanvas.textWidth(key, Size.SMALL) + 8; }

    /** Draws a key cap vertically centred on {@code centre}; returns its width. */
    static float keycap(EditorHudCanvas c, String key, float x, float centre) {
        float w = keycapWidth(key);
        float y = centre - 6.5f;
        c.rect(x, y + 1, w, 13, SHADOW, .8f);
        c.pill(x, y, w, 12, PANEL_HIGHLIGHT, 1);
        c.text(key, x + 4, y + 2, Size.SMALL, TEXT, 1);
        return w;
    }

    /** A window-resolution cursor over the game: the target block in terrain mode and a crosshair. */
    private static void worldCursor(EditorHudCanvas c, EditorHudLayout layout, LevelEditorController controller,
                                    MutableLevel level, long tick) {
        var editor = com.openggf.game.session.SessionManager.getCurrentEditorMode();
        if (editor == null || editor.getCamera() == null) return;
        Rect g = layout.game();
        float unitsPerPixel = layout.gameViewport()[4] / (float) layout.uiScale() * (float) controller.canvasView().zoom();
        var cursor = controller.worldCursor();
        float cx = g.x() + (cursor.x() - editor.getCamera().getXCopy()) * unitsPerPixel;
        float cy = g.y() + (cursor.y() - editor.getCamera().getYCopy()) * unitsPerPixel;
        float pulse = .7f + .3f * (float) Math.sin(tick * .12);
        c.clip(g);
        if (!controller.isSpawnEditing()) {
            int size = level.getBlockPixelSize();
            float bx = g.x() + (Math.floorDiv(cursor.x(), size) * size - editor.getCamera().getXCopy()) * unitsPerPixel;
            float by = g.y() + (Math.floorDiv(cursor.y(), size) * size - editor.getCamera().getYCopy()) * unitsPerPixel;
            float bs = size * unitsPerPixel;
            c.frame(bx - 1, by - 1, bs + 2, bs + 2, 1, SHADOW, .8f);
            c.frame(bx, by, bs, bs, 2, GOLD, pulse);
        }
        c.rect(cx - 7, cy - 1, 5, 2, SHADOW, .8f);
        c.rect(cx + 2, cy - 1, 5, 2, SHADOW, .8f);
        c.rect(cx - 1, cy - 7, 2, 5, SHADOW, .8f);
        c.rect(cx - 1, cy + 2, 2, 5, SHADOW, .8f);
        c.rect(cx - 6, cy - .5f, 4, 1, 0xFFFFFF, 1);
        c.rect(cx + 2, cy - .5f, 4, 1, 0xFFFFFF, 1);
        c.rect(cx - .5f, cy - 6, 1, 4, 0xFFFFFF, 1);
        c.rect(cx - .5f, cy + 2, 1, 4, 0xFFFFFF, 1);
        c.unclip();
    }

    private void region(EditorHudCanvas c, EditorHudLayout layout, LevelEditorController controller, MutableLevel level) {
        var editor = com.openggf.game.session.SessionManager.getCurrentEditorMode();
        if (editor == null || editor.getCamera() == null) return;
        Rect g = layout.game();
        float unit = layout.gameViewport()[4] / (float) layout.uiScale() * (float) controller.canvasView().zoom();
        int cameraX = Short.toUnsignedInt(editor.getCamera().getXCopy()), cameraY = editor.getCamera().getYCopy();
        int size = level.getBlockPixelSize();
        var rect = controller.regionSelection();
        c.clip(g);
        try {
            if (rect != null) c.frame(g.x() + (rect.x() * size - cameraX) * unit,
                    g.y() + (rect.y() * size - cameraY) * unit, rect.width() * size * unit, rect.height() * size * unit, 2, ACCENT, 1);
            if (!controller.isPastePreviewActive()) return;
            try {
                var ghost = controller.pastePreview();
                ghostAtlas.refresh(controller.clipboardLibrary(), 0);
                rect = ghost.rectangle();
                float cell = size * unit;
                for (int y = 0; y < rect.height(); y++) for (int x = 0; x < rect.width(); x++) {
                    float sx = g.x() + ((rect.x() + x) * size - cameraX) * unit;
                    float sy = g.y() + ((rect.y() + y) * size - cameraY) * unit;
                    if (sx >= g.right() || sy >= g.bottom() || sx + cell < g.x() || sy + cell < g.y()) continue;
                    var tile = ghostAtlas.cell(EditorTileAtlas.Kind.BLOCK, ghost.blocks().get(y * rect.width() + x));
                    if (tile != null) c.image(tile.texture(), sx, sy, cell, cell, tile.u0(), tile.v0(), tile.u1(), tile.v1(), .6f);
                    c.frame(sx, sy, cell, cell, 1, GOOD, .8f);
                }
                for (var object : ghost.objects()) {
                    var sprite = objectArt.sprite(object);
                    if (sprite != null) c.image(sprite.texture(),
                            g.x() + (object.x() - cameraX - sprite.originX()) * unit,
                            g.y() + (object.y() - cameraY - sprite.originY()) * unit,
                            sprite.width() * unit, sprite.height() * unit, 0, 0, 1, 1, .5f);
                }
                for (var ring : ghost.rings()) c.frame(g.x() + (ring.x() - cameraX) * unit - 3,
                        g.y() + (ring.y() - cameraY) * unit - 3, 6, 6, 1, GOLD, 1);
            } catch (IllegalArgumentException invalid) {
                c.text("Paste outside bounds or incompatible placement", g.x() + 8, g.y() + 8, Size.SMALL, GOLD, 1);
            }
        } finally { c.unclip(); }
    }

    // ------------------------------------------------------------------ workbench

    /** In Block/Chunk view, dims the world except the block being edited and outlines it. */
    private static void highlightEditedBlock(EditorHudCanvas c, EditorHudLayout layout, LevelEditorController controller,
                                             MutableLevel level) {
        var editor = com.openggf.game.session.SessionManager.getCurrentEditorMode();
        if (editor == null || editor.getCamera() == null) return;
        Rect g = layout.game();
        int[] viewport = layout.gameViewport();
        float unitsPerPixel = viewport[4] / (float) layout.uiScale() * (float) controller.canvasView().zoom();
        int size = level.getBlockPixelSize();
        var cursor = controller.worldCursor();
        float bx = g.x() + (Math.floorDiv(cursor.x(), size) * size - editor.getCamera().getXCopy()) * unitsPerPixel;
        float by = g.y() + (Math.floorDiv(cursor.y(), size) * size - editor.getCamera().getYCopy()) * unitsPerPixel;
        float bs = size * unitsPerPixel;
        c.clip(g);
        c.rect(g.x(), g.y(), g.w(), by - g.y(), SHADOW, .45f);
        c.rect(g.x(), by + bs, g.w(), g.bottom() - by - bs, SHADOW, .45f);
        c.rect(g.x(), by, bx - g.x(), bs, SHADOW, .45f);
        c.rect(bx + bs, by, g.right() - bx - bs, bs, SHADOW, .45f);
        c.frame(bx - 1, by - 1, bs + 2, bs + 2, 2, GOLD, 1);
        if (controller.depth() == EditorHierarchyDepth.CHUNK) {
            float cell = bs / controller.blockGridSide();
            c.frame(bx + controller.selectedBlockCellX() * cell, by + controller.selectedBlockCellY() * cell,
                    cell, cell, 1, ACCENT, 1);
        }
        c.unclip();
    }

    private void workbench(EditorHudCanvas c, Rect bench, EditorHud hud, LevelEditorController controller, MutableLevel level) {
        c.panel(bench.x(), bench.y(), bench.w(), bench.h(), PANEL, .98f);
        boolean blockDepth = controller.depth() == EditorHierarchyDepth.BLOCK;
        Integer blockIndex = controller.selection().selectedBlock();
        Integer chunkIndex = controller.selection().selectedChunk();
        String title = blockDepth ? "Block " + hex(blockIndex) : "Chunk " + hex(chunkIndex);
        c.text(tag(title), bench.x() + 10, bench.y() + 8, Size.LARGE, TEXT, 1);
        String subtitle = blockDepth
                ? controller.blockGridSide() + "x" + controller.blockGridSide() + " chunks, " + level.getBlockPixelSize() + " px"
                : "2x2 patterns, 16 px";
        c.text(subtitle, bench.x() + 11, bench.y() + 31, Size.SMALL, TEXT_DIM, 1);
        int side = blockDepth ? controller.blockGridSide() : 2;
        float top = bench.y() + 46;
        float infoW = 128;
        int cell = (int) Math.min((bench.h() - 56) / side, (bench.w() - 30 - infoW) / side);
        cell = Math.max(8, Math.min(blockDepth ? 40 : 96, cell));
        float gx = bench.x() + 10, gy = top;
        c.rect(gx - 2, gy - 2, side * cell + 4, side * cell + 4, SHADOW, .7f);
        c.rect(gx, gy, side * cell, side * cell, TILE, 1);
        int selX = blockDepth ? controller.selectedBlockCellX() : controller.selectedChunkCellX();
        int selY = blockDepth ? controller.selectedBlockCellY() : controller.selectedChunkCellY();
        Integer brushChunk = controller.chunkBrushIndex();
        if (blockDepth && blockIndex != null && blockIndex >= 0 && blockIndex < level.getBlockCount()) {
            Block block = level.getBlock(blockIndex);
            for (int y = 0; y < side; y++) for (int x = 0; x < side; x++) {
                ChunkDesc desc = block.getChunkDesc(x, y);
                EditorTileAtlas.Cell art = atlas.cell(EditorTileAtlas.Kind.CHUNK, desc.getChunkIndex());
                float px = gx + x * cell, py = gy + y * cell;
                if (art != null) {
                    float u0 = desc.getHFlip() ? art.u1() : art.u0(), u1 = desc.getHFlip() ? art.u0() : art.u1();
                    float v0 = desc.getVFlip() ? art.v1() : art.v0(), v1 = desc.getVFlip() ? art.v0() : art.v1();
                    c.image(art.texture(), px, py, cell, cell, u0, v0, u1, v1, 1);
                }
                if (brushChunk != null && desc.getChunkIndex() == brushChunk) c.frame(px + 1, py + 1, cell - 2, cell - 2, 1, ACCENT, .7f);
                hits.add(new Hit(new Rect(px, py, cell, cell), Modal.NONE, EditorHud.HIT_CELL, y << 8 | x));
            }
        } else if (!blockDepth && chunkIndex != null) {
            EditorTileAtlas.Cell art = atlas.cell(EditorTileAtlas.Kind.CHUNK, chunkIndex);
            for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
                float px = gx + x * cell, py = gy + y * cell;
                if (art != null) {
                    float du = (art.u1() - art.u0()) / 2, dv = (art.v1() - art.v0()) / 2;
                    c.image(art.texture(), px, py, cell, cell, art.u0() + x * du, art.v0() + y * dv,
                            art.u0() + (x + 1) * du, art.v0() + (y + 1) * dv, 1);
                }
                hits.add(new Hit(new Rect(px, py, cell, cell), Modal.NONE, EditorHud.HIT_CELL, y << 8 | x));
            }
        }
        for (int i = 1; i < side; i++) {
            c.rect(gx + i * cell, gy, 1, side * cell, SHADOW, .45f);
            c.rect(gx, gy + i * cell, side * cell, 1, SHADOW, .45f);
        }
        float pulse = .7f + .3f * (float) Math.sin(hud.tick * .12);
        c.frame(gx + selX * cell - 3, gy + selY * cell - 3, cell + 6, cell + 6, 1, SHADOW, .9f);
        c.frame(gx + selX * cell - 2, gy + selY * cell - 2, cell + 4, cell + 4, 2, GOLD, pulse);
        c.frame(gx + selX * cell, gy + selY * cell, cell, cell, 1, SHADOW, .6f);
        // Details of the selected cell.
        float ix = gx + side * cell + 14, iy = top;
        float iw = bench.right() - ix - 8;
        if (blockDepth && blockIndex != null && blockIndex < level.getBlockCount()) {
            ChunkDesc desc = level.getBlock(blockIndex).getChunkDesc(selX, selY);
            iy = label(c, "CELL", "X " + selX + "  Y " + selY, ix, iy, iw);
            iy = label(c, "CHUNK", hex(desc.getChunkIndex()), ix, iy, iw);
            EditorTileAtlas.Cell art = atlas.cell(EditorTileAtlas.Kind.CHUNK, desc.getChunkIndex());
            if (art != null) {
                c.rect(ix, iy, 36, 36, TILE, 1);
                c.image(art.texture(), ix + 2, iy + 2, 32, 32, art.u0(), art.v0(), art.u1(), art.v1(), 1);
                iy += 42;
            }
            iy = flags(c, ix, iy, new String[] {"Flip H", "Flip V"}, new boolean[] {desc.getHFlip(), desc.getVFlip()});
            iy = label(c, "SOLID A", solidity(desc.get() >> 12), ix, iy, iw);
            iy = label(c, "SOLID B", solidity(desc.get() >> 14), ix, iy, iw);
            if (desc.getChunkIndex() < level.getChunkCount()) {
                Chunk chunk = level.getChunk(desc.getChunkIndex());
                label(c, "SHAPE", hex(chunk.getSolidTileIndex()) + " / " + hex(chunk.getSolidTileAltIndex()), ix, iy, iw);
            }
        } else if (!blockDepth && chunkIndex != null && chunkIndex < level.getChunkCount()) {
            PatternDesc desc = level.getChunk(chunkIndex).getPatternDesc(selX, selY);
            iy = label(c, "CELL", "X " + selX + "  Y " + selY, ix, iy, iw);
            iy = label(c, "PATTERN", hex(desc.getPatternIndex()), ix, iy, iw);
            iy = flags(c, ix, iy, new String[] {"Flip H", "Flip V", "High"},
                    new boolean[] {desc.getHFlip(), desc.getVFlip(), desc.getPriority()});
            c.text("PALETTE LINE " + desc.getPaletteIndex(), ix, iy, Size.SMALL, TEXT_FAINT, 1);
            iy += 11;
            for (int i = 0; i < 16; i++) {
                float sx = ix + (i % 8) * 11, sy = iy + (i / 8) * 11;
                c.rect(sx, sy, 10, 10, SHADOW, 1);
                c.rect(sx + 1, sy + 1, 8, 8, EditorTileAtlas.paletteColor(level, desc.getPaletteIndex(), i), 1);
            }
        }
    }

    private static float label(EditorHudCanvas c, String title, String value, float x, float y, float w) {
        c.text(title, x, y, Size.SMALL, TEXT_FAINT, 1);
        c.fittedText(value, x, y + 10, w, Size.BODY, TEXT, 1);
        return y + 26;
    }

    private static float flags(EditorHudCanvas c, float x, float y, String[] names, boolean[] on) {
        float cx = x;
        for (int i = 0; i < names.length; i++) {
            float w = EditorHudCanvas.textWidth(names[i], Size.SMALL) + 8;
            c.pill(cx, y, w, 11, on[i] ? ACCENT : PANEL_RAISED, 1);
            c.text(names[i], cx + 4, y + 2, Size.SMALL, on[i] ? 0xFFFFFF : TEXT_FAINT, 1);
            cx += w + 3;
        }
        return y + 18;
    }

    private static String solidity(int bits) {
        return switch (bits & 3) {
            case 1 -> "Top only";
            case 2 -> "Sides and bottom";
            case 3 -> "All sides";
            default -> "None";
        };
    }

    // ------------------------------------------------------------------ library sheet

    private void library(EditorHudCanvas c, EditorHudLayout layout, EditorHud hud, MutableLevel level) {
        dim(c, layout, .6f);
        Rect sheet = layout.sheet();
        c.panel(sheet.x(), sheet.y(), sheet.w(), sheet.h(), PANEL, .99f);
        LibraryKind kind = hud.libraryKind;
        int count = hud.libraryCount(kind);
        String title = tag(kindTitle(kind));
        c.text(title, sheet.x() + 12, sheet.y() + 8, Size.LARGE, TEXT, 1);
        // The internal-object toggle is a prompt-bar action; the subtitle states which set is shown.
        String countText = kind == LibraryKind.OBJECT
                ? count + (hud.showInternalObjects ? " objects, including internal ones" : " placeable objects")
                : count + " in this level's library";
        c.text(countText, sheet.x() + 18 + EditorHudCanvas.textWidth(title, Size.LARGE), sheet.y() + 16,
                Size.SMALL, TEXT_DIM, 1);
        float detailW = Math.min(170, sheet.w() * .3f);
        Rect detail = new Rect(sheet.right() - detailW - 10, sheet.y() + 36, detailW, sheet.h() - 46);
        Rect grid = new Rect(sheet.x() + 10, sheet.y() + 36, detail.x() - sheet.x() - 26, sheet.h() - 46);
        boolean objects = kind == LibraryKind.OBJECT;
        float cellW = switch (kind) {
            case BLOCK -> 52;
            case CHUNK -> 40;
            case PATTERN -> 32;
            default -> 92;
        };
        float cellH = objects ? 72 : cellW;
        int columns = Math.max(1, (int) (grid.w() / cellW));
        int rows = Math.max(1, (int) (grid.h() / cellH));
        hud.libraryColumns = columns;
        hud.libraryVisibleRows = rows;
        int totalRows = (count + columns - 1) / columns;
        int cursorRow = hud.libraryCursor / columns;
        float target = hud.libraryScroll;
        if (cursorRow < target + 1) target = cursorRow - 1;
        if (cursorRow > target + rows - 2) target = cursorRow - rows + 2;
        target = Math.max(0, Math.min(Math.max(0, totalRows - rows), target));
        hud.libraryScroll += (target - hud.libraryScroll) * .35f;
        if (Math.abs(target - hud.libraryScroll) < .01f) hud.libraryScroll = target;
        float offsetX = grid.x() + (grid.w() - columns * cellW) / 2f;
        c.clip(grid);
        int firstRow = Math.max(0, (int) Math.floor(hud.libraryScroll));
        int lastRow = Math.min(totalRows - 1, (int) Math.ceil(hud.libraryScroll + rows));
        float focusX = 0, focusY = 0;
        boolean focusVisible = false;
        int[] bar = hud.hotbar.slots(kind);
        for (int row = firstRow; row <= lastRow; row++) {
            for (int column = 0; column < columns; column++) {
                int index = row * columns + column;
                if (index >= count) break;
                float x = offsetX + column * cellW, y = grid.y() + (row - hud.libraryScroll) * cellH;
                if (index == hud.libraryCursor) { focusX = x; focusY = y; focusVisible = true; continue; }
                libraryTile(c, kind, hud.libraryValue(kind, index), x + 2, y + 2, cellW - 4, cellH - 4, hud.controller(), contains(bar, hud.libraryValue(kind, index)));
                hit(new Rect(x, y, cellW, cellH), grid, Modal.LIBRARY, EditorHud.HIT_LIBRARY, index);
            }
        }
        if (focusVisible) {
            c.unclip();
            c.clip(grid.inset(-4));
            float grow = 3;
            c.rect(focusX - grow + 3, focusY - grow + 4, cellW + 2 * grow - 4, cellH + 2 * grow - 4, SHADOW, .5f);
            libraryTile(c, kind, hud.libraryValue(kind, hud.libraryCursor), focusX - grow + 2, focusY - grow + 2, cellW + 2 * grow - 4,
                    cellH + 2 * grow - 4, hud.controller(), contains(bar, hud.libraryValue(kind, hud.libraryCursor)));
            float pulse = .75f + .25f * (float) Math.sin(hud.tick * .12);
            c.frame(focusX - grow, focusY - grow, cellW + 2 * grow, cellH + 2 * grow, 2, GOLD, pulse);
            hit(new Rect(focusX, focusY, cellW, cellH), grid, Modal.LIBRARY, EditorHud.HIT_LIBRARY, hud.libraryCursor);
        }
        c.unclip();
        if (totalRows > rows) {
            float trackX = grid.right() + 6;
            c.rect(trackX, grid.y(), 3, grid.h(), LINE, .6f);
            float thumbH = Math.max(12, grid.h() * rows / totalRows);
            float thumbY = grid.y() + (grid.h() - thumbH) * (hud.libraryScroll / Math.max(1, totalRows - rows));
            c.rect(trackX, thumbY, 3, thumbH, ACCENT, 1);
        }
        libraryDetail(c, detail, kind, hud, level);
    }

    private void libraryTile(EditorHudCanvas c, LibraryKind kind, int index, float x, float y, float w, float h,
                             LevelEditorController controller, boolean onHotbar) {
        c.rect(x, y, w, h, TILE, 1);
        if (kind == LibraryKind.OBJECT) {
            EditorStockObjectPalette.Entry entry = controller.objectPalette().entries().get(index);
            thumbnail(c, kind, index, x + (w - 46) / 2, y + 2, 46, controller);
            c.fittedText(EditorStockObjectPalette.displayName(entry.label()), x + 5, y + h - 13, w - 10, Size.SMALL, TEXT, 1);
        } else {
            thumbnail(c, kind, index, x + 1, y + 1, w - 2, controller);
            String label = hex(index);
            float lw = EditorHudCanvas.textWidth(label, Size.SMALL) + 3;
            c.rect(x, y + h - 9, lw, 9, SHADOW, .6f);
            c.text(label, x + 1, y + h - 8, Size.SMALL, TEXT, 1);
        }
        c.frame(x, y, w, h, 1, LINE, .8f);
        if (onHotbar) c.rect(x + w - 6, y + 2, 4, 4, ACCENT, 1);
    }

    private void libraryDetail(EditorHudCanvas c, Rect detail, LibraryKind kind, EditorHud hud, MutableLevel level) {
        c.rect(detail.x(), detail.y(), detail.w(), detail.h(), PANEL_RAISED, 1);
        int index = hud.libraryValue(kind, hud.libraryCursor);
        float preview = Math.min(detail.w() - 20, 132);
        float px = detail.x() + (detail.w() - preview) / 2f, py = detail.y() + 10;
        c.rect(px - 2, py - 2, preview + 4, preview + 4, SHADOW, .7f);
        c.rect(px, py, preview, preview, TILE, 1);
        thumbnail(c, kind, index, px, py, preview, hud.controller());
        float y = py + preview + 10;
        String name = entryName(kind, index, hud.controller());
        c.fittedText(name, detail.x() + 10, y, detail.w() - 20, Size.BODY, TEXT, 1);
        y += 14;
        if (level != null && kind != LibraryKind.OBJECT) {
            int uses = usage(kind, index, level);
            String usageText = uses == 0 ? "Not used yet" : "Used " + uses + (uses == 1 ? " time" : " times");
            c.text(usageText, detail.x() + 10, y, Size.SMALL, TEXT_DIM, 1);
            y += 11;
        }
        int[] bar = hud.hotbar.slots(kind);
        int slot = -1;
        for (int i = 0; i < bar.length; i++) if (bar[i] == index) slot = i;
        c.text(slot >= 0 ? "On the hotbar, slot " + ((slot + 1) % 10) : "Replaces the highlighted slot",
                detail.x() + 10, y, Size.SMALL, slot >= 0 ? ACCENT : TEXT_FAINT, 1);
    }

    private int usage(LibraryKind kind, int index, MutableLevel level) {
        long key = (long) kind.ordinal() << 32 | index;
        if (key == usageKey) return usage;
        int uses = 0;
        if (kind == LibraryKind.BLOCK) {
            var map = level.getMap();
            for (int layer = 0; layer < map.getLayerCount(); layer++)
                for (int y = 0; y < map.getHeight(); y++)
                    for (int x = 0; x < map.getWidth(); x++)
                        if (Byte.toUnsignedInt(map.getValue(layer, x, y)) == index) uses++;
        } else if (kind == LibraryKind.CHUNK) {
            for (int i = 0; i < Math.min(256, level.getBlockCount()); i++) {
                Block block = level.getBlock(i);
                for (int y = 0; y < block.getGridSide(); y++)
                    for (int x = 0; x < block.getGridSide(); x++)
                        if (block.getChunkDesc(x, y).getChunkIndex() == index) uses++;
            }
        } else if (kind == LibraryKind.PATTERN) {
            for (int i = 0; i < Math.min(1024, level.getChunkCount()); i++)
                for (int y = 0; y < 2; y++)
                    for (int x = 0; x < 2; x++)
                        if (level.getChunk(i).getPatternDesc(x, y).getPatternIndex() == index) uses++;
        }
        usageKey = key;
        usage = uses;
        return uses;
    }

    // ------------------------------------------------------------------ wheel

    private void wheel(EditorHudCanvas c, EditorHudLayout layout, EditorHud hud) {
        dim(c, layout, .45f);
        Rect g = layout.game();
        float cx = g.x() + g.w() / 2f, cy = g.y() + g.h() / 2f;
        List<EditorHud.WheelItem> items = hud.wheelItems;
        int count = items.size();
        float tileW = 84, tileH = 30;
        float radiusY = Math.min(100, g.h() / 2f - tileH / 2f - 6);
        float radiusX = Math.min(radiusY * 1.6f, g.w() / 2f - tileW / 2f - 6);
        // Spokes from the hub to the highlighted tool.
        if (hud.wheelSlot >= 0) {
            double angle = hud.wheelSlot * 2 * Math.PI / count;
            for (int i = 2; i <= 5; i++) {
                float t = i / 7f;
                c.rect(cx + (float) Math.sin(angle) * radiusX * t - 1.5f, cy - (float) Math.cos(angle) * radiusY * t - 1.5f,
                        3, 3, GOLD, .9f);
            }
        }
        for (int i = 0; i < count; i++) {
            double angle = i * 2 * Math.PI / count;
            float tx = cx + (float) Math.sin(angle) * radiusX - tileW / 2f;
            float ty = cy - (float) Math.cos(angle) * radiusY - tileH / 2f;
            boolean selected = i == hud.wheelSlot;
            float grow = selected ? 3 : 0;
            c.panel(tx - grow, ty - grow, tileW + 2 * grow, tileH + 2 * grow, selected ? ACCENT : PANEL_RAISED, 1);
            EditorHud.WheelItem item = items.get(i);
            c.text(item.label(), tx + (tileW - EditorHudCanvas.textWidth(item.label(), Size.BODY)) / 2f, ty + 5,
                    Size.BODY, selected ? 0xFFFFFF : TEXT, 1);
            String detail = EditorHudCanvas.fit(item.detail(), tileW - 4, Size.SMALL);
            c.text(detail, tx + (tileW - EditorHudCanvas.textWidth(detail, Size.SMALL)) / 2f, ty + 18,
                    Size.SMALL, selected ? 0xDDE8FF : TEXT_DIM, 1);
            if (selected) c.frame(tx - grow - 1, ty - grow - 1, tileW + 2 * grow + 2, tileH + 2 * grow + 2, 2, GOLD, 1);
            hits.add(new Hit(new Rect(tx, ty, tileW, tileH), Modal.WHEEL, EditorHud.HIT_WHEEL, i));
        }
        float hubW = 84, hubH = 30;
        c.panel(cx - hubW / 2f, cy - hubH / 2f, hubW, hubH, PANEL, 1);
        c.text("TOOLS", cx - EditorHudCanvas.textWidth("TOOLS", Size.SMALL) / 2f, cy - 11, Size.SMALL, TEXT_FAINT, 1);
        if (hud.wheelSlot >= 0 && hud.wheelSlot < count) {
            String name = items.get(hud.wheelSlot).label();
            c.text(name, cx - EditorHudCanvas.textWidth(name, Size.BODY) / 2f, cy + 1, Size.BODY, GOLD, 1);
        }
    }

    // ------------------------------------------------------------------ menu

    private void palette(EditorHudCanvas c, EditorHudLayout layout, EditorHud hud) {
        dim(c, layout, .6f);
        float swatch = 25, gridX = 30;
        float w = Math.min(gridX + 16 * swatch + 12, layout.width() - 24), h = 216;
        float x = (layout.width() - w) / 2, y = (layout.height() - h) / 2;
        c.panel(x, y, w, h, PANEL, .99f);
        c.text("Level palette", x + 12, y + 10, Size.BODY, TEXT, 1);
        c.text("Genesis 9-bit color, 4 lines of 16", x + 18 + EditorHudCanvas.textWidth("Level palette", Size.BODY),
                y + 12, Size.SMALL, TEXT_DIM, 1);
        var level = hud.controller().currentLevel();
        int line = hud.paletteCell / 16, color = hud.paletteCell % 16;
        for (int row = 0; row < 4; row++) {
            float cy = y + 34 + row * swatch;
            c.text(Integer.toString(row), x + 14, cy + 8, Size.SMALL, row == line ? GOLD : TEXT_FAINT, 1);
            for (int i = 0; i < 16; i++) {
                c.rect(x + gridX + i * swatch, cy, swatch - 2, swatch - 2, rgb(level, row, i), 1);
            }
        }
        float sx = x + gridX + color * swatch, sy = y + 34 + line * swatch;
        c.frame(sx - 2, sy - 2, swatch + 2, swatch + 2, 1, SHADOW, .9f);
        c.frame(sx - 1, sy - 1, swatch, swatch, 2, GOLD, 1);
        // The selected entry: a large swatch, where it lives and its three 0-7 hardware channels.
        float py = y + 144;
        int value = rgb(level, line, color);
        c.rect(x + 12, py, 40, 40, SHADOW, .7f);
        c.rect(x + 13, py + 1, 38, 38, value, 1);
        boolean editable = hud.controller().isPaletteCellEditable(line, color);
        c.text("Color " + color + " of line " + line, x + 62, py + 2, Size.SMALL, TEXT, 1);
        c.text(editable ? "Editable" : "Host owned, read only", x + 62, py + 14, Size.SMALL, editable ? TEXT_DIM : GOLD, 1);
        var entry = level.getPalette(line).getColor(color);
        int[] components = {Byte.toUnsignedInt(entry.r), Byte.toUnsignedInt(entry.g), Byte.toUnsignedInt(entry.b)};
        String[] names = {"Red", "Green", "Blue"};
        int[] tints = {0xFF6B6B, 0x5BD68A, ACCENT};
        float cellW = 82, cx0 = x + w - 12 - 3 * cellW;
        for (int i = 0; i < 3; i++) {
            float cx = cx0 + i * cellW;
            boolean selected = i == hud.paletteComponent;
            if (selected) selectionBand(c, cx, py - 2, cellW - 6, 44);
            int step = (components[i] * 7 + 127) / 255;
            c.text(names[i], cx + 8, py + 3, Size.SMALL, selected ? 0xFFFFFF : TEXT, 1);
            String number = Integer.toString(step);
            c.text(number, cx + cellW - 14 - EditorHudCanvas.textWidth(number, Size.SMALL), py + 3, Size.SMALL,
                    selected ? 0xFFFFFF : TEXT_DIM, 1);
            for (int s = 0; s < 8; s++) {
                c.rect(cx + 8 + s * 8, py + 16, 6, 10, s < step ? tints[i] : LINE, s < step ? 1 : .7f);
            }
        }
    }

    private static int rgb(MutableLevel level, int line, int color) {
        var rgb = level.getPalette(line).getColor(color);
        return Byte.toUnsignedInt(rgb.r) << 16 | Byte.toUnsignedInt(rgb.g) << 8 | Byte.toUnsignedInt(rgb.b);
    }

    private void settings(EditorHudCanvas c, EditorHudLayout layout, EditorHud hud) {
        var workspace = hud.controller().projects();
        if (workspace == null || workspace.document() == null) return;
        dim(c, layout, .6f);
        var rows = workspace.settingsRows();
        List<String> labels = new ArrayList<>(), values = new ArrayList<>();
        for (var row : rows) { labels.add(row.label()); values.add(row.value()); }
        formSheet(c, layout, "Level settings", "", labels, values, hud.settingsIndex,
                "Each change is undoable. Playtest applies the environment.");
    }

    private void properties(EditorHudCanvas c, EditorHudLayout layout, EditorHud hud) {
        var spawn = hud.controller().selectedObjectSpawn();
        if (spawn == null) return;
        var fields = hud.controller().selectedObjectProperties().editableFields();
        var gizmos = hud.controller().selectedGizmoFields();
        List<String> labels = new ArrayList<>(), values = new ArrayList<>();
        if (gizmos.isEmpty()) {
            for (var field : fields) { labels.add(field.label()); values.add(field.display(spawn.subtype())); }
        } else {
            for (var gizmo : gizmos) { labels.add(gizmo.label()); values.add(readable(gizmo.value(spawn.authoredProperties()))); }
        }
        formSheet(c, layout, "Object properties", underCursor(hud.controller(), hud.controller().currentLevel()),
                labels, values, hud.propertyIndex, "");
    }

    /**
     * A centred field sheet: a bold heading, light rows with values in one column after the widest
     * label, and arrows on the selected value to show that Left/Right change it.
     */
    private static void formSheet(EditorHudCanvas c, EditorHudLayout layout, String title, String subtitle,
                                  List<String> labels, List<String> values, int index, String footnote) {
        int count = labels.size();
        int visible = Math.min(count, Math.max(3, (layout.height() - 120) / SHEET_ROW));
        int first = Math.clamp(index - visible / 2, 0, Math.max(0, count - visible));
        float w = Math.min(460, layout.width() - 24);
        float h = 42 + visible * SHEET_ROW + (footnote.isEmpty() ? 6 : 22);
        float x = (layout.width() - w) / 2, y = (layout.height() - h) / 2;
        c.panel(x, y, w, h, PANEL, .99f);
        c.text(title, x + 12, y + 10, Size.BODY, TEXT, 1);
        if (!subtitle.isEmpty()) {
            float sx = x + 20 + EditorHudCanvas.textWidth(title, Size.BODY);
            c.fittedText(subtitle, sx, y + 12, x + w - 12 - sx, Size.SMALL, TEXT_DIM, 1);
        }
        float labelWidth = 0;
        for (String label : labels) labelWidth = Math.max(labelWidth, EditorHudCanvas.textWidth(label, Size.SMALL));
        float valueX = x + 12 + Math.min(labelWidth + 24, w * .5f);
        float valueRoom = x + w - 30 - valueX;
        for (int i = first; i < first + visible; i++) {
            float rowY = y + 34 + (i - first) * SHEET_ROW;
            boolean selected = i == index;
            if (selected) selectionBand(c, x + 6, rowY, w - 12, SHEET_ROW - 2);
            c.fittedText(labels.get(i), x + 14, rowY + 4, valueX - x - 26, Size.SMALL, selected ? 0xFFFFFF : TEXT, 1);
            String value = EditorHudCanvas.fit(values.get(i), valueRoom - 20, Size.SMALL);
            if (selected) {
                c.text("<", valueX, rowY + 4, Size.SMALL, ACCENT, 1);
                c.text(value, valueX + 10, rowY + 4, Size.SMALL, 0xFFFFFF, 1);
                c.text(">", valueX + 14 + EditorHudCanvas.textWidth(value, Size.SMALL), rowY + 4, Size.SMALL, ACCENT, 1);
            } else {
                c.text(value, valueX + 10, rowY + 4, Size.SMALL, TEXT_DIM, 1);
            }
        }
        scrollbar(c, x + w - 9, y + 34, visible * SHEET_ROW - 2, first, visible, count);
        if (!footnote.isEmpty()) c.fittedText(footnote, x + 14, y + h - 16, w - 28, Size.SMALL, TEXT_FAINT, 1);
    }

    /** Menu column widths: rows fit the widest label, then one left-aligned column of shortcut caps. */
    record MenuGeometry(float width, float labelRoom, float capColumn) { }

    static MenuGeometry menuGeometry(List<EditorHud.MenuItem> items, float layoutWidth) {
        float label = 0, caps = 0;
        for (EditorHud.MenuItem item : items) {
            if (item.header()) continue;
            label = Math.max(label, EditorHudCanvas.textWidth(item.label(), Size.SMALL));
            if (!item.detail().isEmpty()) caps = Math.max(caps, keycapWidth(item.detail()));
        }
        float capColumn = caps == 0 ? 0 : caps + MENU_CAP_GAP;
        // List inset 6, row indent 10, label, the cap column, right inset 8 and the scrollbar lane.
        float wanted = 12 + 10 + label + capColumn + 8 + MENU_SCROLL_LANE;
        float width = Math.max(MENU_MIN_WIDTH, Math.min(layoutWidth - 40, (float) Math.ceil(wanted)));
        return new MenuGeometry(width, width - 12 - 10 - capColumn - 8 - MENU_SCROLL_LANE, caps);
    }

    private void menu(EditorHudCanvas c, EditorHudLayout layout, EditorHud hud) {
        dim(c, layout, .6f);
        float rowH = 15;
        List<EditorHud.MenuItem> items = hud.menuItems;
        MenuGeometry geometry = menuGeometry(items, layout.width());
        float w = geometry.width();
        float maxH = layout.height() - EditorHudLayout.TOP_BAR - EditorHudLayout.PROMPT_BAR - 24;
        int visible = Math.max(4, (int) ((maxH - 44) / rowH));
        int shown = Math.min(visible, items.size());
        float h = 44 + shown * rowH;
        float x = (layout.width() - w) / 2f, y = EditorHudLayout.TOP_BAR + 12 + (maxH - h) / 2f;
        c.panel(x, y, w, h, PANEL, .99f);
        c.text("MENU", x + 12, y + 8, Size.LARGE, TEXT, 1);
        float target = hud.menuScroll;
        if (hud.menuIndex < target + 1) target = hud.menuIndex - 1;
        if (hud.menuIndex > target + shown - 2) target = hud.menuIndex - shown + 2;
        target = Math.max(0, Math.min(items.size() - shown, target));
        hud.menuScroll += (target - hud.menuScroll) * .35f;
        if (Math.abs(target - hud.menuScroll) < .01f) hud.menuScroll = target;
        Rect list = new Rect(x + 6, y + 36, w - 12 - MENU_SCROLL_LANE, shown * rowH);
        float capX = list.right() - 6 - geometry.capColumn();
        c.clip(list);
        for (int i = Math.max(0, (int) hud.menuScroll); i < Math.min(items.size(), (int) hud.menuScroll + shown + 1); i++) {
            EditorHud.MenuItem item = items.get(i);
            float ry = list.y() + (i - hud.menuScroll) * rowH;
            if (item.header()) {
                String header = tag(item.label());
                c.text(header, list.x() + 6, ry + 4, Size.SMALL, TEXT_FAINT, 1);
                float lx = list.x() + 12 + EditorHudCanvas.textWidth(header, Size.SMALL);
                c.rect(lx, ry + 8, list.right() - lx - 6, 1, LINE, 1);
                continue;
            }
            boolean selected = i == hud.menuIndex;
            if (selected) selectionBand(c, list.x(), ry, list.w(), rowH - 1);
            c.fittedText(item.label(), list.x() + 10, ry + 3, geometry.labelRoom(), Size.SMALL, selected ? 0xFFFFFF : TEXT, 1);
            if (!item.detail().isEmpty()) keycap(c, item.detail(), capX, ry + 7);
            hit(new Rect(list.x(), ry, list.w(), rowH), list, Modal.MENU, EditorHud.HIT_MENU, i);
        }
        c.unclip();
        scrollbar(c, list.right() + 3, list.y(), list.h(), hud.menuScroll, shown, items.size());
    }

    private static void selectionBand(EditorHudCanvas c, float x, float y, float w, float h) {
        c.rect(x, y, w, h, ACCENT_DARK, 1);
        c.rect(x, y, 3, h, GOLD, 1);
    }

    /** A thin track and thumb beside a list that holds more rows than it shows. */
    private static void scrollbar(EditorHudCanvas c, float x, float y, float h, float first, int visible, int count) {
        if (count <= visible) return;
        c.rect(x, y, 2, h, LINE, .8f);
        float thumb = Math.max(10, h * visible / count);
        c.rect(x, y + (h - thumb) * Math.clamp(first / Math.max(1, count - visible), 0, 1), 2, thumb, ACCENT, 1);
    }

    /** A five-unit pixel chevron; {@code left} points it back. */
    private static void chevron(EditorHudCanvas c, float x, float y, int rgb, boolean left) {
        for (int i = 0; i < 3; i++) {
            float dx = left ? 2 - i : i;
            c.rect(x + dx, y + i, 1, 1, rgb, 1);
            if (i < 2) c.rect(x + dx, y + 4 - i, 1, 1, rgb, 1);
        }
    }

    private static void chevron(EditorHudCanvas c, float x, float y, int rgb) { chevron(c, x, y, rgb, false); }

    private static String tag(String text) { return text.toUpperCase(Locale.ROOT); }

    /** Authored enum constants (NONE, PLAYER_RIGHT) read as words; numbers and names are unchanged. */
    static String readable(Object value) {
        String text = String.valueOf(value);
        if (!text.matches("[A-Z]{2,}(_[A-Z0-9]+)*")) return text;
        String words = text.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    // ------------------------------------------------------------------ helpers

    /** Records a pointer target, trimmed to the visible part of a scrolled list or grid. */
    private void hit(Rect area, Rect visible, Modal modal, int kind, int index) {
        float left = Math.max(area.x(), visible.x()), top = Math.max(area.y(), visible.y());
        float right = Math.min(area.right(), visible.right()), bottom = Math.min(area.y() + area.h(), visible.y() + visible.h());
        if (right > left && bottom > top) hits.add(new Hit(new Rect(left, top, right - left, bottom - top), modal, kind, index));
    }

    private static void dim(EditorHudCanvas c, EditorHudLayout layout, float alpha) {
        c.rect(0, EditorHudLayout.TOP_BAR, layout.width(), layout.height() - EditorHudLayout.TOP_BAR - EditorHudLayout.PROMPT_BAR,
                SHADOW, alpha);
    }

    private static String kindTitle(LibraryKind kind) {
        return switch (kind) {
            case BLOCK -> "Blocks";
            case CHUNK -> "Chunks";
            case PATTERN -> "Patterns";
            case OBJECT -> "Objects";
            case NONE -> "";
        };
    }

    static String entryName(LibraryKind kind, int value, LevelEditorController controller) {
        if (value < 0) return "Empty slot";
        return switch (kind) {
            case BLOCK -> "Block " + hex(value);
            case CHUNK -> "Chunk " + hex(value);
            case PATTERN -> "Pattern " + hex(value);
            case OBJECT -> controller.objectPaletteOrNull() == null || value >= controller.objectPaletteOrNull().entries().size()
                    ? "Object" : controller.objectPaletteOrNull().entries().get(value).label();
            case NONE -> "";
        };
    }

    private static String brushDetail(LibraryKind kind, LevelEditorController controller) {
        return switch (kind) {
            case BLOCK -> "Layer " + (controller.activeLayer() == 0 ? "FG" : "BG");
            case PATTERN -> {
                Integer raw = controller.selectedPatternRaw();
                yield raw == null ? "" : "Palette line " + ((raw >>> 13) & 3);
            }
            case OBJECT -> controller.objectPaletteOrNull() == null ? ""
                    : "Subtype " + "%02X".formatted(controller.objectPaletteOrNull().selectedSubtype());
            default -> "";
        };
    }

    private static String underCursor(LevelEditorController controller, MutableLevel level) {
        if (level == null) return "";
        if (controller.depth() == EditorHierarchyDepth.BLOCK) {
            Integer block = controller.selection().selectedBlock();
            if (block == null || block >= level.getBlockCount()) return "";
            return "Chunk " + hex(level.getBlock(block).getChunkDesc(controller.selectedBlockCellX(),
                    controller.selectedBlockCellY()).getChunkIndex());
        }
        if (controller.depth() == EditorHierarchyDepth.CHUNK) {
            Integer chunk = controller.selection().selectedChunk();
            if (chunk == null || chunk >= level.getChunkCount()) return "";
            return "Pattern " + hex(level.getChunk(chunk).getPatternDesc(controller.selectedChunkCellX(),
                    controller.selectedChunkCellY()).getPatternIndex());
        }
        if (controller.spawnEditMode() != EditorSpawnEditMode.TILES) {
            var selected = controller.selectedObjectSpawn();
            if (selected == null) return "None";
            if (selected.objectKey() != null) return EditorStockObjectPalette.displayName(selected.objectKey());
            var palette = controller.objectPaletteOrNull();
            return palette == null ? "Object" : EditorStockObjectPalette.displayName(palette.entries().get(selected.objectId()).label());
        }
        var cursor = controller.worldCursor();
        var map = level.getMap();
        int size = level.getBlockPixelSize();
        int mx = Math.max(0, Math.min(map.getWidth() - 1, cursor.x() / size));
        int my = Math.max(0, Math.min(map.getHeight() - 1, cursor.y() / size));
        return "Block " + hex(Byte.toUnsignedInt(map.getValue(controller.activeLayer(), mx, my)));
    }

    private static boolean contains(int[] values, int value) {
        for (int v : values) if (v == value) return true;
        return false;
    }

    static String hex(Integer value) {
        return value == null ? "-" : "$" + Integer.toHexString(value).toUpperCase(Locale.ROOT);
    }

    @Override
    public void close() {
        atlas.close();
        ghostAtlas.close();
        objectArt.close();
    }
}
