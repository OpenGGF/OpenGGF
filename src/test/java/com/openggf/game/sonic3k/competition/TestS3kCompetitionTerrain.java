package com.openggf.game.sonic3k.competition;

import com.openggf.data.RomManager;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3k;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.competition.S3kCompetitionPlayer.Character;
import com.openggf.game.sonic3k.competition.S3kCompetitionTerrain.Geometry;
import com.openggf.game.sonic3k.competition.S3kCompetitionTerrain.Probe;
import com.openggf.level.ChunkDesc;
import com.openggf.level.LevelManager;
import com.openggf.level.SolidTile;
import com.openggf.physics.Direction;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Literal native branch oracles, independent of the player's movement implementation. */
@Isolated
class TestS3kCompetitionTerrain {
    private record Query(Direction direction,int x,int y,int solidity,boolean secondary) { }
    private static final class Probes implements Geometry {
        final List<Query> calls=new ArrayList<>();
        final Map<Direction,List<Probe>> replies=new HashMap<>();
        @Override public Probe probe(Direction direction,int x,int y,int solidity,boolean secondary) {
            calls.add(new Query(direction,x,y,solidity,secondary));
            List<Probe> queue=replies.get(direction);
            return queue==null || queue.isEmpty()?new Probe(31,3,0):queue.removeFirst();
        }
        void add(Direction d,Probe... results){replies.computeIfAbsent(d,k->new ArrayList<>()).addAll(List.of(results));}
    }
    private static Probe result(int distance,int angle){return new Probe(distance,angle,1);}
    private static S3kCompetitionPlayer player(int slot,Character character){return new S3kCompetitionPlayer(slot,character,0x600,200);}
    private static S3kCompetitionTerrain terrain(Probes probes){return new S3kCompetitionTerrain(probes,p->{});}

