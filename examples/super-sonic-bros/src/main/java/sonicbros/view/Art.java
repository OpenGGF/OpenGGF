package sonicbros.view;

import com.openggf.mods.scene.RomSpriteRequest;
import com.openggf.mods.scene.RomSpriteRequest.Compression;
import com.openggf.mods.scene.RomSpriteRequest.DplcLayout;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Every picture the game uses, decoded from the player's Sonic 3 &amp; Knuckles ROM on first
 * use and kept: the heroes' own sprite sets, the badnik and boss sprites (addresses and palette
 * lines as Three Islands and Slay the Robotnik established them, labels from the S&amp;K
 * disassembly), monitors, rings and explosions, plus the code-drawn props in {@link Gfx}.
 */
public final class Art {
    static final int PAL_SONIC_TAILS = 0x0A8A3C;   // Pal_SonicTails (line 0)
    static final int PAL_AIZ = 0x0A8B7C;           // Pal_AIZ (lines 1-3)
    static final int PAL_HCZ = 0x0A8D9C;           // Pal_HCZ1
    static final int PAL_LBZ = 0x0A929C;           // Pal_LBZ1
    static final int PAL_SSZ = 0x0A973C;           // Pal_SSZ1
    static final int PAL_MECHA = 0x07D850;         // Pal_SSZGHZMisc (Mecha Sonic, line 1)
    static final int PAL_CONTINUE = 0x05CBCA;      // Pal_ContinueScreen (Egg Robo, line 1)
    static final int PAL_KNUCKLES = 0x0A8AFC;      // Pal_Knuckles
    static final int PALCYCLE_SUPER_SONIC = 0x00398E; // PalCycle_SuperSonic

    public final SceneRomArt rom;
    private final Map<String, SceneSpriteSet> sets = new HashMap<>();
    private final Map<Long, SceneSprite> recolored = new HashMap<>();
    private final Map<String, SceneImage> images = new HashMap<>();
    private final Map<String, SceneSprite> sprites = new HashMap<>();

    public Art(SceneRomArt rom) {
        this.rom = rom;
    }

    private int[] s3k(int zonePalette) {
        int[] palette = new int[64];
        System.arraycopy(rom.palette(PAL_SONIC_TAILS, 16), 0, palette, 0, 16);
        System.arraycopy(rom.palette(zonePalette, 48), 0, palette, 16, 48);
        return palette;
    }

    /** A named sprite set. */
    public SceneSpriteSet set(String key) {
        SceneSpriteSet found = sets.get(key);
        if (found != null) return found;
        SceneSpriteSet made = switch (key) {
            case "sonic", "tails", "knuckles" -> rom.character(key);
            case "tails_tails" -> rom.characterAccessory("tails");
            // Super Sonic: ArtUnc_Sonic through Map_SuperSonic / PLC_SuperSonic, in the gold of
            // PalCycle_SuperSonic's brightest step (three colours written over palette 2-4).
            case "super_sonic" -> {
                int[] pal = s3k(PAL_AIZ);
                System.arraycopy(rom.palette(PALCYCLE_SUPER_SONIC + 6 * 6, 3), 0, pal, 2, 3);
                yield rom.sprites(RomSpriteRequest.streamed(0x100000, 0x40060, 0x146816, 0x148378, DplcLayout.PLAYER, 0), pal);
            }
            // ArtUnc_MechaSonic with Map/DPLC_MechaSonic; Pal_SSZGHZMisc over line 1.
            case "mecha" -> {
                int[] pal = s3k(PAL_SSZ);
                System.arraycopy(rom.palette(PAL_MECHA, 16), 0, pal, 16, 16);
                yield rom.sprites(RomSpriteRequest.streamed(0x175A9E, 0x56E0, 0x1853AA, 0x185852, DplcLayout.OBJECT, 1), pal);
            }
            // ArtKosM_EggRoboBadnik / Map_EggRobo, Pal_ContinueScreen.
            case "eggrobo" -> rom.sprites(RomSpriteRequest.of(0x17B17E, Compression.KOSINSKI_MODULED, 0x184F34, 0),
                    rom.palette(PAL_CONTINUE, 64));
            // ArtNem_RobotnikShip / Map_RobotnikShip: head 0-3, Egg Mobile 5.
            case "ship" -> rom.sprites(RomSpriteRequest.of(0x0D771E, Compression.NEMESIS, 0x06820C, 0), s3k(PAL_HCZ));
            // ArtUnc_AIZRhinobot with Map/DPLC_Rhinobot.
            case "rhinobot" -> rom.sprites(RomSpriteRequest.streamed(0x36732A, 0xAA0, 0x3615A8, 0x36156E, DplcLayout.OBJECT, 1),
                    s3k(PAL_AIZ));
            // ArtKosM_Orbinaut (Launch Base): the spike ball is frame 1.
            case "orbinaut" -> rom.sprites(RomSpriteRequest.of(0x377D1A, Compression.KOSINSKI_MODULED, 0x3604A4, 1), s3k(PAL_LBZ));
            // ArtNem_Monitors / Map_Monitor: box 0-2, icons 3-10, broken 11.
            case "monitor" -> rom.sprites(RomSpriteRequest.of(0x190F4A, Compression.NEMESIS, 0x01DBA2, 0), s3k(PAL_AIZ));
            // ArtNem_RingHUDText / Map_Ring: spin 0-3, sparkle 4-7.
            case "ring" -> rom.sprites(RomSpriteRequest.of(0x192AEE, Compression.NEMESIS, 0x01A99A, 1), s3k(PAL_AIZ));
            // ArtNem_Explosion / Map_Explosion.
            case "explosion" -> rom.sprites(RomSpriteRequest.of(0x19200A, Compression.NEMESIS, 0x01E758, 0), s3k(PAL_AIZ));
            // ArtUnc_Invincibility / Map_Invincibility (sparkles 4-8).
            case "stars" -> rom.sprites(RomSpriteRequest.uncompressed(0x18A204, 0x400, 0x018AEA, 0), s3k(PAL_AIZ));
            default -> throw new IllegalArgumentException("Unknown sprite set " + key);
        };
        sets.put(key, made);
        return made;
    }

