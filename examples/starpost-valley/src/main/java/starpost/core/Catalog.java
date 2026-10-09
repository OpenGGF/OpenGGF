package starpost.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every item and crop the game knows, keyed by id. Built once per scene by {@link Content};
 * instance-owned, because a mod may not keep mutable static tables.
 */
public final class Catalog {
    private final Map<String, Item> items = new LinkedHashMap<>();
    private final Map<String, CropDef> crops = new LinkedHashMap<>();
    private final Map<String, CropDef> cropsBySeed = new LinkedHashMap<>();
    private final Map<String, Integer> seedPrices = new LinkedHashMap<>();

    public Catalog() {
        new Content(this).register();
    }

    /** Registers an item; content registrars in other packages call this from {@link Content#register}. */
    public void add(Item item) {
        if (items.putIfAbsent(item.id(), item) != null) {
            throw new IllegalStateException("Duplicate item " + item.id());
        }
    }

    void add(CropDef crop, int seedPrice) {
        if (crops.putIfAbsent(crop.id(), crop) != null) {
            throw new IllegalStateException("Duplicate crop " + crop.id());
        }
        cropsBySeed.put(crop.seed(), crop);
        seedPrices.put(crop.seed(), seedPrice);
    }

    public Item item(String id) {
        Item item = items.get(id);
        if (item == null) {
            throw new IllegalArgumentException("Unknown item " + id);
        }
        return item;
    }

    public boolean hasItem(String id) {
        return items.containsKey(id);
    }

    public CropDef crop(String id) {
        return crops.get(id);
    }

    /** The crop a seed plants, or null when the item is not a seed. */
    public CropDef cropFromSeed(String seedId) {
        return cropsBySeed.get(seedId);
    }

    public int seedPrice(String seedId) {
        return seedPrices.getOrDefault(seedId, 0);
    }

    public List<Item> items() {
        return Collections.unmodifiableList(new ArrayList<>(items.values()));
    }

    public List<CropDef> crops() {
        return Collections.unmodifiableList(new ArrayList<>(crops.values()));
    }

    /** Seeds for a season, in catalogue order (the seed stall's stock). */
    public List<Item> seedsFor(int season) {
        List<Item> out = new ArrayList<>();
        for (CropDef crop : crops.values()) {
            if (crop.grows(season)) {
                out.add(items.get(crop.seed()));
            }
        }
        return out;
    }
}
