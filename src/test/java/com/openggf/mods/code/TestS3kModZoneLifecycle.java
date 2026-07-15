package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.ZoneKey;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.mutation.DirectLevelMutationSurface;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.LevelSnapshot;
import com.openggf.game.session.EditorCursorState;
import com.openggf.game.session.EditorModeContext;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.level.AbstractLevel;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.SolidTile;
import com.openggf.level.rewind.LevelRewindSnapshotAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.ArrayList;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Isolated
class TestS3kModZoneLifecycle {
    private static final ZoneKey MOD_ZONE = ZoneKey.mod("alpha", "sky");
    private static final int RUNTIME_ZONE = 22;

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
    void taggedIdentitySurvivesEditorRoundTripAndLevelRewind() {
        ZoneRegistry zones = mock(ZoneRegistry.class);
        when(zones.getZoneCount()).thenReturn(23);
        when(zones.zoneKey(RUNTIME_ZONE)).thenReturn(MOD_ZONE);
        when(zones.resolveZoneKey(MOD_ZONE)).thenReturn(OptionalInt.of(RUNTIME_ZONE));
        GameModule module = moduleWithZones(zones);
        GameplayModeContext initial = SessionManager.openGameplaySession(module);
        WorldSession world = initial.getWorldSession();
        StubLevel level = new StubLevel();
        level.getMap().setValue(0, 0, 0, (byte) 0x11);
        world.setCurrentZone(RUNTIME_ZONE);
        world.setCurrentAct(0);
        world.setCurrentLevel(level);

        EditorModeContext editor = SessionManager.enterEditorMode(new EditorCursorState(80, 96));
        RewindSnapshottable<LevelSnapshot> rewind =
                LevelRewindSnapshotAdapter.create(editor.getLevelManager());
        LevelSnapshot snapshot = rewind.capture();
        new DirectLevelMutationSurface(level).setBlockInMapWithoutRedraw(0, 0, 0, 0x44);
        rewind.restore(snapshot);
        GameplayModeContext resumed = SessionManager.resumeGameplayFromEditor();

        assertEquals((byte) 0x11, level.getMap().getValue(0, 0, 0));
        assertSame(world, resumed.getWorldSession());
        assertEquals(MOD_ZONE, resumed.getWorldSession().getGameModule()
                .getZoneRegistry().zoneKey(resumed.getWorldSession().getCurrentZone()));
    }

    @Test
    void disabledOwnerFallsBackWithoutTrustingSyntheticIndex() {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("zone", 0);
        payload.put("act", 0);
        payload.put("mainCharacter", "tails");
        payload.put("sidekicks", java.util.List.of());
        payload.put("lives", 3);
        payload.put("chaosEmeralds", java.util.List.of());
        payload.put("superEmeralds", java.util.List.of());
        payload.put("clear", false);
        payload.put("progressCode", 1);
        payload.put("clearState", 0);
        com.openggf.game.sonic3k.dataselect.S3kSavedZone.write(payload, MOD_ZONE);
        ZoneRegistry rebuilt = mock(ZoneRegistry.class);
        when(rebuilt.resolveZoneKey(MOD_ZONE)).thenReturn(OptionalInt.empty());

        var destination = new com.openggf.game.sonic3k.dataselect.S3kDataSelectProfile(
                () -> rebuilt).resolveLoadDestination(payload);

        assertEquals(new com.openggf.game.dataselect.DataSelectDestination(0, 0), destination);
    }

    private static GameModule moduleWithZones(ZoneRegistry zones) {
        return new DelegatingGameModule(new Sonic3kGameModule(), "test:mod-zones") {
            @Override
            public ZoneRegistry getZoneRegistry() {
                return zones;
            }
        };
    }

    private static final class StubLevel extends AbstractLevel {
        private StubLevel() {
            super(0);
            palettes = new Palette[PALETTE_COUNT];
            patterns = new Pattern[16];
            chunks = new Chunk[16];
            blocks = new Block[16];
            solidTiles = new SolidTile[16];
            for (int index = 0; index < 16; index++) {
                chunks[index] = new Chunk();
                blocks[index] = new Block();
            }
            map = new com.openggf.level.Map(2, 256, 256);
            objects = new ArrayList<>();
            rings = new ArrayList<>();
            patternCount = 16;
            chunkCount = 16;
            blockCount = 16;
            solidTileCount = 16;
            minX = 0;
            maxX = 1024;
            minY = 0;
            maxY = 1024;
        }
    }
}
