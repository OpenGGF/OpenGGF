package com.openggf.game.mutators;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.HomingTargets;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.boss.InstagibBossTarget;
import com.openggf.level.objects.homing.HomingTarget;
import com.openggf.level.objects.homing.HomingTargetKind;
import com.openggf.physics.Direction;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * World-owned homing attack for the native leader. A jump re-press scans live objects, then the
 * runtime steers the leader's existing roll at the chosen target. Native touch, solid contact,
 * breakage, grabs and defeat remain the owners of what happens on arrival. Only plain values are
 * captured: the target is re-resolved from live objects every tick by its last aim point, so a
 * restored history never holds a stale object reference.
 */
public final class HomingAttackRuntime implements RewindSnapshottable<HomingAttackRuntime.Snapshot> {
    public enum Mode { OFF, HOMING, INSTAGIB }
    public enum Phase { IDLE, HOMING, DASH }

    /** Admitted policy: the mode and the persisted character keys that may use it as leader. */
    public record Policy(Mode mode, Set<String> characters) {
        public static final Policy STOCK = new Policy(Mode.OFF, Set.of());
        public Policy {
            Objects.requireNonNull(mode, "mode");
            characters = Set.copyOf(characters);
        }
        public boolean active() { return mode != Mode.OFF && !characters.isEmpty(); }
    }

    /** 10 px per tick: fast enough to break native rolling-only walls on arrival. */
    static final int HOMING_SPEED = 0xA00;
    static final int DASH_SPEED = 0x800;
    static final int MAX_HOMING_TICKS = 40;
    static final int FORWARD_RANGE = 160;
    static final int BEHIND_RANGE = 112;
    static final int VERTICAL_RANGE = 144;
    /** A moving target is followed while it stays this close to its previous aim point. */
    static final int TRACK_RADIUS = 48;
    /** Arrival distance at which a vanished breakable/grabbed target counts as reached. */
    static final int ARRIVAL_RADIUS = 32;
    static final int ATTACH_RELEASE_RADIUS = 8;
    static final int LOS_STEP = 8;
    static final int LOS_TARGET_CLEARANCE = 20;
    static final int BADNIK_CHAIN_POP = -0x500;
    static final int BOSS_REBOUND_SPEED = 0x400;
    static final int FLAME_AFTER_TICKS = 8;
    /** Fire-shield dash camera lag: H_scroll_frame_offset $2000 (sonic3k.asm:23463). */
    public static final int CAMERA_LAG_FRAMES = 32;
    /** Instagib invulnerability continues this long after a strike, covering its rebound arc. */
    static final int INSTAGIB_GUARD_TICKS = 32;
    /** Heat reaches full intensity after this many homing ticks. */
    static final int HEAT_BUILD_TICKS = 4;
    static final int SHARDS_PER_BURST = 16;
    static final int MAX_SHARDS = 160;
    static final int SHARD_TICKS = 48;
    static final int SHARD_GRAVITY = 0x30;
    /** A shard strikes a badnik whose centre is within this box half-size. */
    static final int SHARD_HIT_RADIUS = 16;

    /**
     * One Instagib shard in 8.8 world coordinates. Shards are gameplay: they fly from a defeated
     * enemy with the strike's momentum and strike other badniks natively. {@code burst} and
     * {@code index} only select which captured picture tile draws it.
     */
    public record Shard(long burst, int index, int x, int y, int vx, int vy, int age) { }

    private final Supplier<Policy> policies;
    private SpriteManager roster;
    private Phase phase = Phase.IDLE;
    private boolean spent;
    private int ticks;
    private int targetX, targetY;
    private HomingTargetKind kind = HomingTargetKind.BADNIK;
    private Mode launchMode = Mode.OFF;
    private int flameTicks;
    private int heatTicks;
    private int guardTicks;
    private java.util.List<Shard> shards = java.util.List.of();
    private long nextBurst;
    private final HomingAttackEffects effects = new HomingAttackEffects();
    // Presentation-only reticle, recomputed every leader tick from live objects.
    private boolean reticleVisible, reticleLocked;
    private int reticleX, reticleY;

