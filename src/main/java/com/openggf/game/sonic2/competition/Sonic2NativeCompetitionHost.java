package com.openggf.game.sonic2.competition;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameResult;
import com.openggf.LevelFrameStep;
import com.openggf.audio.AudioNativePauseAccess;
import com.openggf.camera.Camera;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameModule;
import com.openggf.game.DamageCause;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.competition.NativeCompetitionWorld;
import com.openggf.game.competition.NativeCompetitionIterationAccess;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.SkippedPresentationPlcLifecycle;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.WorldSessionPolicyState;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.game.sonic2.competition.lifecycle.Sonic2CompetitionCheckpoints;
import com.openggf.level.Level;
import com.openggf.level.LevelArtInternalAccess;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.physics.Direction;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.managers.SpriteManagerInternalAccess;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * One production S2 world, two supplied humans, and six ROM act profiles.
 * The shell supplies native interlace presentation and the complete ROM swap port.
 */
public final class Sonic2NativeCompetitionHost implements Sonic2CompetitionHost, Sonic2CompetitionObjectHost {
    public interface Views {
        void prepare(GameplayModeContext context, Sonic2CompetitionSession session,
                     Sonic2CompetitionLevelProfile profile, Sonic2CompetitionNativeArt art);
        void draw(GameplayModeContext context, Sonic2CompetitionSession session);
        /** Root qualifies routes, native pages, tiebreaker and results together. */
        default boolean qualifiesZone(int romZone) { return false; }
    }

    /**
     * The implementation must exchange only teleport_swap_table-owned state,
     * atomically across actors/history/speeds, cameras/scroll and all three loaders.
     * Human identities, progress banks and unrelated actor fields stay in their slots.
     */
    public interface NativeState {
        boolean swapActorWorldState(GameplayModeContext context, Sonic2CompetitionSession session);
        void freezeObjects(GameplayModeContext context, int ticks);
        default boolean operational() { return false; }
    }

    private final GameModule rootModule;
    private final EngineContext services;
    private final Consumer<GameplayModeContext> publishMode;
    private final Views views;
    private final NativeState nativeState;
    private final boolean teleportOnly;
    private final boolean overseas;
    private NativeCompetitionWorld world;
    private Sonic2CompetitionSession session;
    private Sonic2CompetitionLevelProfile profile;
    private Sonic2CompetitionNativeArt art;
    private Sonic2CompetitionRuntimeModule module;
    private final Sonic2CompetitionPause pause = new Sonic2CompetitionPause();
    private final AudioNativePauseAccess.Port pauseAudio;
    private NativeWaitInput nativeWaitInput;
    private BooleanSupplier nativeSlowMotionFlag;
    private Supplier<Sonic2CompetitionCheckpoints.PrimaryEnvironment> checkpointEnvironment;
    private boolean pendingTitleExit;
    private record PauseSnapshot(Sonic2CompetitionPause.State planner, boolean pendingTitleExit) { }
    private final RewindSnapshottable<PauseSnapshot> pauseAdapter = new RewindSnapshottable<>() {
        @Override public String key() { return pause.key(); }
        @Override public PauseSnapshot capture() { return new PauseSnapshot(pause.capture(), pendingTitleExit); }
        @Override public void restore(PauseSnapshot state) {
            Objects.requireNonNull(state, "pause state");
            pause.restore(state.planner());
            pendingTitleExit = state.pendingTitleExit();
            mirrorNativePause();
        }
        @Override public void resetForMissingSnapshot() { resetNativePause(); }
    };

    public Sonic2NativeCompetitionHost(GameModule rootModule, EngineContext services,
            Consumer<GameplayModeContext> publishMode, Views views, NativeState nativeState) {
        this(rootModule, services, publishMode, views, nativeState, false);
    }

    public Sonic2NativeCompetitionHost(GameModule rootModule, EngineContext services,
            Consumer<GameplayModeContext> publishMode, Views views, NativeState nativeState,
            boolean teleportOnly) {
        this(rootModule, services, publishMode, views, nativeState, teleportOnly, true);
    }

