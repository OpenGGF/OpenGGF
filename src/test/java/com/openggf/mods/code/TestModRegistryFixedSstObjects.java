package com.openggf.mods.code;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.HCZWaterSkimSlotObjectInstance;
import com.openggf.game.sonic3k.objects.MhzPollenSpawnerInstance;
import com.openggf.io.ModAssetRoot;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A mod that registers an object wraps the stock object registry in
 * {@link ModDecoratedObjectRegistry}; the stock game's fixed SST occupants must still install at
 * level init. S3K's {@code SpawnLevelMainSprites} puts HCZ's water-skim record at absolute slot 5
 * and {@code LevelInit_MHZ} puts the pollen spawner at slot 4 before {@code Load_Sprites}; when the
 * decorator dropped {@code installFixedSstObjects}, every later allocation in those zones sat one
 * slot lower under any object-registering mod. Regression for the Typing of the Hedgehog rails
 * lane's Course-vs-builder allocation census, 2026-10-10.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestModRegistryFixedSstObjects {
    private SharedLevel bootstrap;

    @AfterEach void dispose() {
        if (bootstrap != null) bootstrap.dispose();
        bootstrap = null;
        SonicConfigurationService.getInstance().clearSessionOverrides();
    }

    /** The act loaded under a mod whose registered object makes the registry creator-aware. */
    private void launchUnderObjectMod(int zone, int x, int y) throws Exception {
        bootstrap = SharedLevel.load(SonicGame.SONIC_3K, zone, 0);
        var context = new ModContext("fixed-sst", "s3k", ModAssetRoot.forTests("fixed-sst"));
        context.registerObject("marker", (spawn, registry) -> null);
        var boundary = new ModFaultBoundary(Map.of(), new com.openggf.mods.ModRuntimeFindingStore(),
                owners -> new com.openggf.mods.ModStateSaveResult.Saved(), owners -> { });
        GameModule effective = new ModBackedGamePatch(context.freeze(), boundary).apply(GameServices.module(), null);
        SessionManager.clear();
        GameModuleRegistry.setCurrent(effective);
        TestEnvironment.activeGameplayMode();
        HeadlessTestFixture.builder().withZoneAndAct(zone, 0)
                .startPosition((short) x, (short) y).startPositionIsCentre().build();
        assertInstanceOf(ModDecoratedObjectRegistry.class, GameServices.module().createObjectRegistry(),
                "the mod's registered object decorates the stock registry");
    }

    private static List<AbstractObjectInstance> live(Class<? extends AbstractObjectInstance> type) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(type::isInstance).map(type::cast).map(AbstractObjectInstance.class::cast).toList();
    }

    @Test void hydrocityWaterSkimRecordHoldsSlotFiveUnderAnObjectMod() throws Exception {
        launchUnderObjectMod(Sonic3kZoneIds.ZONE_HCZ, 0x0300, 0x0500);
        var skim = live(HCZWaterSkimSlotObjectInstance.class);
        assertEquals(1, skim.size(), "SpawnLevelMainSprites installs Obj_HCZWaterSplash subtype 1 for HCZ");
        assertEquals(5, skim.getFirst().getSlotIndex(), "the water-skim record sits at Dynamic_object_RAM+2");
    }

    @Test void mushroomHillPollenSpawnerHoldsSlotFourUnderAnObjectMod() throws Exception {
        launchUnderObjectMod(Sonic3kZoneIds.ZONE_MHZ, 0x00D8, 0x0500);
        var spawners = live(MhzPollenSpawnerInstance.class);
        assertEquals(1, spawners.size(), "LevelInit_MHZ installs Obj_MHZ_Pollen_Spawner");
        assertEquals(4, spawners.getFirst().getSlotIndex(), "the pollen spawner sits at Dynamic_object_RAM+object_size");
        int firstOther = GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> !(o instanceof MhzPollenSpawnerInstance))
                .filter(o -> o instanceof AbstractObjectInstance a && a.getSlotIndex() >= 4 && a.getSlotIndex() < 100)
                .mapToInt(o -> ((AbstractObjectInstance) o).getSlotIndex()).min().orElse(-1);
        assertNotEquals(4, firstOther, "no layout object took the fixed slot");
    }
}
