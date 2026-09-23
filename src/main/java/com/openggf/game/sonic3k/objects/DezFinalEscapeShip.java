package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.runtime.DezFinalCamera;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.GameOverExit;
import com.openggf.game.save.SaveReason;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** loc_80160 through loc_80424: eight-hit escape ship and the final DEZ exit. */
final class DezFinalEscapeShip extends DezFinalBossSprite
        implements RewindRecreatable, TouchResponseProvider, TouchResponseAttackable {
    private int routine;
    private int collision;
    private int savedCollision;
    private int flash;
    private int timer;
    private int callback;
    private boolean hitByP2;
    private boolean touchPublished;
    private DdzWhiteFadeObjectInstance exitFade;

    DezFinalEscapeShip() { this(new ObjectSpawn(0,0,0,0,0,false,0)); }
    private DezFinalEscapeShip(ObjectSpawn spawn) { super(spawn,"DEZFinalEscapeShip"); codePointer=0x80160; }
    @Override public DezFinalEscapeShip recreateForRewind(RewindRecreateContext context) { return new DezFinalEscapeShip(context.spawn()); }
    private DezFinalBossZoneRuntimeState state() { return (DezFinalBossZoneRuntimeState)services().zoneRuntimeState(); }
    private int cameraX() { return DezFinalCamera.nativeX(services().camera()); }
    @Override public void update(int clock,PlayableEntity player) {
        visible=touchPublished=false;
        if(pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        state().art().service(services());
        switch(codePointer) {
            case 0x80160 -> { updateLive(clock); damage(); visible=touchPublished=true; }
            case 0x8565E -> { waitCallback(); visible=true; } // Wait_Draw, no movement/damage.
            case 0x802C0 -> {
                swing(); move(0);
                if(getX()>((cameraX()+0x180)&0xFFFF)) {
                    codePointer=0x8030E; signal(0x20); control|=0x30;
                    spawnFreeChild(DezFinalArenaSignal::quake);
                    state().art().queueModule(services(),0xDB406,0x5A0);
                } else visible=true;
            }
            case 0x8030E -> {
                codePointer=0x80382; control=0;
                writeX(cameraX()-0x80); writeY((services().camera().getY()&0xFFFF)+0x80);
                explosion(0x16); explosion(0x16); explosion(0x18); explosion(0x18);
            }
            case 0x80382 -> {
                writeX(getX()+2);
                if(getX()>((cameraX()+0x80)&0xFFFF)) {
                    codePointer=0x803D6;
                    exitFade=spawnFreeChild(()->new DdzWhiteFadeObjectInstance(DdzWhiteFadeObjectInstance.Mode.TO_WHITE_HOLD,3));
                    if(exitFade==null || exitFade.isDestroyed() || exitFade.getSlotIndex()<0) installForcedFade();
                }
            }
            case 0x803D6 -> {
                if(exitFade!=null && exitFade.nativeFadeCompleted()) finishLevel();
            }
            default -> throw new IllegalStateException("DEZ escape code "+Integer.toHexString(codePointer));
        }
        updateDynamicSpawn(getX(),getY());
    }
    private void updateLive(int clock) {
        switch(routine) {
            case 0 -> {
                routine=2; frame=5; priority=4; halfWidth=halfHeight=0x20; highPriority=false; flipX=true;
                collision=0xF; collisionProperty=8;
                writeX(DezFinalCamera.nativeCopyX(services().camera())+0x60);
                writeY((services().camera().getYCopy()&0xFFFF)+0x140);
                spawnFreeChild(DezFinalArenaSignal::quake);
                // Independent CreateChild1 tables; a failed head never suppresses the crane table.
                spawnChild(()->DezFinalShipDecoration.head(this));
                var crane=spawnChild(()->DezFinalEscapeScenery.crane(this));
                if(crane!=null && !crane.isDestroyed() && crane.getSlotIndex()>=0) spawnChildAfterSlot(crane.getSlotIndex(),()->new DezFinalEmerald(this,2,0,0x3B));
            }
            case 2 -> {
                writeY(getY()-1);
                if(getY()<=(((services().camera().getY()&0xFFFF)+0x50)&0xFFFF)) {
                    routine=4; xVelocity=0x500; spawnChild(()->DezFinalShipDecoration.flame(this));
                    yVelocity=0xC0; control&=~1; // Swing_Setup1
                }
            }
            case 4 -> {
                accelerateCamera(); swing(); move(0);
                if(getX()>=((cameraX()+0x100)&0xFFFF)) { routine=6; xVelocity=0; timer=0x1F; callback=0x80250; }
            }
            case 6 -> { debris(clock); swing(); move(0); waitCallback(); }
            case 8 -> {
                accelerateCamera(); debris(clock); swing();
                if((xVelocity&0xFFFF)>0x280) xVelocity=(short)(xVelocity-0x10);
                move(0);
                if(getX()<((cameraX()+0x100)&0xFFFF)) writeX(cameraX()+0x100);
            }
            default -> throw new IllegalStateException("DEZ escape routine "+routine);
        }
    }
    private void accelerateCamera() {
        var state=state(); int next=state.escapeCameraSpeed()+0x1000;
        // sub_80F0E stores only values <= $40000 but uses the unstored $41000
        // on every subsequent pass. Keep that shipped overshoot, not a clamp.
        if(Integer.compareUnsigned(next,0x40000)<=0) state.escapeCameraSpeed(next);
        int fixed=(cameraX()<<16)|state.escapeCameraFraction(); fixed+=next;
        state.escapeCameraFraction(fixed); short x=(short)(fixed>>>16);
        var camera=services().camera(); camera.setXAfterRenderCopy(DezFinalCamera.visibleX(camera, x & 0xFFFF)); camera.setMinX(x); camera.setMaxX(x);
    }
    private void swing() {
        int velocity=(short)yVelocity;
        if((control&1)==0) {
            velocity=(short)(velocity-0x10);
            if(velocity<=-0xC0) { control|=1; velocity=(short)(velocity+0x10); }
        } else {
            velocity=(short)(velocity+0x10);
            if(velocity>=0xC0) { control&=~1; velocity=(short)(velocity-0x10); }
        }
        yVelocity=velocity;
    }
    private void debris(int clock) { if((clock&15)==0) spawnFreeChild(DezFinalEscapeScenery::debris); }
    private void signal(int mask) { state().bossSignals(state().bossSignals()|mask); }
    // loc_8030E uses CreateChild6_Simple: parent3 is the moving ship, not
    // a copied position. $16/$18 workers track it until bit 5 or retirement.
    private void explosion(int subtype) { spawnChild(()->new DezMinibossExplosionController(this,subtype)); }
    private void waitCallback() {
        timer=(short)(timer-1); if(timer>=0) return;
        switch(callback) {
            case 0x80250 -> { routine=8; xVelocity=0x500; control|=0x80; }
            case 0x8029C -> { signal(8); timer=0xF; callback=0x802B2; }
            case 0x802B2 -> { codePointer=0x802C0; signal(0x10); }
            default -> throw new IllegalStateException("DEZ escape callback "+callback);
        }
    }
    private void damage() {
        if(collision!=0) return;
        if(collisionProperty==0) {
            codePointer=0x8565E; timer=0x2F; callback=0x8029C; explosion(4);
            services().gameState().addScore(1000); return;
        }
        if(flash==0) {
            flash=0x20; status|=0x40; services().playSfx(Sonic3kSfx.BOSS_HIT.id);
            if((control&0x80)!=0) {
                var p=hitByP2?services().playerQuery().nativeP2OrNull():services().playerQuery().mainPlayerOrNull();
                if(p!=null) { p.setXSpeed((short)0); p.setGSpeed((short)0); }
            }
        }
        // FixBugs=0 in sub_80E2C: even flashes start two WORDS into the
        // three-word rows, yielding $222,$888,$CCC rather than the fixed row.
        int source=0x80EE8+((flash&1)==0?4:0);
        try {
            for(int i=0;i<3;i++) {
                int index=((romWord(0x80EE2+i*2)-0xFC00)&0x7F)/2;
                S3kPaletteWriteSupport.applyContiguousPatch(services().paletteOwnershipRegistryOrNull(),
                        services().currentLevel(),services().graphicsManager(),S3kPaletteOwners.DEZ_FINAL_BOSS,
                        S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,index/16,index%16,services().rom().readBytes(source+i*2,2));
            }
        } catch(IOException failure) { throw new UncheckedIOException(failure); }
        if(--flash==0) { status&=~0x40; collision=savedCollision; if(routine>=8) xVelocity=0x800; }
    }
    private void installForcedFade() {
        // loc_803B4 clears Dynamic_object_RAM + 61*$4A, absolute SST slot 64.
        // Release the Java occupant before reserving the overwritten slot so
        // its later retirement cannot release the new fade's allocation.
        var manager=services().levelManager().getObjectManager();
        for(var occupant:List.copyOf(manager.getActiveObjects())) {
            if(occupant instanceof AbstractObjectInstance sprite && sprite.getSlotIndex()==64) {
                ObjectLifetimeOps.deleteNoRespawn(sprite); manager.releaseSlot(sprite); manager.removeDynamicObject(sprite);
            }
        }
        manager.releaseDynamicSlot(64);
        exitFade=manager.createDynamicObjectAtSlot(()->new DdzWhiteFadeObjectInstance(DdzWhiteFadeObjectInstance.Mode.TO_WHITE_HOLD,3),64);
        if(exitFade==null) throw new IllegalStateException("Native forced DEZ fade slot unavailable");
    }
    private void finishLevel() {
        services().requestSessionSave(SaveReason.PROGRESSION_SAVE); pendingDelete=true; status|=0x80; exitFade=null;
        var character=state().playerCharacter();
        if((character==PlayerCharacter.SONIC_ALONE || character==PlayerCharacter.SONIC_AND_TAILS)
                && services().gameState().getEmeraldCount()==7) services().requestZoneAndAct(0xC,0,true);
        else if(character!=PlayerCharacter.KNUCKLES) services().requestZoneAndAct(0xD,1,true);
        else {
            // Native Game_mode=0 selects Sega. Use the existing engine Sega/title
            // exit request; there is no separate rendered Sega mode in this flow.
            services().levelManager().requestGameOverExit(GameOverExit.TITLE_SCREEN);
        }
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
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if(!visible || isDestroyed()) return;
        var renderer=getRenderer(Sonic3kObjectArtKeys.DEZ_FINAL_SHIP);
        if(renderer!=null && renderer.isReady()) renderer.drawFrameIndexForcedPriority(frame,getX(),getY(),flipX,flipY,0,highPriority);
    }
}
