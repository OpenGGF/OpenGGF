package com.openggf.game.soniccd.objects.boss.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseResult;

/**
 * Eggman's booth, object $34 ({@code BossBoothObject}, objects/r5/boss.asm:224-504): the drill machine whose underside
 * wears on the belt. There is no hit count: {@link #wear} ({@code var_3e}) starts at $960 and the belt takes one off
 * for every $5D0 of belt travel ({@link QqzBossBelt}); the fight ends when it reaches zero or below.
 *
 * <ul>
 *   <li>Routine 0 allocates Eggman, the booth's back, the pipes and the belt, and takes the touch type $3E with
 *       status 2: an enemy type, so a rolling player rebounds off it and clears the type; anyone else is hurt.</li>
 *   <li>Routine 2 waits for the type to clear (FM $AC); routine 4 shakes 60 frames; routine 6 lowers the booth onto the
 *       belt ($13000 a frame to the target, then up $8000 a frame to half way, then down again), landing with
 *       FM $B4, eight falling sparks and a bottom-boundary shake each time; the first landing also plays FM $C4.</li>
 *   <li>Routine 8 is the fight: solid; one spark rolls off for every sixth wear point; at wear $708, $4B0 and $258
 *       the booth sinks by the drop table's next step (routine 6 again).</li>
 *   <li>At zero it gives out: Eggman flees, the top widens the right boundary, routine A throws sparks every 18 frames
 *       for 60, routine C flashes for 120 frames with an explosion every fourth, then the booth stays hidden.</li>
 * </ul>
 * Every frame after its routine it stands at X $DD0, jittering by a pixel while the belt runs at $400 or more, and
 * draws unless hidden.
 */
public final class QqzBossBooth extends QqzBossPiece implements ReevaluatingSolidObject, TouchResponseAttackable {
    /** BossBoothObject_0_Routine0: move.w #$960,obj.var_3e(a0) (objects/r5/boss.asm:285). */
    static final int FULL_WEAR = 0x960;
    /** Booth X: move.w #$DD0,obj.x(a0) (objects/r5/boss.asm:231). */
    static final int BOOTH_X = 0xDD0;
    /** FM IDs: $AC fight start, $B4 landing, $C4 first stop (objects/r5/boss.asm:290, 342, 373). */
    static final int FM_START = 0xAC, FM_LAND = 0xB4, FM_STOP = 0xC4;

    // var_2e / var_30 / var_32: the top, Eggman and the belt.
    QqzBossTop top;
    QqzBossEggman eggman;
    QqzBossBelt belt;
    /** var_3e: the wear left. */
    int wear;
    /** var_34: the jitter toggle. */
    private int jitter;
    /** var_38 / var_36: the drop's bottom and the bounce's top. */
    private int dropBottom;
    private int bounceTop;

    QqzBossBooth(int x, int y, QqzBossTop top) {
        super(0x34, x, y, "QqzBossBooth");
        this.top = top;
    }

    private QqzBossBooth(ObjectSpawn spawn) {
        super(spawn, "QqzBossBooth");
    }

    /** {@code var_3e}: the wear left. */
    public int wear() { return wear; }

    @Override String sheet() { return QqzBossArt.BOOTH; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        skipDraw = false;
        switch (routine) {
            case 0 -> {
                init();
                waitForTouch();
            }
            case 2 -> waitForTouch();
            case 4 -> shakeBeforeDrop();
            case 6 -> drop();
            case 8 -> fight();
            case 0xA -> giveOut();
            case 0xC -> flash();
            default -> { }
        }
        if (has(HIDDEN)) {                                          // btst #3,obj.var_2c(a0); bne.s locret
            skipDraw = true;
            return;
        }
        setXWord(BOOTH_X);
        int beltSpeed = belt == null ? 0 : belt.speed;             // cmpi.w #$400 / #$500,obj.var_32(a1)
        if (beltSpeed >= 0x400) {
            if (beltSpeed >= 0x500) {
                addX(1);
                jitter ^= 1;
                if (jitter != 0) addX(-2);
            } else {
                jitter ^= 1;
                if (jitter != 0) addX(-1);
            }
        }
    }

