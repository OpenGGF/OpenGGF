package com.openggf.mods.code;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameModule;
import com.openggf.game.sonic3k.Sonic3kObjectPlacement;
import com.openggf.game.sonic3k.Sonic3kRingPlacement;
import com.openggf.game.sonic3k.difficulty.HiddenPalaceDuelRecipe;
import com.openggf.game.sonic3k.objects.CutsceneKnucklesHpzInstance;
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
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual creator publication/original identity; no activation, arena or strict SDK claim. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSecondStrikeHiddenPalaceCampaign {
    @TempDir static Path temporary;
    private static URLClassLoader creator;
    private static RomByteReader rom;

    @BeforeAll static void compileCreatorAndReadOriginal() throws Exception {
        String path = System.getProperty("s3k.rom.path");
        assertNotNull(path, "Use the original absolute locked-on S3K ROM");
        byte[] bytes = Files.readAllBytes(Path.of(path));
        CRC32 crc = new CRC32(); crc.update(bytes);
        assertEquals(0x63522553L, crc.getValue());
        rom = new RomByteReader(bytes);
        creator = SecondStrikeSourceFixture.compile(temporary.resolve("classes"),
                SecondStrikeSourceFixture.MAIN.resolve("secondstrike/campaign/HiddenPalaceCampaign.java"));
    }

    @AfterAll static void closeCreator() throws Exception { if (creator != null) creator.close(); }

    private static ModRegistrationPlan registration() throws Exception {
        var context = new ModContext("second-strike", "s3k", ModAssetRoot.forTests("second-strike"));
        creator.loadClass("secondstrike.campaign.HiddenPalaceCampaign")
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

    @Test void bindsExactlyTheOriginalPersonalDuelAndLeavesMasterEmeraldOtherRecordsAndRingsNative() throws Exception {
        var plan = registration();
        assertEquals(1, plan.objectFactories().size());
        assertEquals(1, plan.decodedLevelPatches().size());
        var module = services(plan);
        var level = new Inventory(0x16, 1);
        var original = level.getObjects(); var rings = level.getRings();
        var expected = new ObjectSpawn(0x11F0, 0x042C, 0x82, 0x28, 0, false, 0x042C, 31);
        assertEquals(expected, original.get(31));
        assertEquals(0xB0, original.get(37).objectId(), "Native Master Emerald is a distinct record");
        assertSame(level, module.transformDecodedLevel(level));
        assertEquals(original.size(), level.getObjects().size());
        assertEquals(rings, level.getRings());
        assertEquals(1, level.getObjects().stream().filter(s -> s.ownerModId() != null).count());
        for (int i = 0; i < original.size(); i++) {
            if (i != 31) assertSame(original.get(i), level.getObjects().get(i));
        }
        var bound = level.getObjects().get(31);
        assertEquals("second-strike", bound.ownerModId());
        assertEquals("second-strike:hpz-personal-feint", bound.objectKey());
        assertEquals(expected, new ObjectSpawn(bound.x(), bound.y(), bound.objectId(), bound.subtype(),
                bound.renderFlags(), bound.respawnTracked(), bound.rawYWord(), bound.layoutIndex()));
        var actor = assertInstanceOf(CutsceneKnucklesHpzInstance.class,
                plan.objectFactories().get(bound.objectKey()).create(bound, null));
        assertEquals(bound, actor.getSpawn());
        var recipe = CutsceneKnucklesHpzInstance.class.getDeclaredField("duelRecipe");
        recipe.setAccessible(true);
        assertEquals(HiddenPalaceDuelRecipe.FEINT, recipe.get(actor));
        var patched = level.getObjects();
        assertSame(level, module.transformDecodedLevel(level));
        assertEquals(patched, level.getObjects()); assertEquals(rings, level.getRings());
    }

    @Test void everyChangedOriginalFieldAndForeignOwnershipFailClosed() throws Exception {
        var variants = List.of(
                new ObjectSpawn(0x11F1, 0x042C, 0x82, 0x28, 0, false, 0x042C, 31),
                new ObjectSpawn(0x11F0, 0x042D, 0x82, 0x28, 0, false, 0x042C, 31),
                new ObjectSpawn(0x11F0, 0x042C, 0xB0, 0x28, 0, false, 0x042C, 31),
                new ObjectSpawn(0x11F0, 0x042C, 0x82, 0x24, 0, false, 0x042C, 31),
                new ObjectSpawn(0x11F0, 0x042C, 0x82, 0x28, 1, false, 0x042C, 31),
                new ObjectSpawn(0x11F0, 0x042C, 0x82, 0x28, 0, true, 0x042C, 31),
                new ObjectSpawn(0x11F0, 0x042C, 0x82, 0x28, 0, false, 0x242C, 31),
                new ObjectSpawn(0x11F0, 0x042C, 0x82, 0x28, 0, false, 0x042C, 30),
                new ObjectSpawn(0x11F0, 0x042C, 0x82, 0x28, 0, false, 0x042C, 31,
                        "other-mod", "other-mod:duel"));
        for (var variant : variants) {
            var level = new Inventory(0x16, 1); level.only(variant);
            assertSame(level, services(registration()).transformDecodedLevel(level));
            assertSame(variant, level.getObjects().getFirst(), variant.toString());
        }
    }

    @Test void actualLrzBossAndSuperSanctuaryInventoriesAndAnotherZoneRemainNative() throws Exception {
        var module = services(registration());
        for (var level : List.of(new Inventory(0x16, 0), new Inventory(0x17, 1))) {
            var original = level.getObjects(); var rings = level.getRings();
            assertFalse(original.stream().anyMatch(s -> s.layoutIndex() == 31 && s.x() == 0x11F0
                    && s.y() == 0x042C && s.objectId() == 0x82 && s.subtype() == 0x28));
            assertSame(level, module.transformDecodedLevel(level));
            assertSame(original, level.getObjects()); assertSame(rings, level.getRings());
        }
        var other = new Inventory(0x17, 1);
        var lookalike = new Inventory(0x16, 1).getObjects().get(31); other.only(lookalike);
        assertSame(other, module.transformDecodedLevel(other));
        assertSame(lookalike, other.getObjects().getFirst());
    }
}
