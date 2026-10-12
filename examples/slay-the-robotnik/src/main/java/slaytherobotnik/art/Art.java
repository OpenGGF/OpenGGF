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
public final class Art implements AutoCloseable {
    private final SceneContext ctx;
    private final Map<String, SceneImage> icons;
    private final Map<String, SceneSpriteSet> characterSets = new HashMap<>();
    private final Map<String, SceneSpriteSet> romSets = new HashMap<>();
    private final Map<String, SceneImage> overviews = new HashMap<>();
    private final Map<String, SceneBackdrop> backdrops = new HashMap<>();
    private final Map<String, List<SceneLevelStage>> stages = new HashMap<>();
    /** The last few stage foregrounds (each a screen of pixels), most recent last. */
    private final Map<String, SceneImage> foregrounds = new java.util.LinkedHashMap<>();
    private final Map<String, SceneSpriteSet> titleCards = new HashMap<>();
    private final java.util.LinkedHashMap<String, Failure> failures = new java.util.LinkedHashMap<>();
    private boolean closed;
    private SceneImage blank;
    private com.openggf.mods.scene.art.SceneArtCache spriteCache;

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

    /** True when the supplied running ROM is available for this visit. */
    public boolean hasRom() { return rom() != null; }

    public SceneRomArt rom() {
        requireOpen();
        var driver = ctx.art();
        return attempt("rom:s3k", () -> {
            SceneRomArt found = driver.rom();
            if (found == null) throw new MissingRom();
            return found;
        });
    }

    private SceneRomArt requireRom() {
        SceneRomArt found = rom();
        if (found == null) throw dependency("rom:s3k");
        return found;
    }

    /** A text-art icon by name; a 1x1 transparent image when missing. */
    public SceneImage icon(String name) {
        requireOpen();
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
        requireOpen();
        return icons.containsKey(name);
    }

    /** A playable character's ROM frames ("sonic", "tails", "knuckles"), or null without a ROM. */
    public SceneSpriteSet character(String id) {
        return cached(characterSets, id, "character:" + id, () -> requireRom().character(id));
    }

    /** A sprite from the ROM table in {@link RomSprites}, or null when unavailable. */
    public SceneSpriteSet rom(String key) {
        return cached(romSets, key, "sprite:" + key, () -> {
            SceneRomArt supplied = requireRom();
            if (spriteCache == null) spriteCache = new com.openggf.mods.scene.art.SceneArtCache(supplied, 32);
            return RomSprites.load(supplied, spriteCache, key);
        });
    }

    /**
     * A zoomed-out render of a zone act's level, at most {@code maxHeight} pixels tall, or null
     * when the game cannot render one ({@link SceneRomArt#levelOverview}).
     */
    public SceneImage levelOverview(int zone, int act, int maxHeight) {
        String key = zone + ":" + act + ":" + maxHeight;
        return cached(overviews, key, "overview:" + key,
                () -> requireRom().levelOverview(zone, act, maxHeight));
    }

    /**
     * Runs of a zone act's floor with room above them ({@link SceneRomArt#levelStages}), or
     * none when the game cannot find any.
     */
    public List<SceneLevelStage> levelStages(int zone, int act, int width, int headroom, int maxRise) {
        String key = zone + ":" + act + ":" + width + ":" + headroom + ":" + maxRise;
        List<SceneLevelStage> found = cached(stages, key, "stages:" + key, () -> {
            SceneRomArt supplied = requireRom();
            if (!supplied.hasZonePictures(zone, act)) throw new UnsupportedOperationException("No zone pictures");
            return supplied.levelStages(zone, act, width, headroom, maxRise);
        });
        return found == null ? List.of() : found;
    }

