package starpost.realvalley;

import com.openggf.mods.code.BakedLevelRef;
import com.openggf.mods.code.ModContext;
import com.openggf.mods.code.ModZoneContribution;

/**
 * The valley as a real Sonic 3&amp;K act (design P1): the same Green Hill blocks as the scene's
 * {@code starpost.valley.Valley}, re-encoded at load time from the player's Sonic 1 ROM and played by
 * the engine's own Sonic, Tails and Knuckles with stock S3K springs, path swappers and a Star Post.
 *
 * <p>The jar registers only a one-pattern placeholder act ({@code levels/valley}, generated, no
 * Sega data); {@link RealValleyPatch} replaces it with the re-encoded level whenever the act loads.
 * The scene farm gate starts this act; town doors return to scene menus and back.
 */
public final class RealValley {
    /** The mod's manifest id, which owns the zone. */
    public static final String OWNER = "starpost-valley";
    /** The zone's local key: {@code ZoneKey.mod(OWNER, ZONE)}. */
    public static final String ZONE = "valley";
    /** Sonic 1 Green Hill act 1 ({@code LevelData.S1_GREEN_HILL_1}), whose blocks the valley uses. */
    public static final int S1_GHZ1_LEVEL_INDEX = 0x80;
    /** Empty 128-pixel rows of sky above the Sonic 1 blocks, so springs and jumps stay in the level. */
    public static final int SKY_ROWS = 1;
    /** Sonic 1 block size: each becomes 2x2 Sonic 3&amp;K blocks. */
    public static final int S1_BLOCK = 256;
    /** The loop block ({@code GHZ_LOOP1} $B5 without its layout flag) and its low-plane twin. */
    public static final int LOOP_BLOCK = 0x35;
    public static final int LOOP_TWIN = 0x36;
    /** The block with the spring at the foot of the totem ledge. */
    public static final int SPRING_BLOCK = 3;
    /** The farm gate by the waterfall, where the valley starts (the scene's "farm_gate" place). */
    public static final int FARM_GATE_X = 150;
    /**
     * The one palette cell the placeholder claims. The host bridge re-submits the placeholder's
     * claims every frame, so the re-encoded art never uses this cell (see {@link ValleyEncoder}).
     */
    public static final int PLACEHOLDER_LINE = 3;
    public static final int PLACEHOLDER_COLOR = 15;

    private RealValley() {
    }

    /** The valley's Sonic 1 Green Hill act 1 blocks, west to east (as {@code Valley.blocks}). */
    public static int[] blocks() {
        return new int[] {13, 45, 60, 60, 60, 60, 45, 3, 45, 53, 38, 1, 16};
    }

    /** The column of the first occurrence of {@code block}, or -1. */
    public static int columnOf(int block) {
        int[] blocks = blocks();
        for (int i = 0; i < blocks.length; i++) {
            if (blocks[i] == block) {
                return i;
            }
        }
        return -1;
    }

    /** World Y of a Sonic 1 block's top row (below the sky rows). */
    public static int terrainTop() {
        return SKY_ROWS * 128;
    }

    /** Registers the placeholder act and the patch that fills it from the Sonic 1 ROM. */
    public static void register(ModContext context) {
        context.registerZone(ModZoneContribution.singleAct(ZONE,
                new BakedLevelRef("levels/valley/level.json"), null, null, false));
        context.registerGamePatch(new RealValleyPatch());
    }
}
