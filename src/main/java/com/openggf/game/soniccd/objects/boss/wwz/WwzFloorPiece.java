package com.openggf.game.soniccd.objects.boss.wwz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * A floor piece, object $3D ({@code FloorPieceObject}, objects/r6/boss.asm:1104-1362): the floor Eggman broke, four
 * pieces per floor (BossSpawnSpikeWave, :1717-1749; subtype = piece, {@code subtype_2} = floor - 1). Each falls
 * (gravity $B) from the floor below Eggman to the motion table's landing Y and rests (FM $B4). While Eggman's
 * {@code var_36} is set the resting pieces jump: speed -$600 and gravity $B, or $E for a piece within $18 pixels of
 * the player, held at the table's top Y until they fall back.
 *
 * <p>Every frame the piece is solid for the player (with its Y radius 4 larger while it moves; the 4-smaller case is
 * unreachable, {@code FixBugs = 0}) and a rising piece carries a standing player with its speed, so the player leaves
 * through the broken floor above. A player near the ceiling (Y $108) over a piece is hurt and pushed down at $200, and
 * one under a falling piece within $20 pixels is hurt (FloorPieceCheckPlayerOverlap, :1314-1362).
 */
final class WwzFloorPiece extends WwzBossPiece implements ReevaluatingSolidObject {
    static final int ID = 0x3D;
    /** FloorPieceLand: move.w #$B4,d0; jsr PlayFmSound (objects/r6/boss.asm:1200-1210). */
    static final int FM_LAND = 0xB4;
    // var_2e: Eggman.
    WwzBossEggman eggman;
    /** subtype and subtype_2. */
    private int piece;
    private int row;
    /** var_38: the motion entry (0, or 8 near the player); var_3a: the player's saved Y speed; var_3c: the radius bump. */
    private int entry;
    private int savedYVel;
    private int bump;

    WwzFloorPiece(WwzBossEggman eggman, int px, int py, int piece, int row) {
        super(ID, px, py, "WwzFloorPiece");
        this.eggman = eggman;
        this.piece = piece;
        this.row = row;
        setXWord(px);
        setYWord(py);
    }

    private WwzFloorPiece(ObjectSpawn spawn) {
        super(spawn, "WwzFloorPiece");
    }

    private int motion(int offset) {
        var level = cdLevel();
        var image = stageImage();
        int table = WwzBossCatalogue.offset(level == null ? null : level.payload(), "floorPieceMotion");
        return image == null || table < 0 ? 0 : (short) image.u16(table + row * 0x10 + entry + offset);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        AbstractPlayableSprite sonic = sonic(player);
        switch (routine) {
            case 0 -> {                                      // FloorPieceInit (objects/r6/boss.asm:1144-1161)
                routine = 2;
                priority = 3;
                width = 0xE;
                yRadius = 0x10;
                tileWord = WwzBossArt.FLOOR_PIECE_TILE;
                yVel = 0;
                accelY = 0xB;
            }
            case 2 -> move();
            case 4 -> rest(sonic);
            default -> { }
        }
        adjustPlayerHeight(sonic);
    }

    /** FloorPieceAnimate / FloorPieceClampHeight / FloorPieceLand (objects/r6/boss.asm:1180-1210). */
    private void move() {
        integrateVelocity();
        int land = motion(0);
        if (land <= yWord()) {
            xVel = 0;
            yVel = 0;
            setYWord(land);
            routine = 4;
            fmSound(FM_LAND);
            return;
        }
        int top = motion(2);
        if (top >= yWord()) setYWord(top);
    }

    /** FloorPieceResting / FloorPieceStartMotion (objects/r6/boss.asm:1213-1243). */
    private void rest(AbstractPlayableSprite player) {
        if (eggman == null || eggman.floorPieceJump == 0) return;
        routine = 2;
        int dx = (short) (x - (player == null ? 0 : player.getCentreX()));
        if (dx < 0) dx = -dx;
        entry = dx < 0x18 ? 8 : 0;
        yVel = motion(4);
        accelY = motion(6);
    }

    /** FloorPieceAdjustPlayerHeight / FloorPieceApplyHeightOffset (objects/r6/boss.asm:1246-1274). */
    private void adjustPlayerHeight(AbstractPlayableSprite player) {
        bump = yVel == 0 ? 0 : 4;                            // bgt and the fall-through both reach FloorPieceAdjustPlayerUp
        yRadius = (yRadius + bump) & 0xFF;
        checkPlayer(player);
        yRadius = (yRadius - bump) & 0xFF;
    }

    /** FloorPieceCheckPlayerCollision / FloorPiecePushPlayer (objects/r6/boss.asm:1277-1309). */
    private void checkPlayer(AbstractPlayableSprite player) {
        if (player == null) return;
        if (overlap(player)) return;
        savedYVel = player.getYSpeed();
        if (player.isOnObject() && !player.getAir()) player.setYSpeed((short) 0);
        if (!solidObject(Entry.SOLID)) {
            player.setYSpeed((short) savedYVel);
            return;
        }
        // move.l obj.y(a0),obj.y(a1): the player takes the piece's Y and Y subpixel.
        NativePositionOps.writeYPosPreserveSubpixel(player, yWord());
        player.setSubpixelRaw(player.getXSubpixelRaw(), ySub & 0xFFFF);
        NativePositionOps.addYPosPreserveSubpixel(player, -((byte) player.getYRadius() + 0x10));
        player.setYSpeed((short) (yVel >= 0 ? 0 : yVel));
    }

    /** FloorPieceCheckPlayerOverlap (objects/r6/boss.asm:1314-1362): true (d0 = -1) skips the solid call. */
    private boolean overlap(AbstractPlayableSprite player) {
        int dx = (short) (x - player.getCentreX());
        if (dx < 0) dx = -dx;
        int py = (short) player.getCentreY();
        if (dx <= 0x16) {
            if (py <= 0x108) {                               // FloorPieceBouncePlayer
                hurtPlayer(player);
                player.setYSpeed((short) 0x200);
                return true;
            }
            if ((short) yVel > 0) {
                int gap = (short) (py - (byte) player.getYRadius() - yWord());
                if (gap >= 0 && gap <= 0x20) {
                    hurtPlayer(player);                      // bra.s BossHurtPlayer
                    return true;
                }
            }
        }
        return py >= yWord();                                // FloorPieceComparePlayerY / FloorPiecePlayerAbove
    }

    @Override String sheet() { return WwzBossArt.FLOOR_PIECE; }

    @Override public int solidWidth() { return width; }

    @Override public int solidYRadius() { return yRadius; }

    @Override public boolean solidRenderedOnScreen() { return renderFlagOnScreen(); }

    @Override public int solidXVelocity() { return xVel; }

    @Override public int solidYVelocity() { return yVel; }

    @Override
    protected com.openggf.game.soniccd.objects.boss.CdBossPart shell(ObjectSpawn spawn) {
        return new WwzFloorPiece(spawn);
    }
}