    public SceneSprite frame(String key, int index) { return set(key).frame(index); }

    /**
     * A sprite frame recoloured for a costume (0 is the original colours). Cached per set,
     * frame and costume.
     */
    public SceneSprite costumed(String key, int index, String fighter, int costume) {
        SceneSprite plain = frame(key, index);
        if (costume <= 0) return plain;
        long k = ((long) key.hashCode() << 32) ^ ((long) index << 8) ^ costume;
        SceneSprite found = recolored.get(k);
        if (found != null) return found;
        double[] c = Costumes.recipe(fighter, costume);
        SceneSprite made = Gfx.recolor(plain, c[0], c[1], c[2], c[3], c[4]);
        if (c.length > 5) made = Gfx.recolor(made, c[5], c[6], c[7], c[8], c[9]);
        recolored.put(k, made);
        return made;
    }

    /** A cached code-drawn image. */
    public SceneImage image(String key) {
        SceneImage found = images.get(key);
        if (found != null) return found;
        SceneImage made = switch (key) {
            case "disc8" -> Gfx.disc(8, false);
            case "soft16" -> Gfx.disc(16, true);
            case "soft32" -> Gfx.disc(32, true);
            case "ring12" -> Gfx.ring(12, 2.5);
            case "ring24" -> Gfx.ring(24, 3);
            case "ring40" -> Gfx.ring(40, 4);
            case "bubble24" -> Gfx.bubble(24);
            case "shadow" -> Gfx.shadow(26, 7);
            case "sparkle" -> Gfx.sparkle(5);
            case "sparkle_big" -> Gfx.sparkle(9);
            case "streak" -> Gfx.streak(36);
            case "pixel" -> new SceneImage(1, 1, new int[] {0xFFFFFFFF});
            default -> throw new IllegalArgumentException("Unknown image " + key);
        };
        images.put(key, made);
        return made;
    }

    /** A cached code-drawn sprite. */
    public SceneSprite prop(String key) {
        SceneSprite found = sprites.get(key);
        if (found != null) return found;
        SceneSprite made = switch (key) {
            case "ball" -> Gfx.wreckingBall(11);
            case "ball_small" -> Gfx.wreckingBall(6);
            case "bomb" -> Gfx.bomb(6);
            case "missile" -> Gfx.missile();
            case "spring" -> Gfx.spring();
            case "flame0" -> Gfx.sideFlame(10, 18, 0);
            case "flame1" -> Gfx.sideFlame(10, 18, 1);
            case "flame2" -> Gfx.sideFlame(10, 18, 2);
            case "bigflame0" -> Gfx.flame(18, 30, 3);
            case "bigflame1" -> Gfx.flame(18, 30, 4);
            default -> throw new IllegalArgumentException("Unknown prop " + key);
        };
        sprites.put(key, made);
        return made;
    }

    /**
     * A small head-and-shoulders portrait for HUDs and menus, cut from the fighter's standing
     * sprite and recoloured for the costume. {@code size} pixels square.
     */
    public SceneImage portrait(String fighter, int costume, int size) {
        String key = "portrait:" + fighter + ":" + costume + ":" + size;
        SceneImage found = images.get(key);
        if (found != null) return found;
        SceneSprite s = FighterArt.portraitSprite(this, fighter, costume);
        SceneImage img = s.image();
        int cx = s.originX();
        int top = 0;
        // Find the first opaque row: the head's top.
        outer:
        for (int y = 0; y < img.height(); y++) {
            for (int x = 0; x < img.width(); x++) if ((img.pixel(x, y) >>> 24) != 0) { top = y; break outer; }
        }
        int[] px = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int sx = cx - size / 2 + x + FighterArt.portraitShift(fighter);
                int sy = top + y - 1;
                px[y * size + x] = img.pixel(sx, sy);
            }
        }
        // Outline the portrait so it reads on any panel colour.
        int[] out = px.clone();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if ((px[y * size + x] >>> 24) != 0) continue;
                boolean edge = false;
                for (int d = 0; d < 4 && !edge; d++) {
                    int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0);
                    int ny = y + (d == 2 ? 1 : d == 3 ? -1 : 0);
                    if (nx >= 0 && ny >= 0 && nx < size && ny < size && (px[ny * size + nx] >>> 24) != 0) edge = true;
                }
                if (edge) out[y * size + x] = 0xFF000000;
            }
        }
        SceneImage made = new SceneImage(size, size, out);
        images.put(key, made);
        return made;
    }
}
