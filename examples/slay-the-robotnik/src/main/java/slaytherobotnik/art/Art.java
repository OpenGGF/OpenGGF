package slaytherobotnik.art;

import com.openggf.mods.scene.RomSpriteRequest;
import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneLevelStage;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every picture the game draws: text-art icons from the mod's assets, and sprites decoded
 * from the player's Sonic 3 &amp; Knuckles ROM (characters, badniks, bosses, items). ROM sprites
 * are loaded on first use and kept for the scene's lifetime.
 */
public final class Art {
    private final SceneContext ctx;
    private final Map<String, SceneImage> icons;
    private final Map<String, SceneSpriteSet> characterSets = new HashMap<>();
    private final Map<String, SceneSpriteSet> romSets = new HashMap<>();
    private final Map<String, SceneImage> overviews = new HashMap<>();
    private final Map<String, SceneBackdrop> backdrops = new HashMap<>();
    private final Map<String, List<SceneLevelStage>> stages = new HashMap<>();
    /** The last few stage foregrounds (each a screen of pixels), most recent last. */
    private final Map<String, SceneImage> foregrounds = new java.util.LinkedHashMap<>();
    private SceneImage blank;

    private final CardRecipes cards;
    private final CardRecipes relics;

    public Art(SceneContext ctx, byte[] iconText, byte[] cardText, byte[] relicText) {
        this.ctx = ctx;
        this.icons = TextArt.parse(iconText);
        this.cards = new CardRecipes(cardText);
        this.relics = new CardRecipes(relicText);
    }

    /** Relic pictures from {@code art/relics.txt} (the same recipe format as cards). */
    public CardRecipes relics() {
        return relics;
    }

    /** Card illustrations from {@code art/cards.txt}. */
    public CardRecipes cards() {
        return cards;
    }

    /** True when ROM art is available (always, for this S3K patch mod). */
    public boolean hasRom() {
        return ctx.art().rom() != null;
    }

    public SceneRomArt rom() {
        return ctx.art().rom();
    }

    /** A text-art icon by name; a 1x1 transparent image when missing. */
    public SceneImage icon(String name) {
        SceneImage image = icons.get(name);
        if (image == null) {
            if (blank == null) {
                blank = new SceneImage(1, 1, new int[1]);
            }
            return blank;
        }
        return image;
    }

    public boolean hasIcon(String name) {
        return icons.containsKey(name);
    }

    /** A playable character's ROM frames ("sonic", "tails", "knuckles"), or null without a ROM. */
    public SceneSpriteSet character(String id) {
        if (!hasRom()) {
            return null;
        }
        return characterSets.computeIfAbsent(id, key -> rom().character(key));
    }

    /** A sprite from the ROM table in {@link RomSprites}, or null when unavailable. */
    public SceneSpriteSet rom(String key) {
        if (!hasRom()) {
            return null;
        }
        if (romSets.containsKey(key)) {
            return romSets.get(key);
        }
        SceneSpriteSet set = null;
        try {
            set = RomSprites.load(rom(), key);
        } catch (RuntimeException e) {
            set = null;
        }
        romSets.put(key, set);
        return set;
    }

    /**
     * A zoomed-out render of a zone act's level, at most {@code maxHeight} pixels tall, or null
     * when the game cannot render one ({@link SceneRomArt#levelOverview}).
     */
    public SceneImage levelOverview(int zone, int act, int maxHeight) {
        String key = zone + ":" + act + ":" + maxHeight;
        if (!overviews.containsKey(key)) {
            SceneImage image = null;
            try {
                image = hasRom() ? rom().levelOverview(zone, act, maxHeight) : null;
            } catch (RuntimeException e) {
                image = null;
            }
            overviews.put(key, image);
        }
        return overviews.get(key);
    }

    /**
     * Runs of a zone act's floor with room above them ({@link SceneRomArt#levelStages}), or
     * none when the game cannot find any.
     */
    public List<SceneLevelStage> levelStages(int zone, int act, int width, int headroom, int maxRise) {
        String key = zone + ":" + act + ":" + width + ":" + headroom + ":" + maxRise;
        if (!stages.containsKey(key)) {
            List<SceneLevelStage> found = List.of();
            try {
                found = hasRom() ? rom().levelStages(zone, act, width, headroom, maxRise) : List.of();
            } catch (RuntimeException e) {
                found = List.of();
            }
            stages.put(key, found);
        }
        return stages.get(key);
    }

