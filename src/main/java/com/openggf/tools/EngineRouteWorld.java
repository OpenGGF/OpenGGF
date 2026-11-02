package com.openggf.tools;

import com.openggf.game.GameMode;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.mode.ControlledFrameRuntime;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.level.Level;
import com.openggf.route.Capability;
import com.openggf.route.Inlet;
import com.openggf.route.InstanceKey;
import com.openggf.route.InteractionKind;
import com.openggf.route.InteractionView;
import com.openggf.route.ObjectView;
import com.openggf.route.PlacementKey;
import com.openggf.route.PlayerSlot;
import com.openggf.route.LaunchContract;
import com.openggf.route.PadRow;
import com.openggf.route.PlayerView;
import com.openggf.route.RecordedStep;
import com.openggf.route.RouteHostException;
import com.openggf.route.RouteSample;
import com.openggf.route.RouteWorld;
import com.openggf.route.Seeds;
import com.openggf.route.WorldCheckpoint;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * One booted production gameplay world. Rows enter at {@code GameplayCaptureSession.step}, the
 * recorded-input boundary, and the host drains prepared render-thread work after every row (the
 * work later load ticks need) without drawing. Checkpoints hold the whole rewind registry plus
 * every host-owned write ({@link GameplayCaptureSession.HostState}); they are refused while a
 * fresh level assembly or a callback-bearing fade is pending, and never cross an epoch.
 */
final class EngineRouteWorld implements RouteWorld, EngineRouteHosts.EngineView {
    private final LaunchContract launch;
    private final GameplayCaptureSession session;
    private int rowsOffered;
    private long epoch;
    private Level level;
    private GameMode mode;
    private GameplayModeContext context;
    private boolean closed;

    private EngineRouteWorld(LaunchContract launch, GameplayCaptureSession session) {
        this.launch = launch;
        this.session = session;
    }

    static EngineRouteWorld boot(LaunchContract launch, Path rom, Path donorRom, UnaryOperator<GameModule> decorator) {
        if (!(launch.inlet() instanceof Inlet.ColdLevel cold)) {
            throw new RouteHostException(RouteHostException.Reason.UNSUPPORTED_LAUNCH,
                    "engine host boots cold levels only: " + launch.inlet());
        }
        Seeds seeds = launch.initial().seeds();
        var settings = new GameplayCaptureSession.Settings(launch.width(), launch.roster().main(),
                launch.roster().sidekick(), launch.donor(), donorRom, seeds.startX(), seeds.startY(), seeds.emeralds(),
                cold.showTitleCard(), false, launch.initial().vIntStart(), seeds.cameraXSub(), seeds.starPost(),
                seeds.rings(), seeds.reverseGravity(), seeds.endOfLevel());
        var session = new GameplayCaptureSession(settings);
        var world = new EngineRouteWorld(launch, session);
        try {
            if (decorator != null) session.decorateModule(decorator);
            session.boot(rom, cold.level().zone(), cold.level().act(), settings);
            world.adoptBoundary();
            world.requireRecordedInputOwnership();
            return world;
        } catch (IOException failure) {
            world.close();
            throw new UncheckedIOException(failure);
        } catch (RuntimeException | Error failure) {
            world.close();
            throw failure;
        }
    }

    /** The capture session, for engine-only domain assertions by trusted test playback. */
    @Override
    public GameplayCaptureSession session() {
        requireOpen();
        return session;
    }

    LaunchContract launch() {
        return launch;
    }

    @Override
    public RouteSample observe() {
        requireOpen();
        var level = GameServices.level();
        var objects = level.getObjectManager();
        var camera = GameServices.camera();
        AbstractPlayableSprite p1 = session.player();
        Optional<PlayerView> p2 = Optional.empty();
        for (AbstractPlayableSprite sidekick : GameServices.sprites().getRegisteredSidekicks()) {
            if (sidekick != p1) {
                p2 = Optional.of(view(sidekick));
                break;
            }
        }
        AbstractPlayableSprite sidekick = null;
        for (AbstractPlayableSprite registered : GameServices.sprites().getRegisteredSidekicks()) {
            if (registered != p1) {
                sidekick = registered;
                break;
            }
        }
        var observed = new java.util.ArrayList<ObjectView>();
        var interactions = new java.util.ArrayList<InteractionView>();
        if (objects != null) observeObjects(objects, level.getCurrentZone(), level.getCurrentAct(), p1, sidekick, observed,
                interactions);
        return new RouteSample(rowsOffered, epoch,
                new RouteSample.Level(level.getCurrentZone(), level.getCurrentAct(), level.getApparentAct(),
                        session.loop().getCurrentGameMode().name()),
                new RouteSample.Clocks(objects == null ? 0 : objects.getVblaCounter(), level.getFrameCounter()),
                view(p1), p2, camera.getX() & 0xFFFF, camera.getY() & 0xFFFF,
                camera.isVerticalWrapEnabled() ? camera.getVerticalWrapRange() : 0, observed, interactions);
    }

