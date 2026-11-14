package com.openggf.game.soniccd.objects.boss.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseResult;

/**
 * A Hotaru firefly of the boss intro, object $39 with a subtype below $FD ({@code HotaruObject_2},
 * objects/r8/hotaru.asm:3-17, 41-299). It glows for $3C frames, then loops: pick a point (odd counts: over the player
 * while he is between X $BC0 and $CA0, at Y $158; else X $BF0 + (levelFrames & $7F), Y $160 + ((5 * player X) & $1F)),
 * fly there in $20 frames leaving a trail every 4, flash the room's lights ({@code byte_20F4F0}), open for $14
 * frames (enemy type $B, shooting every other frame), close for $10. Only while open can it be destroyed.
 */
final class MmzBossFirefly extends MmzBossPiece implements TouchResponseAttackable {
    /** HotaruObject_2_RoutineA: move.b #$B,obj.collide_type(a0) (scddisasm/src/objects/r8/hotaru.asm:246-251). */
    static final int OPEN_COLLISION = 0x0B;

    /** The intro that allocated it and watches its slot. */
    MmzBossHotaru owner;
    private int countdown;
    private int pattern;
    private int moveFrames;
    private int flashIndex;
    private int shotPhase;
    private int targetX;
    private int targetY;
    private int xVel;
    private int yVel;

    MmzBossFirefly(ObjectSpawn spawn) {
        super(spawn, "MmzBossFirefly");
    }

    @Override String sheet() { return MmzBossArt.HOTARU; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> { init(); glow(); }
            case 2 -> glow();
            case 4 -> pickTarget(player);
            case 6 -> fly();
            case 8 -> flash();
            case 0xA -> open();
            case 0xC -> close();
            default -> { }
        }
    }

    @Override protected boolean drawn() { return true; }

    /** HotaruObject_2_Routine0 (hotaru.asm:41-47). */
    private void init() {
        priority = 1;
        tileWord = MmzBossArt.HOTARU_TILE;
        yRadius = 8;
        width = 8;
        routine = 2;
        countdown = 0x3C;
    }

    /** HotaruObject_2_Routine2 (hotaru.asm:49-60). */
    private void glow() {
        anim = 4;
        animate(table("hotaruAnimations"));
        countdown = (countdown - 1) & 0xFF;
        if (countdown != 0) return;
        anim = 1;
        pattern = getSpawn().subtype() & 0x7F;                   // andi.b #$7F,obj.subtype(a0); move.b subtype,var_2b
        routine = 4;
    }

    /** HotaruObject_2_Routine4 (hotaru.asm:64-113). */
    private void pickTarget(PlayableEntity player) {
        int px = player == null ? 0 : player.getCentreX() & 0xFFFF;
        int column = px & 0xFFF8;
        if ((pattern & 1) != 0 && column >= 0xBC0 && column < 0xCA0) {
            targetX = column;
            targetY = 0x158;
        } else {
            var levels = services().levelManager();
            int levelFrames = levels == null ? 0 : levels.getFrameCounter();   // move.w stage_frames,d0
            targetX = (levelFrames & 0x7F) + 0xBF0;
            targetY = (((px << 2) + px) & 0x1F) + 0x160;
        }
        yVel = (short) ((targetY - y) << 3);
        xVel = (short) ((targetX - x) << 3);
        pattern = (pattern + 1) & 0xFF;
        moveFrames = 0x20;
        routine = 6;
        setCollision(0, 0);
    }

    /** HotaruObject_2_Routine6 / sub_20F48E (scddisasm/src/objects/r8/hotaru.asm:117-149). */
    private void fly() {
        addXLong((long) xVel << 8);
        addYLong((long) yVel << 8);
        if ((moveFrames & 3) == 3) spawn(new MmzBossFireflyTrail(spawnAt(MmzBossHotaru.ID, x, y, MmzBossHotaru.TRAIL)));
        moveFrames = (moveFrames - 1) & 0xFF;
        if (moveFrames == 0) routine = 8;
        animate(table("hotaruAnimations"));
    }

    /** HotaruObject_2_Routine8 (scddisasm/src/objects/r8/hotaru.asm:154-173): one light row a frame from {@code byte_20F4F0} until $FF. */
    private void flash() {
        anim = 0;
        animate(table("hotaruAnimations"));
        CdStageImage image = image();
        int base = table("hotaruFlashScript");
        if (image == null || base < 0) return;
        int row = image.u8(base + flashIndex);
        flashIndex = (flashIndex + 1) & 0xFF;
        if (row == 0xFF) {
            routine = 0xA;
            flashIndex = 0;
            return;
        }
        MmzBossHotaru.writeLightRow(this, goodFuture(), row);
    }

    /** HotaruObject_2_RoutineA / sub_20F548 (scddisasm/src/objects/r8/hotaru.asm:246-278). */
    private void open() {
        if (flashIndex == 0) {
            flashIndex = 0x14;
            anim = 2;
            setCollision(OPEN_COLLISION, 0);
        }
        int before = shotPhase;
        shotPhase = (shotPhase + 0x80) & 0xFF;                    // addi.b #-$80,obj.var_3f(a0); bcc
        if (before >= 0x80) spawn(new MmzBossFireflyShot(spawnAt(MmzBossHotaru.ID, x, y, MmzBossHotaru.SHOT)));
        animate(table("hotaruAnimations"));
        flashIndex = (flashIndex - 1) & 0xFF;
        if (flashIndex == 0) routine = 0xC;
    }

    /** HotaruObject_2_RoutineC (scddisasm/src/objects/r8/hotaru.asm:283-299). */
    private void close() {
        if (flashIndex == 0) {
            flashIndex = 0x10;
            anim = 0;
        }
        animate(table("hotaruAnimations"));
        flashIndex = (flashIndex - 1) & 0xFF;
        if (flashIndex != 0) return;
        routine = 4;
        anim = 1;
        MmzBossHotaru.writeLightRow(this, goodFuture(), 0);
    }

    private boolean goodFuture() {
        var payload = payload();
        return payload != null && payload.stage().period() == com.openggf.game.soniccd.CdPeriod.GOOD_FUTURE;
    }

    /** {@code Player_TouchEnemy .KillEnemy} (r8/player_object_collide.asm:165-230): an explosion with points. */
    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if (isDestroyed() || player == null) return;
        killAsEnemy(player, subtype2(), true);
    }

    @Override
    protected void onRemovedFromObjectManager() {
        if (owner != null) owner.fireflyRemoved(this);
        owner = null;
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new MmzBossFirefly(spawn);
    }
}
