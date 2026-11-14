package com.openggf.game.soniccd.objects.boss.ssz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.NativePositionOps;

/**
 * The race's spikes, object $32 ({@code SpikesObject}, objects/r7/spikes.asm:3-180), placed on the track by the act 3
 * layout. Act 3 links them with the race (Level/Stardust Speedway/R7 Shared.asm:210-215), and they look for Metal
 * Sonic: at init a spike finds the first $2F and deletes itself when he has already reached it (or there is none);
 * then, once he is within its width, it breaks into four pieces (word_20EEA2; FM $A3) for him to run through.
 * Otherwise it is solid and hurts a player standing on it ({@code HurtPlayer} after backing out the Y speed),
 * unless invincible, hurt or flashing. A piece (routine 4) flies under its speeds and flickers, drawn every other
 * frame, until it leaves the despawn range.
 */
final class SszBossSpikes extends SszBossPiece implements ReevaluatingSolidObject {
    static final int ID = 0x32;
    /** loc_20EE0A: move.w #$A3,d0; jsr PlayFmSound (objects/r7/spikes.asm:105-108). */
    static final int FM_BREAK = 0xA3;

    /** var_2a: the Metal Sonic found at init (sub_20EEDA). */
    SszMetalSonic metalLink;
    /** var_35: the player stood on it this frame. */
    private boolean stoodOnThisFrame;
    /** var_34 bit 0: a piece's flicker. */
    private int flicker;

    SszBossSpikes(ObjectSpawn spawn) {
        super(spawn, "SszBossSpikes");
    }

    private SszBossSpikes(int x, int y, int[] speeds) {
        super(ID, x, y, "SszBossSpikePiece");
        routine = 4;
        mapFrame = 1;
        priority = 5;
        width = 4;
        yRadius = 0x10;
        tileWord = 0x4FC;
        xSpeed = speeds[0];
        ySpeed = speeds[1];
        accelX = speeds[2];
        gravity = speeds[3];
    }

    @Override String sheet() { return SszBossArt.SPIKES; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        skipDraw = false;
        switch (routine) {
            case 0 -> {
                if (!init()) return;
                guard(sonic(player), vIntRunCount);
            }
            case 2 -> guard(sonic(player), vIntRunCount);
            case 4 -> {                                         // SpikesObject_0_Routine4 (:154-161)
                accelerateAndMove();
                flicker ^= 1;
                skipDraw = flicker == 0;
            }
            default -> { }
        }
    }

    /** SpikesObject_0_Routine0 (objects/r7/spikes.asm:18-44); false when it deleted itself. */
    private boolean init() {
        routine = 2;
        priority = 3;
        tileWord = 0x4FC;
        width = 0x10;
        yRadius = 0xC;
        SszMetalSonic metal = firstMetalSonic();
        // move.w obj.x(a0),d0; sub.w width_2(a0); sub.w 8(a1); sub.w $19(a1); blt.s loc_20ED6E
        if (metal == null || (short) (xWord() - width - metal.xWord() - metal.halfWidth()) < 0) {
            skipDraw = true;
            deleteObject();
            return false;
        }
        metalLink = metal;
        return true;
    }

    /** sub_20EEDA (objects/r7/spikes.asm:165-180): the first slot holding ID $2F. */
    private SszMetalSonic firstMetalSonic() {
        var objects = services().objectManager();
        if (objects == null) return null;
        SszMetalSonic first = null;
        for (var object : objects.getActiveObjects()) {
            if (object instanceof SszMetalSonic metal && !metal.isDestroyed()
                    && (first == null || metal.getSlotIndex() < first.getSlotIndex())) {
                first = metal;
            }
        }
        return first;
    }

    /** SpikesObject_0_Routine2 (objects/r7/spikes.asm:48-100). */
    private void guard(AbstractPlayableSprite player, int vIntRunCount) {
        if (metalLink != null && !metalLink.isDestroyed()) {
            int d0 = (short) (xWord() - metalLink.xWord());
            if (d0 < 0) d0 = -d0;
            if (d0 < width) {
                breakApart(player);
                return;
            }
        }
        if (!solidObject(ReevaluatingSolidObject.Entry.SOLID)) return;
        stoodOnThisFrame = false;
        if (player == null || !stoodOn(player)) return;         // btst #3,obj.flags(a1)
        stoodOnThisFrame = true;
        if (player.getInvincibleFrames() > 0) return;           // tst.b invincible
        if (player.isHurt() || player.getInvulnerableFrames() > 0) return;   // routine 4; tst.w obj.var_30(a0)
        NativePositionOps.addYPos16_16(player, -(player.getYSpeed() << 8));
        boolean hadRings = player.getRingCount() > 0;
        if (hadRings && !player.hasShield()) services().spawnLostRings(player, vIntRunCount);
        player.applyHurtOrDeath(x, true, hadRings);             // jsr HurtPlayer
        clearStoodOn();                                         // jsr GetOffObject
    }

    /** loc_20EE0A (objects/r7/spikes.asm:104-142): four pieces at X - $C, - 4, + 4, + $C, then delete. */
    private void breakApart(AbstractPlayableSprite player) {
        fmSound(FM_BREAK);
        CdStageImage image = stageImage();
        if (image != null && payload() != null) {
            int offset = -0xC;
            for (int i = 0; i < 4; i++) {
                SszBossSpikes piece = new SszBossSpikes(xWord() + offset, yWord(),
                        SszBossCatalogue.spikePiece(image, payload(), i));
                if (!spawn(piece)) break;                       // SpawnObject; bne.s loc_20EE8C
                offset += 8;
            }
        }
        if (stoodOnThisFrame && player != null) clearStoodOn();  // tst.b obj.var_35(a0); GetOffObject
        skipDraw = true;
        deleteObject();
    }

    @Override protected boolean drawn() { return !skipDraw && routine != 0; }

    @Override public boolean isCustomOutOfRange(int cameraX) { return despawnOutOfRange(cameraX); }

    @Override
    protected com.openggf.game.soniccd.objects.boss.CdBossPart shell(ObjectSpawn spawn) {
        return new SszBossSpikes(spawn);
    }
}
