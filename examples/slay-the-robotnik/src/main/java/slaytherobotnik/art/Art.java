package slaytherobotnik.art;

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

    /** One frame of a ROM sprite, or null. */
    public SceneSprite romFrame(String key, int frame) {
        SceneSpriteSet set = rom(key);
        return set == null || frame < 0 || frame >= set.frameCount() ? null : set.frame(frame);
    }
}
