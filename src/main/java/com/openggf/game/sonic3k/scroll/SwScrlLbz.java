package com.openggf.game.sonic3k.scroll;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.scroll.AbstractZoneScrollHandler;
import com.openggf.level.scroll.compose.DeformationPlan;
import com.openggf.level.scroll.compose.ScrollEffectComposer;
import com.openggf.level.scroll.compose.ScrollValueTable;
import com.openggf.level.scroll.compose.WaterlineBlendComposer;

import static com.openggf.level.scroll.M68KMath.negWord;

/**
 * Launch Base Zone scroll handler for Sonic 3K.
 *
 * <p>Ports the normal LBZ1/LBZ2 background deformation paths from the S3K
 * disassembly. The late LBZ2 Death Egg launch path switches to the event-owned
 * Death Egg deformation table while the LBZ launch runtime state is active.
 */
public class SwScrlLbz extends AbstractZoneScrollHandler {
    private static final int DEFAULT_BG_PERIOD_WIDTH = 512;
    private static final int VISIBLE_SCREEN_WIDTH_PX = 320;
    private static final int MAX_BG_PERIOD_WIDTH = 8192;

    private static final int[] LBZ1_BG_DEFORM = {
            0xD0, 0x18, 8, 8, 0x7FFF
    };

    private static final int[] LBZ2_BG_DEFORM = {
            0xC0, 0x40, 0x38, 0x18, 0x28, 0x10, 0x10, 0x10, 0x18,
            0x40, 0x20, 0x10, 0x20, 0x70, 0x30, 0x80E0, 0x20, 0x7FFF
    };

    private static final int[] LBZ2_DEATH_EGG_BG_DEFORM = {
            0x38, 0x18, 0x28, 0x10, 0x10, 0x10, 0x18, 0x40,
            0x38, 0x18, 0x28, 0x10, 0x10, 0x10, 0x18, 0x40,
            0x20, 0x10, 0x20, 0x70, 0x60, 0x10, 0x805F, 0x7FFF
    };

    private static final int[] LBZ2_CLOUD_DEFORM_OFFSETS = {
            0x16, 0x0E, 0x0A, 0x14, 0x0C, 0x06, 0x18, 0x10, 0x12, 0x02, 0x08, 0x04, 0x00
    };

    private static final int[] LBZ2_BG_UNDERWATER_DEFORM_RANGE = {
            7, 1, 3, 1, 7
    };

    private static final short[] LBZ_WATER_WAVE_ARRAY = {
            1, 1, 1, 0, 0, 0, -1, -1, -1, -1, -1, -1, 0, 0, 0, 1,
            1, 1, 1, 1, 1, 0, -1, -2, -2, -1, 0, 2, 2, 2, 2, 0,
            0, 0, -1, -1, -1, -1, -1, -1, 0, 0, 0, 1, 1, 1, 1, 1,
            1, 0, 0, 0, -1, -1, -1, -1, -1, -1, 0, 0, 0, 1, 1, 1
    };

    private static final DeformationPlan.ScrollValueTransform NEGATE_WORD = value -> negWord(value);
    private static final WaterlineBlendComposer WATERLINE_BLEND = new WaterlineBlendComposer(
            79,
            143,
            207,
            0x40
    );

    private final ScrollEffectComposer composer = new ScrollEffectComposer();
    private final ScrollValueTable lbz1HScroll = ScrollValueTable.ofLength(9);
    private final ScrollValueTable lbz2HScroll = ScrollValueTable.ofLength(242);
    private final byte[] waterlineData;

    private int cloudAccumulator;
    private int lastBgCameraX = Integer.MIN_VALUE;
    private int screenShakeOffset;
    private int currentBgPeriodWidth = DEFAULT_BG_PERIOD_WIDTH;
    private short vscrollFactorFG;

    public SwScrlLbz() {
        this(null);
    }

    public SwScrlLbz(byte[] waterlineData) {
        this.waterlineData = waterlineData;
    }

    public void setScreenShakeOffset(int screenShakeOffset) {
        this.screenShakeOffset = screenShakeOffset;
    }

