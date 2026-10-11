package threeislands.art;

import com.openggf.mods.scene.RomSpriteRequest;
import com.openggf.mods.scene.RomSpriteRequest.Compression;
import com.openggf.mods.scene.RomSpriteRequest.DplcLayout;
import com.openggf.mods.scene.SceneArt;
import com.openggf.mods.scene.SceneLevelKit;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.HashMap;
import java.util.Map;
import threeislands.core.HeroId;
import threeislands.core.Zone;

/**
 * Every picture in the game comes from the player's own ROMs through this class: characters,
 * badniks, bosses, monitors and level kits, each decoded once and cached. Nothing here is a
 * disassembly file; addresses are the labels' ROM offsets, cited beside each request, and the
 * palette lines are each object's {@code make_art_tile} line.
 *
 * <p>Sonic 1 and Sonic 2 badniks are coloured with the level kit palette of their home zone,
 * which is the palette they use in the stock game (line 0 is the player palette). Sonic 3 &amp;
 * Knuckles art uses the stock zone and boss palettes listed below (from sonic3k.lst), as
 * Slay the Robotnik's {@code RomSprites} does.
 */
public final class Art {
    // S3K palettes.
    private static final int PAL_SONIC_TAILS = 0x0A8A3C;      // Pal_SonicTails (line 0)
    private static final int PAL_AIZ = 0x0A8B7C;              // Pal_AIZ (lines 1-3)
    private static final int PAL_AIZ_FIRE = 0x0A8BDC;         // Pal_AIZFire (lines 1-3, burning act 2)
    private static final int PAL_AIZ_END_BOSS = 0x069E80;     // Pal_AIZEndBoss (line 1)
    private static final int PAL_CONTINUE = 0x05CBCA;         // Pal_ContinueScreen (Egg Robo line 1)
    private static final int PAL_HCZ = 0x0A8D9C;              // Pal_HCZ1
    private static final int PAL_HCZ_END_BOSS = 0x06BF0A;     // Pal_HCZEndBoss
    private static final int PAL_LBZ = 0x0A929C;              // Pal_LBZ1
    private static final int PAL_LBZ_FINAL_BOSS1 = 0x073886;  // Pal_LBZFinalBoss1
    private static final int PAL_LBZ_FINAL_BOSS2 = 0x0751AA;  // Pal_LBZFinalBoss2
    private static final int PAL_SSZ = 0x0A973C;              // Pal_SSZ1
    private static final int PAL_MECHA = 0x07D850;            // Pal_SSZGHZMisc (Mecha Sonic line 1)
    private static final int PAL_INTRO_EMERALDS = 0x067AAA;   // Pal_AIZIntroEmeralds (line 3)
    private static final int PAL_SSTAGE = 0x00896E;           // Pal_SStage_Main (all four lines)
    private static final int PALCYCLE_SUPER_SONIC = 0x00398E; // PalCycle_SuperSonic

    private final SceneArt art;
    private final Map<String, SceneRomArt> roms = new HashMap<>();
    private final Map<String, SceneLevelKit> kits = new HashMap<>();
    private final Map<String, SceneSpriteSet> sets = new HashMap<>();
    private final Map<String, int[]> palettes = new HashMap<>();

    public Art(SceneArt art) {
        this.art = art;
    }

    /** The supplied ROM for "s1", "s2" or "s3k", or null when that game is not installed. */
    public SceneRomArt rom(String game) {
        if (roms.containsKey(game)) return roms.get(game);
        SceneRomArt rom = null;
        try {
            if (art != null && art.availableGames().contains(game)) rom = art.rom(game);
            if (rom == null && art != null && art.rom() != null && game.equals(art.rom().gameId())) rom = art.rom();
        } catch (RuntimeException unavailable) {
            rom = null;
        }
        roms.put(game, rom);
        return rom;
    }

    public boolean has(String game) {
        return rom(game) != null;
    }

