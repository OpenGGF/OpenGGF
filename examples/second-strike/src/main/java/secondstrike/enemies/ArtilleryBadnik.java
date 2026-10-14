package secondstrike.enemies;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectReconstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectCombatRules;

/** Bloominator gunner: two committed bilateral seed fans, then a long vulnerable reload. */
public final class ArtilleryBadnik extends SecondStrikeBadnik {
    private static final int WATCH = 0, WINDUP = 1, VOLLEY = 2, RELOAD = 3;
    private static final int MAX_LIVE_SEEDS = 20;
    private static final int TELL_UPDATES = 36, RELEASE_GAP_UPDATES = 12, RELOAD_UPDATES = 108;
    private static final int SEED_GRAVITY = 0x18, PRIORITY_BUCKET = 4; // Native $200 / $80.
    // Creator recipes, not native Bloominator AI: five low-to-high arcs per side.
    // 8.8 velocities and $18 gravity keep a wide fan readable during the 108-update reload.
    private record SeedRecipe(int muzzleYOffset, int horizontalSpeed, int verticalSpeed) { }
    private static final int FAN_LANES = 5;

    private static SeedRecipe fanRecipe(int lane) {
        return switch (lane) {
            case 0 -> new SeedRecipe(-16, 0x380, -0x080);
            case 1 -> new SeedRecipe(-20, 0x340, -0x180);
            case 2 -> new SeedRecipe(-24, 0x2c0, -0x280);
            case 3 -> new SeedRecipe(-28, 0x200, -0x340);
            case 4 -> new SeedRecipe(-32, 0x0e0, -0x400);
            default -> throw new IllegalArgumentException("fan lane " + lane);
        };
    }

    private ObjectRefId lifetimeIdentity;
    private boolean companionAttempted;

    public ArtilleryBadnik(ObjectSpawn spawn) { super(spawn, "Second Strike artillery", 2); }

    @Override protected void updateMovement(int vIntRunCount, PlayableEntity updatePlayer) {
        if (!updateHitAndLifetime(updatePlayer)) return;
        if (!activeOnScreen()) return;
        admitCompanion();
        age++;
        switch (phase) {
            case WATCH -> {
                if (startTarget(176, 96)) {
                    // Facing/target commit now. Both fixed fans keep their lanes if the player crosses
                    // during the tell; the committed approach gets the first slot if capacity is scarce.
                    lifetimeIdentity = services().objectQuery().identityOf(this).orElse(null);
                    phase = WINDUP;
                    timer = TELL_UPDATES;
                    services().playSfx(0x53); // sfx_Charging.
                }
            }
            case WINDUP -> {
                if (--timer <= 0) { fire(); phase = VOLLEY; timer = RELEASE_GAP_UPDATES; }
            }
            case VOLLEY -> {
                if (--timer <= 0) { fire(); phase = RELOAD; timer = ObjectCombatRules.firingCooldown(services(), RELOAD_UPDATES); }
            }
            case RELOAD -> { if (--timer <= 0) phase = WATCH; }
            default -> throw new IllegalStateException("artillery phase " + phase);
        }
    }

    private void admitCompanion() {
        if (companionAttempted || !SecondStrikeEnemies.reinforcedAnchor(anchorX, anchorY)) return;
        lifetimeIdentity = services().objectQuery().identityOf(this).orElse(null);
        if (lifetimeIdentity == null) return;
        companionAttempted = true; // One bounded allocation attempt per native parent lifetime.
        if (services().objectQuery().activeObjectsOfType(GuardBadnik.class).stream()
                .anyMatch(ally -> !ally.isDestroyed()
                        && ally.companionOf(anchorX, anchorY, getSpawn().layoutIndex()))) return;
        int floorY = anchorY;
        for (int offset = 4; offset <= 48; offset += 4) {
            var floor = services().floorSurface(anchorX + offset, floorY + 16,
                    com.openggf.level.objects.TerrainSurface.Layer.FOREGROUND,
                    com.openggf.level.objects.TerrainSurface.Path.PRIMARY);
            if (!floor.foundSurface() || floor.distance() < -1 || floor.distance() >= 12) return;
            floorY += floor.distance();
        }
        var allySpawn = SecondStrikeEnemies.ownedSpawn(SecondStrikeEnemies.GUARD, anchorX + 48, floorY, -1);
        spawnChild(() -> {
            var ally = new GuardBadnik(allySpawn);
            ally.attachToParent(this, lifetimeIdentity);
            return ally;
        });
    }

