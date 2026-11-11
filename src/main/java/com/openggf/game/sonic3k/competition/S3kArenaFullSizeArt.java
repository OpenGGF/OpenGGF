package com.openggf.game.sonic3k.competition;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.Sonic3kPlayerArt;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.animation.SpriteAnimationSet;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Main-game Sonic, Tails and Knuckles art (mappings, DPLCs, scripts) for the hosted
 * Competition zoom, read through the ordinary S3K player-art pipeline. Presentation only:
 * collision and physics stay with the miniature actors.
 */
public final class S3kArenaFullSizeArt {
    /** Competition palette line of each miniature; the arena palette keeps the main-game colours there. */
    static int paletteLine(S3kCompetitionPlayer.Character character) {
        return character == S3kCompetitionPlayer.Character.KNUCKLES ? 1 : 0;
    }

    private final Map<S3kCompetitionPlayer.Character, SpriteArtSet> art =
            new EnumMap<>(S3kCompetitionPlayer.Character.class);
    private final SpriteArtSet tailsTail;
    private final S3kArenaPoseMap poses;

    S3kArenaFullSizeArt(Rom rom, S3kCompetitionResources resources, S3kMiniatureAnimation.Bank miniScripts,
                        S3kCompetitionAssets assets) throws IOException {
        // The main-game art lives in the S&K half; a standalone Sonic 3 cartridge has only its own.
        if (resources != S3kCompetitionResources.LOCKED_ON)
            throw new IllegalStateException("Full-size arena art requires the Sonic 3 & Knuckles cartridge");
        var loader = new Sonic3kPlayerArt(RomByteReader.fromRom(Objects.requireNonNull(rom)));
        var scripts = new EnumMap<S3kCompetitionPlayer.Character, SpriteAnimationSet>(S3kCompetitionPlayer.Character.class);
        var miniFrames = new EnumMap<S3kCompetitionPlayer.Character, Integer>(S3kCompetitionPlayer.Character.class);
        for (var character : S3kCompetitionPlayer.Character.values()) {
            SpriteArtSet source = loader.loadForCharacter(character.name().toLowerCase(Locale.ROOT));
            art.put(character, new SpriteArtSet(source.artTiles(), source.mappingFrames(), source.dplcFrames(),
                    paletteLine(character), source.basePatternIndex(), source.frameDelay(), source.bankSize(),
                    source.animationProfile(), source.animationSet()));
            scripts.put(character, source.animationSet());
            miniFrames.put(character, assets.miniature(character).getFrameCount());
        }
        // Obj_Tails_Tail's separate art; Tails' palette line, like the body.
        tailsTail = loader.loadTailsTail();
        poses = new S3kArenaPoseMap(miniScripts, scripts, miniFrames);
    }

    public SpriteArtSet art(S3kCompetitionPlayer.Character character) { return art.get(character); }
    public SpriteArtSet tailsTail() { return tailsTail; }
    public S3kArenaPoseMap poses() { return poses; }
}
