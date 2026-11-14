package com.openggf.game.sonic2;

import com.openggf.data.Rom;
import com.openggf.game.CharacterPresentation;
import com.openggf.game.presentation.CharacterNameComposer;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Replace only the name; native suffix pieces and their animation stay with their owner. */
public final class Sonic2ResultsName {
    private Sonic2ResultsName() { }

    public static List<SpriteMappingPiece> pieces(CharacterPresentation.Image image, int firstTile,
            int rightEdge, int y, boolean priority) {
        var pieces = new ArrayList<SpriteMappingPiece>();
        int rows = (image.height() + 7) / 8;
        for (int x = 0; x < image.width(); x += 8) {
            // One tile per piece also supports creator glyphs of arbitrary pixel height.
            for (int row = 0; row < rows; row++)
                pieces.add(new SpriteMappingPiece(rightEdge - image.width() + x, y + row * 8,
                        1, 1, firstTile + x / 8 * rows + row, false, false, 0, priority));
        }
        return pieces;
    }

    public static ObjectSpriteSheet apply(Rom rom, ObjectSpriteSheet stock,
            CharacterPresentation presentation) throws IOException {
        var image = Sonic2CharacterPresentation.name(rom, presentation, CharacterPresentation.Style.RESULTS);
        if (image == null) return stock;
        // EOL_Sonic (s2.asm:28844-28853): first five pieces spell SONIC, followed by GOT.
        var nativePieces = stock.getFrame(0).pieces();
        var pieces = new ArrayList<>(pieces(image, stock.getPatterns().length, 8, 0, true));
        pieces.addAll(nativePieces.subList(5, nativePieces.size()));
        var frames = new ArrayList<SpriteMappingFrame>();
        for (int i = 0; i < stock.getFrameCount(); i++)
            frames.add(i < 3 ? new SpriteMappingFrame(List.copyOf(pieces)) : stock.getFrame(i));
        var tiles = CharacterNameComposer.tiles(image);
        Pattern[] patterns = Arrays.copyOf(stock.getPatterns(), stock.getPatterns().length + tiles.length);
        System.arraycopy(tiles, 0, patterns, stock.getPatterns().length, tiles.length);
        return new ObjectSpriteSheet(patterns, List.copyOf(frames), stock.getPaletteIndex(), stock.getFrameDelay());
    }
}
