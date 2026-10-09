package typinghog.hub;

import com.openggf.mods.scene.RomSpriteRequest;
import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import typinghog.hub.model.TextArt;

/**
 * Every picture the hub draws: the shipped text art (logo, keyboard backpack, icons) and art
 * decoded from the player's ROMs (Sonic and Tails with their animation scripts, rings, a
 * Flicky, zone backdrops and terrain). ROM art is loaded on first use, kept for the visit, and
 * any game or act the ROMs cannot picture comes back null so screens draw their own fallback.
 */
final class HubArt {
    /** Sonic's and Tails' animation ids in each game's player animation table. */
    static final int ANIM_WALK = 0x00;
    static final int ANIM_RUN = 0x01;
    static final int ANIM_ROLL = 0x02;
    static final int ANIM_WAIT = 0x05;

    private final SceneContext ctx;
    private final Map<String, TextArt.Picture> pictures = new HashMap<>();
    private final Map<String, SceneImage> images = new HashMap<>();
    private final Map<String, SceneSpriteSet> sets = new HashMap<>();
    private final Map<String, Stage> stages = new HashMap<>();
    /** Zone backdrops from level kits, most recent last; kits are big, so only a few are kept. */
    private final Map<String, SceneBackdrop> backdrops = new LinkedHashMap<>();
    private final Map<String, Boolean> missing = new HashMap<>();

    HubArt(SceneContext ctx, HubAssets files) {
        this.ctx = ctx;
        pictures.putAll(TextArt.parse(files.art()));
        pictures.putAll(TextArt.parse(files.logo()));
    }

    /** A shipped text-art picture, or null. */
    SceneImage image(String name) {
        SceneImage image = images.get(name);
        if (image == null) {
            TextArt.Picture p = pictures.get(name);
            if (p == null) return null;
            image = new SceneImage(p.width(), p.height(), p.argb());
            images.put(name, image);
        }
        return image;
    }

    List<String> games() { return ctx.art().availableGames(); }

