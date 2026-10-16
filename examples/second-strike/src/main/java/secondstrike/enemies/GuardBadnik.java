package secondstrike.enemies;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectReconstructionContext;
import com.openggf.level.objects.ObjectSpawn;

/** Reinforced rhinobot: provoke its dash, dodge, then punish the long exposed brake. */
public final class GuardBadnik extends SecondStrikeBadnik {
    private static final int WATCH = 0, WINDUP = 1, DASH = 2, RECOVER = 3, RETURN = 4;
    // Previous full flat-floor travel was 90px: 150% further is 225px.
    private static final int DASH_DISTANCE = 225, DASH_STEP = 10, RETURN_STEP = 2;
    private static final int TELL_UPDATES = 36, BRAKE_UPDATES = 72;
    private static final int PRIORITY_BUCKET = 5; // Native ObjSlot $280 / $80.
    private int dashAge;
    private boolean companion;
    private boolean parentDefeated;
    private ObjectRefId parentIdentity;
    // Placement identity deduplicates an ally across combat respawn; never grants lifetime authority.
    private int parentAnchorX;
    private int parentAnchorY;
    private int parentLayoutIndex;

    void attachToParent(ArtilleryBadnik parent, ObjectRefId identity) {
        parentIdentity = java.util.Objects.requireNonNull(identity);
        companion = true;
        parentAnchorX = parent.anchorX; parentAnchorY = parent.anchorY;
        parentLayoutIndex = parent.getSpawn().layoutIndex();
    }
    boolean companionOf(int x, int y, int layoutIndex) {
        return companion && parentAnchorX == x && parentAnchorY == y && parentLayoutIndex == layoutIndex;
    }
    void releaseFromDefeatedParent(ObjectRefId identity) {
        if (parentIdentity != null && parentIdentity.equals(identity)) parentDefeated = true;
    }
    @Override protected boolean hasLiveLifetimeOwner() {
        if (!companion) return true;
        if (!inLifetimeViewRange()) return false;
        if (parentDefeated) return true;
        var parent = services().objectQuery().resolve(parentIdentity).orElse(null);
        return parent instanceof ArtilleryBadnik artillery && !artillery.isDestroyed() && artillery.inLifetimeViewRange();
    }

    public GuardBadnik(ObjectSpawn spawn) { super(spawn, "Second Strike guard", 3); }

    @Override public boolean supportsGroundedFieldMotion() { return true; }

    public boolean armed() { return phase == WINDUP || phase == DASH; }
    public boolean recovering() { return phase == RECOVER || phase == RETURN; }

    @Override protected void updateMovement(int vIntRunCount, PlayableEntity updatePlayer) {
        if (!updateHitAndLifetime(updatePlayer)) return;
        if (!activeOnScreen()) return;
        age++;
        switch (phase) {
            case WATCH -> {
                if (startTarget(144, 48)) {
                    xVelocity = facingLeft ? -0xa00 : 0xa00;
                    if (canStartDash()) {
                        phase = WINDUP;
                        timer = TELL_UPDATES;
                        services().playSfx(0x48); // sfx_Blast, native Rhinobot_StartChargePrep cue.
                    } else beginReturn();
                }
            }
            case WINDUP -> {
                if (--timer <= 0) {
                    // The tell commits direction, but terrain may change before release.
                    if (canStartDash()) {
                        phase = DASH; timer = DASH_DISTANCE; dashAge = 0;
                        services().playSfx(0x43); // Native Sonic fire-shield dash attack cue.
                    } else recover();
                }
            }
            case DASH -> {
                int steps = dashStride(timer);
                // A dash stays on its natural ledge and never turns to chase the dodge.
                if (steps == 0 || !floorStrideAhead(steps, 4)) {
                    recover();
                } else {
                    xVelocity = (facingLeft ? -steps : steps) << 8;
                    moveFixed();
                    dashAge++;
                    timer -= steps;
                    if (timer <= 0) recover();
                }
            }
            case RECOVER -> { if (--timer <= 0) beginReturn(); }
            case RETURN -> returnToPost();
            default -> throw new IllegalStateException("guard phase " + phase);
        }
    }

    private boolean canStartDash() {
        int steps = dashStride(DASH_DISTANCE);
        if (steps == 0) return false;
        int previousY = currentY;
        // Admission is read-only: the actual stride still owns native floor following.
        try { return floorStrideAhead(steps, 4); }
        finally { currentY = previousY; }
    }

    private int dashStride(int remainingDistance) {
        int direction = facingLeft ? -1 : 1;
        int postLimit = anchorX + direction * DASH_DISTANCE;
        return Math.min(Math.min(DASH_STEP, remainingDistance),
                Math.max(0, (postLimit - currentX) * direction));
    }