    @Override
    public int getBgCameraX() {
        return lastBgCameraX;
    }

    @Override
    public int getShakeOffsetY() {
        return screenShakeOffset;
    }

    @Override
    public short getVscrollFactorFG() {
        return vscrollFactorFG;
    }

    short getLbz2HScrollWordForTest(int index) {
        return lbz2HScroll.get(index);
    }

    @Override
    public void update(int[] horizScrollBuf,
                       int cameraX,
                       int cameraY,
                       int frameCounter,
                       int actId) {
        resetScrollTracking();
        composer.reset();
        LbzZoneRuntimeState runtimeState = currentRuntimeState();
        if (runtimeState != null) {
            screenShakeOffset = runtimeState.consumeScreenShakeOffset();
        }

        short fgScroll = negWord(cameraX);
        if (actId == 0) {
            updateAct1(cameraX, cameraY, fgScroll);
        } else if (runtimeState != null && runtimeState.isLaunchActive()) {
            updateAct2DeathEgg(cameraX, cameraY, frameCounter, fgScroll, runtimeState);
        } else {
            updateAct2(cameraX, cameraY, frameCounter, fgScroll);
        }

        if (screenShakeOffset != 0) {
            composer.setVscrollFactorBG((short) (composer.getVscrollFactorBG() + screenShakeOffset));
            composer.setVscrollFactorFG((short) (cameraY + screenShakeOffset));
        }

        composer.copyPackedScrollWordsTo(horizScrollBuf);
        currentBgPeriodWidth = computeBgPeriodWidth(horizScrollBuf);
        vscrollFactorBG = composer.getVscrollFactorBG();
        vscrollFactorFG = composer.getVscrollFactorFG();
        minScrollOffset = composer.getMinScrollOffset();
        maxScrollOffset = composer.getMaxScrollOffset();
    }

    private LbzZoneRuntimeState currentRuntimeState() {
        if (!GameServices.hasRuntime()) {
            return null;
        }
        return S3kRuntimeStates.currentLbz(GameServices.zoneRuntimeRegistry()).orElse(null);
    }

    @Override
    public int getBgPeriodWidth() {
        return currentBgPeriodWidth;
    }

    private void updateAct1(int cameraX, int cameraY, short fgScroll) {
        lbz1HScroll.clear();

        int bgY = asrSignedWord(cameraY, 4);
        composer.setVscrollFactorBG((short) bgY);

        int fineX = fixedFromWord(cameraX) >> 4;
        int halfFineX = fineX >> 1;
        int bgX = wordFromFixed(fineX);
        int firstBand = wordFromFixed(halfFineX);

        lastBgCameraX = (short) (bgX + 0x0A);
        lbz1HScroll.set(0, (short) (bgX + 0x0A));
        lbz1HScroll.set(4, (short) (bgX + 0x0A));

        int value = fineX + halfFineX + halfFineX;
        int step = halfFineX >> 2;
        for (int index = 5; index <= 8; index++) {
            lbz1HScroll.set(index, wordFromFixed(value));
            value += step;
        }

        lbz1HScroll.set(5, (short) (lbz1HScroll.get(5) + 4));
        lbz1HScroll.set(6, (short) (lbz1HScroll.get(6) - 2));
        lbz1HScroll.set(7, (short) (lbz1HScroll.get(7) + 7));

        DeformationPlan.applyTableBands(
                composer,
                composer.getVscrollFactorBG(),
                fgScroll,
                lbz1HScroll,
                LBZ1_BG_DEFORM,
                4,
                NEGATE_WORD);
    }

