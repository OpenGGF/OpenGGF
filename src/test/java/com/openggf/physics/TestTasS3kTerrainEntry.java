package com.openggf.physics;

import com.openggf.game.GameServices;
import com.openggf.game.GroundMode;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.CnzGiantWheelInstance;
import com.openggf.level.*;
import com.openggf.level.objects.*;
import com.openggf.game.sonic3k.objects.Sonic3kMonitorObjectInstance;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.managers.PlayableSpriteMovement;
import com.openggf.sprites.animation.ScriptedVelocityAnimationProfile;
import org.junit.jupiter.api.Test;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Representative declared geometry for 4087S stair clipping and CNZ slope retention.
 * Native 8b2cf3c: Sonic/Tails_DoLevelCollision's downward depth gate;
 * sub_328E8 airborne removal; Player_AnglePos's stick_to_convex branch.
 * A jump apex partially above a top-only ledge is a valid inlet, not a seeded clip.
 * Real GroundSensors calculate all distances; no mocked sensor result is supplied. */
class TestTasS3kTerrainEntry {
    @AfterEach void cleanup() { GroundSensor.setLevelManager(null); SessionManager.clear(); }

    @ParameterizedTest @ValueSource(ints={7,8})
    void downwardStairClipPassesThroughLedgeOnlyBeyondTheNativeDepthGate(int initialFootDepth) {
        var collision=world(1);
        var p=new Tails("tails",(short)0,(short)0);
        p.setCentreX((short)0x400); p.setCentreY((short)(0x300+initialFootDepth-p.getYRadius()));
        p.setAir(true);p.setYSpeed((short)0x100);p.setXSpeed((short)0);p.setJumping(false);
        // MoveSprite precedes the collision probes; the first fall is one pixel.
        int initialY=p.getCentreY();
        p.move(p.getXSpeed(),p.getYSpeed());
        assertEquals(initialY+1,p.getCentreY());
        var floor=new GroundSensor(p,Direction.DOWN,(byte)0,(byte)p.getYRadius(),true).scan();
        assertNotNull(floor);assertEquals(-(initialFootDepth+2),floor.distance());
        collision.resolveAirCollision(p,sprite->sprite.setAir(false));
        if(initialFootDepth==7) {
            assertFalse(p.getAir(),"depth -9 equals -(1+8): land control");
            assertEquals(0x300-p.getYRadius()-1,p.getCentreY());
        } else {
            assertTrue(p.getAir(),"both -10 probes exceed -9: clip is admitted");
            // Continue the fall out of the 16-pixel ledge, applying the native
            // collision tail every time. This observes the exit, not just a flag.
            for(int i=0;i<28;i++) { p.move((short)0,(short)0x100); collision.resolveAirCollision(p,s->s.setAir(false)); }
            assertTrue(p.getAir());assertTrue(p.getCentreY()>0x310);
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void airborneWheelDepartureRetainsAdhesionAndCrossesAnUnsupportedGap(boolean airborneDeparture) {
        try (var reel = com.openggf.tests.reel.S3kTestReelPresentation.open()) {
        world(4);
        var p=new Sonic("sonic",(short)0,(short)0); reel.observeParticipant(p);
        p.setCentreX((short)0x400);p.setCentreY((short)(0x300-p.getYRadius()-1));p.setAir(false);
        p.setXSpeed((short)0x800);p.setGSpeed((short)0x800);
        var camera=GameServices.camera();camera.setMinX((short)0);camera.setMaxX((short)0x1000);camera.setMaxY((short)0x1000);
        var movement=(PlayableSpriteMovement)p.getMovementManager();
        var wheel=new CnzGiantWheelInstance(new ObjectSpawn(0x400,0x2EC,0x49,0,0,false,0));
        wheel.update(0,p);
        assertTrue(p.isStickToConvex());assertEquals(0x800,p.getGSpeed());
        // Declared valid rolling-monitor contact inlet. The monitor owns both
        // bounce velocity and deferred standing-player release; no airborne,
        // landing or gap flag is written by this test after contact.
        if (airborneDeparture) {
            var monitor = new Sonic3kMonitorObjectInstance(new ObjectSpawn(0x400,0x300,1,3,0,false,0));
            monitor.setServices(new TestObjectServices());
            p.setOnObject(true);p.setYSpeed((short)0x400);
            monitor.onSolidContact(p,new SolidContact(true,false,false,true,false),1);
            p.setAnimationId(Sonic3kAnimationIds.ROLL);
            monitor.onTouchResponse(p,new TouchResponseResult(0,0x0E,0x0E,TouchCategory.SPECIAL),1);
            monitor.update(1,p);
            reel.afterObjects(java.util.List.of(monitor),3,0,0x360,0x290,com.openggf.game.sonic3k.Sonic3kObjectArtKeys.MONITOR);
            assertTrue(p.getAir());assertFalse(p.isOnObject());assertEquals(-0x400,p.getYSpeed());
        }
        wheel.update(1,p);
        boolean landedOutsideWheel=false;
        int landingX=0;
        for(int frame=2;frame<120 && p.getCentreX()<0x620;frame++) {
            boolean wasAir=p.getAir();
            movement.handleMovement(false,false,false,true,false,false,false,false);
            wheel.update(frame,p); // player physics, including landing, precedes object execution
            if(wasAir && !p.getAir()) {
                landedOutsideWheel=true;landingX=p.getCentreX();
                assertTrue(landingX>0x460 && landingX<0x600,"bounce lands on floor after leaving wheel");
                assertEquals(0x300-p.getYRadius()-1,p.getCentreY());
                assertTrue(p.isStickToConvex(),"real landing owner preserves stale wheel adhesion");
            }
        }
        assertTrue(p.getCentreX()>=0x620,"real movement reaches gap: x="+p.getCentreX()+" y="+p.getCentreY()+" air="+p.getAir()+" vx="+p.getXSpeed()+" vy="+p.getYSpeed()+" landingX="+landingX);
        assertEquals(airborneDeparture,landedOutsideWheel);
        assertEquals(airborneDeparture,p.isStickToConvex());
        assertEquals(!airborneDeparture,p.getAir(),"retained adhesion walks across gap; grounded departure falls");
            }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void knucklesWallJumpReversesAndPropelsWithoutLeftOrRight(boolean pressJump) {
        world(3);
        var p=new Knuckles("knuckles",(short)0,(short)0);
        p.applyCustomRadii(10,10);p.setCentreX((short)0x125);p.setCentreY((short)0x200);
        p.setSubpixelRaw(0x125,0);p.setDirection(Direction.RIGHT);p.setAir(true);
        p.setDoubleJumpFlag(4);p.setDoubleJumpProperty((byte)2);
        var camera=GameServices.camera();camera.setMinX((short)0);camera.setMaxX((short)2000);camera.setMaxY((short)2000);
        assertEquals(0,GlideWallGrabTerrain.distanceFromWall(p,p.getCentreY(),true).distance(),"declared climb anchor touches a real wall");
        p.setJumpInputPressed(pressJump,pressJump);
        ((PlayableSpriteMovement)p.getMovementManager()).handleMovement(false,false,false,false,pressJump,false,false,false);
        assertEquals(pressJump?Direction.LEFT:Direction.RIGHT,p.getDirection());
        assertEquals(pressJump?-0x400:0,p.getXSpeed());
        assertEquals(pressJump?-0x380:0,p.getYSpeed());
        assertEquals(pressJump?0:4,p.getDoubleJumpFlag());
        if(pressJump) {
            int x=p.getCentreX();p.setJumpInputPressed(false,false);
            ((PlayableSpriteMovement)p.getMovementManager()).handleMovement(false,false,false,false,false,false,false,false);
            assertTrue(p.getCentreX()<x,"neutral subsequent input moves away from wall");
        }
    }

    @ParameterizedTest @ValueSource(ints={0,6})
    void knucklesSpindashChargesReleasesAndTravelsWithoutLeftOrRight(int revs) {
        world(2);
        var p=new Knuckles("knuckles",(short)0,(short)0);
        p.setCentreX((short)0x400);p.setCentreY((short)(0x300-p.getYRadius()-1));
        p.setAir(false);p.setDirection(Direction.RIGHT);p.setAngle((byte)0);p.setGroundMode(GroundMode.GROUND);
        var camera=GameServices.camera();camera.setMinX((short)0);camera.setMaxX((short)0x1000);camera.setMaxY((short)0x1000);
        p.setAnimationProfile(new ScriptedVelocityAnimationProfile().setDuckAnimId(8).setSpindashAnimId(9).setRollAnimId(2));
        // Valid crouched inlet; fresh Down+jump must perform the charge itself.
        p.setAnimationId(8);p.setCrouching(true);
        var movement=(PlayableSpriteMovement)p.getMovementManager();
        movement.handleMovement(false,true,false,false,false,false,false,false);
        p.setJumpInputPressed(true,true);movement.handleMovement(false,true,false,false,true,false,false,false);
        assertTrue(p.getSpindash());
        for(int i=0;i<revs;i++) {
            p.setJumpInputPressed(false,false);movement.handleMovement(false,true,false,false,false,false,false,false);
            p.setJumpInputPressed(true,true);movement.handleMovement(false,true,false,false,true,false,false,false);
        }
        int start=p.getCentreX();p.setJumpInputPressed(false,false);
        movement.handleMovement(false,false,false,false,false,false,false,false);
        assertFalse(p.getSpindash());assertTrue(p.getRolling());
        assertEquals(revs==0?0x800:0xC00,p.getGSpeed(),"SonicKnux_Spindash release table, maximum charge clamps to eight");
        movement.handleMovement(false,false,false,false,false,false,false,false);
        assertTrue(p.getCentreX()>start);assertFalse(p.getAir());
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void lbzLiftWallEntryProducesRepeatedReverseCorrectionsAndExit(boolean wallPresent) {
        try (var reel = com.openggf.tests.reel.S3kTestReelPresentation.open()) {
        var collision=world(wallPresent?5:0);
        var p=new Sonic("sonic",(short)0,(short)0); reel.observeParticipant(p);
        p.setCentreX((short)0x430);p.setCentreY((short)0x1D0);p.setAir(true);
        var camera=GameServices.camera();camera.setX((short)0x300);camera.setY((short)0x140);
        camera.setMinX((short)0);camera.setMaxX((short)0x1000);camera.setMaxY((short)0x1000);
        var services=new TestObjectServices().withCamera(camera);
        var manager=new ObjectManager(java.util.List.of(),new com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry(),
                0,null,null,null,camera,services);
        services.withDirectObjectManager(manager);collision.setObjectManager(manager);
        var lift=manager.createDynamicObject(()->new com.openggf.game.sonic3k.objects.LbzMovingPlatformObjectInstance(
                new ObjectSpawn(0x430,0x200,0x11,0x88,0,false,0)));
        var movement=(PlayableSpriteMovement)p.getMovementManager();
        boolean acquiredRide=false;
        // Start outside the wall and above the platform. Actual floor contact
        // activates Platform_DiagonalLift; its carry acquires the embedding.
        for(int frame=0;frame<100;frame++) {
            movement.handleMovement(false,false,false,false,false,false,false,false);
            manager.update(camera.getX(),p,java.util.List.of(),frame,false,true,false);
            reel.afterObjects(java.util.List.of(lift),6,0,0x300,0x140,com.openggf.game.sonic3k.Sonic3kObjectArtKeys.LBZ_MOVING_PLATFORM);
            acquiredRide|=p.isOnObject();
        }
        assertTrue(acquiredRide,"real platform landing reached; x="+p.getCentreX()+" y="+p.getCentreY()+" platform="+lift.getX()+","+lift.getY());assertTrue(p.isOnObject());
        assertEquals(0x3B0,lift.getX());assertEquals(0x3B0,p.getCentreX());assertFalse(p.getAir());
        int corrections=0;
        for(int frame=100;frame<112;frame++) {
            int before=p.getCentreX();boolean grounded=!p.getAir();
            movement.handleMovement(false,false,true,false,false,false,false,false);
            manager.update(camera.getX(),p,java.util.List.of(),frame,false,true,false);
            reel.afterObjects(java.util.List.of(lift),6,0,0x300,0x140,com.openggf.game.sonic3k.Sonic3kObjectArtKeys.LBZ_MOVING_PLATFORM);
            int dx=p.getCentreX()-before;
            if(grounded && dx>=16)corrections++;
            if(wallPresent && frame<102) {
                // Retail loc_F576/sub_F584 yields -27, then -32 for this
                // full-cell wall. loc_113D6 subtracts that signed distance
                // shifted by eight from fresh Left acceleration (-12), and
                // clears ground_vel. These are native formulas, not fitted
                // velocities or gameplay values supplied to the player.
                assertTrue(grounded,"each correction enters the grounded movement owner");
                assertEquals(-12+(frame==100?27:32)*0x100,p.getXSpeed());
                assertEquals(0,p.getGSpeed());
            }
        }
        if(wallPresent) {
            assertTrue(corrections>=2,"loc_113D6 repeatedly moves right while Left is held");
            assertTrue(p.getCentreX()>0x410,"actual motion exits the far wall face");
        } else {
            assertEquals(0,corrections);assertTrue(p.getCentreX()<0x3B0,"same input moves left without embedded probes");
        }
            }
    }

    @Test void leftUnderflowUsesUnsignedRightBoundary() throws Exception {
        world(0);
        var p=new Sonic("sonic",(short)0,(short)0);
        var camera=GameServices.camera();camera.setMinX((short)0);camera.setMaxX((short)0x1000);camera.setMaxY((short)0x1000);
        p.setCentreX((short)16);p.setCentreY((short)0x100);p.setXSpeed((short)-0x2000);p.setGSpeed((short)-0x2000);
        var method=PlayableSpriteMovement.class.getDeclaredMethod("doLevelBoundary");method.setAccessible(true);
        method.invoke(p.getMovementManager());
        // Player_LevelBound's predicted word is $FFF0: BHI against $10 is false,
        // BLO against $1128 is true. Native writes RIGHT bound $1128, not $10.
        assertEquals(0x1128,p.getCentreX()&0xFFFF,"native unsigned underflow target");
        assertEquals(0,p.getXSpeed());
        p.setCentreX((short)32);p.setXSpeed((short)-0x100);
        method.invoke(p.getMovementManager());
        assertEquals(32,p.getCentreX());assertEquals(-0x100,p.getXSpeed(),"in-bounds control preserves motion");
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void slopeJumpAddsTheNativeNormalImpulseAndLeavesTheSlope(boolean uphill) {
        world(uphill?6:2);
        var p=new Sonic("sonic",(short)0,(short)0);
        // Valid running contact on an authored continuous 45-degree slope (or
        // flat control). The right floor probe is one pixel above the surface.
        p.setCentreX((short)0x400);p.setCentreY((short)(uphill?0x2F2:0x2EC));
        p.setAir(false);p.setAngle((byte)(uphill?0xE0:0));p.setGSpeed((short)0x600);
        p.setXSpeed((short)(uphill?1086:1536));p.setYSpeed((short)(uphill?-1086:0));
        var camera=GameServices.camera();camera.setMinX((short)0);camera.setMaxX((short)0x1000);camera.setMaxY((short)0x1000);
        var probe=new GroundSensor(p,Direction.DOWN,(byte)9,(byte)p.getYRadius(),true).scan();
        assertNotNull(probe);assertEquals(0,probe.distance());
        var movement=(PlayableSpriteMovement)p.getMovementManager();
        p.setJumpInputPressed(true,true);
        movement.handleMovement(false,false,false,false,true,false,false,false);
        // loc_1182E: angle-$40, signed CalcSine products, ASR8; no
        // SpeedToPos on this dispatch. E0 gives -1177 on each axis.
        assertTrue(p.getAir());assertTrue(p.isJumping());
        assertEquals(uphill?-91:1536,p.getXSpeed());
        assertEquals(uphill?-2263:-1664,p.getYSpeed());
        int y=p.getCentreY();p.setJumpInputPressed(true,false);
        for(int i=0;i<4;i++)movement.handleMovement(false,false,false,false,true,false,false,false);
        assertTrue(p.getAir());assertTrue(p.getCentreY()<y-20,"real airborne exit rises clear of surface");
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void ordinaryDamageLaunchMovesAwayFromTheSourceAndRejectsASecondHit(boolean sourceOnLeft) {
        world(0);
        var p=new Sonic("sonic",(short)0,(short)0);
        p.setCentreX((short)0x400);p.setCentreY((short)0x200);p.setAir(false);
        var camera=GameServices.camera();camera.setMinX((short)0);camera.setMaxX((short)0x1000);camera.setMaxY((short)0x1000);
        // HurtCharacter entry after the touch owner has established rings.
        // No hurt flags or launch velocity are supplied by the fixture.
        assertTrue(p.applyHurtOrDeath(sourceOnLeft?0x3F0:0x410,false,true));
        assertTrue(p.isHurt());assertTrue(p.getAir());assertFalse(p.getDead());
        assertEquals(sourceOnLeft?0x200:-0x200,p.getXSpeed());assertEquals(-0x400,p.getYSpeed());
        assertEquals(0,p.getGSpeed());assertEquals(120,p.getInvulnerableFrames());
        assertFalse(p.applyHurtOrDeath(sourceOnLeft?0x410:0x3F0,false,true),"fresh i-frames reject the opposite knockback");
        var movement=(PlayableSpriteMovement)p.getMovementManager();
        for(int i=0;i<4;i++)movement.handleMovement(false,false,false,false,false,false,false,false);
        assertEquals(0x400+(sourceOnLeft?8:-8),p.getCentreX());assertTrue(p.getCentreY()<0x200);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void stationaryMeshRangeUnloadRetainsStandingWallSuppressionAndPose(boolean unload) {
        var collision=world(0);
        var p=new Knuckles("knuckles",(short)0,(short)0);
        p.setCentreX((short)(0x700-0xB8));p.setCentreY((short)0x400);p.setAir(false);
        p.setXSpeed((short)0x600);p.setGSpeed((short)0x600);
        // Declared valid mesh-mouth inlet. Capture, all native control flags,
        // range deletion, and the subsequent unsupported-floor traversal run.
        var camera=GameServices.camera();camera.setX((short)0x600);camera.setY((short)0x380);
        camera.setMinX((short)0);camera.setMaxX((short)0x2000);camera.setMaxY((short)0x1000);
        var services=new TestObjectServices(){@Override public ObjectPlayerQuery playerQuery(){return new ObjectPlayerQuery(()->p,java.util.List::of);}}.withCamera(camera);
        var manager=new ObjectManager(java.util.List.of(),new com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry(),0,null,null,null,camera,services);
        services.withDirectObjectManager(manager);collision.setObjectManager(manager);
        var cage=manager.createDynamicObject(()->new com.openggf.game.sonic3k.objects.FbzWireCageStationaryObjectInstance(new ObjectSpawn(0x700,0x400,0x70,0,0,false,0)));
        manager.update(camera.getX(),p,java.util.List.of(),0,false,true,false);
        assertTrue(p.isOnObject());assertTrue(p.isObjectMappingFrameControl());assertTrue(p.isSuppressGroundWallCollision());
        int capturedPose=p.getMappingFrame();assertNotEquals(0,capturedPose);
        if(unload)camera.setX((short)0x1000); // valid camera seam; no player state is changed
        var movement=(PlayableSpriteMovement)p.getMovementManager();
        for(int frame=1;frame<=100;frame++){
            movement.handleMovement(false,false,false,true,false,false,false,false);
            manager.update(camera.getX(),p,java.util.List.of(),frame,false,true,false);
        }
        assertTrue(p.getCentreX()>0x7C0,"actual movement leaves mesh extent");
        assertEquals(unload,cage.isDestroyed());
        assertEquals(unload,p.isOnObject());assertEquals(unload,p.isSuppressGroundWallCollision());
        assertEquals(unload,p.isObjectMappingFrameControl());assertEquals(!unload,p.getAir());
        if(unload){assertEquals(capturedPose,p.getMappingFrame());assertFalse(manager.getActiveObjects().contains(cage));}
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void freshGlideIntoMonitorReboundsUpwardAndRetainsGlide(boolean monitorPresent) {
        try (var reel = com.openggf.tests.reel.S3kTestReelPresentation.open()) {
        var collision=world(0);var p=new Knuckles("knuckles",(short)0,(short)0); reel.observeParticipant(p);
        // Declared falling-jump inlet beside a monitor. Fresh release/repress
        // must activate glide, acquire the touch, reverse Y, and fly clear.
        p.setCentreX((short)0x3D0);p.setCentreY((short)0x2D0);p.setAir(true);p.setJumping(true);
        p.setRolling(true);p.applyRollingRadii(true);p.setXSpeed((short)0x400);p.setYSpeed((short)0x600);
        p.setDirection(Direction.RIGHT);
        var camera=GameServices.camera();camera.setX((short)0x380);camera.setY((short)0x280);
        camera.setMinX((short)0);camera.setMaxX((short)0x1000);camera.setMaxY((short)0x1000);
        // Touch_Sizes[6] at $FF6E contains $10,$10 in the pinned ROM.
        var table=mock(TouchResponseTable.class);when(table.getWidthRadius(6)).thenReturn(16);when(table.getHeightRadius(6)).thenReturn(16);
        var debug=mock(com.openggf.debug.DebugOverlayManager.class);
        var services=new TestObjectServices().withCamera(camera).withDebugOverlay(debug);
        var manager=new ObjectManager(java.util.List.of(),new com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry(),0,null,table,null,camera,services);
        services.withDirectObjectManager(manager);collision.setObjectManager(manager);
        var monitor=manager.createDynamicObject(()->new Sonic3kMonitorObjectInstance(new ObjectSpawn(monitorPresent?0x400:0x800,0x300,1,3,0,false,0)));
        var movement=(PlayableSpriteMovement)p.getMovementManager();
        p.setJumpInputPressed(false,false);movement.handleMovement(false,false,false,false,false,false,false,false);
        manager.update(camera.getX(),p,java.util.List.of(),0,true,true,false);
            reel.afterObjects(manager.getActiveObjects().stream().map(AbstractObjectInstance.class::cast).toList(),0,0,0x380,0x280,com.openggf.game.sonic3k.Sonic3kObjectArtKeys.MONITOR);
        boolean bounced=false;int bounceY=0;
        for(int frame=1;frame<=28;frame++){
            p.setJumpInputPressed(true,frame==1);
            movement.handleMovement(false,false,false,false,true,false,false,false);
            if(frame==1){
                assertEquals(1,p.getDoubleJumpFlag(),"fresh input activates the real glide owner");
                assertEquals(2160,p.getYSpeed(),"$600 fall + $38 release gravity + $200 glide activation + $38 activation-frame gravity");
            }
            int before=p.getYSpeed();
            manager.update(camera.getX(),p,java.util.List.of(),frame,true,true,false);
            reel.afterObjects(manager.getActiveObjects().stream().map(AbstractObjectInstance.class::cast).toList(),0,0,0x380,0x280,com.openggf.game.sonic3k.Sonic3kObjectArtKeys.MONITOR);
            if(before>0 && p.getYSpeed()<0){
                bounced=true;bounceY=p.getCentreY();assertEquals(5,frame);assertEquals(2032,before);
                assertEquals(-2032,p.getYSpeed());assertEquals(1,p.getDoubleJumpFlag());
            }
        }
        assertEquals(monitorPresent,bounced);assertEquals(1,p.getDoubleJumpFlag());
        if(monitorPresent){assertEquals(0,monitor.getCollisionFlags());assertTrue(p.getCentreY()<bounceY-64);}
        else assertTrue(p.getCentreY()>0x300);
        assertTrue(p.getCentreX()>0x430,"actual glide exits the monitor's horizontal extent");
            }
    }

    private static CollisionSystem world(int shape) {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var mode=TestEnvironment.activeGameplayMode();var terrain=mode.getTerrainCollisionManager();
        var collision=new CollisionSystem(terrain);var level=mock(LevelManager.class);
        when(level.getCurrentLevel()).thenReturn(mock(Level.class));
        var desc=new ChunkDesc(1|(shape==3||shape==5?0x3000:0x1000));byte[] heights=new byte[16];Arrays.fill(heights,(byte)16);
        if(shape==6)for(int i=0;i<16;i++)heights[i]=(byte)(i+1);
        var tile=new SolidTile(1,heights,heights,(byte)(shape==6?0xE0:0));
        when(level.getSolidTileForChunkDesc(any(ChunkDesc.class),anyInt())).thenAnswer(c->desc.isSolidityBitSet(c.getArgument(1))?tile:null);
        when(level.getSolidTileForChunkDesc(any(ChunkDesc.class),anyInt(),anyBoolean())).thenAnswer(c->desc.isSolidityBitSet(c.getArgument(1))?tile:null);
        when(level.getChunkDescAt(anyByte(),anyInt(),anyInt(),anyBoolean())).thenAnswer(c->switch(shape) {
            case 6 -> ((int)c.getArgument(2)&~15)==0x700-((int)c.getArgument(1)&~15)?desc:null;
            case 1 -> (int)c.getArgument(2)>=0x300&&(int)c.getArgument(2)<0x310?desc:null;
            case 2 -> (int)c.getArgument(2)>=0x300?desc:null;
            case 5 -> (int)c.getArgument(1)>=0x200&&(int)c.getArgument(1)<0x400&&(int)c.getArgument(2)>=0x140&&(int)c.getArgument(2)<0x240?desc:null;
            case 4 -> (int)c.getArgument(2)>=0x300&&(int)c.getArgument(1)<0x600?desc:null;
            case 3 -> (int)c.getArgument(1)>=0x130&&(int)c.getArgument(1)<0x140?desc:null;
            default -> null;
        });
        mode.attachLevelManagers(mode.getWaterSystem(),mode.getParallaxManager(),terrain,collision,mode.getSpriteManager(),level);
        GroundSensor.setLevelManager(level);return collision;
    }
}
