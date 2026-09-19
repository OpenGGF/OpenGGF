package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Folded eight-SST SKL {@code $4B}, {@code Obj_DEZTiltingBridge} (sonic3k.asm:92740-92882). */
public final class S3kDezTiltingBridgeObjectInstance extends AbstractObjectInstance
        implements MultiPieceSolidProvider, SpawnRewindRecreatable, RomObjectCodePointerProvider {
    private static final int[] FORCE = {
            0x70,0x50,0x30,0x10,-0x10,-0x30,-0x50,-0x70,
            0x54,0x3C,0x24,0x0C,-0x0C,-0x24,-0x3C,-0x54,
            0x38,0x28,0x18,0x08,-0x08,-0x18,-0x28,-0x38,
            0x1C,0x14,0x0C,0x04,-0x04,-0x0C,-0x14,-0x1C,
            -0x1C,-0x14,-0x0C,-0x04,0x04,0x0C,0x14,0x1C,
            -0x38,-0x28,-0x18,-0x08,0x08,0x18,0x28,0x38,
            -0x54,-0x3C,-0x24,-0x0C,0x0C,0x24,0x3C,0x54,
            -0x70,-0x50,-0x30,-0x10,0x10,0x30,0x50,0x70
    };
    private int baseX, baseY;
    private int[] yFixed = new int[8];
    private int[] velocity = new int[8];
    private int p1Piece, p2Piece;
    private boolean falling;
    private boolean fallingInitialized;
    private boolean slotsReserved;

    public S3kDezTiltingBridgeObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZTiltingBridge");
        baseX = spawn.x(); baseY = spawn.y();
        for (int i=0;i<8;i++) yFixed[i]=baseY<<16;
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        reserveSlots();
        if (falling) {
            for (int i = 0; i < 8; i++) {
                if (!fallingInitialized) velocity[i] *= 4;
                else velocity[i] += 0x1000;
                yFixed[i] += velocity[i];
            }
            fallingInitialized = true;
            return;
        }
        boolean startFalling = false;
        for (int i=0;i<8;i++) {
            velocity[i] += forceFor(p1Piece,i) + forceFor(p2Piece,i);
            yFixed[i] += velocity[i];
            if (Math.abs((yFixed[i]>>16)-baseY) >= 0x70) startFalling = true;
        }
        falling = startFalling;
    }

    private int forceFor(int standingPiece,int piece) {
        return standingPiece == 0 ? 0 : FORCE[(standingPiece-1)*8+piece]*2;
    }
    private void reserveSlots() {
        if (slotsReserved || getSlotIndex()<0) return;
        slotsReserved=true;
        if (tryServices()!=null && services().objectManager()!=null)
            services().objectManager().allocateChildSlotsAfter(spawn,7,getSlotIndex());
    }
    @Override public int getReservedChildSlotCount(){return 7;}
    @Override public int getPieceCount(){return 8;}
    @Override public int getPieceX(int i){return baseX-0x70+i*0x20;}
    @Override public int getPieceY(int i){return yFixed[i]>>16;}
    @Override public SolidObjectParams getSolidParams(){return SolidObjectParams.of(0x1B,0x10,0x11);}
    @Override public boolean usesPieceScopedStandingBits(){return true;}
    @Override public boolean resolvesEarlierPiecesBeforeRidingPiece(){return true;}
    @Override public void onPieceContact(int i,PlayableEntity p,SolidContact c,int f){
        int value=c.standing()?i+1:0;
        if(p.isCpuControlled()){if(value!=0||p2Piece==i+1)p2Piece=value;}
        else if(value!=0||p1Piece==i+1)p1Piece=value;
    }
    @Override public int getX(){return baseX;}
    @Override public int getY(){return baseY;}
    @Override public int getOnScreenHalfWidth(){return 0x80;}
    @Override public int getOnScreenHalfHeight(){return 0x80;}
    @Override public int getPriorityBucket(){return RenderPriority.fromS3kWord(0x280);}
    @Override public int romObjectCodePointerHighWord(){return 4;}
    @Override public void appendRenderCommands(List<GLCommand> commands){
        PatternSpriteRenderer r=getRenderer(Sonic3kObjectArtKeys.DEZ_TILTING_BRIDGE);
        if(r!=null&&r.isReady())for(int i=0;i<8;i++)r.drawFrameIndex(0,getPieceX(i),getPieceY(i),false,false);
    }
    int forceForTest(int standingPiece,int piece){return forceFor(standingPiece,piece);}
    void setStandingPieceForTest(int p1,int p2){p1Piece=p1;p2Piece=p2;}
    int velocityForTest(int piece){return velocity[piece];}
    boolean fallingForTest(){return falling;}
}
