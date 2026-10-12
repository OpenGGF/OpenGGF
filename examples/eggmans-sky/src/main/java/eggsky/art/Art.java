package eggsky.art;

import com.openggf.mods.scene.RomSpriteRequest;
import com.openggf.mods.scene.RomSpriteRequest.Compression;
import com.openggf.mods.scene.RomSpriteRequest.DplcLayout;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneLevelKit;
import com.openggf.mods.scene.art.SceneArtCache;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import eggsky.core.Recolor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Every ROM sprite the game draws, decoded once per scene and cached. Sonic 3 &amp; Knuckles is
 * the base game and supplies Eggman, the HUD props and the heroes; Sonic 1 and Sonic 2 are
 * opened when supplied for their worlds and creatures. Expected asset failures return null with
 * bounded diagnostics and retries; programming faults reach the scene owner boundary.
 */
public final class Art {
    // S3K palettes (sonic3k.lst).
    private static final int PAL_SONIC_TAILS = 0x0A8A3C;  // Pal_SonicTails
    private static final int PAL_AIZ = 0x0A8B7C;          // Pal_AIZ
    private static final int PAL_KNUCKLES = 0x0A8AFC;     // Pal_Knuckles
    private static final int PAL_CONTINUE = 0x05CBCA;     // Pal_ContinueScreen (Egg Robo on line 1)
    private static final int PAL_HPZ = 0x0669D2;          // Pal_HPZ

    public static final int SHIP_HEAD_IDLE0 = 0;
    public static final int SHIP_HEAD_IDLE1 = 1;
    public static final int SHIP_HEAD_LAUGH = 2;
    public static final int SHIP_HEAD_HURT = 3;
    public static final int SHIP_BODY = 5;
    public static final int SHIP_FLAME = 6;

    private final HashMap<String, SceneRomArt> roms = new HashMap<>();
    private final List<String> games = new ArrayList<>();
    private final HashMap<String, SceneSpriteSet> sets = new HashMap<>();
    private final HashMap<String, SceneArtCache> creatureBanks = new HashMap<>();
    private final ArtFailures failures;
    private final java.util.Map<String, SceneImage> tinted = bounded(256);

