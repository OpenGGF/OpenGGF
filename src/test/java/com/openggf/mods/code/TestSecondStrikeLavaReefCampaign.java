package com.openggf.mods.code;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameModule;
import com.openggf.game.sonic3k.Sonic3kObjectPlacement;
import com.openggf.game.sonic3k.Sonic3kRingPlacement;
import com.openggf.game.sonic3k.objects.LrzFlameThrowerObjectInstance;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real creator registration and original records; cue/counterplay are separate gates. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSecondStrikeLavaReefCampaign {
    @TempDir static Path temporary;
    private static URLClassLoader creator;
    private static RomByteReader rom;

    @BeforeAll static void compileCreatorAndReadOriginal() throws Exception {
        var path = System.getProperty("s3k.rom.path");
        assertNotNull(path, "Use the original absolute S3K ROM");
        rom = new RomByteReader(Files.readAllBytes(Path.of(path)));
        creator = SecondStrikeSourceFixture.compile(temporary.resolve("classes"),
                SecondStrikeSourceFixture.MAIN.resolve("secondstrike/campaign/LavaReefCampaign.java"));
    }

    @AfterAll static void closeCreator() throws Exception { if (creator != null) creator.close(); }

    private static ModRegistrationPlan registration() throws Exception {
        var context = new ModContext("second-strike", "s3k", ModAssetRoot.forTests("second-strike"));
        creator.loadClass("secondstrike.campaign.LavaReefCampaign").getMethod("register", ModContext.class)
                .invoke(null, context);
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

    @Test void bindsOneAuthenticNozzleWithoutChangingNativeSubtypeOtherRecordsOrRings() throws Exception {
        var plan = registration();
        assertEquals(1, plan.objectFactories().size());
        assertEquals(1, plan.decodedLevelPatches().size());
        var module = services(plan);
        var level = new Inventory(9, 1);
        var original = level.getObjects(); var rings = level.getRings();
        var expected = new ObjectSpawn(0x08d2, 0x0770, 0x29, 0x10, 0, false, 0x0770, 0);
        assertEquals(expected, original.getFirst());
        assertEquals(52, original.stream().filter(s -> s.objectId() == 0x29).count());
        assertSame(level, module.transformDecodedLevel(level));
        assertEquals(original.size(), level.getObjects().size());
        assertEquals(rings, level.getRings());
        assertEquals(1, level.getObjects().stream().filter(s -> s.ownerModId() != null).count());
        for (int i = 1; i < original.size(); i++) assertSame(original.get(i), level.getObjects().get(i));
        var bound = level.getObjects().getFirst();
        assertEquals("second-strike", bound.ownerModId());
        assertEquals("second-strike:lrz-opening-echo", bound.objectKey());
        assertEquals(expected, new ObjectSpawn(bound.x(), bound.y(), bound.objectId(), bound.subtype(),
                bound.renderFlags(), bound.respawnTracked(), bound.rawYWord(), bound.layoutIndex()));
        var actor = assertInstanceOf(LrzFlameThrowerObjectInstance.class,
                plan.objectFactories().get(bound.objectKey()).create(bound, null));
        assertSame(bound, actor.getSpawn());
        var recipe = LrzFlameThrowerObjectInstance.class.getDeclaredField("echoJet");
        recipe.setAccessible(true);
        assertTrue(recipe.getBoolean(actor));
        var patched = level.getObjects();
        assertSame(level, module.transformDecodedLevel(level));
        assertEquals(patched, level.getObjects());
        assertEquals(rings, level.getRings());
    }

    @Test void everyChangedOriginalFieldAndForeignOwnershipFailClosed() throws Exception {
        var variants = List.of(
                new ObjectSpawn(0x08d3, 0x0770, 0x29, 0x10, 0, false, 0x0770, 0),
                new ObjectSpawn(0x08d2, 0x0771, 0x29, 0x10, 0, false, 0x0770, 0),
                new ObjectSpawn(0x08d2, 0x0770, 0x28, 0x10, 0, false, 0x0770, 0),
                new ObjectSpawn(0x08d2, 0x0770, 0x29, 0x11, 0, false, 0x0770, 0),
                new ObjectSpawn(0x08d2, 0x0770, 0x29, 0x10, 1, false, 0x0770, 0),
                new ObjectSpawn(0x08d2, 0x0770, 0x29, 0x10, 0, true, 0x0770, 0),
                new ObjectSpawn(0x08d2, 0x0770, 0x29, 0x10, 0, false, 0x2770, 0),
                new ObjectSpawn(0x08d2, 0x0770, 0x29, 0x10, 0, false, 0x0770, 1),
                new ObjectSpawn(0x08d2, 0x0770, 0x29, 0x10, 0, false, 0x0770, 0,
                        "other-mod", "other-mod:echo"));
        for (var variant : variants) {
            var level = new Inventory(9, 1); level.only(variant);
            assertSame(level, services(registration()).transformDecodedLevel(level));
            assertSame(variant, level.getObjects().getFirst(), variant.toString());
        }
    }

    @Test void firstActAndAnotherZoneRemainNative() throws Exception {
        var module = services(registration());
        var first = new Inventory(9, 0);
        var original = first.getObjects(); var rings = first.getRings();
        assertEquals(0, original.stream().filter(s -> s.objectId() == 0x29).count());
        assertSame(first, module.transformDecodedLevel(first));
        assertSame(original, first.getObjects()); assertSame(rings, first.getRings());
        var other = new Inventory(8, 0);
        var lookalike = new Inventory(9, 1).getObjects().getFirst(); other.only(lookalike);
        assertSame(other, module.transformDecodedLevel(other));
        assertSame(lookalike, other.getObjects().getFirst());
    }
}
