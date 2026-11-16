package com.openggf.game.sonic3k.scroll;

import static com.openggf.level.scroll.M68KMath.VISIBLE_LINES;
import static com.openggf.level.scroll.M68KMath.negWord;

import com.openggf.camera.NativeViewportFraming;
import com.openggf.game.GameServices;
import static com.openggf.game.sonic3k.events.Sonic3kMHZEvents.computeMhzDeform;
import static com.openggf.game.sonic3k.events.Sonic3kMHZEvents.computeEndBossPillarRampStep;
import com.openggf.game.sonic3k.events.Sonic3kMHZEvents.DeformOutputs;
import com.openggf.game.sonic3k.runtime.MhzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.scroll.AbstractZoneScrollHandler;
import com.openggf.level.scroll.compose.ScrollEffectComposer;

/**
 * Mushroom Hill Zone scroll handler.
 *
 * <p>Ports the shared {@code MHZ_Deform} routine used by MHZ1 and normal MHZ2
 * play, plus the MHZ2 ship/end-boss H-int foreground overrides driven by
 * event/runtime state.
 */
public class SwScrlMhz extends AbstractZoneScrollHandler
        implements com.openggf.level.scroll.ScrollProjectionSource {

    private final ScrollEffectComposer composer;

    public SwScrlMhz() {
        this(224);
    }

    private SwScrlMhz(int lines) {
        composer = new ScrollEffectComposer(lines);
    }

    private int bgCameraX = Integer.MIN_VALUE;
    private int band1CameraX;
    private int band2CameraX;
    @Override
    public void update(int[] horizScrollBuf,
                       int cameraX,
                       int cameraY,
                       int frameCounter,
                       int actId) {
        resetScrollTracking();
        composer.reset();

        MhzZoneRuntimeState state = currentRuntimeState();
        short fgScroll = negWord(cameraX + (state == null ? 0 : state.appliedScreenShakeOffset()));
        DeformOutputs outputs = computeMhzDeform(state == null ? cameraX : state.loopAdjustedCameraX(), cameraY,
                state == null ? 0 : state.appliedScreenShakeOffset(),
                state != null && state.isBossAreaBackgroundDeformActive());
        bgCameraX = outputs.bgCameraX();
        band1CameraX = outputs.band1CameraX();
        band2CameraX = outputs.band2CameraX();

        composer.setVscrollFactorBG(outputs.bgCameraY());
        composer.fillPackedScrollWords(0, VISIBLE_LINES, fgScroll, negWord(bgCameraX));
        if (state != null && state.isShipSequenceActive()) {
            applyShipHIntOverride(state);
        } else {
            applyEndBossArenaHIntOverride(state, cameraX);
        }
        composer.copyPackedScrollWordsTo(horizScrollBuf);

        vscrollFactorBG = composer.getVscrollFactorBG();
        minScrollOffset = composer.getMinScrollOffset();
        maxScrollOffset = composer.getMaxScrollOffset();
    }

    // MHZ2_ScreenEvent already adds the offset to the camera copy. Keep the
    // separate presentation-shake channel at its default zero: sprites,
    // uniform foreground and Render_Sprites bounds all consume that copy.
    @Override
    public int getBgCameraX() {
        return bgCameraX;
    }

    int getMiddleBgCameraX() {
        return band1CameraX;
    }

    int getNearBgCameraX() {
        return band2CameraX;
    }

    private void applyShipHIntOverride(MhzZoneRuntimeState state) {
        short shipScroll = negWord(state.shipPrimaryHScroll());
        int lineCount = Math.min(VISIBLE_LINES, state.shipHIntCounter() & 0xFFFF);
        for (int line = 0; line < lineCount; line++) {
            short bgScroll = (short) composer.packedScrollWordAt(line);
            composer.writePackedScrollWord(line, shipScroll, bgScroll);
        }
    }

    private void applyEndBossArenaHIntOverride(MhzZoneRuntimeState state, int cameraXCopy) {
        if (state == null || !state.isEndBossArenaBackgroundActive()) {
            return;
        }
        short bgCameraScroll = negWord(bgCameraX);
        int lineCount = Math.min(VISIBLE_LINES, 0x30);
        for (int line = 0; line < lineCount; line++) {
            short bgScroll = (short) composer.packedScrollWordAt(line);
            composer.writePackedScrollWord(line, bgCameraScroll, bgScroll);
        }
        applyEndBossPillarHScrollRamp(state, cameraXCopy);
    }

    private void applyEndBossPillarHScrollRamp(MhzZoneRuntimeState state, int cameraXCopy) {
        if (!state.isEndBossArenaForegroundRefreshActive()) {
            return;
        }
        // loc_55552/loc_555A4 use the native 320px camera for both the
        // pillar ramp and its collision helpers. Widescreen moves only the
        // displayed origin; feeding that origin into the ROM math changes
        // the pillars' world positions and their collision surfaces.
        int inset = state.centerNativeArenaCamera()
                ? NativeViewportFraming.inset(GameServices.camera().getWidth()) : 0;
        int nativeCameraX = (cameraXCopy + inset) & 0xFFFF;
        int lineCount = Math.min(VISIBLE_LINES, 0x30);
        if ((short) (nativeCameraX - 0x4180) < -0x140) {
            // loc_555FC clears the BG words and leaves helper positions alone.
            for (int line = 0; line < lineCount; line++) {
                short fgScroll = (short) (composer.packedScrollWordAt(line) >> 16);
                composer.writePackedScrollWord(line, fgScroll, (short) inset);
            }
            return;
        }
        int rampAccumulator = (negWord(nativeCameraX + state.appliedScreenShakeOffset() - 0x80) & 0xFFFF) << 16;
        int rampStep = computeEndBossPillarRampStep(nativeCameraX);
        for (int line = 0; line < lineCount; line++) {
            short fgScroll = (short) (composer.packedScrollWordAt(line) >> 16);
            short rampScroll = (short) ((rampAccumulator >> 16) + inset);
            composer.writePackedScrollWord(line, fgScroll, rampScroll);
            rampAccumulator += rampStep;
        }
    }

    private MhzZoneRuntimeState currentRuntimeState() {
        if (!GameServices.hasRuntime()) {
            return null;
        }
        return S3kRuntimeStates.currentMhz(GameServices.zoneRuntimeRegistry()).orElse(null);
    }

    @Override
    public com.openggf.level.scroll.ScrollProjectionPhase captureProjection(
            com.openggf.level.scroll.ScrollProjectionContext context) {
        MhzZoneRuntimeState state = currentRuntimeState();
        int adjustedX = state == null ? context.cameraX() & 0xffff : state.loopAdjustedCameraX();
        int shake = state == null ? 0 : state.appliedScreenShakeOffset();
        boolean bossArea = state != null && state.isBossAreaBackgroundDeformActive();
        boolean ship = state != null && state.isShipSequenceActive();
        int shipScroll = state == null ? 0 : state.shipPrimaryHScroll();
        int shipLines = state == null ? 0 : state.shipHIntCounter() & 0xffff;
        boolean arena = state != null && state.isEndBossArenaBackgroundActive();
        boolean pillars = state != null && state.isEndBossArenaForegroundRefreshActive();
        int inset =
                state != null && state.centerNativeArenaCamera()
                        ? NativeViewportFraming.inset(context.viewportWidth())
                        : 0;
        return view -> {
            SwScrlMhz scratch = new SwScrlMhz(view.height());
            int dx = view.cameraX() - context.cameraX();
            DeformOutputs outputs =
                    computeMhzDeform((adjustedX + dx) & 0xffff, view.cameraY(), shake, bossArea);
            scratch.composer.setVscrollFactorBG(outputs.bgCameraY());
            scratch.composer.fillPackedScrollWords(
                    0, view.height(), negWord(view.cameraX()), negWord(outputs.bgCameraX()));
            int lines = Math.min(view.height(), ship ? shipLines : arena ? 0x30 : 0);
            for (int line = 0; line < lines; line++) {
                short background = (short) scratch.composer.packedScrollWordAt(line);
                scratch.composer.writePackedScrollWord(
                        line,
                        ship ? (short) (negWord(shipScroll) - dx) : negWord(outputs.bgCameraX()),
                        background);
            }
            if (!ship && arena && pillars) {
                // loc_55552/loc_555A4 compositor only: collision helpers remain native-owned.
                int nativeX = (view.cameraX() + inset) & 0xffff;
                int accumulator = (negWord(nativeX - 0x80) & 0xffff) << 16;
                int step = computeEndBossPillarRampStep(nativeX);
                boolean clear = (short) (nativeX - 0x4180) < -0x140;
                for (int line = 0; line < Math.min(view.height(), 0x30); line++) {
                    short foreground = (short) (scratch.composer.packedScrollWordAt(line) >>> 16);
                    scratch.composer.writePackedScrollWord(
                            line,
                            foreground,
                            clear ? (short) inset : (short) ((accumulator >> 16) + inset));
                    accumulator += step;
                }
            }
            return new com.openggf.level.scroll.ScrollProjection(
                    scratch.composer.packedScrollWords(),
                    null,
                    null,
                    null,
                    (short)
                            (context.nativeOutput().foregroundY()
                                    + view.cameraY()
                                    - context.cameraY()),
                    outputs.bgCameraY(),
                    outputs.bgCameraX(),
                    context.nativeOutput().backgroundPeriod(),
                    context.nativeOutput().shakeX(),
                    context.nativeOutput().shakeY(),
                    context.nativeOutput().tilemapMode());
        };
    }
}
