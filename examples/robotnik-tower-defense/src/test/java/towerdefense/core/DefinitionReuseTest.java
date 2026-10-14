package towerdefense.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefinitionReuseTest {
    @Test void previewsReusePreparedWavesWithoutChangingTheCampaign() throws Exception {
        Battlefield first = new Battlefield(); var waves = new IdentityHashMap<Catalog.Wave, Boolean>();
        for (int i = 0; i < 1000; i++) waves.put(first.preview(), true);
        var text = new StringBuilder();
        for (int number = 1; number <= Catalog.WAVES; number++) {
            var wave = Catalog.wave(number); text.append(number).append('|').append(wave.name()).append('|').append(wave.tip());
            for (var spawn : wave.spawns()) text.append('|').append(spawn.kind()).append('@').append(spawn.tick());
        }
        String signature = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8)));
        System.out.println("Tower preview identities=" + waves.size() + " campaign=" + signature);
        assertEquals("08b5a58c0e7472f76d43886367a15605fd52828dc32dbc0405142a6631ad6162", signature, "content and draw order pinned before preparation");
        assertEquals(1, waves.size());
        Battlefield second = new Battlefield(); assertEquals(first.preview(), second.preview()); assertNotSame(first.preview(), second.preview());
        assertThrows(UnsupportedOperationException.class, () -> first.preview().spawns().clear());
        first.startWave(); assertEquals(0, second.wave());
    }
    @Test void separateScenesHaveImmutableDefinitionsAndDefensesReuseTheirPreparedValues() {
        Catalog.Prepared first = new Catalog.Prepared(), second = new Catalog.Prepared();
        for (int wave = 1; wave <= Catalog.WAVES; wave++) {
            assertEquals(Catalog.wave(wave), first.wave(wave)); assertSame(first.wave(wave), first.wave(wave));
            assertNotSame(first.wave(wave), second.wave(wave));
        }
        for (int kind = Catalog.SNALE; kind <= Catalog.EGG_ROBO; kind++) {
            assertEquals(Catalog.defense(kind), first.defense(kind)); assertSame(first.defense(kind), first.defense(kind));
            assertNotSame(first.defense(kind), second.defense(kind));
        }
        var mutable = new java.util.ArrayList<Catalog.Spawn>(); mutable.add(new Catalog.Spawn(0, 1));
        Catalog.Wave wave = new Catalog.Wave("fixture", "tip", mutable); mutable.clear();
        assertEquals(1, wave.spawns().size());
        assertThrows(UnsupportedOperationException.class, () -> wave.spawns().clear());
        assertThrows(IllegalArgumentException.class, () -> first.wave(0));
        assertThrows(IllegalArgumentException.class, () -> first.defense(-1));
    }

}
