package com.openggf.sprites.managers;

import com.openggf.game.GameServices;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.rules.GameRules;
import com.openggf.game.rules.PlayerAirMovementRules;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.kis2.Kis2PhysicsProvider;
import com.openggf.game.sonic2.kis2.Kis2Rules;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.level.ChunkDesc;
import com.openggf.level.LevelManager;
import com.openggf.level.SolidTile;
import com.openggf.physics.BackgroundPlaneCollisionProvider;
import com.openggf.physics.Direction;
import com.openggf.physics.GlideWallGrabTerrain;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import java.lang.reflect.Method;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Original Knuckles collision/response order: skdisasm044/8b Knux_DoLevelCollision_CheckRet
 * 32665..32854 and Knuckles_Glide 30739..30746; KiS2 c336fede s2.asm 40587..40744,
 * GlideControl 38177..38188. FixBugs=0. Declared port cases plus real GroundSensor
 * corners are components, not measured MHZ terrain returns or native route qualification.
 */
@ExtendWith(SingletonResetExtension.class)
@FullReset
@Isolated
class TestKnucklesGlideCollisionContract {
    private Knuckles p;
    private PlayableSpriteMovement movement;
    private Method collision;

    @BeforeEach void setUp() throws Exception { configure(false); }
    @AfterEach void tearDown() { SessionManager.clear(); }

    private void configure(boolean kis2) throws Exception {
        TestEnvironment.configureGameModuleFixture(kis2 ? new Kis2ComponentModule() : new Sonic3kGameModule());
        p = new Knuckles("glide-contract", (short)0, (short)0);
        p.setAir(true); p.setJumping(true); p.setRolling(false); p.applyCustomRadii(10,10);
        p.setCentreX((short)100); p.setCentreY((short)200); p.setSubpixelRaw(0xA500,0xB600);
        p.setDoubleJumpFlag(1); p.setDoubleJumpProperty((byte)0); p.setGSpeed((short)0x400);
        p.setXSpeed((short)0x400); p.setYSpeed((short)0x100); p.setAngle((byte)0);
        movement = new PlayableSpriteMovement(p);
        collision = PlayableSpriteMovement.class.getDeclaredMethod("doGlideCollision");
        collision.setAccessible(true);
    }
    private void collide() throws Exception { collision.invoke(movement); }
    private void fractions() {
        assertEquals(0xA500,p.getXSubpixelRaw()); assertEquals(0xB600,p.getYSubpixelRaw());
    }