    /** The level kit for a zone, or null when the ROM or the kit is unavailable. */
    public SceneLevelKit kit(Zone zone) {
        return kit(zone.game, zone.zone, zone.act);
    }

    public SceneLevelKit kit(String game, int zone, int act) {
        String key = game + ":" + zone + ":" + act;
        if (kits.containsKey(key)) return kits.get(key);
        SceneRomArt rom = rom(game);
        SceneLevelKit kit = rom != null && rom.hasLevelKit(zone, act) ? rom.levelKit(zone, act) : null;
        kits.put(key, kit);
        return kit;
    }

    /** A hero's sprites from {@code game}'s player art, with its animation scripts. */
    public SceneSpriteSet hero(String game, HeroId hero) {
        return cached(game + ":hero:" + hero.code(), () -> {
            SceneRomArt rom = rom(game);
            return rom == null ? null : rom.character(hero.code());
        });
    }

    /** Tails' separate tails object (Sonic 3 &amp; Knuckles only; Sonic 2 draws them from Tails' own frames). */
    public SceneSpriteSet tailsAccessory(String game) {
        return cached(game + ":tails-tails", () -> {
            SceneRomArt rom = rom(game);
            return rom == null ? null : rom.characterAccessory("tails");
        });
    }

    /** Super Sonic's body frames (Map_SuperSonic) in palette-cycle colour {@code entry} 6..8. */
    public SceneSpriteSet superSonic(int entry) {
        return cached("s3k:super:" + entry, () -> {
            SceneRomArt rom = rom("s3k");
            if (rom == null) return null;
            int[] palette = s3k(rom, PAL_AIZ);
            // PalCycle_SuperSonic: three colours per step written to Normal_palette+4.
            System.arraycopy(rom.palette(PALCYCLE_SUPER_SONIC + entry * 6, 3), 0, palette, 2, 3);
            return rom.sprites(RomSpriteRequest.streamed(
                    0x100000, 0x40060,                  // ArtUnc_Sonic
                    0x146816, 0x148378,                 // Map_SuperSonic, PLC_SuperSonic
                    DplcLayout.PLAYER, 0), palette);
        });
    }

    /**
     * Sprite set for an art key: {@code s1:name}, {@code s2:name} or {@code s3k:name} for a
     * badnik, boss part or prop. Returns null when the game is missing.
     */
    public SceneSpriteSet sprites(String key) {
        return cached(key, () -> load(key));
    }

    private SceneSpriteSet load(String key) {
        int colon = key.indexOf(':');
        String game = key.substring(0, colon);
        String name = key.substring(colon + 1);
        SceneRomArt rom = rom(game.equals("boss") ? "s3k" : game);
        if (rom == null) return null;
        return switch (game) {
            case "s1" -> sonic1(rom, name);
            case "s2" -> sonic2(rom, name);
            default -> sonic3k(rom, name);
        };
    }

