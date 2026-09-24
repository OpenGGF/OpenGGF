package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.runtime.LrzBossActState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.*;
import java.util.List;

/** Obj_LRZ3Platform ($AD): generators, rising platforms and their animated undersides. */
public final class LrzBossPlatformObjectInstance extends AbstractObjectInstance
        implements SlopedSolidProvider, RewindRecreatable, RomObjectCodePointerProvider {
    private static final int PLACED = 0, RISING = 1, FALLING = 2, UNDERSIDE = 3, APPROACH = 4, FLOATING = 5, DEBRIS = 6;
    private final SubpixelMotion.State motion = new SubpixelMotion.State(0,0,0,0,0,0);
    private int mode, subtype, timer, riseTimer, animationTimer, animationFrame;
    private int priorityWord=0x280;
    private boolean initialized, generatorActive, nativeStatus7;
    private boolean displayedLastPass, renderOnScreen, flickerBit, visible = true;
    private LrzBossPlatformObjectInstance parent;

    public LrzBossPlatformObjectInstance(ObjectSpawn spawn) {
        super(spawn,"LRZ3Platform");
        motion.x=spawn.x(); motion.y=spawn.y(); subtype=spawn.subtype() & 255;
    }
    /** loc_79A8E writes only the new stream platform's X; loc_79E5A initializes on dispatch. */
    public static LrzBossPlatformObjectInstance floating(int x) {
        var platform=new LrzBossPlatformObjectInstance(new ObjectSpawn(x,0,0,0,0,false,0));
        platform.mode=FLOATING;
        return platform;
    }
    @Override public LrzBossPlatformObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new LrzBossPlatformObjectInstance(context.spawn());
    }
    private LrzBossActState state() {
        return S3kRuntimeStates.currentLrz(services().zoneRuntimeRegistry()).orElseThrow().bossAct();
    }
    @Override public SolidExecutionMode solidExecutionMode() { return SolidExecutionMode.MANUAL_CHECKPOINT; }
    private void solid() {
        var execution=services().solidExecution();
        if(execution!=null && !execution.isInert()) execution.resolveSolidNowAll();
    }
    private void moveAndSolid() { SubpixelMotion.moveSprite2(motion); solid(); }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        displayedLastPass = mode!=PLACED || subtype>=3;
        if(mode==DEBRIS) {
            if(!initialized) { initializeDebris(); return; }
            SubpixelMotion.moveSprite(motion,0x38);
            coarseXCullViewport(getX());
            if(((getY()-services().camera().getY()+0x80)&65535)>0x200) expire();
            flickerBit=!flickerBit; visible=flickerBit; return;
        }
        if(mode==UNDERSIDE) {
            if(parent==null || parent.isDestroyed() || parent.nativeStatus7) { expire(); return; }
            motion.x=parent.getX(); motion.y=parent.getY(); animate(3,2); return;
        }
        if(mode==PLACED) {
            if(subtype<=2) { generator(); return; }
            initialized=true;
            if(subtype==4) { animate(7,4); solid(); }
            else moveAndSolid();
            coarseXCullViewport(getX()); return;
        }
        if(!initialized) {
            initialized=true;
            if(mode==RISING) { motion.yVel=-0x80; riseTimer=0x20; }
        }
        if(mode==RISING) {
            riseTimer=(short)(riseTimer-1);
            if(riseTimer>=0) { moveAndSolid(); return; }
            mode=FALLING; priorityWord=0x180; motion.yVel=0x80;
            // ChildObjDat_7A1B6: one forward allocation, no retry if it fails.
            spawnPlatform(UNDERSIDE,0,0,0,this);
        }
        if(mode==FALLING) {
            moveAndSolid();
            if(subtype==1) {
                if(!renderOnScreen) expire();
                return;
            }
            timer=(short)(timer-1);
            if(timer<0) { expire(); return; }
            if(state().bossSlot()>=0) {
                if(!renderOnScreen) { expire(); return; }
                if(subtype==0 && !state().entryPlatformClaimed() && (getY() & 65535)<=0x612) {
                    mode=APPROACH; state().claimEntryPlatform();
                }
            }
            return;
        }
        if(mode==APPROACH) {
            moveAndSolid();
            if((getY() & 65535)>=0x612) {
                mode=FLOATING; state().publishEntryPlatformReady(); motion.yVel=0; nativeStatus7=true;
            }
            return;
        }
        // sub_79FFE precedes movement/solid contact and destroys platforms against the boss.
        if (breakAgainstBoss()) return;
        // loc_79DEA: sub_79F14 -> sub_7A064 -> sub_79F30.
        int direction=state().streamDirection();
        if((direction & 255)==0 && !renderOnScreen) { expire(); return; }
        int velocity=(state().lavaAmplitude()+(state().driftClock()>>>2))*2;
        motion.xVel=(short)((direction & 0xFF00)==0 ? -velocity : velocity);
        moveAndSolid();
        int sample=((getX()-0x9E0)&65535)>>>1;
        motion.y=0x612-(state().lavaHeight(sample)-0x30);
        coarseXCullViewport(getX());
    }

    @Override public void refreshPostCameraRenderState() {
        if (!displayedLastPass) return;
        displayedLastPass=false;
        renderOnScreen=isWithinRenderSpriteBounds(getOnScreenHalfWidth(),getOnScreenHalfHeight());
    }

    private boolean breakAgainstBoss() {
        int slot=state().bossSlot();
        if(slot<0) return false;
        var boss=services().objectManager().getActiveObjects().stream()
                .filter(o->o instanceof AbstractObjectInstance a && a.getSlotIndex()==slot && !a.isDestroyed())
                .findFirst().orElse(null);
        if(boss==null || ((getX()-boss.getX()+0x40)&65535)>=0x80
                || ((getY()-boss.getY()+0x40)&65535)>=0x80) return false;
        // ChildObjDat_7A1BC: ten repeated links, first failure ends the suffix.
        for(int i=0;i<10;i++) if(spawnPlatform(DEBRIS,2*i,0,0,null)==null) break;
        expire(); services().playSfx(com.openggf.game.sonic3k.audio.Sonic3kSfx.CLANK.id);
        return true;
    }

    private void initializeDebris() {
        initialized=true;
        try {
            var rom=services().rom();
            animationFrame=rom.readBytes(0x7A0BA+(subtype>>>1),1)[0]&255;
            byte[] offset=rom.readBytes(0x7A0C4+subtype,2);
            motion.x+=offset[0]; motion.y+=offset[1];
            motion.xVel=(short)rom.read16BitAddr(0x7A0D8+subtype*2);
            motion.yVel=(short)rom.read16BitAddr(0x7A0DA+subtype*2);
        } catch(java.io.IOException failure) { throw new java.io.UncheckedIOException("LRZ3 debris tables",failure); }
    }

    private void generator() {
        if(!generatorActive) {
            // sub_7A040 uses an unsigned $140 x $E0 activation rectangle.
            // Keep the ROM's 320px gameplay trigger even on wider displays:
            // widening it starts these invisible generators early, changing
            // platform heights before Sonic reaches them. Rendering/culling
            // still uses the actual viewport; activation is not visibility.
            var camera=services().camera();
            if(((getX()-camera.getX())&65535)>=0x140
                    || ((getY()-camera.getY())&65535)>=0xE0) return;
            if(subtype!=0) {
                spawnPlatform(RISING,subtype,0,0x4FF,null); expire(); return;
            }
            generatorActive=true;
            spawnPlatform(RISING,subtype,0xC0,0x37F,null);
            // Init falls through: the zero timer immediately makes a second platform.
        }
        if(state().bossSlot()>=0) { expire(); return; }
        timer=(short)(timer-1);
        if(timer<0) { timer=0x17F; spawnPlatform(RISING,subtype,0,0x4FF,null); }
    }

    private LrzBossPlatformObjectInstance spawnPlatform(int childMode,int childSubtype,int dy,int lifetime,
                                                        LrzBossPlatformObjectInstance owner) {
        var child=spawnChild(()-> {
            var result=new LrzBossPlatformObjectInstance(new ObjectSpawn(getX(),getY()+dy,0,childSubtype,0,false,0));
            result.mode=childMode; result.timer=lifetime; result.parent=owner; return result;
        });
        return child.getSlotIndex()<0 || child.isDestroyed() ? null : child;
    }
    private void animate(int delay,int firstFrame) {
        if((byte)--animationTimer<0) {
            animationTimer=delay; animationFrame=animationFrame==0 || animationFrame==firstFrame ? firstFrame+1 : firstFrame;
        }
    }
    private void expire() {
        for(var player:services().playerQuery().playersFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS))
            services().objectManager().releaseRidingObject(player,this);
        nativeStatus7=true; ObjectLifetimeOps.expireDynamic(this);
    }
    @Override public int getX() { return (short)motion.x; }
    @Override public int getY() { return (short)motion.y; }
    @Override public boolean isSolidFor(PlayableEntity player) { return mode!=UNDERSIDE && mode!=DEBRIS && (mode!=PLACED || subtype>=3); }
    @Override public SolidRoutineProfile getSolidRoutineProfile() {
        return SolidRoutineProfile.fromProvider(this);
    }
    // sub_7A064 -> SolidObjectTop: d3=$0D owns the landing plane; d2=$10
    // belongs to full-solid overlap. loc_1E45A accepts strictly negative overlap.
    @Override public boolean usesGroundHalfHeightForTopSolidContact() { return true; }
    // The existing direct-top contract is carried by SlopedSolidProvider; null
    // slope data retains a flat plane, as in the lava surface's flat dispatch.
    @Override public byte[] getSlopeData() { return null; }
    @Override public boolean isSlopeFlipped() { return false; }
    @Override public Integer getDirectTopLandingOverlapLimit() { return 17; }
    @Override public boolean rejectsZeroDistanceTopSolidLanding() { return true; }
    @Override public boolean usesPlatformObjectLandingSnap() { return false; }
    @Override public boolean usesStickyContactBuffer() { return false; }
    @Override public boolean isTopSolidOnly() { return !(mode==PLACED && subtype==4); }
    @Override public SolidObjectParams getSolidParams() {
        return subtype==4 && mode==PLACED ? SolidObjectParams.of(0x2B,0x18,0x19) : SolidObjectParams.of(0x23,0x10,0xD);
    }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
    @Override public int getOnScreenHalfWidth() { return mode==DEBRIS ? 0xC : mode==UNDERSIDE || subtype==4 ? 0x20 : 0x18; }
    @Override public int getOnScreenHalfHeight() { return mode==DEBRIS ? 0xC : mode==UNDERSIDE ? 8 : subtype==4 ? 0x18 : 0x10; }
    @Override public int getPriorityBucket() {
        return RenderPriority.fromS3kWord(mode==DEBRIS ? 0x80 : mode==UNDERSIDE ? 0x100 : priorityWord);
    }
    @Override public boolean isHighPriority() { return mode==DEBRIS; } // ObjDat3_7A178 FixBugs=0, not fixed palette 2/high priority.
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if(mode==PLACED && subtype<3 || !visible) return;
        if(mode==DEBRIS) {
            var debris=getRenderer(Sonic3kObjectArtKeys.LRZ3_PLATFORM_DEBRIS);
            if(debris!=null && debris.isReady()) debris.drawFrameIndexForcedPriority(
                    animationFrame,getX(),getY(),false,false,3,true);
            return;
        }
        var renderer=getRenderer(Sonic3kObjectArtKeys.LRZ3_PLATFORM);
        if(renderer!=null && renderer.isReady()) renderer.drawFrameIndexWithPaletteBase(
                mode==UNDERSIDE || subtype==4 ? animationFrame : 1,getX(),getY(),false,false,mode==UNDERSIDE ? 2 : 3);
    }
    @Override public int romObjectCodePointerHighWord() { return getRomCodePointer() >>> 16; }
    public int getRomCodePointer() {
        return switch(mode) {
            case DEBRIS -> initialized ? 0x85102 : 0x79E9C;
            case RISING -> initialized ? 0x79D44 : 0x79D28;
            case FALLING -> 0x79D6E;
            case UNDERSIDE -> 0x79E7C;
            case APPROACH -> 0x79DC2;
            case FLOATING -> initialized ? 0x79DEA : 0x79E5A;
            default -> generatorActive ? 0x79C9E : subtype==4 && initialized ? 0x79D08 : subtype==3 && initialized ? 0x79CEE : 0x79C52;
        };
    }
}
