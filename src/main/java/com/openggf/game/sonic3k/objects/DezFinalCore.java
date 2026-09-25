package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;
import com.openggf.level.objects.*;
import java.io.IOException;
import java.io.UncheckedIOException;

/** loc_804F0: eight-hit core, enabled by the mouth's _unkFAA9 publication. */
final class DezFinalCore extends DezFinalBossSprite
        implements RewindRecreatable, TouchResponseProvider, TouchResponseAttackable {
    private DezFinalBossSprite parent;
    private int routine;
    private int collision;
    private int savedCollision;
    private int flash;
    private int timer;
    private boolean hitByP2;
    private boolean touchPublished;

    private DezFinalCore(ObjectSpawn spawn) { super(spawn,"DEZFinalCore"); }
    DezFinalCore(DezFinalBossSprite parent) {
        this(new ObjectSpawn(parent.getX(),parent.getY(),0,0,0,false,0)); this.parent=parent;
    }
    @Override public DezFinalCore recreateForRewind(RewindRecreateContext context) { return new DezFinalCore(context.spawn()); }
    @Override public void update(int vIntRunCount,PlayableEntity player) {
        visible=touchPublished=false;
        if(pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        if(parent==null) return;
        if(routine==0) {
            routine=1; priority=5; halfWidth=4; halfHeight=0x20; frame=0x18;
            highPriority=false; collisionProperty=8;
        }
        if(routine==5) {
            runtime().laserOffset(0); track();
            // loc_80584 uses Child_Draw_Sprite2: root control bit 4, not status bit 7.
            if((parent.control&0x10)!=0) { pendingDelete=true; control|=0x10; parent=null; }
            else visible=true;
            updateDynamicSpawn(getX(),getY());
            return;
        }
        int mouth=runtime().mouthStatus();
        if(routine==1) {
            if(mouth==0) { serviceDamage(false); return; }
            routine=2;
        }
        if(routine==2 && (mouth&0x80)!=0) { routine=3; collision=0x16; }
        if(routine==3 && (mouth&0x80)==0) routine=4;
        if(routine==4 && mouth==0) { routine=1; return; }
        if(routine==3) {
            track(); serviceDamage(true); touchPublished=true;
        } else if(routine==4) {
            // loc_80562 tracks before damage; loc_80522 opening does the reverse.
            track(); serviceDamage(false);
        } else {
            serviceDamage(false); track();
        }
        visible=true; updateDynamicSpawn(getX(),getY());
    }
    private void track() { writeX(parent.getX()+0x6C); writeY(parent.getY()+8); }
    private DezFinalBossZoneRuntimeState runtime() { return (DezFinalBossZoneRuntimeState)services().zoneRuntimeState(); }
    private void serviceDamage(boolean vulnerable) {
        // sub_8119A returns before servicing an existing flash if collision_flags is live.
        if(vulnerable && collision!=0) return;
        if(collisionProperty==0) { defeat(); return; }
        if(vulnerable && flash==0) {
            flash=0x20; services().playSfx(Sonic3kSfx.BOSS_HIT.id);
            var player=hitByP2?services().playerQuery().nativeP2OrNull():services().playerQuery().mainPlayerOrNull();
            if(player!=null) {
                player.setGSpeed((short)0x600); player.setXSpeed((short)0x600); player.setYSpeed((short)-0x300);
            }
        }
        if(flash==0) return;
        if(vulnerable) status|=0x40;
        try {
            // CopyWordData_5: word_8123E points at line 1 colours $A..$E.
            S3kPaletteWriteSupport.applyContiguousPatch(services().paletteOwnershipRegistryOrNull(),
                    services().currentLevel(),services().graphicsManager(),S3kPaletteOwners.DEZ_FINAL_BOSS,
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,1,0xA,
                    services().rom().readBytes(0x81248+((flash&1)==0?10:0),10));
        } catch(IOException failure) { throw new UncheckedIOException(failure); }
        if(--flash==0) {
            status&=~0x40;
            // sub_8125C outside the vulnerable phase does not restore collision_flags.
            if(vulnerable) collision=savedCollision;
        }
    }
    private void defeat() {
        routine=5;
        spawnChild(()->new DezMinibossExplosionController(getX(),getY(),4));
        parent.status|=0x80;
        // BossDefeated_StopTimer falls through BossDefeated ($3F wait, 1000 points).
        timer=0x3F; services().levelGamestate().pauseTimer(); services().gameState().addScore(1000);
    }
    @Override public void onPlayerAttack(PlayableEntity player,TouchResponseResult result) {
        if(!touchPublished || collision==0 || collisionProperty==0) return;
        savedCollision=collision; collision=0; collisionProperty--;
        hitByP2=player!=null && player==services().playerQuery().nativeP2OrNull();
    }
    @Override public int getCollisionFlags() { return touchPublished?collision:0; }
    @Override public boolean publishesTouchResponseListEntryThisFrame() { return touchPublished; }
    @Override public int getCollisionProperty() { return collisionProperty; }
    @Override public boolean requiresContinuousTouchCallbacks() { return true; }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) { return TouchResponseProfile.fromProvider(this,multiRegionSource); }
}
