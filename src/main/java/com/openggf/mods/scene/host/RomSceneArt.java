package com.openggf.mods.scene.host;

import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameId;
import com.openggf.game.sonic1.S1SpriteDataLoader;
import com.openggf.game.sonic2.S2SpriteDataLoader;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.render.LevelFloorScanner;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.ZonePictureSource;
import com.openggf.mods.scene.RomSpriteRequest;
import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneLevelKit;
import com.openggf.mods.scene.SceneLevelStage;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.util.PatternDecompressor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final ZonePictureSource zones;
    private RomByteReader reader;
    private final Map<String, SceneSpriteSet> characterCache = new HashMap<>();
    /** Per zone and act; unsupported zones cache as empty so they are asked once. */
    private final Map<List<Integer>, Optional<SceneBackdrop>> backdropCache = new HashMap<>();
    /** Per zone, act and height limit. */
    private final Map<List<Integer>, Optional<SceneImage>> overviewCache = new HashMap<>();
    /** Per zone, act, width, headroom and rise. */
    private final Map<List<Integer>, List<SceneLevelStage>> stageCache = new HashMap<>();
    /** Per zone and act. */
    private final Map<List<Integer>, SceneSpriteSet> titleCardCache = new HashMap<>();
    /** The few most recent level kits by zone and act; acts without a kit map to empty. */
    private final Map<List<Integer>, Optional<SceneLevelKit>> kitCache =
            new java.util.LinkedHashMap<>(8, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<List<Integer>, Optional<SceneLevelKit>> eldest) {
                    return size() > 4;
                }
            };

    RomSceneArt(Rom rom, GameId game, Supplier<PlayerSpriteArtProvider> players, Supplier<SpriteArtSet> tailsTails,
            ZonePictureSource zones) {
        this.rom = rom;
        this.game = game;
        this.players = players;
        this.tailsTails = tailsTails;
        this.zones = zones;
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

    private String romSha1;

    @Override
    public String romSha1() {
        if (romSha1 == null) {
            try {
                java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
                RomByteReader bytes = reader();
                byte[] chunk = new byte[8192];
                for (int offset = 0; offset < bytes.size();) {
                    int count = Math.min(chunk.length, bytes.size() - offset);
                    for (int index = 0; index < count; index++) chunk[index] = (byte) bytes.readU8(offset + index);
                    digest.update(chunk, 0, count);
                    offset += count;
                }
                romSha1 = java.util.HexFormat.of().formatHex(digest.digest());
            } catch (java.security.NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("SHA-1 unavailable", impossible);
            }
        }
        return romSha1;
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
        List<SpriteMappingFrame> frames = loadMappings(request);
        List<SpriteDplcFrame> dplcs = request.hasDplc() ? loadDplcs(request) : List.of();
        return new LazySpriteSet(frames, dplcs, art, pal, request.paletteLine(), request.tileOffset(), null);
    }

    @Override
    public SceneImage tiles(int address, RomSpriteRequest.Compression compression, int firstTile, int widthTiles,
            int heightTiles, boolean columnMajor, int[] palette) {
        if (firstTile < 0 || widthTiles < 1 || heightTiles < 1 || widthTiles > 512 || heightTiles > 512) {
            throw new IllegalArgumentException("Tile block out of range: first " + firstTile + ", " + widthTiles + "x"
                    + heightTiles + " tiles");
        }
        if (compression == null || palette == null || palette.length < 16) {
            throw new IllegalArgumentException("tiles needs a compression and a 16-colour palette");
        }
        int count = widthTiles * heightTiles;
        Pattern[] art;
        if (compression == RomSpriteRequest.Compression.UNCOMPRESSED) {
            long end = address + (long) (firstTile + count) * Pattern.PATTERN_SIZE_IN_ROM;
            if (address < 0 || end > reader().size()) {
                throw new IllegalArgumentException("Tiles run past the ROM's end: 0x" + Integer.toHexString(address));
            }
            try {
                art = PatternDecompressor.uncompressed(reader(), address + firstTile * Pattern.PATTERN_SIZE_IN_ROM,
                        count * Pattern.PATTERN_SIZE_IN_ROM);
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not read tiles at 0x" + Integer.toHexString(address), e);
            }
            firstTile = 0;
        } else {
            art = loadArt(new RomSpriteRequest(address, compression, 0, 0, -1, RomSpriteRequest.DplcLayout.OBJECT, 0,
                    0));
        }
        if (art.length < firstTile + count) {
            throw new IllegalArgumentException("The art at 0x" + Integer.toHexString(address) + " has " + art.length
                    + " tiles; asked for " + (firstTile + count));
        }
        int width = widthTiles * 8;
        int[] argb = new int[width * heightTiles * 8];
        for (int t = 0; t < count; t++) {
            int column = columnMajor ? t / heightTiles : t % widthTiles;
            int row = columnMajor ? t % heightTiles : t / widthTiles;
            Pattern tile = art[firstTile + t];
            for (int py = 0; py < 8; py++) {
                for (int px = 0; px < 8; px++) {
                    int index = tile.getPixel(px, py) & 0x0F;
                    argb[(row * 8 + py) * width + column * 8 + px] = index == 0 ? 0 : palette[index];
                }
            }
        }
        return new SceneImage(width, heightTiles * 8, argb);
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

    // Zone pictures are built on the calling thread: the ROM's reads share one channel position.
    @Override
    public boolean hasZonePictures(int zone, int act) {
        return zones != null && zone >= 0 && act >= 0 && zones.supports(zone, act);
    }

    @Override
    public SceneBackdrop zoneBackdrop(int zone, int act) {
        if (!hasZonePictures(zone, act)) {
            return null;
        }
        return backdropCache.computeIfAbsent(List.of(zone, act),
                key -> Optional.ofNullable(zones.backdrop(zone, act)).map(RomSceneArt::toSceneBackdrop))
                .orElse(null);
    }

    @Override
    public SceneImage levelOverview(int zone, int act, int maxHeight) {
        if (maxHeight < 1) {
            throw new IllegalArgumentException("maxHeight must be positive: " + maxHeight);
        }
        if (!hasZonePictures(zone, act)) {
            return null;
        }
        int rows = Math.min(maxHeight, 4096);
        return overviewCache.computeIfAbsent(List.of(zone, act, rows),
                key -> Optional.ofNullable(zones.overview(zone, act, rows)).map(RomSceneArt::toSceneImage))
                .orElse(null);
    }

    @Override
    public List<SceneLevelStage> levelStages(int zone, int act, int width, int headroom, int maxRise) {
        if (width < 1 || headroom < 1 || maxRise < 0) {
            throw new IllegalArgumentException("Invalid stage size: width " + width + ", headroom " + headroom
                    + ", maxRise " + maxRise);
        }
        if (!hasZonePictures(zone, act)) {
            return List.of();
        }
        return stageCache.computeIfAbsent(List.of(zone, act, width, headroom, maxRise), key -> {
            List<SceneLevelStage> stages = new ArrayList<>();
            for (LevelFloorScanner.Stage stage : zones.stages(zone, act, width, headroom, maxRise)) {
                stages.add(new SceneLevelStage(stage.x(), stage.floorY(), stage.width(), stage.floor()));
            }
            return List.copyOf(stages);
        });
    }

    @Override
    public SceneImage levelForeground(int zone, int act, int x, int y, int width, int height) {
        if (width < 1 || height < 1 || width > 4096 || height > 4096) {
            throw new IllegalArgumentException("Foreground size must be 1-4096: " + width + "x" + height);
        }
        if (!hasZonePictures(zone, act)) {
            return null;
        }
        ZonePictureSource.Picture picture = zones.foreground(zone, act, x, y, width, height);
        return picture == null ? null : toSceneImage(picture);
    }

    @Override
    public SceneLevelKit levelKit(int zone, int act) {
        if (zones == null || zone < 0 || act < 0) {
            return null;
        }
        List<Integer> key = List.of(zone, act);
        Optional<SceneLevelKit> cached = kitCache.get(key);
        if (cached == null) {
            com.openggf.level.render.DetachedLevelKit kit = zones.kit(zone, act);
            cached = Optional.ofNullable(kit == null ? null : new Kit(kit));
            kitCache.put(key, cached);
        }
        return cached.orElse(null);
    }

    /** {@link SceneLevelKit} over an engine kit, caching block pictures and the backdrop. */
    private static final class Kit implements SceneLevelKit {
        private final com.openggf.level.render.DetachedLevelKit kit;
        private final SceneImage[] images;
        private SceneBackdrop backdrop;

        Kit(com.openggf.level.render.DetachedLevelKit kit) {
            this.kit = kit;
            this.images = new SceneImage[kit.blockCount()];
        }

        @Override public int blockSize() { return kit.blockSize(); }
        @Override public int columns() { return kit.columns(); }
        @Override public int rows() { return kit.rows(); }
        @Override public int blockCount() { return kit.blockCount(); }
        @Override public int block(int column, int row) { return kit.blockAt(column, row); }
        @Override public int[] playableArea() { return kit.playable(); }
        @Override public int[] palette() { return kit.palette(); }

        @Override
        public SceneImage blockImage(int block) {
            if (block < 0 || block >= images.length) {
                return new SceneImage(kit.blockSize(), kit.blockSize(), new int[kit.blockSize() * kit.blockSize()]);
            }
            if (images[block] == null) {
                images[block] = new SceneImage(kit.blockSize(), kit.blockSize(), kit.blockPixels(block));
            }
            return images[block];
        }

        @Override
        public byte[] blockSolidity(int block) {
            return kit.blockSolidity(block);
        }

        @Override
        public SceneBackdrop backdrop() {
            if (backdrop == null) {
                backdrop = toSceneBackdrop(kit.backdrop());
            }
            return backdrop;
        }
    }

    @Override
    public boolean hasTitleCard(int zone, int act) {
        return zones != null && zone >= 0 && act >= 0 && zones.hasTitleCard(zone, act);
    }

    @Override
    public SceneSpriteSet titleCard(int zone, int act) {
        if (!hasTitleCard(zone, act)) {
            return null;
        }
        return titleCardCache.computeIfAbsent(List.of(zone, act), key -> {
            ZonePictureSource.Sprites card = zones.titleCard(zone, act);
            return new LazySpriteSet(card.frames(), List.of(), card.tiles(), padPalette(
                    SpriteRasterizer.colors(card.paletteWords())), 0, 0, null);
        });
    }

    private static SceneImage toSceneImage(ZonePictureSource.Picture picture) {
        return new SceneImage(picture.width(), picture.height(), picture.argb());
    }

    /** Also checks the engine's bands against the API contract (they cover the image in order). */
    private static SceneBackdrop toSceneBackdrop(ZonePictureSource.Backdrop backdrop) {
        List<SceneBackdrop.Band> bands = new ArrayList<>(backdrop.bands().size());
        for (ZonePictureSource.Band band : backdrop.bands()) {
            bands.add(new SceneBackdrop.Band(band.top(), band.height(), band.speed(), band.drift()));
        }
        return new SceneBackdrop(toSceneImage(backdrop.picture()), bands);
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

    private List<SpriteMappingFrame> loadMappings(RomSpriteRequest request) {
        int address = request.mappingAddress();
        int count = request.mappingFrameCount();
        if (count == 0) {
            return switch (game) {
                case S1 -> S1SpriteDataLoader.loadMappingFrames(reader(), address);
                case S2 -> S2SpriteDataLoader.loadMappingFrames(reader(), address);
                default -> S3kSpriteDataLoader.loadMappingFrames(reader(), address);
            };
        }
        return switch (game) {
            case S1 -> S1SpriteDataLoader.loadMappingFrames(reader(), address, count);
            case S2 -> S2SpriteDataLoader.loadMappingFrames(reader(), address, count);
            default -> S3kSpriteDataLoader.loadMappingFrames(reader(), address, count);
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
