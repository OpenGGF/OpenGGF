package secondstrike.enemies;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.RenderPriority;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectReconstructionContext;
import com.openggf.level.objects.ObjectSpawn;

/** Reinforced rhinobot: provoke its dash, dodge, then punish the long exposed brake. */
public final class GuardBadnik extends SecondStrikeBadnik {
    private static final int WATCH = 0, WINDUP = 1, DASH = 2, RECOVER = 3, RETURN = 4;
    private int dashAge;

    public GuardBadnik(ObjectSpawn spawn) { super(spawn, "Second Strike guard", 3); }

    public boolean armed() { return phase == WINDUP || phase == DASH; }
    public boolean recovering() { return phase == RECOVER || phase == RETURN; }

    @Override protected void updateMovement(int vIntRunCount, PlayableEntity updatePlayer) {
        if (!updateHitAndLifetime(updatePlayer)) return;
        if (!activeOnScreen()) return;
        age++;
        switch (phase) {
            case WATCH -> {
                if (startTarget(nearestPlayer(updatePlayer), 144, 48)) {
                    xVelocity = facingLeft ? -0xa00 : 0xa00;
                    if (canStartDash()) {
                        phase = WINDUP;
                        timer = 36;
                        services().playSfx(0x48); // sfx_Blast, native Rhinobot_StartChargePrep cue.
                    } else beginReturn();
                }
            }
            case WINDUP -> {
                if (--timer <= 0) {
                    // The tell commits direction, but terrain may change before release.
                    if (canStartDash()) {
                        phase = DASH; timer = 10; dashAge = 0;
                        services().playSfx(0x43); // Native Sonic fire-shield dash attack cue.
                    } else recover();
                }
            }
            case DASH -> {
                int nextX = currentX + (xVelocity >> 8);
                // A dash stays on its natural ledge and never turns to chase the dodge.
                if (Math.abs(nextX - anchorX) > 96 || !floorStrideAhead(10, 4)) {
                    recover();
                } else {
                    moveFixed();
                    dashAge++;
                    if (--timer <= 0) recover();
                }
            }
            case RECOVER -> { if (--timer <= 0) beginReturn(); }
            case RETURN -> returnToPost();
            default -> throw new IllegalStateException("guard phase " + phase);
        }
    }

    private boolean canStartDash() {
        if (Math.abs(currentX + (xVelocity >> 8) - anchorX) > 96) return false;
        int previousY = currentY;
        // Admission is read-only: the actual stride still owns native floor following.
        try { return floorStrideAhead(10, 4); }
        finally { currentY = previousY; }
    }

    private boolean floorStrideAhead(int steps, int ahead) {
        int previousY = currentY;
        int direction = facingLeft ? -1 : 1;
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
        timer = 64;
        facingLeft = currentX > anchorX;
    }

    private void returnToPost() {
        int steps = Math.min(2, Math.abs(anchorX - currentX));
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
        // Walk toward the original post without overshooting or exceeding its96px radius.
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
        timer = 72;
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
    @Override public int getPriorityBucket() { return RenderPriority.bucket(5); } // ObjSlot $280.
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
                var graphics = services().graphicsManager();
                var subject = SpritePresentation.subject(graphics);
                SpritePresentation.withSubject(graphics, new SpritePresentation.Subject(subject.id(),
                        SpritePresentation.Part.ATTACHED_EFFECT, subject.suppressed()),
                        () -> flame.drawFrameIndex(9 + ((dashAge / 2) % 6),
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