    public HomingAttackRuntime(Supplier<Policy> policies) {
        this.policies = Objects.requireNonNull(policies, "policies");
    }

    public void bindRoster(SpriteManager sprites) { roster = sprites; }

    public Policy policy() { return Objects.requireNonNull(policies.get(), "homing policy"); }

    public Phase phase() { return phase; }

    /** True while an Instagib launch is homing or its trail is still fading. */
    public boolean flameActive() { return launchMode == Mode.INSTAGIB && flameTicks > 0; }

    /** Instagib strike plus its trail: native hurt and hazard owners treat the leader as invulnerable. */
    public boolean invulnerable(PlayableEntity player) {
        return launchMode == Mode.INSTAGIB && leader(player) && (phase != Phase.IDLE || guardTicks > 0);
    }

    /** 0..1 friction-heat build-up in front of the striking leader. */
    float heat() {
        if (launchMode != Mode.INSTAGIB || !flameActive()) return 0f;
        float built = 0.55f + 0.45f * Math.min(1f, heatTicks / (float) HEAT_BUILD_TICKS);
        // After the strike the heat bleeds away over the trail's fade.
        return phase == Phase.IDLE ? built * flameTicks / (float) FLAME_AFTER_TICKS : built;
    }

    java.util.List<Shard> shards() { return shards; }

    boolean reticleVisible() { return reticleVisible; }

    /** True when an Instagib homing strike, not an ordinary jump, is the current attack. */
    public boolean instagibStrike(PlayableEntity player) {
        return launchMode == Mode.INSTAGIB && leader(player) && phase != Phase.IDLE;
    }

    /**
     * For objects whose native response distinguishes a grounded roll from an airborne one: a
     * homing strike arrives as if spindashed in, so it takes the grounded branch.
     */
    public static boolean striking(com.openggf.game.session.WorldSession world, PlayableEntity player) {
        var runtime = com.openggf.game.session.WorldSessionPolicyAccess.getService(world, HomingAttackRuntime.class);
        return runtime != null && runtime.leader(player) && runtime.phase == Phase.HOMING;
    }

    /** Ends a strike that a special-touch object resolved itself, with the bounded boss rebound. */
    public static void struck(com.openggf.game.session.WorldSession world, PlayableEntity player) {
        var runtime = com.openggf.game.session.WorldSessionPolicyAccess.getService(world, HomingAttackRuntime.class);
        if (runtime != null) runtime.afterAttack(player, null, true);
    }

    public boolean enabledFor(AbstractPlayableSprite sprite) {
        var policy = policy();
        return policy.active() && leader(sprite) && policy.characters().contains(sprite.characterKey().persisted());
    }

    private boolean leader(PlayableEntity player) {
        return player != null && roster != null && roster.getMainPlayable() == player;
    }

    /** Native owners that move or hold the player independently always cancel the attack. */
    private static boolean free(AbstractPlayableSprite sprite) {
        return sprite.getAir() && !sprite.getDead() && !sprite.isHurt() && !sprite.isDebugMode()
                && !sprite.isObjectControlled() && !sprite.isControlLocked() && !sprite.getPinballMode()
                && sprite.getForcedInputMask() == 0 && !sprite.getSpringing();
    }

    /** Once per player movement tick, before input and air control. */
    public void beforeMovement(AbstractPlayableSprite sprite) {
        if (!leader(sprite)) return;
        tickShards(sprite);
        effects.tick(this, sprite);
        if (phase != Phase.IDLE) guardTicks = INSTAGIB_GUARD_TICKS;
        else if (guardTicks > 0) guardTicks--;
        if (phase == Phase.HOMING) heatTicks++;
        else if (flameTicks > 0) flameTicks--;
        if (flameTicks == 0 && phase == Phase.IDLE) heatTicks = 0;
        else if (phase == Phase.IDLE && heatTicks < HEAT_BUILD_TICKS) heatTicks++;
        if (!sprite.getAir() || !enabledFor(sprite) || !free(sprite)) {
            // Landing, a grab, hurt, death or a policy change ends the air action and refreshes it.
            phase = Phase.IDLE;
            spent = false;
            ticks = 0;
        }
        updateReticle(sprite);
    }

