package starpost.art;

import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;
import starpost.fishing.FishArt;
import starpost.orchard.Orchard;
import starpost.core.Item;
import starpost.core.Kind;
import static org.junit.jupiter.api.Assertions.*;

/** The remaining concrete art facades preserve original successes while failed inputs recover. */
class AdditionalArtFailuresTest {
    @Test void fishFailedSpriteRecoversWithoutRepeatedDrawDecodes() {
        var f = new ArtRetryTest.Fixture(); Art art = f.art();
        f.failure = new IllegalArgumentException("fish art");
        FishArt fish = new FishArt(art);
        assertNull(fish.badnikFrame("chopper", 0));
        int first = f.spriteLoads;
        for (int i = 0; i < 20; i++) assertNull(fish.badnikFrame("chopper", 0));
        assertEquals(first, f.spriteLoads);
        f.failure = null; f.ticks = 120;
        assertNotNull(fish.badnikFrame("chopper", 0));
    }

    @Test void fishProgrammingFaultIsNotOptionalMissingArt() {
        var f = new ArtRetryTest.Fixture(); Art art = f.art();
        f.failure = new NullPointerException("fish decoder bug");
        assertThrows(NullPointerException.class, () -> new FishArt(art));
    }

    @Test void treeFallbackDoesNotBecomePermanentMushroomArt() throws Exception {
        var f = new ArtRetryTest.Fixture(); Art art = f.art();
        Object tree = create("starpost.orchard.TreeArt", art);
        f.kitAvailable = false;
        Object fallback = call(tree, "picture", new Class<?>[] {String.class, int.class, Tone.class}, Orchard.RING_FRUIT, 2, new Tone(0));
        f.kitAvailable = true; f.ticks = 119;
        call(tree, "picture", new Class<?>[] {String.class, int.class, Tone.class}, Orchard.RING_FRUIT, 2, new Tone(0));
        f.ticks = 120;
        Object repaired = call(tree, "picture", new Class<?>[] {String.class, int.class, Tone.class}, Orchard.RING_FRUIT, 2, new Tone(0));
        assertNotEquals(((com.openggf.mods.scene.SceneImage) fallback).width(), ((com.openggf.mods.scene.SceneImage) repaired).width(), "recovered Mushroom Hill clump must replace the uncached palm fallback");
        f.failure = new IllegalArgumentException("splash");
        assertNull(call(tree, "splash", new Class<?>[0]));
        f.failure = null; f.ticks = 240;
        assertNotNull(call(tree, "splash", new Class<?>[0]));
    }

    @Test void treeProgrammingFaultPropagates() throws Exception {
        var f = new ArtRetryTest.Fixture(); Object tree = create("starpost.orchard.TreeArt", f.art());
        f.kitFailure = new NullPointerException("tree kit bug");
        assertThrows(NullPointerException.class, () -> call(tree, "picture", new Class<?>[] {String.class, int.class, Tone.class}, Orchard.RING_FRUIT, 2, new Tone(0)));
    }

    @Test void museumFailedBankAndDerivedPictureAreNotNegativeCacheEntries() throws Exception {
        var f = new ArtRetryTest.Fixture(); Object museum = create("starpost.museum.MuseumArt", f.art());
        f.failure = new IllegalArgumentException("museum sprite");
        assertNull(call(museum, "badnikPicture", new Class<?>[] {String.class}, "batbrain"));
        int first = f.spriteLoads;
        for (int i = 0; i < 20; i++) assertNull(call(museum, "badnikPicture", new Class<?>[] {String.class}, "batbrain"));
        assertEquals(first, f.spriteLoads);
        f.failure = null; f.ticks = 120;
        assertNotNull(call(museum, "badnikPicture", new Class<?>[] {String.class}, "batbrain"));
    }

    @Test void museumProgrammingFaultAndParentCloseAreTerminal() throws Exception {
        var f = new ArtRetryTest.Fixture(); Art art = f.art(); Object museum = create("starpost.museum.MuseumArt", art);
        f.failure = new NullPointerException("museum decoder bug");
        assertThrows(NullPointerException.class, () -> call(museum, "badnikPicture", new Class<?>[] {String.class}, "batbrain"));
        art.close();
        assertThrows(IllegalStateException.class, () -> call(museum, "relic", new Class<?>[] {String.class}, "ring_mould"));
    }

    @Test void repairedMuseumArtReplacesTheActualItemIconFallback() throws Exception {
        var f = new ArtRetryTest.Fixture(); Art art = f.art(); Object museum = create("starpost.museum.MuseumArt", art);
        Item item = new Item("batbrain_part", "BATBRAIN PART", Kind.MATERIAL, 0, 0, "part:batbrain", "");
        art.icons.addSource("museum", wanted -> {
            try { return (com.openggf.mods.scene.SceneImage) call(museum, "icon", new Class<?>[] {Item.class}, wanted); }
            catch (Exception failure) { throw new AssertionError(failure); }
        });
        com.openggf.mods.scene.SceneImage[] drawn = new com.openggf.mods.scene.SceneImage[1];
        var canvas = (com.openggf.mods.scene.SceneCanvas) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {com.openggf.mods.scene.SceneCanvas.class}, (p, m, a) -> {
                    if (m.getName().equals("draw") && a[0] instanceof com.openggf.mods.scene.SceneImage picture) drawn[0] = picture;
                    return null;
                });
        f.failure = new IllegalArgumentException("museum art");
        art.icons.draw(canvas, item, 0, 0, com.openggf.mods.scene.SceneDraw.plain());
        var fallback = drawn[0];
        f.failure = null; f.ticks = 120;
        art.icons.draw(canvas, item, 0, 0, com.openggf.mods.scene.SceneDraw.plain());
        assertFalse(java.util.Arrays.equals(fallback.pixels(), drawn[0].pixels()), "the rendered icon must recover as well as the bank");
    }

    private static Object create(String type, Art art) throws Exception {
        var constructor = Class.forName(type).getDeclaredConstructor(Art.class); constructor.setAccessible(true);
        return constructor.newInstance(art);
    }
    private static Object call(Object target, String method, Class<?>[] types, Object... args) throws Exception {
        var member = target.getClass().getDeclaredMethod(method, types); member.setAccessible(true);
        try { return member.invoke(target, args); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw failure;
        }
    }
}
