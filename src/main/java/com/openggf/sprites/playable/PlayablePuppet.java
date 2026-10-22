package com.openggf.sprites.playable;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.game.CharacterConstructionScope;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameModule;
import com.openggf.game.GameStateManager;
import com.openggf.game.GroundMode;
import com.openggf.game.rewind.snapshot.CameraSnapshot;
import com.openggf.game.rewind.snapshot.CollisionSystemSnapshot;
import com.openggf.game.rewind.snapshot.GameStateSnapshot;
import com.openggf.game.rewind.snapshot.TimerManagerSnapshot;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.level.Level;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.Direction;
import com.openggf.physics.GroundSensor;
import com.openggf.physics.SensorResult;
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.managers.TailsTailsController;
import com.openggf.timer.TimerManager;
import java.util.Objects;

/**
 * One isolated playable driven through the normal native input/physics/animation tick.
 * Static terrain belongs to the set; movement rules and animation belong to the actor.
 * This world has no live objects, zone events, water, game session, or audio output.
 * No active services are replaced, even temporarily while stepping or restoring.
 */
public final class PlayablePuppet implements AutoCloseable {
    private final PlayableRuntimeContext context;
    private final AbstractPlayableSprite actor;
    private boolean closed;

    private PlayablePuppet(PlayableRuntimeContext context, AbstractPlayableSprite actor) {
        this.context = context;
        this.actor = actor;
    }