    private void updateReticle(AbstractPlayableSprite sprite) {
        reticleLocked = phase == Phase.HOMING;
        if (reticleLocked) {
            reticleVisible = true;
            reticleX = targetX;
            reticleY = targetY;
            return;
        }
        var target = enabledFor(sprite) && free(sprite) && !spent ? select(sprite) : null;
        reticleVisible = target != null;
        if (target != null) {
            reticleX = target.x();
            reticleY = target.y();
        }
    }

    /** Shards fly under gravity and strike any badnik they pass through, as the leader's attack. */
    private void tickShards(AbstractPlayableSprite leader) {
        if (shards.isEmpty()) return;
        LevelManager level = leader.currentLevelManagerIfAvailable();
        var objects = level == null ? null : level.getObjectManager();
        var badniks = new java.util.ArrayList<ObjectInstance>();
        if (objects != null) {
            for (ObjectInstance object : objects.getActiveObjects()) {
                var target = HomingTargets.classify(object, leader);
                if (target != null && target.kind() == HomingTargetKind.BADNIK) badniks.add(object);
            }
        }
        var moved = new java.util.ArrayList<Shard>(shards.size());
        for (var shard : shards) {
            if (shard.age() + 1 >= SHARD_TICKS) continue;
            moved.add(new Shard(shard.burst(), shard.index(), shard.x() + shard.vx(), shard.y() + shard.vy(),
                    shard.vx(), shard.vy() + SHARD_GRAVITY, shard.age() + 1));
        }
        shards = java.util.List.copyOf(moved);
        // A kill bursts new shards onto the list; they first fly on the next tick.
        for (var shard : moved) {
            for (var badnik : badniks) {
                if (badnik.isDestroyed()) continue;
                if (Math.abs((shard.x() >> 8) - badnik.getCollisionX()) > SHARD_HIT_RADIUS
                        || Math.abs((shard.y() >> 8) - badnik.getCollisionY()) > SHARD_HIT_RADIUS) continue;
                strikeWithShard(objects, leader, badnik, shard);
            }
        }
    }

    private void strikeWithShard(com.openggf.level.objects.ObjectManager objects, AbstractPlayableSprite leader,
                                 ObjectInstance badnik, Shard shard) {
        if (!(badnik instanceof com.openggf.level.objects.TouchResponseAttackable attackable)
                || !(badnik instanceof com.openggf.level.objects.TouchResponseProvider provider)) return;
        int cx = badnik.getCollisionX(), cy = badnik.getCollisionY();
        var result = new com.openggf.level.objects.TouchResponseResult(provider.getCollisionFlags() & 0x3F,
                8, 8, com.openggf.level.objects.TouchCategory.ENEMY, 0);
        // The native badnik owner runs its own defeat: explosion, animal and score.
        com.openggf.level.objects.ObjectCallbackDispatch.run(objects, badnik,
                () -> attackable.onPlayerAttack(leader, result));
        if (badnik.isDestroyed()) burst(cx, cy, shard.vx(), shard.vy());
    }

    /** Throw a deterministic ring of shards from a defeated enemy along the strike's momentum. */
    private void burst(int centreX, int centreY, int impulseX, int impulseY) {
        long id = nextBurst++;
        effects.capture(id, centreX, centreY);
        var next = new java.util.ArrayList<>(shards);
        for (int i = 0; i < SHARDS_PER_BURST && next.size() < MAX_SHARDS; i++) {
            double angle = Math.PI * 2 * i / SHARDS_PER_BURST + 0.2;
            int speed = 0x280 + (i % 4) * 0xA0;
            int vx = impulseX * 3 / 4 + (int) Math.round(Math.cos(angle) * speed);
            int vy = impulseY * 3 / 4 + (int) Math.round(Math.sin(angle) * speed) - 0x180;
            next.add(new Shard(id, i, centreX << 8, centreY << 8, vx, vy, 0));
        }
        shards = java.util.List.copyOf(next);
    }

