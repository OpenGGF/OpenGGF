package survivors;

import com.openggf.level.objects.ObjectServices;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Pure owner-definition checks runnable in the exported creator fork, without a ROM. */
class DefinitionsTest {
    private static Object definitions() throws Exception {
        try { return Class.forName("survivors.Definitions").getDeclaredConstructor().newInstance(); }
        catch (ClassNotFoundException missing) { return null; }
    }

    @Test void warmedEnemyTraitsReuseOnePreparedDefinition() throws Exception {
        Object definitions = definitions();
        var enemy = new Enemy(Enemy.spawnAt(100, 200, Species.COCONUTS, false));
        enemy.setServices((ObjectServices) Proxy.newProxyInstance(ObjectServices.class.getClassLoader(),
                new Class<?>[]{ObjectServices.class}, (proxy, method, args) -> {
                    if (method.getName().equals("gameService")) return definitions;
                    throw new AssertionError("Unexpected definition lookup dependency: " + method.getName());
                }));
        enemy.traits();
        var distinct = Collections.newSetFromMap(new IdentityHashMap<Species.Traits, Boolean>());
        for (int lookup = 0; lookup < 1000; lookup++) distinct.add(enemy.traits());
        System.out.println("Warmed Enemy.traits: 1000 lookups, " + distinct.size() + " distinct trait records");
        assertEquals(1, distinct.size(), "warmed lookups reuse the immutable owner definition");
    }

    @Test void frameArraysCannotMutateAnExistingTrait() {
        var traits = Species.of(Species.COCONUTS);
        int first = traits.frame(0);
        int[] exposed = traits.frames();
        exposed[0] = 999;
        assertEquals(first, traits.frame(0));
        assertNotSame(traits.frames(), traits.frames());
    }

    private static Object lookup(Object definitions, String name, int id) throws Exception {
        assertNotNull(definitions, "one owner catalogue is prepared before lookups");
        return definitions.getClass().getDeclaredMethod(name, int.class).invoke(definitions, id);
    }

    @Test void preparedSpeciesPreserveEveryAuthoredTraitAndWrappedId() throws Exception {
        Object definitions = definitions();
        for (int id = 0; id < Species.COUNT; id++) {
            var expected = Species.of(id);
            var actual = (Species.Traits) lookup(definitions, "species", id);
            assertSame(actual, lookup(definitions, "species", id + Species.COUNT));
            assertSame(actual, lookup(definitions, "species", id - Species.COUNT));
            for (var component : Species.Traits.class.getRecordComponents()) {
                if (component.getName().equals("frames")) assertArrayEquals(expected.frames(), actual.frames());
                else assertEquals(component.getAccessor().invoke(expected), component.getAccessor().invoke(actual),
                        "species " + id + " " + component.getName());
            }
            for (int tick = 0; tick < 100; tick++) assertEquals(expected.frame(tick), actual.frame(tick));
        }
    }

    @SuppressWarnings("unchecked")
    @Test void preparedLineupsKeepEncounterOrderAndRejectConsumerMutation() throws Exception {
        Object definitions = definitions();
        for (int stage = 0; stage < Stages.COUNT; stage++) {
            Object lineup = lookup(definitions, "stage", stage);
            assertSame(lineup, lookup(definitions, "stage", stage));
            List<Integer> ground = (List<Integer>) lineup.getClass().getDeclaredMethod("ground").invoke(lineup);
            List<Integer> air = (List<Integer>) lineup.getClass().getDeclaredMethod("air").invoke(lineup);
            List<Integer> frames = (List<Integer>) lineup.getClass().getDeclaredMethod("bossFrames").invoke(lineup);
            assertEquals(java.util.Arrays.stream(Stages.ground(stage)).boxed().toList(), ground);
            assertEquals(java.util.Arrays.stream(Stages.air(stage)).boxed().toList(), air);
            assertEquals(java.util.Arrays.stream(Stages.bossFrames(stage)).boxed().toList(), frames);
            assertEquals(Stages.averageHp(stage), lineup.getClass().getDeclaredMethod("averageHp").invoke(lineup));
            assertThrows(UnsupportedOperationException.class, () -> ground.add(999));
            assertThrows(UnsupportedOperationException.class, () -> air.add(999));
            assertThrows(UnsupportedOperationException.class, () -> frames.set(0, 999));
        }
    }

    @Test void independentOwnerGraphsPrepareTheirOwnTables() throws Exception {
        Object first = definitions(), second = definitions();
        var distinctSpecies = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        var distinctLineups = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        for (int lookup = 0; lookup < 1000; lookup++) {
            int id = lookup % Species.COUNT, stage = lookup % Stages.COUNT;
            Object species = lookup(first, "species", id), lineup = lookup(first, "stage", stage);
            distinctSpecies.add(species);
            distinctLineups.add(lineup);
            assertNotSame(species, lookup(second, "species", id));
            assertNotSame(lineup, lookup(second, "stage", stage));
        }
        assertEquals(Species.COUNT, distinctSpecies.size());
        assertEquals(Stages.COUNT, distinctLineups.size());
    }
}