    // Sonic 1 (s1disasm labels). Zone ids: 0 GHZ, 1 MZ, 2 SYZ, 3 LZ, 4 SLZ, 5 SBZ.
    private SceneSpriteSet sonic1(SceneRomArt rom, String name) {
        return switch (name) {
            case "motobug" -> s1(rom, 0x37A2C, 0xFE2C, 0, 0);          // Nem_Motobug, Map_Moto
            case "crabmeat" -> s1(rom, 0x35EB0, 0x9DCE, 0, 0);         // Nem_Crabmeat, Map_Crab
            case "buzzbomber" -> s1(rom, 0x3639E, 0xA0B4, 0, 0);       // Nem_Buzz, Map_Buzz
            case "chopper" -> s1(rom, 0x37016, 0xB254, 0, 0);          // Nem_Chopper, Map_Chop
            case "newtron" -> s1(rom, 0x37CB6, 0xE5D0, 1, 0);          // Nem_Newtron, Map_Newt
            case "caterkiller" -> s1(rom, 0x39076, 0x1751A, 1, 1);     // Nem_Cat, Map_Cat
            case "basaran" -> s1(rom, 0x386BC, 0x108CA, 0, 1);         // Nem_Basaran, Map_Bas
            case "yadrin" -> s1(rom, 0x382D4, 0xFFBA, 1, 2);           // Nem_Yadrin, Map_Yad
            case "roller" -> s1(rom, 0x37508, 0xE830, 0, 2);           // Nem_Roller, Map_Roll
            case "bomb" -> s1(rom, 0x38C00, 0x122FC, 0, 4);            // Nem_Bomb, Map_Bomb
            case "orbinaut" -> s1(rom, 0x38E98, 0x125B8, 0, 4);        // Nem_Orbinaut, Map_Orb
            // Robotnik on foot (frame 0), as Sitar Hero's Sonic 1 performer uses it, on the player line.
            case "eggman" -> s1(rom, 0x5E4CE, 0x01A1E4, 0, 2);
            // Animals for village folk (Anml_Variables mappings per species).
            case "flicky" -> s1(rom, 0x3BF06, 0x9AFC, 0, 0);           // Nem_Flicky
            case "pocky" -> s1(rom, 0x3B884, 0x9AE4, 0, 0);            // Nem_Rabbit
            case "rocky" -> s1(rom, 0x3BCB4, 0x9AFC, 0, 0);            // Nem_Seal
            case "ricky" -> s1(rom, 0x3C040, 0x9B14, 0, 0);            // Nem_Squirrel
            case "picky" -> s1(rom, 0x3BDD0, 0x9B14, 0, 0);            // Nem_Pig
            default -> throw new IllegalArgumentException("Unknown Sonic 1 art " + name);
        };
    }

    private SceneSpriteSet s1(SceneRomArt rom, int art, int map, int line, int homeZone) {
        return rom.sprites(RomSpriteRequest.of(art, Compression.NEMESIS, map, line), kitPalette("s1", homeZone));
    }

    // Sonic 2 (s2disasm labels). Zone ids: 0 EHZ, 1 CPZ, 2 ARZ, 3 CNZ, 5 MCZ, 6 OOZ, 7 MTZ.
    private SceneSpriteSet sonic2(SceneRomArt rom, String name) {
        return switch (name) {
            case "buzzer" -> s2(rom, 0x8316A, 0x2D2EA, 0, 0);          // ArtNem_Buzzer
            case "masher" -> s2(rom, 0x839EA, 0x2D442, 0, 0);          // ArtNem_Masher
            case "coconuts" -> s2(rom, 0x8A87A, 0x37D96, 0, 0);        // ArtNem_Coconuts
            case "spiny" -> s2(rom, 0x8B430, 0x38CCA, 1, 1);           // ArtNem_Spiny
            case "grabber" -> s2(rom, 0x8B6B4, 0x3921A, 1, 1);         // ArtNem_Grabber
            case "crawlton" -> s2(rom, 0x8AB36, 0x37FF2, 1, 5);        // ArtNem_Crawlton
            case "flasher" -> s2(rom, 0x8AC5E, 0x388F0, 0, 5);         // ArtNem_Flasher
            case "flicky" -> s2(rom, 0x7EF60, 0x11E1C, 0, 0);          // ArtNem_Flicky
            case "pocky" -> s2(rom, 0x7FDD2, 0x11EAC, 0, 0);           // ArtNem_Rabbit
            case "tocky" -> s2(rom, 0x7FADE, 0x11E40, 0, 0);           // ArtNem_Turtle
            // Silver Sonic (ArtNem_SilverSonic, Obj_AF mappings) on the Death Egg palette.
            case "silver" -> rom.sprites(RomSpriteRequest.of(0x8BE12, Compression.NEMESIS, 0x39E68, 1),
                    s2PalettePointer(rom, 0x12));
            default -> throw new IllegalArgumentException("Unknown Sonic 2 art " + name);
        };
    }

