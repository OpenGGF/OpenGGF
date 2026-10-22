package com.openggf.game.sonic3k;

import com.openggf.level.objects.ObjectRegistryCatalog.AnimalVariant;
import java.util.ArrayList;
import java.util.List;

/** Internal immutable projection of the native art provider's zone/variant table; no ROM art is loaded. */
public final class Sonic3kAnimalCatalog {
    private Sonic3kAnimalCatalog() { }
    public static List<AnimalVariant> variants() {
        var variants = new ArrayList<AnimalVariant>();
        for (int zone = 0; zone < Sonic3kObjectArtProvider.S3K_ZONE_ANIMALS.length; zone++) {
            var pair = Sonic3kObjectArtProvider.S3K_ZONE_ANIMALS[zone];
            for (int art = 0; art < pair.length; art++) variants.add(new AnimalVariant(zone, art, pair[art]));
        }
        return List.copyOf(variants);
    }
}