    public Sonic2NativeCompetitionHost(GameModule rootModule, EngineContext services,
            Consumer<GameplayModeContext> publishMode, Views views, NativeState nativeState,
            boolean teleportOnly, boolean overseas) {
        this.rootModule = Objects.requireNonNull(rootModule, "rootModule");
        this.services = Objects.requireNonNull(services, "services");
        this.publishMode = Objects.requireNonNull(publishMode, "publishMode");
        this.views = Objects.requireNonNull(views, "views");
        this.nativeState = Objects.requireNonNull(nativeState, "nativeState");
        this.teleportOnly = teleportOnly;
        this.overseas = overseas;
        this.pauseAudio = AudioNativePauseAccess.bind(services.audio());
    }

    /** Supply the real logical wait sample and native Slow_motion_flag before opening a world. */
    public void bindNativeWaitInput(NativeWaitInput input, BooleanSupplier slowMotionFlag) {
        if (world != null || nativeWaitInput != null)
            throw new IllegalStateException("Native wait input already belongs to this host");
        Objects.requireNonNull(input, "native wait input");
        Objects.requireNonNull(slowMotionFlag, "native Slow_motion_flag");
        nativeWaitInput = input;
        nativeSlowMotionFlag = slowMotionFlag;
    }

    Sonic2CompetitionPause nativePause() { return pause; }

    /** Root's live native RAM owner supplies all P1-only Obj79 globals at activation. */
    public void bindNativeCheckpointEnvironment(
            Supplier<Sonic2CompetitionCheckpoints.PrimaryEnvironment> environment) {
        if (world != null || checkpointEnvironment != null)
            throw new IllegalStateException("Native checkpoint environment already belongs to this host");
        checkpointEnvironment = Objects.requireNonNull(environment, "native P1 checkpoint environment");
    }

    Sonic2CompetitionCheckpoints.PrimaryEnvironment checkpointEnvironment() {
        if (checkpointEnvironment == null)
            throw new UnsupportedOperationException("Actual native P1 checkpoint globals are not bound");
        return Objects.requireNonNull(checkpointEnvironment.get(), "actual native P1 checkpoint globals");
    }

    com.openggf.data.Rom rom() throws IOException { return services.roms().getRom(); }

    @Override public boolean supportsZone(int zone) {
        return Sonic2CompetitionLevelProfile.supportsZone(zone)
                && nativeWaitInput != null && nativeSlowMotionFlag != null
                && checkpointEnvironment != null
                && nativeState.operational() && views.qualifiesZone(zone);
    }

    @Override public void loadAct(Sonic2CompetitionSession supplied, int zone, int act) {
        requireNativeInput();
        Objects.requireNonNull(supplied, "session");
        if (supplied.phase() != Sonic2CompetitionSession.Phase.ACT
                || supplied.romZone() != zone || supplied.act() != act)
            throw new IllegalArgumentException("Load must match the session's active native act");
        if (session != null && supplied != session) throw new IllegalStateException("Host already owns another session");
        try {
            // Verify/decode the destination before retiring the outgoing mode.
            var nextProfile = Sonic2CompetitionLevelProfile.load(services.roms().getRom(), zone, act);
            if (world == null) {
                session = supplied;
                // Bind identities and banks BEFORE NativeCompetitionWorld.open chooses managers.
                supplied.bindPlayers(Sonic2CompetitionSession.createNativeParticipant(0, "sonic"),
                        Sonic2CompetitionSession.createNativeParticipant(1, "tails"));
                supplied.bindCameras(new Camera(services.configuration()), new Camera(services.configuration()));
                module = new Sonic2CompetitionRuntimeModule(rootModule, this);
                world = NativeCompetitionWorld.open(rootModule, module, supplied,
                        new Policy(), services, publishMode);
            }
            profile = nextProfile;
            resetNativePause();
            // Reuse this exact context: policy.retire resets the complete competition ledger.
            world.load(logicalZone(zone), act);
            module.nativeLifecycle().initializeAct();
            // The inherited VInt clock and native LF=0 are published only when
            // loadZoneAndAct returns. Assembly must not lose the 25 real waits.
            views.prepare(world.context(), session, profile, art);
            runNativeEntry();
            views.prepare(world.context(), session, profile, art);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot load native S2 competition act", failure);
        } finally {
            if (module != null) module.clearCameraSample();
        }
    }

    private static int logicalZone(int zone) {
        return switch (zone) { case 0 -> 0; case 11 -> 5; case 12 -> 3;
            default -> throw new IllegalArgumentException("Not a competition ROM zone: " + zone); };
    }

    public GameplayModeContext context() { return requireWorld().context(); }
    Sonic2CompetitionSession session() { return Objects.requireNonNull(session, "session"); }
    public Sonic2CompetitionLevelProfile profile() { return profile; }
    public Sonic2CompetitionNativeArt nativeArt() { return art; }

