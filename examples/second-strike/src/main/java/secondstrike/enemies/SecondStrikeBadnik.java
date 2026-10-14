package secondstrike.enemies;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.objects.Sonic3kPointsObjectInstance;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractBadnikInstance;
import com.openggf.level.objects.AnimalObjectInstance;
import com.openggf.level.objects.DestructionEffects;
import com.openggf.level.objects.ModRewindRecreatable;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.PoweredScreenAttackable;
import com.openggf.level.objects.TerrainSurface;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;

import java.util.List;

/** Common native touch/destruction and bounded per-instance movement. */
abstract class SecondStrikeBadnik extends AbstractBadnikInstance
        implements ModRewindRecreatable, PoweredScreenAttackable {
    protected int anchorX;
    protected int anchorY;
    protected int age;
    protected int phase;
    protected int timer;
    protected int targetX;
    protected int targetY;
    protected int xFraction;
    protected int yFraction;
    protected int remainingHits;
    private int hitCooldown;
    private boolean awaitingSeparation;
    private boolean companion;
    private boolean parentDefeated;
    private int parentAnchorX;
    private int parentAnchorY;
    private int parentLayoutIndex;

    protected SecondStrikeBadnik(ObjectSpawn spawn, String name, int hits) {
        super(spawn, name);
        remainingHits = hits;
        anchorX = spawn.x();
        anchorY = spawn.y();
        facingLeft = (spawn.renderFlags() & 1) == 0;
    }

    /** Executed object updates own this cooldown, not the V-int value passed to update. */
    protected final boolean updateHitAndLifetime(PlayableEntity player) {
        if (!hasLiveParent()) {
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

    final void attachToParent(ArtilleryBadnik parent) {
        companion = true;
        parentAnchorX = parent.anchorX;
        parentAnchorY = parent.anchorY;
        parentLayoutIndex = parent.getSpawn().layoutIndex();
    }

    private boolean hasLiveParent() {
        if (!companion) return true;
        if (!activeOnScreen()) return false;
        if (parentDefeated) return true;
        return services().objectQuery().activeObjectsOfType(ArtilleryBadnik.class).stream()
                .anyMatch(parent -> !parent.isDestroyed() && parent.activeOnScreen()
                        && parent.anchorX == parentAnchorX && parent.anchorY == parentAnchorY
                        && parent.getSpawn().layoutIndex() == parentLayoutIndex);
    }

    final boolean companionOf(int x, int y, int layoutIndex) {
        return companion && parentAnchorX == x && parentAnchorY == y && parentLayoutIndex == layoutIndex;
    }

    final void releaseFromDefeatedParent(ArtilleryBadnik parent) {
        if (companionOf(parent.anchorX, parent.anchorY, parent.getSpawn().layoutIndex())) parentDefeated = true;
    }

    private void releaseCompanions() {
        if (this instanceof ArtilleryBadnik parent) {
            for (GuardBadnik ally : services().objectQuery().activeObjectsOfType(GuardBadnik.class)) {
                ally.releaseFromDefeatedParent(parent);
            }
        }
    }

    protected boolean damageImmune() { return false; }
    protected void onNonfatalHit() { }
    private boolean acceptsHit() {
        return !isDestroyed() && hasLiveParent() && !damageImmune()
                && hitCooldown == 0 && !awaitingSeparation;
    }

    @Override public int getCollisionProperty() {
        // S3K Touch_Enemy: nonzero uses native velocity rebound; zero uses final kill bounce.
        return Math.max(0, remainingHits - 1);
    }

    @Override public final void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if (!acceptsHit()) return;
        if (remainingHits > 1) nonfatalHit();
        else { remainingHits = 0; releaseCompanions(); destroyBadnik(player); }
    }

    private void nonfatalHit() {
        remainingHits--;
        hitCooldown = 24;
        awaitingSeparation = true;
        onNonfatalHit();
        services().playSfx(0x6e); // Native S3K sfx_BossHit; no explosion/animal/score until final hit.
    }

    protected final PlayableEntity nearestPlayer(PlayableEntity main) {
        PlayableEntity p2 = services().playerQuery().nativeP2OrNull();
        if (main == null) return p2;
        return p2 != null && Math.abs(p2.getCentreX() - currentX) < Math.abs(main.getCentreX() - currentX)
                ? p2 : main;
    }

    protected final boolean inRange(PlayableEntity player, int horizontal, int vertical) {
        return player != null && !player.getDead()
                && Math.abs(player.getCentreX() - currentX) <= horizontal
                && Math.abs(player.getCentreY() - currentY) <= vertical;
    }

    protected final void commitTarget(PlayableEntity player) {
        targetX = player.getCentreX();
        targetY = player.getCentreY();
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

    protected final boolean startTarget(PlayableEntity player, int horizontal, int vertical) {
        if (inRange(player, horizontal, vertical)) {
            commitTarget(player);
            return true;
        }
        SpotterBadnik caller = caller();
        if (caller == null) return false;
        targetX = caller.targetX;
        targetY = caller.targetY;
        facingLeft = targetX < currentX;
        return true;
    }

    protected final void moveFixed() {
        int x = (currentX << 8) + xFraction + xVelocity;
        int y = (currentY << 8) + yFraction + yVelocity;
        currentX = x >> 8;
        currentY = y >> 8;
        xFraction = x & 255;
        yFraction = y & 255;
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
        return isDestroyed() || !hasLiveParent() || hitCooldown > 0 || awaitingSeparation
                ? 0 : super.getCollisionFlags();
    }

    @Override public void onPoweredScreenAttack(PlayableEntity player) {
        if (!acceptsHit()) return;
        if (remainingHits > 1) { nonfatalHit(); return; }
        remainingHits = 0;
        releaseCompanions();
        int slot = ObjectLifetimeOps.detachSlotForTransfer(this);
        ObjectLifetimeOps.destroyLatched(this);
        DestructionEffects.destroyBadnikPowered(currentX, currentY, spawn, slot, player,
                services(), getDestructionConfig());
    }

    @Override protected DestructionEffects.DestructionConfig getDestructionConfig() {
        // Touch_EnemyNormal uses the ordinary S3K explosion, animal and score owner.
        return new DestructionEffects.DestructionConfig(0x3d,
                (spawn, services) -> AnimalObjectInstance.deferredArtVariant(spawn, services, null),
                false, (spawn, services, points) -> new Sonic3kPointsObjectInstance(spawn, services, points),
                null, false);
    }

    protected final void draw(String key, int frame, int x, int y) {
        var renderer = getRenderer(key);
        if (renderer != null) renderer.drawFrameIndex(frame, x, y, !facingLeft, false);
    }

    @Override public final void appendRenderCommands(List<GLCommand> commands) {
        if (!isDestroyed() && hasLiveParent() && (hitCooldown == 0 || (hitCooldown & 2) == 0)) drawBody();
    }

    protected abstract void drawBody();
}