    @ParameterizedTest
    @CsvSource({"true,0,3", "true,-44,0", "false,0,3", "false,-44,0"})
    void s3kFloorWinsAfterWallAndClearCeilingAtCorrectedCoordinates(boolean right,int angle,int flag) throws Exception {
        p.setXSpeed((short)(right ? 0x400 : -0x400));
        p.setDoubleJumpProperty((byte)(right ? 0 : 0x80));
        try (var h = new Ports()) {
            h.wall.put(right,-1); h.floorDistance=-2; h.floorAngle=(byte)angle;
            collide();
            int cx=right ? 99 : 101;
            assertEquals(List.of("W:"+right+":100:200:13:false",
                    "C:"+(cx+10)+":200:10:13:false", "C:"+(cx-10)+":200:10:13:false",
                    "F:"+(cx+10)+":210:12:false", "F:"+(cx-10)+":210:12:false"),h.calls);
            assertEquals(flag,p.getDoubleJumpFlag()); assertEquals(angle,p.getAngle());
            assertEquals(cx,p.getCentreX()); assertEquals(198,p.getCentreY()); fractions();
            assertEquals(flag==3,p.getAir()); assertFalse(h.aligned);
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void kis2CollectsFloorButWallResponseWinsWithGlideRadiiThroughAlignment(boolean right) throws Exception {
        configure(true); p.setXSpeed((short)(right ? 0x400 : -0x400));
        p.setDoubleJumpProperty((byte)(right ? 0 : 0x80));
        try (var h=new Ports()) {
            h.wall.put(right,-1); h.floorDistance=-2; h.align=true;
            collide();
            assertEquals(4,p.getDoubleJumpFlag()); assertTrue(h.aligned);
            assertEquals(List.of(10,10),h.alignRadii); assertEquals(right,h.alignRight);
            assertEquals(2,h.calls.stream().filter(x->x.startsWith("F:")).count());
            assertTrue(h.calls.get(h.calls.size()-1).startsWith("A:"));
            assertEquals(9,p.getXRadius()); assertEquals(19,p.getYRadius());
            assertEquals(p.getCentreX()&0xFFFF,p.getXSubpixelRaw()); assertEquals(0xB600,p.getYSubpixelRaw());
        }
    }

    @ParameterizedTest
    @CsvSource({"0,0,W:false;W:true;F;F", "0,1024,W:false;W:true;F;F",
            "0,-1024,W:false;W:true;C;C", "1024,0,W:true;C;C;F;F", "-1024,0,W:false;C;C;F;F",
            "1024,1024,W:false;W:true;F;F", "-1024,1024,W:false;C;C;F;F",
            "-1024,-1024,W:false;W:true;C;C", "1024,-1024,W:true;C;C"})
    void originalVelocitySelectsNativeQuadrantIncludingZeroAndDiagonals(int vx,int vy,String sequence) throws Exception {
        p.setXSpeed((short)vx);p.setYSpeed((short)vy);
        try(var h=new Ports()) {
            collide();
            assertEquals(Arrays.asList(sequence.split(";")),h.calls.stream().map(s->s.startsWith("W:") ? s.substring(0,s.indexOf(':',2)) : s.substring(0,1)).toList());
            assertEquals(1,p.getDoubleJumpFlag()); fractions();
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void downAndUpScanBothWallsBeforeVerticalProbeUsingSuccessiveCorrections(boolean up) throws Exception {
        p.setXSpeed((short)0);p.setYSpeed((short)(up ? -0x400 : 0x400));
        try(var h=new Ports()) {
            h.wall.put(false,-2);h.wall.put(true,-3);h.floorDistance=-1;h.ceilingDistance=-1;
            collide();
            assertEquals("W:false:100:200:13:false",h.calls.get(0));
            assertEquals("W:true:102:200:13:false",h.calls.get(1));
            assertTrue(h.calls.get(2).startsWith((up ? "C:109:200" : "F:109:210")));
            assertEquals(up ? 201 : 199,p.getCentreY()); assertEquals(99,p.getCentreX());
            assertEquals(0,p.getYSpeed()); fractions();
            assertEquals(up ? 0 : 2,h.calls.stream().filter(x->x.startsWith("F:")).count());
        }
    }

    @ParameterizedTest
    @CsvSource({"false,19,219,false", "false,20,200,true", "true,20,220,false"})
    void asymmetricHorizontalCeilingPenetrationControlsOppositeWallFallback(boolean right,int depth,int y,boolean opposite) throws Exception {
        p.setXSpeed((short)(right ? 0x400 : -0x400));
        try(var h=new Ports()) {
            h.ceilingDistance=-depth;h.floorDistance=-1;
            collide();
            assertEquals(y,p.getCentreY()); assertEquals(0x100,p.getYSpeed()); fractions();
            assertEquals(0,h.calls.stream().filter(x->x.startsWith("F:")).count());
            assertEquals(opposite ? 2 : 1,h.calls.stream().filter(x->x.startsWith("W:")).count());
            if(opposite)assertEquals("W:true:100:200:13:false",h.calls.get(h.calls.size()-1));
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void horizontalCeilingStopsOnlyNegativeStoredY(boolean rising) throws Exception {
        p.setYSpeed((short)(rising ? -0x100 : 0x100));
        try(var h=new Ports()) {
            h.ceilingDistance=-1;collide();
            assertEquals(rising ? 0 : 0x100,p.getYSpeed()); assertEquals(201,p.getCentreY()); fractions();
            assertEquals(0,h.calls.stream().filter(x->x.startsWith("F:")).count());
        }
    }

    @Test void reverseCeilingFloorWrapperLeavesTopBitForSubsequentReverseFloor() throws Exception {
        GameServices.gameState().setReverseGravityActive(true);
        p.setTopSolidBit((byte)14);p.setLrbSolidBit((byte)13);
        try(var h=new Ports()) {
            h.floorDistance=8;h.ceilingDistance=-2;h.ceilingAngle=(byte)0x80;
            collide();
            assertEquals(List.of("W:true:100:200:13:true", "F:110:210:14:true", "F:90:210:14:true",
                    "C:110:200:10:14:true", "C:90:200:10:14:true"),h.calls);
            assertEquals(202,p.getCentreY()); assertEquals(0,p.getAngle()); assertEquals(3,p.getDoubleJumpFlag()); fractions();
        }
    }

    @Test void reverseDeepLeftCeilingFallbackInheritsTopBitAndCollisionPath() throws Exception {
        GameServices.gameState().setReverseGravityActive(true);
        p.setTopSolidBit((byte)14);p.setLrbSolidBit((byte)13);p.setXSpeed((short)-0x400);
        try(var h=new Ports()) {
            h.floorDistance=-20;collide();
            assertEquals(List.of("W:false:100:200:13:true", "F:110:210:14:true", "F:90:210:14:true", "W:true:100:200:14:true"),h.calls);
            assertEquals(200,p.getCentreY()); fractions();
        }
    }

    @ParameterizedTest
    @CsvSource({"0,true", "64,false", "128,false", "192,true"})
    void zeroXWallGrabUsesSignedBytePropertyRatherThanCorrectedVelocity(int property,boolean right) throws Exception {
        p.setXSpeed((short)0);p.setYSpeed((short)0x400);p.setDoubleJumpProperty((byte)property);
        try(var h=new Ports()) {
            h.wall.put(false,-1);h.align=true;collide();
            assertEquals(4,p.getDoubleJumpFlag()); assertEquals(right,h.alignRight);
            assertEquals(right ? Direction.RIGHT : Direction.LEFT,p.getDirection());
            assertEquals(p.getCentreX()&0xFFFF,p.getXSubpixelRaw());
        }
    }

    @Test void responsePrecedenceIsAnIndependentSemanticRuleWithCompatibleDefault() {
        assertTrue(GameRules.SONIC_3K.playerMovement().air().glideFloorContactPrecedesWallGrab());
        assertFalse(Kis2Rules.RULES.playerMovement().air().glideFloorContactPrecedesWallGrab());
        assertFalse(new PlayerAirMovementRules(true,false,true,true).glideFloorContactPrecedesWallGrab());
        assertTrue(new PlayerAirMovementRules(true,true,true,true,true).glideFloorContactPrecedesWallGrab());
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void realGroundSensorInnerCornerCollectsWallAndFloorBeforeNativeResponse(boolean kis2) throws Exception {
        configure(kis2);var world=new TerrainWorld();var context=TestEnvironment.activeGameplayMode();
        context.attachLevelManagers(context.getWaterSystem(),context.getParallaxManager(),context.getTerrainCollisionManager(),context.getCollisionSystem(),context.getSpriteManager(),world.level);
        context.attachBackgroundPlaneCollisionProvider(BackgroundPlaneCollisionProvider.FOREGROUND_ONLY);
        // Right wall: distance 15-width15-lowX1=-1. Corrected X=$126.
        // Floor at Y=$211: 15-height15-lowY1=-1. Both fractions survive WORD writes.
        world.put(0x130,0x1F0,0,15,0x40,0x2000);
        world.put(0x130,0x200,0,15,0x40,0x2000);
        world.put(0x130,0x210,15,15,0,0x3000);
        world.put(0x110,0x210,15,0,0,0x1000);
        p.setCentreX((short)0x127);p.setCentreY((short)0x207);
        p.setSubpixelRaw(0xA500,0xB600); // declare fractions after WORD-zeroing fixture setters
        p.setXSpeed((short)0x100);p.setYSpeed((short)0x400);
        collide();
        assertEquals(0x126,p.getCentreX()); assertEquals(0x206,p.getCentreY());
        assertEquals(kis2 ? 4 : 3,p.getDoubleJumpFlag()); assertTrue(p.getAir());
        assertEquals(0xB600,p.getYSubpixelRaw());
        assertEquals(kis2 ? 0x126 : 0xA500,p.getXSubpixelRaw());
        assertTrue(world.points.contains(new Point(0x130,0x211)),"floor uses corrected X");
        if(kis2){assertEquals(9,p.getXRadius());assertEquals(19,p.getYRadius());}
        else{assertEquals(10,p.getXRadius());assertEquals(10,p.getYRadius());}
    }

    @Test void explicitWallBitDoesNotReplaceSelectedCollisionPathAndDefaultRemainsUnchanged() throws Exception {
        var world=new TerrainWorld();var context=TestEnvironment.activeGameplayMode();
        context.attachLevelManagers(context.getWaterSystem(),context.getParallaxManager(),context.getTerrainCollisionManager(),context.getCollisionSystem(),context.getSpriteManager(),world.level);
        context.attachBackgroundPlaneCollisionProvider(BackgroundPlaneCollisionProvider.FOREGROUND_ONLY);
        world.put(0x130,0x200,0,15,0x40,0x2000);
        p.setCentreX((short)0x127);p.setCentreY((short)0x207);
        p.setTopSolidBit((byte)14);p.setLrbSolidBit((byte)13);
        assertEquals(-1,GlideWallGrabTerrain.glideWallDistance(p,true,13,true).distance());
        assertTrue(world.lookups.contains("13:true"));assertFalse(world.lookups.contains("13:false"));
        world.lookups.clear();
        assertEquals(-1,GlideWallGrabTerrain.glideWallDistance(p,true).distance());
        assertTrue(world.lookups.contains("13:false"),"legacy entry retains bit-derived path");
    }

    private final class Ports implements AutoCloseable {
        final List<String> calls=new ArrayList<>(); final Map<Boolean,Integer> wall=new HashMap<>();
        int floorDistance=8,ceilingDistance=8;byte floorAngle=0,ceilingAngle=(byte)0x80;
        boolean align=false,aligned=false,alignRight;List<Integer> alignRadii;
        final MockedStatic<GlideWallGrabTerrain> walls=mockStatic(GlideWallGrabTerrain.class);
        final MockedStatic<ObjectTerrainUtils> terrain=mockStatic(ObjectTerrainUtils.class);
        Ports(){
            walls.when(()->GlideWallGrabTerrain.glideWallDistance(eq(p),anyBoolean(),anyInt(),anyBoolean())).thenAnswer(c->{
                boolean right=c.getArgument(1);calls.add("W:"+right+":"+p.getCentreX()+":"+p.getCentreY()+":"+c.getArgument(2)+":"+c.getArgument(3));
                return new TerrainCheckResult(wall.getOrDefault(right,8),(byte)0,1);
            });
            terrain.when(()->ObjectTerrainUtils.checkPlayerFloorDistWithFlipAwareAngle(anyInt(),anyInt(),anyInt(),anyBoolean())).thenAnswer(c->{
                calls.add("F:"+c.getArgument(0)+":"+c.getArgument(1)+":"+c.getArgument(2)+":"+c.getArgument(3));return new TerrainCheckResult(floorDistance,floorAngle,2);
            });
            terrain.when(()->ObjectTerrainUtils.checkPlayerCeilingDistWithFlipAwareAngle(anyInt(),anyInt(),anyInt(),anyInt(),anyBoolean())).thenAnswer(c->{
                calls.add("C:"+c.getArgument(0)+":"+c.getArgument(1)+":"+c.getArgument(2)+":"+c.getArgument(3)+":"+c.getArgument(4));return new TerrainCheckResult(ceilingDistance,ceilingAngle,3);
            });
            walls.when(()->GlideWallGrabTerrain.align(eq(p),anyBoolean(),anyBoolean())).thenAnswer(c->{
                aligned=true;alignRight=c.getArgument(1);alignRadii=List.of((int)p.getXRadius(),(int)p.getYRadius());calls.add("A:"+alignRight);return align;
            });
        }
        public void close(){terrain.close();walls.close();}
    }
    private static final class Kis2ComponentModule extends DelegatingGameModule {
        Kis2ComponentModule(){super(new Sonic2GameModule(),"test:kis2-glide-contract");}
        @Override public PhysicsProvider getPhysicsProvider(){return new Kis2PhysicsProvider();}
        @Override public GameRules getRules(){return Kis2Rules.RULES;}
    }
    private record Point(int x,int y){}
    private record Profile(ChunkDesc desc,SolidTile tile){}
    private static final class TerrainWorld {
        final LevelManager level=mock(LevelManager.class);
        final Map<Point,Profile> cells=new HashMap<>();final Map<ChunkDesc,SolidTile> profiles=new HashMap<>();final List<Point> points=new ArrayList<>();final List<String> lookups=new ArrayList<>();
        TerrainWorld(){
            when(level.getChunkDescAt(anyByte(),anyInt(),anyInt())).thenAnswer(c->descriptor(c.getArgument(1),c.getArgument(2)));
            when(level.getChunkDescAt(anyByte(),anyInt(),anyInt(),anyBoolean())).thenAnswer(c->descriptor(c.getArgument(1),c.getArgument(2)));
            doCallRealMethod().when(level).getSolidTileForChunkDesc(any(ChunkDesc.class),anyInt());
            when(level.getSolidTileForChunkDesc(any(ChunkDesc.class),anyInt(),anyBoolean())).thenAnswer(c->{ChunkDesc desc=c.getArgument(0);lookups.add(c.getArgument(1)+":"+c.getArgument(2));return desc.isSolidityBitSet(c.getArgument(1))?profiles.get(desc):null;});
        }
        void put(int x,int y,int height,int width,int angle,int flags){int index=profiles.size()+1;var desc=new ChunkDesc(index|flags);byte[] heights=new byte[16],widths=new byte[16];Arrays.fill(heights,(byte)height);Arrays.fill(widths,(byte)width);var tile=new SolidTile(index,heights,widths,(byte)angle);cells.put(new Point(x,y),new Profile(desc,tile));profiles.put(desc,tile);}
        ChunkDesc descriptor(int x,int y){points.add(new Point(x,y));var profile=cells.get(new Point(x&~15,y&~15));return profile==null?null:profile.desc();}
    }
}
