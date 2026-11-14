package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Collision Chaos' stationary, attached and retracting spikes, object $20 ({@code ObjSpikes}, Level/Collision Chaos/Objects/
 * Spikes/Main.asm:18-228). A solid object (tile {@code $31E}, {@code $3FC} in act 3, priority 3) whose frame is
 * {@code subtype & 3}: 0 points up ($10 wide, 8 tall; it hurts a player standing on it), 1 down and 2, 3 to the sides
 * (touch type {@code $83} or {@code $82}). Subtype bit 2 makes it move: down, up, left or right by 0 to $20 pixels in steps
 * of 8, waiting $3C frames at each end (a sound when it first moves on screen). A spikes child follows its parent (hidden
 * block) at an offset. The despawn is the base X's chunk test, then {@code DespawnObjectR3}.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class CczSpikes extends CczObject implements ReevaluatingSolidObject, TouchResponseProvider,
        ObjectVisualFacing.NonDirectional {
    /** move.w #$B7,d0; jsr PlayFMSound (Spikes/Main.asm:207-208). */
    private static final int FM_B7 = 0xB7;

    private AbstractObjectInstance parent;   // oVar34
    private int parentXOffset;               // oVar38 (signed byte)
    private int parentYOffset;               // oVar39 (signed byte)
    private int baseX;                       // oVar36
    private int baseY;                       // oVar32
    private int moveDelay;                   // oVar3A
    private int moveOffset;                  // oVar3B
    private boolean retracting;              // oVar3C

    public CczSpikes(ObjectSpawn spawn) {
        super(spawn, "Spikes");
    }

    CczSpikes(ObjectSpawn spawn, AbstractObjectInstance parent, int xOffset, int yOffset) {
        super(spawn, "Spikes");
        this.parent = parent;
        this.parentXOffset = (byte) xOffset;
        this.parentYOffset = (byte) yOffset;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {
            if (!init()) return;
        }
        run(vIntRunCount, player);
        if (parent != null) {                                         // .CheckParent (:25-36), after DrawObject
            x = (parent.getX() + parentXOffset) & 0xFFFF;
            y = (parent.getY() + parentYOffset) & 0xFFFF;
        }
    }

    /** ObjSpikes_Init (:67-91); falls into ObjSpikes_Update. */
    private boolean init() {
        var provider = artProvider();
        if (provider == null) return false;
        routine = 2;
        tileWord = act() == 2 ? 0x3FC : 0x31E;                        // move.w #$31E,d0; cmpi.b #2,act; move.w #$3FC,d0
        priority = 3;
        baseX = x;
        baseY = y;
        int frame = spawn.subtype() & 3;
        mapFrame = frame;
        int properties = CczObjectCatalogue.offset(provider.payload(), "spikesProperties") + frame * 3;
        var image = provider.image();
        width = image.u8(properties);
        yRadius = image.u8(properties + 1);
        colType = image.u8(properties + 2);
        return true;
    }

    /** ObjSpikes_Update (:95-131). */
    private void run(int vIntRunCount, PlayableEntity player) {
        updateMovement();
        if (mapFrame != 0) {                                          // tst.b oMapFrame; SolidObject only
            solidObject(Entry.SOLID);
            return;
        }
        if (!solidObject(Entry.SOLID) || !(player instanceof AbstractPlayableSprite sprite)) return;
        if (!stoodOn(sprite)) return;                                 // btst #3,oFlags(a0)
        if (sprite.getInvincibleFrames() > 0) return;                 // tst.b invincible (timeWarp is not hosted)
        if (sprite.isHurt() || sprite.getDead() || sprite.getInvulnerableFrames() > 0) return;   // routine >= 4; oPlayerHurt
        NativePositionOps.addYPos16_16(sprite, -(sprite.getYSpeed() << 8));      // oY.l -= oYVel << 8
        boolean hadRings = sprite.getRingCount() > 0;
        if (hadRings && !sprite.hasShield()) services().spawnLostRings(sprite, vIntRunCount);
        sprite.applyHurtOrDeath(x, true, hadRings);                   // jsr HurtPlayer
    }

    /** ObjSpikes_UpdateMovement and the four moves (:135-196). */
    private void updateMovement() {
        int subtype = spawn.subtype() & 0xFF;
        if ((subtype & 4) == 0) return;
        updateOffset();
        switch (subtype & 3) {
            case 0 -> y = (baseY + moveOffset) & 0xFFFF;              // MoveDown
            case 1 -> y = (baseY - moveOffset) & 0xFFFF;              // MoveUp
            case 2 -> x = (baseX - moveOffset) & 0xFFFF;              // MoveLeft
            default -> x = (baseX + moveOffset) & 0xFFFF;             // MoveRight
        }
    }

    /** ObjSpikes_UpdateOffset (:200-228). */
    private void updateOffset() {
        if (moveDelay != 0) {
            moveDelay--;
            if (moveDelay != 0) return;
            if (renderFlagOnScreen()) services().playSfx(FM_B7);      // btst #7,oSprFlags; beq.s .Move
        }
        if (retracting) {
            moveOffset = (moveOffset - 8) & 0xFF;
            if (moveOffset < 0xF8) return;                            // subq.b #8; bcc.s .End
            moveOffset = 0;
            retracting = false;
            moveDelay = 0x3C;
            return;
        }
        moveOffset = (moveOffset + 8) & 0xFF;
        if (moveOffset < 0x20) return;                                // cmpi.b #$20; bcs.s .End
        moveOffset = 0x20;
        retracting = true;
        moveDelay = 0x3C;
    }

    @Override public int solidWidth() { return width; }
    @Override public int solidYRadius() { return yRadius; }

    /** The orientation's touch type ({@code oColType}, :91). */
    @Override public int getCollisionFlags() { return routine == 0 ? 0 : colType; }
    @Override public int getCollisionProperty() { return 0; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return baseX; }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        return outsideChunkRange(baseX, cameraX);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        CdObjectArtProvider provider = provider();
        if (provider == null) return;
        String key = CczObjectArt.key(provider, CczObjectArt.SPIKES, tileWord, services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, false, false);
    }

    public int moveOffset() { return moveOffset; }
}
