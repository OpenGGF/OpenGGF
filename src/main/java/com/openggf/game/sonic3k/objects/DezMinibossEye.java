package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.level.objects.*;

/** loc_7E768: collision owner for both eight-hit phases of the DEZ miniboss. */
final class DezMinibossEye extends DezMinibossSprite
        implements RewindRecreatable, TouchResponseProvider, TouchResponseAttackable {
    private DezMinibossSprite parent;
    private int routine;
    private int collision;
    private int savedCollision;
    private int flashTimer;
    private boolean touchPublished;
    private boolean debris;

    private DezMinibossEye(ObjectSpawn spawn) { super(spawn,"DEZMinibossEye"); }

    DezMinibossEye(DezMinibossSprite parent) {
        this(new ObjectSpawn(parent.getX(),(parent.getY()-4)&0xFFFF,0,0,0,false,0));
        this.parent=parent;
    }

    @Override public DezMinibossEye recreateForRewind(RewindRecreateContext context) {
        return new DezMinibossEye(context.spawn());
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        touchPublished=false;
        visible=false;
        if(pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        if(debris) { updateDebris(); return; }
        if(parent==null) return;
        if(routine==0) {
            routine=2; priority=4; halfWidth=halfHeight=0xC; frame=2;
            collision=0x17; collisionProperty=0xFF;
            // SetUp_ObjAttributes3 preserves d0=0 from the routine dispatch.
            parent.word44=0;
        }
        if(routine==2) {
            trackPrimary();
            if(parent.codePointer==0x7DE6E) routine=4;
        }
        switch(routine) {
            case 4 -> {
                trackPrimary(); publishHit();
                if((parent.status&0x40)!=0) routine=6;
                else touchPublished=true;
            }
            case 6 -> {
                trackPrimary();
                if((parent.status&0x40)==0) {
                    routine=8; collisionProperty=0xFF; collision=0x17; flashTimer=0;
                }
            }
            case 8 -> { trackPrimary(); publishHit(); touchPublished=true; }
            default -> { }
        }
        visible=true;
        // Child_Draw_Sprite_FlickerMove runs after the eye's routine. The fatal
        // parent's pass therefore still permits tracking/flash before conversion.
        if((parent.status&0x80)!=0) {
            status|=0x80; debris=true; collision=0; touchPublished=false;
            // Set_IndexedVelocity d0=$14, subtype=0; art-facing flip negates X.
            xVelocity=(short)romWord(0x852F4+0x14);
            yVelocity=(short)romWord(0x852F4+0x16);
            if(flipX) xVelocity=(short)-xVelocity;
        }
        updateDynamicSpawn(getX(),getY());
    }

    private void trackPrimary() {
        var player=services().playerQuery().mainPlayerOrNull();
        int dx=player==null?0:(short)(player.getCentreX()-parent.getX());
        flipX=dx<0;
        int index=(Math.min(Math.abs(dx),0xA0)>>>3)&0xFE;
        int offset=romByte(0x7EDBA+index);
        writeX(parent.getX()+(flipX?-offset:offset)); writeY(parent.getY());
        frame=romByte(0x7EDBB+index);
    }

    private void publishHit() {
        if(collision!=0) return;
        if(flashTimer==0) {
            flashTimer=0x20;
            services().playSfx(Sonic3kSfx.BOSS_HIT.id);
            parent.collisionProperty=(parent.collisionProperty+1)&0xFF;
        }
        DezMinibossPaletteState.flash(services(),flashTimer);
        flashTimer=(flashTimer-1)&0xFF;
        if(flashTimer==0) collision=savedCollision;
    }

    private void updateDebris() {
        move(0x38);
        if(isCoarseXOutOfRange(getX(),cameraLeft(),coarseXCullRange())
                || ((getY()-cameraTop()+0x80)&0xFFFF)>0x200) {
            pendingDelete=true; return;
        }
        // Obj_FlickerMove bchg tests the OLD bit: its first pass is hidden.
        visible=(control&0x40)!=0;
        control^=0x40;
        updateDynamicSpawn(getX(),getY());
    }

    @Override public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if(!touchPublished || collision==0) return;
        savedCollision=collision; collision=0;
        collisionProperty=(collisionProperty-1)&0xFF;
    }
    @Override public com.openggf.level.objects.TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
        return com.openggf.level.objects.TouchResponseProfile.fromProvider(this, multiRegionSource);
    }
    @Override public int getCollisionFlags() { return collision; }
    @Override public boolean publishesTouchResponseListEntryThisFrame() { return touchPublished; }
    @Override public int getCollisionProperty() { return collisionProperty; }
    @Override public boolean requiresContinuousTouchCallbacks() { return true; }
    DezMinibossSprite parentForTest() { return parent; }
}
