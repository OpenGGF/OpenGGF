package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.*;
import com.openggf.game.sonic3k.audio.*;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import com.openggf.level.objects.*;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.io.IOException;

/**
 * Obj_SOZMiniboss ($76A0E): Egg Golem's head knocks its body toward the sand pit.
 * No HP counter exists: sub_772F6 defeats the golem only at y_pos >= $A10.
 * Child pose and animation bytes are read from the locked-on ROM.
 */
public final class SozMinibossInstance extends SozMinibossSprite implements SpawnRewindRecreatable {
    private int phase;
    private int routine;
    private int timer;
    private int callback;
    private int radius=0x3B;
    private int pose;
    private int bodyFrame;
    private boolean vulnerable;
    private boolean collapsing;
    private boolean pendingCollapse;
    private int attackerSlot;
    private static final int NATIVE_ARENA_MIN_X=0x4180;
    private int savedMinX;
    private int savedMaxX;
    private long bodyArtOrdinal=-1;
    private long sandArtOrdinal=-1;

    public SozMinibossInstance(ObjectSpawn spawn) {
        super(spawn,"SOZ Egg Golem",Sonic3kObjectArtKeys.SOZ_MINIBOSS);
        x=0x439D;y=0x9F7;priority=6;visible=false;
    }
    @Override public void update(int vIntRunCount,PlayableEntity player) {
        if(phase==0){initialize();return;}
        serviceArt();
        if(phase==1){if(--timer<0)phase=2;return;}
        if(phase==3){
            if(--timer<0)finishSinking();else move(0);
            updateDynamicSpawn(x,y);return;
        }
        if(phase==4)return;
        switch(routine){
            case 0 -> {
                visible=true;routine=2;timer=0x3F;callback=0x76B30;
                services().playMusic(Sonic3kMusic.MINIBOSS.id);
                spawnFreeChild(() -> new SozMinibossChild.PaletteFade(getSpawn()));
            }
            case 2,6 -> waitCallback();
            case 4 -> {
                PlayableEntity target=nearest(player);
                if(target!=null && Math.abs((short)(target.getCentreX()-x))<0x60){
                    flip=target.getCentreX()>=x;routine=6;timer=0x3E;callback=0x76B86;
                    spawnChild(() -> new SozMinibossChild(getSpawn(),this,SozMinibossChild.COVER,0));
                }
            }
            case 8 -> {move(0);waitCallback();}
            case 10 -> {
                move(0);script=0x7745D;int result=animateMulti(false);
                if(result<0){routine=12;callback=0x76C5E;}
                else if(result>0){pose=frame;if(frame==7)yVel=-0x700;frame=0;}
            }
            case 12 -> {
                if(xVel>=0 && (x&65535)>=0x4438)xVel=0;
                move(0x60);
                if(yVel>=0){
                    var floor=ObjectTerrainUtils.checkFloorDist(x,y,radius);
                    if(floor!=null && floor.hasCollision()){y+=floor.distance();runCallback();}
                }
            }
            case 14 -> {
                int result=animateMulti(false);
                if(result<0)runCallback();else if(result>0){pose=frame;frame=bodyFrame;applyPoseOffset();}
            }
            case 16 -> {if(animateRaw()){vulnerable=true;enterObserve();}}
            case 18 -> {
                if(--timer<0){routine=22;script=0x77482;callback=0x76DE6;xVel=0;yVel=0;}
                else {
                    PlayableEntity target=nearest(player);
                    if(target!=null && (target.getCentreX()>=x)!=flip){routine=20;script=0x77452;callback=0x76DA8;}
                }
            }
            case 20 -> {
                timer--;int result=animateMulti(true);
                if(result<0)routine=18;else if(result>0){pose=frame;frame=(frame&0x3F)==9?0x19:1;}
            }
            case 22 -> {
                move(0);int result=animateMulti(false);
                if(result<0)runCallback();else if(result>0){pose=frame;if(frame==7)yVel=-0x700;frame=bodyFrame;applyPoseOffset();}
            }
            default -> throw new IllegalStateException("Egg Golem routine "+routine);
        }
        // Native sub_772F6 runs after the routine, before touch/draw.
        if((y&65535)>=0xA10)beginSinking();
        else if(collapsing && pendingCollapse){
            pendingCollapse=false;routine=12;callback=0x76CA2;radius=8;
            frame=0;pose=0;bodyFrame=0;animCursor=0;animTimer=0;
            services().playSfx(Sonic3kSfx.COLLAPSE.id);
            PlayableEntity attacker=attackerSlot==1?services().playerQuery().mainPlayerOrNull():services().playerQuery().nativeP2OrNull();
            xVel=attacker!=null && attacker.getCentreX()<x?0x200:-0x200;yVel=-0x200;
        }
        updateDynamicSpawn(x,y);
    }
    private void initialize(){
        phase=1;timer=120;
        applyPalette(Sonic3kConstants.PAL_SOZ_MINIBOSS_FADE_ADDR);
        services().playMusic(Sonic3kSmpsConstants.CMD_FADE_OUT); // cmd_FadeOut, Obj_SOZMiniboss at $76A56.
        enqueueArt();
        if(services().renderManager()!=null && services().renderManager().getArtProvider() instanceof Sonic3kObjectArtProvider art)
            art.ensureBossExplosionArtLoaded();
    }
    private void waitCallback(){timer=(short)(timer-1);if(timer<0)runCallback();}
    private void runCallback(){
        switch(callback){
            case 0x76B30 -> {
                routine=4;runtime().requestMinibossDoorClose();
                for(int i=0;i<2;i++){int role=i;if(!allocated(spawnChild(() -> new SozMinibossChild(getSpawn(),this,SozMinibossChild.HITBOX,role))))break;}
            }
            case 0x76B86 -> {
                routine=8;yVel=-0x100;timer=7;callback=0x76BB6;
                for(int i=0;i<8;i++){int role=i;if(!allocated(spawnChild(() -> new SozMinibossChild(getSpawn(),this,SozMinibossChild.PART,role))))break;}
            }
            case 0x76BB6 -> {yVel=0;timer=7;callback=0x76BCA;}
            case 0x76BCA -> {yVel=0x80;timer=15;callback=0x76BE0;}
            case 0x76BE0 -> {routine=10;callback=0x76C26;yVel=0;}
            case 0x76C5E -> {routine=14;bodyFrame=0;callback=0x76CF0;landDust();}
            case 0x76C92 -> {routine=14;callback=0x76D14;landDust();}
            case 0x76CF0 -> {routine=16;script=0x7747D;callback=0x76D0E;}
            case 0x76D14 -> enterObserve();
            case 0x76DA8 -> routine=18;
            case 0x76DE6 -> {routine=12;callback=0x76C92;xVel=flip?0x180:-0x180;}
            case 0x76CA2 -> {
                routine=4;radius=0x3B;xVel=0;yVel=0;runtime().requestMinibossShake(8);
                services().playSfx(Sonic3kSfx.CRASH.id);spawnDust(true);
            }
            default -> throw new IllegalStateException("Egg Golem callback "+Integer.toHexString(callback));
        }
    }
    private void enterObserve(){routine=18;bodyFrame=1;timer=0x60;}
    private void landDust(){script=0x7746A;runtime().requestMinibossShake(0x14);services().playSfx(Sonic3kSfx.BOSS_HIT_FLOOR.id);spawnDust(false);}
    private void spawnDust(boolean collapse){
        for(int i=0;i<6;i++){int role=i;if(!allocated(spawnChild(() -> new SozMinibossChild(getSpawn(),this,collapse?SozMinibossChild.COLLAPSE_DUST:SozMinibossChild.DUST,role))))break;}
    }
    private static boolean allocated(AbstractObjectInstance child){return child!=null && !child.isDestroyed();}
    private void applyPoseOffset(){int dx=(byte)romByte(0x772E2+animCursor),dy=(byte)romByte(0x772E3+animCursor);x+=flip?-dx:dx;y+=dy;}
    private PlayableEntity nearest(PlayableEntity leader){
        PlayableEntity second=services().playerQuery().nativeP2OrNull();
        if(leader==null)return second;if(second==null)return leader;
        return Math.abs((short)(second.getCentreX()-x))<Math.abs((short)(leader.getCentreX()-x))?second:leader;
    }
    private void beginSinking(){
        phase=3;xVel=0;yVel=0x40;timer=0xBF;
        services().levelGamestate().pauseTimer();
        var camera=services().camera();savedMinX=camera.getMinX();savedMaxX=camera.getMaxX();
        camera.setMinX(camera.getX());camera.setMaxX(camera.getX());
        spawnChild(() -> new SozMinibossChild.Explosions(getSpawn(),this));
    }
    private void finishSinking(){
        phase=4;visible=false;
        // loc_76E48 places the sign at Camera_X_pos+$A0. The arena gate writes
        // Camera_min_X_pos=$4180 (sonic3k.asm:113989), so the native camera never sits
        // left of it here. A view wider than the 720px arena gives up that left edge for
        // presentation; anchoring to it would drop the sign into the golem's sand pit.
        x=Math.max(services().camera().getX()&65535,NATIVE_ARENA_MIN_X)+0xA0;
        spawnFreeChild(() -> new SozMinibossChild.Alignment(getSpawn(),savedMinX,savedMaxX));
        // loc_76E48 converts the existing SST to EndSignControl even when allocation is full.
        // It jumps into Obj_EndSignControl, which installs the $77 wait in this same
        // pass; the replacement's own install pass runs one pass later, so its wait
        // starts one entry through (sonic3k.asm:158168-158174, 180377-180383).
        int slot=ObjectLifetimeOps.detachSlotForTransfer(this);
        ObjectLifetimeOps.deleteNoRespawn(this);
        ObjectLifetimeOps.addReplacementAtTransferredSlot(services().objectManager(),
                new S3kBossDefeatSignpostFlow(x,0,S3kBossDefeatSignpostFlow.CleanupAction.NONE,1,0,0,0)
                        .withNativeControlSlot(slot),slot);
    }
    void hitBy(AbstractPlayableSprite player){attackerSlot=player==services().playerQuery().mainPlayerOrNull()?1:2;collapsing=true;pendingCollapse=true;}
    void headRecovered(){collapsing=false;vulnerable=false;}
    boolean vulnerable(){return vulnerable;}
    boolean collapsing(){return collapsing;}
    int pose(){return pose&0x3F;}
    int phase(){return phase;}
    int routine(){return routine;}
    private SozZoneRuntimeState runtime(){return (SozZoneRuntimeState)services().zoneRuntimeState();}
    @Override public boolean isPersistent(){return true;}
    @Override public int getOnScreenHalfWidth(){return 0x24;}
    @Override public int getOnScreenHalfHeight(){return 0x34;}
    private void applyPalette(int address){
        byte[] colors=new byte[32];for(int i=0;i<32;i++)colors[i]=(byte)romByte(address+i);
        S3kPaletteWriteSupport.applyLine(services().paletteOwnershipRegistryOrNull(),services().currentLevel(),services().graphicsManager(),
                "s3k.soz.miniboss",S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,1,colors);
    }
    private void enqueueArt(){
        try{
            var rom=services().rom();if(rom==null)return;
            try{
                var queue=com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator.from(services()).moduleQueue();
                bodyArtOrdinal=queue.queue(rom,Sonic3kConstants.ART_KOSM_SOZ_MINIBOSS_ADDR,0x3B5).ordinal();
                sandArtOrdinal=queue.queue(rom,Sonic3kConstants.ART_KOSM_SOZ_MINIBOSS_SAND_ADDR,0x4F3).ordinal();
            }catch(IllegalStateException unavailable){if(!"runtime-art coordination is unavailable in these object services".equals(unavailable.getMessage()))throw unavailable;}
            var queue=services().kosinskiModuleQueue();
            if(queue!=null){Sonic3kPlcLoader.bindRuntimePatternDmaTarget(queue,services());
                queue.enqueue(rom,Sonic3kConstants.ART_KOSM_SOZ_MINIBOSS_ADDR,0x3B5*32);
                queue.enqueue(rom,Sonic3kConstants.ART_KOSM_SOZ_MINIBOSS_SAND_ADDR,0x4F3*32);}
        }catch(IOException failure){throw new IllegalStateException("Egg Golem art submission",failure);}
    }
    private void serviceArt(){
        if(bodyArtOrdinal<0 && sandArtOrdinal<0)return;
        var queue=com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator.from(services()).moduleQueue();
        if(bodyArtOrdinal>=0){var handle=services().hardwareTiming().pendingHandle(com.openggf.game.timing.HardwareWorkKind.KOS_MODULE_QUEUE,bodyArtOrdinal).orElseThrow();if(queue.isReady(handle)){queue.claim(handle);bodyArtOrdinal=-1;}}
        if(sandArtOrdinal>=0){var handle=services().hardwareTiming().pendingHandle(com.openggf.game.timing.HardwareWorkKind.KOS_MODULE_QUEUE,sandArtOrdinal).orElseThrow();if(queue.isReady(handle)){queue.claim(handle);sandArtOrdinal=-1;}}
    }
}
