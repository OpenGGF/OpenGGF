package com.openggf.game.sonic2.objects;

import com.openggf.camera.Camera;
import com.openggf.game.*;
import com.openggf.game.internal.BadnikChainPolicy;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.solid.*;
import com.openggf.level.objects.*;
import com.openggf.game.sonic2.objects.badniks.CoconutsBadnikInstance;
import com.openggf.sprites.playable.*;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;

@com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_2)
class TestSonic2SmashableChain {
    @BeforeEach void setup() { TestEnvironment.resetAll(); }
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @Test void realBadnikAndSmashableUseOneWordAndFailedPopupAllocationCannotAdvanceIt() {
        var f=new Fixture();
        f.kill(f.player);
        assertEquals(100,f.state.getScore()); assertEquals(2,f.chain.word());
        Sonic2SmashableChain.award(f.services,256,256);
        assertEquals(300,f.state.getScore()); assertEquals(4,f.chain.word());
        f.kill(f.tails);
        assertEquals(800,f.state.getScore()); assertEquals(6,f.chain.word());
        f.objects.reserveAllButNFreeSlots(0);
        Sonic2SmashableChain.award(f.services,256,256);
        assertEquals(800,f.state.getScore()); assertEquals(6,f.chain.word());
        f.objects.reset(0); f.objects.reserveAllButNFreeSlots(1);
        Sonic2SmashableChain.award(f.services,256,256);
        assertEquals(1800,f.state.getScore()); assertEquals(8,f.chain.word());
        assertTrue(f.objects.getActiveObjects().stream().anyMatch(PointsObjectInstance.class::isInstance));
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void bothSolidLandingsPrecedeSavedWordRestoreAndSingleAward(boolean ground) {
        var f=new Fixture(); f.kill(f.player); f.kill(f.tails);
        f.player.setRolling(true); f.player.setAnimationId(2); f.player.setAir(true);
        f.tails.setAir(true);
        AbstractObjectInstance object=f.objects.createDynamicObjectAtSlot(() -> ground
                ?new SmashableGroundObjectInstance(new ObjectSpawn(256,256,0x2F,0x80,0,false,0),"ground")
                :new BreakableBlockObjectInstance(new ObjectSpawn(256,256,0x32,0,0,false,0),"block"),0x30);
        object.setServices(f.services);
        f.objects.forceRidingObjectForBootstrap(f.player, object);
        f.objects.forceRidingObjectForBootstrap(f.tails, object);
        f.player.setAir(true); f.tails.setAir(true);
        when(f.solid.resolveSolidNowAll()).thenAnswer(call -> {
            assertEquals(4,f.chain.word(),"save happens before either solid call");
            // RideObject_SetRide (s2.asm:36012-36030) zeroes y_vel before the floor tail.
            f.player.setYSpeed((short)0); f.tails.setYSpeed((short)0);
            PlayableNativeFloorReset.resetChain(f.player); f.player.setAir(false);
            PlayableNativeFloorReset.resetChain(f.tails); f.tails.setAir(false);
            return new SolidCheckpointBatch(object,Map.of(f.player,standing(),f.tails,standing()));
        });
        object.update(0,f.player);
        assertTrue(object.isDestroyed());
        assertEquals(800,f.state.getScore(),"one 500 award after restoring the pre-solid word");
        assertEquals(6,f.chain.word());
        assertTrue(f.player.getAir()); assertTrue(f.tails.getAir());
        assertFalse(f.objects.isRidingObject(f.player, object));
        assertFalse(f.objects.isRidingObject(f.tails, object));
        assertEquals(ground?0:-0x300,f.player.getYSpeed());
        verify(f.solid).resolveSolidNowAll();
    }

    @Test void subtypeHighBitStillRequiresRollBeforeRestoringTheSavedWord() {
        var f = new Fixture(); f.kill(f.player); f.kill(f.tails);
        f.player.setAnimationId(0); f.tails.setAnimationId(0);
        var ground = f.objects.createDynamicObjectAtSlot(() -> new SmashableGroundObjectInstance(
                new ObjectSpawn(256,256,0x2F,0x80,0,false,0), "ground"),0x30);
        when(f.solid.resolveSolidNowAll()).thenAnswer(call -> {
            PlayableNativeFloorReset.resetChain(f.player);
            PlayableNativeFloorReset.resetChain(f.tails);
            f.player.setAir(false); f.tails.setAir(false);
            return new SolidCheckpointBatch(ground, Map.of(f.player,standing(),f.tails,standing()));
        });
        ground.update(0,f.player);
        // Obj2F_Main tests saved Roll before the subtype branch (s2.asm:49187-49212).
        assertFalse(ground.isDestroyed());
        assertEquals(0,f.chain.word(), "a non-breaking landing keeps the native floor reset");
        assertEquals(300,f.state.getScore());
    }

    @Test void p2SmashingABlockUsesAddPointsP1RatherThanTheAttackersBank() {
        var nativeWorld=new NativeCompetitionRuntimeFixture();
        var module=nativeWorld.world.getGameModule();
        var state=new GameStateManager();
        var solid=mock(ObjectSolidExecutionContext.class);
        var art=mock(ObjectRenderManager.class);
        var services=new StubObjectServices() {
            ObjectManager objects;
            @Override public GameModule gameModule() { return module; }
            @Override public com.openggf.data.RomByteReader romReader() { return readRom(); }
            @Override public com.openggf.game.session.WorldSession worldSession() { return nativeWorld.world; }
            @Override public GameStateManager gameState() { return state; }
            @Override public ObjectRenderManager renderManager() { return art; }
            @Override public ObjectSolidExecutionContext solidExecution() { return solid; }
            @Override public ObjectPlayerQuery playerQuery() {
                return new ObjectPlayerQuery(()->nativeWorld.p1,()->List.of(nativeWorld.p2));
            }
            @Override public ObjectManager objectManager() {
                if(objects==null) objects=new ObjectManager(List.of(),new Sonic2ObjectRegistry(),0,null,null,null,
                        nativeWorld.cameras[0],this);
                return objects;
            }
        };
        nativeWorld.p2.setRolling(true); nativeWorld.p2.setAnimationId(2); nativeWorld.p2.setAir(true);
        var block=services.objectManager().createDynamicObjectAtSlot(() -> new BreakableBlockObjectInstance(new ObjectSpawn(256,256,0x32,0,0,false,0),"P2 block"),0x30);
        block.setServices(services);
        when(solid.resolveSolidNowAll()).thenAnswer(call -> {
            PlayableNativeFloorReset.resetChain(nativeWorld.p2); nativeWorld.p2.setAir(false);
            return new SolidCheckpointBatch(block,Map.of(nativeWorld.p2,standing()));
        });
        block.update(0,nativeWorld.p1);
        assertTrue(block.isDestroyed());
        assertEquals(100,nativeWorld.games[0].getScore());
        assertEquals(0,nativeWorld.games[1].getScore());
        assertEquals(0,state.getScore());
    }

    private static com.openggf.data.RomByteReader readRom() {
        try { return com.openggf.data.RomByteReader.fromRom(TestEnvironment.currentRom()); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
    private static PlayerSolidContactResult standing() {
        return new PlayerSolidContactResult(ContactKind.TOP,true,false,false,false,
                PreContactState.ZERO,new PostContactState((short)0,(short)0,false,true,false),0);
    }
    private static final class Fixture {
        final Sonic2GameModule module=new Sonic2GameModule();
        final GameStateManager state=new GameStateManager();
        final Sonic player;
        final Tails tails;
        final BadnikChainPolicy chain=module.getGameService(BadnikChainPolicy.class);
        final ObjectRenderManager art=mock(ObjectRenderManager.class);
        final ObjectSolidExecutionContext solid=mock(ObjectSolidExecutionContext.class);
        ObjectManager objects;
        int spawn;
        final ObjectServices services=new StubObjectServices() {
            @Override public GameModule gameModule() { return module; }
            @Override public com.openggf.data.RomByteReader romReader() { return readRom(); }
            @Override public GameStateManager gameState() { return state; }
            @Override public ObjectRenderManager renderManager() { return art; }
            @Override public ObjectManager objectManager() { return objects; }
            @Override public ObjectSolidExecutionContext solidExecution() { return solid; }
            @Override public ObjectPlayerQuery playerQuery() { return new ObjectPlayerQuery(()->player,()->List.of(tails)); }
        };
        Fixture() {
            AbstractObjectInstance.updateCameraBounds(0,0,0x2000,0x2000,0);
            GameModuleRegistry.setCurrent(module); TestEnvironment.configureGameModuleFixture(module);
            player=new Sonic("sonic",(short)128,(short)256);
            tails=new Tails("tails",(short)160,(short)256); tails.setCpuControlled(true);
            objects=new ObjectManager(List.of(),new Sonic2ObjectRegistry(),0,null,null,null,new Camera(),services);
        }
        void kill(PlayableEntity attacker) {
            var enemy=objects.createDynamicObject(()->new CoconutsBadnikInstance(
                    new ObjectSpawn(160+(spawn++)*32,256,0x9D,0x1E,0,false,256)));
            assertNotNull(enemy);
            enemy.onPlayerAttack(attacker,new TouchResponseResult(9,16,16,TouchCategory.ENEMY));
            assertTrue(enemy.isDestroyed());
        }
    }
}