    private static <V> java.util.Map<String, V> bounded(int capacity) {
        return new java.util.LinkedHashMap<>(16, .75f, true) {
            @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, V> entry) {
                return size() > capacity;
            }
        };
    }

    /** Drop mod-owned lookup references; live sprites and host-owned textures stay valid. */
    public void clearGeneration() {
        creatureBanks.values().forEach(SceneArtCache::close);
        creatureBanks.clear();
        tinted.clear();
    }
    private int[] aizPalette;
    public final FaunaCatalog fauna = new FaunaCatalog();

    public Art(SceneContext ctx) {
        failures = new ArtFailures(ctx);
        for (String game : new String[] {"s3k", "s2", "s1"}) {
            SceneRomArt rom = ctx.art().rom(game);
            if (rom != null) {
                roms.put(game, rom);
                games.add(game);
            }
        }
        SceneRomArt running = ctx.art().rom();
        if (!roms.containsKey("s3k") && running != null) {
            roms.put(running.gameId(), running);
            if (!games.contains(running.gameId())) games.add(running.gameId());
        }
    }

    /** Immutable snapshots; records retain bounded text, not exception objects or assets. */
    public record Failure(String request, String owner, String kind, String cause) { }
    public List<Failure> failures() {
        return failures.entries().stream().map(entry -> new Failure(entry.request(), entry.owner(),
                entry.kind().name(), entry.cause())).toList();
    }

    /** Optional level kits use the same cause reporting and retry policy as sprites. */
    public SceneLevelKit levelKit(String game, int zone, int act) {
        String request = "level:" + game + ":" + zone + ":" + act;
        if (!failures.ready(request)) return null;
        SceneRomArt rom = roms.get(game);
        if (rom == null) {
            failures.record(request, ArtFailures.Kind.OPTIONAL_ROM_MISSING, "ROM unavailable: " + game);
            return null;
        }
        try {
            SceneLevelKit kit = rom.levelKit(zone, act);
            if (kit == null) failures.record(request, ArtFailures.Kind.UNSUPPORTED, "No level kit for this act");
            else failures.succeeded(request);
            return kit;
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException invalid) {
            failures.record(request, invalid);
            return null;
        }
    }

    /** Supplied games, S3K first. */
    public List<String> games() {
        return games;
    }

    public SceneRomArt rom(String game) {
        return roms.get(game);
    }

    public SceneRomArt s3k() {
        return roms.get("s3k");
    }

    private int[] aiz() {
        if (aizPalette == null) {
            int[] prepared = new int[64];
            SceneRomArt rom = s3k();
            if (rom != null) {
                System.arraycopy(rom.palette(PAL_SONIC_TAILS, 16), 0, prepared, 0, 16);
                System.arraycopy(rom.palette(PAL_AIZ, 48), 0, prepared, 16, 48);
            }
            aizPalette = prepared; // Publish only a complete successful palette.
        }
        return aizPalette;
    }

    private int[] withLine(int[] base, int line, int address) {
        int[] p = base.clone();
        System.arraycopy(s3k().palette(address, 16), 0, p, line * 16, 16);
        return p;
    }

    /** A named S3K sprite set (see the switch for names), or null. */
    public SceneSpriteSet set(String key) {
        if (sets.containsKey(key)) return sets.get(key);
        String request = "set:" + key;
        if (!failures.ready(request)) return null;
        SceneSpriteSet set = null;
        SceneRomArt rom = s3k();
        if (rom != null) {
            try {
                set = switch (key) {
                    case "ship" -> rom.sprites(RomSpriteRequest.of(0x0D771E, Compression.NEMESIS, // ArtNem_RobotnikShip
                            0x06820C, 0), aiz());                                                // Map_RobotnikShip
                    case "boss_explosion" -> rom.sprites(RomSpriteRequest.of(0x0D73CE, Compression.NEMESIS, // ArtNem_BossExplosion
                            0x083FFC, 0), aiz());                                                // Map_BossExplosion
                    case "explosion" -> rom.sprites(RomSpriteRequest.of(0x19200A, Compression.NEMESIS, // ArtNem_Explosion
                            0x01E758, 0), aiz());                                                // Map_Explosion
                    case "ring" -> rom.sprites(RomSpriteRequest.of(0x192AEE, Compression.NEMESIS, // ArtNem_RingHUDText
                            0x01A99A, 1), aiz());                                                // Map_Ring
                    case "monitor" -> rom.sprites(RomSpriteRequest.of(0x190F4A, Compression.NEMESIS, // ArtNem_Monitors
                            0x01DBA2, 0), aiz());                                                // Map_Monitor
                    case "tornado" -> rom.sprites(RomSpriteRequest.of(0x382624, Compression.KOSINSKI_MODULED, // ArtKosM_AIZIntroPlane
                            0x364470, 0), aiz());                                                // Map_AIZIntroPlane
                    case "rock" -> rom.sprites(RomSpriteRequest.of(0x38DC90, Compression.NEMESIS, // ArtNem_AIZMisc1
                            0x21DCDC, 1), aiz());                                                // Map_AIZRock
                    case "capsule" -> rom.sprites(RomSpriteRequest.of(0x0DD990, Compression.NEMESIS, // ArtNem_EggCapsule
                            0x086BFC, 0), aiz());                                                // Map_EggCapsule
                    case "flicky" -> rom.sprites(RomSpriteRequest.of(0x1931D6, Compression.NEMESIS, // ArtNem_BlueFlicky
                            0x02CEBA, 0), aiz());                                                // Map_Animals1
                    case "starpost" -> rom.sprites(RomSpriteRequest.of(0x192D2A, Compression.NEMESIS, // ArtNem_EnemyPtsStarPost
                            0x02D348, 0).withTileOffset(-8), aiz());                             // Map_StarPost
                    case "starpost_stars" -> rom.sprites(RomSpriteRequest.of(0x192D2A, Compression.NEMESIS,
                            0x02D3AA, 0).withTileOffset(-8), aiz());                             // Map_StarpostStars
                    case "big_ring" -> rom.sprites(RomSpriteRequest.streamed(0x0D8766, 0x2700, // ArtUnc_SSEntryRing
                            0x0619E0, 0x061ABE, DplcLayout.OBJECT, 1), aiz());                   // Map/DPLC_SSEntryRing
                    case "egg_robo" -> rom.sprites(RomSpriteRequest.of(0x17B17E, Compression.KOSINSKI_MODULED, // ArtKosM_EggRoboBadnik
                            0x184F34, 0), rom.palette(PAL_CONTINUE, 64));                        // Map_EggRobo
                    case "chaos_emerald" -> rom.sprites(RomSpriteRequest.of(0x0AE3EC, Compression.KOSINSKI_MODULED, // ArtKosM_SSChaosEmerald? (results emeralds)
                            0x00A6B2, 0), rom.palette(0x009D1E, 64));                            // Map_SSResultsEmeralds
                    case "hpz_emerald" -> rom.sprites(RomSpriteRequest.of(0x174B28, Compression.NEMESIS, // ArtNem_HPZEmeraldMisc
                            0x091006, 3), hpz());                                                // Map_HPZEmeraldMisc
                    case "shield" -> rom.sprites(RomSpriteRequest.streamed(0x18E8A4, 0x1040, // ArtUnc_LightningShield
                            0x019DC8, 0x019EFA, DplcLayout.PLAYER, 0), aiz());
                    case "bubble_shield" -> rom.sprites(RomSpriteRequest.streamed(0x18F984, 0x1140, // ArtUnc_BubbleShield
                            0x019F82, 0x01A076, DplcLayout.PLAYER, 0), aiz());
                    case "spikes" -> rom.sprites(RomSpriteRequest.of(0x1927FE, Compression.NEMESIS, // ArtNem_SpikesSprings
                            0x024456, 0).withTileOffset(-8), aiz());                             // Map_Spikes
                    case "sonic_icon" -> rom.sprites(RomSpriteRequest.of(0x190D34, Compression.NEMESIS, // ArtNem_SonicLifeIcon
                            0x01DBA2, 0).withTileOffset(0x310), aiz());
                    case "wreck" -> rom.sprites(RomSpriteRequest.of(0x364BF2, Compression.NEMESIS, // ArtNem_AIZMiniboss
                            0x3624D0, 1), withLine(aiz(), 1, 0x06917C));                         // Map_AIZMiniboss, Pal_AIZMiniboss
                    case "sonic" -> rom.character("sonic");
                    case "tails" -> rom.character("tails");
                    case "tails_tails" -> rom.characterAccessory("tails");
                    case "knuckles" -> rom.character("knuckles");
                    default -> {
                        failures.record(request, ArtFailures.Kind.UNSUPPORTED, "Unknown sprite set");
                        yield null;
                    }
                };
            } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException invalid) {
                failures.record(request, invalid);
            }
        } else {
            failures.record(request, ArtFailures.Kind.OPTIONAL_ROM_MISSING, "ROM unavailable: s3k");
        }
        if (set != null) { sets.put(key, set); failures.succeeded(request); }
        return set;
    }

    /** Frame {@code frame} of a named set, or null. */
    public SceneSprite frame(String key, int frame) {
        return frame(set(key), "frame:" + key + ":" + frame, frame);
    }

    /** Lazy rasterisation has its own bounded diagnostic/retry entry. */
    public SceneSprite frame(SceneSpriteSet set, String request, int frame) {
        if (set == null || frame < 0 || frame >= set.frameCount() || !failures.ready(request)) return null;
        try {
            SceneSprite sprite = set.frame(frame);
            failures.succeeded(request);
            return sprite;
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException invalid) {
            failures.record(request, invalid);
            return null;
        }
    }

    /** Hidden Palace's palette with the Master Emerald's greens (Obj_HPZMasterEmerald loc_90700). */
    private int[] hpz() {
        int[] palette = aiz().clone();
        System.arraycopy(s3k().palette(PAL_HPZ, 48), 0, palette, 16, 48);
        System.arraycopy(s3k().palette(0x09070E, 2), 0, palette, 48 + 1, 2);
        return palette;
    }

    /** Knuckles' own palette line for his character frames, or null. */
    public int[] knucklesPalette() {
        return s3k() == null ? null : s3k().palette(PAL_KNUCKLES, 16);
    }

    /**
     * A creature body for a species: its ROM sprites in {@code palette} (64 colours, usually the
     * planet's level palette) recoloured by {@code recolor}. Cached by key.
     */
    public SceneSpriteSet creature(FaunaDef def, int[] palette, String cacheKey) {
        String request = "creature:" + cacheKey;
        if (!failures.ready(request)) return null;
        SceneRomArt rom = roms.get(def.game());
        if (rom == null) {
            failures.record(request, ArtFailures.Kind.OPTIONAL_ROM_MISSING, "ROM unavailable: " + def.game());
            return null;
        }
        try {
            // The SDK keys actual requests plus copied palettes, independently of caller labels.
            // At most three supplied games retain 32 banks each; eviction leaves live images valid.
            SceneSpriteSet set = creatureBanks.computeIfAbsent(def.game(), game -> new SceneArtCache(rom, 32))
                    .sprites(def.request(), palette);
            failures.succeeded(request);
            return set;
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException invalid) {
            failures.record(request, invalid);
            return null;
        }
    }

    /** A recoloured copy of a sprite frame's image, cached by key. */
    public SceneSprite recolored(SceneSprite sprite, Recolor recolor, String key) {
        if (sprite == null || recolor == null || recolor.isIdentity()) {
            return sprite;
        }
        SceneImage image = tinted.get(key);
        if (image == null) {
            image = recolor.apply(sprite.image());
            tinted.put(key, image);
        }
        return new SceneSprite(image, sprite.originX(), sprite.originY());
    }
}
