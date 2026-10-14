package com.openggf.game.sonic1;

import com.openggf.game.modzone.ModZoneLevelData;
import com.openggf.game.modzone.ModZoneRuntimeProfile;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class TestSonic1ModZoneAdapter {
    @Test
    void sixteenGridAuthoredLevelHasAFlatNativeHost() {
        var module = new Sonic1GameModule();
        var data = data();
        assertDoesNotThrow(() -> module.getModZoneAdapter().validate("editor", data));
        assertEquals(ModZoneRuntimeProfile.flatEmpty(),
                module.getModZoneAdapter().runtimeProfile("editor", data));
    }

    @Test
    void flatAuthoredProfilesNeverReadSyntheticRomPlcHeaders() {
        var hostData=data();
        com.openggf.game.ZoneRegistry zones=org.mockito.Mockito.mock(com.openggf.game.ZoneRegistry.class);
        org.mockito.Mockito.when(zones.modZoneRuntimeContribution(1024)).thenReturn(
                new com.openggf.game.modzone.ModZoneRuntimeContribution("editor","test",hostData,
                        ModZoneRuntimeProfile.flatEmpty()));
        for(com.openggf.game.GameModule stock:List.of(new Sonic1GameModule(),
                new com.openggf.game.sonic2.Sonic2GameModule())) {
            var module=new com.openggf.game.patch.DelegatingGameModule(stock,"authored-test") {
                @Override public com.openggf.game.ZoneRegistry getZoneRegistry() { return zones; }
            };
            var context=new com.openggf.game.LevelLoadContext();
            context.setLevelIndex(1024); context.setGameModule(module);
            context.setLevel(org.mockito.Mockito.mock(com.openggf.level.Level.class));
            assertFalse(com.openggf.game.resources.NativeLevelPlcPolicy.allows(context));
            assertDoesNotThrow(()->module.getLevelInitProfile().levelLoadSteps(context).stream()
                    .filter(step->step.name().equals("QueueInitialPlcs")).findFirst().orElseThrow().execute());
        }
    }

    @Test void s3kStockAndLegacyIdZeroRetainThePlacedRingFactory() {
        com.openggf.tests.TestEnvironment.resetAll();
        var registry=new com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry();
        var spawn=new com.openggf.game.common.CommonObjectPlacementEncoding().create(32,64,0,0,0,true,9);
        assertInstanceOf(com.openggf.game.sonic3k.objects.Sonic3kPlacedRingObjectInstance.class,
                registry.create(spawn));
    }

    static ModZoneLevelData data() {
        byte[] blocks = new byte[1024];
        for (int i=512;i<blocks.length;i+=2) blocks[i]=0x30;
        byte[] profiles = new byte[32];
        java.util.Arrays.fill(profiles,16,32,(byte)16);
        return new ModZoneLevelData(1,64,16,1,1,0,255,0,255,
                new byte[32],new byte[8],blocks,new byte[]{1},new byte[]{0},
                profiles,profiles,new byte[]{0,0},new int[]{1},new int[]{1},
                new byte[][]{new byte[32],new byte[32],new byte[32],new byte[32]},
                Optional.empty(),List.of(),List.of(),List.of(),1,1,2);
    }
}
