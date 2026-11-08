package com.openggf.game.sonic1;

import com.openggf.data.Rom;
import com.openggf.game.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.ActiveGameplayTeamResolver;
import com.openggf.game.presentation.CharacterNameComposer;
import com.openggf.level.Pattern;
import java.io.IOException;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

/** S1-owned adapters from optional character art to native surface tile layouts. */
public final class Sonic1CharacterPresentation {
    private Sonic1CharacterPresentation() { }

    public static CharacterPresentation active() throws IOException {
        var world = SessionManager.getCurrentWorldSession();
        if (world == null) return null;
        String code = ActiveGameplayTeamResolver.resolveMainCharacterCode(GameServices.configuration());
        var definition = world.getPlayableCharacterRegistry().find(CharacterKey.parsePersisted(code)).orElse(null);
        return definition == null || definition.presentationSupplier() == null
                ? null : definition.presentationSupplier().load(code);
    }

    static CharacterPresentation.Art art(CharacterPresentation presentation) {
        return presentation == null ? null : presentation.art().get(CharacterPresentation.Host.S1);
    }

    public static CharacterNameComposer.Composition name(Rom rom, CharacterPresentation presentation,
            CharacterPresentation.Style style) throws IOException {
        if (presentation == null) return null;
        String name = style == CharacterPresentation.Style.HUD && presentation.hudName() != null
                ? presentation.hudName() : presentation.displayName();
        if (name == null) return null;
        return CharacterNameComposer.compose(name,
                style == CharacterPresentation.Style.HUD ? Sonic1CharacterFont.hud(rom) : Sonic1CharacterFont.title(rom),
                presentation.supplementaryGlyphs().getOrDefault(
                        new CharacterPresentation.Font(CharacterPresentation.Host.S1, style), Map.of()), 0);
    }

    static Pattern[] hud(Rom rom, Pattern[] nativeTiles, CharacterPresentation presentation) throws IOException {
        if (presentation == null) return nativeTiles;
        Pattern[] tiles = nativeTiles.clone();
        var art = art(presentation);
        if (art != null && art.hudLifeIcon() != null) {
            System.arraycopy(CharacterNameComposer.tiles(art.hudLifeIcon()), 0, tiles, 0, 4);
        }
        var name = name(rom, presentation, CharacterPresentation.Style.HUD);
        if (name != null && name.complete() && name.image().width() <= 32 && name.image().height() <= 8) {
            // Only the upper row is the name. Keep the native lower-row X and life-number cell.
            var image = name.image();
            byte[] pixels = image.pixels();
            for (int x = 0; x < 32; x++) {
                int tile = 4 + x / 8 * 2;
                if (x % 8 == 0) { tiles[tile] = new Pattern(); }
                for (int y = 0; y < image.height(); y++) {
                    if (x < image.width()) tiles[tile].setPixel(x % 8, y, pixels[y * image.width() + x]);
                }
            }
        }
        return tiles;
    }

    /** Map_Monitor_internal's .sonic face uses four tiles at $1C; the shell is shared. */
    static ObjectSpriteSheet monitor(ObjectSpriteSheet nativeSheet, CharacterPresentation presentation) {
        var art = art(presentation);
        var face = art == null ? null : art.oneUpFace();
        if (face == null || face.width() != 16 || face.height() != 16) return nativeSheet;
        Pattern[] patterns = nativeSheet.getPatterns().clone();
        System.arraycopy(CharacterNameComposer.tiles(face), 0, patterns, 0x1C, 4);
        var frames = new ArrayList<SpriteMappingFrame>();
        for (int i = 0; i < nativeSheet.getFrameCount(); i++) frames.add(nativeSheet.getFrame(i));
        return com.openggf.game.presentation.CharacterIconPalette.monitor(
                new ObjectSpriteSheet(patterns, frames, nativeSheet.getPaletteIndex(), nativeSheet.getFrameDelay()),
                presentation, CharacterPresentation.Host.S1, 0x1C);
    }

    /** Replace only SONIC's five native pieces; retain HAS and every other native piece. */
    public static ObjectSpriteSheet results(Rom rom, Pattern[] patterns,
            List<SpriteMappingFrame> frames,
            CharacterPresentation presentation, int nameFrame) throws IOException {
        var name = name(rom, presentation, CharacterPresentation.Style.RESULTS);
        if (name == null || !name.complete() || name.image().height() > 16 || name.image().width() > 144)
            return new ObjectSpriteSheet(patterns, frames, 0, 1);
        var image = name.image();
        var appended = CharacterNameComposer.tiles(new CharacterPresentation.Image(image.width(), 16,
                padHeight(image, 16)));
        Pattern[] combined = Arrays.copyOf(patterns, patterns.length + appended.length);
        System.arraycopy(appended, 0, combined, patterns.length, appended.length);
        var nativePieces = frames.get(nameFrame).pieces();
        var last = nativePieces.get(4);
        int right = last.xOffset() + last.widthTiles() * 8;
        int columns = (image.width() + 7) / 8;
        var pieces = new ArrayList<SpriteMappingPiece>();
        for (int x = 0; x < columns; x += 4) {
            pieces.add(new SpriteMappingPiece(
                    right - image.width() + x * 8, last.yOffset(), Math.min(4, columns - x), 2,
                    patterns.length + x * 2, false, false, last.paletteIndex(), last.priority()));
        }
        pieces.addAll(nativePieces.subList(5, nativePieces.size()));
        var replaced = new ArrayList<>(frames);
        replaced.set(nameFrame, new SpriteMappingFrame(pieces));
        return new ObjectSpriteSheet(combined, replaced, 0, 1);
    }

    private static byte[] padHeight(CharacterPresentation.Image image, int height) {
        return Arrays.copyOf(image.pixels(), image.width() * height);
    }
}