    /** BossBoothObject_0_Routine0 (objects/r5/boss.asm:271-285). */
    private void init() {
        routine = 2;
        priority = 4;
        width = 0x20;
        yRadius = 0x70;
        tileWord = QqzBossArt.PART_TILE;
        // sub_20C2DC, sub_20C35C, sub_20C338, sub_20C3E2 (objects/r5/boss.asm:1656-1760), in this order.
        QqzBossEggman newEggman = new QqzBossEggman(x, y, this);
        if (spawn(newEggman)) eggman = newEggman;
        spawn(new QqzBossBoothBack(x, y + 0x38, this));
        spawn(new QqzBossPipes(x, y, this));
        QqzBossBelt newBelt = new QqzBossBelt(this);
        if (spawn(newBelt)) belt = newBelt;
        setCollision(0x3E, 2);
        wear = FULL_WEAR;
    }

    /** BossBoothObject_0_Routine2 (objects/r5/boss.asm:287-296). */
    private void waitForTouch() {
        if (colType() != 0) return;
        fmSound(FM_START);
        routine = 4;
        setCollision(0, 0);
    }

    /** BossBoothObject_0_Routine4 (objects/r5/boss.asm:300-323): 60 frames of +-2, then the first drop. */
    private void shakeBeforeDrop() {
        timer = (timer + 1) & 0xFF;
        if (timer != 0x3C) {
            addY((timer & 1) != 0 ? 2 : -2);
            return;
        }
        // loc_20B630: x_speed.l = $13000 is written and never read.
        timer = 0;
        routine = 6;
        if (eggman != null) eggman.setPose(1);           // loc_20C400 with a1 = Eggman, d0 = 1
        dropBottom = 0x288;
        bounceTop = 0x280;
        drop();
    }

    /** BossBoothObject_0_Routine6 (objects/r5/boss.asm:325-383). */
    private void drop() {
        if (step == 1) {
            // loc_20B69E: back up $8000 a frame until above the bounce top.
            if (yWord() >= bounceTop) {
                addYLong(-0x8000);
                return;
            }
            fmSound(FM_LAND);
            step = 2;
            return;
        }
        // loc_20B66A (steps 0 and 2).
        if (yWord() < dropBottom) {
            addYLong(0x13000);
            return;
        }
        fmSound(FM_LAND);
        set(LANDED);
        sparkBurst();
        if (step == 0) {
            step = 1;
            return;
        }
        // loc_20B6C2.
        if (mapFrame == 0) {
            fmSound(FM_STOP);
            mapFrame = 1;
            if (eggman != null) eggman.setPose(0);       // loc_20C400 with a1 = Eggman, d0 = 0
        }
        routine = 8;
        step = 0;
    }

    /** BossBoothObject_0_Routine8 (objects/r5/boss.asm:387-442). */
    private void fight() {
        solidObject(Entry.SOLID);                                  // loc_20B802
        // divu.w #6 of the wear as an unsigned word; the remainder decides.
        if ((wear & 0xFFFF) % 6 != 0) {
            clear(LATCH);
        } else if (!testAndSet(LATCH)) {
            groundSpark();
        }
        if ((short) wear <= 0) {
            beaten();
            return;
        }
        if (wear == 0x708 || wear == 0x4B0 || wear == 0x258) sink();
    }

    /** loc_20B736 (objects/r5/boss.asm:417-425). */
    private void beaten() {
        set(BEATEN);
        if (eggman != null) eggman.set(BEATEN);
        routine = 0xA;
        mapFrame = (mapFrame + 1) & 0xFF;
        if (top != null) top.setRoutine(4);
    }

