package slaytherobotnik.art;

import com.openggf.mods.scene.RomSpriteRequest;
import com.openggf.mods.scene.RomSpriteRequest.Compression;
import com.openggf.mods.scene.RomSpriteRequest.DplcLayout;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSpriteSet;
import com.openggf.mods.scene.art.*;

/**
 * Where the game's sprites live in the Sonic 3 &amp; Knuckles ROM. Every address is a label in
 * the S3K disassembly ({@code sonic3k.lst}), noted beside it, so a modder can find any other
 * sprite the same way: look up its {@code ArtKosM_/ArtNem_/ArtUnc_} art, its {@code Map_}
 * mappings, the {@code make_art_tile} palette line, and the palette it is drawn with.
 */
final class RomSprites {
    // Palettes.
    private static final int PAL_SONIC_TAILS = 0x0A8A3C;      // Pal_SonicTails (line 0)
    private static final int PAL_AIZ = 0x0A8B7C;              // Pal_AIZ (lines 1-3, Angel Island act 1)
    private static final int PAL_AIZ_FIRE = 0x0A8BDC;         // Pal_AIZFire (lines 1-3, burning act 2)
    private static final int PAL_AIZ_MINIBOSS = 0x06917C;     // Pal_AIZMiniboss (line 1)
    private static final int PAL_AIZ_END_BOSS = 0x069E80;     // Pal_AIZEndBoss (line 1)
    private static final int PAL_AIZ_INTRO_EMERALDS = 0x067AAA; // Pal_AIZIntroEmeralds (line 3)
    private static final int PAL_CONTINUE = 0x05CBCA;         // Pal_ContinueScreen (all 4 lines; Egg Robo on line 1)
    private static final int PAL_HCZ = 0x0A8D9C;              // Pal_HCZ1 (lines 1-3, Hydrocity act 1)
    private static final int PAL_HCZ_MINIBOSS = 0x06AE56;     // Pal_HCZMiniboss (line 1)
    private static final int PAL_HCZ_END_BOSS = 0x06BF0A;     // Pal_HCZEndBoss (line 1)
    private static final int PAL_LBZ = 0x0A929C;              // Pal_LBZ1 (lines 1-3, Launch Base act 1)
    private static final int PAL_LBZ_FINAL_BOSS1 = 0x073886;  // Pal_LBZFinalBoss1 (line 1)
    private static final int PAL_LBZ_FINAL_BOSS2 = 0x0751AA;  // Pal_LBZFinalBoss2 (line 1)
    private static final int PAL_SSZ = 0x0A973C;              // Pal_SSZ1 (lines 1-3, Sky Sanctuary)
    private static final int PAL_MECHA = 0x07D850;            // Pal_SSZGHZMisc (Mecha Sonic, line 1)
    private static final int PAL_MECHA_SUPER1 = 0x07DADC;     // Super Mecha Sonic cycle (word_7DA60 .headr2)
    private static final int PAL_MECHA_SUPER2 = 0x07DAFE;
    private static final int PAL_MECHA_SUPER3 = 0x07DB20;
    private static final int PAL_KNUCKLES = 0x0A8AFC;          // Pal_Knuckles (line 0 when playing as Knuckles)
    private static final int PAL_MHZ = 0x0A943C;              // Pal_MHZ1 (lines 1-3, Mushroom Hill act 1)
    private static final int PAL_SSTAGE = 0x00896E;           // Pal_SStage_Main (all 4 lines)

    private RomSprites() {
    }

    static SceneSpriteSet load(SceneRomArt rom, String key) {
        return load(rom, null, key);
    }

