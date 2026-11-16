package com.openggf.game.sonic3k.scroll;

import com.openggf.game.sonic3k.events.Sonic3kMGZEvents.ScreenPublication;
import java.util.function.Supplier;

import static com.openggf.level.scroll.M68KMath.negWord;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.MgzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.scroll.AbstractZoneScrollHandler;
import com.openggf.level.scroll.compose.DeformationPlan;
import com.openggf.level.scroll.compose.ScatterFillPlan;
import com.openggf.level.scroll.compose.ScrollEffectComposer;
import com.openggf.level.scroll.compose.ScrollValueTable;

/**
 * Marble Garden Zone (MGZ) scroll handler for Sonic 3K.
 *
 * <p>Ports MGZ1_Deform / MGZ2_BGDeform (normal path) from the S3K disassembly to produce real
 * per-line parallax rather than a flat fallback ratio.
 */
public class SwScrlMgz extends AbstractZoneScrollHandler
        implements com.openggf.level.scroll.ScrollProjectionSource,
        com.openggf.level.scroll.ScrollOutputRebuilder,
        com.openggf.game.internal.ExplicitForegroundVerticalScroll {
    private static final int BG_RISE_SONIC_STATE = 8;
    private static final int BG_RISE_AFTER_MOVE_STATE = 0xC;
    private static final int MGZ2_SONIC_RISE_Y_BASE = 0x8F0;
    private static final int MGZ2_SONIC_RISE_X_BASE = 0x3200;
    private static final int MGZ2_AFTER_MOVE_Y_BASE = 0x500;
    private short vscrollFactorFG;
    private int lastBgCameraX = Integer.MIN_VALUE;
    private int screenShakeOffset;
    private ScreenPublication composedPublication;
    private final Supplier<ScreenPublication> publication;

    @Override public int getShakeOffsetY() { return screenShakeOffset; }
    @Override public short getVscrollFactorFG() { return vscrollFactorFG; }
    @Override public int getBgCameraX() { return lastBgCameraX; }
    @Override public boolean foregroundVerticalScrollExplicit() { return true; }
    @Override public void init(int act, int x, int y) { update(new int[224], x, y, 0, act); }
    @Override public void rebuildOutput(int[] output, int x, int y, int frame, int act) {
        update(output, x, y, frame, act);
    }

    private static final int HSCROLL_WORD_COUNT = 32;

    // MGZ1_BGDeformArray
    private static final int[] MGZ1_BG_DEFORM = {
            0x10, 0x04, 0x04, 0x08, 0x08, 0x08, 0x0D, 0x13, 0x08, 0x08, 0x08, 0x08, 0x18, 0x7FFF
    };

    // MGZ2_BGDeformArray
    private static final int[] MGZ2_BG_DEFORM = {
            0x10, 0x10, 0x10, 0x10, 0x10, 0x18, 0x08, 0x10, 0x08, 0x08, 0x10, 0x08,
            0x08, 0x08, 0x05, 0x2B, 0x0C, 0x06, 0x06, 0x08, 0x08, 0x18, 0xD8, 0x7FFF
    };

    // MGZ2_BGDeformIndex
    private static final int[] MGZ2_BG_DEFORM_INDEX = {
            0x1C, 0x18, 0x1A, 0x0C, 0x06, 0x14, 0x02, 0x10, 0x16, 0x12, 0x0A, 0x00, 0x08, 0x04, 0x0E
    };

    // MGZ2_BGDeformOffset
    private static final int[] MGZ2_BG_DEFORM_OFFSET = {
            -5, -8, 9, 10, 2, -12, 3, 16, -1, 13, -15, 6, -11, -4, 14,
            -8, 16, 8, 0, -8, 16, 8, 0
    };

    private static final ScatterFillPlan MGZ2_SCATTER_FILL = new ScatterFillPlan(
            18, 16, 17, 10, 7, 14, 5, 12, 15, 13, 9, 4, 8, 6, 11
    );
    private static final DeformationPlan.ScrollValueTransform NEGATE_WORD = value -> negWord(value);

    // ROM accumulators that live in HScroll_table longwords and persist frame-to-frame.
    private final ScrollEffectComposer composer;

    public SwScrlMgz() { this(null, 224); }

    public SwScrlMgz(Supplier<ScreenPublication> publication) {
        this(publication, 224);
    }

    private SwScrlMgz(Supplier<ScreenPublication> publication, int lines) {
        this.publication = publication;
        composer = new ScrollEffectComposer(lines);
    }

    private final ScrollValueTable mgz1HScrollTable = ScrollValueTable.ofLength(HSCROLL_WORD_COUNT);
    private final ScrollValueTable mgz2HScrollTable = ScrollValueTable.ofLength(HSCROLL_WORD_COUNT);
    private final ScrollValueTable mgz2ScatterSource = ScrollValueTable.ofLength(MGZ2_BG_DEFORM_INDEX.length);
    @Override
    public void update(int[] horizScrollBuf,
                       int cameraX,
                       int cameraY,
                       int frameCounter,
                       int actId) {
        var state = currentPublication();
        composedPublication = state;
        if (state.valid()) {
            cameraX = state.cameraX();
            cameraY = state.cameraY();
        }
        int bgRiseRoutine = state.riseRoutine();
        int bgRiseOffset = state.riseOffset();
        int bossBgScrollOffset = state.bossOffset();
        int cloud = state.cloudPhase();
        screenShakeOffset = state.shakeOffset();
        composer.reset();
        composer.setVscrollFactorFG((short) (cameraY + screenShakeOffset));
        short fgScroll = negWord(cameraX);

        if (actId == 0) {
            lastBgCameraX = Integer.MIN_VALUE;
            composer.setVscrollFactorBG((short) 0);
            composeMgz1Table(cameraX, cloud, mgz1HScrollTable);
            DeformationPlan.applyTableBands(
                    composer,
                    0,
                    fgScroll,
                    mgz1HScrollTable,
                    MGZ1_BG_DEFORM,
                    0,
                    NEGATE_WORD);
        } else if (bgRiseRoutine == BG_RISE_SONIC_STATE) {
            // State 8 (Sonic rise): MGZ2_BGDeform still runs the cloud/scatter
            // parallax builder, but it seeds the final deform slot with the
            // 1:1 terrain lock value (Camera_X_pos_BG_copy = cameraX - $3200).
            // That keeps the clouds drifting while the terrain band scrolls
            // 1:1 with the lift.
            int parallaxCameraX = (bossBgScrollOffset == Integer.MIN_VALUE ? cameraX : bossBgScrollOffset);
            int bgY = ((short) cameraY) - MGZ2_SONIC_RISE_Y_BASE + bgRiseOffset;
            int bgScrollBaseX = ((short) cameraX) - MGZ2_SONIC_RISE_X_BASE;
            // Expose to dual-path collision so probes at world X≈$3800
            // translate into the BG layout's populated 0..23 range.
            lastBgCameraX = bgScrollBaseX;
            composer.setVscrollFactorBG((short) bgY);
            composeMgz2Table(parallaxCameraX, cloud, mgz2HScrollTable, mgz2ScatterSource);
            mgz2HScrollTable.set(27, (short) bgScrollBaseX);
            DeformationPlan.applyTableBands(
                    composer,
                    bgY,
                    fgScroll,
                    mgz2HScrollTable,
                    MGZ2_BG_DEFORM,
                    4,
                    NEGATE_WORD);
        } else {
            // State 0 (normal MGZ2 play) uses the drifting cloud parallax.
            // State C (after-move) freezes cloud movement and shifts the
            // parallax origin by $500, matching MGZ2_BGDeform's d1 preload.
            int bgY = bgRiseRoutine == BG_RISE_AFTER_MOVE_STATE
                    ? computeMgz2BgY(cameraY - MGZ2_AFTER_MOVE_Y_BASE)
                    : computeMgz2BgY(cameraY);
            // loc_23D220 clears Camera_X_pos_BG_copy in both normal state 0
            // and after-move state $C. The foreground/boss cursor still feeds
            // HScroll_table separately through getMgz2ParallaxCameraX().
            lastBgCameraX = 0;
            composer.setVscrollFactorBG((short) bgY);
            composeMgz2Table((bossBgScrollOffset == Integer.MIN_VALUE ? cameraX : bossBgScrollOffset), cloud,
                    mgz2HScrollTable, mgz2ScatterSource);
            DeformationPlan.applyTableBands(
                    composer,
                    bgY,
                    fgScroll,
                    mgz2HScrollTable,
                    MGZ2_BG_DEFORM,
                    4,
                    NEGATE_WORD);
        }

        // Screen shake: apply the same camera rumble offset to both planes so the
        // BG cloud/floor strip tracks the shaken viewport instead of staying fixed.
        if (screenShakeOffset != 0) {
            composer.setVscrollFactorBG((short) (composer.getVscrollFactorBG() + screenShakeOffset));
            composer.setVscrollFactorFG((short) (cameraY + screenShakeOffset));
        }

        composer.copyPackedScrollWordsTo(horizScrollBuf);
        minScrollOffset = composer.getMinScrollOffset();
        maxScrollOffset = composer.getMaxScrollOffset();
        vscrollFactorBG = composer.getVscrollFactorBG();
        vscrollFactorFG = composer.getVscrollFactorFG();
    }

    private ScreenPublication currentPublication() {
        if (publication != null) return publication.get();
        if (GameServices.hasRuntime()) {
            var runtime = S3kRuntimeStates.currentMgz(GameServices.zoneRuntimeRegistry()).orElse(null);
            if (runtime != null) return runtime.screenPublication();
        }
        return new ScreenPublication(0, 0, Integer.MIN_VALUE, 0, 0);
    }

    private static void composeMgz1Table(int cameraX, int cloudPhase, ScrollValueTable table) {
        table.clear();
        int d0 = ((short) cameraX) << 16;
        d0 >>= 2;

        int d1 = d0 >> 4;

        int a1 = 14; // HScroll_table+$01C word index
        for (int i = 0; i < 9; i++) {
            table.set(--a1, (short) (d0 >> 16));
            d0 -= d1;
        }

        int d2 = cloudPhase;

        d0 >>= 1;
        a1 = 0;
        for (int i = 0; i < 5; i++) {
            d0 += d2;
            d2 += 0x500;
            table.set(a1++, (short) (d0 >> 16));
            d0 += d1;
        }

        // move.w -2(a1),d0 / move.w -4(a1),-2(a1) / move.w d0,-4(a1)
        short swap = table.get(9);
        table.set(9, table.get(8));
        table.set(8, swap);
    }

    private static void composeMgz2Table(
            int cameraX, int cloudPhase,
                                       ScrollValueTable table,
                                       ScrollValueTable scatterSource) {
        table.clear();
        scatterSource.clear();
        int d0 = ((short) cameraX) << 16;
        d0 >>= 1;

        int d1 = d0 >> 3;
        int d2 = d1 >> 2;

        int a1 = 27; // HScroll_table+$036 word index
        for (int i = 0; i < 8; i++) {
            table.set(--a1, (short) (d0 >> 16));
            d0 -= d1;
        }

        // While auto-moving, the accumulator advances with the frame counter;
        // on the BG-rise freeze the ROM zeroes it, so the frozen value is 0.
        int cloudAcc = cloudPhase;
        int d0Cloud = d2;
        int d2Step = d2 >> 1;
        for (int i = 0; i < MGZ2_BG_DEFORM_INDEX.length; i++) {
            d0Cloud += cloudAcc;
            scatterSource.set(i, (short) (d0Cloud >> 16));
            d0Cloud += d2Step;
        }
        MGZ2_SCATTER_FILL.apply(scatterSource, table);

        for (int i = 0; i < MGZ2_BG_DEFORM_OFFSET.length; i++) {
            int idx = 4 + i;
            if (idx >= 0 && idx < table.size()) {
                table.set(idx, (short) (table.get(idx) + MGZ2_BG_DEFORM_OFFSET[i]));
            }
        }
    }

    /**
     * ROM: MGZ2_BGDeform state 8 still executes the cloud/scatter fill from
     * {@code loc_23D24C..loc_23D2B4}, but the state-8 prelude seeds
     * {@code HScroll_table+$036} with {@code Camera_X_pos_BG_copy}. That final
     * slot feeds the locked terrain band while the earlier slots retain the
     * normal cloud parallax.
     */
    private int computeMgz2BgY(int cameraY) {
        int d0 = ((short) cameraY) << 16;
        d0 >>= 4;
        int d1 = d0;
        d0 += d0;
        d0 += d1;
        return (short) (d0 >> 16);
    }

    @Override
    public com.openggf.level.scroll.ScrollProjectionPhase captureProjection(
            com.openggf.level.scroll.ScrollProjectionContext context) {
        var state = composedPublication != null ? composedPublication : currentPublication();
        int rise = state.riseRoutine(), riseY = state.riseOffset(), parallaxOverride = state.bossOffset();
        int shake = state.shakeOffset();
        int cloud = state.cloudPhase();
        return view -> {
            SwScrlMgz scratch = new SwScrlMgz(null, view.height());
            int bgX, bgY;
            short foreground = negWord(view.cameraX());
            if (context.act() == 0) {
                bgX = Integer.MIN_VALUE;
                bgY = 0;
                composeMgz1Table(view.cameraX(), cloud, scratch.mgz1HScrollTable);
                DeformationPlan.applyTableBands(
                        scratch.composer,
                        bgY,
                        foreground,
                        scratch.mgz1HScrollTable,
                        MGZ1_BG_DEFORM,
                        0,
                        NEGATE_WORD);
            } else {
                int sourceX =
                        parallaxOverride == Integer.MIN_VALUE
                                ? view.cameraX()
                                : parallaxOverride + view.cameraX() - context.cameraX();
                if (rise == BG_RISE_SONIC_STATE) {
                    bgX = ((short) view.cameraX()) - MGZ2_SONIC_RISE_X_BASE;
                    bgY = ((short) view.cameraY()) - MGZ2_SONIC_RISE_Y_BASE + riseY;
                } else {
                    bgX = 0;
                    bgY =
                            scratch.computeMgz2BgY(
                                    rise == BG_RISE_AFTER_MOVE_STATE
                                            ? view.cameraY() - MGZ2_AFTER_MOVE_Y_BASE
                                            : view.cameraY());
                }
                composeMgz2Table(
                        sourceX, cloud, scratch.mgz2HScrollTable, scratch.mgz2ScatterSource);
                if (rise == BG_RISE_SONIC_STATE) scratch.mgz2HScrollTable.set(27, (short) bgX);
                DeformationPlan.applyTableBands(
                        scratch.composer,
                        bgY,
                        foreground,
                        scratch.mgz2HScrollTable,
                        MGZ2_BG_DEFORM,
                        4,
                        NEGATE_WORD);
            }
            return new com.openggf.level.scroll.ScrollProjection(
                    scratch.composer.packedScrollWords(),
                    null,
                    null,
                    null,
                    (short) (view.cameraY() + shake),
                    (short) (bgY + shake),
                    bgX,
                    context.nativeOutput().backgroundPeriod(),
                    context.nativeOutput().shakeX(),
                    shake,
                    context.nativeOutput().tilemapMode());
        };
    }
}
