package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.openggf.game.soniccd.player.TestCdR31PlayerObjectTouch.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.player.CdPlayerTouchResponse.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tests first: constructed complete pure response cuts over actual original R31A tables; NO Body/Frame/World/native route. Java UNRUN. */
class TestCdR31PlayerTouchResponse {
    @Test void exactDataOwnerAndOriginalSelectionCannotBeReplacedByEqualSourceOrLegacyValues() throws Exception {
        var a=assets().first();var b=assets().second();var response=CdPlayerTouchResponse.fromR31aAssets(a);assertTrue(response.ownsR31aAssets(a));assertFalse(response.ownsR31aAssets(b));assertFalse(response.ownsR31aAssets(null));
        var pool=pool(0x47,0x19,0);var cut=pool.capture();var selected=selection(pool,0x46);var actor=actor(0x100,0x200,0,0,0,0,0,0,0);cut=pool.capture();
        var actualCut=cut;var world=world(0,0,5000);var globals=globals(0,0,0,0,0,0,0,0);
        assertThrows(IllegalArgumentException.class,()->CdPlayerTouchResponse.fromR31aAssets(b).respond(selected,actor,globals,world,actualCut));
        var legacy=new CdPlayerTouchResponse(a.view(CdR31aAssets.Asset.ENEMY_POINTS));assertFalse(legacy.ownsR31aAssets(a));
        assertThrows(IllegalArgumentException.class,()->legacy.respond(selected,actor,globals,world,actualCut));
        var oldSelection=assertInstanceOf(CdPlayerObjectTouch.HandlerBoundary.class,new CdPlayerObjectTouch(sizes(a)).scan(player(0x100,0x200,0x13,0,0),cut));
        assertThrows(IllegalArgumentException.class,()->response.respond(oldSelection,actor,globals,world,actualCut));
        assertEquals(cut.slots(),pool.capture().slots());
    }
    @Test void monitorUpwardAndDownwardUseNativeWordsAndOnlyOriginalSlotStores() throws Exception {
        var response=response();var pool=pool(0x46,0x10,0);var selected=selection(pool,0x46);var cut=pool.capture();
        var up=actor(0x100,0x210,0,-1,0,0xE1,5,0xFF,0xFFFF);var r=response.respond(selected,up,globals(1,1,0xFE,1,1,1,0xFFFF,1),world(3,0,5000),cut);
        assertEquals(Exit.MONITOR_UPWARD_BOUNCE,r.exit());assertEquals(1,r.actor().motion().yVelocity());assertEquals(0xFE80,r.changedSlots().getFirst().word(Y_VELOCITY));assertEquals(4,r.changedSlots().getFirst().unsignedByte(ROUTINE_2));assertTrue(r.calls().isEmpty());
        var down=actor(0x100,0x200,0,0x800,0,0,2,0,0);var d=response.respond(selected,down,globals(0,0,0,0,0,0,0,0),world(3,0,5000),cut);
        assertEquals(Exit.MONITOR_DOWNWARD_BOUNCE,d.exit());assertEquals(-0x800,d.actor().motion().yVelocity());assertEquals(2,d.changedSlots().getFirst().routine());assertEquals(cut.slots(),pool.capture().slots());
        byte[] bytes=cut.slots().getFirst().copyBytes();bytes[0x24]=2;assertArrayEquals(bytes,d.changedSlots().getFirst().copyBytes());
    }
    @Test void enemyDamageUsesNegateThenArithmeticShiftWithoutPointsOrMappingReplacement() throws Exception {
        var pool=pool(7,0x27,2);var selected=selection(pool,7);var cut=pool.capture();var a=actor(0x100,0x200,-32768,513,0x1234,0xA5,0,0,0);
        var r=response().respond(selected,a,globals(0,1,0,0,0,0,0,0),world(7,100,5000),cut);
        assertEquals(Exit.DAMAGE_ENEMY,r.exit());assertEquals(-16384,r.actor().motion().xVelocity());assertEquals(-257,r.actor().motion().yVelocity());
        assertEquals(0,r.changedSlots().getFirst().unsignedByte(COLLISION_TYPE));assertEquals(1,r.changedSlots().getFirst().unsignedByte(COLLISION_STATUS));assertEquals(0xA1,r.changedSlots().getFirst().flags());assertTrue(r.calls().isEmpty());
        assertEquals(cut.slots(),pool.capture().slots());assertEquals(cut.slots().getFirst().longBits(CdObjectState.LongField.MAPPINGS),r.changedSlots().getFirst().longBits(CdObjectState.LongField.MAPPINGS));
        pool.write(pool.state(32).moveByte(COLLISION_STATUS,1).moveByte(FLAGS,0x21));var last=selection(pool,7);var lastCut=pool.capture();
        var finalHit=response().respond(last,a,globals(0,1,0,0,0,0,0,0),world(7,100,5000),lastCut);
        assertEquals(0,finalHit.changedSlots().getFirst().unsignedByte(COLLISION_STATUS));assertEquals(0xA1,finalHit.changedSlots().getFirst().flags());assertEquals(lastCut.slots(),pool.capture().slots());
    }
    @Test void originalEnemyPointWordsChainCapAndExplosionMetadataUseExistingOwner() throws Exception {
        var points=assets().first().view(CdR31aAssets.Asset.ENEMY_POINTS);assertEquals("000a001400320064",HexFormat.of().formatHex(points.copy()));
        for(int[] v:new int[][]{{0,10,0},{2,20,2},{4,50,4},{6,100,6},{0x1E,1000,10},{0xFFFE,100,6}}) {
            var pool=pool(7,0x27,0);var selected=selection(pool,7);var cut=pool.capture();var a=actor(0x100,0x200,0,0x500,0,0,2,0,0);
            var r=response().respond(selected,a,globals(0,0,0,0,0,0,v[0],0),world(1,0,5000),cut);var object=r.changedSlots().getFirst();
            assertEquals(Exit.KILL_ENEMY,r.exit());assertEquals((v[0]+2)&0xFFFF,r.player().scoreChain());assertEquals(v[1],r.world().score());assertEquals(v[2],object.word(EXPLOSION_POINTS_FRAME));
            assertEquals(0x18,object.id());assertEquals(0,object.routine());assertEquals(1,object.subtype());assertEquals(cut.slots().getFirst().longBits(CdObjectState.LongField.MAPPINGS),object.longBits(CdObjectState.LongField.MAPPINGS));
            assertEquals(List.of(new AddPoints(v[1],false),new PlayFm(FmSound.FM_DESTROY)),r.calls());assertEquals(cut.slots(),pool.capture().slots());
        }
    }
    @Test void unsignedScoreCapOneExtraLifeAndNative7aAreNotMusicRestore() throws Exception {
        var pool=pool(7,0x27,0);var selected=selection(pool,7);var cut=pool.capture();var a=actor(0x100,0x200,0,0,0,0,2,0,0);
        var w=new WorldGlobals(8,255,255,999995,999999,0xA5);var r=response().respond(selected,a,globals(0,0,0,0,0,0,0,0),w,cut);
        assertEquals(999999,r.world().score());assertEquals(1004999,r.world().nextLifeScore());assertEquals(0,r.world().lives());assertEquals(0,r.world().updateHudLives());assertEquals(1,r.world().updateHudScore());
        assertEquals(List.of(new AddPoints(10,true),new SubCpu(SubCpuCommand.SCMD_YESSFX),new PlayFm(FmSound.FM_DESTROY)),r.calls());assertEquals(8,r.world().rings());assertEquals(cut.slots(),pool.capture().slots());
        // Exact calls above contain only native AddPoints/7A/Destroy; UpdatePowerups music is a separate Body owner.
    }
    @Test void threeNativeEnemyBounceBranchesLeaveFractionsAndOtherMotionFieldsAlone() throws Exception {
        for(int[] v:new int[][]{{-0x500,0x200,-0x400},{0x500,0x200,0x400},{0x500,0x1FF,-0x500}}) {
            var pool=pool(7,0x27,0);var selected=selection(pool,7);var cut=pool.capture();var a=actor(0x100,v[1],0x111,v[0],0x222,0xA1,2,0,0);
            var r=response().respond(selected,a,globals(0,0,0,0,0,0,0,0),world(1,0,5000),cut);assertEquals(v[2],r.actor().motion().yVelocity());
            assertEquals(a.motion().centreX16_16(),r.actor().motion().centreX16_16());assertEquals(a.motion().centreY16_16(),r.actor().motion().centreY16_16());assertEquals(a.motion().xVelocity(),r.actor().motion().xVelocity());
            assertEquals(a.motion().groundVelocity(),r.actor().motion().groundVelocity());assertEquals(a.rotationDistance(),r.actor().rotationDistance());assertEquals(cut.slots(),pool.capture().slots());
        }
    }
    @Test void rawWarpInvincibleAndPostDisplayHurtWordSuppressHazardWithoutReadingShieldOrDebug() throws Exception {
        var pool=pool(0x87,0x19,0);var selected=selection(pool,0x87);var cut=pool.capture();
        for(int gate=0;gate<3;gate++) {
            var a=actor(0x100,0x200,1,2,3,4,5,6,gate==2?0x8000:0);var g=globals(gate==0?0x80:0,gate==1?0xFF:0,0xFE,0xFF,0xFFFF,0xFFFF,0xFFFF,0xFF);var w=world(0,0,5000);
            var r=response().respond(selected,a,g,w,cut);assertEquals(Exit.NO_HURT,r.exit());assertEquals(OptionalInt.of(-1),r.d0());assertEquals(a,r.actor());assertEquals(g,r.player());assertEquals(w,r.world());assertTrue(r.changedSlots().isEmpty());assertTrue(r.calls().isEmpty());
        }
        assertEquals(cut.slots(),pool.capture().slots());
    }
    @Test void shieldHurtDelegatesTheSourceIdenticalGroundResetAndPreservesRotationAndFractions() throws Exception {
        var pool=pool(0x87,0x19,0);var selected=selection(pool,0x87);var cut=pool.capture();var a=actor(0x100,0x200,99,88,77,0x75,2,0xFF,0);
        var g=globals(0,0,3,0xFF,0,0,0xABCD,1);var w=world(0,42,5000);var r=response().respond(selected,a,g,w,cut);var m=r.actor().motion();
        assertEquals(Exit.HURT,r.exit());assertEquals(4,m.routine());assertEquals(0,m.charge());assertEquals(5,m.xRadius());assertEquals(10,m.yRadius());
        assertEquals((0x1FE<<16)|0xABCD,m.centreY16_16());assertEquals(a.motion().centreX16_16(),m.centreX16_16());assertEquals(a.rotationDistance(),r.actor().rotationDistance());
        assertEquals(0x78,r.actor().hurt());assertEquals(2,r.player().shield());assertEquals(0xFF,r.player().combineRing());assertEquals(0,r.player().scoreChain());
        assertEquals(List.of(new ResetOnFloor(),new PlayFm(FmSound.FM_CHARGESTOP)),r.calls());assertTrue(r.changedSlots().isEmpty());assertEquals(w,r.world());assertEquals(cut.slots(),pool.capture().slots());
    }
    @Test void ringSpillFindsDirtySelectedIdZeroAndThenReadsItsOriginalAddressAfterStores() throws Exception {
        var pool=pool(0x87,0,0);pool.write(pool.state(32).moveWord(X,0x104));var selected=selection(pool,0x87);var cut=pool.capture();var a=actor(0x100,0x200,0,0,0,0,0,0,0);
        var r=response().respond(selected,a,globals(0,0,0,0xFF,0,0,0xFFFF,0),world(1,0,5000),cut);
        assertEquals(Exit.HURT,r.exit());assertEquals(0x200,r.actor().motion().xVelocity());assertEquals(-0x400,r.actor().motion().yVelocity());
        assertEquals(List.of(new FindObjSlot(OptionalInt.of(32)),new ResetOnFloor()),r.calls());assertEquals(1,r.changedSlots().size());var child=r.changedSlots().getFirst();assertEquals(0x11,child.id());assertEquals(0x100,child.centreX());assertEquals(0x200,child.centreY());
        assertEquals(1,r.world().rings(),"LostRing child execution/global deduction is not performed by this value response");assertEquals(0,r.player().combineRing());assertEquals(cut.slots(),pool.capture().slots());
    }
    @Test void allocationExhaustionKeepsNativeHurtAndNeverSynthesizesAnOutsideSlot() throws Exception {
        var pool=pool(0x87,0x19,0);for(int n=33;n<128;n++)pool.write(pool.state(n).moveByte(ID,1));pool.write(pool.state(32).moveWord(X,0x104));var selected=selection(pool,0x87);var cut=pool.capture();
        var r=response().respond(selected,actor(0x100,0x200,0,0,0,0,0,0,0),globals(0,0,0,0,0,0,0,0),world(1,0,5000),cut);
        assertEquals(Exit.HURT,r.exit());assertEquals(-0x200,r.actor().motion().xVelocity());assertEquals(List.of(new FindObjSlot(OptionalInt.empty()),new ResetOnFloor()),r.calls());assertTrue(r.changedSlots().isEmpty());assertEquals(cut.slots(),pool.capture().slots());
    }
    @Test void sourceKillAndDebugBranchesReuseExistingExactNativeResetAndRawYWordStore() throws Exception {
        var pool=pool(0x87,0x19,0);var selected=selection(pool,0x87);var cut=pool.capture();var a=actor(0x100,0x200,1,2,3,0,5,0,0);
        var debug=response().respond(selected,a,globals(0,0,0,0,0,0x8000,0x1234,0),world(0,1,5000),cut);assertEquals(Exit.KILL_PLAYER_DEBUG_NO_WRITE,debug.exit());assertEquals(a,debug.actor());assertTrue(debug.calls().isEmpty());
        var r=response().respond(selected,a,globals(0,0,0,0,0,0,0x1234,0),world(0,1,5000),cut);var m=r.actor().motion();
        assertEquals(Exit.KILL_PLAYER,r.exit());assertEquals(6,m.routine());assertEquals(-0x700,m.yVelocity());assertEquals(0,m.xVelocity());assertEquals(0,m.groundVelocity());assertEquals(0x18,m.animation());
        assertEquals(0x9234,r.actor().tile());assertEquals(0,r.actor().priority());assertEquals(2,m.stick());assertEquals(0,r.actor().rotationDistance());assertEquals(0xABCD,m.centreY16_16()&0xFFFF);assertEquals(0x1234,m.centreX16_16()&0xFFFF);
        assertEquals(List.of(new ResetOnFloor(),new PlayFm(FmSound.FM_HURT)),r.calls());assertEquals(cut.slots(),pool.capture().slots());
    }
    @Test void r3SpecialMatrixKeeps27AndRejectsBorrowedPpz21And2fBeforeAnyBossRead() throws Exception {
        for(int selector:new int[]{0x17,0x1F,0x23,0x27,0x3A,0x3B,0x21,0x2F}) {
            var pool=pool(0xC0|selector,0,0xFF);var selected=selection(pool,0xC0|selector);var cut=pool.capture();var a=actor(0x100,0x200,1,2,3,0,2,0,0);var g=globals(0,0,0,0,0,0,0,0);var w=world(0,0,5000);
            if(selector==0x21||selector==0x2F)assertEquals(UnsupportedBoundary.R3_BOSS_STARTED_OWNER,assertThrows(UnsupportedResponse.class,()->response().respond(selected,a,g,w,cut)).boundary());
            else {var r=response().respond(selected,a,g,w,cut);assertEquals(Exit.SPECIAL_FLAG_INCREMENT,r.exit());assertEquals(0,r.changedSlots().getFirst().unsignedByte(COLLISION_STATUS));assertEquals(a,r.actor());assertEquals(g,r.player());assertEquals(w,r.world());assertTrue(r.calls().isEmpty());}
            assertEquals(cut.slots(),pool.capture().slots());
        }
        for(int selector:new int[]{0x3A,0x3B}) {
            var pool=pool(0xC0|selector,0,1);var selected=selection(pool,0xC0|selector);var cut=pool.capture();var r=response().respond(selected,actor(0x100,0x200,0,0,0,4,0,0,0),globals(1,1,1,1,1,1,1,1),world(1,1,1),cut);
            assertEquals(Exit.SPECIAL_ROLL_NO_WRITE,r.exit());assertTrue(r.changedSlots().isEmpty());assertEquals(cut.slots(),pool.capture().slots());
        }
        assertTrue(CdPlayerTouchResponse.ownsSpecialFlagCollision(0xE1));assertTrue(CdPlayerTouchResponse.ownsSpecialFlagCollision(0xEF));assertFalse(CdPlayerTouchResponse.ownsSpecialFlagCollision(0xE7));
    }
    @Test void explicitBossStartedOwnsOnlyNativeNoOpPathsAndStopsAtReachedUnownedResponse() throws Exception {
        var a=actor(0x100,0x200,1,2,3,4,5,6,7);var g=globals(1,1,1,1,1,1,1,1);var w=world(1,1,1);var response=response();
        for(int[] v:new int[][]{{0x0B,0,0},{0x0C,0,0},{0x3C,0,0},{0x21,1,0},{0x2F,255,0},{0x3C,4,0},{0x3F,5,0},{0x3C,1,1},{0x3C,2,1},{0x3C,255,1},{0x3E,4,1},{0x3F,4,1},{0x3D,5,1}}) {
            var pool=pool(0xC0|v[0],0x19,0xA5);var selected=selection(pool,0xC0|v[0]);var cut=pool.capture();
            if(v[2]==0) {var r=response.respondR3Special(selected,a,g,w,v[1],cut);assertEquals(Exit.SPECIAL_NO_WRITE,r.exit());assertEquals(a,r.actor());assertEquals(g,r.player());assertEquals(w,r.world());assertTrue(r.d0().isEmpty());assertTrue(r.calls().isEmpty());assertTrue(r.changedSlots().isEmpty());}
            else assertEquals(UnsupportedBoundary.R3_BOSS_SPECIAL_RESPONSE_OWNER,assertThrows(UnsupportedResponse.class,()->response.respondR3Special(selected,a,g,w,v[1],cut)).boundary());
            assertEquals(cut.slots(),pool.capture().slots());
        }
        var pool=pool(0xE7,0x19,0);var selected=selection(pool,0xE7);var cut=pool.capture();assertEquals(Exit.SPECIAL_FLAG_INCREMENT,response.respondR3Special(selected,a,g,w,5,cut).exit(),"Native27 branch precedes boss_started");
        assertThrows(IllegalArgumentException.class,()->response.respondR3Special(selected,a,g,w,256,cut));
    }
    @Test void oddPointsAndForeignPoolStopsPublishNoStagedWrites() throws Exception {
        var pool=pool(7,0x27,0);var selected=selection(pool,7);var cut=pool.capture();var a=actor(0x100,0x200,1,2,3,0,2,0,0);var g=globals(0,0,0,0,0,0,1,0);var w=world(1,0,5000);
        assertEquals(UnsupportedBoundary.ODD_ENEMY_POINTS_ADDRESS,assertThrows(UnsupportedResponse.class,()->response().respond(selected,a,g,w,cut)).boundary());assertEquals(cut.slots(),pool.capture().slots());
        var foreign=pool(7,0x27,0);assertThrows(IllegalArgumentException.class,()->response().respond(selected,a,g,w,foreign.capture()));assertEquals(cut.slots(),pool.capture().slots());
    }
    @Test void legacyFlagResponsesStayUnchangedAndR3CannotEnterLegacyBossContinuation() throws Exception {
        var raw=assets().first();var legacy=new CdPlayerTouchResponse(raw.view(CdR31aAssets.Asset.ENEMY_POINTS));var a=actor(0x100,0x200,0,0,0,0,2,0,0);var g=globals(0,0,0,0,0,0,0,0);var w=world(0,0,5000);
        for(int type:new int[]{0xE1,0xEF}) {
            var pool=pool(type,0x19,0xFF);var cut=pool.capture();var old=assertInstanceOf(CdPlayerObjectTouch.HandlerBoundary.class,new CdPlayerObjectTouch(sizes(raw)).scan(player(0x100,0x200,0x13,0,0),cut));
            assertEquals(Exit.SPECIAL_FLAG_INCREMENT,legacy.respond(old,a,g,w,cut).exit());assertEquals(cut.slots(),pool.capture().slots());
        }
        var pool=pool(0xFC,0x19,0);var selected=selection(pool,0xFC);var cut=pool.capture();assertThrows(IllegalArgumentException.class,()->response().respondBossSpecial(selected,a,g,w,1,cut));
        assertThrows(IllegalArgumentException.class,()->legacy.respondR3Special(selected,a,g,w,0,cut));
    }
    @Test void sharedCommonResponsesRemainValueExactAgainstTheLegacyFormatKernel() throws Exception {
        var raw=assets().first();var legacy=new CdPlayerTouchResponse(raw.view(CdR31aAssets.Asset.ENEMY_POINTS));
        // Constructed same-table legacy controls only; neither format constructor authenticates a PPZ World.
        for(int[] v:new int[][]{{7,0},{7,2},{0x46,0},{0x87,0}}) {
            var pool=pool(v[0],0x19,v[1]);var selected=selection(pool,v[0]);var cut=pool.capture();
            var old=assertInstanceOf(CdPlayerObjectTouch.HandlerBoundary.class,new CdPlayerObjectTouch(sizes(raw)).scan(player(0x100,0x200,0x13,0,0),cut));
            var a=actor(0x100,0x200,0x123,0x456,0x789,4,2,0xFF,0);var g=globals(0,0,1,1,0,0,2,0);var w=world(12,1234,5000);
            assertEquals(legacy.respond(old,a,g,w,cut),response().respond(selected,a,g,w,cut));assertEquals(cut.slots(),pool.capture().slots());
        }
    }
    private static CdPlayerTouchResponse response() throws Exception{return CdPlayerTouchResponse.fromR31aAssets(assets().first());}
    private static CdObjectPool pool(int type,int id,int status){var pool=new CdObjectPool();pool.write(object(pool,32,id,0x80,type,0x100,0x200).moveByte(COLLISION_STATUS,status).moveByte(FLAGS,0xA1).moveLong(CdObjectState.LongField.MAPPINGS,0x123456));return pool;}
    private static CdPlayerObjectTouch.HandlerBoundary selection(CdObjectPool pool,int type)throws Exception {pool.write(pool.state(32).moveByte(COLLISION_TYPE,type));return assertInstanceOf(CdPlayerObjectTouch.HandlerBoundary.class,scanner().scan(player(0x100,0x200,0x13,0,0),0,pool.capture()));}
    private static Actor actor(int x,int y,int xv,int yv,int gv,int flags,int anim,int charge,int hurt) {
        return new Actor(new CdPlayerMotionState((x<<16)|0x1234,(y<<16)|0xABCD,xv,yv,gv,2,0x22,flags,0xA5,9,19,anim,0x11,1,charge,0x1234,0xA5,0x21,0x55,0xAA),0x1234,0xA5,hurt,0x39);
    }
    private static PlayerGlobals globals(int warp,int invincible,int shield,int combine,int debugCheat,int debugMode,int chain,int mini){return new PlayerGlobals(warp,invincible,shield,combine,debugCheat,debugMode,chain,mini);}
    private static WorldGlobals world(int rings,int score,int next){return new WorldGlobals(rings,3,0,score,next,0);}
}
