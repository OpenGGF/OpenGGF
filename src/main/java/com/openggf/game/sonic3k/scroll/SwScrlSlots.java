package com.openggf.game.sonic3k.scroll;

import static com.openggf.level.scroll.M68KMath.negWord;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kBonusStageCoordinator;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotBonusStageRuntime;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.LevelManager;
import com.openggf.level.scroll.AbstractZoneScrollHandler;
import com.openggf.level.scroll.compose.ScrollEffectComposer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * ROM-shaped Slot Machine bonus-stage screen/background handler.
 *
 * <p>This ports the state owned by Slots_ScreenInit/Event and Slots_BackgroundInit/Event into the
 * engine scroll handler rather than synthesizing a new wobble model in the runtime.
 */
public final class SwScrlSlots extends AbstractZoneScrollHandler
        implements com.openggf.level.scroll.ScrollOutputRebuilder,
                com.openggf.level.scroll.ScrollProjectionSource {
    private static final int VISIBLE_LINES = 224;
    private static final int SCREEN_ORIGIN = 0x400;
    private static final int SCREEN_STEP_LIMIT = 0x20;
    private static final int BG_SCROLL_STEP = 0x400;
    private static final int BG_SCROLL_LIMIT = 0x8000;
    private static final int BG_DEFORM_SENTINEL = 0x7FFF;
    private static final int BAND_COUNT = 8;
    private static final int DEFORM_HEIGHT_COUNT = 16;
    private final com.openggf.data.Rom suppliedRom;
    private com.openggf.game.sonic3k.bonusstage.slots.S3kSlotResources resources;
    private int[] deformHeights;
    private BandConfig[] bandConfigs;

    public SwScrlSlots() {
        suppliedRom = null;
    }

    SwScrlSlots(com.openggf.data.Rom rom) {
        suppliedRom = java.util.Objects.requireNonNull(rom);
    }

    public SwScrlSlots(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotResources resources) {
        suppliedRom = null;
        this.resources = java.util.Objects.requireNonNull(resources);
    }

    private void ensureResources() {
        if (bandConfigs != null) return;
        if (resources == null) {
            try {
                resources =
                        com.openggf.game.sonic3k.bonusstage.slots.S3kSlotResources.fromRom(
                                com.openggf.data.RomByteReader.fromRom(
                                        suppliedRom != null
                                                ? suppliedRom
                                                : GameServices.rom().getRom()));
            } catch (java.io.IOException | RuntimeException failure) {
                throw new IllegalStateException(
                        "Slot background requires its ROM resource bank", failure);
            }
        }
        deformHeights = resources.backgroundDeformHeights();
        bandConfigs =
                resources.backgroundBands().stream()
                        .map(
                                row ->
                                        new BandConfig(
                                                row.stepWord(),
                                                row.deltaLong(),
                                                row.resetCountdown(),
                                                row.phaseLimit(),
                                                row.destVramAddress(),
                                                row.oddPhaseDestDelta(),
                                                row.doubleRow(),
                                                row.sourceY(),
                                                row.longWordCount(),
                                                row.destVramAddress() != 0))
                        .toArray(BandConfig[]::new);
    }

    private final short[] perLineVScroll = new short[VISIBLE_LINES];
    private final ScrollEffectComposer composer = new ScrollEffectComposer();
    private final BandState[] bandStates = createBandStates();
    private final int[] bandScrollValues = new int[BAND_COUNT];
    private final int[] expandedBandScrollValues = new int[DEFORM_HEIGHT_COUNT];
    private final List<BgPlaneRowUpdate> lastBgPlaneRowUpdates = new ArrayList<>(8);

    private S3kSlotBonusStageRuntime activeRuntime;
    private boolean screenInitialized;
    private boolean backgroundInitialized;
    private int foregroundOriginX;
    private int foregroundOriginY;
    private int backgroundVelocity;
    private int backgroundCameraYFixed;
    private int lastForegroundOriginX;
    private int lastForegroundOriginY;
    private int lastBackgroundOriginX;
    private int lastBackgroundOriginY;

    @Override
    public void update(
            int[] horizScrollBuf, int cameraX, int cameraY, int frameCounter, int actId) {
        applyScrollState(horizScrollBuf, cameraX, cameraY, activeSlotRuntime());
    }

    void updateForTest(
            S3kSlotBonusStageRuntime runtime, int cameraX, int cameraY, int frameCounter) {
        applyScrollState(null, cameraX, cameraY, runtime);
    }

    void updateForTest(
            S3kSlotBonusStageRuntime runtime,
            int[] horizScrollBuf,
            int cameraX,
            int cameraY,
            int frameCounter) {
        applyScrollState(horizScrollBuf, cameraX, cameraY, runtime);
    }

    private void applyScrollState(
            int[] horizScrollBuf, int cameraX, int cameraY, S3kSlotBonusStageRuntime runtime) {
        ensureResources();
        resetScrollTracking();
        composer.reset();
        lastBgPlaneRowUpdates.clear();
        ensureRuntimeState(runtime, cameraX, cameraY);

        S3kSlotBonusStageRuntime.SlotVisualState visualState =
                runtime != null
                        ? runtime.slotVisualState()
                        : new S3kSlotBonusStageRuntime.SlotVisualState(0x10, 0x2D, 0x40, false);

        int targetX = (SCREEN_ORIGIN - visualState.eventsBgX()) + cameraX;
        int targetY = (SCREEN_ORIGIN - visualState.eventsBgY()) + cameraY;
        if (!screenInitialized) {
            foregroundOriginX = targetX;
            foregroundOriginY = targetY;
            screenInitialized = true;
        } else {
            foregroundOriginX = stepToward(foregroundOriginX, targetX, SCREEN_STEP_LIMIT);
            foregroundOriginY = stepToward(foregroundOriginY, targetY, SCREEN_STEP_LIMIT);
        }

        tickBackground(visualState.scalarIndex1());
        applyPendingBackgroundPlaneUpdates();

        lastForegroundOriginX = foregroundOriginX;
        lastForegroundOriginY = foregroundOriginY;
        lastBackgroundOriginX = foregroundOriginX + bandScrollValues[0];
        lastBackgroundOriginY = backgroundCameraY();
        composer.setVscrollFactorBG((short) backgroundCameraY());
        Arrays.fill(perLineVScroll, (short) 0);

        if (horizScrollBuf != null) {
            fillPackedScrollBuffer(horizScrollBuf);
        } else {
            // Still publish vscroll factor + scroll bookkeeping when running in headless probes.
            vscrollFactorBG = composer.getVscrollFactorBG();
            minScrollOffset = composer.getMinScrollOffset();
            maxScrollOffset = composer.getMaxScrollOffset();
        }
    }

    private record BandRam(
            int accumulator, int countdown, int phase, int baseScroll, boolean pending) {}

    private record RewindState(
            boolean runtimeAttached,
            boolean screenInitialized,
            boolean backgroundInitialized,
            int foregroundX,
            int foregroundY,
            int backgroundVelocity,
            int backgroundYFixed,
            int lastForegroundX,
            int lastForegroundY,
            int lastBackgroundX,
            int lastBackgroundY,
            int[] bandScroll,
            int[] expandedScroll,
            List<BandRam> bands,
            List<BgPlaneRowUpdate> planeUpdates) {
        RewindState {
            bandScroll = bandScroll.clone();
            expandedScroll = expandedScroll.clone();
            bands = List.copyOf(bands);
            planeUpdates = List.copyOf(planeUpdates);
        }

        @Override
        public int[] bandScroll() {
            return bandScroll.clone();
        }

        @Override
        public int[] expandedScroll() {
            return expandedScroll.clone();
        }
    }

    @Override
    public Object captureRewindState() {
        var bands = new ArrayList<BandRam>(bandStates.length);
        for (var band : bandStates)
            bands.add(
                    new BandRam(
                            band.accumulator,
                            band.countdown,
                            band.phase,
                            band.baseScroll,
                            band.pendingAdvance));
        return new RewindState(
                activeRuntime != null,
                screenInitialized,
                backgroundInitialized,
                foregroundOriginX,
                foregroundOriginY,
                backgroundVelocity,
                backgroundCameraYFixed,
                lastForegroundOriginX,
                lastForegroundOriginY,
                lastBackgroundOriginX,
                lastBackgroundOriginY,
                bandScrollValues,
                expandedBandScrollValues,
                bands,
                lastBgPlaneRowUpdates);
    }

    @Override
    public void restoreRewindState(Object captured) {
        if (!(captured instanceof RewindState state))
            throw new IllegalArgumentException("Expected Slot scroll RAM");
        var runtime = state.runtimeAttached() ? activeSlotRuntime() : null;
        if (state.runtimeAttached() && runtime == null)
            throw new IllegalStateException("Slot scroll owner has retired");
        activeRuntime = runtime;
        screenInitialized = state.screenInitialized();
        backgroundInitialized = state.backgroundInitialized();
        foregroundOriginX = state.foregroundX();
        foregroundOriginY = state.foregroundY();
        backgroundVelocity = state.backgroundVelocity();
        backgroundCameraYFixed = state.backgroundYFixed();
        lastForegroundOriginX = state.lastForegroundX();
        lastForegroundOriginY = state.lastForegroundY();
        lastBackgroundOriginX = state.lastBackgroundX();
        lastBackgroundOriginY = state.lastBackgroundY();
        System.arraycopy(state.bandScroll(), 0, bandScrollValues, 0, bandScrollValues.length);
        System.arraycopy(
                state.expandedScroll(),
                0,
                expandedBandScrollValues,
                0,
                expandedBandScrollValues.length);
        for (int i = 0; i < bandStates.length; i++) {
            var value = state.bands().get(i);
            var band = bandStates[i];
            band.accumulator = value.accumulator();
            band.countdown = value.countdown();
            band.phase = value.phase();
            band.baseScroll = value.baseScroll();
            band.pendingAdvance = value.pending();
        }
        lastBgPlaneRowUpdates.clear();
        lastBgPlaneRowUpdates.addAll(state.planeUpdates());
    }

    @Override
    public void rebuildOutput(
            int[] horizontal, int cameraX, int cameraY, int frameCounter, int actId) {
        // Slots_ScreenEvent/BackgroundEvent own movement, accumulator increments
        // and plane writes. Restore reconstructs their last completed output only.
        ensureResources();
        resetScrollTracking();
        composer.reset();
        composer.setVscrollFactorBG((short) lastBackgroundOriginY);
        Arrays.fill(perLineVScroll, (short) 0);
        fillPackedScrollBuffer(horizontal);
    }

    @Override
    public short[] getPerLineVScrollBG() {
        return perLineVScroll;
    }

    int lastForegroundOriginXForTest() {
        return lastForegroundOriginX;
    }

    int lastForegroundOriginYForTest() {
        return lastForegroundOriginY;
    }

    int lastBackgroundOriginXForTest() {
        return lastBackgroundOriginX;
    }

    int lastBackgroundOriginYForTest() {
        return lastBackgroundOriginY;
    }

    int backgroundVelocityForTest() {
        return backgroundVelocity;
    }

    List<BgPlaneRowUpdate> lastBgPlaneRowUpdatesForTest() {
        return Collections.unmodifiableList(lastBgPlaneRowUpdates);
    }

    @Override
    public short getVscrollFactorFG() {
        return (short) foregroundOriginY;
    }

    private void ensureRuntimeState(S3kSlotBonusStageRuntime runtime, int cameraX, int cameraY) {
        if (runtime == activeRuntime) {
            return;
        }
        activeRuntime = runtime;
        screenInitialized = false;
        backgroundInitialized = false;
        foregroundOriginX = cameraX;
        foregroundOriginY = cameraY;
        backgroundVelocity = 0;
        backgroundCameraYFixed = 0;
        Arrays.fill(bandScrollValues, 0);
        Arrays.fill(expandedBandScrollValues, 0);
        for (BandState bandState : bandStates) {
            bandState.reset();
        }
    }

    private void tickBackground(int scalarIndex1) {
        if (!backgroundInitialized) {
            backgroundInitialized = true;
            backgroundVelocity = 0;
            backgroundCameraYFixed = 0;
            Arrays.fill(bandScrollValues, 0);
            expandBandScrollTable();
            return;
        }

        if (scalarIndex1 == 0) {
            expandBandScrollTable();
            return;
        }

        int step = scalarIndex1 < 0 ? -BG_SCROLL_STEP : BG_SCROLL_STEP;
        backgroundVelocity = clampSignedLong(backgroundVelocity + step, BG_SCROLL_LIMIT);
        backgroundCameraYFixed += backgroundVelocity;
        backgroundCameraYFixed &= 0x00FF_FFFF;

        boolean backgroundEventLatched = false;
        for (int i = 0; i < BAND_COUNT; i++) {
            backgroundEventLatched =
                    tickBand(bandStates[i], bandConfigs[i], backgroundEventLatched);
            // ROM loc_5997C copies word 0 of each $C-byte band state plus
            // word 8 into the deformation table. Word 0 is the high word of
            // the long accumulator updated at loc_598B4.
            bandScrollValues[i] =
                    toSignedWord(bandStates[i].accumulatorHighWord() + bandStates[i].baseScroll);
        }
        expandBandScrollTable();
    }

    private void fillPackedScrollBuffer(int[] horizScrollBuf) {
        short fgScroll = negWord(foregroundOriginX);
        int segmentIndex = 0;
        int segmentOffset = backgroundCameraY();
        while (segmentIndex < DEFORM_HEIGHT_COUNT - 1
                && segmentOffset >= deformHeights[segmentIndex]) {
            segmentOffset -= deformHeights[segmentIndex];
            segmentIndex++;
        }

        int line = 0;
        while (line < VISIBLE_LINES && segmentIndex < DEFORM_HEIGHT_COUNT) {
            int segmentLength = deformHeights[segmentIndex];
            if (segmentLength == BG_DEFORM_SENTINEL) {
                segmentLength = VISIBLE_LINES - line;
            } else {
                segmentLength -= segmentOffset;
            }
            if (segmentLength <= 0) {
                segmentIndex++;
                segmentOffset = 0;
                continue;
            }

            short bgScroll = negWord(expandedBandScrollValues[segmentIndex]);
            int linesToWrite = Math.min(segmentLength, VISIBLE_LINES - line);
            composer.fillPackedScrollWords(line, linesToWrite, fgScroll, bgScroll);
            line += linesToWrite;
            segmentIndex++;
            segmentOffset = 0;
        }

        composer.copyPackedScrollWordsTo(horizScrollBuf);
        vscrollFactorBG = composer.getVscrollFactorBG();
        minScrollOffset = composer.getMinScrollOffset();
        maxScrollOffset = composer.getMaxScrollOffset();
    }

    @Override
    public com.openggf.level.scroll.ScrollProjectionPhase captureProjection(
            com.openggf.level.scroll.ScrollProjectionContext context) {
        int[] heights = deformHeights.clone();
        int[] scrolls = expandedBandScrollValues.clone();
        int originX = foregroundOriginX, originY = foregroundOriginY;
        int bgY = backgroundCameraY();
        return view -> {
            int[] horizontal = new int[view.height()];
            short fg = negWord(originX + view.cameraX() - context.cameraX());
            int band = 0, skip = bgY;
            while (band < heights.length - 1 && skip >= heights[band]) skip -= heights[band++];
            int remaining = heights[band] - skip;
            for (int line = 0; line < horizontal.length; line++) {
                horizontal[line] = ((fg & 65535) << 16) | negWord(scrolls[band]) & 65535;
                if (--remaining == 0 && band < heights.length - 1) remaining = heights[++band];
            }
            var output = context.nativeOutput();
            return new com.openggf.level.scroll.ScrollProjection(
                    horizontal,
                    new short[view.height()],
                    null,
                    null,
                    (short) (originY + view.cameraY() - context.cameraY()),
                    (short) bgY,
                    output.backgroundX(),
                    output.backgroundPeriod(),
                    output.shakeX(),
                    output.shakeY(),
                    output.tilemapMode());
        };
    }

    private void expandBandScrollTable() {
        for (int i = 0; i < expandedBandScrollValues.length; i++) {
            expandedBandScrollValues[i] = bandScrollValues[i & 0x7];
        }
    }

    private int backgroundCameraY() {
        return (backgroundCameraYFixed >>> 16) & 0xFF;
    }

    private boolean tickBand(BandState state, BandConfig config, boolean backgroundEventLatched) {
        long sum = (state.accumulator & 0xFFFF_FFFFL) + (config.deltaLong & 0xFFFF_FFFFL);
        state.accumulator = (int) sum;
        boolean carry = (sum & 0x1_0000_0000L) != 0;
        if (carry) {
            state.setAccumulatorHighWord(state.accumulatorHighWord() + config.stepWord);
            state.pendingAdvance = true;
        } else if (state.accumulatorHighWord() >= config.stepWord) {
            state.setAccumulatorHighWord(state.accumulatorHighWord() - config.stepWord);
            state.pendingAdvance = true;
        }

        boolean blockedByFrameLatch = config.usesFrameLatch && backgroundEventLatched;
        if (blockedByFrameLatch) {
            return backgroundEventLatched;
        }

        state.countdown--;
        if (state.countdown < 0) {
            state.countdown = config.resetCountdown;
        } else if (!state.pendingAdvance) {
            return backgroundEventLatched;
        }

        state.pendingAdvance = false;
        state.phase++;
        if (state.phase >= config.phaseLimit) {
            state.phase = 0;
        }
        if (!config.usesFrameLatch) {
            state.baseScroll = toSignedWord(state.baseScroll + config.stepWord);
        } else if (config.hasPlaneUpdate()) {
            queuePlaneRowUpdate(state, config);
        }
        return true;
    }

    private void queuePlaneRowUpdate(BandState state, BandConfig config) {
        int destVramAddress = config.destVramAddress;
        if ((state.phase & 1) != 0) {
            destVramAddress += config.oddPhaseDestDelta;
        }
        int sourceX = toSignedWord(state.phase * config.stepWord);
        lastBgPlaneRowUpdates.add(
                new BgPlaneRowUpdate(
                        sourceX, config.sourceY, destVramAddress, config.longWordCount));
        if (config.doubleRow) {
            lastBgPlaneRowUpdates.add(
                    new BgPlaneRowUpdate(
                            sourceX,
                            config.sourceY + 0x10,
                            destVramAddress + 0x100,
                            config.longWordCount));
        }
    }

    private void applyPendingBackgroundPlaneUpdates() {
        if (lastBgPlaneRowUpdates.isEmpty()) {
            return;
        }
        try {
            LevelManager levelManager = GameServices.level();
            if (levelManager.getCurrentZone() != Sonic3kZoneIds.ZONE_SLOT_MACHINE
                    && levelManager.getRomZoneId() != Sonic3kZoneIds.ZONE_SLOT_MACHINE) {
                return;
            }
            boolean changed = false;
            for (BgPlaneRowUpdate update : lastBgPlaneRowUpdates) {
                changed |=
                        levelManager.copyBackgroundTileRowFromWorldToVdpPlane(
                                update.sourceX,
                                update.sourceY,
                                update.destVramAddress,
                                update.longWordCount);
            }
            if (changed) {
                levelManager.uploadBackgroundTilemap();
            }
        } catch (IllegalStateException ignored) {
            // Unit tests and headless scroll probes can run without a gameplay session.
        }
    }

    private static int clampSignedLong(int value, int limit) {
        if (value > limit) {
            return limit;
        }
        if (value < -limit) {
            return -limit;
        }
        return value;
    }

    private static int stepToward(int current, int target, int maxStep) {
        int delta = target - current;
        if (delta > maxStep) {
            return current + maxStep;
        }
        if (delta < -maxStep) {
            return current - maxStep;
        }
        return target;
    }

    private static int toSignedWord(int value) {
        return (short) value;
    }

    private static BandState[] createBandStates() {
        BandState[] states = new BandState[BAND_COUNT];
        for (int i = 0; i < states.length; i++) {
            states[i] = new BandState();
        }
        return states;
    }

    private S3kSlotBonusStageRuntime activeSlotRuntime() {
        if (!(GameServices.module().getBonusStageProvider()
                instanceof Sonic3kBonusStageCoordinator coordinator)) {
            return null;
        }
        return coordinator.activeSlotRuntime();
    }

    private static final class BandState {
        private int accumulator;
        private int countdown;
        private int phase;
        private int baseScroll;
        private boolean pendingAdvance;

        private void reset() {
            accumulator = 0;
            countdown = 0;
            phase = 0;
            baseScroll = 0;
            pendingAdvance = false;
        }

        private int accumulatorHighWord() {
            return (accumulator >>> 16) & 0xFFFF;
        }

        private void setAccumulatorHighWord(int value) {
            accumulator = ((value & 0xFFFF) << 16) | (accumulator & 0xFFFF);
        }
    }

    record BgPlaneRowUpdate(int sourceX, int sourceY, int destVramAddress, int longWordCount) {}

    private record BandConfig(
            int stepWord,
            int deltaLong,
            int resetCountdown,
            int phaseLimit,
            int destVramAddress,
            int oddPhaseDestDelta,
            boolean doubleRow,
            int sourceY,
            int longWordCount,
            boolean usesFrameLatch) {
        private boolean hasPlaneUpdate() {
            return destVramAddress != 0 && longWordCount > 0;
        }
    }
}