    static SceneSpriteSet load(SceneRomArt rom, SceneArtCache cache, String key) {
        return switch (key) {
            // ---- Angel Island badniks (art_tile palette line 1) ----
            case "rhinobot" -> rom.sprites(RomSpriteRequest.streamed(
                    0x36732A, 0xAA0,                    // ArtUnc_AIZRhinobot
                    0x3615A8, 0x36156E,                 // Map_Rhinobot, DPLC_Rhinobot
                    DplcLayout.OBJECT, 1), aiz(rom));
            case "bloominator" -> rom.sprites(RomSpriteRequest.of(
                    0x367DCA, Compression.KOSINSKI_MODULED, // ArtKosM_AIZ_Bloominator
                    0x3616C0, 1), aiz(rom));             // Map_Bloominator
            case "monkey_dude" -> rom.sprites(RomSpriteRequest.of(
                    0x36800C, Compression.KOSINSKI_MODULED, // ArtKosM_AIZ_MonkeyDude
                    0x361776, 1), aiz(rom));             // Map_MonkeyDude
            case "caterkiller_jr" -> rom.sprites(RomSpriteRequest.of(
                    0x3681FE, Compression.KOSINSKI_MODULED, // ArtKosM_AIZ_CaterkillerJr
                    0x361A18, 1), aiz(rom));             // Map_CaterKillerJr

            // ---- Angel Island bosses ----
            case "aiz_miniboss" -> rom.sprites(RomSpriteRequest.of(
                    0x364BF2, Compression.NEMESIS,       // ArtNem_AIZMiniboss
                    0x3624D0, 1), bossPalette(rom, PAL_AIZ_MINIBOSS)); // Map_AIZMiniboss
            case "aiz_miniboss_flame" -> rom.sprites(RomSpriteRequest.of(
                    0x37FB50, Compression.NEMESIS,       // ArtNem_AIZBossFire
                    0x36165C, 0), bossPalette(rom, PAL_AIZ_MINIBOSS)); // Map_AIZMinibossFlame
            case "aiz_end_boss" -> rom.sprites(RomSpriteRequest.of(
                    0x365260, Compression.KOSINSKI_MODULED, // ArtKosM_AIZEndBoss
                    0x361FD6, 1), bossPalette(rom, PAL_AIZ_END_BOSS)); // Map_AIZEndBoss
            case "robotnik_ship" -> named(rom, cache, StockSceneArt.S3K_ROBOTNIK_SHIP, aiz(rom));             // Map_RobotnikShip (head 0-3, Egg Mobile 5)
            case "boss_explosion" -> rom.sprites(RomSpriteRequest.of(
                    0x0D73CE, Compression.NEMESIS,       // ArtNem_BossExplosion
                    0x083FFC, 0), aiz(rom));             // Map_BossExplosion

            // ---- Hydrocity badniks ----
            case "jawz" -> rom.sprites(RomSpriteRequest.of(
                    0x36A552, Compression.KOSINSKI_MODULED, // ArtKosM_Jawz
                    0x361364, 1), zone(rom, PAL_HCZ));   // Map_Jawz
            case "blastoid" -> rom.sprites(RomSpriteRequest.of(
                    0x36A7C6, Compression.KOSINSKI_MODULED, // ArtKosM_Blastoid
                    0x360DD0, 1), zone(rom, PAL_HCZ));   // Map_Blastoid
            case "buggernaut" -> rom.sprites(RomSpriteRequest.of(
                    0x36A3E0, Compression.NEMESIS,       // ArtNem_HCZDragonfly
                    0x360EB4, 1), zone(rom, PAL_HCZ));   // Map_Buggernaut
            case "turbo_spiker" -> rom.sprites(RomSpriteRequest.of(
                    0x36A968, Compression.KOSINSKI_MODULED, // ArtKosM_TurboSpiker
                    0x361212, 1), zone(rom, PAL_HCZ));   // Map_TurboSpiker
            case "mega_chopper" -> rom.sprites(RomSpriteRequest.of(
                    0x36A6C4, Compression.KOSINSKI_MODULED, // ArtKosM_MegaChopper
                    0x360F26, 1), zone(rom, PAL_HCZ));   // Map_MegaChopper
            case "pointdexter" -> rom.sprites(RomSpriteRequest.of(
                    0x36AD8A, Compression.KOSINSKI_MODULED, // ArtKosM_Pointdexter
                    0x360E72, 1), zone(rom, PAL_HCZ));   // Map_Poindexter

            // ---- Hydrocity bosses ----
            case "hcz_miniboss" -> rom.sprites(RomSpriteRequest.of(
                    0x368400, Compression.NEMESIS,       // ArtNem_HCZMiniboss
                    0x3629E0, 1), zoneBoss(rom, PAL_HCZ, PAL_HCZ_MINIBOSS)); // Map_HCZMiniboss
            case "hcz_miniboss_l0" -> rom.sprites(RomSpriteRequest.of(
                    0x368400, Compression.NEMESIS,       // the thruster and rocket flames use line 0
                    0x3629E0, 0), zoneBoss(rom, PAL_HCZ, PAL_HCZ_MINIBOSS));
            case "hcz_end_boss" -> rom.sprites(RomSpriteRequest.of(
                    0x36929E, Compression.NEMESIS,       // ArtNem_HCZEndBoss
                    0x3634D4, 1), zoneBoss(rom, PAL_HCZ, PAL_HCZ_END_BOSS)); // Map_HCZEndBoss

            // ---- Launch Base badniks ----
            case "snale_blaster" -> rom.sprites(RomSpriteRequest.of(
                    0x377996, Compression.KOSINSKI_MODULED, // ArtKosM_SnaleBlaster
                    0x360400, 1), zone(rom, PAL_LBZ));   // Map_SnaleBlaster
            case "orbinaut" -> rom.sprites(RomSpriteRequest.of(
                    0x377D1A, Compression.KOSINSKI_MODULED, // ArtKosM_Orbinaut
                    0x3604A4, 1), zone(rom, PAL_LBZ));   // Map_Orbinaut
            case "ribot" -> rom.sprites(RomSpriteRequest.of(
                    0x377BE8, Compression.KOSINSKI_MODULED, // ArtKosM_Ribot
                    0x3604B8, 1), zone(rom, PAL_LBZ));   // Map_Ribot
            case "corkey" -> rom.sprites(RomSpriteRequest.of(
                    0x377DFC, Compression.KOSINSKI_MODULED, // ArtKosM_Corkey
                    0x3605C2, 1), zone(rom, PAL_LBZ));   // Map_Corkey
            case "flybot" -> rom.sprites(RomSpriteRequest.streamed(
                    0x377EBE, 0x1320,                   // ArtUnc_Flybot767
                    0x36065A, 0x3607EC,                 // Map_Flybot767, DPLC_Flybot767
                    DplcLayout.OBJECT, 1), zone(rom, PAL_LBZ));

            // ---- Launch Base bosses ----
            case "lbz_final_boss1" -> rom.sprites(RomSpriteRequest.of(
                    0x37599C, Compression.NEMESIS,       // ArtNem_LBZFinalBoss1
                    0x3645A8, 1), zoneBoss(rom, PAL_LBZ, PAL_LBZ_FINAL_BOSS1)); // Map_LBZFinalBoss1
            case "lbz_final_boss1_l0" -> rom.sprites(RomSpriteRequest.of(
                    0x37599C, Compression.NEMESIS,       // the thruster flames use line 0
                    0x3645A8, 0), zoneBoss(rom, PAL_LBZ, PAL_LBZ_FINAL_BOSS1));
            case "lbz_final_boss2" -> rom.sprites(RomSpriteRequest.of(
                    0x376874, Compression.KOSINSKI_MODULED, // ArtKosM_LBZFinalBoss2
                    0x364A96, 1), zoneBoss(rom, PAL_LBZ, PAL_LBZ_FINAL_BOSS2)); // Map_LBZFinalBoss2
            case "lbz_final_boss2_l0" -> rom.sprites(RomSpriteRequest.of(
                    0x376874, Compression.KOSINSKI_MODULED, // the rear hazard sprite uses line 0
                    0x364A96, 0), zoneBoss(rom, PAL_LBZ, PAL_LBZ_FINAL_BOSS2));
            case "robotnik_ship_lbz" -> named(rom, cache, StockSceneArt.S3K_ROBOTNIK_SHIP, zone(rom, PAL_LBZ));
            case "robotnik_ship_hcz" -> named(rom, cache, StockSceneArt.S3K_ROBOTNIK_SHIP, zone(rom, PAL_HCZ));

            // ---- Sky Sanctuary ----
            case "mecha_sonic" -> mecha(rom, PAL_MECHA);
            case "mecha_super1" -> mecha(rom, PAL_MECHA_SUPER1);
            case "mecha_super2" -> mecha(rom, PAL_MECHA_SUPER2);
            case "mecha_super3" -> mecha(rom, PAL_MECHA_SUPER3);
            case "egg_robo_ssz" -> named(rom, cache, StockSceneArt.S3K_EGG_ROBO, zone(rom, PAL_SSZ));   // Map_EggRobo

            // ---- Characters and props ----
            case "egg_robo" -> named(rom, cache, StockSceneArt.S3K_EGG_ROBO, rom.palette(PAL_CONTINUE, 64)); // Map_EggRobo
            case "tornado" -> rom.sprites(RomSpriteRequest.of(
                    0x382624, Compression.KOSINSKI_MODULED, // ArtKosM_AIZIntroPlane
                    0x364470, 0), aiz(rom));             // Map_AIZIntroPlane (body 0, propeller 1-4, flame 5-6)
            case "monitor" -> rom.sprites(RomSpriteRequest.of(
                    0x190F4A, Compression.NEMESIS,       // ArtNem_Monitors
                    0x01DBA2, 0), aiz(rom));             // Map_Monitor (box 0, icons 1-10, broken 11)
            case "ring" -> named(rom, cache, StockSceneArt.S3K_RING, aiz(rom));             // Map_Ring (spin 0-3, sparkle 4-7)
            case "explosion" -> named(rom, cache, StockSceneArt.S3K_EXPLOSION, aiz(rom));             // Map_Explosion
            case "flicky" -> named(rom, cache, StockSceneArt.S3K_BLUE_FLICKY, aiz(rom));             // Map_Animals1 (flap 0-1, released 2)
            case "game_over" -> rom.sprites(RomSpriteRequest.of(
                    0x191DE4, Compression.NEMESIS,       // ArtNem_GameOver
                    0x02EDD0, 0), aiz(rom));             // Map_GameOver (GAME 0, OVER 1, TIME 2, OVER 3)
            case "starpost" -> rom.sprites(RomSpriteRequest.of(
                    0x192D2A, Compression.NEMESIS,       // ArtNem_EnemyPtsStarPost
                    0x02D348, 0).withTileOffset(-8), aiz(rom)); // Map_StarPost (art_tile is ArtTile_StarPost+8)
            case "starpost_stars" -> rom.sprites(RomSpriteRequest.of(
                    0x192D2A, Compression.NEMESIS,
                    0x02D3AA, 0).withTileOffset(-8), aiz(rom)); // Map_StarpostStars
            case "intro_emeralds" -> rom.sprites(RomSpriteRequest.of(
                    0x387CA6, Compression.KOSINSKI_MODULED, // ArtKosM_AIZIntroEmeralds
                    0x364562, 3), lines(rom, PAL_AIZ_INTRO_EMERALDS, 3, 16)); // Map_AIZIntroEmeralds
            case "big_ring" -> rom.sprites(RomSpriteRequest.streamed(
                    0x0D8766, 0x2700,                   // ArtUnc_SSEntryRing
                    0x0619E0, 0x061ABE,                 // Map_SSEntryRing, DPLC_SSEntryRing
                    DplcLayout.OBJECT, 1), aiz(rom));
            case "egg_capsule" -> named(rom, cache, StockSceneArt.S3K_EGG_CAPSULE, aiz(rom));             // Map_EggCapsule
            case "spring" -> rom.sprites(RomSpriteRequest.of(
                    0x1927FE, Compression.NEMESIS,       // ArtNem_SpikesSprings
                    0x02375C, 0).withTileOffset(-0x10), aiz(rom)); // Map_Spring (vertical red)
            case "spikes" -> rom.sprites(RomSpriteRequest.of(
                    0x1927FE, Compression.NEMESIS,
                    0x024456, 0).withTileOffset(-8), aiz(rom)); // Map_Spikes (upright frames 0-3)
            case "shield_fire" -> rom.sprites(RomSpriteRequest.streamed(
                    0x18C704, 0x21A0, 0x019AC6, 0x019CE6, DplcLayout.PLAYER, 0), aiz(rom)); // ArtUnc_FireShield
            case "shield_lightning" -> rom.sprites(RomSpriteRequest.streamed(
                    0x18E8A4, 0x1040, 0x019DC8, 0x019EFA, DplcLayout.PLAYER, 0), aiz(rom)); // ArtUnc_LightningShield
            case "shield_bubble" -> rom.sprites(RomSpriteRequest.streamed(
                    0x18F984, 0x1140, 0x019F82, 0x01A076, DplcLayout.PLAYER, 0), aiz(rom)); // ArtUnc_BubbleShield
            case "shield_insta" -> rom.sprites(RomSpriteRequest.streamed(
                    0x18C084, 0x680, 0x01A0D0, 0x01A154, DplcLayout.PLAYER, 0), aiz(rom)); // ArtUnc_InstaShield

            // ---- Event props: altar, mural, campfire ----
            case "aiz_rock" -> rom.sprites(RomSpriteRequest.of(
                    0x38DC90, Compression.NEMESIS,       // ArtNem_AIZMisc1 (ArtTile_AIZMisc1, line 1)
                    0x21DCDC, 1), aiz(rom));             // Map_AIZRock (rocks 0-2, debris 3-6)
            case "aiz_bridge_fire" -> rom.sprites(RomSpriteRequest.of(
                    0x38E760, Compression.NEMESIS,       // ArtNem_AIZMisc2 (ArtTile_AIZMisc2, line 2)
                    0x02B092, 2), zone(rom, PAL_AIZ_FIRE)); // Map_AIZDrawBridgeFire (logs 0-2, flames 3-7)
            case "hpz_emerald_0", "hpz_emerald_1", "hpz_emerald_2", "hpz_emerald_3" -> rom.sprites(
                    RomSpriteRequest.of(0x174B28, Compression.NEMESIS, // ArtNem_HPZEmeraldMisc
                            0x091006, key.charAt(key.length() - 1) - '0'), hpz(rom)); // Map_HPZEmeraldMisc, line N
            case "hpz_gray_emerald" -> rom.sprites(RomSpriteRequest.of(
                    0x1757B4, Compression.NEMESIS,       // ArtNem_HPZGrayEmerald (ArtTile_HPZGrayEmerald, line 0)
                    0x091006, 0), hpz(rom));             // Map_HPZEmeraldMisc frame $1E
            case "super_sonic_6", "super_sonic_7", "super_sonic_8" -> rom.sprites(RomSpriteRequest.streamed(
                    0x100000, 0x40060,                  // ArtUnc_Sonic
                    0x146816, 0x148378,                 // Map_SuperSonic, PLC_SuperSonic
                    DplcLayout.PLAYER, 0), superSonic(rom, key.charAt(key.length() - 1) - '0'));
            case "lightning_sparks" -> rom.sprites(new RomSpriteRequest(
                    0x18F8E4, Compression.UNCOMPRESSED, 0xA0, // ArtUnc_LightningShield_Sparks, at ArtTile_Shield_Sparks:
                    0x019DC8, -1, DplcLayout.OBJECT, 0, 0x1F), aiz(rom)); // Map_LightningShield frames $C/$D, $1F tiles on
            case "rabbit" -> rom.sprites(RomSpriteRequest.of(
                    0x193706, Compression.NEMESIS,       // ArtNem_Rabbit (Pocky, Obj_Animal type 0)
                    0x02CF32, 0), aiz(rom));             // Map_Animals5 (falling 0, hopping 1, popped out 2)
            // The 1-Up monitor's face: Map_Monitor frame 2 puts the life icon (ArtTile_PlayerLifeIcon, $310
            // tiles past ArtTile_Monitors) on the screen; drawn alone it is the face over a monitor box.
            case "life_icon_sonic" -> rom.sprites(RomSpriteRequest.of(
                    0x190D34, Compression.NEMESIS,       // ArtNem_SonicLifeIcon (PLC_01)
                    0x01DBA2, 0).withTileOffset(0x310), aiz(rom));
            case "life_icon_tails" -> rom.sprites(RomSpriteRequest.of(
                    0x35CFFE, Compression.NEMESIS,       // ArtNem_TailsLifeIcon (PLC_07)
                    0x01DBA2, 0).withTileOffset(0x310), aiz(rom));
            case "life_icon_knuckles" -> rom.sprites(RomSpriteRequest.of(
                    0x190E4C, Compression.NEMESIS,       // ArtNem_KnucklesLifeIcon (PLC_05), Pal_Knuckles on line 0
                    0x01DBA2, 0).withTileOffset(0x310), lines(rom, PAL_KNUCKLES, 0, 16));

            // ---- Event props: Giant Ring, special stage, monitors, mushrooms ----
            case "ss_entry_flash" -> rom.sprites(RomSpriteRequest.streamed(
                    0x0DAE66, 0x5A0,                    // ArtUnc_SSEntryFlash
                    0x061B28, 0x061BFA,                 // Map_SSEntryFlash, DPLC_SSEntryFlash
                    DplcLayout.OBJECT, 1), aiz(rom));   // ObjSlot_SSEntryFlash: palette line 1
            // The 1-Up monitor's icon (Map_Monitor frame 2) is the player's life icon, which the
            // level PLC loads at ArtTile_PlayerLifeIcon = ArtTile_Monitors + $310; offsetting the
            // mappings by $310 keeps just that piece. Palette line 0 is the player's.
            case "monitor_life_sonic" -> rom.sprites(RomSpriteRequest.of(
                    0x190D34, Compression.NEMESIS,       // ArtNem_SonicLifeIcon (PLC_01)
                    0x01DBA2, 0).withTileOffset(0x310), aiz(rom));
            case "monitor_life_tails" -> rom.sprites(RomSpriteRequest.of(
                    0x35CFFE, Compression.NEMESIS,       // ArtNem_TailsLifeIcon (PLC_07)
                    0x01DBA2, 0).withTileOffset(0x310), aiz(rom));
            case "monitor_life_knuckles" -> rom.sprites(RomSpriteRequest.of(
                    0x190E4C, Compression.NEMESIS,       // ArtNem_KnucklesLifeIcon (PLC_05)
                    0x01DBA2, 0).withTileOffset(0x310), knuckles(rom));
            case "mhz_mushroom_cap" -> rom.sprites(RomSpriteRequest.of(
                    0x14E846, Compression.NEMESIS,       // ArtNem_MHZMisc
                    0x03E1FE, 2).withTileOffset(-0x22), zone(rom, PAL_MHZ)); // Map_MHZMushroomCap, ArtTile_MHZMisc+$22
            case "mhz_mushroom_cap_light" -> rom.sprites(RomSpriteRequest.of(
                    0x14E846, Compression.NEMESIS,
                    0x03E1FE, 2).withTileOffset(-0x52), zone(rom, PAL_MHZ)); // light-spotted: ArtTile_MHZMisc+$52
            case "mhz_pollen" -> rom.sprites(RomSpriteRequest.of(
                    0x14E846, Compression.NEMESIS,
                    0x03DC5C, 3).withTileOffset(-0x21), zone(rom, PAL_MHZ)); // Map_MHZPollen, ArtTile_MHZMisc+$21
            case "aiz_log_splash" -> rom.sprites(RomSpriteRequest.of(
                    0x38E4D8, Compression.NEMESIS,       // ArtNem_AIZFallingLog
                    0x22AEB0, 2), aiz(rom));             // Map_AIZFallingLogSplash (act 1)
            // Obj_HCZWaterSplash DMAs ArtUnc_HCZWaterSplash + frame * $300 over one tile set, so
            // each of its four frames is its own slice of the art under the same mappings.
            case "hcz_water_splash0", "hcz_water_splash1", "hcz_water_splash2", "hcz_water_splash3" ->
                    rom.sprites(new RomSpriteRequest(0x392B14 + (key.charAt(key.length() - 1) - '0') * 0x300,
                            Compression.UNCOMPRESSED, 0x300, // ArtUnc_HCZWaterSplash
                            0x237C60, -1, DplcLayout.OBJECT, 2, 0), zone(rom, PAL_HCZ)); // Map_HCZWaterSplash
            case "ss_sphere" -> rom.sprites(RomSpriteRequest.of(
                    0x0AD904, Compression.NEMESIS,       // ArtNem_SStageSphere
                    0x00A464, 2), rom.palette(PAL_SSTAGE, 64)); // Map_SStageSphere; blue spheres use line 2
            case "ss_sphere_red" -> rom.sprites(RomSpriteRequest.of(
                    0x0AD904, Compression.NEMESIS,
                    0x00A464, 0), rom.palette(PAL_SSTAGE, 64)); // red spheres use line 0 (MapPtr_A10A)
            case "ss_ring" -> rom.sprites(RomSpriteRequest.of(
                    0x0ADF60, Compression.NEMESIS,       // ArtNem_SStageRing
                    0x00A50A, 2), rom.palette(PAL_SSTAGE, 64)); // Map_SStageRing
            default -> null;
        };
    }

