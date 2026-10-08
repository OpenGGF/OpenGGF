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
        var versions = new HashMap<>(nativeFrame.patternVersions());
        int[] before = new int[nativeFrame.tiles().size() + 1];
        var generatedByBank = new HashMap<Integer,Integer>();
        for (int index = 0; index < nativeFrame.tiles().size(); index++) {
            before[index] = tiles.size();
            Tile tile = nativeFrame.tiles().get(index);
            HeadTransform head = tile.subject().head();
            if (head == null || tile.subject().suppressed()) { tiles.add(tile); continue; }
            PatternVersion source = nativeFrame.patternVersions().get(tile.patternId());
            if (source == null) source = head.patterns().get(tile.patternId());
            if (source == null) throw new IllegalStateException("Head presentation lacks admitted ROM pixels");
            Pattern body = SpritePresentationRenderer.pattern(source), enlarged = new Pattern();
            boolean changed = false;
            for (int y = tile.rowStart(); y < tile.rowEnd(); y++) for (int x = 0; x < 8; x++) {
                int sourceX = tile.hFlip() ? 7 - x : x, sourceY = tile.vFlip() ? 7 - y : y;
                int relativeX = (int) tile.x() + cameraX + x - head.originX();
                int relativeY = (int) tile.y() + cameraY + y - head.originY();
                int canonicalX = head.hFlip() ? -relativeX - 1 : relativeX;
                int canonicalY = head.vFlip() ? -relativeY - 1 : relativeY;
                byte pixel = body.getPixel(sourceX, sourceY);
                if (pixel != 0 && head.mask().contains(head.piece(), canonicalX, canonicalY)) {
                    changed = true; enlarged.setPixel(sourceX, sourceY, pixel); body.setPixel(sourceX, sourceY, (byte) 0);
                }
            }
            if (!changed) { tiles.add(withoutHead(tile)); continue; }
            int generated = generatedByBank.getOrDefault(head.fragmentBase(), head.fragmentBase());
            // Native Sonic heads occupy fewer than 128 tiles; each source bank owns 256 companions.
            if (generated + 2 > head.fragmentBase() + 256)
                throw new IllegalStateException("Head presentation fragment limit exceeded");
            Subject subject = new Subject(tile.subject().id(), tile.subject().part(), false);
            versions.put(generated, SpritePresentationRenderer.version(body));
            tiles.add(copy(tile, generated++, tile.x(), tile.y(), 8, 8, tile.rowStart(), tile.rowEnd(), subject));
            versions.put(generated, SpritePresentationRenderer.version(enlarged));
            float scale = head.percent() / 100f;
            float anchorX = head.originX() - cameraX + (head.hFlip() ? -head.mask().anchorX() : head.mask().anchorX());
            float anchorY = head.originY() - cameraY + (head.vFlip() ? -head.mask().anchorY() : head.mask().anchorY());
            tiles.add(copy(tile, generated++, anchorX + (tile.x() - anchorX) * scale,
                    anchorY + (tile.y() - anchorY) * scale, 8 * scale, 8 * scale, 0, 8, subject));
            generatedByBank.put(head.fragmentBase(), generated);
        }
        before[nativeFrame.tiles().size()] = tiles.size();
        var primitives = nativeFrame.primitives().stream().map(p -> new Primitive(before[p.beforeTile()],
                p.layer(), p.primitive(), p.subject())).toList();
        return new Frame(tiles, primitives, versions);
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
