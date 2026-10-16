package secondstrike.enemies;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.objects.badniks.Sonic3kBadnikConfig;
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
import com.openggf.game.rewind.identity.ObjectRefId;

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
    // Keep exact attackers, not roster ordinals: a reorder or replacement must
    // never transfer held contact to a different follower. The existing player
    // reference-array rewind codec binds these through the restored world roster.
    private PlayableEntity[] heldAttackers = new PlayableEntity[0];
    private int contactHalfWidth = 24;
    private int contactHalfHeight = 32;
    private ObjectRefId lastCallerIdentity;
    private int lastCallerSequence;
    private int alertAcknowledgementUpdates;

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
        if (heldAttackers.length != 0) {
            var players = services().playerQuery().playersFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS);
            boolean stillHeld = false;
            for (int index = 0; index < heldAttackers.length; index++) {
                PlayableEntity attacker = heldAttackers[index];
                if (attacker == null) continue;
                if (!containsIdentity(players, attacker) || !sameBodyContact(attacker)) heldAttackers[index] = null;
                else stillHeld = true;
            }
            if (!stillHeld) heldAttackers = new PlayableEntity[0];
        }
        if (alertAcknowledgementUpdates > 0) alertAcknowledgementUpdates--;
        return true;
    }

    private static boolean containsIdentity(List<PlayableEntity> players, PlayableEntity player) {
        for (PlayableEntity candidate : players) if (candidate == player) return true;
        return false;
    }

    private boolean heldContact(PlayableEntity player) {
        if (player == null) return false;
        for (PlayableEntity attacker : heldAttackers) if (attacker == player) return true;
        return false;
    }

    private boolean sameBodyContact(PlayableEntity player) {
        // TouchResponseResult supplies the ROM object's actual touch radii.
        // Normal native player touch uses an 8px X half-width and y_radius-3.
        // This continuity guard owns only repeated body hits, never player motion
        // or the enemy's collision byte. Discrete screen attacks remain separate.
        return player != null && !player.getDead()
                && Math.abs(player.getCentreX() - currentX) <= contactHalfWidth + 8
                && Math.abs(player.getCentreY() - currentY) <= contactHalfHeight + Math.max(1, player.getYRadius() - 3);
    }

    private void blockBodyContact(PlayableEntity player, TouchResponseResult result) {
        if (player != null) {
            int index = 0;
            while (index < heldAttackers.length && heldAttackers[index] != null) index++;
            if (index == heldAttackers.length) {
                var expanded = new PlayableEntity[index + 1];
                for (int previous = 0; previous < index; previous++) expanded[previous] = heldAttackers[previous];
                heldAttackers = expanded;
            }
            heldAttackers[index] = player;
        }
        if (result != null) {
            contactHalfWidth = result.widthRadius();
            contactHalfHeight = result.heightRadius();
        }
    }

    public final boolean acknowledgingAlert() { return alertAcknowledgementUpdates > 0; }

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
                && hitCooldown == 0;
    }

    @Override public int getCollisionProperty() {
        // S3K Touch_Enemy: nonzero uses native velocity rebound; zero uses final kill bounce.
        return Math.max(0, remainingHits - 1);
    }

    @Override public final void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if (!acceptsHit() || heldContact(player)) return;
        if (remainingHits > 1) { blockBodyContact(player, result); nonfatalHit(); }
        else { remainingHits = 0; onFinalDefeat(); DestructionEffects.defeatBadnik(this, player, false); }
    }

    private void nonfatalHit() {
        remainingHits--;
        hitCooldown = HIT_COOLDOWN;
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
                    && Math.abs(spotter.getY() - currentY) <= 128) {
                var identity = services().objectQuery().identityOf(spotter).orElse(null);
                if (identity != null && (!identity.equals(lastCallerIdentity)
                        || spotter.alertSequence() != lastCallerSequence)) return spotter;
            }
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
        lastCallerIdentity = services().objectQuery().identityOf(caller).orElseThrow();
        lastCallerSequence = caller.alertSequence();
        alertAcknowledgementUpdates = 12;
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
        // A caller cannot start an unseen defender's tell. Normal ObjectManager
        // range unloading still owns respawn; this gate owns authored activation.
        return isOnScreen(0);
    }

    /** Preserve the companion graph's existing retirement margin independently of attack admission. */
    protected final boolean inLifetimeViewRange() { return isOnScreen(32); }

    @Override public int getCollisionFlags() {
        return isDestroyed() || !hasLiveLifetimeOwner() || hitCooldown > 0
                ? 0 : super.getCollisionFlags();
    }

    @Override public void onPoweredScreenAttack(PlayableEntity player) {
        // The native screen dispatcher owns eligibility. A discrete screen
        // attack obeys hit flash, independently of a held body-contact latch.
        if (!acceptsHit()) return;
        if (remainingHits > 1) { nonfatalHit(); return; }
        remainingHits = 0;
        onFinalDefeat();
        DestructionEffects.defeatBadnik(this, player, true);
    }

    @Override protected DestructionEffects.DestructionConfig getDestructionConfig() {
        // Host recipe retains native explosion, animal, points popup and score order.
        return Sonic3kBadnikConfig.destruction();
    }

    protected final void draw(String key, int frame, int x, int y) {
        var renderer = getRenderer(key);
        if (renderer != null) renderer.drawFrameIndex(frame, x, y, !facingLeft, false);
    }

    @Override public final void appendRenderCommands(List<GLCommand> commands) {
        if (!isDestroyed() && hasLiveLifetimeOwner() && (hitCooldown == 0 || (hitCooldown & 2) == 0)) {
            drawBody();
            if (acknowledgingAlert()) {
                // One short, silent acknowledgement per heard alert. ROM seed
                // art keeps the raised response readable above the normal body.
                draw(SecondStrikeEnemies.GUNNER_ART, 4, currentX - 8, currentY - 36);
                draw(SecondStrikeEnemies.GUNNER_ART, 4, currentX + 8, currentY - 36);
            }
        }
    }

    protected abstract void drawBody();
}
