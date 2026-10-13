package com.openggf.game.sonic3k.mutators;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.objects.NativeGhostEncounterObjectInstance;
import com.openggf.game.sonic3k.objects.SozHyudoroBodyObjectInstance;
import com.openggf.game.zone.ZoneRuntimeState;
import com.openggf.level.objects.*;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestNativeEncounterRomAssets {
    @Test void detachedEncounterSheetsHaveActualRomPatternsAndMappingFrames() throws Exception {
        try(var rom=new Rom()) {
            assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
            var provider=new Sonic3kObjectArtProvider();
            for(String key:List.of(Sonic3kObjectArtKeys.CNZ_BUMPER,Sonic3kObjectArtKeys.SOZ_GHOSTS,
                    Sonic3kObjectArtKeys.AIZ_RIDE_VINE,Sonic3kObjectArtKeys.ICZ_PLATFORMS_MISC2,
                    Sonic3kObjectArtKeys.LRZ_FALLING_SPIKE,Sonic3kObjectArtKeys.BREAKABLE_WALL_AIZ,
                    Sonic3kObjectArtKeys.SSZ_EGG_ROBO)) {
                assertTrue(provider.ensureEncounterArtLoaded(rom,null,key),key);
                var sheet=provider.getSheet(key);assertNotNull(sheet,key);
                assertTrue(sheet.getFrameCount()>0,key);assertTrue(sheet.getPatterns().length>0,key);
                boolean ink=false;
                for(var pattern:sheet.getPatterns()) if(pattern!=null) {
                    for(int y=0;y<8;y++) for(int x=0;x<8;x++) ink|=pattern.getPixel(x,y)!=0;
                }
                assertTrue(ink,key+" must contain ROM art rather than an empty placeholder");
            }
        }
    }
    @Test void isolatedGhostGraphRecreatesItsNativeContextWithoutReadingActiveZoneLighting() throws Exception {
        try(var rom=new Rom()) {
            assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
            var services=new TestObjectServices(){
                @Override public ZoneRuntimeState zoneRuntimeState(){throw new AssertionError("Encounter queried active zone lighting");}
            };
            var camera=mock(Camera.class);when(camera.getX()).thenReturn((short)128);when(camera.getY()).thenReturn((short)128);
            services.withRom(rom).withCamera(camera);
            var player=new TestablePlayableSprite("sonic",(short)80,(short)80);
            var sprites=mock(com.openggf.sprites.managers.SpriteManager.class);
            when(sprites.getMainPlayable()).thenReturn(player);when(sprites.getSidekicks()).thenReturn(List.of());
            services.withSpriteManager(sprites);
            var objects=new ObjectManager(List.of(),null,0,null,null,null,null,services);services.withDirectObjectManager(objects);
            var owner=objects.createDynamicObject(()->new NativeGhostEncounterObjectInstance(new ObjectSpawn(100,100,0,0,0,false,0)));
            owner.update(0,player);
            var body=objects.objectQuery().activeObjectsOfType(SozHyudoroBodyObjectInstance.class).getFirst();body.update(0,player);
            var ownerId=objects.objectQuery().identityOf(owner).orElseThrow();var bodyId=objects.objectQuery().identityOf(body).orElseThrow();
            var snapshot=objects.rewindSnapshottable().capture();
            owner.finishEncounter();body.update(1,player);ObjectLifetimeOps.expireDynamic(body);ObjectLifetimeOps.expireDynamic(owner);
            objects.rewindSnapshottable().restore(snapshot);
            var restoredOwner=(NativeGhostEncounterObjectInstance)objects.objectQuery().resolve(ownerId).orElseThrow();
            var restoredBody=(SozHyudoroBodyObjectInstance)objects.objectQuery().resolve(bodyId).orElseThrow();
            assertEquals(5,restoredOwner.darkness());assertTrue(restoredBody.belongsToEncounter(restoredOwner));
            restoredBody.update(1,player);assertFalse(restoredBody.isDestroyed());
        }
    }
}
