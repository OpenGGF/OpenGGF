package com.openggf.game.soniccd.objects.boss.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * The belt under the booth, object $22 ({@code BossBeltObject}, objects/r5/boss.asm:1134-1489), allocated by the booth
 * at (0, 0) and never drawn. It runs the arena's conveyor itself (the zone's conveyor blocks are off while
 * {@code boss_flags} is set, player_block_check.asm:12-13), and its travel is what wears the booth down.
 *
 * <ul>
 *   <li>Routine 0 steps the belt colours once; routine 2 waits for the booth to leave routine 2.</li>
 *   <li>Routine 4 pushes the player left 3 pixels a frame (stopping a rightward X speed) until it is at X $D88 or
 *       less, then starts the belt at speed $C0 (routine 6).</li>
 *   <li>Routine 6 each frame: while the booth stands, the belt speeds up 8 when the grounded player's X speed exceeds
 *       it, slows 7 when below, loses 1 a frame while the player is airborne and drops to $C0 while it is hurt or dead,
 *       within $C0-$5D0. A grounded player is carried left by the speed (at most $300 faster than its own X speed);
 *       the frame it leaves the belt, the X and Y speed of its last grounded frame are taken off its speed. The player
 *       is held at X $D88 or less. The travel adds the speed to a counter; every $5D0 takes one off the booth's wear.
 *       Once the booth gives out the speed falls $10 a frame and the belt stops at zero (routine 8).</li>
 *   <li>Routines 4 and 6 step the three belt colours (palette line 2, colours $A-$C; the future's own table) every
 *       frame above speed $300, every second above $200, every fourth below.</li>
 * </ul>
 */
public final class QqzBossBelt extends QqzBossPiece {
    /** The player's limit, X $D88: loc_20BEE2 (objects/r5/boss.asm:1189-1199) and loc_20C048 (:1373-1381). */
    static final int PLAYER_LIMIT_X = 0xD88;
    /** loc_20BF88 / loc_20BF98 (objects/r5/boss.asm:1281-1293) and loc_20BFA8 (:1302): the speed range and wear unit. */
    static final int MIN_SPEED = 0xC0, MAX_SPEED = 0x5D0;
    /** loc_20BFF2: the belt moves the player at most $300 faster than its own X speed (objects/r5/boss.asm:1334-1337). */
    static final int MAX_SLIP = 0x300;
    /** Hurt $1A, drowning $19, death $18 animations stop the belt (sub_20C0F8, objects/r5/boss.asm:1460-1465). */
    private static final int ANIM_DEATH = 0x18, ANIM_DROWN = 0x19, ANIM_HURT = 0x1A;
    /** movea.l #(palette+$54),a4: line 2, colour $A (objects/r5/boss.asm:1440). */
    private static final int BELT_COLOURS = 0x54;

    /** var_2e: the booth. */
    QqzBossBooth booth;
    /** var_32: the belt speed. */
    int speed;
    /** var_34: travel toward the next wear point. */
    int travel;
    /** var_3e: the belt colour step (0, 6, $C). */
    private int colourStep;
    /** var_36 / var_38: the player's X and Y speed on its last grounded frame. */
    private int groundXSpeed;
    private int groundYSpeed;

    QqzBossBelt(QqzBossBooth booth) {
        super(0x22, 0, 0, "QqzBossBelt");
        this.booth = booth;
    }

    private QqzBossBelt(ObjectSpawn spawn) {
        super(spawn, "QqzBossBelt");
    }

    /** {@code var_32}: the belt speed. */
    public int speed() { return speed; }

    /** {@code var_34}: travel toward the next wear point. */
    public int travel() { return travel; }

    @Override String sheet() { return null; }

    /** BossBeltObject jumps to its routine and never reaches DrawObject (objects/r5/boss.asm:1134-1140). */
    @Override protected boolean drawn() { return false; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        AbstractPlayableSprite sonic = player instanceof AbstractPlayableSprite s ? s : null;
        switch (routine) {
            case 0 -> {
                routine = 2;                                        // BossBeltObject_0_Routine0 (:1153-1155)
                colourStep();
            }
            case 2 -> {
                if (booth != null && booth.routine() == 2) return; // BossBeltObject_0_Routine2 (:1159-1167)
                routine = 4;
                pushToLimit(sonic);
            }
            case 4 -> pushToLimit(sonic);
            case 6 -> run(sonic);
            default -> { }                                          // BossBeltObject_0_Routine8: rts
        }
    }

    /** BossBeltObject_0_Routine4 (objects/r5/boss.asm:1169-1185); sub_20C41C always returns equal. */
    private void pushToLimit(AbstractPlayableSprite player) {
        colourCycle();
        if (player == null) return;
        if ((short) player.getCentreX() > PLAYER_LIMIT_X) {
            // loc_20BEE2: subq.w #3,obj.x(a1); a non-negative X speed is cleared.
            NativePositionOps.addXPosPreserveSubpixel(player, -3);
            if (player.getXSpeed() >= 0) player.setXSpeed((short) 0);
            return;
        }
        speed = MIN_SPEED;                                          // loc_20BEFE
        routine = 6;
    }

    /** BossBeltObject_0_Routine6 (objects/r5/boss.asm:1215-1234). */
    private void run(AbstractPlayableSprite player) {
        if (booth != null && booth.has(BEATEN)) {
            speed = (short) (speed - 0x10);
            if (speed <= 0) {
                routine = 8;
                return;
            }
        }
        if (player != null) carry(player);                        // loc_20BF3C (once: sub_20C41C is always equal)
        travel = (short) (travel + speed);                         // loc_20BFA8
        if (travel >= MAX_SPEED) {
            if (booth != null) booth.wear = (short) (booth.wear - 1);
            travel = (short) (travel - MAX_SPEED);
        }
        colourCycle();
    }

    /** loc_20BF3C (objects/r5/boss.asm:1238-1247). */
    private void carry(AbstractPlayableSprite player) {
        int state = playerState(player);
        boolean beaten = booth != null && booth.has(BEATEN);
        if (!beaten) adjustSpeed(player, state);
        if (state == 1) {
            // loc_20C01A: the first airborne frame takes the last grounded speeds off the player's.
            if (!testAndSet(AIRBORNE)) {
                player.setXSpeed((short) (player.getXSpeed() - groundXSpeed));
                player.setYSpeed((short) (player.getYSpeed() - groundYSpeed));
            }
        } else {
            clear(AIRBORNE);
            groundXSpeed = player.getXSpeed();
            groundYSpeed = player.getYSpeed();
            if (state == 0) {
                // loc_20BFF2: moveq #0,d0; move.w obj.var_32(a0),d0; the slip cap is a signed word sum, ext.l.
                long d0 = speed & 0xFFFF;
                if ((short) (speed - player.getXSpeed()) > MAX_SLIP) d0 = (short) (MAX_SLIP + player.getXSpeed());
                NativePositionOps.addXPos16_16(player, (int) -(d0 << 8));   // asl.l #8,d0; sub.l d0,obj.x(a1)
            }
        }
        // loc_20C048: hold the player at X $D88 while the booth stands.
        if (!beaten && (short) player.getCentreX() >= PLAYER_LIMIT_X) {
            NativePositionOps.writeXPosPreserveSubpixel(player, PLAYER_LIMIT_X);
        }
    }

    /** loc_20BF58 (objects/r5/boss.asm:1251-1295). */
    private void adjustSpeed(AbstractPlayableSprite player, int state) {
        if (state > 0) {
            speed = (short) (speed - 1);
        } else if (state < 0) {
            speed = MIN_SPEED;
        } else {
            int xSpeed = player.getXSpeed();
            if (xSpeed < speed) speed = (short) (speed - 7);
            else if (xSpeed > speed) speed = (short) (speed + 8);
        }
        if (speed <= MIN_SPEED) speed = MIN_SPEED;
        else if (speed >= MAX_SPEED) speed = MAX_SPEED;
    }

    /**
     * sub_20C0F8 (objects/r5/boss.asm:1460-1484): -1 while the player shows the hurt, drowning or death animation (or
     * has bit 7 of its flags set, which no player path sets: only the touch routine sets it, on objects,
     * r5/player_object_collide.asm:143-204), 1 while airborne, 0 on the ground.
     */
    private static int playerState(AbstractPlayableSprite player) {
        int a = player.getAnimationId();
        if (a == ANIM_HURT || a == ANIM_DROWN || a == ANIM_DEATH) return -1;
        return player.getAir() ? 1 : 0;
    }

    /** loc_20C060 (objects/r5/boss.asm:1385-1421). */
    private void colourCycle() {
        int period = speed > 0x300 ? 1 : speed > 0x200 ? 2 : 4;
        timer = (timer + 1) & 0xFF;
        if (period > timer) return;
        timer = 0;
        colourStep();
    }

    /** loc_20C09E (objects/r5/boss.asm:1425-1444): three colours from the future's table at the next step. */
    private void colourStep() {
        colourStep = (colourStep + 6) & 0xFF;
        if ((byte) colourStep >= 0x12) colourStep = 0;
        var image = stageImage();
        var payload = payload();
        if (image == null || payload == null) return;
        writePalette(image, QqzBossCatalogue.beltPalette(payload, flags().goodFuture, colourStep), BELT_COLOURS, 6);
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new QqzBossBelt(spawn);
    }
}
