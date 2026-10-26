package com.openggf.game.sonic3k.competition;

import com.openggf.data.RomByteReader;
import com.openggf.data.RomManager;
import com.openggf.game.GameModule;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.DynamicObjectRecreateContext;
import com.openggf.level.objects.NativeSpriteSubmissionAccess;
import com.openggf.level.objects.NativeSpriteSubmissionSource;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Obj_DashDust2P branches driven by the locked-on animation bytes and native fixed-SST order. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kMiniatureDust {
    private RomByteReader reader;
    @BeforeEach void loadRom() throws Exception {reader=RomByteReader.fromRom(RomManager.getInstance().getRom());}

    @Test void actualSpawnInstructionsAndAllFourAnimationScriptsSelectTheMiniatureAssets() {
        assertEquals(0x18F86,reader.readU32BE(0x6BB2));assertEquals(0xCC54,reader.readU16BE(0x6BB6));
        assertEquals(0x18F86,reader.readU32BE(0x6BD0));assertEquals(0xCC9E,reader.readU16BE(0x6BD4));
        assertEquals(0x1910C,reader.readU32BE(0x18FA2));assertEquals(0x7E0,reader.readU16BE(0x18FBC));
        assertEquals(0x190F2,reader.readU32BE(0x19048));assertEquals(0xC,reader.readU16BE(0x190A6));
        for(int address:new int[]{0x1098C,0x13560}) {
            assertEquals(0x1D7C,reader.readU16BE(address));assertEquals(2,reader.readU16BE(address+2));assertEquals(0x20,reader.readU16BE(address+4));
        }
        for(int address:new int[]{0x10A04,0x135DC}) {
            assertEquals(0x1D7C,reader.readU16BE(address));assertEquals(0,reader.readU16BE(address+2));assertEquals(0x20,reader.readU16BE(address+4));
        }
        for(int address:new int[]{0x11474,0x114FA,0x14C9E,0x14D24}) {
            assertEquals(0x1D7C,reader.readU16BE(address));assertEquals(6,reader.readU16BE(address+2));assertEquals(5,reader.readU16BE(address+4));
            assertEquals(0x1D7C,reader.readU16BE(address+6));assertEquals(0x15,reader.readU16BE(address+8));assertEquals(0x22,reader.readU16BE(address+10));
        }
        var scripts=S3kSpriteDataLoader.loadAnimationSet(reader,0x190F2,4);
        assertEquals(31,scripts.getScript(0).delay());assertEquals(List.of(0),scripts.getScript(0).frames());
        assertEquals(3,scripts.getScript(1).delay());assertEquals(0xFD,scripts.getScript(1).flatByteAt(1));
        assertEquals(1,scripts.getScript(2).delay());assertEquals(List.of(1,2,3,4),scripts.getScript(2).frames());
        assertEquals(5,scripts.getScript(3).delay());assertEquals(List.of(5,6,7),scripts.getScript(3).frames());
        assertEquals(0xFC,scripts.getScript(3).flatByteAt(3));
        assertEquals(8,S3kSpriteDataLoader.loadMappingFrames(reader,0x1910C,8).size());
    }

    @Test void bothFixedOwnersInitializeAndPublishAnEmptyFrameWithoutFollowingIdleBodies() {
        for(int slot=0;slot<2;slot++) {
            var world=world();var dust=dust(slot);var parent=world.players.get(slot);
            parent.setCentre(0x632,0x245);parent.setNativeRenderFlags(0x9F);parent.setHighPriority(true);
            assertNotEquals(0,dust.getLiveObjectId());assertEquals(0,dust.getOnScreenHalfWidth());
            dust.refreshNativeVisibility(true);dust.updateNative(world.session,world);
            assertEquals(98+slot,dust.nativeFixedSlot());assertEquals(2,dust.routine());
            assertEquals(0,dust.getX());assertEquals(0,dust.getY());assertEquals(0,dust.mappingFrame());
            assertEquals(0x84,dust.nativeRenderFlags());assertEquals(0x7E0,dust.nativeArtWord());
            assertEquals(16,dust.getOnScreenHalfWidth());assertEquals(0,dust.getOnScreenHalfHeight());
            assertEquals(1,dust.getPriorityBucket());assertFalse(dust.isHighPriority());
            assertTrue(dust.nativeDrawPublished());assertFalse(com.openggf.level.objects.TouchResponseProvider.class.isAssignableFrom(S3kMiniatureDust.class));
            assertTrue(NativeSpriteSubmissionSource.class.isAssignableFrom(S3kMiniatureDust.class));
        }
    }

    @Test void chargeFollowsOnlyItsOwnParentAndCopiesHighPriorityOnlyWhenPreviousAnimationIsZero() {
        for(int slot=0;slot<2;slot++) {
            var world=world();var dust=dust(slot);var parent=world.players.get(slot);
            parent.setSpinDashFlag(1);parent.setStatus(0xFF);parent.setHighPriority(true);dust.setAnimation(2);
            for(int tick=1;tick<=17;tick++) {
                parent.setCentre(0x601+tick,0x221+tick);dust.updateNative(world.session,world);
                assertEquals(1+((tick-1)/2)%4,dust.mappingFrame());
                assertEquals(parent.centreX(),dust.getX());assertEquals(parent.centreY(),dust.getY());
                assertEquals(1,dust.nativeStatus());assertEquals(5,dust.nativeRenderFlags());
                assertTrue(dust.isHighPriority());parent.setHighPriority(false);
            }
            dust.setAnimation(0);dust.updateNative(world.session,world);
            assertEquals(0,dust.previousAnimation());assertTrue(dust.isHighPriority());
            dust.setAnimation(2);parent.setFacingLeft(false);dust.updateNative(world.session,world);
            assertEquals(4,dust.nativeRenderFlags());assertFalse(dust.isHighPriority());
        }
    }

    @Test void chargeGatesReturnWithoutDrawingOrMovingThenTheFollowingIdlePassDraws() {
        for(int gate=0;gate<3;gate++) {
            var world=world();var dust=dust(0);var parent=world.players.getFirst();
            parent.setSpinDashFlag(1);dust.setAnimation(2);dust.updateNative(world.session,world);
            int x=dust.getX(),y=dust.getY(),frame=dust.mappingFrame(),timer=dust.animationTimer();
            parent.setCentre(0x777,0x333);
            if(gate==0)parent.setAirLeft(11);if(gate==1)parent.setRoutine(4);if(gate==2)parent.setSpinDashFlag(0);
            dust.updateNative(world.session,world);
            assertEquals(0,dust.animation());assertFalse(dust.nativeDrawPublished());
            assertEquals(x,dust.getX());assertEquals(y,dust.getY());assertEquals(frame,dust.mappingFrame());assertEquals(timer,dust.animationTimer());
            dust.updateNative(world.session,world);assertTrue(dust.nativeDrawPublished());assertEquals(0,dust.mappingFrame());
        }
    }

    @Test void nativePlayerWritesReachTheCurrentFixedOwnerOnlyAtStartReleaseAndSkidBranches() {
        for(var character:S3kCompetitionPlayer.Character.values())for(int slot=0;slot<2;slot++)for(int air:new int[]{11,12})for(int direction:new int[]{-1,1}) {
            var body=new S3kCompetitionPlayer(slot,character,0x600,0x200);body.setAirLeft(air);body.setAnimation(8);
            var writes=new ArrayList<Integer>();var current=new S3kMiniatureDust[]{dust(slot)};
            body.setNativeDustWrites(new S3kCompetitionPlayer.NativeDustWrites(){
                @Override public void setAnimation(int value){writes.add(value);current[0].setAnimation(value);}
                @Override public void startSkid(){writes.add(6);current[0].startSkid();}
            });
            body.tick(new S3kCompetitionPlayer.Input(S3kCompetitionPlayer.DOWN,S3kCompetitionPlayer.JUMP),flat(),bounds(),0);
            assertEquals(air>=12?List.of(2):List.of(),writes);
            body.tick(new S3kCompetitionPlayer.Input(S3kCompetitionPlayer.DOWN,S3kCompetitionPlayer.JUMP),flat(),bounds(),0);
            assertEquals(air>=12?List.of(2):List.of(),writes,"recharging does not rewrite the dust animation");
            var retired=current[0];current[0]=dust(slot);body.setAirLeft(0);
            body.tick(S3kCompetitionPlayer.Input.NONE,flat(),bounds(),1);
            assertEquals(air>=12?List.of(2,0):List.of(0),writes);
            assertEquals(air>=12?2:0,retired.animation());assertEquals(0,current[0].animation());
            body.setRolling(false);body.setAirborne(false);body.setAnimation(0);body.setAirLeft(air);body.setGroundVelocity(-direction*0x600);
            body.tick(new S3kCompetitionPlayer.Input(direction<0?S3kCompetitionPlayer.LEFT:S3kCompetitionPlayer.RIGHT,0),flat(),bounds(),2);
            assertEquals(0xD,body.animation());assertEquals(air>=12?6:0,current[0].routine());
            assertEquals(air>=12?0x15:0,current[0].mappingFrame());
        }
    }

    @Test void fixedSkidSpawnsEveryFourPassesWithUnscaledOffsetAndNoCreationFrameDraw() {
        for(int slot=0;slot<2;slot++) {
            var world=world();var dust=dust(slot);var parent=world.players.get(slot);
            dust.updateNative(world.session,world);dust.refreshNativeVisibility(true);parent.setHighPriority(true);parent.setAnimation(0xD);
            dust.startSkid();
            for(int tick=0;tick<9;tick++) {
                parent.setCentre(0x610+tick,0x220+tick);dust.updateNative(world.session,world);
                assertEquals(1+tick/4,world.objects.size());assertFalse(dust.nativeDrawPublished());assertEquals(0,dust.mappingFrame());
            }
            var child=assertInstanceOf(S3kMiniatureDust.class,world.objects.getFirst());
            assertEquals(slot,child.parentSlot());assertEquals(0x610,child.getX());assertEquals(0x22C,child.getY());
            assertEquals(4,child.getOnScreenHalfWidth());assertEquals(0,child.getOnScreenHalfHeight());assertEquals(1,child.getPriorityBucket());
            assertEquals(0x87E0,child.nativeArtWord());assertEquals(0,child.nativeStatus());assertEquals(0x84,child.nativeRenderFlags());
            assertFalse(child.nativeDrawPublished());assertEquals(0,child.mappingFrame());
            child.updateNative(world.session,world);assertEquals(5,child.mappingFrame());assertTrue(child.nativeDrawPublished());
        }
    }

    @Test void fullRamStillConsumesTheFourPassSkidTimerAndExitReturnsWithoutDrawing() {
        var world=world();var dust=dust(0);var parent=world.players.getFirst();dust.updateNative(world.session,world);
        parent.setAnimation(0xD);Arrays.fill(world.occupied,true);dust.startSkid();dust.updateNative(world.session,world);
        assertEquals(3,dust.spawnTimer());assertTrue(world.objects.isEmpty());world.occupied[4]=false;
        for(int tick=0;tick<3;tick++)dust.updateNative(world.session,world);
        assertTrue(world.objects.isEmpty());dust.updateNative(world.session,world);assertEquals(1,world.objects.size());
        parent.setAnimation(0);dust.updateNative(world.session,world);assertEquals(2,dust.routine());assertEquals(0,dust.spawnTimer());
        assertFalse(dust.nativeDrawPublished());dust.updateNative(world.session,world);assertTrue(dust.nativeDrawPublished());
    }

    @Test void puffCompletesItsRomSparkleThenDeletesOnTheNextSstDispatchAndLowAirCancelsWithoutDraw() {
        var world=world();var dust=dust(0);var parent=world.players.getFirst();dust.updateNative(world.session,world);
        parent.setAnimation(0xD);dust.startSkid();dust.updateNative(world.session,world);
        var child=assertInstanceOf(S3kMiniatureDust.class,world.objects.getFirst());
        for(int tick=1;tick<=18;tick++){child.updateNative(world.session,world);assertEquals(5+(tick-1)/6,child.mappingFrame());}
        child.updateNative(world.session,world);assertEquals(4,child.routine());assertFalse(child.isDestroyed());assertTrue(child.nativeDrawPublished());
        child.updateNative(world.session,world);assertTrue(child.isDestroyed());assertFalse(child.nativeDrawPublished());
        for(int tick=0;tick<4;tick++)dust.updateNative(world.session,world);
        child=assertInstanceOf(S3kMiniatureDust.class,world.objects.getLast());parent.setAirLeft(11);child.updateNative(world.session,world);
        assertEquals(0,child.animation());assertFalse(child.nativeDrawPublished());
        child.updateNative(world.session,world);assertEquals(2,child.routine());assertEquals(0,child.mappingFrame());assertTrue(child.nativeDrawPublished());
    }

    @Test void bothDustGraphsRecreateCompactlyAndReplaySeventeenPassesWithCapturedAirAndCadence() {
        var world=world();var owners=List.of(dust(0),dust(1));
        for(int slot=0;slot<2;slot++){owners.get(slot).updateNative(world.session,world);world.players.get(slot).setAnimation(0xD);owners.get(slot).startSkid();}
        dustStep(world,owners,5);
        world.players.get(1).setAirLeft(0xE1);
        var sessionBefore=world.session.capture();var ownerStates=owners.stream().map(AbstractObjectInstance::captureRewindState).toList();
        var objectsBefore=world.objects.stream().map(AbstractObjectInstance::captureRewindState).toList();
        var spawns=world.objects.stream().map(AbstractObjectInstance::getSpawn).toList();var occupiedBefore=world.occupied.clone();
        dustStep(world,owners,17);var forward=world.objects.stream().map(AbstractObjectInstance::captureRewindState).toList();
        var ownerForward=owners.stream().map(AbstractObjectInstance::captureRewindState).toList();var sessionForward=world.session.capture();
        world.session.restore(sessionBefore);world.objects.clear();System.arraycopy(occupiedBefore,0,world.occupied,0,occupiedBefore.length);
        var restoredOwners=new ArrayList<S3kMiniatureDust>();
        for(int i=0;i<2;i++)restoredOwners.add(recreate(owners.get(i).getSpawn(),ownerStates.get(i)));
        for(int i=0;i<objectsBefore.size();i++)world.objects.add(recreate(spawns.get(i),objectsBefore.get(i)));
        dustStep(world,restoredOwners,17);
        assertEquals(forward,world.objects.stream().map(AbstractObjectInstance::captureRewindState).toList());
        assertEquals(ownerForward,restoredOwners.stream().map(AbstractObjectInstance::captureRewindState).toList());
        assertEquals(sessionForward,world.session.capture());assertEquals(0xE1,world.players.get(1).airLeft());
    }

    @Test void canonicalDispatchRelinksTheCurrentRosterAndPublishesOnlyActualDustDrawCalls() {
        var fixture=world();var module=mock(GameModule.class);when(module.getIdentifier()).thenReturn("native-dust-test");
        when(module.getGameService(WorldSessionPolicyProvider.class)).thenReturn(ignored->fixture.session);
        var world=new WorldSession(module);
        var services=new StubObjectServices(){@Override public WorldSession worldSession(){return world;}}.withIsolatedObjectManager();
        var manager=services.objectManager();var dust=dust(1);manager.addDynamicObjectAtSlot(dust,99);
        var retired=fixture.players.get(1);retired.setSpinDashFlag(1);dust.setAnimation(2);dust.update(0,null);
        assertEquals(List.of(new NativeSpriteSubmissionAccess.Admission(99,1)),NativeSpriteSubmissionAccess.admissions(manager));
        var before=fixture.session.capture();var fresh1=S3kCompetitionPlayer.recreate(before.players().get(0).body());
        var fresh2=S3kCompetitionPlayer.recreate(before.players().get(1).body());fresh2.setCentre(0x672,0x240);
        var p1=mock(S3kMiniatureSprite.class);when(p1.nativeSlot()).thenReturn(0);when(p1.nativePlayer()).thenReturn(fresh1);
        var p2=mock(S3kMiniatureSprite.class);when(p2.nativeSlot()).thenReturn(1);when(p2.nativePlayer()).thenReturn(fresh2);
        var sprites=mock(SpriteManager.class);when(sprites.getAllSprites()).thenReturn(List.of(p2,p1));fixture.session.bindRoster(sprites);
        retired.setCentre(0x777,0x333);dust.update(1,null);assertEquals(0x672,dust.getX());assertEquals(0x240,dust.getY());
        fresh2.setAirLeft(11);dust.update(2,null);assertFalse(dust.nativeDrawPublished());
        assertEquals(2,NativeSpriteSubmissionAccess.admissions(manager).size(),"gate rejection does not enqueue another native pointer");
    }

    private CompetitionTestWorld world(){
        var world=new CompetitionTestWorld(S3kCompetitionSession.Mode.MATCH_RACE);
        world.animationSets=Map.of(S3kMiniatureDust.ART_KEY,S3kSpriteDataLoader.loadAnimationSet(reader,0x190F2,4));return world;
    }
    private static S3kMiniatureDust dust(int slot){var dust=new S3kMiniatureDust(slot);dust.setSlotIndex(98+slot);return dust;}
    private static S3kMiniatureDust recreate(com.openggf.level.objects.ObjectSpawn spawn,com.openggf.level.objects.PerObjectRewindSnapshot state){
        var object=ObjectRewindDynamicCodecs.genericRecreate(new DynamicObjectEntry(S3kMiniatureDust.class.getName(),spawn,state.slotIndex(),state),new DynamicObjectRecreateContext(mock(ObjectManager.class)));
        assertNotNull(object);assertNotNull(state.compactGenericState());var dust=assertInstanceOf(S3kMiniatureDust.class,object);dust.restoreRewindState(state);return dust;
    }
    private static void dustStep(CompetitionTestWorld world,List<S3kMiniatureDust> owners,int ticks){
        for(int tick=0;tick<ticks;tick++){
            for(var object:List.copyOf(world.objects))if(object instanceof S3kMiniatureDust dust && !dust.isDestroyed())dust.updateNative(world.session,world);
            for(var owner:owners)owner.updateNative(world.session,world);
        }
    }
    private static S3kCompetitionPlayer.Bounds bounds(){return new S3kCompetitionPlayer.Bounds(0x3FF,0x7FF,false,0);}
    private static S3kCompetitionPlayer.Terrain flat(){return new S3kCompetitionPlayer.Terrain(){
        @Override public boolean canJump(S3kCompetitionPlayer p,int clearance){return true;}
        @Override public void resolveGroundWall(S3kCompetitionPlayer p){}
        @Override public void followGround(S3kCompetitionPlayer p){}
        @Override public void resolveAir(S3kCompetitionPlayer p){}
        @Override public void resolveDeath(S3kCompetitionPlayer p){throw new AssertionError("Dust fixture must not die");}
    };}
}
