package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Inventory conservation and backwards-compatible UI progress in the actual packaged mod. */
class TestEggmansSkyQualityOfLife {
    @TempDir static Path work;
    private static ExampleModHarness harness;
    private static Class<?> catalogType;
    private static Class<?> playerType;
    private static Class<?> production;
    private static Object catalog;

    @BeforeAll static void build() throws Exception {
        harness = ExampleModHarness.build(Path.of("examples/eggmans-sky"), work);
        catalogType = harness.loader().loadClass("eggsky.game.Catalog");
        playerType = harness.loader().loadClass("eggsky.game.Player");
        production = harness.loader().loadClass("eggsky.game.Production");
        catalog = catalogType.getConstructor().newInstance();
    }
    @AfterAll static void close() throws Exception { if (harness != null) harness.close(); }
    private Object player() throws Exception { return playerType.getConstructor(catalogType).newInstance(catalog); }
    private int id(String name) throws Exception { return catalogType.getField(name).getInt(null); }
    private Object cargo(Object p) throws Exception { return playerType.getField("cargo").get(p); }
    private Object call(Object o, String method, Class<?>[] types, Object... args) throws Exception {
        return o.getClass().getMethod(method, types).invoke(o, args);
    }
    private void add(Object p, String name, int n) throws Exception {
        call(cargo(p), "add", new Class<?>[] {int.class, int.class}, id(name), n);
    }
    @SuppressWarnings("unchecked")
    private Map<Integer, Integer> reserves(Object p) throws Exception {
        return (Map<Integer, Integer>) playerType.getField("reserves").get(p);
    }
    private boolean make(Object p, String out, int outCount, String in, int count, int n) throws Exception {
        return (boolean) production.getMethod("make", playerType, int.class, int.class, int[].class, int[].class, int.class)
                .invoke(null, p, id(out), outCount, new int[] {id(in)}, new int[] {count}, n);
    }
    private int maximum(Object p, String out, int outCount, String in, int count) throws Exception {
        return (int) production.getMethod("maximum", playerType, int.class, int.class, int[].class, int[].class)
                .invoke(null, p, id(out), outCount, new int[] {id(in)}, new int[] {count});
    }
    private String encode(Object p) throws Exception { return (String) call(p, "encode", new Class<?>[0]); }

    @Test void fullCargoCanProduceIntoSlotsFreedByItsIngredients() throws Exception {
        Object p = player();
        Object bag = cargo(p);
        call(bag, "set", new Class<?>[] {int.class, int.class, int.class}, 0, id("FERRITE"), 50);
        for (int i = 1; i < 16; i++) {
            call(bag, "set", new Class<?>[] {int.class, int.class, int.class}, i, id("OXYGEN"), 250);
        }
        assertEquals(1, maximum(p, "METAL_PLATING", 1, "FERRITE", 50));
        assertTrue(make(p, "METAL_PLATING", 1, "FERRITE", 50, 1));
        assertEquals(1, call(bag, "count", new Class<?>[] {int.class}, id("METAL_PLATING")));
        assertEquals(3750, call(bag, "count", new Class<?>[] {int.class}, id("OXYGEN")));
    }

    @Test void failedOrReservedBatchesCannotDestroyOrConsumeCargo() throws Exception {
        Object p = player();
        add(p, "FERRITE", 100);
        reserves(p).put(id("FERRITE"), 60);
        String before = encode(p);
        assertEquals(0, maximum(p, "METAL_PLATING", 1, "FERRITE", 50));
        assertFalse(make(p, "METAL_PLATING", 1, "FERRITE", 50, 1));
        assertFalse(make(p, "PURE_FERRITE", 1, "FERRITE", 1, 0));
        assertEquals(before, encode(p));
        assertEquals(40, maximum(p, "PURE_FERRITE", 1, "FERRITE", 1));
        assertTrue(make(p, "PURE_FERRITE", 1, "FERRITE", 1, 40));
        assertEquals(60, call(cargo(p), "count", new Class<?>[] {int.class}, id("FERRITE")));
    }

