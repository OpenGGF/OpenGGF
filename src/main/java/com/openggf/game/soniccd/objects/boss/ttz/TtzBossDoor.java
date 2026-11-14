package com.openggf.game.soniccd.objects.boss.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ReevaluatingSolidObject;

/**
 * Boss door, object $47 on R43C/R43D ({@code BossDoorObject}, objects/r4/boss_door.asm:3-141; ID from
 * r4/object_index_3.asm, entry position + 1): a solid block $80 wide and $80 tall (tile $446A, priority 3) that
 * slides down 4 pixels a frame to shut a passage behind the player or the fleeing Egg Mobile. Every frame, after its
 * routine, it calls {@code SolidObject} for the player and {@code DrawObject}.
 *
 * <ul>
 *   <li>Subtype 0 (the layout's door at ($420, $300)) waits in routine 2 until the player is $A0-$BF to its right
 *       and up to $7F below it, then falls $40 pixels ($10 frames); it deletes itself, clearing its loaded bit, when
 *       its chunk is more than $280 from the camera's, on screen or not.</li>
 *   <li>Subtype 1 (the two the chase Eggman allocates) waits in routine 4 until the Egg Mobile's path sets its
 *       {@code var_3f}, then falls $80 pixels ($20 frames); it never deletes itself (the chase Eggman does).</li>
 * </ul>
 *
 * <p>Routine 4 records the shut door in its saved-flag bit 0 (object_states, per layout state ID and time zone); a
 * later load starts shut ($40 or $80 lower, routine 4), which the shared respawn state models as "remembered". The
 * spawned doors have no layout state ID: they read and write the shared scratch state 0 natively, which a
 * respawn-tracked entry cannot hold; they always take the first-load path.
 */
public final class TtzBossDoor extends TtzBossPart implements ReevaluatingSolidObject {
    /** Object $47: BossDoorObject is index entry $46 (r4/object_index_3.asm:3-74). */
    public static final int ID = 0x47;
    /** BossDoorObject_0_Routine6: addi.l #$40000,obj.y(a0) (objects/r4/boss_door.asm:112). */
    private static final long FALL_SPEED = 0x40000;

    /** var_2a: frames of fall left. */
    private int fallFrames;
    /** var_3f: the trigger, set by routine 2 or by the Egg Mobile. */
    boolean triggered;

    public TtzBossDoor(ObjectSpawn spawn) {
        super(spawn, "TtzBossDoor");
    }

    /** A door the chase Eggman allocates ({@code sub_20C014}): subtype 1 at {@code (x, y)}. */
    TtzBossDoor(int x, int y) {
        super(new ObjectSpawn(x, y, ID, 1, 0, false, 0), "TtzBossDoor");
    }

    private int subtype() { return spawn.subtype() & 0xFF; }

    @Override public boolean shouldStayActiveWhenRemembered() { return true; }

    /** loc2_20F1B2 (objects/r4/boss_door.asm:123-136): subtype 1 stays; others go past $280 chunks, on screen or not. */
    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        if (subtype() == 1) return false;
        int chunk = x & 0xFF80;
        int cameraChunk = (cameraX - 0x80) & 0xFF80;
        return ((chunk - cameraChunk) & 0xFFFF) > 0x280;                // cmpi.w #$280,d0; bls.s
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> init();
            case 2 -> waitForPlayer(player);
            case 4 -> waitForTrigger();
            case 6 -> fall();
            default -> { }
        }
        solidObject(Entry.SOLID);                                       // lea player_object,a1; jsr SolidObject
    }

    /** BossDoorObject_0_Routine0 (objects/r4/boss_door.asm:23-67). */
    private void init() {
        priority = 3;                                                   // move.b #3,obj.sprite_layer
        width = 0x40;                                                   // move.b #$40,obj.width_2 (oWidth)
        yRadius = 0x40;                                                 // move.b #$40,obj.height (oYRadius)
        tileWord = TtzBossArt.DOOR_TILE;
        var objects = services().objectManager();
        if (objects != null && spawn.respawnTracked() && objects.isRemembered(spawn)) {
            addY(subtype() == 0 ? 0x40 : 0x80);                         // loc_20F134: btst #0,(a1); add.w d0,obj.y
            routine = 4;                                                // addq.b #4,obj.routine
            return;
        }
        fallFrames = subtype() == 0 ? 0x10 : 0x20;                      // move.w #$10,d0; addi.w #$10 when subtyped
        routine = ((subtype() + 1) * 2) & 0xFF;                         // addq.b #1,d1; add.b d1,d1
    }

    /** BossDoorObject_0_Routine2 and sub_20F15E (objects/r4/boss_door.asm:71-95). */
    private void waitForPlayer(PlayableEntity player) {
        if (player == null) return;
        if (((player.getCentreY() - y) & 0xFFFF) >= 0x80) return;       // subi.w #0; subi.w #$80,d0; bcc.s: no carry
        if (((player.getCentreX() - x - 0xA0) & 0xFFFF) >= 0x20) return;  // subi.w #$A0; subi.w #$20,d0; bcc.s
        triggered = true;                                               // st obj.var_3f(a0)
        routine = 4;
    }

    /** BossDoorObject_0_Routine4 (objects/r4/boss_door.asm:99-107). */
    private void waitForTrigger() {
        if (!triggered) return;
        var objects = services().objectManager();
        if (objects != null && spawn.respawnTracked()) ObjectLifetimeOps.markSpawnRemembered(objects, spawn);   // bset #0,(a1)
        routine = 6;
    }

    /** BossDoorObject_0_Routine6 (objects/r4/boss_door.asm:111-119). */
    private void fall() {
        addYLong(FALL_SPEED);
        fallFrames = (fallFrames - 1) & 0xFFFF;                         // addi.w #-1,obj.var_2a; bne.s
        if (fallFrames != 0) return;
        triggered = false;                                              // sf obj.var_3f(a0)
        routine = 4;                                                    // addi.b #-2,obj.routine
    }

    @Override String sheet() { return TtzBossArt.DOOR; }

    /** The routine's own DrawObject runs every frame, routine 0 included. */
    @Override protected boolean drawn() { return true; }

    @Override
    protected TtzBossDoor shell(ObjectSpawn spawn) {
        return new TtzBossDoor(spawn);
    }
}
