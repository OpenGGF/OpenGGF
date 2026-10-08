package com.openggf.mods;

import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.objects.ObjectArtKeys;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;
import java.util.TreeSet;
import static org.junit.jupiter.api.Assertions.*;

class TestStockArtOverrideCatalog {
    @Test void catalogueTracksTheOwningProviderKeyInventories() throws Exception {
        var expected = new TreeSet<String>();
        for (Class<?> source : new Class<?>[]{ObjectArtKeys.class, Sonic2ObjectArtKeys.class, Sonic3kObjectArtKeys.class}) {
            for (var field : source.getFields()) {
                if (field.getType() == String.class && Modifier.isStatic(field.getModifiers()))
                    expected.add((String) field.get(null));
            }
        }
        assertEquals(expected.stream().toList(), StockArtOverrideCatalog.keys());
        assertTrue(StockArtOverrideCatalog.contains("signpost"));
        assertTrue(StockArtOverrideCatalog.contains("EndSign"));
        assertFalse(StockArtOverrideCatalog.contains("s2", "EndSign"));
        assertTrue(StockArtOverrideCatalog.contains("s3k", "EndSign"));
        for (var pair : java.util.Map.of("s1", ObjectArtKeys.class, "s2", Sonic2ObjectArtKeys.class, "s3k", Sonic3kObjectArtKeys.class).entrySet()) {
            var gameKeys = new TreeSet<String>();
            for (Class<?> source : new Class<?>[]{ObjectArtKeys.class, pair.getValue()})
                for (var field : source.getFields())
                    if (field.getType() == String.class && Modifier.isStatic(field.getModifiers())) gameKeys.add((String) field.get(null));
            assertEquals(gameKeys.stream().toList(), StockArtOverrideCatalog.keys(pair.getKey()));
        }
    }
}
