package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.*;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** SKL $98, Obj_SOZEndBoss ($7764A). Shell, pilot and arena remain separate native owners. */
public final class SozEndBossInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable, TouchResponseProvider, TouchResponseAttackable {
    private int routine, savedRoutine, xFixed, yFixed, baseY, xVelocity, yVelocity;
    private int timer, initialWalkTimer, strideTimer, footPauseTimer, phaseStep, armStep, armAngle, angle;
    private int chargeTimer = 0xC0, hits = 8, flashTimer;
    private boolean collisionEnabled, hitPending, defeated, dismantling, hidden, flipped;
    private int escapePhase, swingVelocity, swingDirection, forcedJumpTimer;
    public SozEndBossInstance(ObjectSpawn spawn) {
        super(spawn,"SOZEndBoss");xFixed=spawn.x()<<16;yFixed=spawn.y()<<16;baseY=spawn.y();
    }
    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if(escapePhase!=0) { escape(player);return; }
        switch(routine) {
            case 0 -> initialize();
            case 2 -> { if(player!=null && Math.abs((short)(getX()-player.getCentreX()))<0xC0) {
                routine=4;timer=59;services().fadeOutMusic();
            } }
            case 4 -> { if(--timer<0) {routine=6;services().playMusic(Sonic3kMusic.BOSS.id);loadWalk(false);} }
            case 6 -> {move();bob();if(--timer<0){routine=8;timer=initialWalkTimer;}}
            case 8 -> {charge();move();bob();armAngle=(armAngle+armStep)&0xFFFF;
                if(--timer<0){routine=10;timer=footPauseTimer;services().playSfx(Sonic3kSfx.THUMP_BOSS.id);runtime().events().screenShakeFlag(0x14);}}
            case 10 -> {charge();if(--timer<0){routine=8;reverseAtBoundary();phaseStep=(phaseStep&0xFF)|((-(phaseStep>>>8)&0xFF)<<8);
                armStep=(short)-armStep;timer=strideTimer;}}
            case 12 -> {
                if(runtime().events().bossWallHitY()==0){routine=savedRoutine;collisionEnabled=false;hitPending=false;flashPalette(false);}
                else updateHits();
            }
            default -> throw new IllegalStateException("SOZ end-boss routine "+routine);
        }
        runtime().events().bossX(getX());runtime().events().bossY(getY());
    }
    private void initialize() {
        routine=2;
        // The direct six-child table and both linked tables have independent
        // prefix-failure boundaries. All helpers search after the root SST.
        for(int role=0;role<6;role++) {
            int selected=role;
            var child=spawnChild(()->new SozEndBossChild(this,null,selected));
            if(child==null||child.getSlotIndex()<0)break;
        }
        for(int arm=0;arm<2;arm++) {
            SozEndBossChild previous=null;
            for(int segment=0;segment<3;segment++) {
                int role=6+arm*3+segment;var prior=previous;
                var child=spawnChild(()->new SozEndBossChild(this,prior,role));
                if(child==null||child.getSlotIndex()<0)break;previous=child;
            }
        }
        loadPalette(1,0x784A0);loadPalette(3,0x784C0);
        services().gameState().setCurrentBossId(0x98);
        queueArt();
    }
    private void queueArt() {
        try {
            if(services().currentLevel() instanceof Sonic3kLevel level) {
                // Load_PLC $6D supplies the shared ship, explosion and capsule.
                var plc=Sonic3kPlcLoader.parsePlc(services().rom(),0x6D);
                var modified=Sonic3kPlcLoader.applyToLevel(plc,level,services().rom());
                Sonic3kPlcLoader.refreshAffectedRenderers(modified,services().levelManager());
            }
            queueKosinskiArt(0x16E1B0,0x3A4);
        } catch(IOException e){throw new UncheckedIOException(e);}
    }
    void queueKosinskiArt(int source,int tile) {
        try {
            var queue=services().kosinskiModuleQueue();
            if(queue==null)return;
            Sonic3kPlcLoader.bindRuntimePatternDmaTarget(queue,services());
            queue.enqueue(services().rom(),source,tile*32);
            com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator.from(services())
                    .moduleQueue().queue(services().rom(),source,tile);
        } catch(IOException e){throw new UncheckedIOException(e);}
    }
    private void loadWalk(boolean returnRight) {
        int address=returnRight?0x78060:knuckles()?0x77766:0x7775A;
        xVelocity=word(address);phaseStep=word(address+2);armStep=word(address+4);
        initialWalkTimer=word(address+6);timer=initialWalkTimer;strideTimer=word(address+8);footPauseTimer=word(address+10);
    }
    private void reverseAtBoundary() {
        if((byte)(armAngle>>>8)>=0)return;
        if(xVelocity<0 && getX()<0x4E80)loadWalk(true);
        else if(xVelocity>=0 && getX()>=0x5210)loadWalk(false);
    }
    private void move(){xFixed+=(short)xVelocity<<8;yFixed+=(short)yVelocity<<8;}
    private void bob(){angle=(angle-(phaseStep>>>8))&255;yFixed=((baseY+(TrigLookupTable.sinHex(angle)>>4))<<16)|(yFixed&0xFFFF);}
    private void charge(){if(--chargeTimer<0){chargeTimer=0x200;services().playSfx(Sonic3kSfx.CHARGING.id);
        spawnChild(()->new SozEndBossChild(this,null,12));}}
    void openShell(PlayableEntity player) {
        runtime().events().bossWallHitY(player.getCentreY());savedRoutine=routine;routine=12;collisionEnabled=true;
        player.setXSpeed((short)-0x400);player.setYSpeed((short)-0x300);
    }
    @Override public int getCollisionFlags(){return collisionEnabled&&!defeated?0x0F:0;}
    @Override public int getCollisionProperty(){return hits;}
    @Override public void onPlayerAttack(PlayableEntity player,TouchResponseResult result) {
        if(getCollisionFlags()==0)return;hits--;hitPending=true;collisionEnabled=false;if(hits==0)defeated=true;
    }
    private void updateHits() {
        if(collisionEnabled)return;
        if(hitPending){hitPending=false;
            if(hits==0){defeated=true;escapePhase=1;timer=0x3F;runtime().requestEndBossDefeat();
                if(services().levelGamestate()!=null)services().levelGamestate().pauseTimer();
                services().gameState().addScore(1000);services().camera().setMinX(services().camera().getX());
                spawnChild(()->new SozEndBossExplosion(getX(),getY(),getSlotIndex()));return;}
            if(flashTimer==0){flashTimer=0x20;services().playSfx(Sonic3kSfx.BOSS_HIT.id);}
        }
        if(flashTimer!=0){flashPalette((flashTimer&1)==0);if(--flashTimer==0)collisionEnabled=true;}
    }
    private void flashPalette(boolean bright) {
        // Shipped FixBugs=0 calls CopyWordData_3, updating only three of the
        // five listed colors. FixBugs=1 changes all five.
        int address=bright?0x78280:0x78276;
        S3kPaletteWriteSupport.applyColors(services().paletteOwnershipRegistryOrNull(),services().currentLevel(),
                services().graphicsManager(),"soz.endboss.hit",200,3,new int[]{6,7,8},
                new int[]{word(address),word(address+2),word(address+4)});
    }
    private void loadPalette(int line,int address) {
        try {S3kPaletteWriteSupport.applyLine(services().paletteOwnershipRegistryOrNull(),services().currentLevel(),
                services().graphicsManager(),"soz.endboss.setup",190,line,services().rom().readBytes(address,32),true);
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    private void escape(PlayableEntity player) {
        switch(escapePhase) {
            case 1 -> {if(--timer<0){timer=119;spawnFreeChild(SongFadeTransitionInstance::toCurrentLevelMusic);
                dismantling=true;flipped=false;xVelocity=0;escapePhase=2;}}
            case 2 -> {move();yVelocity=(short)(yVelocity+0x18);if(yVelocity>=0x200){escapePhase=3;defeated=false;flipped=true;}}
            case 3 -> {yVelocity=(short)(yVelocity-0x40);move();if(yVelocity<=-0x100){
                escapePhase=4;swingVelocity=0xC0;yVelocity=0xC0;swingDirection=0;
                spawnFreeChild(()->new SozEndBossEggCapsule(0x5360,0x720));
                dismantling=false;spawnChild(()->new SozEndBossChild(this,null,16));}}
            case 4 -> {services().camera().setMinX(services().camera().getX());
                if(swingDirection==0){yVelocity-=0x10;if(yVelocity<=-swingVelocity){swingDirection=1;yVelocity+=0x10;}}
                else {yVelocity+=0x10;if(yVelocity>=swingVelocity){swingDirection=0;yVelocity-=0x10;}}
                yFixed-=1<<16;xVelocity=Math.min(0x400,xVelocity+0x20);move();
                if(getX()>((services().camera().getX()&0xFFFF)+0x1A0)){hidden=true;escapePhase=5;
                    services().gameState().setEndOfLevelActive(true);}}
            case 5 -> {services().camera().setMinX(services().camera().getX());if(!services().gameState().isEndOfLevelActive()){escapePhase=6;restoreControl();
                spawnFreeChild(()->new SozEndBossChild(this,null,17));
                spawnFreeChild(S3kNativeP2LockInstance::new);
                services().camera().setMinY((short)-0x100);services().camera().setMaxYTarget((short)0x800);
                spawnFreeChild(()->new com.openggf.game.sonic3k.objects.bosses.HczEndBossGradualMaxXExtender(getX(),getY(),0x5440));}}
            case 6 -> forcedWalk(player);
            case 7 -> exitMove(player);
            case 8 -> exitFall(player);
            default -> throw new IllegalStateException("SOZ end-boss escape "+escapePhase);
        }
    }
    private void restoreControl(){int music=services().getCurrentLevelMusicId();if(music>=0)services().playMusic(music);for(var entity:services().playerQuery().playersFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS))
        if(entity instanceof AbstractPlayableSprite p){p.setObjectControlled(false);p.setControlLocked(false);p.clearAirForNativeControlRestore();p.setAnimationId(5);p.setForcedAnimationId(-1);}
        if(services().playerQuery().mainPlayerOrNull() instanceof AbstractPlayableSprite p){p.clearLogicalInputState();p.setForcedInputMask(0);p.setControlLocked(true);}}
    private void forcedWalk(PlayableEntity entity){if((services().camera().getMaxYTarget()&65535)>=0x700)services().camera().setMaxY((short)0x800);if(!(entity instanceof AbstractPlayableSprite p))return;
        if((p.getCentreX()&0xFFFF)>=0x5468){xFixed=p.getCentreX()<<16;yFixed=p.getCentreY()<<16;xVelocity=p.getXSpeed();yVelocity=0;
            escapePhase=7;if(!knuckles()){p.setObjectControlled(true);p.setObjectControlSuppressesMovement(true);p.setAnimationId(0x1A);}return;}
        boolean jump=p.getPushing()||forcedJumpTimer!=0;if(p.getPushing())forcedJumpTimer=0x1F;else if(forcedJumpTimer!=0)forcedJumpTimer--;
        p.setForcedInputMask(AbstractPlayableSprite.INPUT_RIGHT|(jump?AbstractPlayableSprite.INPUT_JUMP:0));
        p.setLogicalInputState(false,false,false,true,jump,p.getPushing());}
    private void exitMove(PlayableEntity entity){if(!(entity instanceof AbstractPlayableSprite p))return;move();
        if(!knuckles()&&getX()>=0x54C0){xVelocity=0;yVelocity=0;timer=0x7F;escapePhase=8;services().camera().requestFastVerticalScroll();}
        NativePositionOps.writeXPosPreserveSubpixel(p,!knuckles()&&escapePhase==8?0x54C0:getX());NativePositionOps.writeYPosPreserveSubpixel(p,getY());
        if(knuckles()&&getX()>=0x5560)nextZone();}
    private void exitFall(PlayableEntity entity){if(!(entity instanceof AbstractPlayableSprite p))return;yVelocity=Math.min(yVelocity,0x1000);move();yVelocity+=0x38;
        NativePositionOps.writeXPosPreserveSubpixel(p,getX());NativePositionOps.writeYPosPreserveSubpixel(p,getY());
        if(--timer<0)nextZone();}
    private void nextZone(){services().requestZoneAndAct(9,0,true);setDestroyed(true);}
    public boolean ownsPostResultsTransition(){return escapePhase!=0&&!isDestroyed();}
    boolean fallingIntoNextZone(){return escapePhase==8;}
    boolean flipped(){return flipped;} int xVelocity(){return xVelocity;}
    @Override public boolean isHighPriority(){return escapePhase>=2;}
    boolean defeated(){return defeated;} boolean dismantling(){return dismantling;} boolean hidden(){return hidden;}
    boolean hurtFlash(){return flashTimer!=0;} int limbAngle(){return (byte)(armAngle>>>8);} boolean shellOpen(){return runtime().events().bossWallHitY()!=0;}
    boolean knuckles(){return runtime().playerCharacter()==PlayerCharacter.KNUCKLES;}
    private SozZoneRuntimeState runtime(){return (SozZoneRuntimeState)services().zoneRuntimeState();}
    int word(int address){try{return (short)services().rom().read16BitAddr(address);}catch(IOException e){throw new UncheckedIOException(e);}}
    int byteAt(int address){try{return services().rom().readByte(address)&255;}catch(IOException e){throw new UncheckedIOException(e);}}
    @Override public int getX(){return xFixed>>>16;} @Override public int getY(){return yFixed>>>16;}
    @Override public int getOnScreenHalfWidth(){return 32;}
    @Override public int getOnScreenHalfHeight(){return 32;}
    @Override public boolean requiresContinuousTouchCallbacks(){return true;}
    @Override public boolean isPersistent(){return true;} @Override public int getPriorityBucket(){return 6;}
    @Override public void appendRenderCommands(List<GLCommand> commands){if(hidden)return;var r=getRenderer(Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
        if(r!=null&&r.isReady())r.drawFrameIndex(10,getX(),getY(),flipped,false);}
}
