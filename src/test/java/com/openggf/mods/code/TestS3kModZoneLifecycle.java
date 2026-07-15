package com.openggf.mods.code;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.StockGameDataSources;
import com.openggf.game.ZoneKey;
import com.openggf.game.dataselect.DataSelectDestination;
import com.openggf.game.mutation.DirectLevelMutationSurface;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.rewind.InMemoryKeyframeStore;
import com.openggf.game.rewind.InputSource;
import com.openggf.game.rewind.RewindController;
import com.openggf.game.save.RuntimeSaveContext;
import com.openggf.game.save.SaveReason;
import com.openggf.game.save.SaveSessionContext;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.session.EditorCursorState;
import com.openggf.game.session.EditorModeContext;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.game.sonic3k.dataselect.S3kSaveSnapshotProvider;
import com.openggf.game.sonic3k.dataselect.S3kSavedZone;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.tests.RomTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Isolated
class TestS3kModZoneLifecycle {
    private static final ZoneKey.Mod MOD_ZONE = new ZoneKey.Mod("alpha", "sky");
    private static final int ORIGINAL_CELL = 0;
    private static final int MUTATED_CELL = 0x44;

    @BeforeAll
    static void configureServices() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    @Test
    void taggedIdentitySurvivesSaveReopenEditorAndRealBackwardSeek() throws Exception {
        File romFile = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(romFile != null, "S3K mod-zone lifecycle requires a configured S3K ROM");
        try (Rom rom = new Rom()) {
            assumeTrue(rom.open(romFile.getAbsolutePath()), "Configured S3K ROM must be readable");
            RomManager.getInstance().setRom(rom);
            GraphicsManager.getInstance().initHeadless();

            Sonic3kGameModule root = new Sonic3kGameModule();
            GameModule decorated = decoratedModule(root);
            int modZoneIndex = decorated.getZoneRegistry().resolveZoneKey(MOD_ZONE).orElseThrow();
            GameplayModeContext initial = openAndLoad(root, decorated, rom, modZoneIndex, 0);
            assertActiveZoneAndCell(initial.getLevelManager(), MOD_ZONE, ORIGINAL_CELL);

            SaveSessionContext saveSession = SaveSessionContext.forSlot(
                    "s3k", 1, new SelectedTeam("tails", List.of()), modZoneIndex, 0);
            Map<String, Object> payload = new S3kSaveSnapshotProvider().capture(
                    SaveReason.PROGRESSION_SAVE,
                    RuntimeSaveContext.forGameplayMode(initial, saveSession));
            assertFalse(payload.containsKey("zone"));
            assertEquals(MOD_ZONE, S3kSavedZone.read(payload).zoneKey());

            DataSelectDestination destination = decorated.getDataSelectHostProfile()
                    .resolveLoadDestination(payload);
            assertEquals(new DataSelectDestination(modZoneIndex, 0), destination);

            SessionManager.clear();
            GameplayModeContext reopened = openAndLoad(
                    root, decorated, rom, destination.zone(), destination.act());
            assertActiveZoneAndCell(reopened.getLevelManager(), MOD_ZONE, ORIGINAL_CELL);

            EditorModeContext editor = SessionManager.enterEditorMode(
                    new EditorCursorState(80, 96));
            assertActiveZoneAndCell(editor.getLevelManager(), MOD_ZONE, ORIGINAL_CELL);

            GameplayModeContext resumed = SessionManager.resumeGameplayFromEditor();
            GameplaySessionFactory.attachManagers(resumed, EngineServices.current());
            registerTeam(resumed, decorated);
            resumed.getLevelManager().restoreInheritedLevel();
            assertActiveZoneAndCell(resumed.getLevelManager(), MOD_ZONE, ORIGINAL_CELL);

            RewindController rewind = new RewindController(
                    resumed.getRewindRegistry(),
                    new InMemoryKeyframeStore(),
                    new TwoFrameInputSource(),
                    input -> new DirectLevelMutationSurface(
                            resumed.getLevelManager().getCurrentLevel())
                            .setBlockInMapWithoutRedraw(0, 0, 0, MUTATED_CELL),
                    1);
            rewind.step();
            assertActiveZoneAndCell(resumed.getLevelManager(), MOD_ZONE, MUTATED_CELL);

            rewind.seekTo(0);
            assertActiveZoneAndCell(resumed.getLevelManager(), MOD_ZONE, ORIGINAL_CELL);
        }
    }