    @Override public void step(Sonic2CompetitionSession supplied, InputHandler input) {
        requireSession(supplied);
        step(input, NativeCompetitionIterationAccess.currentFrame());
    }

    /** Headless drivers own input/timers and supply their actual admitted lifecycle token. */
    public LevelFrameResult step(InputHandler input, PlcLifecycleFrame frame) {
        requireNativeInput();
        Objects.requireNonNull(input, "input");
        if (NativeCompetitionIterationAccess.currentFrame() != frame)
            throw new IllegalArgumentException("Native pause requires this world's admitted token");
        var before = pause.capture();
        boolean previousExit = pendingTitleExit;
        LevelFrameResult result;
        try {
            var wait = pause.beginWait(session.gameState(0).getLives());
            mirrorNativePause();
            if (wait == Sonic2CompetitionPause.Wait.PAUSE_WAIT) {
                if (before.continuation() == Sonic2CompetitionPause.Continuation.ENTRY)
                    pauseAudio.requestPause();
                var pauseResult = requireWorld().pauseVBlank(frame);
                // VintRet (s2.asm:507-508) advances even when Pause_Loop has
                // no RunObjects. The bound power-on counter delegates to this
                // actual object clock; the ordinary body owns its own tick.
                context().getObjectManager().advanceVblaCounter();
                pause.acceptSample(sampleNativeInput(), nativeSlowMotionFlag.getAsBoolean());
                if (pause.continuation() == Sonic2CompetitionPause.Continuation.ORDINARY_WAIT)
                    pauseAudio.requestResume(); // SlowMo also writes FF while Game_paused remains one.
                mirrorNativePause();
                return pauseResult;
            }
            result = requireWorld().step(input, frame, () -> {
                var sampled = sampleNativeInput();
                if (!sampled.equals(input.logical()))
                    throw new IllegalStateException("Native wait and playable dispatcher disagree on controller sample");
                pause.acceptSample(sampled, nativeSlowMotionFlag.getAsBoolean());
                context().getSpriteManager().update(input);
            });
            if (result == LevelFrameResult.GAMEPLAY_FRAME) {
                pendingTitleExit = pause.completeBody() == Sonic2CompetitionPause.Exit.TITLE_SCREEN;
            } else {
                // A setup-only executor did not traverse its ordinary wait/body.
                pause.restore(before);
            }
            mirrorNativePause();
        } catch (RuntimeException | Error failure) {
            module.clearCameraSample();
            pause.restore(before);
            pendingTitleExit = previousExit;
            mirrorNativePause();
            throw failure;
        }
        // A failed presentation preparation cannot undo the world body or its
        // completed pause/title continuation. The view owner handles its fault.
        views.prepare(context(), session, profile, art);
        return result;
    }

    @Override public boolean consumePendingTitleExit() {
        if (!pendingTitleExit) return false;
        // PauseGame writes Game_Mode before returning to the following body.
        // A simultaneous body-owned mode write needs its own source-qualified
        // priority; never silently override an actual world transition here.
        if (session.phase() != Sonic2CompetitionSession.Phase.ACT)
            throw new UnsupportedOperationException("Simultaneous native pause title and world transition priority is unqualified");
        pendingTitleExit = false;
        return true;
    }

    private void requireNativeInput() {
        if (nativeWaitInput == null || nativeSlowMotionFlag == null)
            throw new IllegalStateException("Actual native wait input and Slow_motion_flag owners are required");
    }

    private LogicalInputSnapshot sampleNativeInput() {
        return Objects.requireNonNull(nativeWaitInput.sampleAfterWait(), "actual native wait controller sample");
    }

    private void mirrorNativePause() {
        if (session == null) return;
        for (int slot = 0; slot < 2; slot++) session.gameState(slot).setGamePaused(pause.gamePaused());
    }

    private void resetNativePause() {
        if (module != null) module.clearCameraSample();
        pause.reset();
        pendingTitleExit = false;
        mirrorNativePause();
    }