    /**
     * A rectangle of a zone act's foreground ({@link SceneRomArt#levelForeground}), or null.
     * Keeps the last eight, so a room's stage is built once.
     */
    public SceneImage levelForeground(int zone, int act, int x, int y, int width, int height) {
        String key = zone + ":" + act + ":" + x + ":" + y + ":" + width + ":" + height;
        boolean known = foregrounds.containsKey(key);
        SceneImage image = foregrounds.remove(key);
        if (!known) {
            try {
                image = hasRom() ? rom().levelForeground(zone, act, x, y, width, height) : null;
            } catch (RuntimeException e) {
                image = null;
            }
        }
        foregrounds.put(key, image);
        if (foregrounds.size() > 8) {
            foregrounds.remove(foregrounds.keySet().iterator().next());
        }
        return image;
    }

    /** ArtUnc_SlotOptions: the Slot Machine bonus stage's eight reel faces, $200 bytes each. */
    private static final int SLOT_FACES = 0x158CAE;
    /** Pal_Slot_Special: its first line colours the reels. */
    private static final int SLOT_PALETTE = 0xA9C7C;
    /** byte_4C8CC: reel A's strip of eight faces; reels B and C follow 8 and 16 bytes on. */
    private static final int SLOT_STRIPS = 0x4C8CC;
    private SceneImage[] slotFaces;
    private int[][] slotStrips;

    /**
     * A Slot Machine bonus stage reel face (0 Jackpot, 1 Sonic, 2 Tails, 3 Knuckles, 4 Robotnik,
     * 5 Ring, 6 Bar, 7 Super Sonic), 32 pixels square, or null without the ROM. Each face is
     * sixteen 8x8 tiles stored a column at a time, as the stage's reel copy reads them.
     */
    public SceneImage slotFace(int face) {
        if (slotFaces == null) {
            slotFaces = new SceneImage[8];
            try {
                if (hasRom()) {
                    int[] palette = rom().palette(SLOT_PALETTE, 16);
                    for (int f = 0; f < 8; f++) {
                        slotFaces[f] = rom().tiles(SLOT_FACES, RomSpriteRequest.Compression.UNCOMPRESSED, f * 16, 4, 4,
                                true, palette);
                    }
                }
            } catch (RuntimeException e) {
                slotFaces = new SceneImage[8];
            }
        }
        return face >= 0 && face < 8 ? slotFaces[face] : null;
    }

    /** The three reels' strips of faces (byte_4C8CC and the two after it), or null without the ROM. */
    public int[][] slotStrips() {
        if (slotStrips == null && hasRom()) {
            try {
                byte[] bytes = rom().read(SLOT_STRIPS, 24);
                int[][] strips = new int[3][8];
                for (int i = 0; i < 24; i++) {
                    strips[i / 8][i % 8] = bytes[i] & 7;
                }
                slotStrips = strips;
            } catch (RuntimeException e) {
                slotStrips = null;
            }
        }
        return slotStrips;
    }

    /** A zone act's parallax background from the ROM ({@link SceneRomArt#zoneBackdrop}), or null. */
    public SceneBackdrop zoneBackdrop(int zone, int act) {
        String key = zone + ":" + act;
        if (!backdrops.containsKey(key)) {
            SceneBackdrop backdrop = null;
            try {
                backdrop = hasRom() ? rom().zoneBackdrop(zone, act) : null;
            } catch (RuntimeException e) {
                backdrop = null;
            }
            backdrops.put(key, backdrop);
        }
        return backdrops.get(key);
    }

    /** The act's title card ({@link SceneRomArt#titleCard}), or null when the game has none for it. */
    public SceneSpriteSet titleCard(int zone, int act) {
        try {
            return hasRom() && rom().hasTitleCard(zone, act) ? rom().titleCard(zone, act) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private SceneSprite emptyTornado;

    /**
     * The Tornado (Map_AIZIntroPlane frame 0) with its cockpit emptied. The ROM art has Tails at
     * the controls; his pixels all lie in the box x 50-70, y 7-23 of the frame, above the
     * fuselage's top edge (row 24) and left of the windshield (x 72), so clearing that box
     * leaves the plane whole for someone else to fly.
     */
    public SceneSprite emptyTornado() {
        if (emptyTornado == null) {
            SceneSprite plane = romFrame("tornado", 0);
            if (plane == null) {
                return null;
            }
            SceneImage image = plane.image();
            int w = image.width();
            int h = image.height();
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    boolean pilot = x >= 50 && x <= 70 && y >= 7 && y <= 23;
                    pixels[y * w + x] = pilot ? 0 : image.pixel(x, y);
                }
            }
            emptyTornado = new SceneSprite(new SceneImage(w, h, pixels), plane.originX(), plane.originY());
        }
        return emptyTornado;
    }

    /** One frame of a ROM sprite, or null. */
    public SceneSprite romFrame(String key, int frame) {
        SceneSpriteSet set = rom(key);
        return set == null || frame < 0 || frame >= set.frameCount() ? null : set.frame(frame);
    }
}
