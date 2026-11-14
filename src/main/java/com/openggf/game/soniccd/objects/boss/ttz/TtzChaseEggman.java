package com.openggf.game.soniccd.objects.boss.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Chase Eggman, object $4B ({@code EggmanChaseObject}, objects/r4/boss_chase.asm:691-773): the pilot the Egg Mobile
 * allocates. Its init allocates the two subtype 1 doors ({@code sub_20C014}: ($760, $2C0), then ($6C0, $460)); then it
 * copies the Egg Mobile's X, Y and {@code oFlags} every frame and animates: pose 1 (hurt) while {@code var_2a} counts
 * down, pose 0 when it ends. When the Egg Mobile lands in the arena it moves this one to routine 4, which no longer
 * draws: once the player is at X $840 and Y $550 or beyond it sets {@code boss_flags} to 4 and deletes the first
 * door, the second door and itself on three frames.
 */
public final class TtzChaseEggman extends TtzBossPart {
    /** The body mirrors by {@code oFlags} bit 0 ({@link TtzBossPart#facingByFlags}). */
    @Override
    public com.openggf.level.objects.ObjectVisualFacing.Basis nativeFacing() {
        return facingByFlags();
    }

    /** Object $4B: EggmanChaseObject is index entry $4A (r4/object_index_3.asm:3-77). */
    public static final int ID = 0x4B;

    /** var_30: the Egg Mobile. */
    TtzChaseMobile mobile;
    /** var_32 / var_34: the doors the Egg Mobile's upper and lower paths shut. */
    TtzBossDoor upperDoor;
    TtzBossDoor lowerDoor;
    /** var_2a: frames of the hurt pose left. */
    int hurtFrames;

    TtzChaseEggman(int x, int y, TtzChaseMobile mobile) {
        super(ID, x, y, "TtzChaseEggman");
        this.mobile = mobile;
    }

    private TtzChaseEggman(ObjectSpawn spawn) {
        super(spawn, "TtzChaseEggman");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> { init(); follow(); }                             // falls into Routine2
            case 2 -> follow();
            case 4 -> waitForPlayer(player);
            default -> { }
        }
    }

    /** EggmanChaseObject_0_Routine0 (objects/r4/boss_chase.asm:706-714). */
    private void init() {
        routine = 2;
        priority = 6;                                                   // move.b #6,obj.sprite_layer
        width = 0x24;                                                   // move.b #$24,obj.width_2
        yRadius = 0x38;                                                 // move.b #$38,obj.height
        tileWord = TtzBossArt.BOSS_TILE;
        // sub_20C014 (objects/r4/boss_chase.asm:538-555): two FindObjSlot doors; a failure ends the allocation.
        TtzBossDoor upper = new TtzBossDoor(0x760, 0x2C0);
        if (!spawn(upper)) return;
        upperDoor = upper;
        TtzBossDoor lower = new TtzBossDoor(0x6C0, 0x460);
        if (!spawn(lower)) return;
        lowerDoor = lower;
    }

    /** EggmanChaseObject_0_Routine2 (objects/r4/boss_chase.asm:716-741). */
    private void follow() {
        if (hurtFrames != 0) {
            hurtFrames = (hurtFrames - 1) & 0xFF;
            if (hurtFrames == 0) restartAnimation(0);                   // loc_20C186
            else anim = 1;                                              // move.b #1,obj.anim_id
        }
        if (mobile != null) {                                           // loc_20C19C
            setXWord(mobile.getX());
            setYWord(mobile.getY());
            facing = mobile.facing;                                     // move.b obj.flags(a1),obj.flags(a0)
        }
        animateObject("chaseEggmanAnimations");
    }

    /** EggmanChaseObject_0_Routine4 (objects/r4/boss_chase.asm:745-773): no AnimateObject, no DrawObject. */
    private void waitForPlayer(PlayableEntity player) {
        if (player == null) return;
        if ((short) player.getCentreX() < 0x840 || (short) player.getCentreY() < 0x550) return;
        flags().bossFlags = 4;                                          // move.b #4,boss_flags
        if (upperDoor != null && !upperDoor.isDestroyed()) {            // tst.b obj.id(a1); jmp DeleteOtherObject
            upperDoor.setDestroyed(true);
            upperDoor = null;
            return;
        }
        upperDoor = null;
        if (lowerDoor != null && !lowerDoor.isDestroyed()) {
            lowerDoor.setDestroyed(true);
            lowerDoor = null;
            return;
        }
        lowerDoor = null;
        deleteObject();                                                 // loc_20C200
    }

    @Override protected boolean drawn() { return routine == 2; }

    @Override String sheet() { return TtzBossArt.CHASE_EGGMAN; }

    @Override
    protected TtzChaseEggman shell(ObjectSpawn spawn) {
        return new TtzChaseEggman(spawn);
    }
}
