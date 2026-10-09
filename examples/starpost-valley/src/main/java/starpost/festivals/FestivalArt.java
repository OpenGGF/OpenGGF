package starpost.festivals;

import com.openggf.mods.scene.RomSpriteRequest;
import com.openggf.mods.scene.RomSpriteRequest.Compression;
import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneLevelKit;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.HashMap;
import java.util.Map;
import starpost.art.Art;
import starpost.art.Tone;

/**
 * The festivals' pictures, all from the player's ROMs (addresses from s1disasm's sonic.lst,
 * skdisasm's sonic3k.lst and, for Sonic 2, matched against its disassembly's art files):
 *
 * <ul>
 *   <li>the Signpost Board, assembled from Green Hill's pixels like the town's buildings (bridge
 *       logs, the planks of block 6, the grass lip), with notes as plain paper shapes;</li>
 *   <li>Green Hill's big sunflower (Art_GhzFlower1, the two frames AniArt_GHZ swaps every 16
 *       frames) and Spring Yard's bumper (Nem_Bumper, Map_Bump);</li>
 *   <li>the Egg Mobile (ArtNem_RobotnikShip, Map_RobotnikShip) and Mecha Sonic (ArtUnc_MechaSonic,
 *       Map_MechaSonic, DPLC_MechaSonic) from Sonic 3 &amp; Knuckles;</li>
 *   <li>Sonic on his snowboard and the empty board from Ice Cap's opening (ArtUnc_SonicSnowboard
 *       and ArtUnc_Snowboard with their maps and DPLCs, Pal_SonicTails);</li>
 *   <li>slot faces: Casino Night's (ArtUnc_CNZSlotPics, Sonic 2) when Sonic 2 is supplied, else
 *       Sonic 3's slot bonus stage faces (ArtUnc_SlotOptions, Pal_Slot_Special);</li>
 *   <li>Star Light Zone's night sky and Scrap Brain Zone's steel, from Sonic 1's level kits.</li>
 * </ul>
 *
 * Built on first use from {@code update} or a screen's {@code enter}, never in draw; anything
 * missing comes back null and the festival does without it.
 */
public final class FestivalArt {
    // Sonic 1 (sonic.lst).
    private static final int ART_GHZ_FLOWER1 = 0x66C96;   // Art_GhzFlower1: 2 frames of 16 tiles
    private static final int NEM_BUMPER = 0x342F8;
    private static final int MAP_BUMP = 0xF18C;
    private static final int S1_SLZ = 4;                  // the engine's Sonic 1 zone ids: 4 Star Light
    private static final int S1_SBZ = 5;                  // 5 Scrap Brain
    // Sonic 3 & Knuckles (sonic3k.lst).
    private static final int ARTNEM_ROBOTNIK_SHIP = 0x0D771E;
    private static final int MAP_ROBOTNIK_SHIP = 0x06820C;
    private static final int ARTUNC_MECHA_SONIC = 0x175A9E;
    private static final int ARTUNC_MECHA_SONIC_SIZE = 0x56E0;
    private static final int MAP_MECHA_SONIC = 0x1853AA;
    private static final int DPLC_MECHA_SONIC = 0x185852;
    private static final int PAL_SSZ_GHZ_MISC = 0x07D850;    // Pal_SSZGHZMisc (Mecha Sonic's line)
    private static final int PAL_DEZ = 0x0A973C;
    private static final int ARTUNC_SONIC_SNOWBOARD = 0x345010;
    private static final int ARTUNC_SONIC_SNOWBOARD_SIZE = 0x2840;
    private static final int MAP_SONIC_SNOWBOARD = 0x347E30;
    private static final int DPLC_SONIC_SNOWBOARD = 0x347F8A;
    private static final int ARTUNC_SNOWBOARD = 0x347850;
    private static final int ARTUNC_SNOWBOARD_SIZE = 0x5E0;
    private static final int MAP_SNOWBOARD = 0x348020;
    private static final int DPLC_SNOWBOARD = 0x348128;
    private static final int ARTUNC_SLOT_OPTIONS = 0x158CAE;
    private static final int PAL_SLOT_SPECIAL = 0xA9C7C;
    private static final int PAL_SONIC_TAILS = 0x0A8A3C;
    // Sonic 2: ArtUnc_CNZSlotPics ("art/uncompressed/Slot pictures.bin", six 4x4-tile faces), and
    // Pal_SonicTails (line 0, the slot pictures' palette line).
    private static final int S2_CNZ_SLOT_PICS = 0x4EEFE;
    private static final int S2_PAL_SONIC = 0x29E2;