    @Override
    public RecordedStep step(PadRow offered) {
        requireOpen();
        session.step(offered.toEngine(rowsOffered));
        GameServices.graphics().runPendingRenderThreadTasks();
        rowsOffered++;
        adoptBoundary();
        requireRecordedInputOwnership();
        return new RecordedStep(offered, observe());
    }

    @Override
    public int rowsOffered() {
        return rowsOffered;
    }

    @Override
    public WorldCheckpoint capture() {
        requireOpen();
        if (mode != GameMode.LEVEL) {
            throw new RouteHostException(RouteHostException.Reason.CHECKPOINT_REFUSED, "checkpoint outside LEVEL mode: " + mode);
        }
        RewindRegistry registry = context.getRewindRegistry();
        CompositeSnapshot snapshot = registry.capture();
        try {
            GameplayInputBranchTool.requireRestorableCheckpoint(snapshot,
                    GameServices.level().hasPendingFreshLevelTransitionBoundary());
        } catch (IllegalStateException refused) {
            throw new RouteHostException(RouteHostException.Reason.CHECKPOINT_REFUSED, refused.getMessage());
        }
        return new Checkpoint(this, epoch, level, mode, context, registry, registry.courseLayoutVersion(),
                snapshot, session.captureHostState(), rowsOffered);
    }

    @Override
    public void restore(WorldCheckpoint checkpoint) {
        requireOpen();
        if (!(checkpoint instanceof Checkpoint c) || c.owner != this) {
            throw new RouteHostException(RouteHostException.Reason.CHECKPOINT_MISMATCH, "checkpoint from another host");
        }
        RewindRegistry registry = context.getRewindRegistry();
        if (c.epoch != epoch || c.level != level || c.mode != mode || c.context != context || c.registry != registry
                || c.layoutVersion != registry.courseLayoutVersion()) {
            throw new RouteHostException(RouteHostException.Reason.CHECKPOINT_MISMATCH,
                    "checkpoint epoch " + c.epoch + " does not match the current world (epoch " + epoch + ")");
        }
        registry.restore(c.snapshot);
        session.restoreHostState(c.host);
        rowsOffered = c.rowsOffered;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        session.close();
    }

    /** Level object, game mode or gameplay context identity changes start a new epoch. */
    private void adoptBoundary() {
        Level currentLevel = GameServices.level().getCurrentLevel();
        GameMode currentMode = session.loop().getCurrentGameMode();
        GameplayModeContext currentContext = SessionManager.getCurrentGameplayMode();
        if (currentLevel != level || currentMode != mode || currentContext != context) {
            if (context != null) epoch++;
            level = currentLevel;
            mode = currentMode;
            context = currentContext;
        }
    }

    /**
     * A creator frame controller can substitute its own pad input or hold the world after the
     * recorded row is published, so the rows would no longer own gameplay input. Such launches
     * are refused until a controller is integrated explicitly.
     */
    private void requireRecordedInputOwnership() {
        if (ControlledFrameRuntime.controller(SessionManager.getCurrentGameplayMode()) != null) {
            throw new RouteHostException(RouteHostException.Reason.INPUT_NOT_OWNED,
                    "a gameplay frame controller owns this world's input; route rows would not be the effective input");
        }
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("route host is closed");
    }