    private void updateAct2(int cameraX, int cameraY, int frameCounter, short fgScroll) {
        lbz2HScroll.clear();

        int relativeY = (short) (cameraY - 0x5F0);
        int bgYFixed = fixedFromWord(relativeY) >> 1;
        int step = bgYFixed >> 3;
        bgYFixed -= step;
        bgYFixed -= step >> 2;
        int bgYWithoutBase = wordFromFixed(bgYFixed);
        int equilibriumDelta = (short) (bgYWithoutBase - relativeY);
        composer.setVscrollFactorBG((short) (bgYWithoutBase + 0x2C0));

        int cameraXFixed = fixedFromWord(cameraX);
        buildWaterlineGradient(cameraXFixed, equilibriumDelta);
        buildUnderwaterBands(cameraXFixed, equilibriumDelta);
        buildCloudBands(cameraXFixed);
        buildLowerBackgroundBands(cameraXFixed, equilibriumDelta);
        applyWaterWaves(equilibriumDelta, frameCounter);

        DeformationPlan.applyFlaggedTableBands(
                composer,
                composer.getVscrollFactorBG(),
                fgScroll,
                lbz2HScroll,
                LBZ2_BG_DEFORM,
                0,
                NEGATE_WORD);
    }

    private void updateAct2DeathEgg(int cameraX,
                                    int cameraY,
                                    int frameCounter,
                                    short fgScroll,
                                    LbzZoneRuntimeState runtimeState) {
        lbz2HScroll.clear();

        int adjustedCameraY = cameraY
                - screenShakeOffset
                - (runtimeState.getFgAccum() >> 16)
                - (runtimeState.getBgAccum() >> 16);
        int relativeY = (short) (adjustedCameraY - 0x5F0);
        int bgYFixed = fixedFromWord(relativeY) >> 1;
        int step = bgYFixed >> 3;
        bgYFixed -= step;
        bgYFixed -= step >> 2;
        int bgYWithoutBase = wordFromFixed(bgYFixed);
        int equilibriumDelta = (short) (bgYWithoutBase - relativeY);
        int bgY = (short) (bgYWithoutBase + 0x2C0 - (runtimeState.getFgAccum() >> 16) + screenShakeOffset);
        int latch = runtimeState.getDeathEggDeformWrapLatch();
        if (bgY < 0) {
            int wrap = 0;
            int wrapped = bgY;
            while (wrapped < 0) {
                wrap += 0x100;
                wrapped += 0x100;
            }
            if (wrap >= latch) {
                latch = wrap;
                runtimeState.setDeathEggDeformWrapLatch(latch);
            }
        }
        bgY = (short) (bgY + latch);
        if (latch != 0) {
            while (bgY >= 0x100) {
                bgY -= 0x100;
            }
        }
        composer.setVscrollFactorBG((short) bgY);

        int cameraXFixed = fixedFromWord(cameraX);
        buildDeathEggUpperGradient(cameraXFixed);
        buildDeathEggUnderwaterBands(cameraXFixed, equilibriumDelta);
        buildDeathEggCloudBands(cameraXFixed);
        buildDeathEggLowerBackgroundBands(cameraXFixed);
        applyDeathEggWaterWaves(equilibriumDelta, frameCounter);

        DeformationPlan.applyFlaggedTableBands(
                composer,
                composer.getVscrollFactorBG(),
                fgScroll,
                lbz2HScroll,
                LBZ2_DEATH_EGG_BG_DEFORM,
                0,
                NEGATE_WORD);
    }

    private void buildDeathEggUpperGradient(int cameraXFixed) {
        int value = cameraXFixed;
        int step = cameraXFixed >> 6;
        step -= step >> 3;
        int index = 0x0AC / 2;
        for (int i = 0; i < 0x20 && index > 1; i++) {
            lbz2HScroll.set(--index, wordFromFixed(value));
            value -= step;
            lbz2HScroll.set(--index, wordFromFixed(value));
            value -= step;
        }
    }

    private void buildDeathEggUnderwaterBands(int cameraXFixed, int equilibriumDelta) {
        int value = cameraXFixed >> 1;
        int step = value >> 3;
        lastBgCameraX = wordFromFixed(value);
        for (int i = 0; i < 7; i++) {
            value -= step;
        }

        int count = 0x60 - 1 - equilibriumDelta;
        if (count < 0) {
            return;
        }
        int index = 0x0EC / 2;
        short word = wordFromFixed(value);
        for (int i = 0; i <= count && index > 0; i++) {
            lbz2HScroll.set(--index, word);
        }
    }

