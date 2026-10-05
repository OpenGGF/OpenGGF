package slaytherobotnik.art;

import com.openggf.mods.scene.RomSpriteRequest;
import com.openggf.mods.scene.RomSpriteRequest.Compression;
import com.openggf.mods.scene.RomSpriteRequest.DplcLayout;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSpriteSet;

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

    private RomSprites() {
    }

    static SceneSpriteSet load(SceneRomArt rom, String key) {
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
            case "robotnik_ship" -> rom.sprites(RomSpriteRequest.of(
                    0x0D771E, Compression.NEMESIS,       // ArtNem_RobotnikShip
                    0x06820C, 0), aiz(rom));             // Map_RobotnikShip (head 0-3, Egg Mobile 5)
            case "boss_explosion" -> rom.sprites(RomSpriteRequest.of(
                    0x0D73CE, Compression.NEMESIS,       // ArtNem_BossExplosion
                    0x083FFC, 0), aiz(rom));             // Map_BossExplosion

            // ---- Characters and props ----
            case "egg_robo" -> rom.sprites(RomSpriteRequest.of(
                    0x17B17E, Compression.KOSINSKI_MODULED, // ArtKosM_EggRoboBadnik
                    0x184F34, 0), rom.palette(PAL_CONTINUE, 64)); // Map_EggRobo
            case "tornado" -> rom.sprites(RomSpriteRequest.of(
                    0x382624, Compression.KOSINSKI_MODULED, // ArtKosM_AIZIntroPlane
                    0x364470, 0), aiz(rom));             // Map_AIZIntroPlane (body 0, propeller 1-4, flame 5-6)
            case "monitor" -> rom.sprites(RomSpriteRequest.of(
                    0x190F4A, Compression.NEMESIS,       // ArtNem_Monitors
                    0x01DBA2, 0), aiz(rom));             // Map_Monitor (box 0, icons 1-10, broken 11)
            case "ring" -> rom.sprites(RomSpriteRequest.of(
                    0x192AEE, Compression.NEMESIS,       // ArtNem_RingHUDText (ring tiles 0-13)
                    0x01A99A, 1), aiz(rom));             // Map_Ring (spin 0-3, sparkle 4-7)
            case "explosion" -> rom.sprites(RomSpriteRequest.of(
                    0x19200A, Compression.NEMESIS,       // ArtNem_Explosion
                    0x01E758, 0), aiz(rom));             // Map_Explosion
            case "flicky" -> rom.sprites(RomSpriteRequest.of(
                    0x1931D6, Compression.NEMESIS,       // ArtNem_BlueFlicky
                    0x02CEBA, 0), aiz(rom));             // Map_Animals1 (flap 0-1, released 2)
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
            case "egg_capsule" -> rom.sprites(RomSpriteRequest.of(
                    0x0DD990, Compression.NEMESIS,       // ArtNem_EggCapsule
                    0x086BFC, 0), aiz(rom));             // Map_EggCapsule
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
            default -> null;
        };
    }

    /** Player line 0 plus the Angel Island act 1 zone palette on lines 1-3. */
    private static int[] aiz(SceneRomArt rom) {
        int[] palette = new int[64];
        System.arraycopy(rom.palette(PAL_SONIC_TAILS, 16), 0, palette, 0, 16);
        System.arraycopy(rom.palette(PAL_AIZ, 48), 0, palette, 16, 48);
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
}
