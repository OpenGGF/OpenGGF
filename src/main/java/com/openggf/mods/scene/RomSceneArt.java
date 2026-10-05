package com.openggf.mods.scene;

import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameId;
import com.openggf.game.sonic1.S1SpriteDataLoader;
import com.openggf.game.sonic2.S2SpriteDataLoader;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.util.PatternDecompressor;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.function.Supplier;

/**
 * {@link SceneRomArt} for the running stock game: reads the user's ROM, decompresses art,
 * loads mappings in the game's own format and rasterises frames on demand. Engine-internal.
 */
final class RomSceneArt implements SceneRomArt {
    private final Rom rom;
    private final GameId game;
    private final Supplier<PlayerSpriteArtProvider> players;
    private final Supplier<SpriteArtSet> tailsTails;
    private RomByteReader reader;
    private final Map<String, SceneSpriteSet> characterCache = new HashMap<>();

    RomSceneArt(Rom rom, GameId game, Supplier<PlayerSpriteArtProvider> players, Supplier<SpriteArtSet> tailsTails) {
        this.rom = rom;
        this.game = game;
        this.players = players;
        this.tailsTails = tailsTails;
    }

    private RomByteReader reader() {
        if (reader == null) {
            try {
                reader = RomByteReader.fromRom(rom);
            } catch (IOException e) {
                throw new IllegalStateException("ROM unavailable: " + e.getMessage(), e);
            }
        }
        return reader;
    }

    @Override
    public String gameId() {
        return game.code();
    }

    @Override
    public byte[] read(int address, int length) {
        if (address < 0 || length < 0 || length > (1 << 20) || address + (long) length > reader().size()) {
            throw new IllegalArgumentException("ROM read out of range: " + Integer.toHexString(address));
        }
        byte[] out = new byte[length];
        for (int i = 0; i < length; i++) {
            out[i] = (byte) reader().readU8(address + i);
        }
        return out;
    }

    @Override
    public int[] palette(int address, int colors) {
        if (colors <= 0 || colors > 64) {
            throw new IllegalArgumentException("colors must be 1-64");
        }
        return SpriteRasterizer.colors(read(address, colors * 2));
    }

    @Override
    public SceneSpriteSet sprites(RomSpriteRequest request, int[] palette) {
        int[] pal = padPalette(palette);
        Pattern[] art = loadArt(request);
        List<SpriteMappingFrame> frames = loadMappings(request.mappingAddress());
        List<SpriteDplcFrame> dplcs = request.hasDplc() ? loadDplcs(request) : List.of();
        return new LazySpriteSet(frames, dplcs, art, pal, request.paletteLine(), request.tileOffset(), null);
    }