    @Test void insufficientOutputCapacityLeavesFractionalRefiningInputsUntouched() throws Exception {
        Object p = player();
        Object bag = cargo(p);
        for (int i = 0; i < 16; i++) {
            call(bag, "set", new Class<?>[] {int.class, int.class, int.class}, i,
                    id(i == 0 ? "EMERIL" : "CHROMATIC_METAL"), i == 0 ? 250 : 249);
        }
        // 2 Emeril -> 3 metal: only five batches fit, without freeing the input slot.
        assertEquals(5, maximum(p, "CHROMATIC_METAL", 3, "EMERIL", 2));
        String before = encode(p);
        assertFalse(make(p, "CHROMATIC_METAL", 3, "EMERIL", 2, 6));
        assertEquals(before, encode(p));
        assertTrue(make(p, "CHROMATIC_METAL", 3, "EMERIL", 2, 5));
        assertEquals(240, call(bag, "count", new Class<?>[] {int.class}, id("EMERIL")));
    }

    @Test void pinsAndReservesRoundTripAndLegacySavesKeepDefaults() throws Exception {
        Object p = player();
        playerType.getField("pinned").setInt(p, id("WARP_CELL"));
        reserves(p).put(id("OXYGEN"), 25);
        Object loaded = player();
        call(loaded, "decode", new Class<?>[] {String.class}, encode(p));
        assertEquals(encode(p), encode(loaded));
        call(loaded, "decode", new Class<?>[] {String.class}, "version=1\nrings=42\n");
        assertEquals(0, playerType.getField("pinned").getInt(loaded));
        assertTrue(reserves(loaded).isEmpty());
    }

    @Test void pinExpandsMissingIntermediatesAndStopsRequestingOwnedOnes() throws Exception {
        Object p = player();
        playerType.getField("pinned").setInt(p, id("EGG_FUEL"));
        Class<?> list = harness.loader().loadClass("eggsky.game.ShoppingList");
        assertEquals(true, list.getMethod("wanted", playerType, int.class).invoke(null, p, id("DIHYDROGEN")));
        add(p, "DIHYDROGEN_JELLY", 1);
        assertEquals(false, list.getMethod("wanted", playerType, int.class).invoke(null, p, id("DIHYDROGEN")));
        assertEquals(true, list.getMethod("wanted", playerType, int.class).invoke(null, p, id("FERRITE")));
    }

    @Test void pickupAmountsAccumulateWhileWarningsPreserveDiscoveryBanners() throws Exception {
        Class<?> type = harness.loader().loadClass("eggsky.ui.Toasts");
        Object toasts = type.getConstructor().newInstance();
        call(toasts, "pickup", new Class<?>[] {int.class, int.class, String.class, int.class}, 1, 12, "Carbon", -1);
        call(toasts, "pickup", new Class<?>[] {int.class, int.class, String.class, int.class}, 1, 8, "Carbon", -1);
        var field = type.getDeclaredField("toasts"); field.setAccessible(true);
        List<?> items = (List<?>) field.get(toasts);
        assertEquals(1, items.size());
        var text = items.getFirst().getClass().getDeclaredField("text"); text.setAccessible(true);
        assertEquals("Carbon +20", text.get(items.getFirst()));
        call(toasts, "banner", new Class<?>[] {String.class, String.class, int.class}, "DISCOVERY", "Species", -1);
        call(toasts, "warning", new Class<?>[] {String.class, int.class}, "LOW LIFE", -1);
        for (int i = 0; i < 150; i++) call(toasts, "update", new Class<?>[0]);
        var age = type.getDeclaredField("bannerAge"); age.setAccessible(true);
        assertEquals(0, age.getInt(toasts));
        call(toasts, "update", new Class<?>[0]);
        assertEquals(1, age.getInt(toasts));
    }
}
