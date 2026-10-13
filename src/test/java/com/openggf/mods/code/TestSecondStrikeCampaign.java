package com.openggf.mods.code;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameModule;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.sonic3k.Sonic3kObjectPlacement;
import com.openggf.game.sonic3k.Sonic3kRingPlacement;
import com.openggf.game.sonic3k.difficulty.AizMinibossAttackPolicy;
import com.openggf.game.sonic3k.difficulty.BlueSphereLayoutPolicy;
import com.openggf.game.sonic3k.difficulty.GumballSpringPolicy;
import com.openggf.io.ModAssetRoot;
import com.openggf.level.AbstractLevel;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual creator registration over native inventories; cold gameplay is a separate capture. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSecondStrikeCampaign {
    @TempDir static Path temporary;
    static URLClassLoader loader;
    static GgfMod mod;
    static RomByteReader rom;

    @BeforeAll static void compileRealModAndReadOriginal() throws Exception {
        String romPath = System.getProperty("s3k.rom.path");
        assertNotNull(romPath, "Use the original absolute S3K ROM; these checks must not skip");
        rom = new RomByteReader(Files.readAllBytes(Path.of(romPath)));
        var output = Files.createDirectory(temporary.resolve("classes"));
        var args = new ArrayList<>(List.of("--release", "25", "-cp", System.getProperty("java.class.path"),
                "-d", output.toString()));
        try (var files = Files.walk(Path.of("examples/second-strike/src/main/java"))) {
            files.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> args.add(p.toString()));
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
        loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, TestSecondStrikeCampaign.class.getClassLoader());
        mod = (GgfMod) loader.loadClass("secondstrike.SecondStrikeMod").getConstructor().newInstance();
    }
    @AfterAll static void closeLoader() throws Exception { if (loader != null) loader.close(); }

    private static ModRegistrationPlan registration() {
        var context = new ModContext("second-strike", "s3k", ModAssetRoot.forTests("second-strike"));
        mod.register(context);
        return context.freeze();
    }
    private static GameModule services(ModRegistrationPlan plan) {
        return OwnedModServices.decorate(mock(GameModule.class, CALLS_REAL_METHODS), plan,
                new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                        ignored -> new ModStateSaveResult.Saved(), ignored -> { }));
    }
    private static final class NativeInventory extends AbstractLevel {
        NativeInventory(int zone, int act) throws Exception {
            super(zone);
            objects = new Sonic3kObjectPlacement(rom).load(zone, act);
            rings = new Sonic3kRingPlacement(rom).load(zone, act);
        }
    }

    @Test void registrationPatchesBothNativeBanksWithoutTouchingLaterEnemyRoutes() throws Exception {
        var plan = registration();
        assertEquals(4, plan.objectFactories().size());
        assertEquals(2, plan.decodedLevelPatches().size());
        assertEquals(1, plan.explicitPatches().size());
        var module = services(plan);
        var forest = new NativeInventory(0, 0);
        assertSame(forest, module.transformDecodedLevel(forest));
        assertEquals(68, forest.getRings().size());
        assertEquals(10, forest.getObjects().stream().filter(s -> s.ownerModId() != null).count());
        var fire = new NativeInventory(0, 1);
        var later = fire.getObjects().stream().filter(s -> s.x() >= 0x1000).toList();
        assertSame(fire, module.transformDecodedLevel(fire));
        assertEquals(3, fire.getObjects().stream().filter(s -> s.ownerModId() != null).count());
        assertEquals(45, fire.getRings().stream().filter(r -> r.x() < 0x1000).count());
        assertEquals(later, fire.getObjects().stream().filter(s -> s.x() >= 0x1000).toList());
        assertSame(fire, module.transformDecodedLevel(fire));
        assertEquals(3, fire.getObjects().stream().filter(s -> s.ownerModId() != null).count(), "idempotent reload");
    }

    @Test void servicesAreCanonicalAndStockTitleSaveAndFrameOwnersStayDelegated() throws Exception {
        var plan = registration();
        var module = services(plan);
        assertEquals(20, module.getGameService(AizMinibossAttackPolicy.class).followupDelayTicks(3));
        assertEquals(-1, module.getGameService(AizMinibossAttackPolicy.class).followupDelayTicks(6));
        assertArrayEquals(new int[]{-32, 0, 32}, module.getGameService(GumballSpringPolicy.class).springOffsets());
        assertNotNull(module.getGameService(BlueSphereLayoutPolicy.class));
        var base = mock(GameModule.class, CALLS_REAL_METHODS);
        var title = mock(TitleScreenProvider.class);
        var dataSelect = mock(com.openggf.game.DataSelectProvider.class);
        when(base.getTitleScreenProvider()).thenReturn(title);
        when(base.getDataSelectProvider()).thenReturn(dataSelect);
        var patched = plan.explicitPatches().getFirst().apply(base, null);
        assertSame(dataSelect, patched.getDataSelectProvider());
        assertSame(base.gameplayFrameController(), patched.gameplayFrameController());
        assertSame(base.getSaveSnapshotProvider(), patched.getSaveSnapshotProvider());
        patched.getTitleScreenProvider().initialize();
        patched.getTitleScreenProvider().setExitToLevelHandler(() -> { });
        patched.getTitleScreenProvider().reset();
        verify(title).initialize(); verify(title).setExitToLevelHandler(any()); verify(title).reset();
    }

    @Test void otherZonesKeepTheirExactNativeTables() throws Exception {
        var source = new NativeInventory(1, 0);
        var objects = source.getObjects(); var rings = source.getRings();
        assertSame(source, services(registration()).transformDecodedLevel(source));
        assertSame(objects, source.getObjects()); assertSame(rings, source.getRings());
    }
}
