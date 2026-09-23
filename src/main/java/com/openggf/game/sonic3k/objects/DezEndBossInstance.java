package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.ShieldType;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.objects.bosses.S3kSharedBossCameraGate;
import com.openggf.game.sonic3k.runtime.S3kDezZoneRuntimeState;
import com.openggf.level.LevelContinuationCarry;
import com.openggf.level.objects.*;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Obj_DEZEndBoss $7F06C: the released enemies, never player attacks, own damage publication. */
public final class DezEndBossInstance extends DezEndBossSprite implements SpawnRewindRecreatable,DezEndBossEnemy.Owner {
    private final S3kSharedBossCameraGate cameraGate=new S3kSharedBossCameraGate();
    private final DezEndBossArtState art=new DezEndBossArtState();
    private final DezEndBossDamageState damage=new DezEndBossDamageState();
    private int routine;
    private int timer;
    private int callback;
    private int enemyCount;
    private int animationScript;
    private int animationCursor;
    private int animationTimer;

    public DezEndBossInstance(ObjectSpawn spawn) { super(spawn,"DEZEndBoss"); }
    @Override public void update(int clock,PlayableEntity ignored) {
        visible=false;
        if(codePointer==0) { initialize(); return; }
        art.service(services());
        var camera=services().camera();
        switch(codePointer) {
            case 0x7F0CE -> {
                if(cameraGate.update(camera,()->services().playMusic(Sonic3kMusic.BOSS.id),nativeFramedCameraX())) {
                    // loc_7F0D2 changes only the next code pointer.
                    codePointer=0x7F0DA;
                }
            }
            case 0x7F0DA -> {
                fight();
                if(damage.update(services())) defeat();
                if(damage.invulnerable()) status|=0x40; else status&=~0x40;
                if((clock&0x3F)==0) services().playSfx(Sonic3kSfx.WAVE_HOVER.id);
                visible=true;
            }
            case 0x85694 -> {
                timer=(short)(timer-1);
                if(timer<0) { timer=119; codePointer=0x7F220; xVelocity=yVelocity=0; }
                else visible=true;
            }
            case 0x7F220 -> {
                move(0x20);
                if(getY()>=0x318) {
                    codePointer=0x7F266;
                    spawnPrefix(DezEndBossEscape.PATH,2);
                    spawnPrefix(DezEndBossEscape.DEBRIS,6);
                    spawnChild(()->new DezMinibossExplosionController(getX(),getY(),0));
                    services().fadeOutMusic();
                } else visible=true;
            }
            case 0x7F266 -> {
                if((runtime().bossSignals()&2)!=0) {
                    codePointer=0x7F29A; control|=0x10; timer=0x3F;
                    writeX(0x35D0); writeY(0x328);
                    spawnChild(()->new DezMinibossExplosionController(getX(),getY(),0));
                }
            }
            case 0x7F29A -> {
                timer=(short)(timer-1);
                if(timer==0) {
                    codePointer=0x7F2DC; runtime().setBossFlag(false);
                    runtime().clearWidescreenHorizontalArenaLock();
                    services().playMusic(Sonic3kMusic.DEZ2.id);
                    runtime().setCameraStoredMaxX(0x3620);
                    spawnFreeChild(()->new S3kCameraGradualObjectInstance(S3kCameraGradualObjectInstance.INC_END_X));
                } else if(timer==0x30) runtime().setEventsFg4(0xFF);
            }
            case 0x7F2DC -> {
                camera.setMinX((short) nativeFramedCameraX());
                // Native Camera_X cannot reach $3620 before the max-X worker does.
                // A wide viewport's centre can: preserve that ordering and anchor its
                // native 320-pixel window at the ROM exit before locking the camera.
                if((camera.getMaxX()&0xFFFF)>=0x3620 && nativeFramedCameraX()>=0x3620) {
                    int framing=nativeFramedCameraX()-(camera.getX()&0xFFFF);
                    camera.setX((short)(0x3620-framing)); camera.setMinX((short) nativeFramedCameraX());
                    codePointer=0x7F2FE; camera.setScrollLocked(true);
                    camera.setMaxX((short)(camera.getMaxX()+0x40));
                }
            }
            case 0x7F2FE -> {
                var player=services().playerQuery().mainPlayerOrNull();
                if(player!=null && (player.getCentreX()&0xFFFF)>=((nativeFramedCameraX()+0x160)&0xFFFF)) {
                    ShieldType shield=player.hasShield()&&player.getShieldType()!=ShieldType.BASIC?player.getShieldType():null;
                    LevelContinuationCarry.request(services().levelManager(),0x17,0,player.getRingCount(),
                            services().levelGamestate().getTimerFrames(),shield);
                    ObjectLifetimeOps.deleteNoRespawn(this);
                }
            }
            default -> throw new IllegalStateException("Unknown DEZ boss code "+codePointer);
        }
        updateDynamicSpawn(getX(),getY());
    }
    private void initialize() {
        var camera=services().camera(); int x=nativeFramedCameraX(),y=camera.getY()&0xFFFF;
        if(y<0x198||y>0x498||x<0x33E0||x>0x3480) {
            if(isCoarseXOutOfRange(getX(),x,coarseXCullRange())) ObjectLifetimeOps.destroyRespawnableOffscreen(this);
            return;
        }
        var state=runtime();
        // ROM word_7F0C6 permits X $3400..$34E0, a moving 320px window.
        // Native 320px follows Sonic horizontally within this range. Widescreen
        // can show the arena at once, so fix its X at the range midpoint ($3470)
        // minus half the extra width. Y still follows Sonic inside $218..$288;
        // changing minX/maxX would also change player walls, so keep them native.
        // Release this X-only lock at loc_7F2DC when the escape corridor opens.
        // The ordinary centred-window policy continues through the exit.
        state.setCenterNativeArenaCamera(true);
        state.lockWidescreenHorizontalArena(0x3400,0x34E0);
        state.setBossFlag(true); state.setBossSignals(0);
        state.setCameraStoredMinX(camera.getMinX()); state.setCameraStoredMaxX(camera.getMaxX());
        state.setCameraStoredMinY(camera.getMinY()); state.setCameraStoredMaxY(camera.getMaxYTarget());
        services().fadeOutMusic();
        cameraGate.begin(camera,new S3kSharedBossCameraGate.LockBounds(0x218,0x288,0x3400,0x34E0),120,x);
        codePointer=0x7F0CE; art.submit(services());
        try {
            S3kPaletteWriteSupport.applyLine(services().paletteOwnershipRegistryOrNull(),services().currentLevel(),
                    services().graphicsManager(),S3kPaletteOwners.DEZ_END_BOSS,S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                    1,services().rom().readBytes(0x7FD08,32));
        } catch(IOException failure) { throw new UncheckedIOException(failure); }
        var robotnik=spawnChild(()->new DezEndBossEscape(this,DezEndBossEscape.ROBOTNIK,0));
        if(robotnik!=null&&!robotnik.isDestroyed()) spawnChild(()->new DezEndBossEscape(this,DezEndBossEscape.DOOR,2));
    }
    private int nativeFramedCameraX() {
        var camera=services().camera();
        return com.openggf.camera.NativeViewportFraming.nativeLeft(camera.getX(),camera.getWidth())&0xFFFF;
    }
    private void fight() {
        switch(routine) {
            case 0 -> {
                routine=2; frame=0; priority=4; halfWidth=halfHeight=0x20;
                yVelocity=0x100; timer=0xBF; callback=0x7F15A;
                spawnChild(()->new DezEndBossBumper(this,0));
                if(services().playerQuery().nativeP2OrNull()!=null) spawnChild(()->new DezEndBossBumper(this,2));
            }
            case 2 -> { move(0); waitCallback(); }
            case 4,8 -> { swing(); move(0); reverseAtWalls(); waitCallback(); }
            case 6,10 -> { swing(); move(0); reverseAtWalls(); animate(); }
            default -> throw new IllegalStateException("Unknown DEZ boss routine "+routine);
        }
    }
    private void waitCallback() { timer=(short)(timer-1); if(timer<0) callback(callback); }
    private void callback(int address) {
        switch(address) {
            case 0x7F15A -> { yVelocity=0xC0; control&=~1; xVelocity=0x100; startPatrol(); }
            case 0x7F166 -> startPatrol();
            case 0x7F194 -> {
                if(enemyCount>=3) { timer=0xF; return; }
                routine=6; enemyCount=(enemyCount+1)&255; control|=8; callback=0x7F1DE;
                setAnimation(0x7FCD4);
                var shield=spawnChild(()->new DezEndBossShield(this));
                if(shield!=null&&!shield.isDestroyed()) spawnChild(()->new DezEndBossEnemy(this));
            }
            case 0x7F1DE -> { routine=8; control|=4; timer=0x3F; callback=0x7F1FA; }
            case 0x7F1FA -> { routine=10; callback=0x7F166; setAnimation(0x7FCD9); }
            default -> throw new IllegalStateException("Unknown DEZ boss callback "+address);
        }
    }
    private void startPatrol() { routine=4; control&=0xF3; timer=0xB3; callback=0x7F194; }
    private void setAnimation(int script) { animationScript=script; animationCursor=animationTimer=0; }
    private void animate() {
        animationTimer=(byte)(animationTimer-1); if(animationTimer>=0) return;
        animationCursor=(animationCursor+1)&255;
        int next=romByte(animationScript+1+animationCursor);
        if(next==0xF4) { animationTimer=0; callback(callback); animationCursor=0; }
        else { animationTimer=romByte(animationScript); frame=next; }
    }
    private void swing() {
        int velocity=(short)yVelocity;
        if((control&1)==0) {
            velocity=(short)(velocity-0x10);
            if(velocity>-0xC0) { yVelocity=velocity; return; }
            control|=1;
        }
        velocity=(short)(velocity+0x10);
        if(velocity>=0xC0) { control&=~1; velocity=(short)(velocity-0x10); }
        yVelocity=velocity;
    }
    private void reverseAtWalls() {
        if(xVelocity>=0?getX()>=0x3598:getX()<=0x3488) xVelocity=(short)-xVelocity;
    }
    private void defeat() {
        // Wait_NewDelay inherits $2E from the active attack routine, including negative values.
        codePointer=0x85694; status|=0x80; runtime().setBossSignals(runtime().bossSignals()|1);
        spawnChild(()->new DezMinibossExplosionController(getX(),getY(),0));
        spawnFreeChild(DezEndBossEscape.GravityClearer::new);
        services().levelGamestate().pauseTimer(); services().gameState().addScore(1000);
    }
    private void spawnPrefix(int kind,int count) {
        for(int i=0;i<count;i++) {
            int subtype=i*2; var child=spawnChild(()->new DezEndBossEscape(this,kind,subtype));
            if(child==null||child.isDestroyed()) break;
        }
    }
    @Override public DezEndBossDamageState.Contact enemyContact(int x,int y,boolean flipY,int velocity) {
        var result=damage.enemyContact(getX(),getY(),x,y,flipY,velocity);
        if(result==DezEndBossDamageState.Contact.HIT) status|=0x40;
        return result;
    }
    @Override public void enemyRetired() { enemyCount=(enemyCount-1)&255; }
    S3kDezZoneRuntimeState runtime() {
        return (S3kDezZoneRuntimeState)services().zoneRuntimeState();
    }
    int healthForTest() { return damage.health(); }
    int routineForTest() { return routine; }
    int enemyCountForTest() { return enemyCount; }
    int timerForTest() { return timer; }
}
