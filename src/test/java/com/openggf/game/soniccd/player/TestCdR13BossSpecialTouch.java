package com.openggf.game.soniccd.player;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
/** Tests first; all Java UNRUN. Actual authenticated C/D collision data plus labelled constructed
* pure component values/public-pool ingress. No World/Frame/native-trace or private snapshot authority. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR13BossSpecialTouch {
    private List<CdR13FutureAssets> originals;
    @Test void exactFourSpecialSelectorsAreSeparateFromTheSevenLegacyFlagHandlers(){
        for(int raw=0;raw<256;raw++){
            assertEquals(raw>=0xFC,CdPlayerTouchResponse.ownsBossSpecialCollision(raw));
            if(raw>=0xFC)assertFalse(CdPlayerTouchResponse.ownsSpecialFlagCollision(raw));
        }
        assertThrows(IllegalArgumentException.class,()->CdPlayerTouchResponse.ownsBossSpecialCollision(256));
    }
    @Test void bothActualProfilesUseTheirOwnCompleteCollisionTablesAndRealScanner()throws Exception{
        for(var a:actual()){
            var data=CdR13PlayerCollisionAssets.fromAssets(a);
            assertTrue(data.ownsAssets(a));
            var response=CdPlayerTouchResponse.fromAct3FutureAssets(data);
            for(int raw=0xFC;raw<=0xFF;raw++){
                var h=new Input(data,raw,2,0x2A);
                var out=response.respondBossSpecial(h.selected,actor(2,0,0x200,0x400,0),player(0,0,0,0),world(0),1,h.cut);
                assertInstanceOf(CdPlayerTouchResponse.BossSpecialCompleted.class,out);
                assertEquals(0,h.pool.state(50).unsignedByte(COLLISION_TYPE)^raw,"pure owner never publishes");
                assertEquals(5,out.values().changedSlots().getFirst().unsignedByte(COLLISION_STATUS));
            }
        }
    }
    @Test void onlyActualBossFightByteOneEntersTheBranch()throws Exception{
        var h=input(0xFC,2,0x2A);
        for(int flag:new int[]{
            0,2,255
        }){
            var a=actor(2,0,1,2,0);
            var p=player(0,0,0,0);
            var w=world(10);
            var result=(CdPlayerTouchResponse.BossSpecialCompleted)h.owner.respondBossSpecial(h.selected,a,p,w,flag,h.cut);
            assertTrue(result.commonResult().isEmpty());
            assertEquals(a,result.values().actor());
            assertEquals(p,result.values().player());
            assertEquals(w,result.values().world());
            assertTrue(result.values().changedSlots().isEmpty());
            assertTrue(result.values().calls().isEmpty());
        }
        assertThrows(IllegalArgumentException.class,()->h.owner.respondBossSpecial(h.selected,actor(2,0,1,2,0),player(0,0,0,0),world(0),256,h.cut));
    }
    @Test void damageReusesOriginalNegateHalfRecoilThenAddsThreeClearsTypeAndFlagsCollision()throws Exception{
        for(int count:new int[]{
            1,2,255
        }){
            var h=input(0xFD,count,0x2A);
            var out=(CdPlayerTouchResponse.BossSpecialCompleted)h.owner.respondBossSpecial(h.selected,actor(2,0,-32768,32767,0),player(0,0,0,0),world(0),1,h.cut);
            var s=out.values().changedSlots().getFirst();
            assertEquals((count+3)&255,s.unsignedByte(COLLISION_STATUS));
            assertEquals(0,s.unsignedByte(COLLISION_TYPE));
            assertEquals(count==1?128:0,s.flags());
            assertEquals(-16384,out.values().actor().motion().xVelocity());
            assertEquals(-16384,out.values().actor().motion().yVelocity());
            assertEquals(CdPlayerTouchResponse.Exit.DAMAGE_ENEMY,out.commonResult().orElseThrow().exit());
            assertTrue(out.values().calls().isEmpty());
        }
    }
    @Test void zeroStatusUsesOriginalKillScoreChainPointsSoundSubtypeAndBounce()throws Exception{
        var h=input(0xFE,0,0x2D);
        var a=actor(2,0,3,0x200,0);
        var p=player(0,0,0,0);
        var out=(CdPlayerTouchResponse.BossSpecialCompleted)h.owner.respondBossSpecial(h.selected,a,p,world(0),1,h.cut);
        var s=out.values().changedSlots().getFirst();
        assertEquals(0x18,s.id());
        assertEquals(0,s.routine());
        assertEquals(1,s.subtype());
        assertEquals(1,s.unsignedByte(COLLISION_STATUS));
        assertEquals(0,s.unsignedByte(COLLISION_TYPE));
        assertEquals(2,out.values().player().scoreChain());
        assertEquals(10,out.values().world().score());
        assertInstanceOf(CdPlayerTouchResponse.AddPoints.class,out.values().calls().getFirst());
        assertInstanceOf(CdPlayerTouchResponse.PlayFm.class,out.values().calls().getLast());
        assertEquals(0x100,out.values().actor().motion().yVelocity());
    }
    @Test void oddEnemyPointsStopExposesActualPriorFlagsChainAndSourceFrameWithoutInventingAnExit()throws Exception{
        var h=input(0xFC,0,0x2A);
        var a=actor(2,0,1,2,0);
        var out=(CdPlayerTouchResponse.BossSpecialStopped)h.owner.respondBossSpecial(h.selected,a,player(0,0,0,1),world(0),1,h.cut);
        assertInstanceOf(CdPlayerTouchResponse.UnsupportedResponse.class,out.originalException());
        assertEquals(CdPlayerTouchResponse.UnsupportedBoundary.ODD_ENEMY_POINTS_ADDRESS,((CdPlayerTouchResponse.UnsupportedResponse)out.originalException()).boundary());
        assertTrue(out.commonResult().isEmpty());
        assertEquals(3,out.values().player().scoreChain());
        var s=out.values().changedSlots().getFirst();
        assertEquals(128,s.flags());
        assertEquals(1,s.word(EXPLOSION_POINTS_FRAME));
        assertEquals(0xFC,s.unsignedByte(COLLISION_TYPE));
        assertEquals(a,out.values().actor());
        assertTrue(out.values().calls().isEmpty());
        assertEquals(0,h.pool.state(50).flags());
    }
    @Test void nonRollingHurtKeepsSelectedA2ButSpecialTailTargetsActualNewLostRingSlot()throws Exception{
        var h=input(0xFF,2,0x32);
        h.pool.write(h.pool.state(32).moveWord(X,0x7000).moveByte(COLLISION_STATUS,253).moveByte(COLLISION_TYPE,0));
        h.pool.write(h.pool.state(50).moveWord(X,0x201));
        h.refresh();
        var out=(CdPlayerTouchResponse.BossSpecialCompleted)h.owner.respondBossSpecial(h.selected,actor(0,2,0,0,0),player(0,0,0,0),world(10),1,h.cut);
        var s=out.values().changedSlots().getFirst();
        assertEquals(32,s.identity().wholeIndex());
        assertEquals(0x11,s.id());
        assertEquals(1,s.unsignedByte(COLLISION_STATUS));
        assertEquals(0x200,s.centreX());
        assertEquals(-0x200,out.values().actor().motion().xVelocity(),"unsigned compare reads selected A2 X, not the new ring slot X");
        assertTrue(out.values().changedSlots().stream().noneMatch(x->x.identity().wholeIndex()==50));
        assertEquals(CdPlayerTouchResponse.Exit.HURT,out.commonResult().orElseThrow().exit());
    }
    @Test void idZeroSelectedA2CanBeTheAllocatedRingBeforeTheUnsignedRecoilCompare()throws Exception{
        var h=input(0xFC,2,0);
        for(int w=32;w<50;w++)h.pool.write(h.pool.state(w).moveByte(ID,1));
        h.pool.write(h.pool.state(50).moveWord(X,0x201));
        h.refresh();
        var out=(CdPlayerTouchResponse.BossSpecialCompleted)h.owner.respondBossSpecial(h.selected,actor(0,2,0,0,0),player(0,0,0,0),world(1),1,h.cut);
        assertEquals(0x200,out.values().actor().motion().xVelocity(),"allocated A2 now has player X; equality flips recoil");
        var s=out.values().changedSlots().getFirst();
        assertEquals(50,s.identity().wholeIndex());
        assertEquals(0x11,s.id());
        assertEquals(3,s.unsignedByte(COLLISION_STATUS));
        assertEquals(0,s.unsignedByte(COLLISION_TYPE));
    }
    @Test void exhaustedHurtAllocationKeepsCompletedCommonPrefixBeforeConsumedReturnTargetStop()throws Exception{
        var h=input(0xFC,2,0x2A);
        for(int n=32;n<128;n++)if(n!=50)h.pool.write(h.pool.state(n).moveByte(ID,1));
        h.refresh();
        var out=(CdPlayerTouchResponse.BossSpecialStopped)h.owner.respondBossSpecial(h.selected,actor(0,2,0,0,0),player(0,0,0,0),world(10),1,h.cut);
        assertEquals(128,((CdPlayerTouchResponse.UnownedBossSpecialTarget)out.originalException()).wholeIndex());
        assertEquals(CdPlayerTouchResponse.Exit.HURT,out.commonResult().orElseThrow().exit());
        assertEquals(120,out.values().actor().hurt());
        assertTrue(out.values().changedSlots().isEmpty());
        assertEquals(0xFC,h.pool.state(50).unsignedByte(COLLISION_TYPE));
        assertInstanceOf(CdPlayerTouchResponse.FindObjSlot.class,out.values().calls().getFirst());
    }
    @Test void shieldOrNoHurtAndDebugKillPathsKeepTheirRealCommonOutcomeAndSelectedTail()throws Exception{
        var h=input(0xFC,2,0x2A);
        for(int mode=0;mode<3;mode++){
            var a=actor(0,2,0,0,mode==1?1:0);
            var p=player(0,mode==0?1:0,mode==2?1:0,0);
            var out=(CdPlayerTouchResponse.BossSpecialCompleted)h.owner.respondBossSpecial(h.selected,a,p,world(0),1,h.cut);
            assertEquals(3,out.values().changedSlots().getFirst().unsignedByte(COLLISION_STATUS));
            assertEquals(0,out.values().changedSlots().getFirst().unsignedByte(COLLISION_TYPE));
            assertEquals(mode==0?CdPlayerTouchResponse.Exit.HURT:mode==1?CdPlayerTouchResponse.Exit.NO_HURT:CdPlayerTouchResponse.Exit.KILL_PLAYER_DEBUG_NO_WRITE,out.commonResult().orElseThrow().exit());
        }
    }
    @Test void timeWarpAndInvincibleSelectOriginalEnemyDamageRatherThanTheHurtGate()throws Exception{
        var h=input(0xFC,2,0x2A);
        for(int mode=0;mode<2;mode++){
            var p=mode==0?player(1,0,0,0):new CdPlayerTouchResponse.PlayerGlobals(0,1,0,1,0,0,0,0);
            var out=(CdPlayerTouchResponse.BossSpecialCompleted)h.owner.respondBossSpecial(h.selected,actor(0,2,0x100,-0x200,0),p,world(10),1,h.cut);
            assertEquals(CdPlayerTouchResponse.Exit.DAMAGE_ENEMY,out.commonResult().orElseThrow().exit());
            assertEquals(5,out.values().changedSlots().getFirst().unsignedByte(COLLISION_STATUS));
            assertTrue(out.values().calls().isEmpty());
        }
    }
    @Test void ordinarySpecialAndEnemyControlsKeepOldAdmissionAndOutcomes()throws Exception{
        var h=input(0xDF,2,0x2A);
        assertThrows(IllegalArgumentException.class,()->h.owner.respondBossSpecial(h.selected,actor(2,0,1,2,0),player(0,0,0,0),world(0),1,h.cut));
        var legacy=h.owner.respond(h.selected,actor(0,0,1,2,0),player(0,0,0,0),world(0),h.cut);
        assertEquals(CdPlayerTouchResponse.Exit.SPECIAL_FLAG_INCREMENT,legacy.exit());
        var boss=input(0xFC,2,0x2A);
        assertThrows(IllegalArgumentException.class,()->boss.owner.respond(boss.selected,actor(2,0,1,2,0),player(0,0,0,0),world(0),boss.cut));
    }
    @Test void completeSameOwnerSourceMismatchAndWrongGroupRejectBeforeAnyPureResponse()throws Exception{
        var h=input(0xFC,2,0x2A);
        h.pool.write(h.pool.state(50).moveByte(SUBTYPE,1));
        assertThrows(IllegalArgumentException.class,()->h.owner.respondBossSpecial(h.selected,actor(2,0,1,2,0),player(0,0,0,0),world(0),1,h.pool.capture()));
        var other=input(0x3C,2,0x2A);
        assertEquals(CdPlayerTouchResponse.Exit.DAMAGE_ENEMY,other.owner.respond(other.selected,actor(2,0,1,2,0),player(0,0,0,0),world(0),other.cut).exit());
        assertThrows(IllegalArgumentException.class,()->other.owner.respondBossSpecial(other.selected,actor(2,0,1,2,0),player(0,0,0,0),world(0),1,other.cut));
        assertThrows(IllegalArgumentException.class,()->h.owner.respondBossSpecial(h.selected,actor(2,0,1,2,0),player(0,0,0,0),world(0),1,new CdObjectPool().capture()));
    }
    @Test void completedValuesAreImmutableAndReadingThemNeverPublishesOrGrantsScope()throws Exception{
        var h=input(0xFC,2,0x2A);
        var before=h.pool.capture();
        var out=h.owner.respondBossSpecial(h.selected,actor(2,0,1,2,0),player(0,0,0,0),world(0),1,h.cut);
        assertThrows(UnsupportedOperationException.class,()->out.values().changedSlots().clear());
        assertThrows(UnsupportedOperationException.class,()->out.values().calls().clear());
        assertEquals(before.slots(),h.pool.capture().slots());
    }
    @Test void cappedChainUsesOriginalThousandPointsAndOneUpBeforeDestroySound()throws Exception{
        var h=input(0xFC,0,0x2A);
        var world=new CdPlayerTouchResponse.WorldGlobals(0,3,0,4999,5000,0);
        var out=(CdPlayerTouchResponse.BossSpecialCompleted)h.owner.respondBossSpecial(h.selected,actor(2,0,0,0,0),player(0,0,0,30),world,1,h.cut);
        assertEquals(32,out.values().player().scoreChain());
        assertEquals(5999,out.values().world().score());
        assertEquals(4,out.values().world().lives());
        assertEquals(10000,out.values().world().nextLifeScore());
        assertEquals(10,out.values().changedSlots().getFirst().word(EXPLOSION_POINTS_FRAME));
        assertEquals(new CdPlayerTouchResponse.AddPoints(1000,true),out.values().calls().get(0));
        assertInstanceOf(CdPlayerTouchResponse.SubCpu.class,out.values().calls().get(1));
        assertEquals(new CdPlayerTouchResponse.PlayFm(CdPlayerTouchResponse.FmSound.FM_DESTROY),out.values().calls().get(2));
    }
    @Test void genuineCommonKillAndLegacyOddFailureRemainUnweakened()throws Exception{
        var h=input(0xFC,2,0x2A);
        var out=(CdPlayerTouchResponse.BossSpecialCompleted)h.owner.respondBossSpecial(h.selected,actor(0,2,1,2,0),player(0,0,0,0),world(0),1,h.cut);
        assertEquals(CdPlayerTouchResponse.Exit.KILL_PLAYER,out.commonResult().orElseThrow().exit());
        assertEquals(6,out.values().actor().motion().routine());
        assertEquals(-0x700,out.values().actor().motion().yVelocity());
        assertEquals(0x9234,out.values().actor().tile());
        assertEquals(0,out.values().actor().priority());
        assertEquals(3,out.values().changedSlots().getFirst().unsignedByte(COLLISION_STATUS));
        var old=input(0x3C,0,0x2A);
        assertThrows(CdPlayerTouchResponse.UnsupportedResponse.class,()->old.owner.respond(old.selected,actor(2,0,0,0,0),player(0,0,0,1),world(0),old.cut));
    }
    private Input input(int raw,int status,int id)throws Exception{
        return new Input(CdR13PlayerCollisionAssets.fromAssets(actual().getFirst()),raw,status,id);
    }
    private static final class Input {
        final CdR13PlayerCollisionAssets data;
        final CdObjectPool pool=new CdObjectPool();
        final CdPlayerTouchResponse owner;
        CdObjectPool.Snapshot cut;
        CdPlayerObjectTouch.HandlerBoundary selected;
        Input(CdR13PlayerCollisionAssets data,int raw,int status,int id){
            this.data=data;
            owner=CdPlayerTouchResponse.fromAct3FutureAssets(data);
            pool.write(pool.state(50).moveByte(ID,id).moveByte(SPRITE_FLAGS,128).moveByte(COLLISION_TYPE,raw).moveByte(COLLISION_STATUS,status).moveWord(X,0x200).moveWord(Y,0x100));
            refresh();
        }
        void refresh(){
            cut=pool.capture();
            selected=(CdPlayerObjectTouch.HandlerBoundary)new CdPlayerObjectTouch(data.touchDimensions()).scan(new CdPlayerObjectTouch.PlayerCut(0x200,0x100,0x13,0,0),cut);
        }
    }
    private static CdPlayerTouchResponse.Actor actor(int anim,int flags,int xv,int yv,int hurt){
        return new CdPlayerTouchResponse.Actor(new CdPlayerMotionState(0x2000000,0x1000000,xv,yv,0,2,0,flags,4,9,19,anim,0,0,0,0,0,0,0,0),0x1234,3,hurt,0x55);
    }
    private static CdPlayerTouchResponse.PlayerGlobals player(int warp,int shield,int debugMode,int chain){
        return new CdPlayerTouchResponse.PlayerGlobals(warp,0,shield,1,0,debugMode,chain,0);
    }
    private static CdPlayerTouchResponse.WorldGlobals world(int rings){
        return new CdPlayerTouchResponse.WorldGlobals(rings,3,0,0,5000,0);
    }
    private List<CdR13FutureAssets> actual()throws Exception{
        if(originals!=null)return originals;
        String root=System.getProperty("soniccd.media.root");
        assumeTrue(root!=null&&!root.isBlank(),"Optional original USA media");
        Path cue;
        try(var paths=Files.list(Path.of(root).resolve("USA"))){
            var cues=paths.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1,cues.size());
            cue=cues.getFirst();
        }
        try(var p=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){
            originals=List.of(CdR13FutureAssets.fromPrograms(p,CdStagePayload.R13C.stage()),CdR13FutureAssets.fromPrograms(p,CdStagePayload.R13D.stage()));
        }
        return originals;
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
