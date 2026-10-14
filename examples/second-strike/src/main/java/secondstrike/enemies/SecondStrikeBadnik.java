package secondstrike.enemies;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractBadnikInstance;
import com.openggf.level.objects.DestructionEffects;
import com.openggf.level.objects.ModRewindRecreatable;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.PoweredScreenAttackable;
import com.openggf.level.objects.TerrainSurface;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectCombatRules;
import com.openggf.level.objects.ObjectPresentationMetadata;
import com.openggf.game.mutators.NativeObjectFieldTarget;
import com.openggf.level.objects.SubpixelMotion;

import java.util.List;

/** Common native touch/destruction and bounded per-instance movement. */
abstract class SecondStrikeBadnik extends AbstractBadnikInstance
        implements ModRewindRecreatable, PoweredScreenAttackable, NativeObjectFieldTarget {
    private static final int HIT_COOLDOWN = 24;
    protected int anchorX;
    protected int anchorY;
    protected int age;
    protected int phase;
    protected int timer;
    protected int targetX;
    protected int xFraction;
    protected int yFraction;
    protected int remainingHits;
    private int hitCooldown;
    private boolean awaitingSeparation;

    protected SecondStrikeBadnik(ObjectSpawn spawn, String name, int hits) {
        super(spawn, name);
        remainingHits = hits;
        anchorX = spawn.x();
        anchorY = spawn.y();
        facingLeft = (spawn.renderFlags() & 1) == 0;
    }

    /** Executed object updates own this cooldown, not the V-int value passed to update. */
    protected final boolean updateHitAndLifetime(PlayableEntity player) {
        if (!hasLiveLifetimeOwner()) {
            ObjectLifetimeOps.destroyLatched(this);
            return false;
        }
        if (hitCooldown > 0) hitCooldown--;
        if (awaitingSeparation) {
            boolean near = inRange(player, 48, 64);
            for (PlayableEntity participant : services().playerQuery()
                    .playersFor(ObjectPlayerParticipationPolicy.NATIVE_P1_P2)) {
                near |= inRange(participant, 48, 64);
            }
            if (!near) awaitingSeparation = false;
        }
        return true;
    }

    protected boolean hasLiveLifetimeOwner() { return true; }
    /** Runs before native reward/slot transfer can retire this exact identity. */
    protected void onFinalDefeat() { }

    @Override public Kind fieldKind() { return Kind.BADNIK; }
    @Override public void displaceForField(int dx, int dy) {
        currentX += dx; currentY += dy;
        updateDynamicSpawn(currentX, currentY);
    }
    @Override public ObjectPresentationMetadata presentationMetadata() {
        return new ObjectPresentationMetadata(ObjectPresentationMetadata.Role.BADNIK, currentX, currentY);
    }

    protected boolean damageImmune() { return false; }
    protected void onNonfatalHit() { }
    private boolean acceptsHit() {
        return !isDestroyed() && hasLiveLifetimeOwner() && !damageImmune()
                && hitCooldown == 0 && !awaitingSeparation;
    }

    @Override public int getCollisionProperty() {
        // S3K Touch_Enemy: nonzero uses native velocity rebound; zero uses final kill bounce.
        return Math.max(0, remainingHits - 1);
    }

    @Override public final void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if (!acceptsHit()) return;
        if (remainingHits > 1) nonfatalHit();
        else { remainingHits = 0; onFinalDefeat(); DestructionEffects.defeatBadnik(this, player, false); }
    }

    private void nonfatalHit() {
        remainingHits--;
        hitCooldown = HIT_COOLDOWN;
        awaitingSeparation = true;
        onNonfatalHit();
        services().playSfx(0x6e); // Native S3K sfx_BossHit; no explosion/animal/score until final hit.
    }

    protected final PlayableEntity nearestTarget(int horizontal, int vertical) {
        return services().playerQuery().nearestByRomX(ObjectPlayerParticipationPolicy.NATIVE_P1_P2, currentX,
                player -> inRange(player, horizontal, vertical)).player();
    }

    protected final boolean inRange(PlayableEntity player, int horizontal, int vertical) {
        return player != null && !player.getDead()
                && Math.abs(player.getCentreX() - currentX) <= horizontal
                && Math.abs(player.getCentreY() - currentY) <= vertical;
    }

    protected final void commitTarget(PlayableEntity player) {
        targetX = player.getCentreX();
        facingLeft = targetX < currentX;
    }

    protected final SpotterBadnik caller() {
        for (SpotterBadnik spotter : services().objectQuery().activeObjectsOfType(SpotterBadnik.class)) {
            if (!spotter.isDestroyed() && spotter.isCalling() && spotter.activeOnScreen()
                    && Math.abs(spotter.getX() - currentX) <= 240
                    && Math.abs(spotter.getY() - currentY) <= 128) return spotter;
        }
        return null;
    }

    protected final boolean startTarget(int horizontal, int vertical) {
        if (!ObjectCombatRules.permitsTargeting(services())) return false;
        PlayableEntity player = nearestTarget(horizontal, vertical);
        if (player != null) {
            commitTarget(player);
            return true;
        }
        SpotterBadnik caller = caller();
        if (caller == null) return false;
        targetX = caller.targetX;
        facingLeft = targetX < currentX;
        return true;
    }

    protected final void moveFixed() {
        var motion = new SubpixelMotion.State(currentX, currentY, xFraction, yFraction, xVelocity, yVelocity);
        SubpixelMotion.moveSprite2(motion);
        currentX = motion.x; currentY = motion.y;
        xFraction = motion.xSub; yFraction = motion.ySub;
    }

    /** Native floor sensors, with the same small ledge tolerance as Rhinobot_CheckFloorAhead. */
    protected final boolean floorAhead(int nextX) {
        TerrainSurface floor = services().floorSurface(nextX, currentY + 16,
                TerrainSurface.Layer.FOREGROUND, TerrainSurface.Path.PRIMARY);
        if (!floor.foundSurface() || floor.distance() < -1 || floor.distance() >= 12) return false;
        currentY += floor.distance();
        return true;
    }

    protected final boolean activeOnScreen() {
        // Dormant ahead of the viewport; normal ObjectManager range unloading owns respawn.
        return isOnScreen(32);
    }

    @Override public int getCollisionFlags() {
        return isDestroyed() || !hasLiveLifetimeOwner() || hitCooldown > 0 || awaitingSeparation
                ? 0 : super.getCollisionFlags();
    }

    @Override public void onPoweredScreenAttack(PlayableEntity player) {
        if (!acceptsHit()) return;
        if (remainingHits > 1) { nonfatalHit(); return; }
        remainingHits = 0;
        onFinalDefeat();
        DestructionEffects.defeatBadnik(this, player, true);
    }

    @Override protected DestructionEffects.DestructionConfig getDestructionConfig() {
        // Host recipe retains native explosion, animal, points popup and score order.
        return DestructionEffects.sonic3kBadnik();
    }

    protected final void draw(String key, int frame, int x, int y) {
        var renderer = getRenderer(key);
        if (renderer != null) renderer.drawFrameIndex(frame, x, y, !facingLeft, false);
    }

    @Override public final void appendRenderCommands(List<GLCommand> commands) {
        if (!isDestroyed() && hasLiveLifetimeOwner() && (hitCooldown == 0 || (hitCooldown & 2) == 0)) drawBody();
    }

    protected abstract void drawBody();
}