    @Test
    void disabledOwnerFallsBackWithoutTrustingSyntheticIndex() {
        Map<String, Object> payload = validPayload();
        S3kSavedZone.write(payload, MOD_ZONE);

        var destination = new com.openggf.game.sonic3k.dataselect.S3kDataSelectProfile(
                () -> new Sonic3kGameModule().getZoneRegistry())
                .resolveLoadDestination(payload);

        assertEquals(new DataSelectDestination(0, 0), destination);
    }

    private static GameplayModeContext openAndLoad(
            GameModule root, GameModule decorated, Rom rom, int zone, int act) throws Exception {
        GameplayModeContext gameplay = SessionManager.openGameplaySession(
                root, decorated, StockGameDataSources.pinned(rom, root), null);
        GameplaySessionFactory.attachManagers(gameplay, EngineServices.current());
        registerTeam(gameplay, decorated);
        gameplay.getCamera().setFrozen(false);
        gameplay.getLevelManager().loadZoneAndAct(zone, act);
        return gameplay;
    }

    private static void registerTeam(GameplayModeContext gameplay, GameModule module) {
        var player = GameplayTeamBootstrap.registerActiveTeam(
                module, gameplay.getSpriteManager(), SonicConfigurationService.getInstance())
                .mainSprite();
        gameplay.getCamera().setFocusedSprite(player);
    }

    private static GameModule decoratedModule(Sonic3kGameModule root) {
        ModZoneContribution declared = new ModZoneContribution(
                MOD_ZONE.localName(), new BakedLevelRef("sky/level.json"), null, null);
        PreparedModZone prepared = PreparedModZone.prepared(
                MOD_ZONE.ownerModId(), declared,
                TestS3kModZoneAdapter.definition(2, null,
                        List.of(new ModPaletteClaim(2, 0, 0))));
        ModRegistrationPlan plan = new ModRegistrationPlan(
                MOD_ZONE.ownerModId(), "s3k", Map.of(), Map.of(), Map.of(), List.of(),
                List.of(declared), List.of(prepared));
        return new ModBackedGamePatch(plan).apply(root,
                new PatchContext(ignored -> null, SonicConfigurationService.getInstance()));
    }

    private static void assertActiveZoneAndCell(
            LevelManager levelManager, ZoneKey expectedZone, int expectedCell) {
        assertInstanceOf(Sonic3kLevel.class, levelManager.getCurrentLevel());
        assertSame(levelManager.getCurrentLevel(),
                SessionManager.getCurrentWorldSession().getCurrentLevel());
        assertEquals(expectedCell,
                levelManager.getCurrentLevel().getMap().getValue(0, 0, 0) & 0xFF);
        assertEquals(expectedZone, levelManager.getGameModule().getZoneRegistry()
                .zoneKey(levelManager.getCurrentZone()));
    }

    private static Map<String, Object> validPayload() {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("zone", 0);
        payload.put("act", 0);
        payload.put("mainCharacter", "tails");
        payload.put("sidekicks", List.of());
        payload.put("lives", 3);
        payload.put("chaosEmeralds", List.of());
        payload.put("superEmeralds", List.of());
        payload.put("clear", false);
        payload.put("progressCode", 1);
        payload.put("clearState", 0);
        return payload;
    }

    private static final class TwoFrameInputSource implements InputSource {
        @Override
        public int frameCount() {
            return 2;
        }

        @Override
        public Bk2FrameInput read(int frame) {
            return new Bk2FrameInput(frame, 0, 0, false, "lifecycle");
        }
    }
}
