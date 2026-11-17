package com.openggf.game.soniccd.objects.art;

import com.openggf.game.*;
import com.openggf.game.presentation.CharacterNameComposer;
import com.openggf.game.session.ActiveGameplayTeamResolver;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import java.io.IOException;
import java.util.*;

/** CD's results header owns five SONIC pieces, followed by the unchanged native message. */
public final class CdDonorNames {
    private CdDonorNames() { }
    public static ObjectSpriteSheet results(ObjectSpriteSheet sheet) throws IOException {
        if (!CrossGameFeatureProvider.isActive()) return sheet;
        String character = ActiveGameplayTeamResolver.resolveMainCharacterCode(GameServices.configuration());
        if ("sonic".equals(character)) return sheet;
        var name = CharacterNameComposer.compose(character.toUpperCase(Locale.ROOT),
                CrossGameFeatureInternalAccess.nameFont(GameServices.crossGameFeatures()), Map.of(), 0);
        if (!name.complete()) throw new IOException("Donor cannot draw results name: " + character);
        var image = name.image();
        // The native good-future header fills the screen; retain its 72-pixel name cell.
        // Resample only donor lettering, never extend the fixed native message offscreen.
        if (image.width() > 72) {
            byte[] pixels = new byte[72 * image.height()];
            byte[] source = image.pixels();
            for (int y = 0; y < image.height(); y++)
                for (int x = 0; x < 72; x++) pixels[y * 72 + x] = source[y * image.width() + x * image.width() / 72];
            image = new CharacterPresentation.Image(72, image.height(), pixels);
        }
        var tiles = CharacterNameComposer.tiles(image);
        var original = sheet.getPatterns();
        var combined = Arrays.copyOf(original, original.length + tiles.length);
        System.arraycopy(tiles, 0, combined, original.length, tiles.length);
        var frame = sheet.getFrame(0).pieces();
        var last = frame.get(4);
        int right = last.xOffset() + last.widthTiles() * 8;
        int columns = (image.width() + 7) / 8;
        var pieces = new ArrayList<SpriteMappingPiece>();
        for (int x = 0; x < columns; x += 4)
            pieces.add(new SpriteMappingPiece(right - image.width() + x * 8, last.yOffset(),
                    Math.min(4, columns - x), 2, original.length + x * 2,
                    false, false, last.paletteIndex(), last.priority()));
        pieces.addAll(frame.subList(5, frame.size()));
        var frames = new ArrayList<SpriteMappingFrame>();
        for (int i = 0; i < sheet.getFrameCount(); i++) frames.add(i == 0 ? new SpriteMappingFrame(pieces) : sheet.getFrame(i));
        return new ObjectSpriteSheet(combined, frames, sheet.getPaletteIndex(), sheet.getFrameDelay());
    }
}