    /** loc_20B75C (objects/r5/boss.asm:429-442): the next drop is the table's step for this frame, the bounce half. */
    private void sink() {
        sparkBurst();
        var image = stageImage();
        var payload = payload();
        int d = image == null || payload == null ? 0 : QqzBossCatalogue.boothDrop(image, payload, mapFrame);
        dropBottom = (yWord() + d) & 0xFFFF;
        bounceTop = (yWord() + ((d & 0xFFFF) >>> 1)) & 0xFFFF;     // lsr.w #1,d0
        routine = 6;
        mapFrame = (mapFrame + 1) & 0xFF;
    }

    /** BossBoothObject_0_RoutineA (objects/r5/boss.asm:454-476). */
    private void giveOut() {
        solidObject(Entry.SOLID);
        timer = (timer + 1) & 0xFF;
        if (timer != 0x3C) {
            if (timer % 0x12 == 0) sparkBurst();
            return;
        }
        timer = 0;
        routine = 0xC;
        // move.b #1,Z80_RAM+$1C3E: the FM driver's flag that lets its conditional jumps branch (the player's death
        // writes it too, Level/_Objects/Sonic/Main.asm:2461-2463). A Z80 RAM write; the FM driver here does not model
        // it, so it is not reproduced (reported to the audio owner).
    }

    /** BossBoothObject_0_RoutineC (objects/r5/boss.asm:480-495). */
    private void flash() {
        if (timer >= 0x78) {                                       // cmpi.b #$78,obj.var_2a(a0); bcc.s
            set(HIDDEN);
            return;
        }
        solidObject(Entry.SOLID);
        timer = (timer + 1) & 0xFF;
        explosionCycle();
        if ((timer & 1) != 0) clear(HIDDEN);
        else set(HIDDEN);
    }

    /** sub_20C196 (objects/r5/boss.asm:1532-1541): one spark rolling left along the belt from ($DE8, $2B8). */
    private void groundSpark() {
        spawn(new QqzBossSparks(0xDE8, 0x2B8, 0));
    }

    /**
     * sub_20C1C4 (objects/r5/boss.asm:1555-1580): eight falling sparks at the booth plus the offset table, lowered by
     * the Y table's entry for this map frame; the first failed allocation ends the burst.
     */
    private void sparkBurst() {
        var image = stageImage();
        var payload = payload();
        if (image == null || payload == null) return;
        int dy = QqzBossCatalogue.sparkY(image, payload, mapFrame);
        for (int i = 0; i < 8; i++) {
            int[] o = QqzBossCatalogue.sparkOffset(image, payload, i);
            if (!spawn(new QqzBossSparks(x + o[0], y + o[1] + dy, 1))) return;
        }
    }

    /**
     * sub_20C238 (objects/r5/boss.asm:1596-1628): every fourth count an explosion $23 at one of four offsets
     * ({@code (count / 4) mod 4}) and FM_EXPLODE. It calls SpawnObject twice; the first slot is not claimed (the ID is
     * never written), so both calls find the same slot and only a full pool stops it.
     */
    private void explosionCycle() {
        if ((timer & 3) != 0) return;
        var image = stageImage();
        var payload = payload();
        if (image == null || payload == null) return;
        int index = (timer >> 2) & 3;
        int[] o = QqzBossCatalogue.explosionOffset(image, payload, index);
        if (spawn(new QqzBossExplosion(x + o[0], y + o[1]))) fmSound(FM_EXPLODE);
    }

    /**
     * {@code PlayerObjectCollide}'s enemy branch, sub_206FA6 (r5/player_object_collide.asm:126-146), for a rolling or
     * invincible player: with a collision status left the type is cleared and the status counts down (the shared
     * touch owner applies the halved rebound); the zone's kill path for a spent status is a bare {@code rts}.
     */
    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if (colStatus == 0) return;
        colType = 0;
        colStatus = (colStatus - 1) & 0xFF;
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new QqzBossBooth(spawn);
    }
}