    private void buildDeathEggCloudBands(int cameraXFixed) {
        int value = cameraXFixed >> 6;
        int step = value;
        int cloudPhase = cloudAccumulator;
        cloudAccumulator += 0xE00;
        int baseIndex = 0x00C / 2;

        for (int offset : LBZ2_CLOUD_DEFORM_OFFSETS) {
            value += cloudPhase;
            int index = baseIndex + (offset / 2);
            if (index >= 0 && index < lbz2HScroll.size()) {
                lbz2HScroll.set(index, wordFromFixed(value));
            }
            value += step;
        }
        for (int i = 0; i < 8; i++) {
            lbz2HScroll.set(i, lbz2HScroll.get(8 + i));
        }
    }

    private void buildDeathEggLowerBackgroundBands(int cameraXFixed) {
        int value = cameraXFixed >> 4;
        int step = value >> 1;
        int index = 0x026 / 2;

        for (int i = 0; i < 3 && index < lbz2HScroll.size(); i++) {
            lbz2HScroll.set(index++, wordFromFixed(value));
            value += step;
        }
    }

    private void applyDeathEggWaterWaves(int equilibriumDelta, int frameCounter) {
        int count = 0x40 - 1 - equilibriumDelta + 0x20;
        if (count < 0) {
            return;
        }
        if (count >= 0x60) {
            count = 0x60 - 1;
        }

        int waveIndex = (frameCounter >> 1) & 0x3F;
        int tableIndex = 0x0EC / 2;
        for (int i = 0; i <= count && tableIndex > 0; i++) {
            waveIndex = (waveIndex - 1) & 0x3F;
            tableIndex--;
            lbz2HScroll.set(tableIndex, (short) (lbz2HScroll.get(tableIndex) + LBZ_WATER_WAVE_ARRAY[waveIndex]));
        }
    }

    private void buildWaterlineGradient(int cameraXFixed, int equilibriumDelta) {
        if (equilibriumDelta == 0) {
            return;
        }

        int value = cameraXFixed;
        int gradientStep = (cameraXFixed >> 6) - (cameraXFixed >> 9);
        if (equilibriumDelta <= -0x40) {
            fillForwardGradient(15, value, gradientStep, 64);
            return;
        }

        fillBackwardGradient(143, value, gradientStep, 64);
        if (equilibriumDelta < 0x40) {
            WATERLINE_BLEND.apply(
                    lbz2HScroll,
                    (short) equilibriumDelta,
                    lbz2HScroll.get(78),
                    lbz2HScroll.get(143),
                    waterlineData);
        }
    }

    private void fillForwardGradient(int startIndex, int value, int step, int count) {
        int index = startIndex;
        for (int i = 0; i < count && index < lbz2HScroll.size(); i++) {
            lbz2HScroll.set(index++, wordFromFixed(value));
            value -= step;
        }
    }

    private void fillBackwardGradient(int endExclusive, int value, int step, int count) {
        int index = endExclusive;
        for (int i = 0; i < count && index > 0; i++) {
            lbz2HScroll.set(--index, wordFromFixed(value));
            value -= step;
        }
    }

    private void buildUnderwaterBands(int cameraXFixed, int equilibriumDelta) {
        int value = cameraXFixed >> 1;
        int step = value >> 3;
        lastBgCameraX = wordFromFixed(value);

        int index = 241;
        lbz2HScroll.set(--index, wordFromFixed(value));
        value -= step;
        lbz2HScroll.set(--index, wordFromFixed(value));

        for (int range : LBZ2_BG_UNDERWATER_DEFORM_RANGE) {
            value -= step;
            short word = wordFromFixed(value);
            for (int i = 0; i <= range && index > 0; i++) {
                for (int copy = 0; copy < 4 && index > 0; copy++) {
                    lbz2HScroll.set(--index, word);
                }
            }
        }

        int fillCount = 0x3F;
        if (equilibriumDelta >= 0) {
            fillCount -= equilibriumDelta;
            if (fillCount < 0) {
                return;
            }
        }
        short word = wordFromFixed(value);
        for (int i = 0; i <= fillCount && index > 0; i++) {
            lbz2HScroll.set(--index, word);
        }
    }