    SceneRomArt rom(String game) {
        if (game == null) return null;
        try {
            return ctx.art().rom(game);
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    /** A playable character's frames from {@code game} ("sonic", "tails"), or null. */
    SceneSpriteSet character(String game, String code) {
        return load(game + ":char:" + code, () -> rom(game).character(code));
    }

    /** Tails' tails from {@code game}, or null. */
    SceneSpriteSet tailsAccessory(String game) {
        return load(game + ":acc:tails", () -> rom(game).characterAccessory("tails"));
    }

    /** The first supplied game, preferring {@code first}, that has Tails (Sonic 2 or 3 & Knuckles). */
    String tailsGame(String first) {
        if (("s2".equals(first) || "s3k".equals(first)) && games().contains(first)) return first;
        for (String g : List.of("s3k", "s2")) if (games().contains(g)) return g;
        return null;
    }

    /** The spinning ring, from the running game when it can, else any supplied game. */
    SceneSpriteSet ring(String prefer) {
        for (String g : order(prefer)) {
            SceneSpriteSet set = load(g + ":ring", () -> ringFrom(g));
            if (set != null) return set;
        }
        return null;
    }

    private SceneSpriteSet ringFrom(String game) {
        SceneRomArt rom = rom(game);
        return switch (game) {
            // sonic.lst: Nem_Ring $39A0E, Map_Ring $A610, make_art_tile(ArtTile_Ring,1,0);
            // Pal_Sonic $2380 on line 0, Pal_GHZ $23A0 on lines 1-3.
            case "s1" -> rom.sprites(RomSpriteRequest.of(0x39A0E, RomSpriteRequest.Compression.NEMESIS, 0xA610, 1),
                    palette(rom, 0x2380, 0x23A0));
            // s2.asm: ArtNem_Ring (art/nemesis/Ring.nem, byte-matched at $7945C), Obj25_MapUnc_12382,
            // make_art_tile(ArtTile_ArtNem_Ring,1,0); Pal_SonicTails $29E2 and PalPtr_EHZ's palette.
            case "s2" -> rom.sprites(RomSpriteRequest.of(0x7945C, RomSpriteRequest.Compression.NEMESIS, 0x12382, 1),
                    s2Palette(rom, 4));
            // sonic3k.lst: ArtNem_RingHUDText $192AEE, Map_Ring $01A99A on line 1; Pal_SonicTails
            // $0A8A3C and Pal_AIZ $0A8B7C.
            default -> rom.sprites(RomSpriteRequest.of(0x192AEE, RomSpriteRequest.Compression.NEMESIS, 0x01A99A, 1),
                    palette(rom, 0x0A8A3C, 0x0A8B7C));
        };
    }

    /**
     * A story zone's signature badnik ({@link ZoneCast}) in the act's own palette, or null
     * when the zone has none or the ROM cannot draw it.
     */
    SceneSpriteSet badnik(String game, int zone, int act) {
        ZoneCast.Badnik who = ZoneCast.of(game, zone);
        if (who == null) return null;
        return load(game + ":badnik:" + zone, () -> {
            SceneRomArt rom = rom(game);
            int[] palette = null;
            if (rom.hasLevelKit(zone, act)) {
                var kit = rom.levelKit(zone, act);
                if (kit != null) palette = kit.palette();
            }
            if (palette == null) {
                palette = switch (game) {
                    case "s1" -> palette(rom, 0x2380, 0x23A0);
                    case "s2" -> s2Palette(rom, 4);
                    default -> palette(rom, 0x0A8A3C, 0x0A8B7C);
                };
            }
            return rom.sprites(who.request(), palette);
        });
    }

    /** A Flicky (the stand-in instructor when no supplied ROM has Tails), or null. */
    SceneSpriteSet flicky(String prefer) {
        for (String g : order(prefer)) {
            SceneSpriteSet set = load(g + ":flicky", () -> flickyFrom(g));
            if (set != null) return set;
        }
        return null;
    }

    private SceneSpriteSet flickyFrom(String game) {
        SceneRomArt rom = rom(game);
        return switch (game) {
            // sonic.lst: Nem_Flicky $3BF06 with Map_Animal2 $9AFC; animals draw on line 0.
            case "s1" -> rom.sprites(RomSpriteRequest.of(0x3BF06, RomSpriteRequest.Compression.NEMESIS, 0x9AFC, 0),
                    palette(rom, 0x2380, 0x23A0));
            // s2.asm: ArtNem_Flicky $7EF60 with Obj28's mappings at $11E1C.
            case "s2" -> rom.sprites(RomSpriteRequest.of(0x7EF60, RomSpriteRequest.Compression.NEMESIS, 0x11E1C, 0),
                    s2Palette(rom, 4));
            // sonic3k.lst: ArtNem_BlueFlicky $1931D6 with Map_Animals1 $02CEBA.
            default -> rom.sprites(RomSpriteRequest.of(0x1931D6, RomSpriteRequest.Compression.NEMESIS, 0x02CEBA, 0)
                    .withMappingFrameCount(3), palette(rom, 0x0A8A3C, 0x0A8B7C));
        };
    }

    /** The title's stage: a pictured act with terrain, rotating through supplied games per visit. */
    Stage titleStage(String running, int visit, int width, int height) {
        List<int[]> acts = new java.util.ArrayList<>();
        List<String> owners = new java.util.ArrayList<>();
        for (String g : order(running)) {
            for (int[] act : switch (g) {
                case "s1" -> List.of(new int[] {0, 0});
                case "s2" -> List.of(new int[] {1, 0});
                default -> List.of(new int[] {0, 0}, new int[] {1, 0}, new int[] {6, 0}, new int[] {0, 1});
            }) {
                acts.add(act);
                owners.add(g);
            }
        }
        for (int attempt = 0; attempt < acts.size(); attempt++) {
            int i = Math.floorMod(visit + attempt, acts.size());
            String game = owners.get(i);
            int[] act = acts.get(i);
            Stage stage = stages.computeIfAbsent(game + ":" + act[0] + ":" + act[1] + ":" + width,
                    key -> Stage.build(rom(game), act[0], act[1], width, height));
            if (stage.pictured()) return stage;
        }
        return Stage.build(null, 0, 0, width, height);
    }

    /**
     * A zone's background for a story stop or list row: the zone backdrop where the act is
     * pictured, else the act's level kit background. Null when neither exists.
     */
    SceneBackdrop zoneBackdrop(String game, int zone, int act) {
        String key = game + ":" + zone + ":" + act;
        if (backdrops.containsKey(key)) {
            SceneBackdrop bg = backdrops.remove(key);
            backdrops.put(key, bg);
            return bg;
        }
        if (missing.containsKey("bg:" + key)) return null;
        SceneBackdrop bg = null;
        try {
            SceneRomArt rom = rom(game);
            if (rom != null) {
                bg = rom.hasZonePictures(zone, act) ? rom.zoneBackdrop(zone, act) : null;
                if (bg == null && rom.hasLevelKit(zone, act)) {
                    var kit = rom.levelKit(zone, act);
                    bg = kit == null ? null : kit.backdrop();
                }
            }
        } catch (RuntimeException unavailable) {
            bg = null;
        }
        if (bg == null) {
            missing.put("bg:" + key, true);
            return null;
        }
        backdrops.put(key, bg);
        while (backdrops.size() > 4) backdrops.remove(backdrops.keySet().iterator().next());
        return bg;
    }

    /** True once a backdrop for this act is cached (or known missing), so asking costs nothing. */
    boolean backdropReady(String game, int zone, int act) {
        String key = game + ":" + zone + ":" + act;
        return backdrops.containsKey(key) || missing.containsKey("bg:" + key);
    }

    private List<String> order(String prefer) {
        List<String> out = new java.util.ArrayList<>();
        if (prefer != null && games().contains(prefer)) out.add(prefer);
        for (String g : List.of("s3k", "s1", "s2")) if (!out.contains(g) && games().contains(g)) out.add(g);
        return out;
    }

    private SceneSpriteSet load(String key, java.util.function.Supplier<SceneSpriteSet> loader) {
        if (sets.containsKey(key)) return sets.get(key);
        if (missing.containsKey(key)) return null;
        SceneSpriteSet set;
        try {
            set = loader.get();
        } catch (RuntimeException unavailable) {
            set = null;
        }
        if (set == null) missing.put(key, true);
        else sets.put(key, set);
        return set;
    }

    private static int[] palette(SceneRomArt rom, int line0, int lines123) {
        int[] palette = new int[64];
        System.arraycopy(rom.palette(line0, 16), 0, palette, 0, 16);
        System.arraycopy(rom.palette(lines123, 48), 0, palette, 16, 48);
        return palette;
    }

    /** Sonic 2's PalPointers entry {@code id} (s2.asm), over Pal_SonicTails ($29E2) on line 0. */
    private static int[] s2Palette(SceneRomArt rom, int id) {
        byte[] pointer = rom.read(0x2782 + id * 8, 4);
        int address = (pointer[1] & 255) << 16 | (pointer[2] & 255) << 8 | pointer[3] & 255;
        int[] palette = new int[64];
        System.arraycopy(rom.palette(0x29E2, 16), 0, palette, 0, 16);
        System.arraycopy(rom.palette(address, 48), 0, palette, 16, 48);
        return palette;
    }

    /** The seven emeralds' colours, in the order they are won. */
    static int emeraldColour(int i) {
        return switch (i % 7) {
            case 0 -> 0xFF40D0FF;
            case 1 -> 0xFFFFE040;
            case 2 -> 0xFFFF60D0;
            case 3 -> 0xFF60FF70;
            case 4 -> 0xFFFF5040;
            case 5 -> 0xFFB0B8FF;
            default -> 0xFFFFA040;
        };
    }
}
