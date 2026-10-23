package com.openggf.game.sonic2.competition.interlace;

import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.SpriteSatEntry;
import com.openggf.graphics.SpriteSatInternalAccess;
import com.openggf.graphics.SpriteSatMaskPostProcessor;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.level.render.SpritePieceRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.sonic2.competition.interlace.Sonic2InterlaceDecoder.*;

/**
 * CPU field projection into the production 8x8 atlas and native SAT admission port.
 * Each atlas bank contains alternate rows of all 1024 native 8x16 patterns. Selecting
 * the opposite bank before an 8-row VFlip reproduces a full 16-row native VFlip;
 * projecting or vertically scaling an ordinary 8x8 level cannot do this.
 *
 * <p>The host owns VRAM updates, field cadence, plane composition/priority masks,
 * scissor and the dedicated atlas range registration. This adapter does not activate
 * competition or construct two ordinary worlds. Upload/draw/flush on the GL owner
 * thread before reusing this atlas window for another VRAM image.</p>
 */
public final class Sonic2InterlaceProducer {
    public static final int REQUIRED_PATTERNS = PATTERN_COUNT * 2;
    private final int patternBase;

    /** The host registers this private window with PatternAtlas's raw range API. */
    public Sonic2InterlaceProducer(int patternBase, int capacity) {
        if (patternBase < 0 || capacity < REQUIRED_PATTERNS
                || (long) patternBase + capacity > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Interlace atlas needs at least 2048 pattern IDs");
        this.patternBase = patternBase;
    }

    public record Viewport(int x, int y, int width, int height) {
        public Viewport {
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("Empty field viewport");
        }
    }

    public record PreparedSprite(Sprite nativeSprite, SpriteSatEntry geometry,
                                 List<SpritePieceRenderer.PreparedPiece> fragments) {
        public PreparedSprite {
            Objects.requireNonNull(nativeSprite, "native sprite");
            Objects.requireNonNull(geometry, "geometry");
            fragments = List.copyOf(fragments);
        }
    }

    public record PreparedField(Input input, List<PreparedSprite> sprites) {
        public PreparedField { sprites = List.copyOf(sprites); Objects.requireNonNull(input, "input"); }
    }

    public record PlaneCell(NameWord name, int patternId, int x, int y) { }

    public record PreparedPlane(Sonic2InterlaceVram vram, Field field, Viewport viewport,
                                List<PlaneCell> cells) {
        public PreparedPlane {
            Objects.requireNonNull(vram, "vram"); Objects.requireNonNull(field, "field");
            Objects.requireNonNull(viewport, "viewport"); cells = List.copyOf(cells);
        }
    }

    /** Upload both parities so flipped and odd-origin pieces can coexist in one field. */
    public void uploadPatterns(GraphicsManager graphics, Sonic2InterlaceVram vram) {
        graphics.beginPatternAtlasBatch();
        try {
            for (int parity = 0; parity < 2; parity++) for (int index = 0; index < PATTERN_COUNT; index++) {
                var pattern = new Pattern();
                for (int row = 0; row < 8; row++) for (int x = 0; x < 8; x++)
                    pattern.setPixel(x, row, (byte) patternPixel(vram, index, x, row * 2 + parity));
                graphics.cachePatternTexture(pattern, patternBase + parity * PATTERN_COUNT + index);
            }
        } finally {
            graphics.endPatternAtlasBatch();
        }
    }

    public PreparedField prepareSprites(Input input) {
        var result = new ArrayList<PreparedSprite>(input.sprites().size());
        for (var sprite : input.sprites()) {
            int y = input.field().projectY(sprite.y());
            var name = sprite.name();
            // Hardware admission is one whole native SAT piece, measured in field rows.
            var geometry = SpriteSatEntry.of(sprite.x(), y, sprite.widthCells(), sprite.heightCells(),
                    name.patternIndex(), name.value() & 0x7FF, name.palette(), name.hFlip(), name.vFlip(),
                    name.priority(), false);
            var fragments = new ArrayList<SpritePieceRenderer.PreparedPiece>();
            int localParity = (input.field().parity() - sprite.y()) & 1;
            for (int row = 0; row < sprite.heightCells(); row++) {
                for (int col = 0; col < sprite.widthCells(); col++) {
                    int srcCol = name.hFlip() ? sprite.widthCells() - 1 - col : col;
                    int srcRow = name.vFlip() ? sprite.heightCells() - 1 - row : row;
                    int index = (name.patternIndex() + srcCol * sprite.heightCells() + srcRow) & 0x3FF;
                    int id = fieldPatternId(index, localParity, name.vFlip());
                    fragments.add(SpriteSatEntry.of(sprite.x() + col * 8, y + row * 8, 1, 1,
                            id, name.value() & 0x7FF, name.palette(), name.hFlip(), name.vFlip(),
                            name.priority(), false).toPreparedPiece());
                }
            }
            result.add(new PreparedSprite(sprite, geometry, fragments));
        }
        return new PreparedField(input, result);
    }

    /** For a host that already owns a collection; retain native geometry, freeze all fragments. */
    public void submitSprites(GraphicsManager graphics, PreparedField prepared) {
        for (var sprite : prepared.sprites()) {
            SpriteSatInternalAccess.submitGroups(graphics, List.of(sprite.geometry()),
                    () -> sprite.fragments().forEach(graphics::submitSpriteSatPiece));
        }
    }

    /** A complete field SAT pass through existing count/pixel/mask admission and replay. */
    public void drawSprites(GraphicsManager graphics, PreparedField prepared,
            SpriteSatMaskPostProcessor.Raster raster) {
        uploadPatterns(graphics, prepared.input().vram());
        SpriteSatInternalAccess.beginCollection(graphics, raster);
        submitSprites(graphics, prepared);
        graphics.endSpriteSatCollectionAndReplay();
    }

    /** Fixed-scroll plane projection. Per-line scroll hosts can project one scanline at a time. */
    public PreparedPlane preparePlane(Sonic2InterlaceVram vram, Plane plane, Field field, Viewport viewport) {
        int firstCol = Math.floorDiv(viewport.x() + plane.scrollX(), 8);
        int lastCol = Math.floorDiv(viewport.x() + viewport.width() - 1 + plane.scrollX(), 8);
        int firstRow = Math.floorDiv(field.physicalLine(viewport.y()) + plane.scrollY(), 16);
        int lastRow = Math.floorDiv(field.physicalLine(viewport.y() + viewport.height() - 1) + plane.scrollY(), 16);
        var cells = new ArrayList<PlaneCell>();
        for (int row = firstRow; row <= lastRow; row++) for (int col = firstCol; col <= lastCol; col++) {
            int nativeTop = row * 16 - plane.scrollY();
            var name = planeName(vram, plane, col, row);
            int localParity = (field.parity() - nativeTop) & 1;
            cells.add(new PlaneCell(name, fieldPatternId(name.patternIndex(), localParity, name.vFlip()),
                    col * 8 - plane.scrollX(), field.projectY(nativeTop)));
        }
        return new PreparedPlane(vram, field, viewport, cells);
    }

    /**
     * Emit one plane priority layer using production indexed-pattern drawing. The host
     * clips to prepared.viewport and owns A/B ordering and background priority metadata.
     * Call uploadPatterns once for the image before drawing planes/SAT from that image.
     */
    public void drawPlane(GraphicsManager graphics, PreparedPlane prepared, boolean highPriority) {
        for (var cell : prepared.cells()) {
            if (cell.name().priority() != highPriority) continue;
            var descriptor = new PatternDesc(cell.name().value());
            graphics.renderPatternWithId(cell.patternId(), descriptor, cell.x(), cell.y());
        }
    }

    private int fieldPatternId(int index, int localParity, boolean vFlip) {
        return patternBase + ((localParity ^ (vFlip ? 1 : 0)) * PATTERN_COUNT) + index;
    }
}
