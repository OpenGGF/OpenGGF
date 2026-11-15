package com.openggf.game.soniccd.objects.wwz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.rings.LostRingObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Goddess statue, object $08 ({@code ObjGoddessStatue}, Level/Wacky Workbench/Objects/Goddess Statue/Main.asm:
 * 164-264): an invisible trigger (it is never drawn, so its on-screen bit never sets). When the player is
 * 0 to 15 pixels right of it and 0 to 31 below (unsigned word tests, :37-49), it spits 50 rings, one
 * every 11 frames, from 24 right and 16 above itself, each with a vertical speed of -$200 and a horizontal
 * speed picked from a table by {@code Random}. Act 2 and 3 indexes leave $08 to {@code NullObject}
 * (r6/object_index_2.asm:11).
 *
 * <p>The rings are lost-ring objects ($11, {@code ObjLostRing}, Level/_Objects/Ring/Main.asm:317-409), which the shared
 * lost-ring owner hosts (CD's routine is Sonic 1's Obj37, see the common objects report). The statue allocates each one
 * with {@code FindObjSlot} (the lowest free dynamic slot, nothing happens when there is none), then draws
 * {@code Random} for the horizontal speed, so the draws follow slot order; it starts the ring at routine 2 (the main
 * routine, no init) and sets {@code ringLossAnimTimer} to $FF, which is the shared owner's full lifetime. The statue's
 * scatter differs from the hurt scatter only in its speeds: -$200 vertical and one of eight horizontal speeds from the
 * statue's own table, where a hurt ring takes the $288 angle sweep.
 */
public final class ObjGoddessStatue extends WwzObject implements ObjectVisualFacing.NonDirectional {
    private static final int MAIN = 2, SPIT = 4, DONE = 6;
    /** {@code move.b #50,oGoddessCount(a0)} (:32). */
    private static final int RING_COUNT = 50;
    /** {@code move.b #10,oGoddessTime(a0)} (:61). */
    private static final int RING_PERIOD = 10;

    private int time;
    private int count;
    private int ringsSpat;

    public ObjGoddessStatue(ObjectSpawn spawn) {
        super(spawn, "GoddessStatue");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (WwzStage.nullObject(services(), 0x08)) {
            setDestroyed(true);
            return;
        }
        AbstractPlayableSprite sprite = sprite(player);
        switch (routine) {
            case 0 -> {
                routine = MAIN;
                count = RING_COUNT;
                main(sprite);
            }
            case MAIN -> main(sprite);
            case SPIT -> spit();
            default -> { }
        }
    }

    /** ObjGoddessStatue_Main (:36-54). */
    private void main(AbstractPlayableSprite player) {
        if (player == null) return;
        int dx = (player.getCentreX() - x) & 0xFFFF;
        if (dx + 16 > 0xFFFF || ((dx + 16) & 0xFFFF) >= 32) return;      // addi.w #16 / bcs.s / cmpi.w #32 / bcc.s
        int dy = (player.getCentreY() - y) & 0xFFFF;
        if (dy + 32 > 0xFFFF || ((dy + 32) & 0xFFFF) >= 64) return;      // addi.w #32 / bcs.s / cmpi.w #64 / bcc.s
        routine = SPIT;
    }

    /** ObjGoddessStatue_SpitRings (:58-70). */
    private void spit() {
        time = (time - 1) & 0xFF;
        if ((time & 0x80) == 0) return;                                  // bpl.s ObjGoddessStatue_Done
        time = RING_PERIOD;
        count = (count - 1) & 0xFF;
        if ((count & 0x80) == 0) {
            spawnRing();                                                 // ObjGoddessStatue_SpawnRing
        } else {
            routine = DONE;
        }
    }

    /** ObjGoddessStatue_SpawnRing (:72-100). */
    private void spawnRing() {
        var objects = services().objectManager();
        var rings = services().ringManager();
        var provider = artProvider();
        if (objects == null || rings == null || provider == null || !objects.hasFreeDynamicSlot()) return;   // FindObjSlot / bne.s .End
        int table = WwzObjectCatalogue.offset(provider.payload(), "goddessXVels");
        int xVel = (short) provider.image().u16(table + ((services().rng().nextRaw() << 1) & 0xE));   // Random / lsl.w #1 / andi.w #$E
        LostRingObjectInstance ring = LostRingObjectInstance.spawn(   // move.b #$11,oID(a1) (Level/Wacky Workbench/Objects/Goddess Statue/Main.asm:76)
                com.openggf.level.rings.RingManagerInternalAccess.lostRingObjectId(rings), x + 24, y - 16, xVel, -0x200, 0, 0xFF,
                rings.getSpillAnimationState());
        objects.addDynamicObject(ring);
        rings.getSpillAnimationState().reset();                          // move.b #$FF,ringLossAnimTimer
        ring.setSpillPhaseOffset(ring.getSlotIndex());
        ringsSpat++;
    }

    /** The routine never draws, so {@code oSprFlags} bit 7 stays clear (:12-17). */
    @Override
    protected boolean renderFlagOnScreen() {
        return false;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override protected String sheetKey() { return null; }

    public int ringsSpat() { return ringsSpat; }
}
