package com.openggf.mods.code;

import com.openggf.level.LevelPatch;
import com.openggf.level.LevelPatchOwnership;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.game.sonic3k.events.S3kSeamlessMutationExecutor;
import com.openggf.graphics.GraphicsManager;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Direct native load/mutation evidence; campaign intro, boss and results playback are separate checks. */
@RequiresRom(SonicGame.SONIC_3K)
class TestNativeDecodedPlacementPatchRom {
    private Object oldSkipIntros;
    private Object oldCharacter;

    @BeforeEach void headlessNativeLoad() {
        var config=SonicConfigurationService.getInstance();
        oldSkipIntros=config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        oldCharacter=config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS,true);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        GraphicsManager.getInstance().initHeadless();
    }
    @AfterEach void restoreConfiguration() {
        var config=SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS,oldSkipIntros==null?false:oldSkipIntros);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,oldCharacter==null?"sonic":oldCharacter);
    }

    @Test void nativeRomLevelRemainsTheLiveFireTerrainMutationConsumerAfterPlacementAdmission() throws Exception {
        var player=new Sonic("sonic",(short)0,(short)0);
        GameServices.sprites().addSprite(player);
        var camera=GameServices.camera();camera.setFocusedSprite(player);camera.setFrozen(false);
        var manager=GameServices.level();manager.loadZoneAndAct(0,0);
        GroundSensor.setLevelManager(manager);camera.updatePosition(true);
        var source=assertInstanceOf(Sonic3kLevel.class,manager.getCurrentLevel());
        assertTrue(source.hasStockRomZoneIdentity());
        var sourceMap=source.getMap();var sourcePattern=source.getPattern(0);
        var sourceCollision=source.getSolidTile(0);var sourcePalette=source.getPalette(0);
        var sourceBlocks=source.blocksReference();var sourceChunks=source.chunksReference();
        var patch=LevelPatchOwnership.bind("sample",LevelPatch.empty().forZone(0).nativePlacements()
                .select(s->s.ownerModId()==null && s.objectId()==0x8d).bind("guard")
                .addObject("guard",512,512,0,3,true)
                .retainRings((rings,objects)->rings.stream().filter(r->(r.placementId()&1)==0).toList()),
                Set.of("sample:guard"));
        assertSame(source,LevelPatchOwnership.applyDecoded(patch,source,GameServices.module().getObjectPlacementEncoding()));
        assertSame(source,manager.getCurrentLevel());assertSame(sourceMap,source.getMap());
        assertSame(sourcePattern,source.getPattern(0));assertSame(sourceCollision,source.getSolidTile(0));
        assertSame(sourcePalette,source.getPalette(0));assertSame(sourceBlocks,source.blocksReference());
        assertSame(sourceChunks,source.chunksReference());
        assertTrue(source.getObjects().stream().anyMatch(s->"sample:guard".equals(s.objectKey())));
        assertTrue(source.getObjects().stream().anyMatch(s->s.x()==512 && s.y()==512
                && "sample:guard".equals(s.objectKey())));
        var added=source.getObjects().stream().filter(s->s.x()==512 && s.y()==512
                && "sample:guard".equals(s.objectKey())).findFirst().orElseThrow();
        assertEquals(GameServices.module().getObjectPlacementEncoding().createKeyed(
                512,512,"sample:guard",0,3,true,added.layoutIndex()),added);
        var admittedObjects=source.getObjects();var admittedRings=source.getRings();

        // This is the actual native AIZ event consumer, whose instanceof check rejects a MutableLevel snapshot.
        S3kSeamlessMutationExecutor.apply(manager,S3kSeamlessMutationExecutor.MUTATION_AIZ1_FIRE_TERRAIN_READY);
        assertSame(source,manager.getCurrentLevel());
        assertNotSame(sourceBlocks,source.blocksReference(),"native fire transition must still replace the block table");
        assertNotSame(sourceChunks,source.chunksReference(),"native fire transition must still replace the chunk table");
        assertSame(admittedObjects,source.getObjects());assertSame(admittedRings,source.getRings());
    }
    @Test void registeredAdditionInstantiatesReentersLatchesAndRestoresThroughNativeManager() throws Exception {
        var player=new Sonic("sonic",(short)0,(short)0);
        GameServices.sprites().addSprite(player);
        GameServices.camera().setFocusedSprite(player);
        GameServices.level().loadZoneAndAct(0,0);
        var context=new ModContext("sample","s3k",
                com.openggf.io.ModAssetRoot.forTests("sample"));
        var created=new java.util.concurrent.atomic.AtomicInteger();
        context.registerObject("guard",(spawn,registry)->{created.incrementAndGet();return new AddedProbe(spawn);});
        int y=Math.max(0,GameServices.camera().getY())+64;
        context.decodedLevelPatch("extra",LevelPatch.empty().nativePlacements().addObject("guard",128,y,0,3,true));
        var effective=new ModBackedGamePatch(context.freeze(),
                new ModFaultBoundary(java.util.Map.of(),new com.openggf.mods.ModRuntimeFindingStore(),
                    ignored->new com.openggf.mods.ModStateSaveResult.Saved(),ignored->{ }))
                .apply(GameServices.module(),null);
        var source=effective.transformDecodedLevel(GameServices.level().getCurrentLevel());
        var added=source.getObjects().stream().filter(s->"sample:guard".equals(s.objectKey())).findFirst().orElseThrow();
        var objects=new com.openggf.level.objects.ObjectManager(java.util.List.of(added),effective.createObjectRegistry(),0,null,null);
        objects.enableExecThenLoadPlacement();
        objects.enablePermanentDestroyLatch();
        objects.reset(0);
        var initial=assertInstanceOf(AddedProbe.class,objects.getActiveObjects().stream()
                .filter(AddedProbe.class::isInstance).findFirst().orElseThrow());
        assertEquals(added,initial.getSpawn());
        assertEquals(1,created.get());
        objects.update(0x1000,player,java.util.List.of(),1);
        assertTrue(objects.getActiveObjects().stream().noneMatch(AddedProbe.class::isInstance),"out-of-range unload");
        objects.update(0,player,java.util.List.of(),2);
        var reentered=assertInstanceOf(AddedProbe.class,objects.getActiveObjects().stream()
                .filter(AddedProbe.class::isInstance).findFirst().orElseThrow());
        assertNotSame(initial,reentered);
        assertEquals(2,created.get());
        reentered.value=73;
        var adapter=objects.rewindSnapshottable();var beforeKill=adapter.capture();
        reentered.setDestroyed(true);
        objects.update(0,player,java.util.List.of(),3);
        objects.update(0x1000,player,java.util.List.of(),4);
        objects.update(0,player,java.util.List.of(),5);
        assertTrue(objects.getActiveObjects().stream().noneMatch(AddedProbe.class::isInstance),"player destruction remains latched");
        var kept=objects.capturePersistentRespawn();
        var returned=new com.openggf.level.objects.ObjectManager(java.util.List.of(added),effective.createObjectRegistry(),0,null,null);
        returned.enableExecThenLoadPlacement();
        returned.enablePermanentDestroyLatch();returned.reset(0,kept);
        assertTrue(returned.getActiveObjects().stream().noneMatch(AddedProbe.class::isInstance),"kept table suppresses reload spawn");
        adapter.restore(beforeKill);
        var restored=assertInstanceOf(AddedProbe.class,objects.getActiveObjects().stream()
                .filter(AddedProbe.class::isInstance).findFirst().orElseThrow());
        assertEquals(added,restored.getSpawn());
        assertEquals(73,restored.value);
        assertFalse(restored.isDestroyed());
    }

    private static final class AddedProbe extends com.openggf.level.objects.AbstractObjectInstance {
        private int value;
        AddedProbe(com.openggf.level.objects.ObjectSpawn spawn) { super(spawn,"added probe"); }
        @Override public void update(int vIntRunCount,com.openggf.game.PlayableEntity player) { }
        @Override public void appendRenderCommands(java.util.List<com.openggf.graphics.GLCommand> commands) { }
    }

}
