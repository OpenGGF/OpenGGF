package com.openggf.game.sonic2.specialstage;

import com.openggf.data.Rom;
import com.openggf.game.CharacterPresentation;
import com.openggf.game.presentation.CharacterNameComposer;
import com.openggf.game.sonic2.Sonic2CharacterPresentation;
import com.openggf.game.sonic2.Sonic2ResultsName;
import com.openggf.level.Pattern;
import java.io.IOException;
import java.util.*;

/** Session-local name tiles, outside all native results VRAM ranges. */
public final class Sonic2SpecialStageResultsName {
    private Sonic2SpecialStageResultsName() { }
    public record Projection(Pattern[] patterns,
            Map<Integer, Sonic2SpecialStageResultsMappings.ResultsPiece[]> frames) { }

    public static Projection apply(Rom rom, Pattern[] stock, CharacterPresentation presentation) throws IOException {
        var image = Sonic2CharacterPresentation.name(rom, presentation, CharacterPresentation.Style.RESULTS);
        if (image == null) return new Projection(stock, Map.of());
        var tiles = CharacterNameComposer.tiles(image);
        var patterns = Arrays.copyOf(stock, stock.length + tiles.length);
        System.arraycopy(tiles, 0, patterns, stock.length, tiles.length);
        var frames = new HashMap<Integer, Sonic2SpecialStageResultsMappings.ResultsPiece[]>();
        for (int frame : new int[]{Sonic2SpecialStageResultsMappings.FRAME_SONIC_GOT_A,
                Sonic2SpecialStageResultsMappings.FRAME_SONIC_HAS_ALL,
                Sonic2SpecialStageResultsMappings.FRAME_NOW_SONIC_CAN,
                Sonic2SpecialStageResultsMappings.FRAME_SUPER_SONIC}) {
            var nativePieces = Sonic2SpecialStageResultsMappings.getFrame(frame);
            // Obj6F's headings retain their native prefix/suffix, replacing only SONIC.
            int start = frame == Sonic2SpecialStageResultsMappings.FRAME_NOW_SONIC_CAN ? 3
                    : frame == Sonic2SpecialStageResultsMappings.FRAME_SUPER_SONIC ? 5 : 0;
            var last = nativePieces[start + 4];
            var pieces = new ArrayList<Sonic2SpecialStageResultsMappings.ResultsPiece>();
            pieces.addAll(Arrays.asList(nativePieces).subList(0, start));
            for (var piece : Sonic2ResultsName.pieces(image, stock.length,
                    last.xOffset() + last.widthTiles() * 8, 0, false))
                pieces.add(new Sonic2SpecialStageResultsMappings.ResultsPiece(piece.xOffset(), piece.yOffset(),
                        piece.widthTiles(), piece.heightTiles(), piece.tileIndex(), false, false, 0,
                        Sonic2SpecialStageResultsMappings.ART_TYPE_SS_LETTERS));
            pieces.addAll(Arrays.asList(nativePieces).subList(start + 5, nativePieces.length));
            frames.put(frame, pieces.toArray(Sonic2SpecialStageResultsMappings.ResultsPiece[]::new));
        }
        return new Projection(patterns, Map.copyOf(frames));
    }
}