    /**
     * The jump re-press, tried after creator character hooks and before native double-jump moves.
     * Returns true only for a launch; a spent air action leaves the press to the native moves.
     */
    public boolean tryLaunch(AbstractPlayableSprite sprite) {
        if (!enabledFor(sprite) || !free(sprite) || spent) return false;
        var target = select(sprite);
        if (target == null) return false;
        launch(sprite, target);
        return true;
    }

    /** No target and no native move fired: the forward air dash. */
    public boolean tryAirDash(AbstractPlayableSprite sprite) {
        if (!enabledFor(sprite) || !free(sprite) || spent) return false;
        boolean right = sprite.getDirection() == Direction.RIGHT;
        sprite.setXSpeed((short) (right ? DASH_SPEED : -DASH_SPEED));
        if (sprite.getYSpeed() > 0) sprite.setYSpeed((short) 0);
        sprite.setGSpeed(sprite.getXSpeed());
        spent = true;
        phase = Phase.DASH;
        ticks = 0;
        launchMode = policy().mode();
        flameTicks = FLAME_AFTER_TICKS;
        heatTicks = HEAT_BUILD_TICKS / 2;
        guardTicks = INSTAGIB_GUARD_TICKS;
        return true;
    }

    private void launch(AbstractPlayableSprite sprite, HomingTarget target) {
        spent = true;
        phase = Phase.HOMING;
        ticks = 0;
        kind = target.kind();
        targetX = target.x();
        targetY = target.y();
        launchMode = policy().mode();
        flameTicks = FLAME_AFTER_TICKS;
        guardTicks = INSTAGIB_GUARD_TICKS;
        aim(sprite, targetX, targetY);
    }

    /**
     * Replaces native air control while homing. Returns true when this tick's velocity is owned
     * by the attack; native movement, gravity and terrain collision still run afterwards.
     */
    public boolean steer(AbstractPlayableSprite sprite) {
        if (!leader(sprite)) return false;
        if (phase == Phase.DASH) {
            phase = Phase.IDLE;
            return false;
        }
        if (phase != Phase.HOMING) return false;
        if (!free(sprite) || ++ticks > MAX_HOMING_TICKS) {
            settle(sprite, true);
            return false;
        }
        var tracked = track(sprite);
        if (tracked == null) {
            boolean arrived = distance(sprite.getCentreX(), sprite.getCentreY(), targetX, targetY) <= ARRIVAL_RADIUS;
            settle(sprite, !arrived);
            if (arrived) spent = false;
            return false;
        }
        targetX = tracked.x();
        targetY = tracked.y();
        if (!kind.attack() && distance(sprite.getCentreX(), sprite.getCentreY(), targetX, targetY) <= ATTACH_RELEASE_RADIUS) {
            // Hand the last few pixels to the native grab test at the target's own point.
            sprite.setXSpeed((short) ((targetX - sprite.getCentreX()) << 8));
            sprite.setYSpeed((short) ((targetY - sprite.getCentreY()) << 8));
            phase = Phase.IDLE;
            spent = false;
            return true;
        }
        aim(sprite, targetX, targetY);
        return true;
    }

    private void settle(AbstractPlayableSprite sprite, boolean missed) {
        phase = Phase.IDLE;
        if (missed) {
            sprite.setXSpeed((short) (sprite.getXSpeed() / 2));
            sprite.setYSpeed((short) (sprite.getYSpeed() / 2));
        } else {
            clamp(sprite, 0x600);
        }
    }

    private static void aim(AbstractPlayableSprite sprite, int x, int y) {
        double dx = x - sprite.getCentreX(), dy = y - sprite.getCentreY();
        double length = Math.hypot(dx, dy);
        if (length < 1) return;
        short vx = (short) Math.round(dx / length * HOMING_SPEED);
        short vy = (short) Math.round(dy / length * HOMING_SPEED);
        sprite.setXSpeed(vx);
        sprite.setYSpeed(vy);
        sprite.setGSpeed(vx);
        if (vx != 0) sprite.setDirection(vx > 0 ? Direction.RIGHT : Direction.LEFT);
        // Keep the native attacking roll; homing never leaves the jump ball.
        sprite.setRolling(true);
    }