    private static SceneSpriteSet named(SceneRomArt rom, SceneArtCache cache, StockSceneArt recipe, int[] palette) {
        return cache == null ? rom.sprites(recipe.request(rom), palette) : cache.sprites(recipe, palette);
    }

    /** Player line 0 plus the Angel Island act 1 zone palette on lines 1-3. */
    private static int[] aiz(SceneRomArt rom) {
        int[] palette = new int[64];
        System.arraycopy(rom.palette(PAL_SONIC_TAILS, 16), 0, palette, 0, 16);
        System.arraycopy(rom.palette(PAL_AIZ, 48), 0, palette, 16, 48);
        return palette;
    }

    /** Knuckles' line 0 (Pal_Knuckles) with the Angel Island act 1 palette on lines 1-3. */
    private static int[] knuckles(SceneRomArt rom) {
        int[] palette = aiz(rom);
        System.arraycopy(rom.palette(PAL_KNUCKLES, 16), 0, palette, 0, 16);
        return palette;
    }

    /** Mecha Sonic: one DPLC sprite (its head is part of the body frames) over a line-1 palette. */
    private static SceneSpriteSet mecha(SceneRomArt rom, int line1) {
        return rom.sprites(RomSpriteRequest.streamed(
                0x175A9E, 0x56E0,                       // ArtUnc_MechaSonic
                0x1853AA, 0x185852,                     // Map_MechaSonic, DPLC_MechaSonic
                DplcLayout.OBJECT, 1), zoneBoss(rom, PAL_SSZ, line1));
    }