    @Test void miniatureFloorPairUsesActualRadiiNearestSensorAndPreservesSubpixels() {
        var probes=new Probes();probes.add(Direction.DOWN,result(-2,0x10),result(-4,0x18));
        var p=player(0,Character.SONIC);p.setAngle(0x10);p.setFractions(0x1234,0x5678);
        terrain(probes).followGround(p);
        assertEquals(List.of(new Query(Direction.DOWN,0x605,211,0xC,false),new Query(Direction.DOWN,0x5FB,211,0xC,false)),probes.calls);
        assertEquals(196,p.centreY());assertEquals(0x18,p.angle());
        assertEquals(0x1234,p.xFraction());assertEquals(0x5678,p.yFraction());assertFalse(p.airborne());
    }
    @Test void equalDistanceSelectsSecondaryAngleAndLargeAngleJumpsSnapToCardinal() {
        var probes=new Probes();var p=player(0,Character.SONIC);
        probes.add(Direction.DOWN,result(-1,0x10),result(-1,0x12));terrain(probes).followGround(p);
        assertEquals(0x12,p.angle());
        p.setCentre(0x600,200);p.setAngle(0);
        probes.add(Direction.DOWN,result(0,0x20),result(0,0x20));terrain(probes).followGround(p);
        assertEquals(0,p.angle(),"native unsigned >=$20 angle jump snaps, rather than adopting the distant surface");
    }
    @Test void groundPenetrationAndGapEdgesUseNativeFourteenAndIntegerVelocityByte() {
        var probes=new Probes();var p=player(0,Character.SONIC);var terrain=terrain(probes);
        probes.add(Direction.DOWN,result(-15,0x10),result(-15,0x10));terrain.followGround(p);
        assertEquals(200,p.centreY());assertEquals(0x10,p.angle(),"angle is selected before the deep-penetration rejection");
        p.setAngle(0);probes.add(Direction.DOWN,result(-14,0),result(-14,0));terrain.followGround(p);assertEquals(186,p.centreY());
        p.setCentre(0x600,200);p.setXVelocity(0x3FF);
        probes.add(Direction.DOWN,result(8,0),result(8,0));terrain.followGround(p);
        assertTrue(p.airborne());assertEquals(200,p.centreY());assertEquals(1,p.animationState().previousAnimation());
        p.setAirborne(false);probes.add(Direction.DOWN,result(7,0),result(7,0));terrain.followGround(p);
        assertFalse(p.airborne());assertEquals(207,p.centreY(),"integer speed3 plus4 accepts a seven-pixel positive gap");
    }
    @Test void rotatedGroundAttachmentKeepsTopSolidityAndNativeAxisSigns() {
        int[] angles={0x40,0x80,0xC0};Direction[] directions={Direction.LEFT,Direction.UP,Direction.RIGHT};
        for(int i=0;i<angles.length;i++) {
            var p=player(1,Character.TAILS);p.setAngle(angles[i]);p.setSolidBits(0xE,0xF);
            var probes=new Probes();probes.add(directions[i],result(-3,angles[i]),result(-3,angles[i]));terrain(probes).followGround(p);
            assertEquals(0xE,probes.calls.getFirst().solidity());assertTrue(probes.calls.getFirst().secondary());
            assertEquals(i==0?0x603:i==2?0x5FD:0x600,p.centreX());assertEquals(i==1?203:200,p.centreY());
        }
        var p=player(0,Character.SONIC);p.setStandingObject(7);var probes=new Probes();terrain(probes).followGround(p);
        assertTrue(probes.calls.isEmpty(),"standing-object ownership skips terrain attachment");
    }
    @Test void nativeAttachmentQuadrantBoundaryDiffersFromSimplePlusThirtyTwoRounding() {
        int[] angles={0x20,0x21,0x60,0xA0,0xA1,0xE0};
        Direction[] expected={Direction.DOWN,Direction.LEFT,Direction.UP,Direction.UP,Direction.RIGHT,Direction.DOWN};
        for(int i=0;i<angles.length;i++) {
            var p=player(0,Character.SONIC);p.setAngle(angles[i]);var probes=new Probes();terrain(probes).followGround(p);
            assertEquals(expected[i],probes.calls.getFirst().direction(),"angle $"+Integer.toHexString(angles[i]));
        }
    }
    @Test void jumpClearanceUsesPairedMiniatureHeadSensorsAndSixPixelAcceptance() {
        var probes=new Probes();var p=player(0,Character.KNUCKLES);var terrain=terrain(probes);
        probes.add(Direction.UP,result(6,0x80),result(5,0x80));assertFalse(terrain.canJump(p,6));
        assertEquals(new Query(Direction.UP,0x605,189,0xD,false),probes.calls.getFirst());
        probes.add(Direction.UP,result(6,0x80),result(7,0x80));assertTrue(terrain.canJump(p,6));
        p.setAngle(0x80);probes.add(Direction.DOWN,result(6,0),result(6,0));assertTrue(terrain.canJump(p,6));
        assertEquals(0xC,probes.calls.getLast().solidity(),"the downward overhead branch reloads top solidity");
    }
    @Test void groundedWallPredictsFractionalMotionUsesXRadiusAndAdjustsVelocityBeforeMovement() {
        var probes=new Probes();var p=player(0,Character.SONIC);
        p.setGroundVelocity(0x100);p.setXVelocity(0x100);p.setFractions(0xFF00,0x1234);
        probes.add(Direction.RIGHT,result(-1,0xC0));terrain(probes).resolveGroundWall(p);
        assertEquals(new Query(Direction.RIGHT,0x606,200,0xD,false),probes.calls.getFirst());
        assertEquals(0,p.xVelocity());assertEquals(0,p.groundVelocity());assertTrue(p.pushing());assertEquals(0x600,p.centreX());
        assertEquals(0xFF00,p.xFraction());
        p.setObjectControl(0x40);p.setGroundVelocity(0x100);probes.calls.clear();terrain(probes).resolveGroundWall(p);
        assertTrue(probes.calls.isEmpty());
    }
    @Test void collisionTableSelectionIsIndependentOfTheRequestedSolidityBit() {
        var p=player(0,Character.SONIC);p.setSolidBits(0xC,0xF);p.setGroundVelocity(0x100);p.setXVelocity(0x100);
        var probes=new Probes();terrain(probes).resolveGroundWall(p);
        assertEquals(0xF,probes.calls.getFirst().solidity());assertFalse(probes.calls.getFirst().secondary(),"native top_solid_bit selects the collision-index table");
        p.setSolidBits(0xE,0xD);probes.calls.clear();terrain(probes).resolveGroundWall(p);
        assertEquals(0xD,probes.calls.getFirst().solidity());assertTrue(probes.calls.getFirst().secondary());
    }
    @Test void fallingLandingRestoresMiniatureRadiusWithNativeCentreCorrectionForBothSlots() {
        for(int slot=0;slot<2;slot++) {
            var p=player(slot,slot==0?Character.SONIC:Character.TAILS);p.setStatus(2|4|0x10|0x20);
            p.setRadii(7,14);p.setJumping(true);p.setDoubleJumpFlag(1);p.setFlipAngle(0x40);
            p.setXVelocity(0x100);p.setYVelocity(0x400);p.setFractions(0x1234,0x5678);
            var probes=new Probes();probes.add(Direction.DOWN,result(-2,0),result(-2,0));terrain(probes).resolveAir(p);
            assertFalse(p.airborne());assertFalse(p.rolling());assertFalse(p.rollingJump());assertFalse(p.pushing());
            assertFalse(p.jumping());assertEquals(5,p.xRadius());assertEquals(11,p.yRadius());
            assertEquals(201,p.centreY());assertEquals(0x100,p.groundVelocity());assertEquals(0,p.yVelocity());
            assertEquals(0,p.doubleJumpFlag());assertEquals(0,p.flipAngle());assertEquals(0x5678,p.yFraction());
        }
    }
    @Test void fallingPenetrationGateReadsBothFeetAndHighVelocityByte() {
        var p=player(0,Character.SONIC);p.setAirborne(true);p.setYVelocity(0x100);
        var probes=new Probes();probes.add(Direction.DOWN,result(-10,0),result(-10,0));terrain(probes).resolveAir(p);
        assertTrue(p.airborne());assertEquals(200,p.centreY());
        probes.add(Direction.DOWN,result(-10,0),result(-8,0));terrain(probes).resolveAir(p);
        assertFalse(p.airborne());assertEquals(190,p.centreY(),"a shallower second foot admits the nearest negative distance");
    }
    @Test void slopeLandingSelectsHorizontalHalfVerticalOrSteepVerticalMomentum() {
        // loc_11F7A/loc_11F9C: $F0+$10 wraps to zero, so that exact
        // negative edge keeps horizontal momentum; $E8 takes ASR and NEG.
        int[] angles={0,0x10,0xF0,0xE8,0x40};int[] expected={0x100,0x200,0x100,-0x200,0x400};
        for(int i=0;i<angles.length;i++) {
            var p=player(0,Character.SONIC);p.setAirborne(true);p.setXVelocity(0x100);p.setYVelocity(0x400);
            var probes=new Probes();probes.add(Direction.DOWN,result(-1,angles[i]),result(-1,angles[i]));terrain(probes).resolveAir(p);
            assertEquals(expected[i],p.groundVelocity(),"landing angle $"+Integer.toHexString(angles[i]));assertFalse(p.airborne());
            if(i==4) assertEquals(0,p.xVelocity());
        }
    }
    @Test void leftAndRightAirCeilingBranchesPreserveTheNativeTwentyPixelAsymmetry() {
        for(boolean left:List.of(true,false)) {
            var p=player(0,Character.SONIC);p.setAirborne(true);p.setXVelocity(left?-0x400:0x400);
            var probes=new Probes();probes.add(Direction.UP,result(-20,0x80),result(-20,0x80));terrain(probes).resolveAir(p);
            assertEquals(left?200:220,p.centreY());assertTrue(p.airborne());
            assertEquals(left?Direction.RIGHT:Direction.UP,probes.calls.getLast().direction());
        }
    }
    @Test void ascendingFlatCeilingStopsVelocityWhileSteepCeilingCanAttach() {
        for(int angle:new int[]{0x80,0xC0}) {
            var p=player(1,Character.TAILS);p.setAirborne(true);p.setYVelocity(-0x400);
            var probes=new Probes();probes.add(Direction.UP,result(-3,angle),result(-3,angle));terrain(probes).resolveAir(p);
            assertEquals(203,p.centreY());
            if(angle==0x80) {assertTrue(p.airborne());assertEquals(0,p.yVelocity());}
            else {assertFalse(p.airborne());assertEquals(0x400,p.groundVelocity());assertEquals(0xC0,p.angle());}
        }
    }
    @Test void spindashLandingSkipsRadiusRollAndAnimationResetButClearsAirFlags() {
        var p=player(0,Character.SONIC);p.setStatus(2|4|0x10|0x20);p.setRadii(3,7);
        p.setSpinDashFlag(1);p.setAnimation(9);p.setJumping(true);p.setYVelocity(0x200);
        var probes=new Probes();probes.add(Direction.DOWN,result(-1,0),result(-1,0));terrain(probes).resolveAir(p);
        assertFalse(p.airborne());assertFalse(p.rollingJump());assertFalse(p.jumping());assertTrue(p.rolling());
        assertEquals(3,p.xRadius());assertEquals(7,p.yRadius());assertEquals(9,p.animation());assertEquals(199,p.centreY());
    }
    @Test void itemAndArenaProbesShareRealNativeCoordinatesAndWordWritesWrapWithoutLosingFractions() {
        var probes=new Probes();var terrain=terrain(probes);
        terrain.floorDistance(0xFFFF,0xFFFF,8,0xE);terrain.leftWallDistance(3,0xFFFF,8,0xF);terrain.floorDistance(0xFFFF,0x10000);
        assertEquals(List.of(new Query(Direction.DOWN,0xFFFF,7,0xE,true),new Query(Direction.LEFT,0xFFFB,0xFFFF,0xF,true),
                new Query(Direction.DOWN,0xFFFF,0,0xC,false)),probes.calls);
        var p=player(0,Character.SONIC);p.setCentre(0x600,1);p.setFractions(0x1234,0x5678);
        probes.add(Direction.DOWN,result(-3,0),result(-3,0));terrain.followGround(p);
        assertEquals(-2,p.centreY());assertEquals(0x5678,p.yFraction());
    }
    @Test void deathCallbackRunsOnTheSameNativePlayerIdentity() {
        var p=player(1,Character.TAILS);var seen=new ArrayList<S3kCompetitionPlayer>();
        new S3kCompetitionTerrain(new Probes(),seen::add).resolveDeath(p);assertEquals(List.of(p),seen);
    }
    @Test void productionDeathAdapterUsesP1CameraForBothSlotsAndRestoresForward() {
        var world=new CompetitionTestWorld(S3kCompetitionSession.Mode.MATCH_RACE);
        var level=mock(LevelManager.class);int[] cameraReads={0};
        var terrain=new S3kCompetitionTerrain(level,0x1FF,()->world.session,()->{cameraReads[0]++;return 100;});
        for(var p:world.players) {
            world.session.applyImmediateItem(p.slot(),0);world.session.applyImmediateItem(p.slot(),3);
            p.kill();p.setCentre(0x777,244);p.setSpinDashFlag(7);
            terrain.resolveDeath(p);
            assertEquals(6,p.routine(),"P1 camera100+$90==244 is excluded for both slots");
            assertEquals(0,p.spinDashFlag());
            p.setCentreNative(0x777,245);p.setFractions(0x1234,0x5678);p.setYVelocity(0);
        }
        assertEquals(2,cameraReads[0]);
        var before=world.session.capture();
        var input=new S3kCompetitionPlayer.Input(0,0);
        var bounds=new S3kCompetitionPlayer.Bounds(0x3FF,0x1FF,false,-0x100);
        for(var p:world.players) p.tick(input,terrain,bounds,0);
        var expected=world.session.capture();
        for(var p:world.players) {
            assertEquals(p.slot()==0?984:980,p.centreX());assertEquals(200,p.centreY());
            assertEquals(0x1234,p.xFraction());assertEquals(0x5678,p.yFraction());
            assertEquals(2,p.routine());assertEquals(0x38,p.yVelocity(),"respawn still falls through death gravity");
            assertEquals(0,p.speedShoesTimer());assertEquals(0,world.session.rings(p.slot()));
            assertEquals(p.slot()==0?0x600:0x4C0,p.speeds().maxSpeed());
            assertTrue(expected.players().get(p.slot()).respawnPending());
        }
        world.session.restore(before);
        for(var p:world.players) p.tick(input,terrain,bounds,0);
        assertEquals(expected,world.session.capture());assertEquals(6,cameraReads[0]);
        verifyNoInteractions(level);
    }
    @Test void productionDeathThresholdAddsNinetyWithNativeSignedWordWrap() {
        var world=new CompetitionTestWorld(S3kCompetitionSession.Mode.MATCH_RACE);
        int[] camera={0x7FF0};
        var terrain=new S3kCompetitionTerrain(mock(LevelManager.class),0x1FF,()->world.session,()->camera[0]);
        // Literal native ADDI.W #$100 / SUBI.W #$70 results, followed by signed CMP.W.
        int[][] cases={{0x7FF0,0x8080},{0xFF80,0x0010}};
        for(int[] nativeCase:cases) for(var p:world.players) {
            camera[0]=nativeCase[0];p.kill();p.setCentre(0x777,nativeCase[1]);
            terrain.resolveDeath(p);assertEquals(6,p.routine(),"equal signed word does not respawn");
            p.setCentreNative(0x777,nativeCase[1]+1);terrain.resolveDeath(p);
            assertEquals(2,p.routine());assertEquals(p.slot()==0?984:980,p.centreX());assertEquals(200,p.centreY());
        }
    }