    private boolean floorStrideAhead(int steps, int ahead) {
        int previousY = currentY;
        int direction = facingLeft ? -1 : 1;
        // A front sensor cannot prove the first few feet of a newly reversed
        // stride. Check that prefix too, so a hole beside the body is not skipped.
        for (int step = 1; step <= ahead; step++) {
            var floor = services().floorSurface(currentX + direction * step, currentY + 16,
                    com.openggf.level.objects.TerrainSurface.Layer.FOREGROUND,
                    com.openggf.level.objects.TerrainSurface.Path.PRIMARY);
            if (!floor.foundSurface() || floor.distance() < -1 || floor.distance() >= 12) return false;
        }
        // Probe every pixel of either stride: an endpoint alone skips small gaps.
        for (int step = 1; step <= steps; step++) {
            if (!floorAhead(currentX + direction * (step + ahead))) {
                currentY = previousY;
                return false;
            }
        }
        return true;
    }

    private void beginReturn() {
        xVelocity = 0;
        if (currentX == anchorX) { phase = WATCH; timer = 0; return; }
        phase = RETURN;
        timer = (Math.abs(currentX - anchorX) + RETURN_STEP - 1) / RETURN_STEP;
        facingLeft = currentX > anchorX;
    }

    private void returnToPost() {
        int steps = Math.min(RETURN_STEP, Math.abs(anchorX - currentX));
        facingLeft = currentX > anchorX;
        // The charge ends aligned to its former front sensor. Return follows
        // centre-foot terrain one pixel at a time, avoiding an opposite-front
        // height jump on a continuous slope without widening native tolerances.
        if (steps == 0 || timer <= 0 || !floorStrideAhead(steps, 0)) {
            phase = WATCH;
            timer = 0;
            xVelocity = 0;
            return;
        }
        // Walk toward the original post without overshooting, including an odd last pixel.
        xVelocity = (facingLeft ? -steps : steps) << 8;
        moveFixed();
        if (--timer <= 0 || currentX == anchorX) {
            phase = WATCH;
            xVelocity = 0;
        }
    }

    @Override protected boolean damageImmune() { return phase == DASH; }
    @Override protected void onNonfatalHit() { recover(); }

    private void recover() {
        phase = RECOVER;
        timer = BRAKE_UPDATES;
        xVelocity = 0;
        services().playSfx(0x36); // sfx_Skid: armor disappears for the punish window.
    }

    @Override protected void updateAnimation(int vIntRunCount) {
        animFrame = phase == DASH ? 1 : phase == RECOVER ? 2 : 0;
    }

    @Override protected int getCollisionSizeIndex() { return 0x0b; } // ObjSlot_Rhinobot.
    @Override public int getCollisionFlags() {
        int flags = super.getCollisionFlags();
        return flags == 0 ? 0 : (armed() ? 0x80 : 0) | flags;
    }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; } // ObjSlot $280.
    @Override public int getOnScreenHalfWidth() { return 24; }
    @Override public int getOnScreenHalfHeight() { return 32; }

    @Override protected void drawBody() {
        draw(SecondStrikeEnemies.GUARD_ART, animFrame, currentX, currentY);
        if (phase == WINDUP) {
            // Rhinobot's own charge-effect frames 4..7 show the dangerous armored phase.
            draw(SecondStrikeEnemies.GUARD_ART, 4 + ((age / 3) & 3),
                    currentX + (facingLeft ? -16 : 16), currentY + 8);
        }
        if (phase == DASH) {
            var flame = getRenderer(SecondStrikeEnemies.DASH_ART);
            if (flame != null) {
                // Ani_FireShield script1: delay1, frames9..14 twice then SWITCH0.
                // Fire shield's native right-facing art flips on LEFT, unlike Rhinobot body art.
                services().renderAttachedEffect(() -> flame.drawFrameIndex(9 + ((dashAge / 2) % 6),
                        currentX, currentY, facingLeft, false));
            }
        }
        if (!recovering()) {
            // Raised seed plates identify the variant, shake during the armed tell/dash,
            // and vanish with the curled brake pose when native enemy collision returns.
            int shake = armed() ? ((age & 2) == 0 ? -2 : 2) : 0;
            for (int plate = 0; plate < remainingHits; plate++) {
                draw(SecondStrikeEnemies.GUNNER_ART, 4,
                        currentX + (plate * 10 - (remainingHits - 1) * 5) + shake, currentY - 19);
            }
        }
    }

    @Override public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) {
        return new GuardBadnik(context.spawn());
    }
}