    @Override protected void onNonfatalHit() { phase = RELOAD; timer = ObjectCombatRules.firingCooldown(services(), RELOAD_UPDATES); }

    private void fire() {
        var query = services().objectQuery();
        var owner = query.identityOf(this).orElse(null);
        if (owner == null || query.resolve(owner).orElse(null) != this
                || isDestroyed() || getSlotIndex() < 0) return;
        lifetimeIdentity = owner;
        int available = MAX_LIVE_SEEDS;
        for (CommittedShot shot : query.activeObjectsOfType(CommittedShot.class)) {
            if (!shot.isDestroyed() && shot.belongsTo(owner)) available--;
        }
        if (available <= 0) return;
        boolean released = false;
        int preferredSide = facingLeft ? -1 : 1;
        release:
        for (int lane = 0; lane < FAN_LANES; lane++) {
            SeedRecipe recipe = fanRecipe(lane);
            for (int side : new int[]{preferredSide, -preferredSide}) {
                if (available == 0 || !ObjectCombatRules.permitsFiring(services(), currentX)) break release;
                var shotSpawn = SecondStrikeEnemies.ownedSpawn(SecondStrikeEnemies.SHOT,
                        currentX + side * 12, currentY + recipe.muzzleYOffset(), -1);
                var shot = spawnChild(() -> new CommittedShot(shotSpawn,
                        side * recipe.horizontalSpeed(), recipe.verticalSpeed(),
                        owner, SEED_GRAVITY));
                // A factory can return a constructed, destroyed object after SST exhaustion.
                // Stop this release at the first rejection; never retry or sound for phantom shots.
                if (shot == null || shot.isDestroyed() || shot.getSlotIndex() < 0) break release;
                var identity = query.identityOf(shot).orElse(null);
                if (identity == null || query.resolve(identity).orElse(null) != shot) break release;
                available--;
                released = true;
            }
        }
        if (released) services().playSfx(0x4d); // One Bloominator_FireProjectile cue per admitted fan.
    }

    @Override protected void onFinalDefeat() {
        var identity = lifetimeIdentity != null ? lifetimeIdentity : services().objectQuery().identityOf(this).orElse(null);
        if (identity != null) {
            for (GuardBadnik ally : services().objectQuery().activeObjectsOfType(GuardBadnik.class))
                ally.releaseFromDefeatedParent(identity);
        }
        finishCommittedSeeds(true);
    }

    @Override public void onUnload() {
        // Manager removal can erase identityOf(this) before this callback; use the captured id.
        finishCommittedSeeds(remainingHits == 0);
        super.onUnload();
    }

    private void finishCommittedSeeds(boolean defeated) {
        if (lifetimeIdentity == null) return;
        for (CommittedShot shot : services().objectQuery().activeObjectsOfType(CommittedShot.class)) {
            if (shot.belongsTo(lifetimeIdentity)) shot.finishOwnerLifetime(defeated);
        }
    }

    @Override protected void updateAnimation(int vIntRunCount) {
        animFrame = phase == WINDUP ? 1 + ((age / 6) & 1) : phase == VOLLEY ? 3 : 0;
    }

    @Override protected int getCollisionSizeIndex() { return 0x23; } // ObjDat_Bloominator.
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; } // $200.
    @Override public int getOnScreenHalfWidth() { return 24; }
    @Override public int getOnScreenHalfHeight() { return 32; }

    @Override protected void drawBody() {
        draw(SecondStrikeEnemies.GUNNER_ART, animFrame, currentX, currentY);
        // The native seed frame sketches the widening fan during all 36 committed tell updates.
        if (phase == WINDUP) {
            for (int lane = 0; lane < FAN_LANES; lane++) {
                draw(SecondStrikeEnemies.GUNNER_ART, 4, currentX - 16 - lane * 6, currentY - 20 - lane * 5);
                draw(SecondStrikeEnemies.GUNNER_ART, 4, currentX + 16 + lane * 6, currentY - 20 - lane * 5);
            }
        } else if (phase != RELOAD) {
            draw(SecondStrikeEnemies.GUNNER_ART, 4, currentX - 12, currentY - 25);
            if (remainingHits > 1) draw(SecondStrikeEnemies.GUNNER_ART, 4, currentX + 12, currentY - 25);
        }
    }

    @Override public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) {
        return new ArtilleryBadnik(context.spawn());
    }
}
