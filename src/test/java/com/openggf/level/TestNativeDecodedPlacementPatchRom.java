package com.openggf.level;

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
                .retainRings((rings,objects)->rings.stream().filter(r->(r.placementId()&1)==0).toList()),
                Set.of("sample:guard"));
        assertSame(source,LevelPatchOwnership.applyDecoded(patch,source));
        assertSame(source,manager.getCurrentLevel());assertSame(sourceMap,source.getMap());
        assertSame(sourcePattern,source.getPattern(0));assertSame(sourceCollision,source.getSolidTile(0));
        assertSame(sourcePalette,source.getPalette(0));assertSame(sourceBlocks,source.blocksReference());
        assertSame(sourceChunks,source.chunksReference());
        assertTrue(source.getObjects().stream().anyMatch(s->"sample:guard".equals(s.objectKey())));
        var admittedObjects=source.getObjects();var admittedRings=source.getRings();

        // This is the actual native AIZ event consumer, whose instanceof check rejects a MutableLevel snapshot.
        S3kSeamlessMutationExecutor.apply(manager,S3kSeamlessMutationExecutor.MUTATION_AIZ1_FIRE_TERRAIN_READY);
        assertSame(source,manager.getCurrentLevel());
        assertNotSame(sourceBlocks,source.blocksReference(),"native fire transition must still replace the block table");
        assertNotSame(sourceChunks,source.chunksReference(),"native fire transition must still replace the chunk table");
        assertSame(admittedObjects,source.getObjects());assertSame(admittedRings,source.getRings());
    }
}
