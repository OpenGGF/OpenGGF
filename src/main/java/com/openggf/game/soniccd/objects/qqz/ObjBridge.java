package com.openggf.game.soniccd.objects.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdStandOnObject;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.NativePositionOps;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.physics.TrigLookupTable;

/**
 * Bridge, object $2F ({@code BridgeObject}, objects/r5/bridge.asm:18-26): Sonic 1's log bridge on Sonic CD's
 * routines. The object is the ninth log and the hub: routine 0 makes eight pieces ({@link ObjBridgePiece},
 * routine 4) at {@code x + $30 ... x - $40} in steps of 16 with {@code SpawnObject}, left to right in slot
 * order (:37-90); routine 2 (BridgeMain, :114-146) finds out where the player stands, sinks by 4 of an angle
 * up to {@code $40} while stood on and rises by 4 when not, and bends the pieces by
 * {@code ((row + 1) * strength * sin(sink)) >> 16} ({@code BridgeBend}, :206-286) using the stage's own
 * {@code BridgeBendStrengths} and {@code BridgeBendYOffsets} (:289-331, read from the disc).
 *
 * <p>The player is stood on with the routine's own window (BridgeCheckPlayer, :334-396), not SolidObject:
 * within {@code $48} either side horizontally, falling or still, and {@code [1, $10]} above the log row by
 * {@code oYRadius + 4}; the player's Y is set to sit on the bridge and StandOnObject runs. The bend index is
 * the player's 16-pixel column ({@code (px - x + $48) >> 4}); index 0 reads as not stood on, as assembled
 * (:114-120), and the unbend always uses index 0 (BridgeMain clears it first). CheckObjectDespawn runs on the
 * hub only; pieces delete themselves with it (:99-110). Shipped {@code FixBugs = 0}.
 *
 * <p>StandOnObject is {@link CdStandOnObject} (the roll landing checked against R51A row 744); the shared
 * solid owner has no public primitive for it, so its riding record is not created (reported as a need).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjBridge extends QqzObject implements ObjectVisualFacing.NonDirectional {
    private static final int[] X_OFFSETS = {0x30, 0x20, 0x10, 0, -0x10, -0x20, -0x30, -0x40};

    private final ObjBridgePiece[] pieces = new ObjBridgePiece[8];
    private int sinkAngle;         // oVar3A
    private int bendX;             // oVar3D
    private boolean standingPlayer;

    public ObjBridge(ObjectSpawn spawn) {
        super(spawn, "Bridge");
    }

    @Override public ObjectVisualFacing.Basis nativeFacing() { return ObjectVisualFacing.Basis.NON_DIRECTIONAL; }

    @Override protected String sheetKind() { return QqzObjectArt.BRIDGE; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    protected void run(PlayableEntity player) {
        if (standDownInBossAct()) return;
        switch (routine) {
            case 0 -> { init(); skipDespawnThisFrame = true; }   // BridgeObject: jsr routine; jmp DrawObject
            case 2 -> main(player);
            default -> { }
        }
    }

    /** BridgeInit (objects/r5/bridge.asm:37-90). */
    private void init() {
        routine += 2;
        priority = 3;
        tileWord = QqzObjectArt.BRIDGE_TILE;
        width = 0x40;
        yRadius = 8;
        mapFrame = 1;
        initialised = true;
        var objects = services().objectManager();
        if (objects == null) return;
        int count = 0;
        for (int d1 = 7; d1 >= 0; d1--) {
            ObjectSpawn pieceSpawn = new ObjectSpawn((x + X_OFFSETS[d1]) & 0xFFFF, y, 0x2F, 0, 0, false, 0);
            ObjBridgePiece piece = new ObjBridgePiece(pieceSpawn, this, d1);
            objects.addDynamicObject(piece);                 // jsr SpawnObject
            if (piece.isDestroyed()) continue;               // bne.w .NextPiece
            pieces[count++] = piece;
        }
    }

    /** BridgeMain (objects/r5/bridge.asm:114-146). */
    private void main(PlayableEntity player) {
        bendX = 0;
        if (player != null && checkPlayer(player)) setBendX(player);
        if (bendX != 0) {
            if (sinkAngle != 0x40) sinkAngle += 4;
            bend();
            if (bendX != 0) movePlayer(player);
        } else if (sinkAngle != 0) {
            sinkAngle -= 4;
            bend();
        }
    }

    /** BridgeSetBendX (objects/r5/bridge.asm:152-182). */
    private void setBendX(PlayableEntity player) {
        if (player.getAir()) {                               // btst #1,obj.flags(a1)
            bendX = 0;
            return;
        }
        int d0 = (centreX(player) - x + 0x48) & 0xFFFF;
        if ((short) d0 < 0 || d0 >= 0x80) {
            bendX = 0;
            return;
        }
        bendX = (d0 >> 4) & 0xFF;                            // lsr.w #4,d0
    }

    /** BridgeMovePlayer (objects/r5/bridge.asm:184-204). */
    private void movePlayer(PlayableEntity player) {
        ObjBridgePiece piece = pieces[bendX & 7];
        if (piece == null || player == null) return;
        NativePositionOps.writeYPosPreserveSubpixel(player, piece.getY() - 8 - player.getYRadius());
    }

    /**
     * BridgeBend (objects/r5/bridge.asm:206-286): the pieces on the player's side of {@code bendX} take the row of
     * {@code BridgeBendYOffsets} at index {@code bendX} forward, the rest the row {@code 7 - bendX} backward.
     */
    private void bend() {
        CdObjectArtProvider provider = artProvider();
        if (provider == null) return;
        int offsets = QqzObjectCatalogue.offset(provider.payload(), "bridgeBendYOffsets");
        int strengths = QqzObjectCatalogue.offset(provider.payload(), "bridgeBendStrengths");
        if (offsets < 0 || strengths < 0) return;
        var image = provider.image();
        int sink = TrigLookupTable.sinHex(sinkAngle);        // jsr SineCosine; move.w d0,d4
        int strength = image.u8(strengths + 0x80 + bendX);   // move.b (a5,d3.w),d5 with d3 = bendX + $80
        int row = offsets + ((bendX & 0xF) << 4);
        int piece = 0;
        for (int i = 0; i <= bendX; i++) {                   // .BendRight: d2 + 1 pieces
            setPieceY(piece++, ((image.u8(row + i) + 1) * strength * sink) >>> 16);
        }
        int remaining = 7 - bendX;                           // addq.b #1; sub.b #8; neg.b d3
        if (remaining <= 0) return;
        int back = offsets + (remaining << 4) + remaining;   // lea (a4,d3.w),a3; adda.w d2,a3
        for (int i = 0; i < remaining; i++) {                // .BendLeft
            back--;
            setPieceY(piece++, ((image.u8(back) + 1) * strength * sink) >>> 16);
        }
    }

    private void setPieceY(int index, int offset) {
        ObjBridgePiece piece = index < pieces.length ? pieces[index] : null;
        if (piece != null) piece.setBend(offset);
    }

    /**
     * BridgeCheckPlayer (objects/r5/bridge.asm:334-396): true when the player is stood on the bridge; the player's Y
     * is aligned and StandOnObject applied. Otherwise a player this bridge was holding is let go.
     */
    private boolean checkPlayer(PlayableEntity player) {
        if (!player.getDead() && !player.isDebugMode() && player.getYSpeed() >= 0) {
            int d0 = (centreX(player) - x + 0x48) & 0xFFFF;
            if ((short) d0 >= 0 && d0 < 0x80) {
                int top = (y - 8) & 0xFFFF;
                int feet = (centreY(player) + player.getYRadius() + 4) & 0xFFFF;
                int diff = (top - feet) & 0xFFFF;
                boolean higher = top > feet;                 // bhi after sub.w d1,d0
                if (!higher && diff >= 0xFFF0) {             // cmpi.w #-$10,d0; bcs
                    NativePositionOps.writeYPosPreserveSubpixel(player, centreY(player) + (short) diff + 3);
                    standOn(player);
                    return true;
                }
            }
        }
        if (standingPlayer) {                                // oPlayerStandObj is this bridge
            standingPlayer = false;
            player.setOnObject(false);                       // bclr #3,obj.flags(a1)
        }
        return false;
    }

    /** StandOnObject through {@link CdStandOnObject}; {@link #standingPlayer} is the bridge's "stood on" bit. */
    private void standOn(PlayableEntity player) {
        if (player instanceof AbstractPlayableSprite sprite) {
            CdStandOnObject.apply(sprite, standingPlayer, standingPlayer && sprite.isOnObject());
        }
        standingPlayer = true;
    }
}
