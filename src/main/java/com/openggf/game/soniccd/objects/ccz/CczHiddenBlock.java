package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Collision Chaos' hidden block, object $22 ({@code ObjHiddenBlock}, Level/Collision Chaos/Objects/Block/Main.asm:215-402,
 * legacy ABI). A solid $20 by $1C block (tile {@code $39F}, priority 3, frame {@code subtype >> 2}) that slides along Y,
 * down or (subtype bit 2) up, by an offset of 0 to $20 that grows 8 a frame while the player is left of its left edge and
 * shrinks 8 a frame while he is $50 or more to the right of it. A subtype whose low bits are 1 carries spikes on its side
 * ({@link CczSpikes}, subtype 2, $20 to the right); 2 and 3 a sideways spring ({@link com.openggf.game.soniccd.objects.common.CdSpring#attach}).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class CczHiddenBlock extends CczObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    static final int TILE = 0x039F;

    private int baseX;               // oVar36
    private int baseY;               // oVar32
    private int offset;              // oVar3B

    public CczHiddenBlock(ObjectSpawn spawn) {
        super(spawn, "Hidden block");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) init();
        move(player);                                                 // ObjHiddenBlock_Update (:297-310)
        solidObject(Entry.SOLID);
    }

    /** ObjHiddenBlock_Init (:255-293); falls into ObjHiddenBlock_Update. */
    private void init() {
        routine = 2;
        tileWord = TILE;
        priority = 3;
        mapFrame = (spawn.subtype() & 0xFF) >> 2;                     // lsr.b #2,d0
        width = 0x10;
        yRadius = 0xE;
        baseX = x;
        baseY = y;
        int low = spawn.subtype() & 3;
        if (low == 0) return;
        var objects = services().objectManager();
        if (objects == null) return;
        if (low != 1) {
            // ID $A, X offset $18, subtype bit 2 (sideways) plus bit 1 for the colour from subtype bit 0 (Block/Main.asm:286-293).
            ObjectSpawn side = new ObjectSpawn(x, y, 0x0A, ((spawn.subtype() & 1) << 1) | 4, 0, false, 0);
            objects.addDynamicObjectAfterCurrent(new com.openggf.game.soniccd.objects.common.CdSpring(side).attach(this, 0x18, 0));
            return;
        }
        ObjectSpawn spikes = new ObjectSpawn(x, y, 0x20, 2, 0, false, 0);
        objects.addDynamicObjectAfterCurrent(new CczSpikes(spikes, this, 0x20, 0));   // oVar38 = $20; subtype 2
    }

    /** ObjHiddenBlock_Move (:314-347). */
    private void move(PlayableEntity player) {
        checkPlayer(player);
        int d0 = offset;
        if ((spawn.subtype() & 4) != 0) d0 = -d0;                     // btst #2,oSubtype(a0); .Negative
        y = (d0 + baseY) & 0xFFFF;
    }

    /** ObjHiddenBlock_CheckPlayer (:351-402, legacy). */
    private void checkPlayer(PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite sprite)) return;
        int left = (x - (byte) width) & 0xFFFF;                       // move.w oX,d0; move.b oWidth,d1; ext.w; sub.w d1,d0
        int d1 = (sprite.getCentreX() & 0xFFFF) - left;
        if (d1 < 0) {                                                 // bcs.s .MoveRight
            offset = (offset + 8) & 0xFF;
            if (offset >= 0x20) offset = 0x20;                        // cmpi.b #$20; bcs.s; move.b #$20
            return;
        }
        if (d1 < 0x50) return;                                        // cmpi.w #$50,d1; bcc.s .MoveLeft
        offset = (offset - 8) & 0xFF;                                 // subq.b #8; bcc.s .ClampEnd
        if (offset >= 0xF8) offset = 0;                               // borrow: move.b #0
    }

    @Override public int solidWidth() { return width; }
    @Override public int solidYRadius() { return yRadius; }

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
        String key = CczObjectArt.key(provider, CczObjectArt.HIDDEN_BLOCK, tileWord, services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, false, false);
    }

    public int offset() { return offset; }
}
