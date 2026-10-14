package secondstrike.enemies;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectReconstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectCombatRules;

/** Monkey caller: a raised coconut and native throw cue advertise a committed group alert. */
public final class SpotterBadnik extends SecondStrikeBadnik {
    private static final int WATCH = 0, CALL = 1, REST = 2;
    private static final int PRIORITY_BUCKET = 5; // Native ObjDat $280 / $80.
    private static final int CALL_UPDATES = 48, REST_UPDATES = 120;

    public SpotterBadnik(ObjectSpawn spawn) { super(spawn, "Second Strike spotter", 2); }

    public boolean isCalling() { return phase == CALL && !isDestroyed(); }

    @Override protected void updateMovement(int vIntRunCount, PlayableEntity updatePlayer) {
        if (!updateHitAndLifetime(updatePlayer)) return;
        if (!activeOnScreen()) return;
        age++;
        PlayableEntity player = nearestTarget(208, 144);
        switch (phase) {
            case WATCH -> {
                if (ObjectCombatRules.permitsTargeting(services()) && inRange(player, 208, 144)) {
                    commitTarget(player);
                    phase = CALL;
                    timer = CALL_UPDATES;
                    services().playSfx(0x51); // Native MonkeyDude sfx_MissileThrow, one cue at call entry.
                }
            }
            case CALL -> {
                if (--timer <= 0) { phase = REST; timer = REST_UPDATES; }
            }
            case REST -> { if (--timer <= 0) phase = WATCH; }
            default -> throw new IllegalStateException("spotter phase " + phase);
        }
    }

    @Override protected void onNonfatalHit() { phase = REST; timer = REST_UPDATES; }

    @Override protected void updateAnimation(int vIntRunCount) {
        // Obj_MonkeyDude: body frames 0/1 wait, 2 active; frame 6 is the coconut.
        animFrame = isCalling() ? 2 : (age / 8) & 1;
    }

    @Override protected int getCollisionSizeIndex() { return 0x0b; } // ObjDat_MonkeyDude.
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; } // $280.
    @Override public int getOnScreenHalfWidth() { return 32; }
    @Override public int getOnScreenHalfHeight() { return 40; }

    @Override protected void drawBody() {
        draw(SecondStrikeEnemies.MONKEY_ART, animFrame, currentX, currentY);
        // Paired raised coconuts distinguish this caller from the stock climbing monkey.
        int lift = isCalling() ? 32 + ((age & 8) == 0 ? 3 : 0) : 25;
        draw(SecondStrikeEnemies.MONKEY_ART, 6, currentX - 11, currentY - lift);
        if (remainingHits > 1) draw(SecondStrikeEnemies.MONKEY_ART, 6, currentX + 11, currentY - lift);
    }

    @Override public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) {
        return new SpotterBadnik(context.spawn());
    }
}
