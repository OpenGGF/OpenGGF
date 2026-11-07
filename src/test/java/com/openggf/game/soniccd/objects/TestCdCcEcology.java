package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Original media operands, constructed live component ingress. No World/PLC/exit/native playback claim.
 * Native N1ecc5f3a CC Animal:38-259, Projector:77-157, Flower:32-144, Level End:211-339. */
class TestCdCcEcology {
    static final CdNativeFile[] FILES={CdNativeFile.R31A,CdNativeFile.R31B,CdNativeFile.R31C,CdNativeFile.R31D};
    static final List<CdR31EcologyEndAssets> all=new ArrayList<>();
    static CdR31aAssets stage;static CdPlayerMath math;static CdPlayerTerrain terrain;static CdSolidObject solid;
    @BeforeAll static void media()throws Exception {
        String root=System.getProperty("soniccd.media.root");Assumptions.assumeTrue(root!=null&&!root.isBlank(),"Explicit Sonic CD media root");
        Path cue;try(var paths=Files.list(Path.of(root).resolve("USA"))){cue=paths.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).findFirst().orElseThrow();}
        String[] hashes={"30bb932c3f7a18f0629dcee56fb982280e0b6d6063fb67392b23771f77e7682e","2a7dbe5463233d4cfb69b34dfcf480f8bec93407918d53883ee1d96771247862","cdb48963c2661c537c19986f2f360ce89bc39d8dab0974d05ebca6557fefa987","6980ad899736183083261cd57e05d897f76e01d9da517e9a07d82145b3af229d"};
        try(var programs=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            for(int i=0;i<4;i++){
                var file=FILES[i];var image=programs.mmd(file);assertEquals(hashes[i],sha256(image.readFile(0,image.fileBytes())));
                all.add(new CdR31EcologyEndAssets(new CdR31EcologyEndAssets.Source(){public CdNativeFile file(){return file;}public CdAssetBytes borrow(CdR31EcologyEndAssets.View v){return CdAssetBytes.copyOf(image.readFile(v.fileOffset(file),v.length()));}}));
            }
            stage=CdR31aAssets.fromPrograms(programs,CdStagePayload.R31A.stage());
        }
        math=new CdPlayerMath(CdR11aPlayerAssets.fromR31aAssets(stage));terrain=CdPlayerTerrain.fromStageTerrain(stage.terrain());
        solid=new CdSolidObject(CdPlayerMovement.fromStageSource(CdStagePlayerSource.fromR31aAssets(stage)));
    }
    private static String sha256(byte[] bytes){
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    static CdObjectPlacementRuntime placement(){return CdObjectPlacementRuntime.fromR31aAssets(stage,CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]));}
    @Test void allFourIndependentFiniteOperandSetsDecodeWithoutFallbacks(){
        for(var a:all){a.flower();a.bigRing();a.goal();a.signpost();a.results();a.gameOver();a.robot();a.projector();a.animal();a.capsuleVisual();for(var v:CdR31EcologyEndAssets.View.values())if(!v.name().startsWith("CHECKPOINT")||a.file()==CdNativeFile.R31A)assertEquals(v.sha256(),sha256(a.view(v).copy()));}
        all.getFirst().checkpoint(math);for(var a:all.subList(1,4))assertThrows(IllegalArgumentException.class,()->a.checkpoint(math));
    }
    @Test void falseBorrowedViewFailsBeforeProducingFamilyAssets(){
        var a=new CdR31EcologyEndAssets(new CdR31EcologyEndAssets.Source(){public CdNativeFile file(){return CdNativeFile.R31A;}public CdAssetBytes borrow(CdR31EcologyEndAssets.View v){return CdAssetBytes.copyOf(new byte[v.length()]);}});
        assertThrows(IllegalArgumentException.class,a::animal);
    }
    @Test void flyingInitReturnsWithoutDrawAndPreservesFractionsAndDirtyFields(){
        var h=new H(0,0);h.edit(s->s.moveWord(X_FRACTION,0xABCD).moveWord(Y_FRACTION,0x5678).moveByte(FLAGS,0xA4));h.run();
        assertEquals(2,h.s().routine());assertEquals(0x83DA,h.s().word(TILE));assertEquals(0x101,h.s().word(ANTON_ANCHOR_X));assertEquals(0xA5,h.s().flags());assertEquals(0xABCD,h.s().word(X_FRACTION));assertEquals(0x5678,h.s().word(Y_FRACTION));assertEquals(0,h.draws);
    }
    @Test void signedPresenceOrderSkipsDirtyRoutineWithoutReadingFutureOrAct(){
        var h=new H(0,0xFE);h.destroyed=0;h.period=0x80;h.camera=0x1000;h.run();assertEquals(0,h.s().id());assertFalse(h.reads.contains(CdCcAnimal.Field.GOOD_FUTURE));assertFalse(h.reads.contains(CdCcAnimal.Field.ACT));
    }
    @Test void badFutureDeletesEntireImageWithoutSavedBankClear(){
        var h=new H(1,0xFE);h.period=2;h.good=0;h.edit(s->s.moveByte(SAVED_FLAGS_ID,1).moveWord(X_FRACTION,0xBEEF));var before=h.p.capture().savedFlags();h.run();assertEquals(0,h.s().id());assertArrayEquals(before.copy(),h.p.capture().savedFlags().copy());assertFalse(h.reads.contains(CdCcAnimal.Field.PROJECTOR_DESTROYED));
    }
    @Test void hologramIgnoresPresenceGlobalsAndChecksRawSignExtendedParent(){
        var h=new H(0x80,4);h.edit(s->s.moveWord(EXPLOSION_POINTS_FRAME,0x8123).moveByte(RAMP_TIMER,124));h.run();assertEquals(List.of(0xFF8123,0xFF8162),h.addresses);assertEquals(0,h.draws);assertEquals(128,h.s().unsignedByte(RAMP_TIMER));assertFalse(h.reads.contains(CdCcAnimal.Field.TIME_ZONE));
    }
    @Test void rawPositiveParentIsNotPromotedIntoFfRam(){var h=new H(0x81,8);h.parentId=0;h.edit(s->s.moveWord(EXPLOSION_POINTS_FRAME,0x1234));h.run();assertEquals(List.of(0x1234),h.addresses);assertEquals(0,h.s().id());}
    @Test void parentInvalidFlagDeletesBeforeAnimationOrDrawing(){var h=new H(0x81,8);h.parentFlag=1;h.run();assertEquals(0,h.s().id());assertEquals(0,h.draws);}
    @Test void flyingOrbitReflectsAtNativeEndpointAndUsesOldPhaseForPosition(){
        var h=new H(0,2);h.edit(s->s.moveWord(CHECKPOINT_BALL_X,0x1000).moveWord(CHECKPOINT_BALL_Y,0x200).moveByte(RAMP_TIMER,127).moveByte(SWING_COUNT,1).moveByte(ANIMATION_TIME,1));
        var trig=math.calcSine(127);h.run();assertEquals(0x1000+(trig.cosine()>>1),h.s().centreX());assertEquals(127,h.s().unsignedByte(RAMP_TIMER));assertEquals(255,h.s().unsignedByte(SWING_COUNT));assertEquals(1,h.draws);
    }
    @Test void groundInitUsesLongVelocitiesAndHologramInitKeepsDirtyMotion(){
        var h=new H(1,0);h.run();assertEquals(0x10000,h.s().longBits(TAMABBOH_X_DELTA));assertEquals(-0x40000,h.s().longBits(CHECKPOINT_PARENT));assertEquals(0,h.draws);
        var g=new H(0x81,0);g.edit(s->s.moveLong(TAMABBOH_X_DELTA,0xDEADBEEF));g.run();assertEquals(8,g.s().routine());assertEquals(0xDEADBEEF,g.s().longBits(TAMABBOH_X_DELTA));
    }
    @Test void groundTurnNegatesLongAndReturnsToBounceWithoutFloor(){var h=new H(1,6);h.edit(s->s.moveLong(TAMABBOH_X_DELTA,0x10001));h.run();assertEquals(2,h.s().routine());assertEquals(-0x10001,h.s().longBits(TAMABBOH_X_DELTA));assertEquals(1,h.draws);}
    @Test void baseTileFaultRetainsInitPrefixAndNoDraw(){var h=new H(0,0);h.act=255;assertThrows(CdCcAnimal.MissingNativeOperand.class,h::run);assertEquals(2,h.s().routine());assertEquals(all.getFirst().animal().flyingAddress(),h.s().longBits(MAPPINGS));assertEquals(0,h.draws);}
    @Test void completedCallCannotBeReusedAfterDispatch(){var h=new H(0x81,8);h.run();assertFalse(h.body.isCurrentCall(h.last));assertThrows(IllegalStateException.class,()->((CdCcAnimal.Draw)h.last).complete());}
    @Test void readFaultPreservesOriginalAndCompletePoolPrefix(){var h=new H(0,0);var original=new IllegalStateException("literal");h.fault=original;var before=h.p.capture();assertSame(original,assertThrows(IllegalStateException.class,h::run));assertEquals(before.pool().slots(),h.p.capture().pool().slots());}
    @Test void capsuleSharedEntryHasSameNoAllocationPrefixWhenSoundFails(){
        var p=placement();p.pool().write(p.pool().state(80).moveByte(ID,0x15).moveByte(ROUTINE,4).moveByte(LEVEL_END_TIMER,5));
        var palette=new CdR13Capsule.PaletteOperands(4,0xFB20,23,CdAssetBytes.copyOf(new byte[96]));
        var original=new IllegalStateException("fm");var body=CdR13Capsule.collisionChaos(p,all.getFirst().capsule(palette),terrain,new CdR13Capsule.Ports(){public CdR13Capsule.Completion fm(CdR13Capsule.FmCall c){throw original;}});
        assertSame(original,assertThrows(IllegalStateException.class,()->body.run(80)));assertEquals(4,p.pool().state(80).unsignedByte(LEVEL_END_TIMER));assertEquals(0,p.pool().state(32).id());
    }
    @Test void ccProjectorChildFailurePreservesEarlierOffspringAndFixedTile(){
        var p=placement();for(int i=32;i<128;i++)p.pool().write(p.pool().state(i).moveByte(ID,0x7F));
        for(int i=32;i<35;i++)p.pool().write(p.pool().state(i).moveByte(ID,0).moveWord(X_FRACTION,0xBEEF));
        p.pool().write(p.pool().state(80).moveByte(ID,0x38).moveWord(X,0x1000).moveWord(Y,0x200));
        var body=projector(p);body.run(80,new CdProjector.Globals(0,0,1,0,0x1000),null);
        assertEquals(0,p.pool().state(80).id());assertEquals(0x38,p.pool().state(32).id());assertEquals(0x39,p.pool().state(34).id());assertEquals(0xFFD000+80*64,p.pool().state(32).word(EXPLOSION_POINTS_FRAME)|0xFF0000);assertEquals(0xBEEF,p.pool().state(34).word(X_FRACTION));
    }
    @Test void ccProjectorFourOffspringUseLiteralIdsOffsetsAndTileWithoutActRead(){
        var p=placement();p.pool().write(p.pool().state(80).moveByte(ID,0x38).moveWord(X,0x1000).moveWord(Y,0x200));
        var body=projector(p);
        assertThrows(AssertionError.class,()->body.run(80,new CdProjector.Reads(){public int projDestroyed(){return 0;}public int act(){throw new AssertionError("CC does not read act for tile");}public int timeZone(){return 1;}public int timeWarpDirection(){return 0;}public int cameraXWord(){return 0x1000;}},new CdProjector.Inputs(new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(new CdPlayerMotionState(0,0,0,0,0,6,0,0,4,9,19,0,0,0,0,0,0,0,0,0),0x780,2,0,0),1,0,0),new CdSolidObject.Gates(0,0),new CdSolidObject.Globals(0,4,0,0))));
        assertEquals(0x33E,p.pool().state(80).word(TILE));assertEquals(0x39,p.pool().state(35).id());
        assertEquals(0x1000-0x62,p.pool().state(35).centreX());assertEquals(0x200,p.pool().state(35).centreY());
    }
    @Test void ccRobotUsesIdentity37AndNoReadAfterTheGoodFutureGate(){
        var p=placement();p.pool().write(p.pool().state(80).moveByte(ID,0x37).moveWord(X,0x1000).moveWord(Y,0x200));
        var b=CdRobotGenerator.collisionChaos(p,all.getFirst().robot(),solid,c->{throw new AssertionError("Solid");},c->{},c->{},c->{},c->p.pool().write(c.expectedSource().moveByte(SPRITE_FLAGS,c.expectedSource().spriteFlags()&127)),new CdRobotGenerator.DispatchObserver(){public void beforePublication(CdRobotGenerator.Publication c){}public void afterPublication(CdRobotGenerator.Publication c){}public void beforeCall(Object c){}public void afterCall(Object c){}},new CdRobotGenerator.ReceiptGuard(){public void prepared(Object c){}public void completed(Object c){}});
        b.runWithReads(80,new CdRobotGenerator.Globals(1,1,0,0,0x1000),null);assertEquals(0x37,p.pool().state(80).id());assertEquals(2,p.pool().state(80).routine());assertEquals(0x33E,p.pool().state(80).word(TILE));
    }
    @Test void ccRobotExplosionStoresChildBeforeFmFaultAndDoesNotReadGlobals(){
        var p=placement();var a=all.getFirst().robot();p.pool().write(p.pool().state(80).moveByte(ID,0x37).moveByte(ROUTINE,4).moveLong(ROBOT_GENERATOR_SCHEDULE,a.explosionScheduleAddress()).moveWord(X,0x1000).moveWord(Y,0x200));
        var original=new IllegalStateException("FM");
        var b=CdRobotGenerator.collisionChaos(p,a,solid,c->{throw new AssertionError("Solid");},c->{},c->{},c->{throw original;},c->{throw new AssertionError("Draw after failed FM");},new CdRobotGenerator.DispatchObserver(){public void beforePublication(CdRobotGenerator.Publication c){}public void afterPublication(CdRobotGenerator.Publication c){}public void beforeCall(Object c){}public void afterCall(Object c){}},new CdRobotGenerator.ReceiptGuard(){public void prepared(Object c){}public void completed(Object c){}});
        assertSame(original,assertThrows(IllegalStateException.class,()->b.runWithReads(80,null,null)));assertEquals(0x18,p.pool().state(32).id());assertEquals(1,p.pool().state(32).unsignedByte(ROUTINE_2));assertEquals(a.explosionScheduleAddress()+3,p.pool().state(80).longBits(ROBOT_GENERATOR_SCHEDULE));
    }

    @Test void ccRobotOutsideDespawnReadsPeriodOnlyForTheReachedSavedClear(){
        for(int savedId:new int[]{0,1}) {
            var p=placement();p.pool().write(p.pool().state(80).moveByte(ID,0x37).moveByte(ROUTINE,2)
                    .moveByte(SAVED_FLAGS_ID,savedId).moveByte(SPRITE_FLAGS,0x81).moveWord(X,0x1000).moveWord(Y,0x200));
            var before=p.capture();var status=p.status();var events=new ArrayList<String>();int[] periodReads={0};
            var body=ccRobotForReadOrder(p,events,c->{throw new AssertionError("unexpected goodFuture store");},c->{throw new AssertionError("unexpected points");});
            body.runWithReads(80,new CdRobotGenerator.Reads(){
                public int goodFuture(){events.add("good");return 1;}
                public int timeZone(){events.add("period");if(savedId==0||++periodReads[0]>1)throw new AssertionError("period read outside its native cut");return 1;}
                public int act(){throw new AssertionError("act");}public int timeWarpDirection(){throw new AssertionError("direction");}
                public int cameraXWord(){events.add("camera");return 0;}
            },null);
            assertArrayEquals(new byte[64],p.pool().state(80).copyBytes());
            assertEquals(savedId==0?List.of("good","draw","camera"):List.of("good","draw","camera","period"),events);
            assertEquals(savedId,periodReads[0]);assertEquals(status,p.status());
            assertArrayEquals(before.savedFlags().copy(),p.capture().savedFlags().copy());
            for(int n=32;n<128;n++)if(n!=80)assertEquals(before.pool().slots().get(n-32),p.pool().state(n),"Nonwritten neighbour "+n);
        }
    }
    @Test void ccRobotHitCarriesTheGateValueThroughTheStoreBeforePointsFailure(){
        var p=placement();p.pool().write(p.pool().state(80).moveByte(ID,0x37).moveByte(ROUTINE,2)
                .moveByte(SPRITE_FLAGS,0x81).moveByte(COLLISION_TYPE,0xFA).moveByte(COLLISION_STATUS,0xA3)
                .moveWord(X,0x1000).moveWord(Y,0x200).moveWord(Y_FRACTION,0xBEEF)
                .moveWord(ROBOT_GENERATOR_TIMER,7).moveWord(ROBOT_GENERATOR_FLOAT_STEP,3));
        var before=p.capture();var status=p.status();var expected=p.pool().state(80).moveWord(Y,0x203)
                .moveByte(COLLISION_TYPE,0).moveByte(COLLISION_STATUS,0).moveWord(ROBOT_GENERATOR_TIMER,0)
                .moveByte(MAP_FRAME,7).moveByte(ROUTINE,4);
        var events=new ArrayList<String>();int[] gateReads={0},good={0};var pointFault=new IllegalStateException("points reached");
        var body=ccRobotForReadOrder(p,events,c->{
            assertEquals(0,c.expectedGoodFuture());assertEquals(1,c.value());assertEquals(expected,c.expectedSource());
            events.add("storeGood");good[0]=c.value();
        },c->{assertEquals(expected,c.expectedSource());events.add("points");throw pointFault;});
        assertSame(pointFault,assertThrows(IllegalStateException.class,()->body.runWithReads(80,new CdRobotGenerator.Reads(){
            public int goodFuture(){events.add("good");if(++gateReads[0]>1)throw new AssertionError("second non-native goodFuture read");return good[0];}
            public int timeZone(){events.add("period");return 0;}
            public int act(){throw new AssertionError("act");}public int timeWarpDirection(){throw new AssertionError("direction");}
            public int cameraXWord(){throw new AssertionError("camera after failed points");}
        },null)));
        assertEquals(List.of("good","period","storeGood","points"),events);assertEquals(1,gateReads[0]);assertEquals(1,good[0]);
        assertEquals(expected,p.pool().state(80));assertEquals(status,p.status());
        assertArrayEquals(before.savedFlags().copy(),p.capture().savedFlags().copy());
        for(int n=32;n<128;n++)if(n!=80)assertEquals(before.pool().slots().get(n-32),p.pool().state(n),"Nonwritten neighbour "+n);
    }
    private static CdRobotGenerator ccRobotForReadOrder(CdObjectPlacementRuntime p,List<String> events,
            CdRobotGenerator.GoodFutureStore good,CdRobotGenerator.AddPointsCall points){
        return CdRobotGenerator.collisionChaos(p,all.getFirst().robot(),solid,c->{throw new AssertionError("Unexpected Solid");},
                good,points,c->{throw new AssertionError("Unexpected FM");},c->{events.add("draw");p.pool().write(c.expectedSource().moveByte(SPRITE_FLAGS,c.expectedSource().spriteFlags()&127));},
                new CdRobotGenerator.DispatchObserver(){public void beforePublication(CdRobotGenerator.Publication c){}public void afterPublication(CdRobotGenerator.Publication c){}public void beforeCall(Object c){}public void afterCall(Object c){}},
                new CdRobotGenerator.ReceiptGuard(){public void prepared(Object c){}public void completed(Object c){}});
    }

    static CdProjector projector(CdObjectPlacementRuntime p){return CdProjector.collisionChaos(p,all.getFirst().projector(),solid,c->{throw new AssertionError("Unexpected Solid");},c->{},c->{},c->{},c->p.pool().write(c.expectedSource().moveByte(SPRITE_FLAGS,c.expectedSource().spriteFlags()&127)),new CdProjector.DispatchObserver(){public void beforePublication(CdProjector.Publication c){}public void afterPublication(CdProjector.Publication c){}public void beforeCall(Object c){}public void afterCall(Object c){}},new CdProjector.ReceiptGuard(){public void prepared(Object c){}public void completed(Object c){}});}
    static final class H {
        final CdObjectPlacementRuntime p=placement();final CdCcAnimal body;int period=1,good=1,destroyed=1,act=0,camera=0x1000,parentId=0x38,parentFlag=0,draws;RuntimeException fault;CdCcAnimal.Call last;
        final List<CdCcAnimal.Field> reads=new ArrayList<>();final List<Integer> addresses=new ArrayList<>();
        H(int subtype,int routine){p.pool().write(p.pool().state(80).moveByte(ID,0x39).moveByte(SUBTYPE,subtype).moveByte(ROUTINE,routine).moveWord(X,0x1000).moveWord(Y,0x200));
            body=new CdCcAnimal(p,all.getFirst().animal(),math,terrain,new CdCcAnimal.Ports(){
                public CdCcAnimal.Value read(CdCcAnimal.Read c){last=c;assertTrue(body.isCurrentCall(c));if(fault!=null)throw fault;reads.add(c.field());if(c.field()==CdCcAnimal.Field.PARENT_ID||c.field()==CdCcAnimal.Field.PARENT_FLAG)addresses.add(c.nativeAddress());return c.complete(switch(c.field()){case TIME_ZONE->period;case GOOD_FUTURE->good;case PROJECTOR_DESTROYED->destroyed;case ACT->act;case CAMERA_X->camera;case PARENT_ID->parentId;case PARENT_FLAG->parentFlag;default->0;});}
                public CdCcAnimal.Done angle(CdCcAnimal.Angle c){return c.complete();}
                public CdCcAnimal.Done draw(CdCcAnimal.Draw c){last=c;draws++;p.pool().write(c.expectedSource().moveByte(SPRITE_FLAGS,c.expectedSource().spriteFlags()&127));return c.complete();}
            });}
        CdObjectState s(){return p.pool().state(80);}void edit(java.util.function.UnaryOperator<CdObjectState> f){p.pool().write(f.apply(s()));}void run(){body.run(80);}
    }
}