    /**
     * A rectangle of a zone act's foreground ({@link SceneRomArt#levelForeground}), or null.
     * Keeps the last eight, so a room's stage is built once.
     */
    public SceneImage levelForeground(int zone, int act, int x, int y, int width, int height) {
        requireOpen();
        String key = zone + ":" + act + ":" + x + ":" + y + ":" + width + ":" + height;
        SceneImage image = foregrounds.remove(key);
        if (image == null) image = attempt("foreground:" + key,
                () -> requireRom().levelForeground(zone, act, x, y, width, height));
        if (image != null) {
            foregrounds.put(key, image);
            if (foregrounds.size() > 8) foregrounds.remove(foregrounds.keySet().iterator().next());
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
        requireOpen();
        if (face < 0 || face >= 8) return null;
        if (slotFaces == null) slotFaces = attempt("slot:faces", () -> {
            SceneRomArt supplied = requireRom();
            int[] palette = supplied.palette(SLOT_PALETTE, 16);
            SceneImage[] candidate = new SceneImage[8];
            for (int f = 0; f < candidate.length; f++) {
                candidate[f] = java.util.Objects.requireNonNull(supplied.tiles(SLOT_FACES,
                        RomSpriteRequest.Compression.UNCOMPRESSED, f * 16, 4, 4, true, palette), "slot face");
            }
            return candidate; // Publish only after all eight faces are complete.
        });
        return slotFaces == null ? null : slotFaces[face];
    }

    /** The three reels' strips of faces (byte_4C8CC and the two after it), or null without the ROM. */
    public int[][] slotStrips() {
        requireOpen();
        if (slotStrips == null) slotStrips = attempt("slot:strips", () -> {
            byte[] bytes = requireRom().read(SLOT_STRIPS, 24);
            int[][] candidate = new int[3][8];
            for (int i = 0; i < 24; i++) candidate[i / 8][i % 8] = bytes[i] & 7;
            return candidate;
        });
        return slotStrips;
    }

    /** A zone act's parallax background from the ROM ({@link SceneRomArt#zoneBackdrop}), or null. */
    public SceneBackdrop zoneBackdrop(int zone, int act) {
        String key = zone + ":" + act;
        return cached(backdrops, key, "backdrop:" + key, () -> requireRom().zoneBackdrop(zone, act));
    }

    /** The act's title card ({@link SceneRomArt#titleCard}), or null when the game has none for it. */
    public SceneSpriteSet titleCard(int zone, int act) {
        String key = zone + ":" + act;
        return cached(titleCards, key, "title:" + key, () -> {
            SceneRomArt supplied = requireRom();
            return supplied.hasTitleCard(zone, act) ? supplied.titleCard(zone, act) : null;
        });
    }

    private SceneSprite emptyTornado;

    /**
     * The Tornado (Map_AIZIntroPlane frame 0) with its cockpit emptied. The ROM art has Tails at
     * the controls; his pixels all lie in the box x 50-70, y 7-23 of the frame, above the
     * fuselage's top edge (row 24) and left of the windshield (x 72), so clearing that box
     * leaves the plane whole for someone else to fly.
     */
    public SceneSprite emptyTornado() {
        requireOpen();
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
        return set == null || frame < 0 || frame >= set.frameCount() ? null
                : attempt("frame:" + key + ":" + frame, () -> set.frame(frame));
    }

    public enum FailureKind { OPTIONAL_ROM_MISSING, UNSUPPORTED, INVALID_REQUEST, UNAVAILABLE }
    public record Failure(String request, String owner, FailureKind kind, String cause, long retryAt) { }

    /** At most 64 bounded request/cause entries; no exception graphs or image ownership are retained. */
    public List<Failure> failures() { return List.copyOf(failures.values()); }

    private <T> T cached(Map<String, T> cache, String key, String request, java.util.function.Supplier<T> loader) {
        requireOpen();
        T value = cache.get(key);
        if (value == null) {
            value = attempt(request, loader);
            if (value != null) cache.put(key, value);
        }
        return value;
    }

    private <T> T attempt(String request, java.util.function.Supplier<T> loader) {
        requireOpen();
        String id = bounded(request);
        Failure prior = failures.get(id);
        if (prior != null && ctx.ticks() < prior.retryAt()) return null;
        try {
            T value = loader.get();
            if (value == null) record(id, FailureKind.UNSUPPORTED, "No art for this request");
            else failures.remove(id);
            return value;
        } catch (DependencyFailure unavailable) {
            Failure source = unavailable.failure;
            record(id, source.kind(), source.cause(), source.retryAt());
        } catch (MissingRom missing) {
            record(id, FailureKind.OPTIONAL_ROM_MISSING, "Running ROM unavailable");
        } catch (UnsupportedOperationException unsupported) {
            record(id, FailureKind.UNSUPPORTED, detail(unsupported));
        } catch (IllegalArgumentException invalid) {
            record(id, FailureKind.INVALID_REQUEST, detail(invalid));
        } catch (IllegalStateException state) {
            if (!(state.getCause() instanceof java.io.IOException)) throw state;
            record(id, FailureKind.UNAVAILABLE, detail(state));
        }
        return null;
    }

    private void record(String request, FailureKind kind, String cause) {
        long now = ctx.ticks();
        long retryAt = now > Long.MAX_VALUE - 120 ? Long.MAX_VALUE : now + 120;
        record(request, kind, cause, retryAt);
    }
    private void record(String request, FailureKind kind, String cause, long retryAt) {
        if (!failures.containsKey(request) && failures.size() == 64)
            failures.remove(failures.keySet().iterator().next());
        failures.put(request, new Failure(request, ctx.ownerModId(), kind, bounded(cause), retryAt));
    }
    private static String detail(RuntimeException cause) {
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
    private static String bounded(String value) { return value.length() <= 256 ? value : value.substring(0, 256); }
    private void requireOpen() { if (closed) throw new IllegalStateException("Art facade is closed"); }
    private DependencyFailure dependency(String request) {
        Failure source = failures.get(bounded(request));
        if (source == null) throw new AssertionError("Missing asset failure status: " + request);
        return new DependencyFailure(source);
    }
    private static final class DependencyFailure extends RuntimeException {
        final Failure failure;
        DependencyFailure(Failure failure) { this.failure = failure; }
    }
    private static final class MissingRom extends RuntimeException { }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (spriteCache != null) spriteCache.close();
        spriteCache = null;
        romSets.clear(); characterSets.clear(); overviews.clear(); backdrops.clear(); titleCards.clear();
        stages.clear(); foregrounds.clear(); icons.clear(); failures.clear();
        slotFaces = null; slotStrips = null; emptyTornado = null; blank = null;
    }

}