    public static PlayablePuppet create(Level terrain, GameModule setModule, int zone, int act,
            GameModule actorModule, String character, SpriteArtSet art,
            int centreX, int centreY, int collisionPath, boolean snapToFloor, boolean flipX) {
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(setModule, "setModule");
        Objects.requireNonNull(actorModule, "actorModule");
        Objects.requireNonNull(art, "art");
        if (collisionPath < 0 || collisionPath > 1) throw new IllegalArgumentException("Collision path must be 0 or 1");
        if (centreX < 0 || centreX > Short.MAX_VALUE || centreY < Short.MIN_VALUE || centreY > Short.MAX_VALUE)
            throw new IllegalArgumentException("Puppet spawn exceeds native signed coordinate range");
        CharacterKey key = CharacterKey.parsePersisted(character);
        var definition = actorModule.getPlayableCharacterRegistry().find(key)
                .orElseThrow(() -> new IllegalArgumentException("Unknown puppet character: " + character));
        EngineContext shared = EngineServices.current();
        AudioManager audio = AudioManager.createSilent();
        try {
            EngineContext engine = new EngineContext(shared.configuration(), shared.graphics(), audio,
                    shared.roms(), shared.profiler(), shared.debugOverlay(), shared.playbackDebug(),
                    shared.romDetection(), shared.crossGameFeatures(), shared.moduleResolutionService());
            var camera = new Camera(shared.configuration());
            var sprites = new SpriteManager(shared.configuration());
            var collision = new CollisionSystem(new TerrainCollisionManager());
            var water = new WaterSystem();
            var gameState = new GameStateManager();
            var timers = new TimerManager();
            var level = GameplaySessionFactory.createCollisionOnlyLevel(setModule, terrain, zone, act,
                    engine, camera, sprites, collision, water, gameState);
            var context = new PlayableRuntimeContext(actorModule, camera, level, collision, water,
                    sprites, gameState, timers, audio);
            AbstractPlayableSprite actor = context.construct(() -> CharacterConstructionScope.call(key,
                    () -> CharacterConstructionScope.validateFactoryResult(key,
                            definition.spriteFactory().create(character, centreX, centreY))));
            NativePositionOps.writeXPosResetSubpixel(actor, centreX);
            NativePositionOps.writeYPosResetSubpixel(actor, centreY);
            actor.setAnimationProfile(art.animationProfile());
            actor.setAnimationSet(art.animationSet());
            actor.setAnimationFrameCount(art.mappingFrames().size());
            actor.setDirection(flipX ? Direction.LEFT : Direction.RIGHT);
            actor.setTopSolidBit((byte) (collisionPath == 0 ? 0x0C : 0x0E));
            actor.setLrbSolidBit((byte) (collisionPath == 0 ? 0x0D : 0x0F));
            if (actor.getTopSolidBit() != (collisionPath == 0 ? 0x0C : 0x0E))
                throw new IllegalArgumentException("Actor physics does not support the selected collision path");
            actor.setAir(true);
            if (actor instanceof Tails) {
                actor.setTailsTailsController(new TailsTailsController(actor, null, actorModule.hasSeparateTailsTailArt()));
            }
            sprites.addSprite(actor);
            camera.setFocusedSprite(actor);
            camera.setMinX((short) terrain.getMinX());
            camera.setMaxX((short) terrain.getMaxX());
            camera.setMinY((short) terrain.getMinY());
            camera.setMaxY((short) terrain.getMaxY());
            // Boundary death belongs to a live gameplay visit; this world has no respawn/session owner.
            camera.setLevelStarted(false);
            var result = new PlayablePuppet(context, actor);
            if (snapToFloor) result.snapInitialFloor();
            return result;
        } catch (RuntimeException | Error failure) {
            try { audio.getBackend().destroy(); }
            catch (RuntimeException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }

    /**
     * Route proposal entry matches the current native Stageplay world, including its initially
     * airborne status. The detached authoring snap in {@link #create} intentionally differs:
     * it searches a larger range and explicitly attaches the actor to the floor.
     */
    public static PlayablePuppet createNativeWorldProbe(Level terrain, GameModule setModule, int zone, int act,
            GameModule actorModule, String character, SpriteArtSet art,
            int centreX, int centreY, int collisionPath, boolean snapToFloor, boolean flipX) {
        var probe = create(terrain, setModule, zone, act, actorModule, character, art,
                centreX, centreY, collisionPath, false, flipX);
        try {
            // Same injected collision owner/attachment policy as StageplayLiveWorld.begin.
            // Ground attachment adjusts the native position/angle; it does not fabricate a
            // grounded status or a controller edge before the first native physics tick.
            if (snapToFloor) probe.context.collision.resolveGroundAttachment(probe.actor, 14, () -> false);
            return probe;
        } catch (RuntimeException | Error failure) {
            try { probe.close(); }
            catch (RuntimeException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }

    /** Buttons are held for this one native 60 Hz tick; jump release remains native input state. */
    public void tick(boolean left, boolean right, boolean jump, int frame) {
        requireOpen();
        if (frame < 0) throw new IllegalArgumentException("Negative puppet frame");
        context.sprites.setFrameCounter(frame);
        actor.captureOnObjectAtFrameStart();
        // Poll_Controller publishes held and pressed bytes before the playable
        // control routine. A held boolean alone cannot initiate a native jump.
        actor.setJumpInputPressed(jump);
        actor.setDirectionalInputPressed(false, false, left, right);
        actor.setLogicalInputState(false, false, left, right, jump);
        context.timers.update();
        SpriteManager.tickPlayablePhysics(actor, false, false, left, right, jump,
                false, false, false, context.level, frame);
        var tails = actor.getTailsTailsController();
        if (tails != null) tails.update();
    }

    /** Positions are pixels; speed/acceleration values use the native signed 8.8 units. */
    public record Observation(double x, double y, int xSpeed, int ySpeed, int groundSpeed,
            int angle, int animation, int mappingFrame, boolean flipX, boolean flipY,
            boolean airborne, boolean onGround, int maxSpeed, int xRadius, int yRadius,
            Tail tail, int runAccel, int runDecel, int friction) { }

    public record Tail(int mappingFrame, boolean flipX, boolean flipY) { }

    public Observation sample() {
        requireOpen();
        var controller = actor.getTailsTailsController();
        var tail = controller == null ? null : controller.presentation();
        return new Observation(actor.getCentreX() + actor.getXSubpixelRaw() / 65536.0,
                actor.getCentreY() + actor.getYSubpixelRaw() / 65536.0,
                actor.getXSpeed(), actor.getYSpeed(), actor.getGSpeed(), actor.getAngle() & 0xFF,
                actor.getAnimationId(), actor.getMappingFrame(), actor.getRenderHFlip(), actor.getRenderVFlip(),
                actor.getAir(), !actor.getAir(), actor.getMax(), actor.getXRadius(), actor.getYRadius(),
                tail == null ? null : new Tail(tail.mappingFrame(), tail.flipX(), tail.flipY()),
                actor.getRunAccel(), actor.getRunDecel(), actor.getFriction());
    }

    /** An opaque checkpoint, accepted only by the same puppet instance. */
    public static final class Snapshot {
        private final PlayablePuppet owner;
        private final PerObjectRewindSnapshot actor;
        private final CameraSnapshot camera;
        private final CollisionSystemSnapshot collision;
        private final GameStateSnapshot gameState;
        private final TimerManagerSnapshot timers;
        private final long rng;
        private final int frame;
        private final com.openggf.audio.rewind.AudioLogicalSnapshot audio;
        private Snapshot(PlayablePuppet owner) {
            this.owner = owner;
            this.actor = owner.actor.captureRewindState();
            this.camera = owner.context.camera.capture();
            this.collision = owner.context.collision.capture();
            this.gameState = owner.context.gameState.capture();
            this.timers = owner.context.timers.capture();
            this.rng = owner.context.rng.getSeed();
            this.frame = owner.context.sprites.getFrameCounter();
            this.audio = owner.context.audio.captureLogicalSnapshot();
        }
    }

    public Snapshot capture() { requireOpen(); return new Snapshot(this); }

    public void restore(Snapshot snapshot) {
        requireOpen();
        if (Objects.requireNonNull(snapshot, "snapshot").owner != this)
            throw new IllegalArgumentException("Checkpoint belongs to another puppet");
        context.camera.restore(snapshot.camera, actor);
        context.collision.restore(snapshot.collision);
        context.gameState.restore(snapshot.gameState);
        context.timers.restore(snapshot.timers);
        context.rng.setSeed(snapshot.rng);
        context.sprites.setFrameCounter(snapshot.frame);
        actor.restoreRewindState(snapshot.actor);
        context.audio.restoreLogicalSnapshot(snapshot.audio);
    }

    /** Internal probe diagnostics; silent simulation still has rewindable producer state. */
    public com.openggf.audio.rewind.AudioLogicalSnapshot audioLogicalSnapshot() {
        requireOpen();
        return context.audio.captureLogicalSnapshot();
    }

    private void snapInitialFloor() {
        // Search near the authored feet, not from the top of the map. Native paired
        // sensors resolve tile slopes, flips and the actor's selected solidity bits.
        int anchor = actor.getCentreY();
        actor.setGroundMode(GroundMode.GROUND);
        GroundSensor left = new GroundSensor(actor, Direction.DOWN,
                (byte) -actor.getXRadius(), (byte) actor.getYRadius(), true);
        GroundSensor right = new GroundSensor(actor, Direction.DOWN,
                (byte) actor.getXRadius(), (byte) actor.getYRadius(), true);
        for (int offset = 0; offset <= 128; offset += 16) {
            SensorResult a = left.scan((short) 0, (short) offset);
            SensorResult b = right.scan((short) 0, (short) offset);
            SensorResult floor = validFloor(a) ? a : null;
            if (validFloor(b) && (floor == null || b.distance() < floor.distance())) floor = b;
            if (floor == null) continue;
            int correction = offset + floor.distance();
            if (correction < -16 || correction > 128) continue;
            NativePositionOps.writeYPosResetSubpixel(actor, anchor + correction);
            GroundSensor ceiling = new GroundSensor(actor, Direction.UP,
                    (byte) 0, (byte) -actor.getYRadius(), true);
            SensorResult head = ceiling.scan();
            if (head != null && head.tileId() != 0 && head.distance() < 0) {
                NativePositionOps.writeYPosResetSubpixel(actor, anchor);
                continue;
            }
            actor.setAngle((byte) ((floor.angle() & 1) != 0 ? 0 : floor.angle()));
            actor.setAir(false);
            return;
        }
        throw new IllegalArgumentException("No walkable floor near puppet feet at "
                + actor.getCentreX() + "," + (anchor + actor.getYRadius()));
    }

    private static boolean validFloor(SensorResult result) {
        if (result == null || result.tileId() == 0 || result.distance() < -16 || result.distance() > 16) return false;
        int angle = result.angle() & 0xFF;
        return (angle & 1) != 0 || ((angle + 0x20) & 0xC0) == 0;
    }

    private void requireOpen() { if (closed) throw new IllegalStateException("Puppet is closed"); }

    @Override public void close() {
        if (closed) return;
        closed = true;
        context.timers.resetState();
        context.audio.getBackend().destroy();
    }
}
