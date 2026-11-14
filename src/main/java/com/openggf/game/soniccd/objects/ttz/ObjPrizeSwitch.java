package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Tidal Tempest's prize switches, object $3F ({@code PrizeSwitchObject}, objects/r4/prize.asm:3-372): a bonus game in R41C and R41D. The
 * placed object makes five more of itself ({@code PrizeSwitchObject_0_Routine0}, :21-62) at fixed offsets
 * ({@code word_20EEBC}); each is a solid button hit from below ({@code BottomSolidObject}). The first one the player
 * hits is the chosen one ({@code obj.var_3c} of the placed object); the placed object's {@code obj.var_35} is the
 * outcome picked from the second of the timer ({@code sub_20EFA4}, {@code byte_20EFCA}). Sixteen frames later every switch
 * becomes the outcome's routine ({@code off_20EF32}): 0 spikes ($22) over the others, 1 a $41 points sprite and 100 points,
 * 2 a stream of bubbles ($20, subtype 2) over the others, 3 a shower of $40 rings from the placed object. Every switch
 * despawns by the placed object's origin X ({@code obj.var_36}).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjPrizeSwitch extends TtzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    static final int ID = 0x3F;
    static final int TILE = 0x050A;
    /** word_20EEBC (prize.asm:67-74): the offsets of the five more switches. */
    private static final int[][] OFFSETS = {{0, 0}, {-0x40, 0x40}, {-0x80, 0x80}, {0x40, -0x40}, {0x60, -0x80}, {0x80, -0x80}};
    /** byte_20EFCA (prize.asm:176-220): the outcome by switch index and tens of seconds (six entries a switch). */
    private static final int[] OUTCOMES = {
            2, 1, 0, 3, 3, 2,
            1, 0, 3, 3, 2, 2,
            0, 3, 3, 2, 2, 1,
            2, 2, 1, 0, 3, 3,
            3, 2, 2, 1, 0, 3,
            3, 3, 2, 2, 1, 0,
            0, 0, 0, 0, 0, 0};
    /** byte_20F0DC (prize.asm:322-346): the X offsets of the ring shower. */
    private static final int[] SHOWER_X = {-0x20, 0, 0x20, 0x40, 0x60, 0x7F, 0x60, 0x40, 0x20, 0, -0x20,
            -0x20, 0, 0x20, 0x40, 0x60, 0x7F, 0x60, 0x40, 0x20, 0, -0x20};
    /** move.w #$BF,d0; jsr PlayFmSound (:86-92). */
    private static final int FM_BF = 0xBF;

    private int routine;
    private int index;                 // obj.var_34: 0 the placed object, 1-5 the others
    private int originX;               // obj.var_36
    private int homeY;                 // obj.var_32
    private int parentSlot = -1;       // obj.var_3a
    private int chosenSlot = -1;       // obj.var_3c of the placed object (0 = none)
    private int outcome;               // obj.var_35 of the placed object
    private boolean pressedBefore;     // obj.var_30 (low byte): obj.var_31 last frame
    private boolean pressed;           // obj.var_31
    private boolean armed;             // obj.var_2e: set when the sound has played
    private int arm;                   // obj.var_2f: counts up by $10 a frame
    private int counter38;             // obj.var_38 (byte)
    private int counter39;             // obj.var_39 (byte)
    private int mapFrame;

    public ObjPrizeSwitch(ObjectSpawn spawn) {
        super(spawn, "PrizeSwitch");
    }

    private ObjPrizeSwitch(ObjectSpawn spawn, int index, int originX, int parentSlot) {
        super(spawn, "PrizeSwitch");
        this.index = index;
        this.originX = originX;
        this.parentSlot = parentSlot;
        this.homeY = spawn.y();
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> init();
            case 2 -> routine2(player);
            default -> routine4(player);
        }
        // jsr DrawObject; move.w obj.var_36(a0),d0; jmp CheckObjectDespawn2 (:3-12): every switch despawns by the placed object's X.
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override protected int despawnX() { return originX; }

    /** PrizeSwitchObject_0_Routine0 (prize.asm:21-62). */
    private void init() {
        routine = 2;
        tileWord = TILE;
        priority = 3;
        width = 0xE;
        yRadius = 8;
        if (index != 0) return;                                         // tst.b obj.var_34(a0); bne.w locret
        originX = x;
        homeY = y;
        parentSlot = getSlotIndex();                                    // move.w a0,obj.var_3a(a0)
        for (int i = 1; i <= 5; i++) {                                  // moveq #1,d1; moveq #4,d2; dbf
            ObjectSpawn childSpawn = childSpawn(x + OFFSETS[i][0], y + OFFSETS[i][1], ID, 0, 0);
            findObjSlot(new ObjPrizeSwitch(childSpawn, i, originX, parentSlot));   // jsr SpawnObject; bne.s loc_20EEB4
        }
    }

    private ObjPrizeSwitch placed() {
        return slotOccupant(parentSlot) instanceof ObjPrizeSwitch p ? p : null;
    }

    /** PrizeSwitchObject_0_Routine2 (prize.asm:77-102). */
    private void routine2(PlayableEntity player) {
        press(player);
        ObjPrizeSwitch placed = placed();
        if (placed == null || placed.chosenSlot < 0) return;            // movea.w obj.var_3a,a1; move.w obj.var_3c(a1),d0; bne
        if (!armed) {
            services().playSfx(FM_BF);
            armed = true;
        }
        arm += 0x10;                                                    // addi.b #$10,obj.var_2f(a0); bcc.s locret
        if (arm < 0x100) return;
        arm &= 0xFF;
        routine = 4;
        if (player != null) ObjectManagerInternalAccess.releaseFromReevaluatingSolid(services().objectManager(), player, this);
        counter38 = 0;                                                  // clr.w obj.var_38(a0)
        mapFrame = 2;
    }

    /** sub_20EF3A (prize.asm:125-160). */
    private void press(PlayableEntity player) {
        boolean before = pressed;
        pressedBefore = before;
        boolean contact = solidObject(Entry.BOTTOM);                    // BottomSolidObject; sne obj.var_31(a0)
        pressed = contact;
        AbstractPlayableSprite sprite = player instanceof AbstractPlayableSprite p ? p : null;
        ObjPrizeSwitch placed = placed();
        if (contact && sprite != null && sprite.getYSpeed() >= 0 && !armed && placed != null) {
            placed.chosenSlot = getSlotIndex();                         // move.w a0,obj.var_3c(a1)
            int tens = (seconds() / 10) & 7;                            // divu #10,d1; andi.w #7,d1
            placed.outcome = OUTCOMES[index * 6 + tens];                // sub_20EFA4
        }
        if (!before && pressed) {                                       // cmpi.w #$FF,obj.var_30(a0)
            if (sprite != null) NativePositionOps.addYPosPreserveSubpixel(sprite, 8);
            y = (y + 4) & 0xFFFF;
            mapFrame = 1;
            yRadius -= 4;
        }
        if (before && !pressed) {                                       // cmpi.w #$FF00,obj.var_30(a0)
            if (sprite != null) NativePositionOps.addYPosPreserveSubpixel(sprite, -8);
            y = (y - 4) & 0xFFFF;
            mapFrame = 0;
            yRadius += 4;
        }
    }

    private int seconds() {
        var state = services().levelGamestate();
        return state == null ? 0 : (int) ((state.getTimerFrames() / 60) % 60);
    }

    /** PrizeSwitchObject_0_Routine4 (prize.asm:107-123): the outcome's routine. */
    private void routine4(PlayableEntity player) {
        ObjPrizeSwitch placed = placed();
        int which = placed == null ? 0 : placed.outcome & 3;
        boolean isChosen = placed != null && placed.chosenSlot == getSlotIndex();
        switch (which) {
            case 0 -> {                                                 // PrizeSwitchObject_1_Routine0 (:222-243)
                if (counter38 != 0) { setDestroyed(true); return; }
                if (!isChosen && index != 4) {
                    spawn(0x22, x, homeY, 0);                           // move.b #$22,obj.id(a1): the spikes
                }
                counter38++;
            }
            case 1 -> {                                                 // PrizeSwitchObject_1_Routine2 (:348-372)
                if (counter38 != 0) { setDestroyed(true); return; }
                if (!isChosen) {
                    var game = services().gameState();
                    if (game != null) game.addScore(100 * 10);          // moveq #100,d0; jsr AddPoints (units of ten)
                    spawn(0x41, x, y, 0);                               // move.b #$41,obj.id(a1)
                }
                counter38++;
            }
            case 2 -> {                                                 // PrizeSwitchObject_1_Routine4 (:249-280)
                if (isChosen) {
                    counter38 = (counter38 + 1) & 0xFF;                 // addq.b #1,obj.var_38(a0); bcc.s locret
                    if (counter38 == 0) setDestroyed(true);
                    return;
                }
                counter39 += 4;                                         // addq.b #4,obj.var_39(a0); bcc.s locret
                if (counter39 < 0x100) return;
                counter39 &= 0xFF;
                spawn(0x20, x, homeY, 2);                               // move.b #$20,obj.id(a1); move.b #2,obj.subtype(a1)
                counter38++;
                if (counter38 >= 2) setDestroyed(true);
            }
            default -> {                                                // PrizeSwitchObject_1_Routine6 (:282-301)
                if (placed != this) { setDestroyed(true); return; }
                counter38 += 0x10;                                      // addi.b #$10,obj.var_38(a0); bcc.s locret
                if (counter38 < 0x100) return;
                counter38 &= 0xFF;
                if (counter39 >= 0x15) { setDestroyed(true); return; }
                spawn(0x40, x + SHOWER_X[counter39], y - 0x180, 0);     // sub_20F0AC (:303-320)
                counter39++;
            }
        }
    }

    /** {@code jsr SpawnObject} then the id, position and subtype writes of the routine. */
    private void spawn(int id, int px, int py, int subtype) {
        ObjectSpawn childSpawn = childSpawn(px, py, id, subtype, 0);
        var child = switch (id) {
            case 0x40 -> new ObjRingPrize(childSpawn);
            case 0x41 -> new ObjPointsPrize(childSpawn);
            case 0x20 -> new ObjBubble(childSpawn, false, 0);
            default -> new ObjSpikes(childSpawn);                                 // $22: Tidal Tempest's spikes
        };
        if (child instanceof com.openggf.level.objects.AbstractObjectInstance a) findObjSlot(a);
    }

    @Override public int solidWidth() { return width; }
    @Override public int solidYRadius() { return yRadius; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        drawSheet(TtzObjectArt.PRIZE_SWITCH, mapFrame, false, false);
    }
}
