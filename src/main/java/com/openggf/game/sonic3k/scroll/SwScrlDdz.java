package com.openggf.game.sonic3k.scroll;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.DdzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.scroll.compose.DeformationPlan;
import com.openggf.level.scroll.compose.ScrollEffectComposer;
import com.openggf.level.scroll.compose.ScrollValueTable;

import static com.openggf.level.scroll.M68KMath.negWord;

/**
 * The Doomsday Zone ({@code $C00}) screen and background scroll: {@code DDZ_ScreenEvent},
 * {@code sub_59648}, {@code DDZ_BackgroundInit}/{@code DDZ_BackgroundEvent} and {@code sub_596EA}
 * (sonic3k.asm:118813-119007).
 *
 * <p><b>Foreground.</b> The foreground plane is not the level: it holds the end boss's body, and
 * its scroll is decoupled from the camera. Every frame {@code sub_59648} sets
 * {@code _unkEE98 = Camera_X_pos_copy - Events_bg+$02 + Events_bg+$00} and
 * {@code _unkEE9C = Camera_Y_pos_copy - Events_bg+$04 + $100}, where {@code +$02/+$04} is the boss
 * position and {@code +$00} the window base ({@code $200} phase 1, {@code $600} phase 2). The
 * {@code Events_routine_fg} stages then keep the previous words when the new window leaves the drawn
 * range (X below {@code $400} and Y below {@code $220} in phase 1; X in {@code $400..$7FF} and Y below
 * {@code $220} in phase 2). The transition into phase 2 freezes the old words while
 * {@code Draw_PlaneVertBottomUp} redraws the plane. {@code ApplyDeformation2} writes
 * {@code -_unkEE98} as the foreground word of every line and {@code V_scroll_value} is
 * {@code _unkEE9C}. The engine draws the foreground from the whole layout rather than a 512-pixel
 * nametable: once routine 4 starts the drawn window equals the layout at the scroll words, and
 * before that {@link DdzZoneRuntimeState#displayedForegroundX()} wraps into the initial draw.
 *
 * <p><b>Background.</b> {@code Camera_Y_pos_BG_copy} is half the camera Y. {@code sub_596EA} writes
 * six words downward from {@code HScroll_table+$00C}: {@code Events_bg+$06 / 16}, then each next
 * word 1/8 of that smaller; word 0 stays at the {@code clr.w (HScroll_table)} value, zero.
 * {@code DDZ_BGDeformArray} bands them from the top.
 */
public class SwScrlDdz extends SwScrlS3kDefault {
    /** {@code DDZ_BGDeformArray}. */
    private static final int[] DDZ_BG_DEFORM = {0xB0, 0x10, 8, 8, 0x18, 0x38, 0x7FFF};
    private static final int SPEED_WORDS = 6;
    private static final DeformationPlan.ScrollValueTransform NEGATE_WORD = value -> negWord(value);

    /** {@code Events_routine_fg} stages. */
    static final int FG_IDLE = 0;
    static final int FG_PHASE_ONE = 4;
    static final int FG_REDRAW = 8;
    static final int FG_PHASE_TWO = 0xC;

    private final ScrollEffectComposer composer = new ScrollEffectComposer();
    private final ScrollValueTable hScrollTable = ScrollValueTable.ofLength(SPEED_WORDS + 1);
    private short foregroundVscroll;

    @Override
    public void update(int[] horizScrollBuf, int cameraX, int cameraY, int frameCounter, int actId) {
        DdzZoneRuntimeState ddz = GameServices.hasRuntime()
                ? S3kRuntimeStates.currentDdz(GameServices.zoneRuntimeRegistry()).orElse(null)
                : null;
        if (ddz == null) {
            foregroundVscroll = 0;
            super.update(horizScrollBuf, cameraX, cameraY, frameCounter, actId);
            return;
        }
        resetScrollTracking();
        composer.reset();

        applyScreenEvent(ddz, cameraX, cameraY);
        ddz.setInitialPlaneBlank(initialPlaneBlank());

        short bgY = (short) (((short) cameraY) >> 1);
        composer.setVscrollFactorBG(bgY);
        buildSpeedTable(hScrollTable, ddz.backgroundScroll());
        DeformationPlan.applyTableBands(composer, bgY, (short) negWord(ddz.displayedForegroundX()),
                hScrollTable, DDZ_BG_DEFORM, 0, NEGATE_WORD);
        foregroundVscroll = (short) ddz.displayedForegroundY();

        composer.copyPackedScrollWordsTo(horizScrollBuf);
        vscrollFactorBG = composer.getVscrollFactorBG();
        minScrollOffset = composer.getMinScrollOffset();
        maxScrollOffset = composer.getMaxScrollOffset();
    }

