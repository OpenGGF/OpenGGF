package com.openggf.sprites.playable;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.game.CharacterConstructionScope;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameModule;
import com.openggf.game.GameStateManager;
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
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.managers.TailsTailsController;
import com.openggf.timer.TimerManager;
import java.util.Objects;

/**
 * One isolated route probe driven through the normal native input/physics/animation tick.
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

    /** Isolated route proposal probe with the same entry/attachment policy as the native Stageplay world. */
    public static PlayablePuppet createNativeWorldProbe(Level terrain, GameModule setModule, int zone, int act,
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
            // Native attachment adjusts position/angle without fabricating a grounded
            // status or a controller edge before the first ordinary physics tick.
            if (snapToFloor) context.collision.resolveGroundAttachment(actor, 14, () -> false);
            return result;
        } catch (RuntimeException | Error failure) {
            try { audio.getBackend().destroy(); }
            catch (RuntimeException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }

    public boolean supports(com.openggf.game.CharacterDefinition.MovementAbility ability) {
        var module = context.module;
        return module.getPlayableCharacterRegistry().movementAbilities(actor.characterKey(), module).contains(ability);
    }

    public void tick(boolean left, boolean right, boolean jump, int frame) {
        tick(left, right, false, jump, frame);
    }

    public void tick(boolean left, boolean right, boolean down, boolean jump, int frame) {
        requireOpen();
        if (frame < 0) throw new IllegalArgumentException("Negative puppet frame");
        context.sprites.setFrameCounter(frame);
        actor.captureOnObjectAtFrameStart();
        // Poll_Controller publishes held and pressed bytes before the playable
        // control routine. A held boolean alone cannot initiate a native jump.
        actor.setJumpInputPressed(jump);
        actor.setDirectionalInputPressed(false, down, left, right);
        actor.setLogicalInputState(false, down, left, right, jump);
        context.timers.update();
        SpriteManager.tickPlayablePhysics(actor, false, down, left, right, jump,
                false, false, false, context.level, frame);
        var tails = actor.getTailsTailsController();
        if (tails != null) tails.update();
    }

    /** Positions are pixels; speed/acceleration values use the native signed 8.8 units. */
    public record Observation(double x, double y, int xSpeed, int ySpeed, int groundSpeed,
            int angle, int animation, int mappingFrame, boolean flipX, boolean flipY,
            boolean airborne, boolean onGround, int maxSpeed, int xRadius, int yRadius,
            Tail tail, int runAccel, int runDecel, int friction, boolean skidding, boolean spindash, int slopeImpulse, boolean crouching) { }

    public record Tail(int mappingFrame, boolean flipX, boolean flipY) { }

    public Observation sample() {
        requireOpen();
        return observe(actor);
    }

    /** Same native view for isolated search and production LIVE route controls. */
    public static Observation observe(AbstractPlayableSprite actor) {
        var controller = actor.getTailsTailsController();
        var tail = controller == null ? null : controller.presentation();
        return new Observation(actor.getCentreX() + actor.getXSubpixelRaw() / 65536.0,
                actor.getCentreY() + actor.getYSubpixelRaw() / 65536.0,
                actor.getXSpeed(), actor.getYSpeed(), actor.getGSpeed(), actor.getAngle() & 0xFF,
                actor.getAnimationId(), actor.getMappingFrame(), actor.getRenderHFlip(), actor.getRenderVFlip(),
                actor.getAir(), !actor.getAir(), actor.getMax(), actor.getXRadius(), actor.getYRadius(),
                tail == null ? null : new Tail(tail.mappingFrame(), tail.flipX(), tail.flipY()),
                actor.getRunAccel(), actor.getRolling() ? actor.getRollDecel() : actor.getRunDecel(),
                // Sonic_RollSpeed (s2.asm:37009-37013): natural roll drag is live acceleration / 2.
                actor.getRolling() ? actor.getRunAccel() / 2 : actor.getFriction(),
                actor.getSkidding(), actor.getSpindash(),
                (actor.getSlopeRunning() * com.openggf.physics.TrigLookupTable.sinHex(actor.getAngle() & 0xFF)) >> 8, actor.getCrouching());
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

    private void requireOpen() { if (closed) throw new IllegalStateException("Puppet is closed"); }

    @Override public void close() {
        if (closed) return;
        closed = true;
        context.timers.resetState();
        context.audio.getBackend().destroy();
    }
}