    private void buildCloudBands(int cameraXFixed) {
        int value = cameraXFixed >> 6;
        int step = value;
        int cloudPhase = cloudAccumulator;
        cloudAccumulator += 0xE00;

        for (int offset : LBZ2_CLOUD_DEFORM_OFFSETS) {
            value += cloudPhase;
            lbz2HScroll.set(offset / 2, wordFromFixed(value));
            value += step;
        }
    }

    private void buildLowerBackgroundBands(int cameraXFixed, int equilibriumDelta) {
        int value = cameraXFixed >> 4;
        int step = value >> 1;
        int index = 0x1A / 2;

        lbz2HScroll.set(index++, wordFromFixed(value));
        value += step;
        lbz2HScroll.set(index++, wordFromFixed(value));

        int count;
        if (equilibriumDelta < 0) {
            count = 0x40 - 1 + equilibriumDelta;
            if (count < 0) {
                return;
            }
            if (count >= 0x30) {
                count -= 0x30;
            } else {
                fillCountedPairs(index, value, 0x18 - 1);
                value += step;
                index += 0x18 * 2;
            }
        } else {
            count = 0x10 - 1;
            fillCountedPairs(index, value, 0x18 - 1);
            value += step;
            index += 0x18 * 2;
        }

        short word = wordFromFixed(value);
        for (int i = 0; i <= count && index < lbz2HScroll.size(); i++) {
            lbz2HScroll.set(index++, word);
        }
    }

    private void fillCountedPairs(int startIndex, int fixedValue, int dbfCount) {
        int index = startIndex;
        short word = wordFromFixed(fixedValue);
        for (int i = 0; i <= dbfCount && index + 1 < lbz2HScroll.size(); i++) {
            lbz2HScroll.set(index++, word);
            lbz2HScroll.set(index++, word);
        }
    }

    private void applyWaterWaves(int equilibriumDelta, int frameCounter) {
        int count = 0x3F - equilibriumDelta;
        if (count < 0) {
            return;
        }
        count += 0x60;
        if (count >= 0xE0) {
            count = 0xE0 - 1;
        }

        int waveIndex = (frameCounter >> 1) & 0x3F;
        int tableIndex = 0x1DE / 2;
        for (int i = 0; i <= count && tableIndex > 0; i++) {
            waveIndex = (waveIndex - 1) & 0x3F;
            tableIndex--;
            lbz2HScroll.set(tableIndex, (short) (lbz2HScroll.get(tableIndex) + LBZ_WATER_WAVE_ARRAY[waveIndex]));
        }
    }

    private static int fixedFromWord(int value) {
        return ((short) value) << 16;
    }

    private static short wordFromFixed(int fixed) {
        return (short) (fixed >> 16);
    }

    private static int asrSignedWord(int value, int shift) {
        return ((short) value) >> shift;
    }

    private static int computeBgPeriodWidth(int[] packedHScroll) {
        int minBgScroll = Integer.MAX_VALUE;
        int maxBgScroll = Integer.MIN_VALUE;
        for (int packed : packedHScroll) {
            int bgScroll = (short) packed;
            if (bgScroll < minBgScroll) {
                minBgScroll = bgScroll;
            }
            if (bgScroll > maxBgScroll) {
                maxBgScroll = bgScroll;
            }
        }
        if (minBgScroll == Integer.MAX_VALUE) {
            return DEFAULT_BG_PERIOD_WIDTH;
        }

        int requiredWidth = VISIBLE_SCREEN_WIDTH_PX + (maxBgScroll - minBgScroll);
        int width = DEFAULT_BG_PERIOD_WIDTH;
        while (width < requiredWidth && width < MAX_BG_PERIOD_WIDTH) {
            width <<= 1;
        }
        return Math.min(width, MAX_BG_PERIOD_WIDTH);
    }
}