    private static void clamp(AbstractPlayableSprite sprite, int magnitude) {
        double vx = sprite.getXSpeed(), vy = sprite.getYSpeed(), length = Math.hypot(vx, vy);
        if (length <= magnitude) return;
        sprite.setXSpeed((short) Math.round(vx / length * magnitude));
        sprite.setYSpeed((short) Math.round(vy / length * magnitude));
    }

    /** Called by the native touch pass before an attack is delivered to {@code target}. */
    public void beforeAttack(PlayableEntity player, ObjectInstance target) {
        if (!instagibStrike(player)) return;
        if (target instanceof InstagibBossTarget boss) boss.collapseToFinalHit();
        if (target != null) burst(target.getCollisionX(), target.getCollisionY(),
                player.getXSpeed(), player.getYSpeed());
    }

    /**
     * Called after the native touch pass resolved an attack and its rebound. A homing or dash
     * strike refreshes the air action so attacks can chain; the rebound is shaped for the next one.
     */
    public void afterAttack(PlayableEntity player, ObjectInstance target, boolean boss) {
        if (!leader(player) || phase == Phase.IDLE || !(player instanceof AbstractPlayableSprite sprite)) return;
        phase = Phase.IDLE;
        spent = false;
        if (boss) {
            // Bounce off: keep the native reflected direction but bound its size and lift.
            double vx = sprite.getXSpeed(), vy = sprite.getYSpeed(), length = Math.hypot(vx, vy);
            if (length >= 1) {
                sprite.setXSpeed((short) Math.round(vx / length * BOSS_REBOUND_SPEED));
                sprite.setYSpeed((short) Math.min(-0x200, Math.round(vy / length * BOSS_REBOUND_SPEED)));
            }
        } else {
            sprite.setXSpeed((short) (sprite.getXSpeed() >> 2));
            sprite.setYSpeed((short) BADNIK_CHAIN_POP);
        }
    }

    private HomingTarget select(AbstractPlayableSprite sprite) {
        LevelManager level = sprite.currentLevelManagerIfAvailable();
        if (level == null || level.getObjectManager() == null) return null;
        int px = sprite.getCentreX(), py = sprite.getCentreY();
        boolean right = sprite.getDirection() == Direction.RIGHT;
        HomingTarget best = null;
        boolean bestForward = false;
        int bestDistance = 0;
        for (ObjectInstance object : level.getObjectManager().getActiveObjects()) {
            var target = HomingTargets.classify(object, sprite);
            if (target == null) continue;
            int dx = target.x() - px, dy = target.y() - py;
            boolean forward = right ? dx >= 0 : dx <= 0;
            int distance = distance(px, py, target.x(), target.y());
            if (Math.abs(dy) > VERTICAL_RANGE || distance > (forward ? FORWARD_RANGE : BEHIND_RANGE)) continue;
            if (best != null && !better(target, forward, distance, best, bestForward, bestDistance)) continue;
            if (!clearLine(level, px, py, target.x(), target.y())) continue;
            best = target;
            bestForward = forward;
            bestDistance = distance;
        }
        return best;
    }

    /** Hidden priority, then facing, then target kind, then distance. */
    static boolean better(HomingTarget a, boolean aForward, int aDistance,
                          HomingTarget b, boolean bForward, int bDistance) {
        if (a.priority() != b.priority()) return a.priority() > b.priority();
        if (aForward != bForward) return aForward;
        if (a.kind() != b.kind()) return a.kind().rank() < b.kind().rank();
        return aDistance < bDistance;
    }

