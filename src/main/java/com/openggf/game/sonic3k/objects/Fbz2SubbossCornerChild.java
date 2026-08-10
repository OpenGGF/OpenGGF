package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import java.util.List;

/** Four members of ChildObjDat_703C8, loc_6FF5C. */
final class Fbz2SubbossCornerChild extends AbstractFbz2SubbossChild
        implements RewindRecreatable, RomWorldPositionedObject {
    private static final int[] SUBTYPES = {0, 2, 4, 6};
    private static final int[][] OFFSETS = {{-0xB0,0x18},{0xB0,0x18},{-0xB0,0xA8},{0xB0,0xA8}};
    private int nativeSubtype;
    private int moveTimer = -1;
    private boolean detached;

    Fbz2SubbossCornerChild(Fbz2SubbossInstance root, int subtype) {
        this(spawn(root, subtype));
        this.root = root; familySlot = root.getSlotIndex();
    }
    private Fbz2SubbossCornerChild(ObjectSpawn spawn) {
        super(spawn, "FBZ2SubbossCorner"); nativeSubtype = spawn.subtype() & 6;
    }
    private static ObjectSpawn spawn(Fbz2SubbossInstance root, int subtype) {
        int[] d = OFFSETS[(subtype & 6) >> 1];
        return new ObjectSpawn(root.getX()+d[0], root.getY()+d[1], 0xAB, subtype, 0, false, 0);
    }
    static Fbz2SubbossCornerChild forTest(Fbz2SubbossInstance root, int subtype) { return new Fbz2SubbossCornerChild(root, subtype); }
    static int[] nativeSubtypes() { return SUBTYPES.clone(); }
    int nativeSubtype() { return nativeSubtype; }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (root != null && root.statusBit(Fbz2SubbossInstance.STATUS_CHARACTER_DEFEAT)) detached = true;
        // loc_6FF70 skips subtypes whose bit 1 is set: only native roles 0/4 move.
        boolean enteredMoveState = false;
        if (!detached && root != null && (nativeSubtype & 2) == 0
                && root.controlBit(Fbz2SubbossInstance.CONTROL_MOVE_RIGHT) && moveTimer < 0) {
            // loc_6FF70 installs loc_6FF90/$1F/$0100 and returns through Draw_Sprite.
            // MoveSprite2 is first reached on this child's following object pass.
            moveTimer = 0x1F;
            enteredMoveState = true;
        }
        if (moveTimer >= 0 && !enteredMoveState) {
            x += 1;
            if (--moveTimer < 0 && root != null) root.clearControlBit(Fbz2SubbossInstance.CONTROL_MOVE_RIGHT);
        }
        if (detached && tryServices() != null && !isOnScreen()) ObjectLifetimeOps.expireDynamic(this);
    }
    @Override public Fbz2SubbossCornerChild recreateForRewind(RewindRecreateContext ctx) { return new Fbz2SubbossCornerChild(ctx.spawn()); }
    @Override public int getPriorityBucket() { return 1; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer r=getRenderer(Sonic3kObjectArtKeys.FBZ2_SUBBOSS);
        if(r!=null&&r.isReady())r.drawFrameIndex(1,x,y,false,false);
    }
}