    /** Player line 0 plus a zone's 48-colour palette on lines 1-3. */
    private static int[] zone(SceneRomArt rom, int zonePalette) {
        return new PaletteAssembly().rom(rom, PAL_SONIC_TAILS, 0, 16)
                .rom(rom, zonePalette, 16, 48).build();
    }

    /** A zone palette with a boss's own 16 colours loaded over line 1, as the boss's PLC does. */
    private static int[] zoneBoss(SceneRomArt rom, int zonePalette, int bossLine) {
        int[] palette = zone(rom, zonePalette);
        System.arraycopy(rom.palette(bossLine, 16), 0, palette, 16, 16);
        return palette;
    }

    /** A boss palette on line 1 over the burning Angel Island palette (as in act 2). */
    private static int[] bossPalette(SceneRomArt rom, int bossLine) {
        int[] palette = new int[64];
        System.arraycopy(rom.palette(PAL_SONIC_TAILS, 16), 0, palette, 0, 16);
        System.arraycopy(rom.palette(PAL_AIZ_FIRE, 48), 0, palette, 16, 48);
        System.arraycopy(rom.palette(bossLine, 16), 0, palette, 16, 16);
        return palette;
    }

    /** Player line 0 with {@code colors} colours from {@code address} placed on {@code line}. */
    private static int[] lines(SceneRomArt rom, int address, int line, int colors) {
        int[] palette = aiz(rom);
        System.arraycopy(rom.palette(address, colors), 0, palette, line * 16, colors);
        return palette;
    }

    /**
     * Hidden Palace's emerald shrine: player line 0, Pal_HPZ on lines 1-3 (Obj_HPZPaletteControl
     * past x $460), and line 3's colours 1-2 set to the Master Emerald's greens, as
     * Obj_HPZMasterEmerald loc_90700 writes them every frame (the immediate of its move.l at
     * $9070C) until all seven Super Emeralds are won.
     */
    private static int[] hpz(SceneRomArt rom) {
        int[] palette = zone(rom, 0x0669D2);                         // Pal_HPZ
        System.arraycopy(rom.palette(0x09070E, 2), 0, palette, 48 + 1, 2);
        return palette;
    }

    /**
     * Super Sonic's colours: Pal_SonicTails with colours 2-4 from PalCycle_SuperSonic entry
     * {@code entry} (6-8 are the steady cycle, Palette_frame $24-$30, a step every 7 frames;
     * SuperHyper_PalCycle_SuperSonic writes them to Normal_palette+4).
     */
    private static int[] superSonic(SceneRomArt rom, int entry) {
        int[] palette = aiz(rom);
        System.arraycopy(rom.palette(0x00398E + entry * 6, 3), 0, palette, 2, 3); // PalCycle_SuperSonic
        return palette;
    }
}
