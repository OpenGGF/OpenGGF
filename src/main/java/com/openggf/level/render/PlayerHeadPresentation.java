package com.openggf.level.render;

import com.openggf.graphics.SpritePresentation;
import com.openggf.graphics.SpritePresentation.*;
import com.openggf.level.Pattern;
import java.util.ArrayList;
import java.util.HashMap;

/** Anatomy composition after native SAT admission, using only frozen production indexed pixels. */
final class PlayerHeadPresentation {
    private PlayerHeadPresentation() { }
    static Frame compose(Frame nativeFrame, int cameraX, int cameraY) {
        if (nativeFrame.tiles().stream().noneMatch(t -> t.subject().head() != null)) return nativeFrame;
        var tiles = new ArrayList<Tile>();
        var headTiles = new ArrayList<Tile>();
        var foregroundTiles = new ArrayList<Tile>();
        var versions = new HashMap<>(nativeFrame.patternVersions());
        int[] before = new int[nativeFrame.tiles().size() + 1];
        var generatedByBank = new HashMap<Integer,Integer>();
        var primitiveBoundaries = nativeFrame.primitives().stream().map(Primitive::beforeTile)
                .collect(java.util.stream.Collectors.toSet());
        Tile occurrence = null;
        for (int index = 0; index < nativeFrame.tiles().size(); index++) {
            Tile tile = nativeFrame.tiles().get(index);
            HeadTransform head = tile.subject().head();
            if (primitiveBoundaries.contains(index) || !sameDraw(occurrence, tile)) {
                tiles.addAll(headTiles); headTiles.clear();
                tiles.addAll(foregroundTiles); foregroundTiles.clear();
                occurrence = head == null ? null : tile;
            }
            before[index] = tiles.size();
            if (head == null || tile.subject().suppressed()) { tiles.add(tile); continue; }
            PatternVersion source = nativeFrame.patternVersions().get(tile.patternId());
            if (source == null) source = head.patterns().get(tile.patternId());
            if (source == null) throw new IllegalStateException("Head presentation lacks admitted ROM pixels");
            Pattern body = SpritePresentationRenderer.pattern(source), enlarged = new Pattern(), foreground = new Pattern();
            boolean changed = false, foregroundChanged = false;
            for (int y = tile.rowStart(); y < tile.rowEnd(); y++) for (int x = 0; x < 8; x++) {
                int sourceX = tile.hFlip() ? 7 - x : x, sourceY = tile.vFlip() ? 7 - y : y;
                int relativeX = (int) tile.x() + cameraX + x - head.originX();
                int relativeY = (int) tile.y() + cameraY + y - head.originY();
                int canonicalX = head.hFlip() ? -relativeX - 1 : relativeX;
                int canonicalY = head.vFlip() ? -relativeY - 1 : relativeY;
                byte pixel = body.getPixel(sourceX, sourceY);
                if (pixel != 0 && head.mask().contains(head.piece(), canonicalX, canonicalY)) {
                    changed = true; enlarged.setPixel(sourceX, sourceY, pixel); body.setPixel(sourceX, sourceY, (byte) 0);
                } else if (pixel != 0 && head.mask().foregroundContains(head.piece(), canonicalX, canonicalY)) {
                    foregroundChanged = true; foreground.setPixel(sourceX, sourceY, pixel);
                    body.setPixel(sourceX, sourceY, (byte) 0);
                }
            }
            if (!changed && !foregroundChanged) { tiles.add(withoutHead(tile)); continue; }
            int generated = generatedByBank.getOrDefault(head.fragmentBase(), head.fragmentBase());
            // Each draw owns 256 cutout companions; native admission still determines source pixels.
            if (generated + 1 + (changed ? 1 : 0) + (foregroundChanged ? 1 : 0) > head.fragmentBase() + 256)
                throw new IllegalStateException("Head presentation fragment limit exceeded");
            Subject subject = new Subject(tile.subject().id(), tile.subject().part(), false);
            versions.put(generated, SpritePresentationRenderer.version(body));
            tiles.add(copy(tile, generated++, tile.x(), tile.y(), 8, 8, tile.rowStart(), tile.rowEnd(), subject));
            if (changed) {
                versions.put(generated, SpritePresentationRenderer.version(enlarged));
                float scale = head.percent() / 100f;
                float anchorX = head.originX() - cameraX + (head.hFlip() ? -head.mask().anchorX() : head.mask().anchorX());
                float anchorY = head.originY() - cameraY + (head.vFlip() ? -head.mask().anchorY() : head.mask().anchorY());
                headTiles.add(copy(tile, generated++, anchorX + (tile.x() - anchorX) * scale,
                        anchorY + (tile.y() - anchorY) * scale, 8 * scale, 8 * scale, 0, 8, subject));
            }
            if (foregroundChanged) {
                versions.put(generated, SpritePresentationRenderer.version(foreground));
                foregroundTiles.add(copy(tile, generated++, tile.x(), tile.y(), 8, 8, tile.rowStart(), tile.rowEnd(), subject));
            }
            generatedByBank.put(head.fragmentBase(), generated);
        }
        tiles.addAll(headTiles);
        tiles.addAll(foregroundTiles);
        before[nativeFrame.tiles().size()] = tiles.size();
        var primitives = nativeFrame.primitives().stream().map(p -> new Primitive(before[p.beforeTile()],
                p.layer(), p.primitive(), p.subject())).toList();
        return new Frame(tiles, primitives, versions);
    }
    /** Enlargement makes disjoint native tiles overlap. Finish this draw's body before its head,
     * then its native foreground limbs. No fragment crosses an actor, render bucket or draw occurrence. */
    private static boolean sameDraw(Tile a, Tile b) {
        if (a == null || a.subject().head() == null || b.subject().head() == null) return false;
        HeadTransform x = a.subject().head(), y = b.subject().head();
        return a.layer() == b.layer() && a.priority() == b.priority()
                && a.priorityShader() == b.priorityShader() && a.occlusionMask() == b.occlusionMask()
                && a.subject().id().equals(b.subject().id())
                && x.mask() == y.mask() && x.fragmentBase() == y.fragmentBase()
                && x.originX() == y.originX() && x.originY() == y.originY()
                && x.hFlip() == y.hFlip() && x.vFlip() == y.vFlip();
    }
    private static Tile withoutHead(Tile tile) {
        return copy(tile, tile.patternId(), tile.x(), tile.y(), tile.width(), tile.height(), tile.rowStart(), tile.rowEnd(),
                new Subject(tile.subject().id(), tile.subject().part(), tile.subject().suppressed()));
    }
    private static Tile copy(Tile source, int id, float x, float y, float width, float height,
                             int rowStart, int rowEnd, Subject subject) {
        return new Tile(source.layer(), id, source.palette(), source.hFlip(), source.vFlip(), source.priority(),
                x, y, width, height, source.priorityShader(), source.occlusionMask(), source.ghost(), source.ghostAlpha(),
                rowStart, rowEnd, subject);
    }
}