    @Override
    public SceneSpriteSet character(String characterCode) {
        return characterCache.computeIfAbsent(characterCode, code -> {
            PlayerSpriteArtProvider provider = players.get();
            if (provider == null) {
                throw new IllegalStateException("This game provides no character art");
            }
            try {
                SpriteArtSet set = provider.loadPlayerSpriteArt(code);
                if (set == null || set.isEmpty()) {
                    throw new IllegalArgumentException("Unknown character " + code);
                }
                int[] pal = padPalette(characterPalette(code));
                return new LazySpriteSet(set.mappingFrames(), set.dplcFrames(), set.artTiles(), pal, 0, 0,
                        set.animationSet());
            } catch (IOException e) {
                throw new IllegalStateException("Character art unavailable: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public SceneSpriteSet characterAccessory(String characterCode) {
        if (!"tails".equals(characterCode) || tailsTails == null) {
            return null;
        }
        return characterCache.computeIfAbsent("tails#accessory", key -> {
            SpriteArtSet set = tailsTails.get();
            if (set == null || set.isEmpty()) {
                return null;
            }
            return new LazySpriteSet(set.mappingFrames(), set.dplcFrames(), set.artTiles(),
                    padPalette(characterPalette("tails")), 0, 0, set.animationSet());
        });
    }

    @Override
    public int[] characterPalette(String characterCode) {
        PlayerSpriteArtProvider provider = players.get();
        Palette palette = provider == null ? null : provider.loadCharacterPalette(characterCode);
        int[] out = new int[16];
        if (palette != null) {
            for (int i = 0; i < 16 && i < palette.getColorCount(); i++) {
                Palette.Color c = palette.getColor(i);
                out[i] = 0xFF000000 | ((c.r & 0xFF) << 16) | ((c.g & 0xFF) << 8) | (c.b & 0xFF);
            }
        }
        return out;
    }

    private static int[] padPalette(int[] palette) {
        int[] out = new int[64];
        if (palette != null) {
            System.arraycopy(palette, 0, out, 0, Math.min(64, palette.length));
        }
        return out;
    }

    private Pattern[] loadArt(RomSpriteRequest request) {
        try {
            return switch (request.compression()) {
                case NEMESIS -> PatternDecompressor.nemesis(rom, request.artAddress());
                case KOSINSKI -> PatternDecompressor.kosinski(rom, request.artAddress());
                case KOSINSKI_MODULED -> PatternDecompressor.kosinskiModuled(rom, request.artAddress());
                case UNCOMPRESSED -> PatternDecompressor.uncompressed(reader(), request.artAddress(),
                        request.artSize());
            };
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Could not decode art at 0x" + Integer.toHexString(request.artAddress())
                    + ": " + e.getMessage(), e);
        }
    }

    private List<SpriteMappingFrame> loadMappings(int address) {
        return switch (game) {
            case S1 -> S1SpriteDataLoader.loadMappingFrames(reader(), address);
            case S2 -> S2SpriteDataLoader.loadMappingFrames(reader(), address);
            default -> S3kSpriteDataLoader.loadMappingFrames(reader(), address);
        };
    }

    private List<SpriteDplcFrame> loadDplcs(RomSpriteRequest request) {
        int address = request.dplcAddress();
        return switch (game) {
            case S1 -> S1SpriteDataLoader.loadDplcFrames(reader(), address);
            case S2 -> S2SpriteDataLoader.loadDplcFrames(reader(), address);
            default -> request.dplcLayout() == RomSpriteRequest.DplcLayout.PLAYER
                    ? S3kSpriteDataLoader.loadDplcFrames(reader(), address)
                    : loadS3kObjectDplcs(address);
        };
    }

    /**
     * S3K {@code Perform_DPLC} cues: a word of entries minus one, then entries of
     * {@code (startTile << 4) | (count - 1)} (the layout Sonic3kObjectArt reads for objects).
     */
    private List<SpriteDplcFrame> loadS3kObjectDplcs(int address) {
        RomByteReader r = reader();
        int frameCount = r.readU16BE(address) / 2;
        List<SpriteDplcFrame> frames = new ArrayList<>(frameCount);
        for (int i = 0; i < frameCount; i++) {
            int frameAddr = address + r.readU16BE(address + i * 2);
            int requests = r.readU16BE(frameAddr) + 1;
            List<com.openggf.level.render.TileLoadRequest> list = new ArrayList<>(requests);
            for (int q = 0; q < requests; q++) {
                int entry = r.readU16BE(frameAddr + 2 + q * 2);
                list.add(new com.openggf.level.render.TileLoadRequest((entry >> 4) & 0xFFF, (entry & 0xF) + 1));
            }
            frames.add(new SpriteDplcFrame(list));
        }
        return frames;
    }

    /** Rasterises frames the first time they are asked for. */
    private static final class LazySpriteSet implements SceneSpriteSet {
        private final List<SpriteMappingFrame> frames;
        private final List<SpriteDplcFrame> dplcs;
        private final Pattern[] art;
        private final int[] palette;
        private final int paletteLine;
        private final int tileOffset;
        private final SpriteAnimationSet animations;
        private final SceneSprite[] cache;

        LazySpriteSet(List<SpriteMappingFrame> frames, List<SpriteDplcFrame> dplcs, Pattern[] art, int[] palette,
                int paletteLine, int tileOffset, SpriteAnimationSet animations) {
            this.frames = frames;
            this.dplcs = dplcs;
            this.art = art;
            this.palette = palette;
            this.paletteLine = paletteLine;
            this.tileOffset = tileOffset;
            this.animations = animations;
            this.cache = new SceneSprite[frames.size()];
        }

        @Override
        public int frameCount() {
            return frames.size();
        }

        @Override
        public SceneSprite frame(int index) {
            if (index < 0 || index >= frames.size()) {
                return new SceneSprite(new SceneImage(1, 1, new int[1]), 0, 0);
            }
            SceneSprite sprite = cache[index];
            if (sprite == null) {
                Pattern[] tiles = index < dplcs.size() ? SpriteRasterizer.dplcBank(dplcs.get(index), art) : art;
                sprite = SpriteRasterizer.rasterize(frames.get(index), tiles, palette, paletteLine, tileOffset);
                cache[index] = sprite;
            }
            return sprite;
        }

        @Override
        public int[] animationFrames(int animationId) {
            SpriteAnimationScript script = animations == null ? null : animations.getScript(animationId);
            if (script == null) {
                return new int[0];
            }
            List<Integer> list = script.frames();
            int[] out = new int[list.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = list.get(i);
            }
            return out;
        }

        @Override
        public int animationDelay(int animationId) {
            SpriteAnimationScript script = animations == null ? null : animations.getScript(animationId);
            return script == null ? 8 : Math.max(1, script.delay() + 1);
        }
    }
}
