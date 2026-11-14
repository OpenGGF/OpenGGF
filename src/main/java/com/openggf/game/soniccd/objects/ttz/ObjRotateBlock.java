package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.sprites.NativePositionOps;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

import java.util.List;

/**
 * Rotate block, object $3D in R41B and R42B ({@code RotateBlockObject}, objects/r4/rotate_block.asm:3-129):
 * a $80 tall, $80 wide block (tile $4300, priority 3) that turns a quarter at a time. It spawns two
 * invisible solid parts ({@link ObjRotateBlockPart}, the same ID with {@code var_3c = $FF}): part 0 is the
 * surface the player stands on, part 1 the side that is pushed. Routine 2 waits until the player stands on
 * part 0, then compares the player's X with the block's (RotateBlockObject_1_Routine2, objects/r4/rotate_block.asm:65-129): when the player is far
 * enough toward the turning side it measures the floor under the block (CheckBlockY, an alias of
 * ObjGetFloorDist: the floor at {@code (x, y + yRadius)}, Level/Tidal Tempest/R4 Compatibility.asm:223) and either turns
 * with the player riding (routine 6, :217-240) or, with the floor close, turns alone (routine 4, :190-207).
 * Every quarter plays {@code FM $BF} (RotateBlockObject_1_Routine4, objects/r4/rotate_block.asm:130-148).
 *
 * <p>The player's position is written in routine 6: Y to the block's top (RotateBlockObject_1_Routine6, objects/r4/rotate_block.asm:166-190), X to the block's X
 * after the first step and to {@code x ± $10} after the second (objects/r4/rotate_block.asm:192-210).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjRotateBlock extends TtzObject implements ObjectVisualFacing.NonDirectional {
    /** byte_20ED42 (RotateBlockObject_1_Routine4, objects/r4/rotate_block.asm:158-162): frames of a turn without the player, $FF ends it. */
    private static final int[][] TURN = {{0, 4, 1, -1}, {1, 5, 2, -1}, {2, 5, 1, -1}, {3, 6, 2, -1}};
    /** byte_20EDD8 (RotateBlockObject_1_Routine6, objects/r4/rotate_block.asm:220-224): frames of a turn carrying the player. */
    private static final int[][] CARRY = {{0, 7, 3, -1}, {1, 4, 2, -1}, {2, 4, 1, -1}, {3, 7, 0, -1}};
    /** move.w #$BF,d0; jsr PlayFmSound (RotateBlockObject_1_Routine4, objects/r4/rotate_block.asm:140-142). */
    private static final int TURN_SOUND = 0xBF;

    private int routine;
    private int mapFrame;
    /** oVar3D: the turn state, 0-3 (RotateBlockObject_1_Routine0, objects/r4/rotate_block.asm:33-44). */
    private int state;
    private int timer;
    private int step;
    private ObjRotateBlockPart standPart;
    private ObjRotateBlockPart pushPart;

    public ObjRotateBlock(ObjectSpawn spawn) {
        super(spawn, "RotateBlock");
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> init();
            case 2 -> waitForRider();
            case 4 -> turn();
            case 6 -> turnWithRider();
            default -> { }
        }
    }

    /** RotateBlockObject_1_Routine0 (objects/r4/rotate_block.asm:33-63). */
    private void init() {
        routine += 2;
        priority = 3;
        tileWord = TtzObjectArt.ROTATE_BLOCK_TILE;
        yRadius = 0x40;
        width = 0x40;
        mapFrame = spawn.subtype() & 3;
        state = mapFrame;
        standPart = new ObjRotateBlockPart(x, y, this, 0);
        if (!spawnChild(standPart)) { setDestroyed(true); return; }      // bne.w loc_20EBE2: DespawnObject
        pushPart = new ObjRotateBlockPart(x, y, this, 1);
        if (!spawnChild(pushPart)) setDestroyed(true);
    }

    int mapFrame() { return mapFrame; }
    int state() { return state; }

    /** RotateBlockObject_1_Routine2 (objects/r4/rotate_block.asm:65-129). */
    private void waitForRider() {
        if (standPart == null || !standPart.standingFlag()) return;
        var player = mainPlayer();
        if (player == null) return;
        int reach = (0x18 + 2) & 0xFF;                      // move.b obj.width_2(a1),d1; addq.b #2,d1 (the player's oWidth is $18)
        int d0;
        int d1;
        if (state < 2) {
            d0 = x & 0xFFFF;
            d1 = (reach + player.getCentreX()) & 0xFFFF;
        } else {
            d1 = x & 0xFFFF;
            d0 = (player.getCentreX() - reach) & 0xFFFF;
        }
        if (d0 < d1) return;                                // sub.w d1,d0; bcc.s loc_20ECC4
        // loc_20ECC4 loads d0 = $10 (objects/r4/rotate_block.asm:101-103) for the CheckBlockY call at (x, y + $10), not at the
        // block's bottom edge.
        int distance = floorDistance(x, y + 0x10);
        int low = distance & 0xFF;
        if ((byte) low < 0 || low < 0x10) {
            if (pushPart != null && pushPart.pushFlag()) {  // loc_20ECD6: tst.b $3E(a1); beq
                routine = 6;
                timer = 0x10;
                step = 0;
            }
        } else {
            routine += 2;                                   // loc_20ECF2
            timer = 0x10;
            step = 0;
        }
    }

    /** RotateBlockObject_1_Routine4 (objects/r4/rotate_block.asm:130-153). */
    private void turn() {
        int value = TURN[state][step];
        if (value < 0) {                                    // loc_20ED36
            state = mapFrame;
            routine -= 2;
            return;
        }
        mapFrame = value;
        if (--timer == 0) {
            services().playSfx(TURN_SOUND);
            timer = 4;
            step++;
        }
    }

    /** RotateBlockObject_1_Routine6 (objects/r4/rotate_block.asm:166-215). */
    private void turnWithRider() {
        int value = CARRY[state][step];
        if (value < 0) {                                    // loc_20EDCA
            state = mapFrame;
            routine = 2;
            return;
        }
        mapFrame = value;
        var player = mainPlayer();
        if (player != null) {
            // obj.y(a1) = obj.y(a0) - obj.height(a0) - obj.height(a1)
            NativePositionOps.writeYPosPreserveSubpixel(player, y - yRadius - player.getYRadius());
        }
        if (--timer != 0) return;
        timer = 8;
        step++;
        if (player == null) return;
        if (step == 1) {
            player.setCentreX((short) x);
        } else if (step == 2) {
            player.setCentreX((short) (x + ((state & 2) != 0 ? -0x10 : 0x10)));
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        drawSheet(TtzObjectArt.ROTATE_BLOCK, mapFrame, false, false);
    }
}
