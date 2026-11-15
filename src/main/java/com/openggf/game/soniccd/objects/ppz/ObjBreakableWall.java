package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * The breakable wall, object $36 ({@code ObjBreakableWall}, Palmtree Panic/Objects/Breakable Wall/Main.asm:7-161):
 * a solid 32 by 48 wall (frame = subtype, tile $44BE, priority 1) with special collision $EF, which a
 * rolling player sets ({@code Player_TouchSpecial .CheckIfRoll}, Player Object Collision.asm:302, 319-320, 389-394).
 * A hit clears the collision, plays {@code FM_B0}, halves the player's X speed and breaks the wall into six 16 by
 * 16 pieces: the wall itself becomes piece 0 and five more come from {@code FindObjSlot} (an allocation failure
 * ends the loop and the pieces made so far fall). Pieces start from the wall's position moved by piece 0's offset,
 * not from its own (the loop copies {@code oX(a0)}/{@code oY(a0)} after the first iteration has moved them), so
 * the offsets are relative to piece 0. Each piece has its own X and Y speed (16.16, X negated when the player's
 * (halved) X speed is negative), gains $4000 of Y speed a frame, and is deleted once it is $E0 or more below the
 * player (ObjBreakableWall_Fall, Main.asm:108-124). The tables are the stage's own (catalogue columns {@code breakableWall*}).
 *
 * <p>Init has no {@code rts} and runs the main routine in the same frame (ObjBreakableWall_Init, Main.asm:21-32); the main routine
 * calls {@code R43LegacySolidObject}, which is plain {@code SolidObject} in these stages
 * ({@code R43_LEGACY_DATA_PREFIX} is 0, Level/_Objects/Level End/Main.asm:35-47), then draws and runs
 * CheckObjDespawn; the fall routine draws only.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjBreakableWall extends CdObject implements ReevaluatingSolidObject, TouchResponseProvider,
        TouchResponseListener, ObjectVisualFacing.NonDirectional {
    /** move.b #$EF,oColType(a0) (ObjBreakableWall_Init, Main.asm:28). */
    private static final int COLLISION_FLAGS = 0xEF;
    /** {@code move.w #$44BE,oTile(a0)} (ObjBreakableWall_Init, Main.asm:29). */
    private static final int TILE = 0x44BE;
    /** {@code addi.l #$4000,oVar2E(a0)} (ObjBreakableWall_Fall, Main.asm:109). */
    private static final int GRAVITY = 0x4000;
    /** {@code cmpi.w #-$E0,d0; ble.s .Destroy} (ObjBreakableWall_Fall, Main.asm:117-118). */
    private static final int DESTROY_BELOW_PLAYER = -0xE0;
    private static final int INIT = 0, MAIN = 2, FALL = 4;
    private static final int PIECES = 6;

    private int routine = INIT;
    private int collisionFlags;
    private boolean touched;
    private int mapFrame;
    private int xSub;
    private int ySub;
    /** oVar2A and oVar2E: the piece's X and Y speed, 16.16 (ObjBreakableWall_Main .InitLoop, scddisasm/src/Level/Palmtree Panic/Objects/Breakable Wall/Main.asm:95-102; ObjBreakableWall_Fall 110-113). */
    private int xSpeed;
    private int ySpeed;

    public ObjBreakableWall(ObjectSpawn spawn) {
        super(spawn, "Breakable wall");
    }

    private ObjBreakableWall(int x, int y, int mapFrame, int xSpeed, int ySpeed) {
        super(new ObjectSpawn(x, y, 0x36, 0, 0, false, 0), "Breakable wall piece");
        this.routine = FALL;                                    // move.b oRoutine(a0),oRoutine(a1)
        this.priority = 1;
        this.tileWord = TILE;
        this.width = 8;
        this.yRadius = 8;
        this.mapFrame = mapFrame;
        this.xSpeed = xSpeed;
        this.ySpeed = ySpeed;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return routine == MAIN; }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        return routine == MAIN && super.isCustomOutOfRange(cameraX);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case INIT -> {
                if (PpzStage.standDown(services(), this)) return;
                init();
                main();                                         // ObjBreakableWall_Init falls into _Main
            }
            case MAIN -> main();
            case FALL -> fall();
            default -> { }
        }
    }

    /** ObjBreakableWall_Init (scddisasm/src/Level/Palmtree Panic/Objects/Breakable Wall/Main.asm:21-32). */
    private void init() {
        routine = MAIN;
        collisionFlags = COLLISION_FLAGS;
        priority = 1;
        width = 0x10;
        yRadius = 0x18;
        tileWord = TILE;
        mapFrame = spawn.subtype();                             // move.b oSubtype(a0),oMapFrame(a0)
    }

    /** ObjBreakableWall_Main (scddisasm/src/Level/Palmtree Panic/Objects/Breakable Wall/Main.asm:36-52). */
    private void main() {
        if (!touched) {                                         // tst.b oColStatus(a0); beq.s .Solid
            solidObject(Entry.SOLID);
            return;
        }
        touched = false;
        collisionFlags = 0;                                     // clr.w oColType(a0): type and status
        routine = FALL;                                         // addq.b #2,oRoutine(a0)
        breakUp();
        fall();                                                 // the loop's last iteration, or a failed FindObjSlot, lands here
    }

    /** ObjBreakableWall_Main .BreakUp (scddisasm/src/Level/Palmtree Panic/Objects/Breakable Wall/Main.asm:56-103). */
    private void breakUp() {
        services().playSfx(PpzSounds.FM_B0);
        AbstractPlayableSprite player = services().camera().getFocusedSprite() instanceof AbstractPlayableSprite sprite
                ? sprite : null;
        if (player != null) player.setXSpeed((short) (player.getXSpeed() >> 1));   // asr oXVel(a6)
        CdObjectArtAccess access = access();
        if (access == null) return;
        boolean flip = player != null && player.getXSpeed() < 0;                  // tst.w oXVel(a6); bpl.s .NoFlip
        int frames = access.frames + spawn.subtype() * 8;                         // lsl.w #3,d0; adda.w d0,a5
        var objects = services().objectManager();
        // Piece 0 is this object: radii 8, frame, then X and Y moved by the first delta (.InitLoop).
        int baseX = x;
        int baseY = y;
        for (int i = 0; i < PIECES; i++) {
            int dx = (short) access.image.u16(access.deltas + i * 4);
            int dy = (short) access.image.u16(access.deltas + i * 4 + 2);
            int speedX = access.image.u32(access.speeds + i * 8);
            int speedY = access.image.u32(access.speeds + i * 8 + 4);
            if (flip) speedX = -speedX;                                           // neg.l d0
            int frame = access.image.u8(frames + i);
            if (i == 0) {
                width = 8;
                yRadius = 8;
                mapFrame = frame;
                x = (x + dx) & 0xFFFF;
                y = (y + dy) & 0xFFFF;
                xSpeed = speedX;
                ySpeed = speedY;
                baseX = x;
                baseY = y;
                continue;
            }
            // .Loop copies oX/oY from piece 0 (already moved), then adds this piece's delta.
            ObjBreakableWall piece = new ObjBreakableWall((baseX + dx) & 0xFFFF, (baseY + dy) & 0xFFFF, frame,
                    speedX, speedY);
            objects.addDynamicObject(piece);
            if (piece.getSlotIndex() < 0) return;                                 // jsr FindObjSlot; bne.s ObjBreakableWall_Fall
        }
    }

    /** ObjBreakableWall_Fall (scddisasm/src/Level/Palmtree Panic/Objects/Breakable Wall/Main.asm:108-124). */
    private void fall() {
        ySpeed += GRAVITY;
        int px = ((x & 0xFFFF) << 16 | xSub & 0xFFFF) + xSpeed;
        x = px >>> 16;
        xSub = px & 0xFFFF;
        int py = ((y & 0xFFFF) << 16 | ySub & 0xFFFF) + ySpeed;
        y = py >>> 16;
        ySub = py & 0xFFFF;
        var player = services().camera().getFocusedSprite();
        int d0 = (short) ((player == null ? 0 : player.getCentreY()) - y);
        if (d0 <= DESTROY_BELOW_PLAYER) setDestroyed(true);                      // ble.s .Destroy
    }

    private record CdObjectArtAccess(CdStageImage image, int frames, int deltas, int speeds) { }
    private CdObjectArtAccess access() {
        var provider = artProvider();
        if (provider == null) return null;
        var entry = PpzObjectCatalogue.entry(provider.payload());
        if (entry.breakableWallFrames() < 0) return null;
        return new CdObjectArtAccess(provider.image(), entry.breakableWallFrames(), entry.breakableWallDeltas(),
                entry.breakableWallSpeeds());
    }

    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        // .CheckIfRoll: cmpi.b #2,oAnim(a0); addq.b #1,oColStatus(a1) (Player Object Collision.asm:389-394).
        if (collisionFlags != 0 && player.getAnimationId() == com.openggf.game.soniccd.player.CdSonicAnimation.ROLL.id()) {
            touched = true;
        }
    }

    @Override public int getCollisionFlags() { return collisionFlags; }
    @Override public int getCollisionProperty() { return 0; }

    /**
     * Type {@code $EF} is a {@code $C0}-class special type: {@code Player_TouchSpecial} only adds to the collision status
     * (src/Level/Player Object Collision.asm .CheckIfRoll, :389-394), with no bounce or hurt, and the wall breaks on it.
     */
    @Override public boolean usesS3kTouchSpecialPropertyResponse() { return (getCollisionFlags() & 0xC0) == 0xC0; }
    @Override public TouchResponseProfile getTouchResponseProfile() { return TouchResponseProfile.fromProvider(this); }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multi) { return TouchResponseProfile.fromProvider(this, multi); }

    @Override public int solidWidth() { return width; }
    @Override public int solidYRadius() { return yRadius; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == INIT || PpzStage.bossAct(services())) return;
        var provider = artProvider();
        if (provider == null) return;
        String key = PpzObjectArt.key(provider, PpzObjectArt.PPZ_WALL, tileWord, services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, false, false);
    }

    public int mapFrame() { return mapFrame; }
    public boolean isPiece() { return routine == FALL; }
}
