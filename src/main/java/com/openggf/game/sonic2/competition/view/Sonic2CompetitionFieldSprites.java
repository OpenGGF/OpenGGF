package com.openggf.game.sonic2.competition.view;

import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.SpriteSatEntry;
import com.openggf.graphics.SpriteSatInternalAccess;
import com.openggf.level.render.SpritePieceRenderer;
import java.util.ArrayList;
import java.util.List;

/** Projects completed native 8x16 sprite cells at full progressive resolution. */
public final class Sonic2CompetitionFieldSprites {
    private Sonic2CompetitionFieldSprites() { }
    public record Piece(SpriteSatEntry geometry, List<SpritePieceRenderer.PreparedPiece> fragments) {
        public Piece { fragments = List.copyOf(fragments); }
    }
    public static List<Piece> project(Sonic2CompetitionSatBuilder.Page page) {
        var result = new ArrayList<Piece>();
        for (var sprite : page.entries()) {
            int y = sprite.y();
            var name = sprite.name();
            var geometry = SpriteSatEntry.of(sprite.x(), y, sprite.widthCells(), sprite.heightCells() * 2,
                    name.patternIndex(), name.value() & 0x7FF, name.palette(), name.hFlip(), name.vFlip(),
                    name.priority(), false);
            var fragments = new ArrayList<SpritePieceRenderer.PreparedPiece>();
            for (int row = 0; row < sprite.heightCells() * 2; row++) for (int col = 0; col < sprite.widthCells(); col++) {
                int sourceColumn = name.hFlip() ? sprite.widthCells() - 1 - col : col;
                int sourceRow = name.vFlip() ? sprite.heightCells() * 2 - 1 - row : row;
                int cell = (name.patternIndex() + sourceColumn * sprite.heightCells() + sourceRow / 2) & 1023;
                fragments.add(new SpritePieceRenderer.PreparedPiece(sprite.x() + col * 8, y + row * 8,
                        1, 1, cell * 2 + sourceRow % 2, name.value() & 0x7FF, name.palette(), name.hFlip(), name.vFlip(),
                        name.priority(), false, 0, 1));
            }
            result.add(new Piece(geometry, fragments));
            if (sprite.link() == 0) break;
        }
        return List.copyOf(result);
    }
    public static void submit(GraphicsManager graphics, Sonic2CompetitionSatBuilder.Page page) {
        for (var piece : project(page)) SpriteSatInternalAccess.submitGroups(graphics, List.of(piece.geometry()),
                () -> piece.fragments().forEach(graphics::submitSpriteSatPiece));
    }
}
