package com.openggf.game.mutators;

import com.openggf.control.PlayerInputState;
import com.openggf.game.GameStateInternalAccess;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.WorldSessionPolicyState;
import com.openggf.mods.mutators.*;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.managers.SpriteManagerInternalAccess;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.util.EngineCallerAccess;

import java.math.BigInteger;
import java.util.List;

/** World-owned trusted motion consumers. Bindings are structural; all historical tails are captured. */
public final class MotionMutatorRuntime implements WorldSessionPolicyState,
        RewindSnapshottable<MotionMutatorRuntime.Snapshot> {
    private static final java.util.concurrent.atomic.AtomicLong GENERATIONS = new java.util.concurrent.atomic.AtomicLong();
    private final long generation = GENERATIONS.incrementAndGet();
    private final MotionInputQueue input = new MotionInputQueue();
    private final java.util.Set<Long> cancelledCruises = new java.util.HashSet<>();
    static final int ONE_SHOT_NATIVE_TICK_LIMIT = 120;
    static final int ONE_SHOT_PRESENTATION_LIMIT = 180;
    private final java.util.Map<Long, Integer> oneShotAttempts = new java.util.HashMap<>();
    private final java.util.Set<Long> settledOneShots = new java.util.HashSet<>();
    private SpriteManager sprites;
    private GameStateManager gameState;
    private com.openggf.level.WaterSystem nativeWater;
    private int waterZone, waterAct;
    private TransientMutatorPolicySource source;
    private boolean retired;
    private boolean ordinaryGameplay = true;
    private boolean gravityQualified;
    private boolean fluidMovementActive;
    private boolean fluidMovementSample;
    private long inputControlGeneration = -1;
    private long fluidActivation;
    private int fluidX, fluidY;

    public void bind(SpriteManager sprites, GameStateManager gsm, TransientMutatorPolicySource source) {
        requireEngineCaller();
        if (retired) throw new IllegalStateException("Motion owner is retired");
        if (source != null && source.getClass().getClassLoader() != MotionMutatorRuntime.class.getClassLoader())
            throw new SecurityException("Motion policies require a trusted engine source");
        this.sprites = java.util.Objects.requireNonNull(sprites);
        this.gameState = java.util.Objects.requireNonNull(gsm);
        this.source = java.util.Objects.requireNonNull(source);
        SpriteManagerInternalAccess.bindMotionMutators(sprites, this);
        GameStateInternalAccess.bindSignedGravity(gsm, this::reversesGravity);
        sampleGravityQualification();
    }

    private static void requireEngineCaller() {
        if (EngineCallerAccess.callerOutside(MotionMutatorRuntime.class).getClassLoader()
                != MotionMutatorRuntime.class.getClassLoader()) throw new SecurityException("Engine motion binding only");
    }

    private List<TransientMutatorPolicySource.Entry> entries() {
        return retired || !ordinaryGameplay || source == null ? List.of() : source.entries().stream()
                .filter(entry -> entry.oneShot() || entry.remainingPresentations() > 0).toList();
    }

    public boolean leader(AbstractPlayableSprite sprite) {
        return sprite != null && !retired && sprites != null && sprites.getMainPlayable() == sprite;
    }

    /** Exclude independent native motion owners; their controls/launches must never be replaced. */
    public static boolean ordinary(AbstractPlayableSprite sprite) {
        return sprite != null && !sprite.getDead() && !sprite.isHurt() && !sprite.isDebugMode()
                && !sprite.isHidden() && !sprite.getPinballMode() && !sprite.getPinballSpeedLock()
                && sprite.getForcedInputMask() == 0
                && !sprite.isForceInputRight() && !sprite.getTailsCarryController().isCarryingMainCharacter()
                && !sprite.isControlLocked() && !sprite.isObjectControlled() && !sprite.isSliding()
                && !sprite.getSpindash() && sprite.getDoubleJumpFlag() == 0
                && sprite.getMoveLockTimer() == 0 && !sprite.getSpringing() && !sprite.isDrowningPreDeath()
                && (sprite.getSuperStateController() == null
                || sprite.getSuperStateController().getState() != com.openggf.sprites.playable.SuperState.TRANSFORMING);
    }

    private boolean free(AbstractPlayableSprite sprite) {
        return leader(sprite) && ordinary(sprite) && !sprite.isOnObject()
                && gameState != null && !gameState.isBossFightActive() && !gameState.isEndOfLevelActive();
    }

    public PlayerInputState admitInput(PlayerInputState sample, boolean suppressed) {
        var leader = sprites == null ? null : sprites.getMainPlayable();
        var candidates = entries().stream().filter(e -> e.policy() instanceof InputControlPolicy).toList();
        var selected = candidates.isEmpty() ? null : candidates.getFirst();
        // Input interference is exclusive. Deterministic first admission wins; report every other lease.
        for (int i = 1; i < candidates.size(); i++) source.report(candidates.get(i).activationId(), "Input interference already active");
        var policy = selected == null ? null : (InputControlPolicy) selected.policy();
        long controlGeneration = leader == null ? -1 : leader.getObjectControlGeneration();
        boolean landingContinuity = inputControlGeneration < 0 || inputControlGeneration == controlGeneration;
        inputControlGeneration = controlGeneration;
        PlayerInputState admitted = input.admit(sample, policy, selected == null ? 0 : selected.activationId(),
                !suppressed && free(leader) && (policy == null || policy.mode() != InputControlPolicy.Mode.LANDING_JUMP
                        || landingContinuity), leader != null && leader.getAir());
        var active = entries();
        cancelledCruises.removeIf(id -> active.stream().noneMatch(e -> e.activationId() == id));
        for (var entry : active) if (entry.policy() instanceof PlayerMotionPolicy p && p.mode() == PlayerMotionPolicy.Mode.CRUISE) {
            int opposite = p.amount() > 0 ? AbstractPlayableSprite.INPUT_LEFT : AbstractPlayableSprite.INPUT_RIGHT;
            if ((sample.heldMask() & (AbstractPlayableSprite.INPUT_DOWN | opposite)) != 0) {
                cancelledCruises.add(entry.activationId()); source.report(entry.activationId(), "Cruise cancelled by player");
            }
        }
        int cruise = suppressed ? 0 : cruiseDirection(leader);
        if (cruise != 0) {
            int horizontal = AbstractPlayableSprite.INPUT_LEFT | AbstractPlayableSprite.INPUT_RIGHT;
            int direction = cruise < 0 ? AbstractPlayableSprite.INPUT_LEFT : AbstractPlayableSprite.INPUT_RIGHT;
            admitted = PlayerInputState.of((admitted.heldMask() & ~horizontal) | direction,
                    admitted.pressedMask() & ~horizontal, admitted.actionHeldMask(), admitted.actionPressedMask(),
                    admitted.startHeld(), admitted.startPressed());
        }
        return admitted;
    }

    public boolean blocksFreshJump(AbstractPlayableSprite sprite) {
        if (!free(sprite)) return false;
        for (var entry : entries()) if (entry.policy() instanceof InputControlPolicy p)
            return p.mode() == InputControlPolicy.Mode.NO_FRESH_JUMP;
        return false;
    }

    public int jumpImpulse(AbstractPlayableSprite sprite, int nativeImpulse) {
        return leader(sprite) && ordinary(sprite) ? scale(nativeImpulse, PlayerMotionPolicy.Mode.JUMP_SCALE, 100, 150) : nativeImpulse;
    }

    public int springResponse(AbstractPlayableSprite sprite, int nativeStrength, int routeMinimum, int directionalCap) {
        if (!leader(sprite) || sprite.getDead() || sprite.isDebugMode() || sprite.isObjectControlled()) return nativeStrength;
        BigInteger numerator = BigInteger.valueOf(100), denominator = BigInteger.ONE;
        boolean active = false;
        for (var entry : entries()) if (entry.policy() instanceof SpringResponsePolicy p) {
            active = true; numerator = numerator.multiply(BigInteger.valueOf(p.percent()));
            denominator = denominator.multiply(BigInteger.valueOf(100));
        }
        if (!active) return nativeStrength;
        int percent = numerator.divide(denominator).max(BigInteger.valueOf(75)).min(BigInteger.valueOf(135)).intValue();
        if (percent == 100) return nativeStrength;
        return new SpringResponsePolicy(percent).scale(nativeStrength, routeMinimum, directionalCap);
    }
    public short traction(AbstractPlayableSprite sprite, short nativeValue) {
        return leader(sprite) && ordinary(sprite) && !sprite.getAir()
                ? (short) scale(nativeValue, PlayerMotionPolicy.Mode.TRACTION, 25, 200) : nativeValue;
    }
    private int scale(int nativeValue, PlayerMotionPolicy.Mode mode, int minimum, int maximum) {
        BigInteger numerator = BigInteger.valueOf(100), denominator = BigInteger.ONE;
        for (var entry : entries()) {
            if (entry.policy() instanceof PlayerMotionPolicy p && p.mode() == mode) {
                numerator = numerator.multiply(BigInteger.valueOf(p.amount()));
                denominator = denominator.multiply(BigInteger.valueOf(100));
            }
        }
        int percent = numerator.divide(denominator).max(BigInteger.valueOf(minimum))
                .min(BigInteger.valueOf(maximum)).intValue();
        return nativeValue * percent / 100;
    }

    public void beforeMovement(AbstractPlayableSprite sprite) {
        prepareFluid(sprite);
        if (fluidActive(sprite) && sprite.getDrowningController() != null) sprite.getDrowningController().replenishAir();
        if (!free(sprite)) return;
        int horizontalForce = 0, forceCap = 0x800;
        for (var entry : entries()) if (entry.policy() instanceof PlayerMotionPolicy p
                && p.mode() == PlayerMotionPolicy.Mode.HORIZONTAL_FORCE) {
            horizontalForce += p.amount(); forceCap = Math.min(forceCap, p.speedCap());
        }
        horizontalForce = Math.max(-0x10, Math.min(0x10, horizontalForce));
        if (horizontalForce != 0) {
            // Sum contributions before clamping; opposite forces cancel independently of registration order.
            int acceleration = sprite.getAir() ? horizontalForce
                    : (com.openggf.physics.TrigLookupTable.cosHex(sprite.getAngle() & 0xFF) * horizontalForce) >> 8;
            int velocity = sprite.getAir() ? sprite.getXSpeed() : sprite.getGSpeed();
            int changed = force(velocity, acceleration, forceCap);
            if (sprite.getAir()) sprite.setXSpeed((short) changed); else sprite.setGSpeed((short) changed);
        }
        for (var entry : entries()) {
            if (!(entry.policy() instanceof PlayerMotionPolicy policy)) continue;
            switch (policy.mode()) {
                case MOMENTUM_PULSE -> {
                    if (!hasHorizontalMomentum(sprite) || !settleOneShot(entry, null)) continue;
                    sprite.setXSpeed((short) -sprite.getXSpeed());
                    sprite.setGSpeed((short) -sprite.getGSpeed());
                    int velocity = sprite.getAir() ? sprite.getXSpeed() : sprite.getGSpeed();
                    if (velocity != 0) sprite.setDirection(velocity < 0
                            ? com.openggf.physics.Direction.LEFT : com.openggf.physics.Direction.RIGHT);
                }
                case GROUND_STOP -> {
                    if (sprite.getAir() || !hasHorizontalMomentum(sprite) || !settleOneShot(entry, null)) continue;
                    sprite.setGSpeed((short) 0); sprite.setXSpeed((short) 0);
                }
                default -> { }
            }
        }
    }

    private static boolean hasHorizontalMomentum(AbstractPlayableSprite sprite) {
        return sprite.getAir() ? sprite.getXSpeed() != 0 : sprite.getXSpeed() != 0 || sprite.getGSpeed() != 0;
    }

    private boolean settleOneShot(TransientMutatorPolicySource.Entry entry, String failure) {
        if (!entry.oneShot() || !settledOneShots.add(entry.activationId())) return false;
        if (failure != null) source.report(entry.activationId(), failure);
        oneShotAttempts.remove(entry.activationId());
        return source.acknowledge(entry.activationId());
    }

    /** Runs even when a native control/routine owner skips the ordinary movement consumer. */
    private void advanceOneShotDeadlines() {
        var pending = entries().stream().filter(e -> e.oneShot() && e.policy() instanceof PlayerMotionPolicy p
                && (p.mode() == PlayerMotionPolicy.Mode.MOMENTUM_PULSE || p.mode() == PlayerMotionPolicy.Mode.GROUND_STOP)).toList();
        var activeIds = pending.stream().map(TransientMutatorPolicySource.Entry::activationId).collect(java.util.stream.Collectors.toSet());
        oneShotAttempts.keySet().retainAll(activeIds); settledOneShots.retainAll(activeIds);
        var visited = new java.util.HashSet<Long>();
        for (var entry : pending) {
            if (!visited.add(entry.activationId()) || settledOneShots.contains(entry.activationId())) continue;
            int attempts = oneShotAttempts.merge(entry.activationId(), 1,
                    (previous, next) -> Math.min(ONE_SHOT_NATIVE_TICK_LIMIT, previous + next));
            if (attempts >= ONE_SHOT_NATIVE_TICK_LIMIT || entry.agePresentations() >= ONE_SHOT_PRESENTATION_LIMIT)
                settleOneShot(entry, "Failed: no qualified motion opportunity before deadline");
        }
    }

    static int force(int velocity, int acceleration, int cap) {
        if (acceleration > 0 && velocity >= cap || acceleration < 0 && velocity <= -cap) return velocity;
        return acceleration > 0 ? Math.min(cap, velocity + acceleration) : Math.max(-cap, velocity + acceleration);
    }

    public int cruiseDirection(AbstractPlayableSprite sprite) {
        if (!free(sprite) || sprite.getAir() || sprite.isInWater()) return 0;
        int angle = sprite.getAngle() & 0xFF;
        if (angle > 0x10 && angle < 0xF0) return 0;
        for (var entry : entries()) if (!cancelledCruises.contains(entry.activationId()) && entry.policy() instanceof PlayerMotionPolicy p
                && p.mode() == PlayerMotionPolicy.Mode.CRUISE) return p.amount();
        return 0;
    }
    public boolean requestsRoll(AbstractPlayableSprite sprite) {
        return free(sprite) && !sprite.getAir() && Math.abs(sprite.getGSpeed()) >= 0x100
                && entries().stream().anyMatch(e -> e.policy() instanceof PlayerMotionPolicy p
                && p.mode() == PlayerMotionPolicy.Mode.ROLLING);
    }

    private boolean reversesGravity() {
        if (!gravityQualified || retired) return false;
        boolean reverse = false;
        for (var entry : entries()) if (entry.policy() instanceof SignedGravityPolicy p) reverse ^= p.reversesAt(entry.agePresentations());
        return reverse;
    }

    void sampleGravityQualification() {
        var leader = sprites == null ? null : sprites.getMainPlayable();
        gravityQualified = free(leader) && !leader.isInWater() && !fluidActive(leader);
        if (gravityQualified) for (var sidekick : sprites.getSidekicks())
            if (!ordinary(sidekick) || sidekick.isInWater()) { gravityQualified = false; break; }
    }

    private void prepareFluid(AbstractPlayableSprite sprite) {
        if (!leader(sprite)) return;
        var activation = entries().stream().filter(e -> e.policy() instanceof FluidContextPolicy).findFirst().orElse(null);
        if (activation == null) { fluidActivation = 0; return; }
        if (activation.activationId() != fluidActivation) {
            fluidActivation = activation.activationId(); fluidX = sprite.getCentreX(); fluidY = sprite.getCentreY();
        }
    }

    /** Read-only captured region, shared by movement and its bounded water presentation. */
    public FluidRegion fluidRegion() {
        for (var entry : entries()) if (entry.activationId() == fluidActivation && entry.policy() instanceof FluidContextPolicy p)
            return new FluidRegion(fluidX, fluidY, p.radius());
        return null;
    }

    public boolean fluidActive(AbstractPlayableSprite sprite) {
        if (!fluidQualified(sprite)) return false;
        var region = fluidRegion();
        return region != null && region.contains(sprite.getCentreX(), sprite.getCentreY());
    }

    /** Freeze only the local overlay through one player dispatch, including both gravity halves. */
    public void beginFluidMovement(AbstractPlayableSprite sprite) {
        if (!leader(sprite)) return;
        prepareFluid(sprite); fluidMovementSample = fluidActive(sprite); fluidMovementActive = true;
    }
    public void endFluidMovement(AbstractPlayableSprite sprite) { if (leader(sprite)) fluidMovementActive = false; }
    public boolean movementFluidActive(AbstractPlayableSprite sprite) {
        return leader(sprite) && fluidMovementActive ? fluidMovementSample : fluidActive(sprite);
    }

    private boolean fluidQualified(AbstractPlayableSprite sprite) {
        return leader(sprite) && !sprite.getDead() && !sprite.isHurt() && !sprite.isDrowningPreDeath()
                && !sprite.isHidden() && !sprite.getPinballMode() && !sprite.getPinballSpeedLock()
                && sprite.getForcedInputMask() == 0 && !sprite.getTailsCarryController().isCarryingMainCharacter()
                && (sprite.getSuperStateController() == null
                || sprite.getSuperStateController().getState() != com.openggf.sprites.playable.SuperState.TRANSFORMING)
                && !sprite.isDebugMode() && !sprite.isControlLocked() && !sprite.isObjectControlled()
                && !sprite.isSliding() && !sprite.isOnObject() && !sprite.isWaterSkimActive()
                && gameState != null && !gameState.isBossFightActive() && !gameState.isEndOfLevelActive();
    }

    public boolean underwaterAt(AbstractPlayableSprite player, int centreX, int centreY) {
        boolean nativeUnderwater = nativeWater != null && nativeWater.hasWater(waterZone, waterAct)
                && centreY > nativeWater.getGameplayWaterLevelY(waterZone, waterAct);
        // During unassembled fixtures the only known native observation is the player's current point.
        if (nativeWater == null && player != null && centreX == player.getCentreX() && centreY == player.getCentreY())
            nativeUnderwater = player.isInWater();
        var region = fluidRegion();
        return nativeUnderwater || fluidQualified(player) && region != null && region.contains(centreX, centreY);
    }

    private void bindNativeWater(com.openggf.level.LevelManager level) {
        if (level == null || level.getObjectManager() == null) { nativeWater = null; return; }
        nativeWater = level.getObjectManager().getObjectServices().waterSystem();
        waterZone = level.getFeatureZoneId(); waterAct = level.getFeatureActId();
    }

    public record FluidRegion(int centreX, int centreY, int radius) {
        public boolean contains(int x, int y) {
            long dx = (long) x - centreX, dy = (long) y - centreY;
            return dx * dx + dy * dy <= (long) radius * radius;
        }
    }

    @Override public <T> T getService(Class<T> type) {
        if (type == EffectiveFluidContextSource.class) return type.cast((EffectiveFluidContextSource) this::underwaterAt);
        return type == MotionMutatorRuntime.class ? type.cast(this) : null;
    }
    @Override public RewindSnapshottable<?> rewindAdapter() { return this; }
    @Override public void bindRoster(SpriteManager sprites) {
        if (source != null && gameState != null) bind(sprites, gameState, source);
    }
    /** The foundation calls this after the context managers and world policy graph are attached. */
    @Override public void bindGameplay(com.openggf.game.session.GameplayModeContext context) {
        requireEngineCaller();
        var policies = TransientMutatorPolicyAccess.source(context.getWorldSession());
        if (policies != null) {
            bind(context.getSpriteManager(), context.getGameStateManager(), policies);
            bindNativeWater(context.getLevelManager());
        }
    }
    @Override public void beforeNativeTick(com.openggf.LevelFrameContext context, com.openggf.control.LogicalInputSnapshot input) {
        ordinaryGameplay = true;
        advanceOneShotDeadlines();
        bindNativeWater(context.levelManager());
        sampleGravityQualification();
        if (sprites != null) prepareFluid(sprites.getMainPlayable());
        if (sprites != null) SpriteManagerInternalAccess.prepareMotionInputForNativeTick(sprites);
    }
    @Override public void onPresentation(com.openggf.game.session.GameplayModeContext context,
            com.openggf.control.LogicalInputSnapshot input, com.openggf.game.GameplayObservation.Phase phase) {
        bindNativeWater(context.getLevelManager());
        if (phase == com.openggf.game.GameplayObservation.Phase.ACTIVE) {
            ordinaryGameplay = true;
            if (sprites != null) prepareFluid(sprites.getMainPlayable());
        }
    }
    /** Clear departed gameplay contributions while paused, held and rewind presentations preserve them. */
    @Override public void leaveOrdinaryGameplay(com.openggf.game.GameplayObservation.Phase phase) {
        if (phase == com.openggf.game.GameplayObservation.Phase.SETUP
                || phase == com.openggf.game.GameplayObservation.Phase.STAGE
                || phase == com.openggf.game.GameplayObservation.Phase.TRANSITION
                || phase == com.openggf.game.GameplayObservation.Phase.CLOSED) {
            ordinaryGameplay = false; reset(); nativeWater = null;
        }
    }
    @Override public void beforeAssembly(LevelLoadCause cause) {
        if (cause != LevelLoadCause.DECODE_ONLY && cause != LevelLoadCause.PREVIEW) reset();
    }
    @Override public void failedAssembly(LevelLoadCause cause) { beforeAssembly(cause); }
    @Override public void closeScreens() { }
    @Override public void retire() { retired = true; reset(); }
    private void reset() {
        if (sprites != null) SpriteManagerInternalAccess.clearPreparedMotionInput(sprites);
        input.reset(); cancelledCruises.clear(); fluidActivation = 0; fluidX = fluidY = 0;
        inputControlGeneration = -1;
        oneShotAttempts.clear(); settledOneShots.clear();
        gravityQualified = fluidMovementActive = fluidMovementSample = false;
    }
    @Override public String key() { return "motion-mutators"; }
    @Override public Snapshot capture() { return new Snapshot(generation, input.capture(), fluidActivation, fluidX, fluidY,
            cancelledCruises, oneShotAttempts, settledOneShots, inputControlGeneration,
            gravityQualified, fluidMovementActive, fluidMovementSample, ordinaryGameplay); }
    @Override public void restore(Snapshot snapshot) {
        if (retired) throw new IllegalStateException("Motion owner is retired");
        if (snapshot.generation() != generation) throw new IllegalArgumentException("Motion snapshot belongs to another world");
        if (sprites != null) SpriteManagerInternalAccess.clearPreparedMotionInput(sprites);
        input.restore(snapshot.input()); fluidActivation = snapshot.fluidActivation();
        fluidX = snapshot.fluidX(); fluidY = snapshot.fluidY();
        cancelledCruises.clear(); cancelledCruises.addAll(snapshot.cancelledCruises());
        oneShotAttempts.clear(); oneShotAttempts.putAll(snapshot.oneShotAttempts());
        settledOneShots.clear(); settledOneShots.addAll(snapshot.settledOneShots());
        inputControlGeneration = snapshot.inputControlGeneration();
        gravityQualified = snapshot.gravityQualified(); fluidMovementActive = snapshot.fluidMovementActive();
        fluidMovementSample = snapshot.fluidMovementSample();
        ordinaryGameplay = snapshot.ordinaryGameplay();
    }
    public record Snapshot(long generation, MotionInputQueue.Snapshot input, long fluidActivation, int fluidX, int fluidY,
                           java.util.Set<Long> cancelledCruises, java.util.Map<Long, Integer> oneShotAttempts,
                           java.util.Set<Long> settledOneShots, long inputControlGeneration, boolean gravityQualified,
                           boolean fluidMovementActive, boolean fluidMovementSample, boolean ordinaryGameplay) {
        public Snapshot {
            cancelledCruises = java.util.Set.copyOf(cancelledCruises);
            oneShotAttempts = java.util.Map.copyOf(oneShotAttempts);
            settledOneShots = java.util.Set.copyOf(settledOneShots);
        }
    }
}
