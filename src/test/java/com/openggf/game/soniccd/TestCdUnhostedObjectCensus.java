package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PlaceholderObjectInstance;
import com.openggf.physics.GroundSensor;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which object IDs the shipped layouts place that no registered class hosts, stage by stage (the shared placeholder
 * stands in for them): every entry of every stage's object layout, before the time-zone filter, is resolved through the
 * stage's registry. The pinned table is the census of lane routes C; hosting an object, or a registry change, must move
 * its entries in the same commit. What the IDs are (native routine per index, the stages that place them and how
 * many): the object lanes' reports. Notes that keep the table honest:
 * <ul>
 *   <li>Tidal Tempest's {@code NullObject} is the compatibility stub ({@code moveq #0,d0; rts}, Level/Tidal Tempest/R4
 *       Compatibility.asm:204-206), which leaves the object in its slot: the placeholder is what native holds for the
 *       Null entries ($05..$3F where the stage's index says so). The other zones' {@code NullObject} deletes itself
 *       (objects/null.asm) and is hosted by {@code CdNullObject}, so no Null ID is left here for them.</li>
 *   <li>Real routines still unhosted, by zone: Palmtree Panic $35 (tunnel door), $2F in R11A (Amy
 *       Rose); Collision Chaos $2C (spike chain); Tidal Tempest $05 (break pole, R42x), $23 (vertical move block), $29 (swing, R41A), $30 (switch elevator, R41A), $37 (spike ball,
 *       R42A/C/D), $52 (not the projector: the projector's $52 in R41B/R42B is hosted by objects/projector); Wacky Workbench
 *       $29 (animal); Metallic Madness $2E (poh bee, R81x).</li>
 * </ul>
 */
class TestCdUnhostedObjectCensus {
    /** Stage to the placed IDs (hex, ascending) that resolve to the shared placeholder; stages not listed place none. */
    private static final Map<String, String> UNHOSTED = Map.ofEntries(
        Map.entry("R11A", "2F 35"),
        Map.entry("R11B", "35"),
        Map.entry("R11C", "35"),
        Map.entry("R11D", "35"),
        Map.entry("R12A", "35"),
        Map.entry("R12B", "35"),
        Map.entry("R12C", "35"),
        Map.entry("R12D", "35"),
        Map.entry("R31A", "2C"),
        Map.entry("R31B", "2C"),
        Map.entry("R31C", "2C"),
        Map.entry("R31D", "2C"),
        Map.entry("R32A", "2C"),
        Map.entry("R32B", "2C"),
        Map.entry("R32C", "2C"),
        Map.entry("R32D", "2C"),
        Map.entry("R41A", "23 29 30 3F 52"),
        Map.entry("R41B", "23 28 29 30 39 3F"),
        Map.entry("R41C", "23 28 29 30 39 51 52"),
        Map.entry("R41D", "23 28 29 30 39 51 52"),
        Map.entry("R42A", "05 23 37 52"),
        Map.entry("R42B", "05 23 28 37 44"),
        Map.entry("R42C", "05 23 37 44 51 52"),
        Map.entry("R42D", "05 23 37 44 51 52"),
        Map.entry("R61A", "29"),
        Map.entry("R61B", "29"),
        Map.entry("R61C", "29"),
        Map.entry("R61D", "29"),
        Map.entry("R62A", "29"),
        Map.entry("R62B", "29"),
        Map.entry("R62C", "29"),
        Map.entry("R62D", "29"),
        Map.entry("R63C", "29"),
        Map.entry("R63D", "29"),
        Map.entry("R81A", "2E"),
        Map.entry("R81B", "2E"),
        Map.entry("R81C", "2E"),
        Map.entry("R81D", "2E")
    );

    @TempDir
    Path tempDir;
    private EngineContext previous;
    private CdDiscDataSource disc;

    @ParameterizedTest
    @EnumSource(CdStagePayload.class)
    void unhostedPlacedIdsAreAsPinned(CdStagePayload payload) throws Exception {
        TestEnvironment.resetAll();
        previous = EngineServices.current();
        disc = CdDiscDataSource.open(CdTestMedia.usaCue());
        SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        EngineContext old = EngineServices.current();
        EngineContext injected = new EngineContext(config, old.graphics(), old.audio(), old.roms(),
                old.profiler(), old.debugOverlay(), old.playbackDebug(), old.romDetection(), old.crossGameFeatures());
        EngineServices.configure(injected);
        GameplayModeContext gameplay = HeadlessGameBoot.openResolvedSessionForBoot(
                injected, new SonicCdGameModule(), ModuleResolutionService.LaunchPolicy.DETERMINISTIC, disc);
        GameplaySessionFactory.attachManagers(gameplay, injected);
        injected.graphics().initHeadless();
        GameModuleRegistry.setCurrent(SessionManager.requireCurrentGameModule());
        var sonic = GameplayTeamBootstrap.registerActiveTeam(
                SessionManager.requireCurrentGameModule(), GameServices.sprites(), config).mainSprite();
        GameServices.camera().setFocusedSprite(sonic);
        GameServices.level().loadZoneAndAct(SonicCdZoneRegistry.zoneIndex(payload), SonicCdZoneRegistry.actIndex(payload));
        CdLevel level = (CdLevel) GameServices.level().getCurrentLevel();
        var registry = SessionManager.requireCurrentGameModule().createObjectRegistry();
        TreeSet<String> unhosted = new TreeSet<>();
        for (var placement : level.placements()) {
            int id = placement.idByte() & 0x7F;
            var instance = registry.create(new ObjectSpawn(placement.x(), placement.yWord() & 0xFFF, id,
                    placement.subtype(), 0, false, 0));
            if (instance instanceof PlaceholderObjectInstance) unhosted.add(String.format("%02X", id));
        }
        assertEquals(UNHOSTED.getOrDefault(payload.name(), ""), String.join(" ", unhosted),
                payload + ": placed IDs without a hosting class");
    }

    @AfterEach
    void cleanup() throws Exception {
        GroundSensor.setLevelManager(null);
        SessionManager.clear();
        GameModuleRegistry.reset();
        if (previous != null) EngineServices.configure(previous);
        TestEnvironment.resetAll();
        if (disc != null) disc.close();
    }
}