    private SceneSpriteSet s2(SceneRomArt rom, int art, int map, int line, int homeZone) {
        return rom.sprites(RomSpriteRequest.of(art, Compression.NEMESIS, map, line), kitPalette("s2", homeZone));
    }

    /**
     * Sonic 2's PalPointers entry {@code id} (8 bytes each at $2782: address, RAM, size) for
     * lines 1-3, under Pal_SonicTails ($29E2) on line 0; Sitar Hero loads Silver Sonic this way.
     */
    private int[] s2PalettePointer(SceneRomArt rom, int id) {
        byte[] pointer = rom.read(0x2782 + id * 8, 4);
        int address = (pointer[1] & 255) << 16 | (pointer[2] & 255) << 8 | pointer[3] & 255;
        int[] palette = new int[64];
        System.arraycopy(rom.palette(0x29E2, 16), 0, palette, 0, 16);
        System.arraycopy(rom.palette(address, 48), 0, palette, 16, 48);
        return palette;
    }

    private SceneSpriteSet sonic3k(SceneRomArt rom, String name) {
        return switch (name) {
            // Angel Island badniks (line 1).
            case "rhinobot" -> rom.sprites(RomSpriteRequest.streamed(0x36732A, 0xAA0, // ArtUnc_AIZRhinobot
                    0x3615A8, 0x36156E, DplcLayout.OBJECT, 1), s3k(rom, PAL_AIZ)); // Map/DPLC_Rhinobot
            case "bloominator" -> kosm(rom, 0x367DCA, 0x3616C0, 1, s3k(rom, PAL_AIZ));   // ArtKosM_AIZ_Bloominator
            case "monkeydude" -> kosm(rom, 0x36800C, 0x361776, 1, s3k(rom, PAL_AIZ));    // ArtKosM_AIZ_MonkeyDude
            // Hydrocity badniks.
            case "jawz" -> kosm(rom, 0x36A552, 0x361364, 1, s3k(rom, PAL_HCZ));          // ArtKosM_Jawz
            case "blastoid" -> kosm(rom, 0x36A7C6, 0x360DD0, 1, s3k(rom, PAL_HCZ));      // ArtKosM_Blastoid
            case "buggernaut" -> rom.sprites(RomSpriteRequest.of(0x36A3E0, Compression.NEMESIS, // ArtNem_HCZDragonfly
                    0x360EB4, 1), s3k(rom, PAL_HCZ));                                       // Map_Buggernaut
            case "turbospiker" -> kosm(rom, 0x36A968, 0x361212, 1, s3k(rom, PAL_HCZ));   // ArtKosM_TurboSpiker
            case "megachopper" -> kosm(rom, 0x36A6C4, 0x360F26, 1, s3k(rom, PAL_HCZ));   // ArtKosM_MegaChopper
            case "pointdexter" -> kosm(rom, 0x36AD8A, 0x360E72, 1, s3k(rom, PAL_HCZ));   // ArtKosM_Pointdexter
            // Launch Base badniks.
            case "snaleblaster" -> kosm(rom, 0x377996, 0x360400, 1, s3k(rom, PAL_LBZ));  // ArtKosM_SnaleBlaster
            case "orbinaut" -> kosm(rom, 0x377D1A, 0x3604A4, 1, s3k(rom, PAL_LBZ));      // ArtKosM_Orbinaut
            case "ribot" -> kosm(rom, 0x377BE8, 0x3604B8, 1, s3k(rom, PAL_LBZ));         // ArtKosM_Ribot
            case "flybot" -> rom.sprites(RomSpriteRequest.streamed(0x377EBE, 0x1320,     // ArtUnc_Flybot767
                    0x36065A, 0x3607EC, DplcLayout.OBJECT, 1), s3k(rom, PAL_LBZ));
            // Bosses and their parts.
            case "ship_aiz" -> rom.sprites(ship(), s3k(rom, PAL_AIZ_FIRE));
            case "ship_hcz" -> rom.sprites(ship(), s3k(rom, PAL_HCZ));
            case "ship_lbz" -> rom.sprites(ship(), s3k(rom, PAL_LBZ));
            case "ship" -> rom.sprites(ship(), s3k(rom, PAL_AIZ));
            case "aiz_end_boss" -> kosm(rom, 0x365260, 0x361FD6, 1,                     // ArtKosM_AIZEndBoss
                    boss(rom, PAL_AIZ_FIRE, PAL_AIZ_END_BOSS));                            // Map_AIZEndBoss
            case "hcz_end_boss" -> rom.sprites(RomSpriteRequest.of(0x36929E, Compression.NEMESIS, // ArtNem_HCZEndBoss
                    0x3634D4, 1), boss(rom, PAL_HCZ, PAL_HCZ_END_BOSS));                   // Map_HCZEndBoss
            case "lbz_boss1" -> rom.sprites(RomSpriteRequest.of(0x37599C, Compression.NEMESIS, // ArtNem_LBZFinalBoss1
                    0x3645A8, 1), boss(rom, PAL_LBZ, PAL_LBZ_FINAL_BOSS1));                // Map_LBZFinalBoss1
            case "lbz_boss1_l0" -> rom.sprites(RomSpriteRequest.of(0x37599C, Compression.NEMESIS,
                    0x3645A8, 0), boss(rom, PAL_LBZ, PAL_LBZ_FINAL_BOSS1));                // thruster flames, line 0
            case "lbz_boss2" -> kosm(rom, 0x376874, 0x364A96, 1,                        // ArtKosM_LBZFinalBoss2
                    boss(rom, PAL_LBZ, PAL_LBZ_FINAL_BOSS2));                              // Map_LBZFinalBoss2
            case "lbz_boss2_l0" -> kosm(rom, 0x376874, 0x364A96, 0, boss(rom, PAL_LBZ, PAL_LBZ_FINAL_BOSS2));
            case "egg_robo" -> kosm(rom, 0x17B17E, 0x184F34, 0,                          // ArtKosM_EggRoboBadnik
                    rom.palette(PAL_CONTINUE, 64));                                         // Map_EggRobo
            case "mecha" -> rom.sprites(RomSpriteRequest.streamed(0x175A9E, 0x56E0,      // ArtUnc_MechaSonic
                    0x1853AA, 0x185852, DplcLayout.OBJECT, 1), boss(rom, PAL_SSZ, PAL_MECHA)); // Map/DPLC_MechaSonic
            // Props.
            case "monitor" -> rom.sprites(RomSpriteRequest.of(0x190F4A, Compression.NEMESIS, // ArtNem_Monitors
                    0x01DBA2, 0), s3k(rom, PAL_AIZ));          // Map_Monitor (box 0, icons 1-10, broken 11)
            case "ring" -> rom.sprites(RomSpriteRequest.of(0x192AEE, Compression.NEMESIS, // ArtNem_RingHUDText
                    0x01A99A, 1), s3k(rom, PAL_AIZ));          // Map_Ring (spin 0-3, sparkle 4-7)
            case "explosion" -> rom.sprites(RomSpriteRequest.of(0x19200A, Compression.NEMESIS, // ArtNem_Explosion
                    0x01E758, 0), s3k(rom, PAL_AIZ));          // Map_Explosion
            case "starpost" -> rom.sprites(RomSpriteRequest.of(0x192D2A, Compression.NEMESIS, // ArtNem_EnemyPtsStarPost
                    0x02D348, 0).withTileOffset(-8), s3k(rom, PAL_AIZ)); // Map_StarPost (art_tile ArtTile_StarPost+8)
            case "emeralds" -> kosm(rom, 0x387CA6, 0x364562, 3, emeraldPalette(rom)); // ArtKosM_AIZIntroEmeralds
            case "tornado" -> kosm(rom, 0x382624, 0x364470, 0, s3k(rom, PAL_AIZ));    // ArtKosM_AIZIntroPlane
            case "big_ring" -> rom.sprites(RomSpriteRequest.streamed(0x0D8766, 0x2700, // ArtUnc_SSEntryRing
                    0x0619E0, 0x061ABE, DplcLayout.OBJECT, 0), s3k(rom, PAL_AIZ)); // Map/DPLC_SSEntryRing
            case "sphere" -> rom.sprites(RomSpriteRequest.of(0x0AD904, Compression.NEMESIS, // ArtNem_SStageSphere
                    0x00A464, 2), rom.palette(PAL_SSTAGE, 64)); // Map_SStageSphere, blue spheres on line 2
            case "flicky" -> rom.sprites(RomSpriteRequest.of(0x1931D6, Compression.NEMESIS, // ArtNem_BlueFlicky
                    0x02CEBA, 0), s3k(rom, PAL_AIZ));          // Map_Animals1
            case "pocky" -> rom.sprites(RomSpriteRequest.of(0x193706, Compression.NEMESIS, // ArtNem_Rabbit
                    0x02CF32, 0), s3k(rom, PAL_AIZ));          // Map_Animals5
            case "ricky" -> rom.sprites(RomSpriteRequest.of(0x1935A8, Compression.NEMESIS, // ArtNem_Squirrel
                    0x02CED8, 0), s3k(rom, PAL_AIZ));          // Map_Animals2
            default -> throw new IllegalArgumentException("Unknown S3K art " + name);
        };
    }

