package secondstrike.enemies;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectReconstructionContext;
import com.openggf.level.objects.ObjectSpawn;

/** Reinforced rhinobot: provoke its dash, dodge, then punish the long exposed brake. */
public final class GuardBadnik extends SecondStrikeBadnik {
    private static final int WATCH = 0, WINDUP = 1, DASH = 2, RECOVER = 3;

    public GuardBadnik(ObjectSpawn spawn) { super(spawn, "Second Strike guard"); }

    public boolean armed() { return phase == WINDUP || phase == DASH; }
    public boolean recovering() { return phase == RECOVER; }

    @Override protected void updateMovement(int vIntRunCount, PlayableEntity updatePlayer) {
        if (!activeOnScreen()) return;
        age++;
        switch (phase) {
            case WATCH -> {
                if (startTarget(nearestPlayer(updatePlayer), 144, 48)) {
                    phase = WINDUP;
                    timer = 36;
                    xVelocity = facingLeft ? -0x400 : 0x400;
                    services().playSfx(0x48); // sfx_Blast, native Rhinobot_StartChargePrep cue.
                }
            }
            case WINDUP -> { if (--timer <= 0) { phase = DASH; timer = 24; } }
            case DASH -> {
                int nextX = currentX + (xVelocity >> 8);
                // A dash stays on its natural ledge and never turns to chase the dodge.
                if (Math.abs(nextX - anchorX) > 96 || !floorAhead(nextX + (facingLeft ? -4 : 4))) {
                    recover();
                } else {
                    moveFixed();
                    if (--timer <= 0) recover();
                }
            }
            case RECOVER -> { if (--timer <= 0) phase = WATCH; }
            default -> throw new IllegalStateException("guard phase " + phase);
        }
    }

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
        return isDestroyed() ? 0 : (armed() ? 0x80 : 0) | getCollisionSizeIndex();
    }
    @Override public int getPriorityBucket() { return RenderPriority.bucket(5); } // ObjSlot $280.
    @Override public int getOnScreenHalfWidth() { return 24; }
    @Override public int getOnScreenHalfHeight() { return 32; }

    @Override protected void drawBody() {
        draw(SecondStrikeEnemies.GUARD_ART, animFrame, currentX, currentY);
        if (armed()) {
            // Rhinobot's own charge-effect frames 4..7 show the dangerous armored phase.
            draw(SecondStrikeEnemies.GUARD_ART, 4 + ((age / 3) & 3),
                    currentX + (facingLeft ? -16 : 16), currentY + 8);
        }
        if (!recovering()) {
            // Raised seed plates identify the variant, shake during the armed tell/dash,
            // and vanish with the curled brake pose when native enemy collision returns.
            int shake = armed() ? ((age & 2) == 0 ? -2 : 2) : 0;
            draw(SecondStrikeEnemies.GUNNER_ART, 4, currentX - 9 + shake, currentY - 19);
            draw(SecondStrikeEnemies.GUNNER_ART, 4, currentX + 9 + shake, currentY - 19);
        }
    }

    @Override public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) {
        return new GuardBadnik(context.spawn());
    }
}