    @Override public void draw(Sonic2CompetitionSession supplied) {
        requireSession(supplied);
        views.draw(context(), session);
    }
    @Override public boolean teleportOnly() { return teleportOnly; }
    @Override public void requestTeleport(Sonic2CompetitionSession supplied) {
        requireSession(supplied);
        // Obj2E's award performs teleport_monitor synchronously. Later slots in
        // this same RunObjects walk must observe the committed native exchange.
        // The root's precise transaction and routine gates remain unsupported
        // until its complete actor/world facade is operational.
        session.teleportInitializedMonitor(this);
    }
    @Override public boolean swapNativeActorWorldState() {
        return nativeState.operational() && nativeState.swapActorWorldState(context(), session);
    }
    @Override public void freezeObjects(int ticks) { nativeState.freezeObjects(context(), ticks); }

    void prepareLoad(LevelLoadContext load) throws IOException {
        art = Sonic2CompetitionNativeArt.load(services.roms().getRom(), profile);
        load.setRom(services.roms().getRom());
        load.setZone(profile.romZone()); load.setAct(profile.act());
        context().getRng().setSeed(0);
    }
    Level decodeTerrain() throws IOException { return Sonic2CompetitionTerrain.decode(services.roms().getRom(), art); }

    void completeInitialPlcs() throws IOException {
        // InitGameModule created this act's service. Level (s2.asm:4764-4804)
        // clears it, queues PLC1/Std2 and selects the Graphics_Flags 2P life cue.
        var plc = Objects.requireNonNull(module.getGameService(Sonic2PlcService.class), "S2 native PLC service");
        var operations = new ArrayList<Sonic2PlcService.Operation>();
        operations.add(Sonic2PlcService.clearOperation());
        if (profile.art().primaryPlc() != 0) operations.add(Sonic2PlcService.appendOperation(profile.art().primaryPlc()));
        operations.add(Sonic2PlcService.appendOperation(1));
        operations.add(Sonic2PlcService.appendOperation(overseas ? 8 : 6));
        plc.transact(operations.toArray(Sonic2PlcService.Operation[]::new));
        // Level_TtlCard (s2.asm:4914-4926) waits for these descriptors before
        // geometry/player assembly. Replay only its omitted PLC boundaries.
        SkippedPresentationPlcLifecycle.drain(module.nativeVramOwner().plcLifecycle(),
                PlcLifecyclePhase.LEVEL_TITLE_CARD, plc::isBusy);
        // LoadZoneTiles patches its own span after the real initial PLC writes;
        // completed high VRAM uploads remain intact for native presentation.
        module.nativeVramOwner().loadZoneArt(profile);
    }

    void completeSecondaryPlc() throws IOException {
        var plc = Objects.requireNonNull(module.getGameService(Sonic2PlcService.class), "S2 native PLC service");
        // loadZoneBlockMaps (s2.asm:20105-20110) appends PLC2 only after the
        // initial queue drained; CNZ's initial queue already uses all 15 slots.
        if (profile.art().secondaryPlc() != 0) plc.append(profile.art().secondaryPlc());
    }

    private void runNativeEntry() {
        var mode = context();
        var title = module.nativeTitle();
        LogicalInputSnapshot[] lastWaitSample = {null};
        title.beginOmittedPresentationLeave(logicalZone(profile.romZone()), profile.act());
        for (int slot = 0; slot < 2; slot++) {
            session.player(slot).setControlLocked(true);
            session.camera(slot).setLevelStarted(false);
        }
        try {
            // Level_FromCheckpoint (s2.asm:5004-5009): the leading RunObjects
            // follows initial object/ring/bumper interest and has no WaitForVint.
            runNativeEntryObjectPass(false);
            // Level_FromCheckpoint (s2.asm:5004-5009) publishes BuildSprites
            // immediately after this leading pass, before the first title wait.
            views.prepare(mode, session, profile, art);
            title.completeLockedIteration();
            // The existing Obj34 state machine owns 5+11+9 leave passes. Each
            // wait services VInt/art, then players, title slots, dynamic objects,
            // BuildSprites and PLC tail. No ordinary frame/timer/placement runs.
            while (!title.shouldReleaseControl()) {
                mode.plcFrameLifecycle().runLogicalIteration(() -> { }, frame -> {
                    LevelFrameStep.executeHardwareTimedObjectScan(LevelFrameContext.from(mode), frame,
                            PlcLifecyclePhase.LEVEL_TITLE_CARD, () -> {
                                // VintRet (s2.asm:507-508): art service precedes
                                // the counter increment read by RunObjects.
                                mode.getObjectManager().advanceVblaCounter();
                                lastWaitSample[0] = sampleNativeInput();
                                runNativeEntryObjectPass(true);
                            });
                    title.completeLockedIteration();
                    views.prepare(mode, session, profile, art);
                    return null;
                });
            }
            // PauseGame first reads the controller words left by this actual
            // final title wait. S2 clears them earlier in Level; neither zero
            // nor S3K's GetDemoPtr $8000 is a substitute for the polled sample.
            pause.seedFromNativeWait(Objects.requireNonNull(lastWaitSample[0], "final native entry wait"));
            mirrorNativePause();
            // Retain Obj34's real delayed animal/water art tail on ordinary
            // object passes. The omitted pieces never create overlay elements.
            title.beginOmittedPresentationExitTail(logicalZone(profile.romZone()), profile.act());
        } finally {
            for (int slot = 0; slot < 2; slot++) {
                session.player(slot).setControlLocked(false);
                session.camera(slot).setLevelStarted(true);
            }
        }
    }