    /** Map_RobotnikShip over ArtNem_RobotnikShip: head 0-3, Egg Mobile 5, cockpits 8 and $C. */
    private static RomSpriteRequest ship() {
        return RomSpriteRequest.of(0x0D771E, Compression.NEMESIS, 0x06820C, 0);
    }

    private static SceneSpriteSet kosm(SceneRomArt rom, int art, int map, int line, int[] palette) {
        return rom.sprites(RomSpriteRequest.of(art, Compression.KOSINSKI_MODULED, map, line), palette);
    }

    /** Pal_SonicTails on line 0 with a 48-colour zone palette on lines 1-3. */
    private int[] s3k(SceneRomArt rom, int zonePalette) {
        return palettes.computeIfAbsent("s3k:" + zonePalette, key -> {
            int[] palette = new int[64];
            System.arraycopy(rom.palette(PAL_SONIC_TAILS, 16), 0, palette, 0, 16);
            System.arraycopy(rom.palette(zonePalette, 48), 0, palette, 16, 48);
            return palette;
        }).clone();
    }

    /** A zone palette with the boss's own 16 colours over line 1, as the boss's palette load does. */
    private int[] boss(SceneRomArt rom, int zonePalette, int bossLine) {
        int[] palette = s3k(rom, zonePalette);
        System.arraycopy(rom.palette(bossLine, 16), 0, palette, 16, 16);
        return palette;
    }

    private int[] emeraldPalette(SceneRomArt rom) {
        int[] palette = s3k(rom, PAL_AIZ);
        System.arraycopy(rom.palette(PAL_INTRO_EMERALDS, 16), 0, palette, 48, 16);
        return palette;
    }

    /** The palette an act loads with, from its level kit (line 0 is the player palette). */
    public int[] kitPalette(String game, int zone) {
        return palettes.computeIfAbsent(game + ":kit:" + zone, key -> {
            SceneLevelKit kit = kit(game, zone, 0);
            return kit == null ? new int[64] : kit.palette();
        }).clone();
    }

    private interface Loader {
        SceneSpriteSet load();
    }

    private SceneSpriteSet cached(String key, Loader loader) {
        if (sets.containsKey(key)) return sets.get(key);
        SceneSpriteSet set;
        try {
            set = loader.load();
        } catch (RuntimeException failure) {
            set = null;
        }
        sets.put(key, set);
        return set;
    }
}
