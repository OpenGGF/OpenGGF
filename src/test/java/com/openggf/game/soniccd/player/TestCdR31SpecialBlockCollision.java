package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.objects.CdObjectPool;
import com.openggf.game.soniccd.objects.CdObjectState;
import com.openggf.game.soniccd.player.CdR31SpecialBlockCollision.BlockRead;
import com.openggf.game.soniccd.player.CdR31SpecialBlockCollision.Store;
import com.openggf.game.soniccd.player.CdR31SpecialBlockCollision.PlaceBlock;
import com.openggf.game.soniccd.player.CdR31SpecialBlockCollision.Fm;
import com.openggf.game.soniccd.player.CdR31SpecialBlockCollision.SubCpu;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.player.CdR31SpecialBlockCollision.*;

/** Tests authored before production. All inputs are CONSTRUCTED COMPONENT controls, not media/World admission.
 * Literal vectors: N1ecc5f3a r3/player_block_check.asm3-550. Author Java UNRUN.
 * Math-table literals are source-derived N vectors, not production assets or media authentication. */
class TestCdR31SpecialBlockCollision {
    static final Source PRESENT = new Source(0x0100,OptionalInt.of(1),OptionalInt.empty());
    static State actor(int routine,int animation,int xv,int yv,int flags) {
        var m=new CdPlayerMotionState(0x01081234,0x01085678,xv,yv,123,routine,17,flags,0x44,7,19,
                animation,9,1,5,-3,7,0,0x12,0x34);
        return new State(new CdPlayerBody.State(m,new CdPlayerBody.AnimationCursor(3,4,5),
                new CdPlayerBody.Header(1,0x222222,0xA780,2,24),new CdPlayerBody.Timers(77,88,99,111),
                new CdPlayerBody.History(CdAssetBytes.copyOf(new byte[256]),8),17,new CdPlayerBody.ObjectLinks(32,33),19),OptionalInt.empty());
    }
    static CdAssetBytes words(int... values){byte[] b=new byte[values.length*2];for(int i=0;i<values.length;i++){b[2*i]=(byte)(values[i]>>>8);b[2*i+1]=(byte)values[i];}return CdAssetBytes.copyOf(b);}
    static Data data(){
        var a=words(15,0x13C,0x146,0x19B,0x1AE,0x83,0x84,0x89,0x8A,0x77,0x76,0x80,0x7F,0x7E,0x7D,0x7C,0x82);
        var b=words(1,0x145,0x146);var c=words(15,0x13C,0x146,0,0,0x83,0x84,0x89,0x8A,0x77,0x76,0x80,0x7F,0x7E,0x7D,0x7C,0x82);
        var d=words(15,0x13C,0x146,0x165,0x166,0x83,0x84,0x89,0x8A,0x77,0x76,0x80,0x7F,0x7E,0x7D,0x7C,0x82);
        byte[] sine=HexFormat.of().parseHex("00000006000c00120019001f0025002b00310038003e0044004a00500056005c00610067006d00730078007e00830088008e00930098009d00a200a700ab00b000b500b900bd00c100c500c900cd00d100d400d800db00de00e100e400e700ea00ec00ee00f100f300f400f600f800f900fb00fc00fd00fe00fe00ff00ff00ff010000ff00ff00ff00fe00fe00fd00fc00fb00f900f800f600f400f300f100ee00ec00ea00e700e400e100de00db00d800d400d100cd00c900c500c100bd00b900b500b000ab00a700a2009d00980093008e00880083007e00780073006d00670061005c00560050004a0044003e00380031002b0025001f00190012000c00060000fffafff4ffeeffe7ffe1ffdbffd5ffcfffc8ffc2ffbcffb6ffb0ffaaffa4ff9fff99ff93ff8bff88ff82ff7dff78ff72ff6dff68ff63ff5eff59ff55ff50ff4bff47ff43ff3fff3bff37ff33ff2fff2cff28ff25ff22ff1fff1cff19ff16ff14ff12ff0fff0dff0cff0aff08ff07ff05ff04ff03ff02ff02ff01ff01ff01ff00ff01ff01ff01ff02ff02ff03ff04ff05ff07ff08ff0aff0cff0dff0fff12ff14ff16ff19ff1cff1fff22ff25ff28ff2cff2fff33ff37ff3bff3fff43ff47ff4bff50ff55ff59ff5eff63ff68ff6dff72ff78ff7dff82ff88ff8bff93ff99ff9fffa4ffaaffb0ffb6ffbcffc2ffc8ffcfffd5ffdbffe1ffe7ffeefff4fffa00000006000c00120019001f0025002b00310038003e0044004a00500056005c00610067006d00730078007e00830088008e00930098009d00a200a700ab00b000b500b900bd00c100c500c900cd00d100d400d800db00de00e100e400e700ea00ec00ee00f100f300f400f600f800f900fb00fc00fd00fe00fe00ff00ff00ff");
        var shapes=List.of(Optional.of(CdAssetBytes.copyOf(new byte[]{1,1,1,2,2,2,3,3,3,4,4,4,5,5,5,6})),
                Optional.of(CdAssetBytes.copyOf(new byte[]{6,6,7,7,7,8,8,8,9,9,9,10,10,10,11,11})),
                Optional.of(CdAssetBytes.copyOf(new byte[]{11,12,12,12,13,13,13,14,14,14,15,15,15,16,16,16})));
        return new Data(List.of(Optional.of(b),Optional.of(a),Optional.of(d),Optional.of(c)),shapes,
                Optional.of(CdAssetBytes.copyOf(sine)),Optional.of(CdAssetBytes.copyOf(HexFormat.of().parseHex("000000000101010101010202020202020303030303030304040404040405050505050506060606060606070707070707080808080808080909090909090a0a0a0a0a0a0a0b0b0b0b0b0b0b0c0c0c0c0c0c0c0d0d0d0d0d0d0d0e0e0e0e0e0e0e0f0f0f0f0f0f0f10101010101010111111111111111112121212121212131313131313131314141414141414141515151515151515151616161616161616171717171717171717181818181818181818191919191919191919191a1a1a1a1a1a1a1a1a1b1b1b1b1b1b1b1b1b1b1c1c1c1c1c1c1c1c1c1c1c1d1d1d1d1d1d1d1d1d1d1d1e1e1e1e1e1e1e1e1e1e1e1f1f1f1f1f1f1f1f1f1f1f1f20202020202020"))));
    }
    static final class Harness implements Ports {
        final CdObjectPool pool=new CdObjectPool(); final List<String> actions=new ArrayList<>();
        final List<BlockRead> reads=new ArrayList<>(); final ArrayDeque<Integer> blocks=new ArrayDeque<>();
        Store leaked; PlaceBlock leakedPlace; boolean omitStore,omitFm; RuntimeException fault,fmFault; Error error; int failPlace=-1,placements;
        CdR31SpecialBlockCollision owner; Runnable onRead=()->{};
        Harness(int...raw){for(int v:raw)blocks.add(v);}
        CdR31SpecialBlockCollision component(Data d){owner=new CdR31SpecialBlockCollision(d,Optional.of(pool),this);return owner;}
        public int readBlock(BlockRead r){reads.add(r);onRead.run();if(fault!=null&&blocks.isEmpty())throw fault;return blocks.removeFirst();}
        public void store(Store s){leaked=s;actions.add(s.field()+"="+s.resultRaw());if(!omitStore)s.applyNative();}
        public void placeBlock(PlaceBlock p){leakedPlace=p;actions.add("place:"+p.xWord()+","+p.yWord()+","+p.blockWord());if(placements++==failPlace){if(error!=null)throw error;throw fault;}p.completedNative();}
        public void fm(Fm p){actions.add("fm:"+p.nativeId());if(fmFault!=null)throw fmFault;if(!omitFm)p.completedNative();}
        public void subCpu(SubCpu p){actions.add("cpu:"+p.nativeId());p.completedNative();}
    }
    @Test void boredReturnDoesNotReadAnyMissingDataOrGeometry(){
        var h=new Harness();var c=h.component(Data.unowned());var before=actor(2,0x2B,0,0,0);
        assertSame(before,c.run(Site.MAIN_AFTER_MODE,new Source(0x100,OptionalInt.empty(),OptionalInt.empty()),before).state());
        assertTrue(h.reads.isEmpty());assertTrue(h.actions.isEmpty());
    }
    @Test void sourceAndCallerCutRejectBeforeAnyNativeWork(){
        var h=new Harness(0);var c=h.component(data());
        assertThrows(IllegalArgumentException.class,()->c.run(Site.MAIN_AFTER_MODE,new Source(0,OptionalInt.of(1),OptionalInt.empty()),actor(2,0,0,256,0)));
        assertThrows(IllegalArgumentException.class,()->c.run(Site.HURT_BEFORE_RECOVERY,PRESENT,actor(2,0,0,256,0)));
        assertTrue(h.reads.isEmpty());assertFalse(c.failed());
    }
    @Test void cardinalQuadrantsUseLiteralNativeCornerOrder(){
        int[][] velocities={{0,256},{-256,0},{0,-256},{256,0}};
        int[][][] expected={{{0x101,0x11B},{0x10F,0x11B}},{{0x101,0xFB},{0x101,0x11B}},{{0x101,0xF5},{0x10F,0xF5}},{{0x10F,0xFB},{0x10F,0x11B}}};
        for(int i=0;i<4;i++){var h=new Harness(0,0);var c=h.component(data());var before=actor(2,0,velocities[i][0],velocities[i][1],0);assertFalse(c.run(Site.MAIN_AFTER_MODE,PRESENT,before).contacted());
            for(int j=0;j<2;j++){assertEquals(expected[i][j][0],h.reads.get(j).xWord());assertEquals(expected[i][j][1],h.reads.get(j).yWord());}assertSame(before,c.state());}
    }
    @Test void blankBlockDoesNotReadUnknownPeriodOrLists(){var h=new Harness(0xF800,0);var c=h.component(data());assertFalse(c.run(Site.MAIN_AFTER_MODE,new Source(0x100,OptionalInt.empty(),OptionalInt.empty()),actor(2,0,0,256,0)).contacted());}
    @Test void nonblankUnknownPeriodStopsBeforeActorOrNoBumperStore(){var h=new Harness(0x84);var c=h.component(data());var before=actor(4,0,0,256,0);var f=assertThrows(Unowned.class,()->c.run(Site.HURT_BEFORE_RECOVERY,new Source(0x100,OptionalInt.empty(),OptionalInt.empty()),before));assertEquals(Boundary.PERIOD_BYTE,f.boundary());assertSame(before,c.state());assertTrue(h.actions.isEmpty());assertTrue(c.failed());}
    @Test void presentNeverReadsUnknownGoodFutureAndMaskedPresentSelectsSameList(){for(int raw:new int[]{1,0x81}){var h=new Harness(0x84);var c=h.component(data());assertTrue(c.run(Site.MAIN_AFTER_MODE,new Source(0x100,OptionalInt.of(raw),OptionalInt.empty()),actor(2,0,0,256,0)).contacted());assertEquals(-0x700,c.state().actor().motion().yVelocity());}}
    @Test void futureGoodFutureReadIsLazyAndByteAdditionWraps(){
        var h=new Harness(0x84);var c=h.component(data());assertEquals(Boundary.GOOD_FUTURE_BYTE,assertThrows(Unowned.class,()->c.run(Site.MAIN_AFTER_MODE,new Source(0x100,OptionalInt.of(0x82),OptionalInt.empty()),actor(2,0,0,256,0))).boundary());
        var h2=new Harness(0x84);var c2=h2.component(data());assertTrue(c2.run(Site.MAIN_AFTER_MODE,new Source(0x100,OptionalInt.of(2),OptionalInt.of(255)),actor(2,0,0,256,0)).contacted());
    }
    @Test void outOfDirectorySelectorIsNotClamped(){var h=new Harness(0x84);var c=h.component(data());assertEquals(Boundary.BLOCK_LIST_SELECTOR,assertThrows(Unowned.class,()->c.run(Site.MAIN_AFTER_MODE,new Source(0x100,OptionalInt.of(2),OptionalInt.of(2)),actor(2,0,0,256,0))).boundary());assertTrue(h.actions.isEmpty());}
    @Test void exactListExtentStopsAtReachedWordWithoutCreatingMatch(){var d=data();var lists=new ArrayList<>(d.blockLists());lists.set(1,Optional.of(words(15,0xFFFF)));var h=new Harness(0x84);var c=h.component(new Data(lists,d.bumperShapes(),d.sineWords(),d.arcTanBytes()));assertEquals(Boundary.BLOCK_LIST_WORD,assertThrows(Unowned.class,()->c.run(Site.MAIN_AFTER_MODE,PRESENT,actor(2,0,0,256,0))).boundary());assertTrue(h.actions.isEmpty());}
    @Test void hurtMatchRecoversBeforeNoBumperAndKeepsOtherTimersAndFractions(){var h=new Harness(0x84);var c=h.component(data());var before=actor(4,0,12,256,0xF5);c.run(Site.HURT_BEFORE_RECOVERY,PRESENT,before);var after=c.state().actor();assertEquals(List.of("ROUTINE=2","HURT_TIMER=120","NO_BUMPER=0"),h.actions.subList(0,3));assertEquals(120,after.timers().hurt());assertEquals(before.actor().timers().invincibility(),after.timers().invincibility());assertEquals(before.actor().motion().centreX16_16(),after.motion().centreX16_16());assertSame(before.actor().history(),after.history());assertSame(before.actor().header(),after.header());assertEquals(0xC7,after.motion().flags());assertEquals(0,after.motion().playerJump());assertEquals(123,after.motion().groundVelocity());}
    @Test void directionBouncesUseOldSignAndPreserveOtherVelocity(){for(int[] v:new int[][]{{0x84,123,0,-0x700,123},{0x84,123,-1,0x700,123},{0x89,0,256,256,-0x700}}){var h=new Harness(v[0]);var c=h.component(data());c.run(Site.MAIN_AFTER_MODE,PRESENT,actor(2,0,v[1],v[2],0));assertEquals(v[3],c.state().actor().motion().yVelocity());assertEquals(v[4],c.state().actor().motion().xVelocity());}}
    @Test void halfAndFullRadialUseNativeArithmeticShift(){for(int[] v:new int[][]{{0x19B,-0x380},{0x82,-0x700}}){var h=new Harness(v[0]);var c=h.component(data());c.run(Site.MAIN_AFTER_MODE,PRESENT,actor(2,0,0,256,0));assertEquals(0,c.state().actor().motion().xVelocity());assertEquals(v[1],c.state().actor().motion().yVelocity());}}
    @Test void matchedFirstCornerSkipsSecondProbe(){var h=new Harness(0x84);var c=h.component(data());c.run(Site.MAIN_AFTER_MODE,PRESENT,actor(2,0,0,256,0));assertEquals(1,h.reads.size());assertEquals("fm:181",h.actions.getLast());}
    @Test void shapeRejectionStillIssuesFmBeforeSecondCorner(){var before=actor(4,0,0,256,0);var m=before.actor().motion();var low=new CdPlayerMotionState(m.centreX16_16(),0x01015678,m.xVelocity(),m.yVelocity(),m.groundVelocity(),m.routine(),m.angle(),m.flags(),m.spriteFlags(),m.xRadius(),0,m.animation(),m.previousAnimation(),m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());before=new State(new CdPlayerBody.State(low,before.actor().animation(),before.actor().header(),before.actor().timers(),before.actor().history(),before.actor().rotationDistance(),before.actor().objectLinks(),before.actor().rotationAngle()),OptionalInt.empty());var h=new Harness(0x76,0);var c=h.component(data());assertFalse(c.run(Site.HURT_BEFORE_RECOVERY,PRESENT,before).contacted());assertEquals(2,h.reads.size());assertEquals(1,c.state().noBumperByte().orElseThrow());assertEquals(2,c.state().actor().motion().routine());assertEquals("fm:181",h.actions.getLast());}
    @Test void missingShapeRetainsHurtAndClearPrefixWithoutBouncing(){var d=data();var h=new Harness(0x76);var c=h.component(new Data(d.blockLists(),List.of(Optional.empty(),Optional.empty(),Optional.empty()),d.sineWords(),d.arcTanBytes()));assertEquals(Boundary.BUMPER_SHAPE_BYTE,assertThrows(Unowned.class,()->c.run(Site.HURT_BEFORE_RECOVERY,PRESENT,actor(4,0,0,256,0))).boundary());assertEquals(2,c.state().actor().motion().routine());assertEquals(0,c.state().noBumperByte().orElseThrow());assertEquals(256,c.state().actor().motion().yVelocity());}
    @Test void destructiveMatchPreservesDirtySlotAndPlacesFourSharedChunkWords(){var h=new Harness(0x13C);var dirty=h.pool.state(32).moveByte(CdObjectState.ByteField.SUBTYPE,0xA5).moveWord(CdObjectState.WordField.X_FRACTION,0x1234).moveWord(CdObjectState.WordField.Y_FRACTION,0x5678);h.pool.write(dirty);var c=h.component(data());c.run(Site.MAIN_AFTER_MODE,PRESENT,actor(2,0,0,256,0));var child=h.pool.state(32);assertEquals(0x29,child.id());assertEquals(0x110,child.centreX());assertEquals(0x120,child.centreY());assertEquals(0xA5,child.subtype());assertEquals(0x1234,child.word(CdObjectState.WordField.X_FRACTION));assertEquals(0x5678,child.word(CdObjectState.WordField.Y_FRACTION));assertEquals(List.of("place:272,288,0","place:256,288,0","place:256,272,0","place:272,272,0","cpu:146"),h.actions.subList(h.actions.size()-5,h.actions.size()));}
    @Test void allocationExhaustionStillReachesAllFourMutationsAndCpu(){var h=new Harness(0x13C);for(int w=32;w<128;w++)h.pool.write(h.pool.state(w).moveByte(CdObjectState.ByteField.ID,1));var c=h.component(data());c.run(Site.MAIN_AFTER_MODE,PRESENT,actor(2,0,0,256,0));assertEquals(4,h.placements);assertEquals("cpu:146",h.actions.getLast());assertEquals(1,h.pool.state(32).id());}
    @Test void mutationFaultKeepsOriginalThrowableEarlierChildAndPrefixAndRetires(){var h=new Harness(0x13C);h.failPlace=1;h.fault=new IllegalStateException("literal second PlaceBlock fault");var c=h.component(data());assertSame(h.fault,assertThrows(IllegalStateException.class,()->c.run(Site.MAIN_AFTER_MODE,PRESENT,actor(2,0,0,256,0))));assertEquals(0x29,h.pool.state(32).id());assertEquals(2,h.placements);assertEquals(-1701,c.state().actor().motion().yVelocity());assertFalse(h.actions.contains("cpu:146"));assertThrows(IllegalStateException.class,()->c.run(Site.MAIN_AFTER_MODE,PRESENT,actor(2,0,0,256,0)));}
    @Test void errorFaultAlsoRetainsOriginalIdentityAndPrefix(){var h=new Harness(0x13C);h.failPlace=0;h.error=new AssertionError("native helper fault");var c=h.component(data());assertSame(h.error,assertThrows(AssertionError.class,()->c.run(Site.MAIN_AFTER_MODE,PRESENT,actor(2,0,0,256,0))));assertTrue(c.failed());assertEquals(0x29,h.pool.state(32).id());}
    @Test void unacknowledgedStoreCannotFabricateSuccessfulMutation(){var h=new Harness(0x84);h.omitStore=true;var c=h.component(data());var before=actor(4,0,0,256,0);assertThrows(IllegalStateException.class,()->c.run(Site.HURT_BEFORE_RECOVERY,PRESENT,before));assertSame(before,c.state());assertThrows(IllegalStateException.class,()->h.leaked.applyNative());}
    @Test void caughtReentryIsObservationalAndLeakedStoreExpires(){var h=new Harness(0x84);var c=h.component(data());h.onRead=()->assertThrows(IllegalStateException.class,()->c.run(Site.MAIN_AFTER_MODE,PRESENT,actor(2,0,0,256,0)));assertTrue(c.run(Site.MAIN_AFTER_MODE,PRESENT,actor(2,0,0,256,0)).contacted());assertFalse(c.failed());assertThrows(IllegalStateException.class,()->h.leaked.applyNative());}
    @Test void mainContinuationAfterModeKillKeepsRoutineSix(){var h=new Harness(0x84);var c=h.component(data());c.run(Site.MAIN_AFTER_MODE,PRESENT,actor(6,0,0,256,0));assertEquals(6,c.state().actor().motion().routine());}
    @Test void realReflectionUsesNativeLiteralSineCellsAndHorizontalFlip(){for(int[] v:new int[][]{{0x76,-1267},{0x876,1267}}){var h=new Harness(v[0]);var c=h.component(data());c.run(Site.MAIN_AFTER_MODE,PRESENT,actor(2,0,0,256,0));assertEquals(v[1],c.state().actor().motion().xVelocity());assertEquals(-1267,c.state().actor().motion().yVelocity());}}
    @Test void routineTenPreservesItsAsymmetricFlipBranches(){for(int[] v:new int[][]{{0x77,0,0x100},{0x877,-0x700,0x100}}){var h=new Harness(v[0]);var c=h.component(data());c.run(Site.MAIN_AFTER_MODE,PRESENT,actor(2,0,0,256,0));if(v[0]==0x77){assertEquals(-1267,c.state().actor().motion().xVelocity());assertEquals(-1267,c.state().actor().motion().yVelocity());}else{assertEquals(v[1],c.state().actor().motion().xVelocity());assertEquals(v[2],c.state().actor().motion().yVelocity());}}}
    @Test void exactOriginalReceiptsCannotBeConsumedByAnotherComponent(){var h=new Harness(0x84);var c=h.component(data());var foreign=new Harness().component(data());h.onRead=()->{h.reads.getLast().requireOwner(c);assertThrows(IllegalArgumentException.class,()->h.reads.getLast().requireOwner(foreign));};c.run(Site.MAIN_AFTER_MODE,PRESENT,actor(2,0,0,256,0));assertThrows(IllegalStateException.class,()->h.leaked.requireOwner(foreign));assertThrows(IllegalStateException.class,()->h.leaked.requireOwner(c));}
    @Test void unacknowledgedFmRetainsVelocityPrefixAndExpiresReceipt(){var h=new Harness(0x84);h.omitFm=true;var c=h.component(data());assertThrows(IllegalStateException.class,()->c.run(Site.MAIN_AFTER_MODE,PRESENT,actor(2,0,0,256,0)));assertEquals(-0x700,c.state().actor().motion().yVelocity());assertTrue(c.failed());}
    @Test void completedGeometryReceiptExpiresAndDoesNotReplay(){var h=new Harness(0x13C);var c=h.component(data());c.run(Site.MAIN_AFTER_MODE,PRESENT,actor(2,0,0,256,0));assertThrows(IllegalStateException.class,()->h.leakedPlace.completedNative());assertEquals(4,h.placements);}
    @Test void missingGetBlockOwnerStopsAtItsReachedRead(){var before=actor(2,0,0,256,0);var c=new CdR31SpecialBlockCollision(data(),Optional.empty(),new Ports(){});assertEquals(Boundary.BLOCK_WORD_READ,assertThrows(Unowned.class,()->c.run(Site.MAIN_AFTER_MODE,PRESENT,before)).boundary());assertSame(before,c.state());}
    @Test void missingActualPoolStopsAfterVelocityFlagsBeforeFirstAllocation(){var h=new Harness(0x13C);var c=new CdR31SpecialBlockCollision(data(),Optional.empty(),h);assertEquals(Boundary.SPAWN_OBJECT_POOL,assertThrows(Unowned.class,()->c.run(Site.MAIN_AFTER_MODE,PRESENT,actor(2,0,0,256,0))).boundary());assertEquals(-1701,c.state().actor().motion().yVelocity());assertEquals(0,h.placements);assertEquals(0,h.pool.state(32).id());}
    @Test void unboundChunkMutationStopsAtFirstPlaceAfterActualChildPrefix(){var pool=new CdObjectPool();var c=new CdR31SpecialBlockCollision(data(),Optional.of(pool),new Ports(){public int readBlock(BlockRead r){return 0x13C;}public void store(Store s){s.applyNative();}});assertEquals(Boundary.SHARED_CHUNK_MUTATION_OWNER,assertThrows(Unowned.class,()->c.run(Site.MAIN_AFTER_MODE,PRESENT,actor(2,0,0,256,0))).boundary());assertEquals(0x29,pool.state(32).id());assertEquals(0x110,pool.state(32).centreX());}
    @Test void omittedActualStoreDoesNotEstablishNoBumperKnowledge(){var before=actor(2,0,0,256,0);var c=new CdR31SpecialBlockCollision(data(),Optional.empty(),new Ports(){public int readBlock(BlockRead r){return 0x84;}});assertEquals(Boundary.ACTOR_STORE_OWNER,assertThrows(Unowned.class,()->c.run(Site.MAIN_AFTER_MODE,PRESENT,before)).boundary());assertSame(before,c.state());assertTrue(c.state().noBumperByte().isEmpty());}
    @Test void signedRadiusByteAndSubqByteWrapBeforeCornerWords(){var before=actor(2,0,256,0,0);var m=before.actor().motion();var next=new CdPlayerMotionState(0xFFFE1234,0x00025678,m.xVelocity(),m.yVelocity(),m.groundVelocity(),m.routine(),m.angle(),m.flags(),m.spriteFlags(),0x80,5,m.animation(),m.previousAnimation(),m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());before=new State(new CdPlayerBody.State(next,before.actor().animation(),before.actor().header(),before.actor().timers(),before.actor().history(),before.actor().rotationDistance(),before.actor().objectLinks(),before.actor().rotationAngle()),before.noBumperByte());var h=new Harness(0,0);var c=h.component(data());c.run(Site.MAIN_AFTER_MODE,PRESENT,before);assertEquals(0xFF7E,h.reads.get(0).xWord());assertEquals(3,h.reads.get(0).yWord());assertEquals(7,h.reads.get(1).yWord());assertSame(before,c.state());}
    @Test void bothHalfRadialEntriesUseBeAndOtherBounceUsesB5(){for(int[] v:new int[][]{{0x19B,0xBE},{0x1AE,0xBE},{0x82,0xB5}}){var h=new Harness(v[0]);var c=h.component(data());c.run(Site.MAIN_AFTER_MODE,PRESENT,actor(2,0,0,256,0));assertEquals("fm:"+v[1],h.actions.getLast());}}
    @Test void highBlockMetadataIsMaskedOnlyForMatchingAndRetainedForFlip(){var h=new Harness(0xF876);var c=h.component(data());c.run(Site.MAIN_AFTER_MODE,PRESENT,actor(2,0,0,256,0));assertEquals(1267,c.state().actor().motion().xVelocity());assertEquals(-1267,c.state().actor().motion().yVelocity());}
    @Test void fmFaultKeepsCompletedActorPrefixAndDoesNotProbeAgain(){var h=new Harness(0x84);h.fmFault=new IllegalArgumentException("literal native FM fault");var c=h.component(data());assertSame(h.fmFault,assertThrows(IllegalArgumentException.class,()->c.run(Site.MAIN_AFTER_MODE,PRESENT,actor(2,0,0,256,0))));assertEquals(-0x700,c.state().actor().motion().yVelocity());assertEquals(1,h.reads.size());assertTrue(c.failed());}
    @Test void routineTenRightHalfUsesReflectionOrYBranchInsteadOfXBranch(){for(int[] v:new int[][]{{0x77,-1267,-1267},{0x877,0,-0x700}}){var before=actor(2,0,0,256,0);var m=before.actor().motion();var next=new CdPlayerMotionState(0x01161234,m.centreY16_16(),m.xVelocity(),m.yVelocity(),m.groundVelocity(),m.routine(),m.angle(),m.flags(),m.spriteFlags(),m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());before=new State(new CdPlayerBody.State(next,before.actor().animation(),before.actor().header(),before.actor().timers(),before.actor().history(),before.actor().rotationDistance(),before.actor().objectLinks(),before.actor().rotationAngle()),before.noBumperByte());var h=new Harness(v[0]);var c=h.component(data());c.run(Site.MAIN_AFTER_MODE,PRESENT,before);assertEquals(v[1],c.state().actor().motion().xVelocity());assertEquals(v[2],c.state().actor().motion().yVelocity());}}
    @Test void missingSineStopsAfterClearAndBeforeFirstVelocityStore(){var d=data();var h=new Harness(0x82);var c=h.component(new Data(d.blockLists(),d.bumperShapes(),Optional.empty(),d.arcTanBytes()));assertEquals(Boundary.SINE_WORD,assertThrows(Unowned.class,()->c.run(Site.MAIN_AFTER_MODE,PRESENT,actor(2,0,0,256,0))).boundary());assertEquals(List.of("NO_BUMPER=0"),h.actions);assertEquals(256,c.state().actor().motion().yVelocity());}
    @Test void mainRejectsEveryOtherRawRoutineBeforeClearingStateOrTouchingPorts() {
        var h=new Harness();var c=h.component(Data.unowned());var healthy=actor(2,0x2B,0,0,0);
        c.run(Site.MAIN_AFTER_MODE,PRESENT,healthy);var pool=h.pool.capture();var calls=c.calls();
        for(int routine=0;routine<256;routine++)if(routine!=2&&routine!=6){
            var rejected=actor(routine,0x2B,0,0,0);
            assertThrows(IllegalArgumentException.class,()->c.run(Site.MAIN_AFTER_MODE,PRESENT,rejected),"Impossible Main routine "+routine);
            assertSame(healthy,c.state());assertEquals(calls,c.calls());assertEquals(pool.slots(),h.pool.capture().slots());assertFalse(c.failed());
        }
        assertTrue(h.actions.isEmpty());assertTrue(h.reads.isEmpty());
    }
    @Test void hurtRejectsEveryOtherRawRoutineBeforeClearingStateOrTouchingPorts() {
        var h=new Harness();var c=h.component(Data.unowned());var healthy=actor(4,0x2B,0,0,0);
        c.run(Site.HURT_BEFORE_RECOVERY,PRESENT,healthy);var pool=h.pool.capture();var calls=c.calls();
        for(int routine=0;routine<256;routine++)if(routine!=4){
            var rejected=actor(routine,0x2B,0,0,0);
            assertThrows(IllegalArgumentException.class,()->c.run(Site.HURT_BEFORE_RECOVERY,PRESENT,rejected),"Impossible Hurt routine "+routine);
            assertSame(healthy,c.state());assertEquals(calls,c.calls());assertEquals(pool.slots(),h.pool.capture().slots());assertFalse(c.failed());
        }
        assertTrue(h.actions.isEmpty());assertTrue(h.reads.isEmpty());
    }

}
