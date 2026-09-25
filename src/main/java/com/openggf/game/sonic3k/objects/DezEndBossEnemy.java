package com.openggf.game.sonic3k.objects;

import com.openggf.game.DamageCause;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.level.objects.*;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** loc_7F398: released enemy, its gravity/roll response and three-shot death burst. */
final class DezEndBossEnemy extends DezEndBossSprite
        implements RewindRecreatable, TouchResponseProvider, TouchResponseListener, PoweredScreenAttackSpecial {
    interface Owner {
        DezEndBossDamageState.Contact enemyContact(int x, int y, boolean flipY, int yVelocity);
        void enemyRetired();
    }
    private DezEndBossSprite parent;
    private int state;
    private int routine;
    private int timer;
    private int childDy=0xC;
    private int flipTimer;
    private int contactGrace;
    private int savedXVelocity;
    private int animationCursor;
    private int animationTimer;
    private boolean onCeiling;
    private boolean burst;
    private boolean touchPublished;
    private DezEndBossEnemy(ObjectSpawn spawn) { super(spawn,"DEZEndBossEnemy"); }
    DezEndBossEnemy(DezEndBossSprite parent) {
        this(new ObjectSpawn(parent.getX(),parent.getY()+0xC,0,2,0,false,0));
        if (!(parent instanceof Owner)) throw new IllegalArgumentException("DEZ enemy requires encounter owner");
        this.parent=parent;
    }
    @Override public DezEndBossEnemy recreateForRewind(RewindRecreateContext context) {
        return new DezEndBossEnemy(context.spawn());
    }
    @Override public void update(int vIntRunCount,PlayableEntity ignored) {
        visible=touchPublished=false;
        if(pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        if(parent==null) return;
        if(state==0) {
            state=1; priority=5; halfWidth=0x10; halfHeight=0x18; frame=0x11;
            drawWithParent(); return;
        }
        if(state==1) {
            if((parent.control&4)==0) { follow(); drawWithParent(); return; }
            state=2; timer=0x10;
        }
        if(state==2) {
            if(--timer>=0) {
                if((control&4)==0) childDy=(childDy+1)&255;
                control^=4; follow(); drawWithParent(); return;
            }
            state=3; timer=7;
        }
        if(state==3) {
            if(--timer<0) { state=4; services().playSfx(Sonic3kSfx.MUSHROOM_BOUNCE.id); }
            // loc_7F40A falls through into loc_7F414, even during the seven-pass wait.
            writeX(parent.getX());
            fall(vIntRunCount); return;
        }
        if(state==4) { fall(vIntRunCount); return; }
        if(state==5) { grounded(vIntRunCount); return; }
        if(state==6) {
            if(--timer<0) {
                if(burst) for(int i=0;i<3;i++) {
                    int subtype=i*2; var shot=spawnChild(()->new Shot(this,subtype));
                    if(shot==null || shot.isDestroyed()) break;
                }
                goDelete();
            } else visible=true;
        }
        updateDynamicSpawn(getX(),getY());
    }
    private void follow() { writeX(parent.getX()); writeY(parent.getY()+(byte)childDy); }
    private void drawWithParent() {
        if((parent.status&0x80)!=0) goDelete(); else visible=true;
        updateDynamicSpawn(getX(),getY());
    }
    /** sub_7F8A0 adds signed gravity BEFORE integrating Y, unlike MoveSprite. */
    private void gravityMove() {
        posX+=(short)xVelocity<<8;
        yVelocity=(short)(yVelocity+(services().gameState().isReverseGravityActive()?-0x38:0x38));
        posY+=(short)yVelocity<<8;
    }
    private int terrainDistance(boolean ceiling) {
        var level=services().levelManager();
        return ceiling?ObjectTerrainUtils.checkNativeUpwardCeilingDist(level,getX(),getY(),0xB).distance()
                :ObjectTerrainUtils.checkFloorDist(level,services().backgroundPlaneCollisionProvider(),
                        services().useSecondaryTerrainCollisionPath(),getX(),getY()+0xB).distance();
    }
    private void fall(int vIntRunCount) {
        gravityMove(); boolean ceiling=yVelocity<0; int distance=terrainDistance(ceiling);
        if(distance<0) {
            writeY(getY()+(ceiling?-distance:distance)); onCeiling=ceiling;
            routine=ceiling?0:2; state=5; timer=599;
            xVelocity=(byte)services().rng().nextRaw()<0?-0x80:0x80; yVelocity=0;
        }
        // FixBugs=0: loc_7F43E loads the parent but tests status(a0), NOT status(a1).
        if((status&0x80)!=0) retire(false);
        else visible=(vIntRunCount&1)==0;
        updateDynamicSpawn(getX(),getY());
    }
    private void grounded(int vIntRunCount) {
        boolean skipTail=false;
        switch(routine) {
            case 0 -> {
                int outcome=ceilingContact(vIntRunCount);
                if(outcome==2) return;
                if(outcome==0) { releaseForGravity(); if(--timer<0) beginTimeout(); }
            }
            case 2 -> {
                move(0); floorContact(vIntRunCount); reverseAtWalls(); animateWalk();
                releaseForGravity(); if(--timer<0) beginTimeout();
            }
            case 4 -> { gravityMove(); skipTail=airborne(vIntRunCount); }
            case 6 -> {
                if(--flipTimer==0) flipY=!flipY;
                gravityMove(); reverseAtWalls(); savedXVelocity=xVelocity<0?-0x80:0x80;
                skipTail=airborne(vIntRunCount);
            }
            case 8 -> { if(--timer<0) { retire(true); return; } }
            default -> throw new IllegalStateException("Unknown DEZ enemy routine "+routine);
        }
        if(skipTail) return;
        if((parent.status&0x80)!=0) { retire(false); return; }
        var contact=((Owner)parent).enemyContact(getX(),getY(),flipY,yVelocity);
        if(contact!=DezEndBossDamageState.Contact.NONE) { retire(true); return; }
        visible=touchPublished=true; updateDynamicSpawn(getX(),getY());
    }
    private void releaseForGravity() {
        if(onCeiling==services().gameState().isReverseGravityActive()) return;
        routine=4; contactGrace=0;
        if(xVelocity!=0) { savedXVelocity=xVelocity; xVelocity=0; }
        yVelocity=0;
    }
    private void beginTimeout() { routine=8; timer=0x1F; }
    private void reverseAtWalls() {
        if(xVelocity>=0?getX()>=0x3598:getX()<=0x3488) xVelocity=(short)-xVelocity;
    }
    private void animateWalk() {
        animationTimer=(byte)(animationTimer-1);
        if(animationTimer>=0) return;
        animationTimer=romByte(0x7FCDE); animationCursor=(animationCursor+1)&255;
        int next=romByte(0x7FCDF+animationCursor);
        if(next==0xFC) { animationCursor=0; next=romByte(0x7FCDF); }
        frame=next;
    }
    private PlayableEntity consumeContact() {
        if(collisionProperty==0) return null;
        var query=services().playerQuery();
        var player=(collisionProperty&2)!=0?query.nativeP2OrNull():query.mainPlayerOrNull();
        collisionProperty=0; return player;
    }
    /** 0 normal continuation; 1 skip routine tail after kick; 2 complete owner return. */
    private int ceilingContact(int vIntRunCount) {
        var player=consumeContact(); if(player==null) return 0;
        if(player.getAnimationId()!=2) { hurt(player,vIntRunCount); return 0; }
        if(player.getAir()) {
            int x=player.getCentreX(),y=player.getCentreY(); player.setRolling(true);
            if(player instanceof AbstractPlayableSprite sprite) {
                sprite.applyCustomRadii(7,0xE);
                NativePositionOps.writeXPosPreserveSubpixel(sprite,x); NativePositionOps.writeYPosPreserveSubpixel(sprite,y);
                sprite.setAnimationId(2);
            }
            player.setYSpeed((short)-0x300); player.setAir(true); retire(false); return 2;
        }
        routine=6; flipTimer=7; contactGrace=8;
        xVelocity=player.getXSpeed()<0?-0x200:0x200;
        int speed=Math.max(0x500,Math.abs((short)player.getGSpeed()));
        yVelocity=(short)(flipY?-speed:speed);
        return 1;
    }
    private void floorContact(int vIntRunCount) {
        var player=consumeContact(); if(player==null) return;
        if(player.getAnimationId()!=2 || player.getAir()) { hurt(player,vIntRunCount); return; }
        boolean left=(short)(getX()-player.getCentreX())>=0;
        if(left==(player.getXSpeed()>=0)) {
            player.setXSpeed((short)-player.getXSpeed()); player.setGSpeed((short)-player.getGSpeed());
        }
    }
    private boolean airborne(int vIntRunCount) {
        var player=consumeContact();
        if(player!=null) {
            if(contactGrace!=0) contactGrace=(contactGrace-1)&255;
            else {
                boolean attack=player.getAnimationId()==2 || player.getAnimationId()==9;
                boolean above=(short)(getY()-player.getCentreY())>=0;
                if(attack && Math.abs((short)(getX()-player.getCentreX()))<0x10 && flipY==above) {
                    player.setXSpeed((short)-player.getXSpeed()); player.setYSpeed((short)-player.getYSpeed());
                    player.setGSpeed((short)-player.getGSpeed()); retire(false); return true;
                }
                hurt(player,vIntRunCount);
            }
        }
        boolean ceiling=yVelocity<0; int distance=terrainDistance(ceiling);
        if(distance<0) {
            writeY(getY()+(ceiling?-distance:distance)); onCeiling=ceiling;
            if(ceiling==flipY) { routine=2; xVelocity=savedXVelocity; yVelocity=0; }
            else routine=0;
        }
        return false;
    }
    private void hurt(PlayableEntity player,int vIntRunCount) {
        if(player.getInvulnerable() || player.getInvincibleFrames()>0 || player.isSuperSonic()) return;
        if(player.isCpuControlled()) player.applyHurt(getX(),DamageCause.NORMAL);
        else {
            boolean rings=player.getRingCount()>0;
            if(rings&&!player.hasShield()) services().spawnLostRings(player,vIntRunCount);
            player.applyHurtOrDeath(getX(),DamageCause.NORMAL,rings);
        }
    }
    private void retire(boolean shots) {
        spawnChild(()->new DezMinibossExplosionController(getX(),getY(),6));
        ((Owner)parent).enemyRetired(); state=6; timer=7; burst=shots;
        visible=true; touchPublished=false; updateDynamicSpawn(getX(),getY());
    }
    private void goDelete() { status|=0x80; pendingDelete=true; }
    @Override public void onTouchResponse(PlayableEntity player,TouchResponseResult result,int vIntRunCount) {
        if(!touchPublished || result.category()!=TouchCategory.SPECIAL) return;
        var query=services().playerQuery();
        if(player==query.mainPlayerOrNull()) collisionProperty|=1;
        else if(player==query.nativeP2OrNull()) collisionProperty|=2;
    }
    @Override public void orCollisionProperty(int mask) { if(touchPublished) collisionProperty|=mask; }
    @Override public int getCollisionFlags() { return state>=5?0xC6:0; }
    @Override public int getCollisionProperty() { return collisionProperty; }
    @Override public boolean usesS3kTouchSpecialPropertyResponse() { return true; }
    @Override public boolean requiresContinuousTouchCallbacks() { return true; }
    @Override public boolean publishesTouchResponseListEntryThisFrame() { return touchPublished; }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
        return TouchResponseProfile.fromProvider(this,multiRegionSource);
    }
    int stateForTest() { return state; }
    int routineForTest() { return routine; }
    DezEndBossSprite parentForTest() { return parent; }

    static final class Shot extends DezEndBossSprite implements RewindRecreatable,TouchResponseProvider {
        private DezEndBossSprite parent;
        private boolean initialized;
        private Shot(ObjectSpawn spawn) { super(spawn,"DEZEndBossShot"); }
        Shot(DezEndBossEnemy parent,int subtype) {
            this(new ObjectSpawn(parent.getX(),parent.getY(),0,subtype,0,false,0)); this.parent=parent;
        }
        @Override public Shot recreateForRewind(RewindRecreateContext context) { return new Shot(context.spawn()); }
        @Override public void update(int vIntRunCount,PlayableEntity ignored) {
            visible=false;
            if(pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
            if(parent==null) return;
            if(!initialized) {
                initialized=true; priority=5; halfWidth=halfHeight=8;
                int subtype=spawn.subtype(),dx=(subtype-2)*8,dy=subtype==2?-0xC:-9;
                flipX=parent.flipX; flipY=parent.flipY;
                writeX(parent.getX()+(flipX?-dx:dx)); writeY(parent.getY()+(flipY?-dy:dy));
                frame=0x1F+subtype/2;
                xVelocity=(short)romWord(0x7FB42+subtype*2); yVelocity=(short)romWord(0x7FB44+subtype*2);
                if(flipY) yVelocity=(short)-yVelocity;
                parent=((DezEndBossEnemy)parent).parent;
                return; // loc_7F60A never draws or moves on initialization.
            }
            move(0);
            if((parent.status&0x80)!=0) ObjectLifetimeOps.deleteNoRespawn(this);
            else if(isCoarseXOutOfRange(getX(),cameraLeft(),coarseXCullRange())
                    || ((getY()-cameraTop()+0x80)&0xFFFF)>0x200) {
                // Sprite_CheckDeleteTouchXY installs Delete_Current_Sprite for the next pass.
                status|=0x80; pendingDelete=true;
            } else visible=true;
            updateDynamicSpawn(getX(),getY());
        }
        @Override public int getCollisionFlags() { return 0x98; }
        @Override public int getCollisionProperty() { return 0; }
        @Override public boolean publishesTouchResponseListEntryThisFrame() { return visible; }
        @Override public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
            return TouchResponseProfile.fromProvider(this,multiRegionSource);
        }
        DezEndBossSprite parentForTest() { return parent; }
    }
}