    private void runNativeEntryObjectPass(boolean titleSlots) {
        var level = context().getLevelManager();
        level.prepareTouchResponseSnapshots();
        SpriteManagerInternalAccess.runNativeCompetitionTitlePlayablePass(context().getSpriteManager());
        if (titleSlots) module.runNativeTitleSlots();
        ObjectManagerInternalAccess.runNativeCompetitionTitleObjectPass(context().getObjectManager());
    }

    void deformNativeEntry() {
        var events = module.getLevelEventProvider();
        try {
            events.updateAfterObjectsBeforeCamera();
            session.camera(0).updatePosition();
            events.updateFixedInLevelObjects();
            session.camera(0).captureRenderCopy();
            events.update();
            context().getLevelManager().updateZoneFeaturesAfterObjectExecution();
            context().getLevelManager().recomputeParallaxOnlyForCurrentFrame();
        } finally {
            module.clearCameraSample();
        }
    }

    void initializeObjects() throws IOException {
        var mode = context(); var level = mode.getLevelManager();
        level.initObjectManager();
        var objects = level.getObjectManager();
        ObjectManagerInternalAccess.enableNativeCompetitionStreaming(objects, new Sonic2CompetitionObjectStreaming());
        level.initCameraBounds();
        mode.registerLevelAdapters(level);
        replaceRewindAdapter(ObjectManagerInternalAccess.nativeCompetitionStreamingRewindAdapter(objects));
        replaceRewindAdapter(ObjectManagerInternalAccess.nativeCompetitionCollisionPointersRewindAdapter(objects));
        replaceRewindAdapter(module.nativeTitle());
        replaceRewindAdapter(pauseAdapter);
    }

    private void replaceRewindAdapter(RewindSnapshottable<?> adapter) {
        var registry = context().getRewindRegistry();
        registry.deregister(adapter.key());
        registry.register(adapter);
    }

    void initializeRuntimeState() { module.onLevelLoad(); }
    void initializeRings() {
        LevelArtInternalAccess.initializeNativeCompetitionRings(context().getLevelManager(), new Sonic2CompetitionRingStreaming());
    }
    void initializeArt() {
        var level = context().getLevelManager();
        LevelArtInternalAccess.initializeObjectArt(level);
        // This production initializer binds ROM animation scripts and native fixed
        // dust/tail owners to both humans. Its ordinary renderer/cache is not the
        // native interlace VRAM producer and cannot qualify a competition view.
        level.refreshPlayableSpriteArt();
        for (int slot = 0; slot < 2; slot++) {
            var actor = session.player(slot);
            if (actor.getAnimationProfile() == null || actor.getAnimationSet() == null
                    || actor.getSpindashDustController() == null)
                throw new IllegalStateException("Native participant ROM animation/auxiliary art was not installed");
        }
    }

