package com.openggf.level.objects;

import com.openggf.level.objects.ObjectVisualFacing.Basis;

import com.openggf.graphics.*;
import com.openggf.level.PatternDesc;
import com.openggf.level.render.*;

import java.util.*;

import static com.openggf.level.objects.StageplayObjectDirection.*;

/** Directed render entries and graphics leases. No native object routine executes here. */
final class StageplayObjectDraw {
    private final ObjectManager objects;
    private final IdentityHashMap<ObjectInstance, Directed> membership;
    private final LinkedHashMap<String, StageplayObjectDirection.VisualObject> visuals;
    private final Map<List<PatternPixels>, SharedArt> sharedArt = new HashMap<>();
    private final Map<List<PaletteColors>, SharedColors> sharedColors = new HashMap<>();

    StageplayObjectDraw(StageplayObjectDirection owner) {
        objects = owner.objects;
        membership = owner.membership;
        visuals = owner.visuals;
    }

    private static final class SharedArt {
        final List<PatternPixels> key;
        ObjectVisualGraphicsAccess.ArtBank bank;
        int references;

        SharedArt(List<PatternPixels> key) {
            this.key = key;
        }
    }

    private static final class SharedColors {
        final List<PaletteColors> key;
        ScopedPaletteGraphicsAccess.Bank bank;
        int references;

        SharedColors(List<PaletteColors> key) {
            this.key = key;
        }
    }

    final class Directed implements AutoCloseable {
        final ObjectInstance root;
        final VisualTemplate template;
        Direction direction;
        SharedArt art;
        SharedColors colors;

        Directed(ObjectInstance root, VisualTemplate template, Direction direction) {
            this.root = root;
            this.template = template;
            this.direction = direction;
        }

        public void close() {
            if (art != null && --art.references == 0) {
                sharedArt.remove(art.key, art);
                ObjectVisualGraphicsAccess.releaseArt(art.bank, art);
            }
            if (colors != null && --colors.references == 0) {
                sharedColors.remove(colors.key, colors);
                ScopedPaletteGraphicsAccess.release(colors.bank, colors);
            }
            art = null;
            colors = null;
        }
    }

    boolean addRenderEntries(
            ObjectInstance object,
            List<ObjectRenderParts.RenderEntry>[] unified,
            List<ObjectRenderParts.RenderEntry>[] low,
            List<ObjectRenderParts.RenderEntry>[] high) {
        Directed state = membership.get(object);
        if (state == null) {
            return false;
        }
        if (state.root != object) {
            return true;
        }
        Set<String> entries = new HashSet<>();
        for (var part : state.template.parts()) {
            int slot = renderSlot(state, part);
            String key = part.bucket() + ":" + part.highPriority() + ":" + slot;
            if (!entries.add(key)) {
                continue;
            }
            int index = RenderPriority.clamp(part.bucket()) - RenderPriority.MIN;
            var entry = new ObjectRenderParts.RenderEntry(object, part.highPriority(), slot);
            unified[index].add(entry);
            (part.highPriority() ? high[index] : low[index]).add(entry);
        }
        return true;
    }

    void addVisualRenderEntries(
            List<ObjectRenderParts.RenderEntry>[] unified,
            List<ObjectRenderParts.RenderEntry>[] low,
            List<ObjectRenderParts.RenderEntry>[] high) {
        for (var visual : visuals.values()) {
            addRenderEntries(visual, unified, low, high);
        }
    }

    private int renderSlot(Directed state, VisualPart part) {
        if (!(state.root instanceof VisualObject)) {
            return part.slotOrder();
        }
        int first = state.template.parts().stream().mapToInt(VisualPart::slotOrder).min().orElse(0);
        return Integer.MAX_VALUE - 65536 + Math.min(65535, Math.max(0, part.slotOrder() - first));
    }

    boolean drawReplacement(ObjectInstance object, int bucket, boolean high, int slot) {
        Directed state = membership.get(object);
        if (state == null) {
            return false;
        }
        if (object == state.root) {
            draw(state, bucket, high, slot);
        }
        return true;
    }

