package eggsky.art;

import com.openggf.mods.scene.RomSpriteRequest;
import com.openggf.mods.scene.RomSpriteRequest.Compression;
import com.openggf.mods.scene.RomSpriteRequest.DplcLayout;
import com.openggf.mods.scene.SceneContext;
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
 * opened when supplied for their worlds and creatures. Lookups never throw: missing art comes
 * back null and callers draw a stand-in.
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
    private final HashMap<String, SceneImage> tinted = new HashMap<>();
    private int[] aizPalette;
    public final FaunaCatalog fauna = new FaunaCatalog();

    public Art(SceneContext ctx) {
        for (String game : new String[] {"s3k", "s2", "s1"}) {
            try {
                SceneRomArt rom = ctx.art().rom(game);
                if (rom != null) {
                    roms.put(game, rom);
                    games.add(game);
                }
            } catch (RuntimeException unavailable) {
                // That ROM is not supplied.
            }
        }
        if (!roms.containsKey("s3k") && ctx.art().rom() != null) {
            roms.put(ctx.art().rom().gameId(), ctx.art().rom());
            if (!games.contains(ctx.art().rom().gameId())) {
                games.add(ctx.art().rom().gameId());
            }
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
            aizPalette = new int[64];
            SceneRomArt rom = s3k();
            if (rom != null) {
                System.arraycopy(rom.palette(PAL_SONIC_TAILS, 16), 0, aizPalette, 0, 16);
                System.arraycopy(rom.palette(PAL_AIZ, 48), 0, aizPalette, 16, 48);
            }
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
        if (sets.containsKey(key)) {
            return sets.get(key);
        }
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
                    default -> null;
                };
            } catch (RuntimeException failed) {
                set = null;
            }
        }
        sets.put(key, set);
        return set;
    }

    /** Frame {@code frame} of a named set, or null. */
    public SceneSprite frame(String key, int frame) {
        SceneSpriteSet set = set(key);
        if (set == null || frame < 0 || frame >= set.frameCount()) {
            return null;
        }
        try {
            return set.frame(frame);
        } catch (RuntimeException broken) {
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
        if (sets.containsKey(cacheKey)) {
            return sets.get(cacheKey);
        }
        SceneSpriteSet set = null;
        SceneRomArt rom = roms.get(def.game());
        if (rom != null) {
            try {
                set = rom.sprites(def.request(), palette);
            } catch (RuntimeException failed) {
                set = null;
            }
        }
        sets.put(cacheKey, set);
        return set;
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
