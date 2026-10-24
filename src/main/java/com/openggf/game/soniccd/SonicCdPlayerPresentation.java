package com.openggf.game.soniccd;

import com.openggf.camera.Camera;
import com.openggf.control.InputActionMasks;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.GameplayInputFilter;
import com.openggf.game.ScrollHandlerProvider;
import com.openggf.game.internal.ControlledVBlankOnlyOwner;
import com.openggf.game.mode.CourseControl;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.mode.GameplayFrameDisposition;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.session.GameplayInputFilterAccess;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.level.SonicCdLevel;
import com.openggf.game.soniccd.player.CdPlayableSonic;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.scroll.CdR11aForegroundScroll;
import com.openggf.level.LevelManager;
import com.openggf.level.scroll.CameraDrivenScrollHandler;
import com.openggf.level.scroll.ZoneScrollHandler;
import com.openggf.sprites.managers.SpriteManagerInternalAccess;

import java.util.Arrays;
import java.util.Objects;

/**
 * Normal controlled-frame and camera adapters for the private terrain-only entry.
 * Static background, no native objects/rings/HUD/music, and no retail-route claim.
 * Source service is provided by the existing PLC boundary, never by draw/callback.
 */
final class SonicCdPlayerPresentation implements GameplayFrameController, ControlledVBlankOnlyOwner,
        ScrollHandlerProvider {
    private final SonicCdGameModule module;
    private final Scroll scroll = new Scroll();
    private CdR11aAssets assets;
    private CdPlayerFrameRuntime frame;
    private CdPlayableSonic actor;
    private CdR11aForegroundScroll.State scrollState;
    private Object scrollCaptureOwner = new Object();
    private PlcLifecyclePhase heldPhase;
    private int horizontalOffset;

    SonicCdPlayerPresentation(SonicCdGameModule module) { this.module = Objects.requireNonNull(module); }

    void bind(CdR11aAssets assets) {
        if (frame != null && !frame.closed()) throw new IllegalStateException("Retire the previous CD frame owner first");
        this.assets = Objects.requireNonNull(assets);
        scrollState = CdR11aForegroundScroll.coldFromLoadPlan(assets.loadPlan(), 320, 224);
        scrollCaptureOwner = new Object();
        heldPhase = null;
    }

    void bindFrame(CdPlayerFrameRuntime frame, CdPlayableSonic actor) {
        Objects.requireNonNull(frame); Objects.requireNonNull(actor);
        if (frame.assets() != assets || actor.nativeState() != frame.state()) {
            throw new IllegalArgumentException("CD actor/frame/scroll must bind the same source owner");
        }
        var camera = GameServices.camera();
        requireViewport(camera);
        this.frame = frame;
        this.actor = actor;
        requireBound();
        camera.setFocusedSprite(actor); // This resets camera words; project only AFTER this call.
        camera.setFrozen(true); // Only CameraDrivenScrollHandler advances the native foreground.
        camera.setLevelStarted(true);
        projectCamera(camera);
    }

    void unbind() {
        if (frame != null) frame.clearPendingInput();
        frame = null; actor = null; assets = null; scrollState = null; heldPhase = null;
        horizontalOffset = 0;
    }

    void afterRestore() {
        requireBound();
        actor.requireCompletedNativeFrame();
        if (GameServices.camera().getFocusedSprite() != actor) {
            frame.close();
            throw new IllegalStateException("CD restore requires its retained main actor");
        }
        projectCamera(GameServices.camera());
        actor.rebindAfterRestore(); // Full24 residency, no input/body/animation/cue.
        heldPhase = null;
    }

    private void requireBound() {
        module.validateTerrainPlayerRequest();
        if (frame == null || actor == null || assets == null || scrollState == null || frame.closed()) {
            throw new IllegalStateException("CD terrain-player admission is incomplete or closed");
        }
        if (module.requireRuntimeSource().assets() != assets
                || !(GameServices.level().getCurrentLevel() instanceof SonicCdLevel level)
                || level.assets() != assets) {
            throw new IllegalArgumentException("CD player no longer owns its authenticated stage");
        }
        var sprites = GameServices.sprites();
        if (sprites.getMainPlayable() != actor || sprites.getAllSprites().size() != 1
                || !sprites.getSidekicks().isEmpty()) {
            throw new UnsupportedOperationException("CD terrain entry requires its one retained Sonic");
        }
        var world = GameServices.level().getWorldSession();
        if (world.getGameModule() != module || WorldSessionPolicyAccess.hasPolicies(world)
                || GameplayInputFilterAccess.currentSessionFilter() != GameplayInputFilter.IDENTITY
                || SpriteManagerInternalAccess.motionMutators(actor) != null) {
            throw new UnsupportedOperationException("CD terrain input/slot admission does not support policy decorators");
        }
        requireViewport(GameServices.camera());
    }

    private static void requireViewport(Camera camera) {
        if (camera.getWidth() != 320 || camera.getHeight() != 224) {
            throw new UnsupportedOperationException("Native CD foreground currently requires a 320x224 viewport");
        }
    }

    @Override public boolean requiresExclusiveControl() { return true; }
    @Override public boolean beforeTick(CourseControl course, LogicalInputSnapshot input) {
        throw new IllegalStateException("CD uses the complete controlled-frame disposition");
    }
    @Override public GameplayFrameDisposition beforeFrame(CourseControl course, LogicalInputSnapshot input) {
        requireBound();
        actor.requireCompletedNativeFrame();
        if (input != null && input.debugModeTogglePressed()) {
            throw new UnsupportedOperationException("CD terrain entry does not support host debug movement");
        }
        int held = nativeHeldByte(input, SpriteManagerInternalAccess.inputSuppressed(GameServices.sprites()));
        var selection = frame.prepareInput(held);
        heldPhase = selection.nextPaused() ? selection.phase() : null;
        return selection.nextPaused() ? GameplayFrameDisposition.HOLD : GameplayFrameDisposition.ADVANCE_NATIVE_INPUT;
    }

    static int nativeHeldByte(LogicalInputSnapshot input, boolean suppressed) {
        if (suppressed || input == null) return 0;
        var p1 = Objects.requireNonNull(input.player1());
        return (p1.heldMask() & 15) | InputActionMasks.toMegaDriveButtonBits(p1.actionHeldMask())
                | (p1.startHeld() ? 0x80 : 0);
    }

    @Override public PlcLifecyclePhase heldVBlankPhase() { return heldPhase; }
    @Override public void afterTick(CourseControl course, boolean advanced) {
        requireBound();
        actor.requireCompletedNativeFrame();
        if (advanced == frame.paused() || (advanced && frame.environment().globals().levelFrames()
                != (GameServices.level().getFrameCounter() & 0xFFFF))) {
            frame.close();
            throw new IllegalStateException("CD represented frame did not complete its selected native body/hold");
        }
        frame.clearPendingInput();
        heldPhase = null;
    }

    @Override public boolean drawScene() { return true; }
    @Override public boolean presentationPaused() { return frame != null && frame.paused(); }
    @Override public void drawOverlay() {
        requireBound();
        actor.requireCompletedNativeFrame();
        var level = GameServices.level();
        GameServices.parallax().update(0, 0, GameServices.camera(), level.getFrameCounter());
        level.drawWithRenderOptions(GameServices.sprites(),
                new LevelManager.LevelRenderOptions(true, false, false, false, false, false, false));
        GameServices.graphics().flush();
    }

    @Override public void close() { module.closeTerrainPlayer(); }
    @Override public void load(Rom rom) {
        if (rom != null) throw new UnsupportedOperationException("CD scroll is source-bound, not cartridge-backed");
    }
    @Override public ZoneScrollHandler getHandler(int zoneIndex) {
        if (zoneIndex != 0) throw new UnsupportedOperationException("Only R11A foreground is admitted");
        return scroll;
    }
    @Override public ZoneConstants getZoneConstants() {
        return new ZoneConstants() {
            @Override public int getZoneCount() { return 1; }
            @Override public String getZoneName(int index) { return module.getZoneRegistry().getZoneName(index); }
        };
    }
    @Override public void resetZoneState() { horizontalOffset = 0; }

    private void projectCamera(Camera camera) {
        var bounds = frame.environment().movement().bounds();
        camera.setMinX((short) bounds.left()); camera.setMaxX((short) bounds.right());
        camera.setMinY((short) assets.loadPlan().bounds().top()); camera.setMaxY((short) bounds.bottom());
        camera.setVerticalWrapEnabled(false);
        camera.setX((short) scrollState.cameraX());
        camera.setY((short) (scrollState.cameraY16_16() >> 16));
        camera.setShakeOffsets(0, 0);
    }

    private record ScrollCapture(Object owner, CdR11aForegroundScroll.State state) { }
    private final class Scroll implements ZoneScrollHandler, CameraDrivenScrollHandler {
        @Override public boolean advanceCameraForFrame(Camera camera, int actId) {
            requireBound();
            if (actId != 0) throw new UnsupportedOperationException("Only R11A foreground is admitted");
            var m = frame.state().motion(); var shared = frame.environment().movement();
            var actorInput = new CdR11aForegroundScroll.Actor(m.centreX16_16() >>> 16,
                    m.centreY16_16() >>> 16, m.flags(), m.groundVelocity(), m.animation(), m.playerControl());
            try {
                var next = CdR11aForegroundScroll.step(scrollState, new CdR11aForegroundScroll.Input(
                        actorInput, shared.bounds(), assets.loadPlan().bounds().top(),
                        frame.environment().globals().cameraXCentre(), shared.globals().cameraYCentre(),
                        assets.profile().variant(), 0, 320, 224)); // DEV omission of the native HUD timer flag.
                frame.applyScrollFeedback(next.bounds(), next.cameraYCentre());
                scrollState = next.state();
                projectCamera(camera);
                actor.finishAfterCamera();
                return true;
            } catch (RuntimeException | Error failure) {
                frame.close(); // Body has already completed; no false whole-frame rollback/retry.
                throw failure;
            }
        }

        @Override public void update(int[] output, int cameraX, int cameraY, int frameCounter, int actId) {
            if (actId != 0) throw new UnsupportedOperationException("Only R11A background presentation is admitted");
            Arrays.fill(output, ((-cameraX) & 0xFFFF) << 16);
            horizontalOffset = cameraX; // Derived rendering data, no source step or accumulators.
        }
        @Override public short getVscrollFactorBG() { return 0; }
        @Override public int getMinScrollOffset() { return horizontalOffset; }
        @Override public int getMaxScrollOffset() { return horizontalOffset; }
        @Override public int getBgCameraX() { return 0; }
        @Override public int getBgPeriodWidth() {
            if (assets == null) throw new IllegalStateException("CD background has no bound assets");
            return assets.terrain().layout().columns(CdR11aTerrain.Plane.BACKGROUND) * 256;
        }
        @Override public Object captureRewindState() {
            return scrollState == null ? null : new ScrollCapture(scrollCaptureOwner, scrollState);
        }
        @Override public void restoreRewindState(Object snapshot) {
            if (snapshot == null) return;
            if (!(snapshot instanceof ScrollCapture value) || value.owner() != scrollCaptureOwner) {
                throw new IllegalArgumentException("CD camera capture belongs to another source visit");
            }
            scrollState = value.state();
        }
    }
}