    private void draw(Directed state, int bucket, boolean high, int slot) {
        if (!state.direction.visible()) {
            return;
        }
        GraphicsManager graphics = objects.services().graphicsManager();
        if (state.art == null) {
            state.art = sharedArt.computeIfAbsent(state.template.patterns(), SharedArt::new);
            state.art.references++;
        }
        if (!ObjectVisualGraphicsAccess.validArt(state.art.bank, graphics, state.art)) {
            state.art.bank =
                    ObjectVisualGraphicsAccess.acquireArt(
                            graphics, state.art, state.template.patterns().size());
            int base = ObjectVisualGraphicsAccess.artBase(state.art.bank, graphics, state.art);
            graphics.beginPatternAtlasBatch();
            try {
                for (int i = 0; i < state.template.patterns().size(); i++) {
                    graphics.cachePatternTexture(
                            SpritePresentationRenderer.pattern(
                                    state.template.patterns().get(i).version()),
                            base + i);
                }
            } finally {
                graphics.endPatternAtlasBatch();
            }
        }
        if (state.colors == null) {
            state.colors =
                    sharedColors.computeIfAbsent(state.template.palettes(), SharedColors::new);
            state.colors.references++;
        }
        if (!ScopedPaletteGraphicsAccess.valid(state.colors.bank, graphics, state.colors)) {
            PaletteView[] palettes = new PaletteView[4];
            for (int i = 0; i < 4; i++) {
                List<Integer> rgb =
                        i < state.template.palettes().size()
                                ? state.template.palettes().get(i).argb()
                                : Collections.nCopies(16, 0);
                palettes[i] =
                        new PaletteView() {
                            public byte red(int c) {
                                return (byte) (rgb.get(c) >> 16);
                            }

                            public byte green(int c) {
                                return (byte) (rgb.get(c) >> 8);
                            }

                            public byte blue(int c) {
                                return (byte) (int) rgb.get(c);
                            }
                        };
            }
            try {
                state.colors.bank =
                        ScopedPaletteGraphicsAccess.acquire(graphics, state.colors, palettes);
            } catch (IllegalStateException full) {
                throw new IllegalStateException(
                        "Directed cast exceeds the bounded distinct palette snapshot budget; reuse"
                                + " an appearance phase or reduce simultaneous palette variants",
                        full);
            }
        }
        int patternBase = ObjectVisualGraphicsAccess.artBase(state.art.bank, graphics, state.art);
        int paletteBase =
                ScopedPaletteGraphicsAccess.base(state.colors.bank, graphics, state.colors);
        if (SpritePresentation.isPreparing(graphics)) {
            for (int i = 0; i < state.template.patterns().size(); i++) {
                SpritePresentation.bindPatternVersion(
                        graphics, patternBase + i, state.template.patterns().get(i).version());
            }
        }
        boolean previousHigh = graphics.getCurrentSpriteHighPriority();
        int previousMask = graphics.getCurrentSpriteTileOcclusionPaletteMask();
        graphics.flushPatternBatch();
        graphics.setCurrentSpriteHighPriority(high);
        graphics.setCurrentSpriteTileOcclusionPaletteMask(
                MultiBucketRenderable.tileOcclusionPaletteMask(high));
        // Own a batch while the sprite-priority shader and this part's mask are active.
        // Direct tile commands resolve their shader later, after the enclosing sprite pass
        // has disabled priority; flushing without reopening would draw through foreground.
        graphics.beginPatternBatch();
        try {
            for (var part : state.template.parts()) {
                if (part.bucket() != bucket
                        || part.highPriority() != high
                        || renderSlot(state, part) != slot) {
                    continue;
                }
                VisualPose pose = selectedPose(part, state.direction);
                List<VisualTile> tiles = pose.tiles();
                for (int index = graphics.isSpriteSatCollectionActive() ? tiles.size() - 1 : 0;
                        graphics.isSpriteSatCollectionActive() ? index >= 0 : index < tiles.size();
                        index += graphics.isSpriteSatCollectionActive() ? -1 : 1) {
                    VisualTile tile = tiles.get(index);
                    graphics.setCurrentSpriteTileOcclusionPaletteMask(tile.occlusionMask());
                    boolean flip = switch (part.facing()) {
                        case LEFT -> state.direction.facing() == Basis.RIGHT;
                        case RIGHT -> state.direction.facing() == Basis.LEFT;
                        case NON_DIRECTIONAL -> false;
                    };
                    int x =
                            (int)
                                    Math.round(
                                            state.direction.x()
                                                    + (flip ? 2 * part.facingPivotX() - tile.x() - tile.width() : tile.x()));
                    int y = (int) Math.round(state.direction.y() + tile.y());
                    int pattern = patternBase + tile.pattern(),
                            palette = paletteBase + tile.palette();
                    if (graphics.isSpriteSatCollectionActive()) {
                        graphics.submitSpriteSatPiece(
                                new SpritePieceRenderer.PreparedPiece(
                                        x,
                                        y,
                                        1,
                                        1,
                                        pattern,
                                        pattern & 0x7ff,
                                        palette,
                                        tile.flipX() ^ flip,
                                        tile.flipY(),
                                        tile.priority(),
                                        high,
                                        0,
                                        1));
                        ObjectVisualGraphicsAccess.attachLastVisualMask(
                                graphics, tile.occlusionMask());
                        ObjectVisualGraphicsAccess.clipLastSatTile(
                                graphics, tile.rowStart(), tile.rowEnd());
                    } else {
                        PatternDesc descriptor = new PatternDesc();
                        descriptor.setPaletteIndex(palette);
                        descriptor.setHFlip(tile.flipX() ^ flip);
                        descriptor.setVFlip(tile.flipY());
                        descriptor.setPriority(tile.priority());
                        if (tile.rowStart() != 0 || tile.rowEnd() != 8) {
                            var rows =
                                    new SpritePresentation.Tile(
                                            SpritePresentation.Layer.OBJECT,
                                            pattern,
                                            palette,
                                            tile.flipX() ^ flip,
                                            tile.flipY(),
                                            tile.priority(),
                                            x,
                                            y,
                                            8,
                                            8,
                                            tile.priorityShader(),
                                            tile.occlusionMask(),
                                            false,
                                            1,
                                            tile.rowStart(),
                                            tile.rowEnd());
                            SpritePresentation.renderTileRows(graphics, rows, descriptor, x, y);
                        } else {
                            graphics.renderPatternWithId(pattern, descriptor, x, y);
                        }
                    }
                }
            }
        } finally {
            graphics.flushPatternBatch();
            graphics.setCurrentSpriteHighPriority(previousHigh);
            graphics.setCurrentSpriteTileOcclusionPaletteMask(previousMask);
            graphics.beginPatternBatch();
        }
    }

}
