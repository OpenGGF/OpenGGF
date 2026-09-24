package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.*;
import com.openggf.physics.TrigLookupTable;

/** loc_7E80E orbiters and their independent loc_7E916 eight-way fragments. */
final class DezMinibossOrb extends DezMinibossSprite implements RewindRecreatable,TouchResponseProvider {
    private DezMinibossSprite parent;
    private boolean initialized;
    private boolean fragment;
    private boolean launched;
    private boolean touchPublished;
    private int angle;
    private int collision;

    private DezMinibossOrb(ObjectSpawn spawn) { super(spawn,"DEZMinibossOrb"); }
    DezMinibossOrb(DezMinibossSprite parent,int subtype,boolean fragment) {
        this(new ObjectSpawn(parent.getX(),parent.getY(),0,subtype,0,false,0));
        // loc_7E916/loc_7E972 fragments copy position/velocity and never read a parent.
        // Keep no Java reference to the orb that loc_7E908 deletes: it must not
        // become a live rewind dependency after the native slot has disappeared.
        this.parent=fragment?null:parent; this.fragment=fragment;
    }
    @Override public DezMinibossOrb recreateForRewind(RewindRecreateContext context) {
        return new DezMinibossOrb(context.spawn());
    }
    @Override public void update(int vIntRunCount,PlayableEntity player) {
        visible=false; touchPublished=false;
        if(pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        if(!initialized) {
            initialized=true;
            if(fragment) {
                priority=2; halfWidth=halfHeight=4; collision=0x98;
                int subtype=spawn.subtype(); frame=romByte(0x7E942+subtype);
                int flags=romByte(0x7E943+subtype); flipX=(flags&1)!=0; flipY=(flags&2)!=0;
                xVelocity=(short)romWord(0x7E952+subtype*2);
                yVelocity=(short)romWord(0x7E954+subtype*2);
                return; // loc_7E916 does not draw or move on its init pass
            }
            priority=3; halfWidth=halfHeight=0x10; frame=0x15;
            angle=romByte(0x7ED64+(spawn.subtype()>>>1));
        }
        if(fragment) {
            move(0);
            if(isCoarseXOutOfRange(getX(),cameraLeft(),coarseXCullRange())
                    || ((getY()-cameraTop()+0x80)&0xFFFF)>0x200) {
                status|=0x80; pendingDelete=true; return;
            }
            drawTouch(); return;
        }
        if(parent==null) return;
        collision=(parent.status&0x40)!=0?0:0x86;
        if(launched) {
            move(0x38);
            if(yVelocity<0) { drawTouch(); return; }
            // Independent tables: fragments are attempted even when the controller fails.
            // Subtype 6 selects the finite, stationary explosion routine; the ROM
            // never follows its creator after setup (unlike the $08 follower).
            spawnChild(()->new DezMinibossExplosionController(getX(),getY(),6));
            for(int i=0;i<8;i++) {
                int subtype=i*2;
                var child=spawnChild(()->new DezMinibossOrb(this,subtype,true));
                if(child==null || child.isDestroyed()) break;
            }
            status|=0x80; pendingDelete=true; return;
        }
        angle=(angle+4)&0xFF;
        if((parent.control&2)!=0) {
            parent.control&=~2; launched=true; frame=0x15; highPriority=true; priority=3; yVelocity=-0x400;
            return; // loc_7E89C returns without Draw_Sprite
        }
        int dx=(TrigLookupTable.sinHex(angle)*(short)parent.word3A)>>16;
        writeX(parent.getX()+dx); writeY(parent.getY()+(byte)(parent.word3C>>>8));
        int index=((angle>=0x80?(~angle&0xFF):angle)>>>2)&0x1C;
        frame=romByte(0x7E8C2+index); highPriority=(romByte(0x7E8C3+index)&0x80)!=0;
        priority=romWord(0x7E8C4+index)/0x80;
        if(((dx+0x20)&0xFFFF)<0x40 && !highPriority) return;
        drawTouch();
    }
    private void drawTouch() { visible=true; touchPublished=true; updateDynamicSpawn(getX(),getY()); }
    DezMinibossSprite parentForTest() { return parent; }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
        return TouchResponseProfile.fromProvider(this,multiRegionSource);
    }
    @Override public int getCollisionFlags() { return collision; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public boolean publishesTouchResponseListEntryThisFrame() { return touchPublished; }
}