    /**
     * Copies facts from live objects whose owners implement a read-only capability interface
     * ({@code instanceof}; no registration list), plus any object a player rides. Values only; no
     * instance escapes, and nothing is called that could mutate the owner.
     */
    private void observeObjects(com.openggf.level.objects.ObjectManager manager, int zone, int act,
                                AbstractPlayableSprite p1, AbstractPlayableSprite p2, java.util.List<ObjectView> out,
                                java.util.List<InteractionView> interactions) {
        var query = manager.objectQuery();
        var riding1 = p1 == null ? null : manager.getRidingObject(p1);
        var riding2 = p2 == null ? null : manager.getRidingObject(p2);
        for (var object : manager.getActiveObjects()) {
            boolean phase = object instanceof com.openggf.level.objects.observe.PhaseSource;
            boolean attachment = object instanceof com.openggf.level.objects.observe.AttachmentSource;
            boolean hits = object instanceof com.openggf.level.objects.observe.HitPointSource;
            if (!phase && !attachment && !hits && object != riding1 && object != riding2) continue;
            var identity = query.identityOf(object);
            if (identity.isEmpty()) continue;
            var ref = identity.get();
            var key = new InstanceKey(epoch, ref.slotIndex(), ref.generation(), ref.spawnId(), ref.dynamicId(),
                    ref.kind().name());
            var spawn = object.getSpawn();
            java.util.Optional<PlacementKey> placement = spawn != null && spawn.layoutIndex() >= 0
                    ? java.util.Optional.of(new PlacementKey(zone, act, spawn.layoutIndex(), spawn.objectId(), spawn.subtype()))
                    : java.util.Optional.empty();
            var capabilities = java.util.EnumSet.noneOf(Capability.class);
            java.util.List<String> phaseNames = java.util.List.of();
            String current = "";
            if (object instanceof com.openggf.level.objects.observe.PhaseSource source) {
                capabilities.add(Capability.PHASE);
                phaseNames = source.phaseNames();
                current = source.phase();
            }
            int remaining = -1;
            boolean defeated = false;
            if (object instanceof com.openggf.level.objects.observe.HitPointSource source) {
                capabilities.add(Capability.HIT_POINTS);
                remaining = source.hitsRemaining();
                defeated = source.defeated();
            }
            if (attachment) capabilities.add(Capability.ATTACHMENT);
            out.add(new ObjectView(key, placement, object.getClass().getSimpleName(), object.getX(), object.getY(),
                    capabilities, phaseNames, current, remaining, defeated));
            if (object == riding1) interactions.add(new InteractionView(key, PlayerSlot.P1, InteractionKind.STANDING,
                    object.getX(), object.getY(), false));
            if (object == riding2) interactions.add(new InteractionView(key, PlayerSlot.P2, InteractionKind.STANDING,
                    object.getX(), object.getY(), false));
            if (object instanceof com.openggf.level.objects.observe.AttachmentSource source) {
                attach(source, p1, PlayerSlot.P1, key, interactions);
                attach(source, p2, PlayerSlot.P2, key, interactions);
            }
        }
    }

    private static void attach(com.openggf.level.objects.observe.AttachmentSource source, AbstractPlayableSprite player,
                               PlayerSlot slot, InstanceKey key, java.util.List<InteractionView> interactions) {
        if (player == null) return;
        source.attachmentOf(player).ifPresent(a -> interactions.add(new InteractionView(key, slot,
                InteractionKind.valueOf(a.kind().name()), a.anchorX(), a.anchorY(), a.releasing())));
    }

    private static PlayerView view(AbstractPlayableSprite sprite) {
        // The same native motion facts the Stageplay probe and live route controls read.
        var o = com.openggf.sprites.playable.PlayablePuppet.observe(sprite);
        return new PlayerView(sprite.getCentreX(), sprite.getCentreY(), sprite.getXSubpixelRaw(),
                sprite.getYSubpixelRaw(), sprite.getXSpeed(), sprite.getYSpeed(), sprite.getGSpeed(),
                sprite.getAngle() & 0xFF, sprite.getAir(), sprite.getRolling(), sprite.getSpindash(),
                sprite.getCrouching(), sprite.isHurt(), sprite.getDead(), sprite.getRingCount(), sprite.isOnObject(),
                sprite.isObjectControlled(), sprite.getDirection() == com.openggf.physics.Direction.LEFT,
                new PlayerView.Motion(o.maxSpeed(), o.runAccel(), o.runDecel(), o.friction(), o.slopeImpulse(),
                        o.skidding()));
    }


    private record Checkpoint(EngineRouteWorld owner, long epoch, Level level, GameMode mode,
                              GameplayModeContext context, RewindRegistry registry, long layoutVersion,
                              CompositeSnapshot snapshot, GameplayCaptureSession.HostState host,
                              int rowsOffered) implements WorldCheckpoint { }
}
