package com.openggf.game.sonic3k.titlescreen;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.data.compression.EnigmaReader;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.S3kFrontendPaletteUploader;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.util.PatternDecompressor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.Arrays;

/** SK_Alone_Title_Screen's ROM assets. Every address belongs to the original 2 MiB SK cartridge. */
final class SonicKnucklesTitleArt {
    // Verified byte-for-byte against SK CRC0658F691 and skdisasm8b2cf3c.
    private static final int BASE = PatternAtlasRange.S3K_TITLE_SCREEN_ANIMATION.base();
    private final GraphicsManager graphics;
    private final Rom rom;
    private final RomByteReader reader;
    private final Pattern[] patterns = new Pattern[0x800];
    private final Pattern[] standing;
    private final Pattern[] landing;
    private final Pattern[] hands;
    private final Pattern[] bannerArt;
    private final Pattern[] menuArt;
    private final com.openggf.sprites.animation.SpriteAnimationSet iconAnimations;
    private final int[] sega;
    private final int[] background;
    private final int[][] foreground = new int[4][];
    private final PatternDesc desc = new PatternDesc();
    private final PatternSpriteRenderer falling;
    private final PatternSpriteRenderer deathEgg;
    private final PatternSpriteRenderer mountain;
    private PatternSpriteRenderer banner;
    private PatternSpriteRenderer menu;
    private PatternSpriteRenderer tm;
    private PatternSpriteRenderer copyright;
    private boolean cached;

    SonicKnucklesTitleArt(Rom rom, GraphicsManager graphics) throws IOException {
        this.rom = rom;
        this.graphics = graphics;
        reader = RomByteReader.fromRom(rom);
        put(0, PatternDecompressor.kosinski(rom, 0xCCA20));
        put(0x49C, PatternDecompressor.kosinski(rom, 0xCC6A0));
        var fall = PatternDecompressor.kosinski(rom, 0xD2504);
        put(0x4EF, fall);
        var egg = PatternDecompressor.kosinski(rom, 0xD33E4);
        put(0x680, egg);
        var peak = PatternDecompressor.kosinski(rom, 0xD3A64);
        put(0x7A0, peak);
        standing = PatternDecompressor.kosinskiModuled(rom, 0xCD910);
        landing = PatternDecompressor.kosinskiModuled(rom, 0xD0E12);
        hands = PatternDecompressor.kosinski(rom, 0xD4BA6);
        bannerArt = PatternDecompressor.kosinskiModuled(rom, 0xD3BB4);
        menuArt = PatternDecompressor.kosinskiModuled(rom, 0xD5CC6);
        iconAnimations = S3kSpriteDataLoader.loadAnimationSet(reader, 0x5C5A, 3);
        sega = mapping(0xCBA68, 0x849C);
        background = mapping(0xCC3AA, 0);
        int[] maps = {0xCBB32, 0xCBCE0, 0xCBF3E, 0xCC1C4};
        for (int i = 0; i < maps.length; i++) foreground[i] = mapping(maps[i], 0x80E2);
        falling = sprite(fall, 0xCB860, 0, 0x4EF);
        deathEgg = sprite(egg, 0xCB924, 2, 0x680);
        mountain = sprite(peak, 0xCBA4C, 3, 0x7A0);
    }

    byte[] palette(int address, int count) throws IOException { return rom.readBytes(address, count); }

    void startScene() {
        put(0xE2, standing);
        put(0xE2 + 0x380, landing);
    }

    void finishLanding() throws IOException {
        put(0x4EE, bannerArt);
        put(0x462, menuArt);
        banner = sprite(bannerArt, 0xCB994, 0, 0x4EE);
        menu = sprite(menuArt, 0x5ED8, 0, 0x462);
        tm = sprite(menuArt, 0x5F62, 0, 0x462);
        copyright = sprite(menuArt, 0x5F6C, 0, 0x462);
    }

    void initialHands() {
        copyHands(0, 0xE3, 3);
        copyHands(0x640 / 32, 0xE6, 0x290 / 16);
        // Obj_SKTitle_HandAnimMain (sonic3k.asm:7042-7070): Knuckles'
        // two regions initially contain numbered template tiles in standing art.
        copyHands(0x1080 / 32, 0xE2 + 0x2D, 0x2F0 / 16);
        copyHands(0x2220 / 32, 0xE2 + 0x5C, 0x140 / 16);
    }

    com.openggf.sprites.animation.SpriteAnimationScript iconAnimation(int option) {
        return iconAnimations.getScript(option);
    }

    void copyHands(int source, int destination, int count) {
        for (int i = 0; i < count; i++) patterns[destination + i] = hands[source + i];
        cached = false;
    }

    private void put(int tile, Pattern[] art) {
        System.arraycopy(art, 0, patterns, tile, art.length);
        cached = false;
    }

