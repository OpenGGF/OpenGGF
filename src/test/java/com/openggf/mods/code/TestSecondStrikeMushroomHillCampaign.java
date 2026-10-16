package com.openggf.mods.code;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameModule;
import com.openggf.game.sonic3k.Sonic3kObjectPlacement;
import com.openggf.game.sonic3k.Sonic3kRingPlacement;
import com.openggf.game.sonic3k.difficulty.MushroomHillWindRecipe;
import com.openggf.game.sonic3k.objects.badniks.CluckoidBadnikInstance;
import com.openggf.io.ModAssetRoot;
import com.openggf.level.AbstractLevel;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Authenticated creator binding, not a route, presentation or harder-encounter certificate. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSecondStrikeMushroomHillCampaign {
    @TempDir static Path temporary;
    private static URLClassLoader creator;
    private static RomByteReader rom;

    @BeforeAll static void compileCreatorAndReadOriginalRom() throws Exception {
        String path = System.getProperty("s3k.rom.path");
        assertNotNull(path, "Use the original absolute S3K ROM");
        rom = new RomByteReader(Files.readAllBytes(Path.of(path)));
        creator = SecondStrikeSourceFixture.compile(temporary.resolve("classes"),
                SecondStrikeSourceFixture.MAIN.resolve("secondstrike/campaign/MushroomHillCampaign.java"));
    }

    @AfterAll static void closeCreator() throws Exception {
        if (creator != null) creator.close();
    }

    private static ModRegistrationPlan registration() throws Exception {
        var context = new ModContext("second-strike", "s3k", ModAssetRoot.forTests("second-strike"));
        creator.loadClass("secondstrike.campaign.MushroomHillCampaign")
                .getMethod("register", ModContext.class).invoke(null, context);
        return context.freeze();
    }

    private static GameModule services(ModRegistrationPlan plan) {
        return OwnedModServices.decorate(mock(GameModule.class, CALLS_REAL_METHODS), plan,
                new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                        ignored -> new ModStateSaveResult.Saved(), ignored -> { }));
    }

    private static final class Inventory extends AbstractLevel {
        Inventory(int zone, int act) throws Exception {
            super(zone);
            objects = new Sonic3kObjectPlacement(rom).load(zone, act);
            rings = new Sonic3kRingPlacement(rom).load(zone, act);
        }

        void only(ObjectSpawn spawn) { objects = List.of(spawn); }
    }

    @Test void bindsOnlyTheExactOpeningRecordAndPreservesNativeFieldsRingsAndReload() throws Exception {
        var plan = registration();
        assertEquals(1, plan.objectFactories().size());
        assertEquals(1, plan.decodedLevelPatches().size());
        var module = services(plan);
        var level = new Inventory(7, 1);
        var original = level.getObjects();
        var rings = level.getRings();
        var expected = original.get(34);
        assertEquals(new ObjectSpawn(0x06a8, 0x0518, 0x90, 0, 0, false, 0x0518, 34), expected);
        assertEquals(13, original.stream().filter(s -> s.objectId() == 0x90).count());

        assertSame(level, module.transformDecodedLevel(level));
        assertEquals(original.size(), level.getObjects().size());
        assertEquals(rings, level.getRings());
        assertEquals(1, level.getObjects().stream().filter(s -> s.ownerModId() != null).count());
        for (int i = 0; i < original.size(); i++) {
            if (i != 34) assertSame(original.get(i), level.getObjects().get(i), "untouched record " + i);
        }
        var bound = level.getObjects().get(34);
        assertEquals("second-strike", bound.ownerModId());
        assertEquals("second-strike:mhz-opening-wind", bound.objectKey());
        assertEquals(expected, new ObjectSpawn(bound.x(), bound.y(), bound.objectId(), bound.subtype(),
                bound.renderFlags(), bound.respawnTracked(), bound.rawYWord(), bound.layoutIndex()));
        var actor = assertInstanceOf(CluckoidBadnikInstance.class,
                plan.objectFactories().get(bound.objectKey()).create(bound, null));
        // Native badnik getSpawn() builds a position-tracking value record.
        assertEquals(bound, actor.getSpawn());
        var recipe = CluckoidBadnikInstance.class.getDeclaredField("windRecipe");
        recipe.setAccessible(true);
        assertSame(MushroomHillWindRecipe.PULSED, recipe.get(actor));

        var patched = level.getObjects();
        assertSame(level, module.transformDecodedLevel(level));
        assertEquals(patched, level.getObjects(), "binding is idempotent");
        assertEquals(rings, level.getRings());
    }

    @Test void changedOriginalFieldsAndForeignOwnershipNeverSelectTheRecipe() throws Exception {
        var original = new Inventory(7, 1).getObjects().get(34);
        var variants = new ArrayList<ObjectSpawn>();
        variants.add(new ObjectSpawn(original.x() + 1, original.y(), 0x90, 0, 0, false, 0x0518, 34));
        variants.add(new ObjectSpawn(original.x(), original.y() + 1, 0x90, 0, 0, false, 0x0518, 34));
        variants.add(new ObjectSpawn(original.x(), original.y(), 0x91, 0, 0, false, 0x0518, 34));
        variants.add(new ObjectSpawn(original.x(), original.y(), 0x90, 1, 0, false, 0x0518, 34));
        variants.add(new ObjectSpawn(original.x(), original.y(), 0x90, 0, 1, false, 0x0518, 34));
        variants.add(new ObjectSpawn(original.x(), original.y(), 0x90, 0, 0, true, 0x0518, 34));
        variants.add(new ObjectSpawn(original.x(), original.y(), 0x90, 0, 0, false, 0x4518, 34));
        variants.add(new ObjectSpawn(original.x(), original.y(), 0x90, 0, 0, false, 0x0518, 35));
        variants.add(new ObjectSpawn(original.x(), original.y(), 0x90, 0, 0, false, 0x0518, 34,
                "other-mod", "other-mod:wind"));
        for (var variant : variants) {
            var level = new Inventory(7, 1);
            level.only(variant);
            assertSame(level, services(registration()).transformDecodedLevel(level));
            assertSame(variant, level.getObjects().getFirst(), variant.toString());
        }
    }

    @Test void firstActAndOtherZonesStayStockEvenWithARecordLookalike() throws Exception {
        var module = services(registration());
        var first = new Inventory(7, 0);
        var original = first.getObjects();
        var rings = first.getRings();
        assertEquals(0, original.stream().filter(s -> s.objectId() == 0x90).count());
        assertSame(first, module.transformDecodedLevel(first));
        assertSame(original, first.getObjects());
        assertSame(rings, first.getRings());
        var other = new Inventory(8, 0);
        var lookalike = new Inventory(7, 1).getObjects().get(34);
        other.only(lookalike);
        assertSame(other, module.transformDecodedLevel(other));
        assertSame(lookalike, other.getObjects().getFirst());
    }
}
