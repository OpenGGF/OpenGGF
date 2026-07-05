package com.openggf.game.sonic1.objects.badniks;

import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the SYZ Roller's three lifecycle collision states separately
 * (docs/s1disasm/_incObj/43 Badnik - Roller.asm), triaging the reported
 * "Roller cannot be defeated in its initial standing state" symptom
 * (S1 bug batch ledger row 7).
 * <p>
 * ROM finding: Roll_Main (routine 0) and Roll_Action_FromLeft (ob2ndRout=0,
 * the initial curled/waiting state before Sonic gets 256px ahead) never
 * write {@code obColType} -- it stays 0 (col_none) from the object's
 * zeroed spawn RAM. ReactToItem skips col_none objects entirely
 * ("Sonic ReactToItem.asm:52-53", {@code move.b obColType(a1),d0 / bne...}),
 * so a dormant, not-yet-activated Roller genuinely cannot be touched,
 * hurt Sonic, OR be defeated by Sonic -- this is not a "blanket invincible"
 * placeholder, it is the literal absence of any obColType write in that
 * state. Once activated it becomes destroyable ($0E, Roll_Action_StopAndUnfold,
 * line 177) while stopped/unfolded, and invincible-and-damaging ($8E,
 * Roll_Action_FromLeft line 96 / Roll_Action_Unfolded line 111) while rolling.
 * <p>
 * These tests drive the private lifecycle fields directly via reflection
 * (rather than {@code update()}) to isolate the ROM-cited {@code obColType}
 * decision in {@link Sonic1RollerBadnikInstance#getCollisionFlags()} from
 * terrain/services plumbing that a bare {@code new Sonic1RollerBadnikInstance(...)}
 * (outside {@code ObjectManager} injection) does not have wired up.
 */
public class TestSonic1RollerBadnikInstance {

    private static final int STATE_ROLL_CHK = 0;
    private static final int STATE_ROLL_NO_CHK = 1;
    private static final int STATE_CHK_JUMP = 2;

    private Sonic1RollerBadnikInstance newRoller() {
        return new Sonic1RollerBadnikInstance(new ObjectSpawn(160, 100, 0x43, 0, 0, false, 0));
    }

    private void setState(Sonic1RollerBadnikInstance roller, int secondaryState, boolean invincible) throws Exception {
        Field stateField = Sonic1RollerBadnikInstance.class.getDeclaredField("secondaryState");
        stateField.setAccessible(true);
        stateField.set(roller, secondaryState);
        Field invincibleField = Sonic1RollerBadnikInstance.class.getDeclaredField("invincible");
        invincibleField.setAccessible(true);
        invincibleField.set(roller, invincible);
    }

    @Test
    public void initialStandingStateHasNoCollisionAtAll() throws Exception {
        // ROM: Roll_Main / Roll_Action_FromLeft never write obColType while
        // waiting for Sonic to be 0x100px to the right (docs/s1disasm/_incObj/
        // 43 Badnik - Roller.asm:19-38, 86-100). The object's RAM starts
        // zeroed, so obColType == 0 (col_none) until activation. This is NOT
        // "invincible" (obColType $80+) -- it is no collision entry at all,
        // so ReactToItem's `move.b obColType(a1),d0 / bne` skip (Sonic
        // ReactToItem.asm:52-53) applies: Sonic cannot touch, hurt from, or
        // defeat a still-curled, not-yet-activated Roller.
        Sonic1RollerBadnikInstance roller = newRoller();
        setState(roller, STATE_ROLL_CHK, false);

        assertEquals(0, roller.getCollisionFlags(),
                "Dormant pre-activation Roller must report col_none (0), matching ROM's unset obColType");
    }

    @Test
    public void rollingStateIsDamagingCategoryNotDestroyable() throws Exception {
        // ROM Roll_Action_FromLeft (line 96) / Roll_Action_Unfolded re-fold
        // (line 111): obColType = col_28x28|col_hurt = $8E while actively
        // rolling. col_hurt ($80) routes straight to React_ChkHurt in ROM
        // (Sonic ReactToItem.asm:188-189), bypassing the badnik-defeat check
        // entirely -- touching a rolling Roller always hurts Sonic, even
        // while he is spinning/invincible, and it can never be destroyed by
        // touch in this state.
        Sonic1RollerBadnikInstance roller = newRoller();
        setState(roller, STATE_CHK_JUMP, true);

        int flags = roller.getCollisionFlags();
        assertEquals(0x80, flags & 0xC0,
                "Rolling Roller must use the col_hurt ($80) category, not badnik ($00)");
    }

    @Test
    public void stoppedUnfoldedStateIsDestroyableBadnikCategory() throws Exception {
        // ROM Roll_Action_StopAndUnfold (line 177): obColType =
        // col_28x28|col_badnik = $0E once the Roller has passed Sonic by
        // 48px and stops to unfold. This is the one window where the Roller
        // is a normal, destroyable badnik (col_badnik == 0, ENEMY category)
        // -- the reported "cannot be defeated" symptom does NOT reproduce
        // here: getCollisionFlags() already returns the ENEMY category, so
        // AbstractBadnikInstance's default onPlayerAttack()/destroyBadnik()
        // path applies normally.
        Sonic1RollerBadnikInstance roller = newRoller();
        setState(roller, STATE_ROLL_NO_CHK, false);

        int flags = roller.getCollisionFlags();
        assertEquals(0x00, flags & 0xC0,
                "Stopped/unfolded Roller must use the col_badnik (ENEMY, $00) category so it can be destroyed");
    }
}
