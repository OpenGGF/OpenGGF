package slaytherobotnik.art;

import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.HashMap;
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
    private SceneImage blank;

    private final CardRecipes cards;
    private final CardRecipes relics;

    public Art(SceneContext ctx, byte[] iconText, byte[] cardText, byte[] relicText) {
        this.ctx = ctx;
        this.icons = TextArt.parse(ctx.art(), iconText);
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
                blank = ctx.art().image(1, 1, new int[1]);
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
            emptyTornado = new SceneSprite(ctx.art().image(w, h, pixels), plane.originX(), plane.originY());
        }
        return emptyTornado;
    }

    /** One frame of a ROM sprite, or null. */
    public SceneSprite romFrame(String key, int frame) {
        SceneSpriteSet set = rom(key);
        return set == null || frame < 0 || frame >= set.frameCount() ? null : set.frame(frame);
    }
}