    private HomingTarget track(AbstractPlayableSprite sprite) {
        LevelManager level = sprite.currentLevelManagerIfAvailable();
        if (level == null || level.getObjectManager() == null) return null;
        HomingTarget nearest = null;
        int nearestDistance = TRACK_RADIUS + 1;
        for (ObjectInstance object : level.getObjectManager().getActiveObjects()) {
            var target = HomingTargets.classify(object, sprite);
            if (target == null || target.kind() != kind) continue;
            int distance = distance(targetX, targetY, target.x(), target.y());
            if (distance < nearestDistance) {
                nearest = target;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    /** Samples the foreground collision path; a point well inside solid terrain blocks the line. */
    private static boolean clearLine(LevelManager level, int x0, int y0, int x1, int y1) {
        double length = Math.hypot(x1 - x0, y1 - y0);
        for (double travelled = LOS_STEP; travelled < length - LOS_TARGET_CLEARANCE; travelled += LOS_STEP) {
            int x = (int) Math.round(x0 + (x1 - x0) * travelled / length);
            int y = (int) Math.round(y0 + (y1 - y0) * travelled / length);
            if (ObjectTerrainUtils.checkFloorDist(level, x, y).distance() < -2) return false;
        }
        return true;
    }

    private static int distance(int x0, int y0, int x1, int y1) {
        return (int) Math.round(Math.hypot(x1 - x0, y1 - y0));
    }

    /**
     * Renderer seam: the sprite table about to be displayed. Samples the flame trail and keeps the
     * pixels a later strike can throw. Presentation only; gameplay never reads it back.
     */
    public void observeFrame(com.openggf.graphics.SpritePresentation.Frame frame, int cameraX, int cameraY) {
        var leader = roster == null ? null : roster.getMainPlayable();
        effects.observe(frame, cameraX, cameraY, flameActive() && leader != null,
                leader == null ? 0 : leader.getCentreX(), leader == null ? 0 : leader.getCentreY());
    }

    /** Renderer seam: draw the Instagib afterimages and shards behind the live sprite table. */
    public void drawEffects(com.openggf.graphics.GraphicsManager graphics, int cameraX, int cameraY) {
        effects.drawBehind(graphics, cameraX, cameraY, shards);
    }

    /** Renderer seam: friction heat, embers and the lock-on reticle above the live sprite table. */
    public void drawOverlay(com.openggf.graphics.GraphicsManager graphics, int cameraX, int cameraY) {
        var leader = roster == null ? null : roster.getMainPlayable();
        effects.drawFront(graphics, cameraX, cameraY, heat(), leader,
                reticleVisible, reticleLocked, reticleX, reticleY);
    }

    public void reset() {
        effects.release();
        phase = Phase.IDLE;
        spent = false;
        ticks = 0;
        launchMode = Mode.OFF;
        flameTicks = 0;
        heatTicks = 0;
        guardTicks = 0;
        shards = java.util.List.of();
        reticleVisible = false;
    }

    @Override public String key() { return "homing-attack"; }

    @Override public Snapshot capture() {
        return new Snapshot(phase, spent, ticks, targetX, targetY, kind, launchMode, flameTicks,
                heatTicks, guardTicks, shards, nextBurst);
    }

    @Override public void restore(Snapshot snapshot) {
        // Effects are presentation-only work from the abandoned future.
        effects.clear();
        phase = snapshot.phase();
        spent = snapshot.spent();
        ticks = snapshot.ticks();
        targetX = snapshot.targetX();
        targetY = snapshot.targetY();
        kind = snapshot.kind();
        launchMode = snapshot.launchMode();
        flameTicks = snapshot.flameTicks();
        heatTicks = snapshot.heatTicks();
        guardTicks = snapshot.guardTicks();
        shards = snapshot.shards();
        nextBurst = snapshot.nextBurst();
        reticleVisible = false;
    }

    @Override public void resetForMissingSnapshot() { reset(); }

    public record Snapshot(Phase phase, boolean spent, int ticks, int targetX, int targetY,
                           HomingTargetKind kind, Mode launchMode, int flameTicks,
                           int heatTicks, int guardTicks, java.util.List<Shard> shards, long nextBurst) {
        public Snapshot {
            shards = java.util.List.copyOf(shards);
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(launchMode, "launchMode");
            if (ticks < 0 || flameTicks < 0) throw new IllegalArgumentException("Negative homing clock");
        }
    }
}