    public final Art art;
    private final SceneRomArt s2;
    private final Map<Integer, SceneImage> boards = new HashMap<>();
    private SceneImage[] flowers;
    private SceneSpriteSet bumper;
    private SceneSpriteSet eggMobile;
    private SceneSpriteSet mechaSonic;
    private SceneSpriteSet sonicBoard;
    private SceneSpriteSet board;
    private SceneImage[] slotFaces;
    private boolean slotFacesFromS2;
    private SceneBackdrop starLight;
    private SceneLevelKit scrapBrain;
    private boolean triedFlowers;
    private boolean triedBumper;
    private boolean triedEgg;
    private boolean triedMecha;
    private boolean triedBoards;
    private boolean triedSlots;
    private boolean triedStarLight;
    private boolean triedScrapBrain;

    public FestivalArt(Art art, SceneRomArt s2) {
        this.art = art;
        this.s2 = s2;
    }

    // ------------------------------------------------------------------ the board

    /** The Signpost Board in a season's colours: a plank face framed with bridge logs, on two log posts. */
    public SceneImage board(int season) {
        return boards.computeIfAbsent(season, s -> new Tone(s).apply(buildBoard()));
    }

    private SceneImage buildBoard() {
        int w = 64, h = 62;
        int[] px = new int[w * h];
        SceneImage log = art.kit.blockImage(51).crop(0, 129, 256, 12);
        SceneImage planks = art.kit.blockImage(6).crop(64, 128, 64, 64);
        SceneImage flat = art.kit.blockImage(60);
        // Two posts: the bridge log stood on end (its pixels turned a quarter).
        for (int post : new int[] {8, w - 16}) {
            for (int y = 14; y < h; y++) {
                for (int x = 0; x < 8; x++) {
                    int c = log.pixel(Math.floorMod(y * 3, 256), 2 + x);
                    if (c >>> 24 != 0) {
                        px[y * w + post + x] = c;
                    }
                }
            }
        }
        // The face: planks, framed top and bottom by a log, a grass lip along the top as a roof.
        for (int y = 10; y < 44; y++) {
            for (int x = 2; x < w - 2; x++) {
                int c = planks.pixel(Math.floorMod(x * 2, 64), y + 10);
                if (c >>> 24 != 0) {
                    px[y * w + x] = c;
                }
            }
        }
        for (int band : new int[] {6, 42}) {
            for (int y = 0; y < 6; y++) {
                for (int x = 0; x < w; x++) {
                    int c = log.pixel(x + 40, 3 + y);
                    if (c >>> 24 != 0) {
                        px[(band + y) * w + x] = c;
                    }
                }
            }
        }
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < w; x++) {
                int c = flat.pixel(x + 100, Art.FLOOR - 4 + y);
                if (c >>> 24 != 0) {
                    px[y * w + x] = c;
                }
            }
        }
        return new SceneImage(w, h, px);
    }

    // ------------------------------------------------------------------ Sonic 1 pieces

    /**
     * Green Hill's big sunflower (32x32), frame 0 or 1, its petals in one of the parade's cycle of
     * colours (the parade's palette cycle; 0 is the ROM's own yellow), or null. The flower is the
     * four 16x16 chunks GHZ lays out for VRAM tiles $35C-$36B (chunks 427-430 in a square, each
     * two tiles by two, palette line 1), which AniArt_GHZ_Bigflower fills from Art_GhzFlower1.
     */
    public SceneImage flower(int frame, int petals) {
        if (!triedFlowers) {
            triedFlowers = true;
            try {
                int[] line = new int[16];
                System.arraycopy(art.ghzPalette, 16, line, 0, 16);
                int[] cycle = petalCycle;
                flowers = new SceneImage[2 * cycle.length];
                for (int f = 0; f < 2; f++) {
                    int[] px = new int[32 * 32];
                    for (int chunk = 0; chunk < 4; chunk++) {
                        SceneImage piece = art.s1.tiles(ART_GHZ_FLOWER1, Compression.UNCOMPRESSED, f * 16 + chunk * 4, 2, 2,
                                false, line);
                        int ox = chunk % 2 * 16, oy = chunk / 2 * 16;
                        for (int y = 0; y < 16; y++) {
                            for (int x = 0; x < 16; x++) {
                                px[(oy + y) * 32 + ox + x] = piece.pixel(x, y);
                            }
                        }
                    }
                    for (int c = 0; c < cycle.length; c++) {
                        flowers[f * cycle.length + c] = new SceneImage(32, 32, recolourPetals(px, cycle[c]));
                    }
                }
            } catch (RuntimeException e) {
                flowers = null;
            }
        }
        if (flowers == null) {
            return null;
        }
        int n = petalCycle.length;
        return flowers[(frame & 1) * n + Math.floorMod(petals, n)];
    }

    /** The parade's petal colours, Mega Drive levels: Green Hill's yellow, then orange, red, orange. */
    static final int PETAL_CYCLE_LENGTH = 4;
    private final int[] petalCycle = {0, 0xFFFFB600, 0xFFFF6D00, 0xFFFFB600};

    /** The petals (the flower's yellows) turned to {@code colour}; 0 keeps the ROM's. */
    private static int[] recolourPetals(int[] px, int colour) {
        int[] out = px.clone();
        if (colour == 0) {
            return out;
        }
        for (int i = 0; i < out.length; i++) {
            int p = out[i];
            int r = p >> 16 & 255, g = p >> 8 & 255, b = p & 255;
            if (p >>> 24 != 0 && r > 180 && g > 150 && b < 100) {
                out[i] = colour;
            }
        }
        return out;
    }

    /** Spring Yard's round bumper (Map_Bump frame 0), or null. */
    public SceneSpriteSet bumper() {
        if (!triedBumper) {
            triedBumper = true;
            bumper = load(art.s1, RomSpriteRequest.of(NEM_BUMPER, Compression.NEMESIS, MAP_BUMP, 0), art.ghzPalette);
        }
        return bumper;
    }

    /** Star Light Zone's night city sky (Sonic 1's level kit backdrop), or null. */
    public SceneBackdrop starLight() {
        if (!triedStarLight) {
            triedStarLight = true;
            try {
                SceneLevelKit kit = art.s1.levelKit(S1_SLZ, 0);
                starLight = kit == null ? null : kit.backdrop();
            } catch (RuntimeException e) {
                starLight = null;
            }
        }
        return starLight;
    }

    /** Scrap Brain Zone act 1's kit (its blocks are the haunted maze's steel), or null. */
    public SceneLevelKit scrapBrain() {
        if (!triedScrapBrain) {
            triedScrapBrain = true;
            try {
                scrapBrain = art.s1.levelKit(S1_SBZ, 0);
            } catch (RuntimeException e) {
                scrapBrain = null;
            }
        }
        return scrapBrain;
    }

    /**
     * The maze's steel, cut from Scrap Brain act 1's blocks: the floor from block 4's dark deck
     * plate, the walls from block 16's piped panels. Null without the kit.
     */
    public SceneImage[] scrapBrainSteel() {
        if (steel == null) {
            SceneLevelKit kit = scrapBrain();
            if (kit == null) {
                return null;
            }
            steel = new SceneImage[] {kit.blockImage(4).crop(176, 40, 32, 32), kit.blockImage(16).crop(64, 96, 32, 32),
                kit.blockImage(24).crop(0, 0, 32, 32)};
        }
        return steel;
    }

    private SceneImage[] steel;

    // ------------------------------------------------------------------ Sonic 3 & Knuckles pieces

    /** The Egg Mobile (Map_RobotnikShip: frame 5 the ship, 2 Robotnik in it), or null. */
    public SceneSpriteSet eggMobile() {
        if (!triedEgg) {
            triedEgg = true;
            eggMobile = load(art.s3k, RomSpriteRequest.of(ARTNEM_ROBOTNIK_SHIP, Compression.NEMESIS,
                    MAP_ROBOTNIK_SHIP, 0), art.aizPalette);
        }
        return eggMobile;
    }

    /** Mecha Sonic (Map_MechaSonic, art line 1 in Pal_SSZGHZMisc), or null. */
    public SceneSpriteSet mechaSonic() {
        if (!triedMecha) {
            triedMecha = true;
            int[] palette = new int[64];
            try {
                System.arraycopy(art.s3k.palette(PAL_SONIC_TAILS, 16), 0, palette, 0, 16);
                System.arraycopy(art.s3k.palette(PAL_DEZ, 48), 0, palette, 16, 48);
                System.arraycopy(art.s3k.palette(PAL_SSZ_GHZ_MISC, 16), 0, palette, 16, 16);
                mechaSonic = art.s3k.sprites(RomSpriteRequest.streamed(ARTUNC_MECHA_SONIC, ARTUNC_MECHA_SONIC_SIZE,
                        MAP_MECHA_SONIC, DPLC_MECHA_SONIC, RomSpriteRequest.DplcLayout.OBJECT, 1), palette);
            } catch (RuntimeException e) {
                mechaSonic = null;
            }
        }
        return mechaSonic;
    }

    /** Sonic riding his snowboard (Map_SonicSnowboard), or null. */
    public SceneSpriteSet sonicBoard() {
        loadBoards();
        return sonicBoard;
    }

    /** The snowboard on its own (Map_Snowboard), or null. */
    public SceneSpriteSet snowboard() {
        loadBoards();
        return board;
    }

    private void loadBoards() {
        if (triedBoards) {
            return;
        }
        triedBoards = true;
        int[] palette = art.aizPalette;
        sonicBoard = load(art.s3k, RomSpriteRequest.streamed(ARTUNC_SONIC_SNOWBOARD, ARTUNC_SONIC_SNOWBOARD_SIZE,
                MAP_SONIC_SNOWBOARD, DPLC_SONIC_SNOWBOARD, RomSpriteRequest.DplcLayout.PLAYER, 0), palette);
        board = load(art.s3k, RomSpriteRequest.streamed(ARTUNC_SNOWBOARD, ARTUNC_SNOWBOARD_SIZE, MAP_SNOWBOARD,
                DPLC_SNOWBOARD, RomSpriteRequest.DplcLayout.PLAYER, 0), palette);
    }

    // ------------------------------------------------------------------ slot faces

    /**
     * A slot face in Sonic 2's order ({@link Fair#SONIC} ... {@link Fair#BAR}), 32 pixels square:
     * Casino Night's own when Sonic 2 is supplied, else Sonic 3's slot bonus faces (which have
     * every Casino Night face but a different order). Null without either.
     */
    public SceneImage slotFace(int face) {
        if (!triedSlots) {
            triedSlots = true;
            slotFaces = new SceneImage[6];
            try {
                if (s2 != null) {
                    int[] palette = s2.palette(S2_PAL_SONIC, 16);
                    for (int f = 0; f < 6; f++) {
                        slotFaces[f] = s2.tiles(S2_CNZ_SLOT_PICS, Compression.UNCOMPRESSED, f * 16, 4, 4, true, palette);
                    }
                    slotFacesFromS2 = true;
                }
            } catch (RuntimeException e) {
                slotFacesFromS2 = false;
            }
            if (!slotFacesFromS2) {
                try {
                    int[] palette = art.s3k.palette(PAL_SLOT_SPECIAL, 16);
                    // Sonic 3's faces: 0 Jackpot, 1 Sonic, 2 Tails, 3 Knuckles, 4 Robotnik, 5 Ring, 6 Bar.
                    int[] fromS3 = {1, 2, 4, 0, 5, 6};
                    for (int f = 0; f < 6; f++) {
                        slotFaces[f] = art.s3k.tiles(ARTUNC_SLOT_OPTIONS, Compression.UNCOMPRESSED, fromS3[f] * 16,
                                4, 4, true, palette);
                    }
                } catch (RuntimeException e) {
                    slotFaces = new SceneImage[6];
                }
            }
        }
        return face >= 0 && face < 6 ? slotFaces[face] : null;
    }

    /** Whether the slot booth wears Casino Night's faces (Sonic 2 supplied). */
    public boolean casinoNight() {
        slotFace(0);
        return slotFacesFromS2;
    }

    private static SceneSpriteSet load(SceneRomArt rom, RomSpriteRequest request, int[] palette) {
        if (rom == null) {
            return null;
        }
        try {
            return rom.sprites(request, palette);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