    private static SolidTile shape(int id,int height,int width,int angle) {
        byte[] heights=new byte[16],widths=new byte[16];Arrays.fill(heights,(byte)height);Arrays.fill(widths,(byte)width);
        return new SolidTile(id,heights,widths,(byte)angle);
    }
    private static final class Tiles {
        final LevelManager level=mock(LevelManager.class);
        final Map<Long,ChunkDesc> descriptors=new HashMap<>();final Map<Integer,SolidTile> shapes=new HashMap<>();
        final List<String> lookups=new ArrayList<>();
        Tiles() {
            when(level.getChunkDescAt(eq((byte)0),anyInt(),anyInt())).thenAnswer(call->{
                int x=call.getArgument(1),y=call.getArgument(2);lookups.add(x+":"+y);
                return descriptors.get(key(x,y));
            });
            when(level.getSolidTileForChunkDesc(any(ChunkDesc.class),anyInt(),anyBoolean())).thenAnswer(call->{
                ChunkDesc desc=call.getArgument(0);int bit=call.getArgument(1);
                return desc.isSolidityBitSet(bit)?shapes.get(desc.getChunkIndex()):null;
            });
        }
        void put(int x,int y,int id,int flags,SolidTile shape) {descriptors.put(key(x,y),new ChunkDesc(id|flags));shapes.put(id,shape);}
        private static long key(int x,int y){return ((long)(x&0xFFF0)<<32)|(y&0xFFF0L);}
    }
    @Test void productionGeometryUsesNativeRegressExtensionAngleWritesAndEmptyDistances() {
        var tiles=new Tiles();var geometry=new S3kCompetitionTerrain.LevelGeometry(tiles.level,0x1FF);
        assertEquals(24,geometry.probe(Direction.DOWN,100,119,0xC,false).distance());
        assertEquals(23,geometry.probe(Direction.UP,100,119,0xD,false).distance());
        tiles.put(100,116,1,0x1000,shape(1,16,0,0x10));
        var regress=geometry.probe(Direction.DOWN,100,116,0xC,false);
        assertEquals(-5,regress.distance());assertEquals(0x10,regress.angle());
        tiles.put(100,116,1,0x1000,shape(1,0,0,0x10));tiles.put(100,132,2,0x1000,shape(2,0,0,0x20));
        var extension=geometry.probe(Direction.DOWN,100,116,0xC,false);
        assertEquals(27,extension.distance());assertEquals(0x20,extension.angle(),"zero-height extension still writes its angle");
    }
    @Test void productionGeometryReadsFlippedActualHeightMapsAndAppliesArenaLayoutYMask() {
        var tiles=new Tiles();var geometry=new S3kCompetitionTerrain.LevelGeometry(tiles.level,0x1FF);
        byte[] gradient=new byte[16],widths=new byte[16];for(int i=0;i<16;i++) gradient[i]=(byte)(i+1);
        tiles.put(100,117,1,0x1000|0x400,new SolidTile(1,gradient,widths,(byte)0x20));
        var hflip=geometry.probe(Direction.DOWN,100,117,0xC,false);
        assertEquals(-2,hflip.distance());assertEquals(0xE0,hflip.angle());
        tiles.put(100,116,2,0x2000|0x800,shape(2,8,8,0x20));
        var ceiling=geometry.probe(Direction.UP,100,116,0xD,false);
        assertEquals(-4,ceiling.distance());assertEquals(0x60,ceiling.angle());
        geometry.probe(Direction.UP,0xFFFF,0xFFFF,0xD,false);
        assertTrue(tiles.lookups.contains("65535:511"));assertTrue(tiles.lookups.contains("65535:495"));
    }
    @Test @RequiresRom(SonicGame.SONIC_3K)
    void realAzureLakeRomGeometrySuppliesNativeFloorAndMiniatureLandingWithoutCampaignBoot() throws Exception {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        TestEnvironment.activeGameplayMode();
        try {
            var rom=RomManager.getInstance().getRom();var game=new Sonic3k(rom);
            var prepared=game.prepareLevelBuildTask(0xE*2,null).call();
            var level=GameServices.level();level.setLevel(prepared.level());
            assertEquals(0xE,prepared.level().getZoneIndex());
            var definition=S3kCompetitionArenaDefinition.load(rom,S3kCompetitionSession.Arena.AZURE_LAKE);
            var nativeGeometry=new S3kCompetitionTerrain.LevelGeometry(level,0x1FF);
            var terrain=new S3kCompetitionTerrain(nativeGeometry,p->{});
            int comparisons=0,landingX=-1,landingProbeY=0,landingDistance=0,landingAngle=0;
            int pairedSurfaces=0,negativePairs=0,admissiblePairs=0,oddAngles=0,steepAngles=0;
            var pairSamples=new ArrayList<String>();var negativeSamples=new ArrayList<String>();
            // Independent existing native floor oracle, sampling the actual decoded
            // ROM start neighborhood. No sprite, controller, title or level boot runs.
            for(int x=definition.startX()-24;x<=definition.startX()+24;x+=4) for(int y=definition.startY()-16;y<=definition.startY()+64;y+=4) {
                var expected=ObjectTerrainUtils.checkFloorDistOnLayer(level,x,y&0x1FF,(byte)0,false,true);
                if(!expected.foundSurface() || expected.distance()< -14 || expected.distance()>14) continue;
                var actual=nativeGeometry.probe(Direction.DOWN,x,y,0xC,false);
                assertEquals(expected.distance(),actual.distance(),"ROM floor x="+x+" y="+y);
                assertEquals(expected.angle()&255,actual.angle());comparisons++;
                if(landingX<0) {
                    var left=ObjectTerrainUtils.checkFloorDistOnLayer(level,x-5,y&0x1FF,(byte)0,false,true);
                    var right=ObjectTerrainUtils.checkFloorDistOnLayer(level,x+5,y&0x1FF,(byte)0,false,true);
                    var selected=left.distance()<=right.distance()?left:right;
                    String sample="("+x+","+y+") L="+left+" R="+right;
                    if(pairSamples.size()<6) pairSamples.add(sample);
                    if(left.foundSurface() && right.foundSurface()) {
                        pairedSurfaces++;
                        if(selected.distance()<0) {
                            negativePairs++;if(negativeSamples.size()<10) negativeSamples.add(sample);
                            if(selected.distance()>=-8) admissiblePairs++;
                            if((selected.angle()&1)!=0) oddAngles++;
                            if(Math.abs((byte)selected.angle())>0x20) steepAngles++;
                        }
                    }
                    // loc_11F7A's floor entry converts an odd collision angle
                    // to the cardinal floor angle. ALZ's actual tile $54/$55
                    // uses $FF, so that native sentinel is a valid flat floor.
                    int nativeAngle=(selected.angle()&1)!=0?0:selected.angle()&255;
                    if(left.foundSurface() && right.foundSurface() && selected.distance()<0
                            && selected.distance()>=-8 && Math.abs((byte)nativeAngle)<=0x20) {
                        landingX=x;landingProbeY=y;landingDistance=selected.distance();landingAngle=nativeAngle;
                    }
                }
            }
            assertTrue(comparisons>=10,"actual ALZ start must supply multiple native collision samples");
            assertTrue(landingX>=0,"actual ALZ paired landing: start=("+definition.startX()+","+definition.startY()
                    +"), comparisons="+comparisons+", pairs="+pairedSurfaces+", negative="+negativePairs
                    +", selected>=-8="+admissiblePairs+", odd="+oddAngles+", steep="+steepAngles
                    +", samples="+pairSamples+", negativeSamples="+negativeSamples);
            var p1=player(0,Character.SONIC);var p2=player(1,Character.TAILS);
            for(var p:List.of(p1,p2)) {
                p.setCentre(landingX,landingProbeY-11);p.setAirborne(true);p.setXVelocity(0x100);p.setYVelocity(0x100);
                terrain.resolveAir(p);assertFalse(p.airborne());assertEquals(landingX,p.centreX());
                assertEquals(landingProbeY-11+landingDistance,p.centreY());assertEquals(landingAngle,p.angle());
                // Independent literal loc_11F7A branch oracle, using the actual
                // ROM sensor-selected angle rather than requiring a flat floor.
                int expectedY=((landingAngle+0x20)&0x40)!=0?0x100:((landingAngle+0x10)&0x20)!=0?0x80:0;
                int expectedGround=expectedY==0?0x100:((byte)landingAngle<0?-expectedY:expectedY);
                assertEquals(expectedY,p.yVelocity());assertEquals(expectedGround,p.groundVelocity());
                var left=ObjectTerrainUtils.checkFloorDistOnLayer(level,p.centreX()-5,p.centreY()+11,(byte)0,false,true);
                var right=ObjectTerrainUtils.checkFloorDistOnLayer(level,p.centreX()+5,p.centreY()+11,(byte)0,false,true);
                assertEquals(0,Math.min(left.distance(),right.distance()),"native pair lands on the actual ROM slope");
            }
        } finally {SessionManager.clear();GameModuleRegistry.reset();}
    }
}