    void initializeParticipants() {
        context().getSpriteManager().setFrameCounter(0);
        context().getLevelManager().resetLevelGamestate(session.levelState(0));
        for (int slot = 0; slot < 2; slot++) {
            var player = session.player(slot);
            player.resetState();
            // Level_ClrRam clears the actor slots on a fresh act. resetState
            // deliberately preserves motion for other callers, so clear these
            // native words here instead of carrying the previous act's slope.
            player.setXSpeed((short) 0); player.setYSpeed((short) 0); player.setGSpeed((short) 0);
            player.setAngle((byte) 0);
            player.setDebugMode(false);
            var start = slot == 0 ? profile.sonicStart() : profile.tailsStart();
            NativePositionOps.writeXPosResetSubpixel(player, start.x());
            NativePositionOps.writeYPosResetSubpixel(player, start.y());
            player.setDirection(Direction.RIGHT);
            player.setLayer((byte) 0);
            player.resetPositionAndStatTableHistory();
            // Obj01_Init_Continued pre-fills P1's delayed-follow bank; Obj02
            // leaves its bank zeroed until the first Tails_RecordPos write.
            // Obj01_Init_Continued (s2.asm:36201-36221), Obj02_Init_Continued (s2.asm:38934-38947).
            player.resetPositionAndStatTableHistoryAtCentre((short) (slot == 0 ? start.x() - 0x20 : 0),
                    (short) (slot == 0 ? start.y() + 4 : 0));
        }
        context().getLevelManager().refreshPlayablePowerUpSpawners();
    }

    void initializeCameras() {
        var bounds = profile.bounds();
        for (int slot = 0; slot < 2; slot++) {
            var camera = session.camera(slot);
            camera.resetState();
            camera.setFocusedSprite(session.player(slot));
            camera.setMinX((short) bounds.minX()); camera.setMaxX((short) bounds.maxX());
            camera.setMinY((short) bounds.minY()); camera.setMaxY((short) bounds.maxY());
            camera.setLevelStarted(true);
            camera.updatePosition(true);
            camera.captureRenderCopy();
        }
        // Reset native interest from the actual initial camera, after both actors exist.
        context().getObjectManager().reset(session.camera(0).getX());
        context().getLevelManager().getRingManager().reset(session.camera(0).getX());
        module.getLevelEventProvider().initLevel(logicalZone(profile.romZone()), profile.act());
        module.initializeBoundaryWords();
        module.nativeFeatures().updateNativeCursors();
    }

    void resetLoadState() {
        art = null;
        if (module != null) module.clearCameraSample();
    }

    private NativeCompetitionWorld requireWorld() {
        if (world == null) throw new IllegalStateException("Native competition world has not been opened");
        return world;
    }
    private void requireSession(Sonic2CompetitionSession supplied) {
        if (supplied != session || world == null) throw new IllegalStateException("Foreign native session");
    }

    private final class Policy implements WorldSessionPolicyState {
        @Override public <T> T getService(Class<T> type) {
            if (type == NativeCompetitionRoster.class || type == Sonic2CompetitionSession.class) return type.cast(session);
            if (type == Sonic2CompetitionObjectHost.class) return type.cast(Sonic2NativeCompetitionHost.this);
            if (type == com.openggf.game.sonic2.bumpers.Sonic2NativeCNZBumpers.class)
                return type.cast(module.nativeFeatures().nativeBumpers());
            if (type == com.openggf.game.competition.NativeTouchResponseOrder.class)
                return type.cast(module.nativeFeatures());
            if (type == com.openggf.game.competition.NativePlayerDeathPass.class
                    || type == com.openggf.game.sonic2.competition.lifecycle.Sonic2CompetitionLifecycle.class)
                return type.cast(module.nativeLifecycle());
            return null;
        }
        @Override public RewindSnapshottable<?> rewindAdapter() { return session; }
        @Override public void beforeAssembly(LevelLoadCause cause) { }
        @Override public void bindRoster(SpriteManager sprites) {
            module.clearCameraSample();
            var sonic = sprites.getSprite("sonic"); var tails = sprites.getSprite("tails");
            if (!(sonic instanceof AbstractPlayableSprite one) || !(tails instanceof AbstractPlayableSprite two))
                throw new IllegalStateException("Native human roster disappeared");
            session.bindPlayers(one, two);
            module.bindNativeArtPlayers();
        }
        @Override public void afterNativeTick(LevelFrameContext frame, LogicalInputSnapshot input) {
            module.nativeFeatures().updateNativeCursors();
            session.advanceActFlow(); // LevelManager already advanced each bank's timer once.
            int timeOver = session.consumeTimeOverRequests();
            for (int slot = 0; slot < 2; slot++) if ((timeOver & 1 << slot) != 0 && !session.player(slot).getDead())
                session.player(slot).applyHurtOrDeath(0, DamageCause.TIME_OVER, false);
        }
        @Override public void failedAssembly(LevelLoadCause cause) { resetLoadState(); resetNativePause(); }
        @Override public void closeScreens() { resetNativePause(); }
        @Override public void retire() { resetLoadState(); resetNativePause(); session.reset(); }
    }
}