    /** {@code DDZ_ScreenEvent}, preceded by {@code sub_59648}. */
    static void applyScreenEvent(DdzZoneRuntimeState ddz, int cameraX, int cameraY) {
        int previousX = ddz.foregroundX();
        int previousY = ddz.foregroundY();
        int base = ddz.foregroundWindowBase();
        short x = (short) (cameraX - ddz.bossX() + base);
        short y = (short) (cameraY - ddz.bossY() + 0x100);
        ddz.setForeground(x, y);
        int ux = x & 0xFFFF;
        int uy = y & 0xFFFF;
        switch (ddz.foregroundRoutine()) {
            case FG_IDLE -> {
                // tst.w (Events_bg+$00).w / beq.w locret
                if (base == 0) {
                    return;
                }
                roundForDraw(ddz);
                ddz.setForegroundRoutine(FG_PHASE_ONE);
                phaseOne(ddz, base, ux, uy, previousX, previousY);
            }
            case FG_PHASE_ONE -> phaseOne(ddz, base, ux, uy, previousX, previousY);
            case FG_REDRAW -> {
                ddz.setForeground(previousX, previousY);
                // Draw_PlaneVertBottomUp: the engine plane has no staged nametable to fill.
                ddz.setForegroundRoutine(FG_PHASE_TWO);
            }
            case FG_PHASE_TWO -> {
                // cmpi.w #$400,d0 / blo / cmpi.w #$800,d0 / bhs / cmpi.w #$220,d1 / blo draw
                if (ux < 0x400 || ux >= 0x800 || uy >= 0x220) {
                    ddz.setForeground(previousX, previousY);
                } else {
                    roundForDraw(ddz);
                }
            }
            default -> throw new IllegalStateException("DDZ Events_routine_fg " + ddz.foregroundRoutine());
        }
    }

    /** {@code loc_595C0}. */
    private static void phaseOne(DdzZoneRuntimeState ddz, int base, int ux, int uy,
                                 int previousX, int previousY) {
        if (base == 0x200) {
            if (ux >= 0x400 || uy >= 0x220) {
                ddz.setForeground(previousX, previousY);
            } else {
                roundForDraw(ddz);
            }
            return;
        }
        // loc_595D6: sub_59672, keep the previous words, start the bottom-up redraw.
        roundForDraw(ddz);
        ddz.setForeground(previousX, previousY);
        ddz.setForegroundRoutine(FG_REDRAW);
    }

    /** {@code sub_59672} and the {@code Draw_TileColumn/Row} tracking words. */
    private static void roundForDraw(DdzZoneRuntimeState ddz) {
        ddz.setForegroundRounded(ddz.foregroundX() & 0xFFF0, ddz.foregroundY());
    }

    /** Whether layout layer 0 is empty across {@code Refresh_PlaneFull}'s 512x256 draw at (0,0). */
    private static boolean initialPlaneBlank() {
        var levelManager = GameServices.levelOrNull();
        var level = levelManager == null ? null : levelManager.getCurrentLevel();
        if (level == null || level.getMap() == null) {
            return false;
        }
        int size = level.getBlockPixelSize();
        int columns = Math.min((0x200 + size - 1) / size, level.getLayerWidthBlocks(0));
        int rows = Math.min((0x100 + size - 1) / size, level.getLayerHeightBlocks(0));
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                if ((level.getMap().getValue(0, x, y) & 0xFF) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /** {@code sub_596EA}. */
    static void buildSpeedTable(ScrollValueTable table, int backgroundScroll) {
        table.clear();
        // move.w (Events_bg+$06).w,d0 / swap d0 / clr.w d0 / asr.l #4,d0
        int speed = (((short) backgroundScroll) << 16) >> 4;
        int step = speed >> 3;
        for (int i = SPEED_WORDS; i >= 1; i--) {
            table.set(i, (short) (speed >> 16));
            speed -= step;
        }
    }

    @Override
    public short getVscrollFactorFG() {
        return foregroundVscroll;
    }
}
