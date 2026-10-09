package starpost.fishing;

import starpost.core.Catalog;
import starpost.core.Item;
import starpost.core.Kind;

/**
 * Fishing's items, registered from {@code Content.register}: every fish and badnik shell in the
 * {@link FishTable} (Kind FISH, so the Angler profession pays more for them; badnik shells carry
 * the {@link #BADNIK_ICON} icon key, which the Reef Hand profession recognises) and Barnaby's hat.
 */
public final class FishingContent {
    /** The icon-key prefix of a badnik catch (see {@code Game.sellPrice}). */
    public static final String BADNIK_ICON = "badnik:";

    private final Catalog catalog;

    public FishingContent(Catalog catalog) {
        this.catalog = catalog;
    }

    public void register() {
        for (FishDef def : new FishTable().all()) {
            catalog.add(new Item(def.id(), def.name(), Kind.FISH, def.price(), 0,
                    def.isBadnik() ? BADNIK_ICON + def.badnik() : def.id(), def.text()));
        }
        catalog.add(new Item(Fishing.HAT, "BARNABY'S HAT", Kind.RELIC, 0, 0, Fishing.HAT,
                "RED, WITH A FEATHER. THE RED CHOPPER HAD IT. GIVE IT BACK."));
    }
}
