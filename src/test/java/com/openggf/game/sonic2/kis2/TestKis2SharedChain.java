package com.openggf.game.sonic2.kis2;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomByteReader;
import com.openggf.game.*;
import com.openggf.game.internal.BadnikChainPolicy;
import com.openggf.game.patch.*;
import com.openggf.game.solid.*;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.objects.BreakableBlockObjectInstance;
import com.openggf.game.sonic2.objects.badniks.CoconutsBadnikInstance;
import com.openggf.level.objects.*;
import com.openggf.sprites.managers.PlayableSpriteMovement;
import com.openggf.sprites.playable.*;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

/** Tier-one KiS2: real S2 cart and S&K donor bytes, no optional lock-on chip needed. */
@RequiresRom(SonicGame.SONIC_2)
class TestKis2SharedChain {
    @TempDir Path temporary;
    RomByteReader cart;
    Kis2GameModule module;
    Knuckles player;
    BadnikChainPolicy chain;

    @BeforeEach void setup() throws Exception {
        var s2=RomTestUtils.ensureSonic2RomAvailable();
        var s3k=RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(s2!=null && s3k!=null,"KiS2 tier one needs S2 and S3K ROMs");
        cart=RomByteReader.fromBytes(Files.readAllBytes(s2.toPath()));
        var donor=LogicalRomResolver.windowSkFromCombined(Files.readAllBytes(s3k.toPath()));
        var context=new PatchContext(rom->switch(rom) {
            case S2->cart;
            case SK->donor;
            default->throw new IOException("Tier-one test supplies only the S2 and S&K windows");
        },SonicConfigurationService.createStandalone(temporary));
        module=new Kis2GameModule(new Sonic2GameModule(),context);
        TestEnvironment.configureGameModuleFixture(module);
        player=new Knuckles("knuckles",(short)128,(short)256);
        chain=module.getGameService(BadnikChainPolicy.class);
    }
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void nativeGlideFloorOwnersClearTheGlobalWord(boolean steep) throws Exception {
        chain.incrementAndAward(); chain.incrementAndAward();
        player.setAir(true);player.setDoubleJumpFlag(1);player.setAngle((byte)0);
        var movement=new PlayableSpriteMovement(player);
        var floor=PlayableSpriteMovement.class.getDeclaredMethod("glideHitFloor");floor.setAccessible(true);
        floor.invoke(movement);
        assertTrue(player.getAir(),"flat BeginSlide starts sliding without ResetOnFloor");
        assertEquals(4,chain.word());
        if(steep) {
            // Knuckles_BeginSlide (docs/kis2disasm/s2.asm:38217-38231).
            player.setAngle((byte)0x40);floor.invoke(movement);
        } else {
            // Knuckles_Sliding (docs/kis2disasm/s2.asm:38426-38446).
            var getUp=PlayableSpriteMovement.class.getDeclaredMethod("slideGetUp");
            getUp.setAccessible(true);getUp.invoke(movement);
        }
        assertFalse(player.getAir());
        assertEquals(0,chain.word());
        assertEquals(100,player.incrementBadnikChain());
    }

    @Test void ledgeCompletionCallsTheNativeFloorTailButWaitingForAnimationDoesNot() throws Exception {
        chain.incrementAndAward(); chain.incrementAndAward();
        player.setAir(true); player.setDoubleJumpFlag(5);
        player.setDoubleJumpProperty((byte)12); player.setAnimationTick(1);
        var movement=new PlayableSpriteMovement(player);
        var ledge=PlayableSpriteMovement.class.getDeclaredMethod("updateLedgeClimb");
        ledge.setAccessible(true);
        ledge.invoke(movement);
        assertEquals(4,chain.word(),"the animation-wait return does not call ResetOnFloor");
        player.setAnimationTick(0);
        ledge.invoke(movement);
        // Knuckles_Climbing_Onto_Ledge (docs/kis2disasm/s2.asm:38848-38864).
        assertFalse(player.getAir());
        assertEquals(0,chain.word(),"the final ledge frame calls ResetOnFloor_Part2");
        assertEquals(100,player.incrementBadnikChain());
    }

    @Test void nativeSmashableAndBadnikInterleaveThroughOneWord() {
        var state=new GameStateManager();
        var solid=mock(ObjectSolidExecutionContext.class);
        var art=mock(ObjectRenderManager.class);
        ObjectManager[] objects={null};
        var services=new StubObjectServices() {
            @Override public GameModule gameModule(){return module;}
            @Override public RomByteReader romReader(){return cart;}
            @Override public GameStateManager gameState(){return state;}
            @Override public ObjectRenderManager renderManager(){return art;}
            @Override public ObjectManager objectManager(){return objects[0];}
            @Override public ObjectSolidExecutionContext solidExecution(){return solid;}
            @Override public ObjectPlayerQuery playerQuery(){return new ObjectPlayerQuery(()->player,List::of);}
        };
        objects[0]=new ObjectManager(List.of(),module.createObjectRegistry(),0,null,null,null,new Camera(),services);
        AbstractObjectInstance.updateCameraBounds(0,0,0x2000,0x2000,0);
        kill(objects[0],0);
        assertEquals(100,state.getScore());assertEquals(2,chain.word());
        player.setRolling(true);player.setAnimationId(2);player.setAir(true);
        var block=objects[0].createDynamicObjectAtSlot(() -> new BreakableBlockObjectInstance(new ObjectSpawn(256,256,0x32,0,0,false,0),"KiS2 block"),0x30);
        block.setServices(services);
        when(solid.resolveSolidNowAll()).thenAnswer(call->{
            PlayableNativeFloorReset.resetChain(player);player.setYSpeed((short)0);player.setAir(false);
            var contact=new PlayerSolidContactResult(ContactKind.TOP,true,false,false,false,
                    PreContactState.ZERO,new PostContactState((short)0,(short)0,false,true,false),0);
            return new SolidCheckpointBatch(block,Map.of(player,contact));
        });
        // SmashableObject_LoadPoints (docs/kis2disasm/s2.asm:52696-52723).
        block.update(0,player);
        assertTrue(block.isDestroyed());assertEquals(300,state.getScore());assertEquals(4,chain.word());
        // Touch_KillEnemy (docs/kis2disasm/s2.asm:88811-88833).
        kill(objects[0],1);
        assertEquals(800,state.getScore());assertEquals(6,chain.word());
        verify(solid).resolveSolidNowAll();
    }
    private void kill(ObjectManager objects,int ordinal) {
        var enemy=objects.createDynamicObject(()->new CoconutsBadnikInstance(
                new ObjectSpawn(160+ordinal*32,256,0x9D,0x1E,0,false,256)));
        assertNotNull(enemy);
        enemy.onPlayerAttack(player,new TouchResponseResult(9,16,16,TouchCategory.ENEMY));
        assertTrue(enemy.isDestroyed());
    }
}
