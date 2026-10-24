package com.openggf.game.sonic2;

import com.openggf.level.objects.ObjectRegistryCatalog.AnimalVariant;
import java.util.ArrayList;
import java.util.List;

/** Internal immutable projection of the native art provider's zone/variant table; no ROM art is loaded. */
public final class Sonic2AnimalCatalog {
    private Sonic2AnimalCatalog() { }
    public static List<AnimalVariant> variants() {
        var variants = new ArrayList<AnimalVariant>();
        for (int zone = 0; zone < Sonic2ObjectArt.ZONE_ANIMALS.length; zone++) {
            var pair = Sonic2ObjectArt.ZONE_ANIMALS[zone];
            for (int art = 0; art < pair.length; art++) variants.add(new AnimalVariant(zone, art, pair[art]));
        }
        return List.copyOf(variants);
    }
}