    private PatternSpriteRenderer sprite(Pattern[] art, int mappings, int palette, int tile) throws IOException {
        var renderer = new PatternSpriteRenderer(new ObjectSpriteSheet(art,
                S3kSpriteDataLoader.loadMappingFrames(reader, mappings), palette, 1), graphics);
        // Logical simulation never uploads textures. The draw owner caches them below.
        return renderer;
    }

    private int[] mapping(int address, int base) throws IOException {
        try (var channel = Channels.newChannel(new ByteArrayInputStream(rom.readBytes(address, 4096)))) {
            byte[] bytes = EnigmaReader.decompress(channel, base);
            int[] words = new int[bytes.length / 2];
            for (int i = 0; i < words.length; i++) words[i] = (bytes[i * 2] & 255) << 8 | bytes[i * 2 + 1] & 255;
            return words;
        }
    }

    void draw(SonicKnucklesTitleScreen screen, byte[] palette) {
        if (graphics.isHeadlessMode()) return;
        if (!cached) {
            graphics.beginPatternAtlasBatch();
            try {
                for (int i = 0; i < patterns.length; i++) if (patterns[i] != null)
                    graphics.cachePatternTexture(patterns[i], BASE + i);
            } finally { graphics.endPatternAtlasBatch(); }
            cached = true;
        }
        for (int line = 0; line < 4; line++) S3kFrontendPaletteUploader.cacheLineFromBytes(
                graphics, Arrays.copyOfRange(palette, line * 32, line * 32 + 32), line);
        int ox = (Math.max(320, graphics.getProjectionWidth()) - 320) / 2;
        graphics.beginPatternBatch();
        plane(background, ox, screen.backgroundY(), false);
        if (!screen.sceneStarted()) plane(sega, ox, 0, true);
        else {
            if (!screen.landed()) {
                plane(sega, ox, screen.foregroundY(), true);
                if (screen.foregroundY() > 0) plane(foreground[screen.mappingFrame()], ox,
                        screen.foregroundY() - 256, true);
            } else plane(foreground[3], ox, 0, true);
            deathEgg.ensurePatternsCached(graphics, BASE + 0x680);
            deathEgg.drawFrameIndex(0, ox + 192, screen.deathEggY());
            mountain.ensurePatternsCached(graphics, BASE + 0x7A0);
            mountain.drawFrameIndex(0, ox + 192, 0x128 - screen.backgroundY());
            if (!screen.landed()) {
                falling.ensurePatternsCached(graphics, BASE + 0x4EF);
                falling.drawFrameIndex(0, ox + 104, screen.sonicY() - 128);
            }
            if (banner != null && screen.bannerVisible()) {
                banner.ensurePatternsCached(graphics, BASE + 0x4EE);
                banner.drawFrameIndex(0, ox + 160, screen.bannerY() - 128);
            }
            if (screen.menuReady()) {
                menu.ensurePatternsCached(graphics, BASE + 0x462);
                // Obj_SKTitle_IconDisplay (sonic3k.asm:7185-7220): paired
                // label sprites exchange depth/frame as the selection rotates.
                int angle = screen.iconAngle();
                int dx = com.openggf.physics.TrigLookupTable.cosHex((angle + 0x98) & 255) * 5 >> 8;
                int dy = com.openggf.physics.TrigLookupTable.sinHex((angle + 0x98) & 255) * 5 >> 8;
                boolean exchange = (byte) (angle - 0xA0) < 0;
                menu.drawFrameIndexWithPaletteBase(exchange ? 2 : 0,
                        ox + 136 + (exchange ? -dx : dx), 208 + (exchange ? -dy : dy), false, false, 0);
                menu.drawFrameIndexWithPaletteBase(exchange ? 1 : 3,
                        ox + 136 + (exchange ? dx : -dx), 208 + (exchange ? dy : -dy), false, false, 0);
                menu.drawFrameIndexWithPaletteBase(screen.portraitFrame(), ox + 80, 204, false, false, 0);
                tm.ensurePatternsCached(graphics, BASE + 0x462);
                tm.drawFrameIndex(0, ox + 290, 157);
                copyright.ensurePatternsCached(graphics, BASE + 0x462);
                copyright.drawFrameIndex(0, ox + 255, 210);
            }
        }
        graphics.flushPatternBatch();
    }

    private void plane(int[] map, int ox, int scrollY, boolean foregroundPlane) {
        int rows = map.length / 40;
        for (int y = 0; y < 224; y += 8) {
            // A title name table has 32 physical rows; the native $100 scroll
            // wraps to row zero, rather than wrapping at the 28 visible rows.
            int row = ((y + scrollY) & 255) / 8;
            if (row >= rows) continue;
            for (int x = 0; x < 40; x++) {
                int word = map[row * 40 + x];
                if ((word & 0x7FF) == 0 && foregroundPlane) continue;
                desc.set(word);
                graphics.renderPatternWithId(BASE + (word & 0x7FF), desc, ox + x * 8, y);
            }
        }
    }
}
