package eggsky.world;

import java.util.ArrayList;
import java.util.List;

/**
 * The acts planets are remixed from, with the climate and weather each suggests and the
 * Sonic 3 &amp; Knuckles track that plays on its surface (the scene's music comes from the base
 * game, so Sonic 1 and Sonic 2 worlds borrow a matching S3K or competition-zone track).
 * Only biomes whose ROM is supplied and whose act has a level kit are used.
 */
public final class Biomes {
    private final List<Biome> all = new ArrayList<>();

    public Biomes() {
        // Sonic 3 & Knuckles (always present: the mod's base game).
        add("s3k", 0, 0, "Jungle", Biome.CLIMATE_LUSH, Biome.WEATHER_RAIN, 0x01, Biome.FLORA_JUNGLE, 3);
        add("s3k", 0, 1, "Scorched Jungle", Biome.CLIMATE_HOT, Biome.WEATHER_EMBERS, 0x02, Biome.FLORA_EMBER, 2);
        add("s3k", 1, 0, "Aquatic", Biome.CLIMATE_TEMPERATE, Biome.WEATHER_RAIN, 0x03, Biome.FLORA_CORAL, 2);
        // Hydrocity act 2 is left out: a detached build lacks tiles its events load.
        add("s3k", 2, 0, "Ancient Ruins", Biome.CLIMATE_TEMPERATE, Biome.WEATHER_SAND, 0x05, Biome.FLORA_JUNGLE, 2);
        add("s3k", 2, 1, "Ancient Ruins", Biome.CLIMATE_TEMPERATE, Biome.WEATHER_SAND, 0x06, Biome.FLORA_JUNGLE, 2);
        add("s3k", 3, 0, "Neon Carnival", Biome.CLIMATE_EXOTIC, Biome.WEATHER_SPARKS, 0x07, Biome.FLORA_TECH, 1);
        add("s3k", 3, 1, "Neon Carnival", Biome.CLIMATE_EXOTIC, Biome.WEATHER_SPARKS, 0x08, Biome.FLORA_TECH, 1);
        add("s3k", 4, 0, "Derelict Fortress", Biome.CLIMATE_DEAD, Biome.WEATHER_SPARKS, 0x09, Biome.FLORA_TECH, 0);
        add("s3k", 4, 1, "Derelict Fortress", Biome.CLIMATE_DEAD, Biome.WEATHER_SPARKS, 0x0A, Biome.FLORA_TECH, 0);
        add("s3k", 5, 0, "Frozen", Biome.CLIMATE_COLD, Biome.WEATHER_SNOW, 0x0B, Biome.FLORA_FROST, 1);
        add("s3k", 5, 1, "Glacial", Biome.CLIMATE_COLD, Biome.WEATHER_SNOW, 0x0C, Biome.FLORA_FROST, 1);
        add("s3k", 6, 0, "Industrial", Biome.CLIMATE_TOXIC, Biome.WEATHER_ACID, 0x0D, Biome.FLORA_TECH, 1);
        add("s3k", 6, 1, "Industrial", Biome.CLIMATE_TOXIC, Biome.WEATHER_ACID, 0x0E, Biome.FLORA_TECH, 1);
        add("s3k", 7, 0, "Fungal Forest", Biome.CLIMATE_LUSH, Biome.WEATHER_SPORES, 0x0F, Biome.FLORA_FUNGAL, 3);
        add("s3k", 7, 1, "Autumn Forest", Biome.CLIMATE_LUSH, Biome.WEATHER_SPORES, 0x10, Biome.FLORA_FUNGAL, 3);
        // Sandopolis's background art is uploaded by its events, so its skies are procedural.
        plain("s3k", 8, 0, "Desert", Biome.CLIMATE_HOT, Biome.WEATHER_SAND, 0x11, Biome.FLORA_DESERT, 1);
        plain("s3k", 8, 1, "Tomb World", Biome.CLIMATE_BARREN, Biome.WEATHER_SAND, 0x12, Biome.FLORA_DESERT, 1);
        add("s3k", 9, 0, "Volcanic", Biome.CLIMATE_HOT, Biome.WEATHER_EMBERS, 0x13, Biome.FLORA_EMBER, 1);
        add("s3k", 9, 1, "Magma Caverns", Biome.CLIMATE_HOT, Biome.WEATHER_ASH, 0x14, Biome.FLORA_EMBER, 1);
        add("s3k", 10, 0, "Sky Islands", Biome.CLIMATE_EXOTIC, Biome.WEATHER_NONE, 0x15, Biome.FLORA_JUNGLE, 2);
        add("s3k", 11, 0, "Mechanical", Biome.CLIMATE_RADIOACTIVE, Biome.WEATHER_SPARKS, 0x16, Biome.FLORA_TECH, 0);
        add("s3k", 11, 1, "Mechanical", Biome.CLIMATE_RADIOACTIVE, Biome.WEATHER_SPARKS, 0x17, Biome.FLORA_TECH, 0);
        add("s3k", 22, 1, "Crystal Caverns", Biome.CLIMATE_EXOTIC, Biome.WEATHER_SPARKS, 0x24, Biome.FLORA_CRYSTAL, 1);
        // Sonic 2.
        add("s2", 0, 0, "Paradise", Biome.CLIMATE_LUSH, Biome.WEATHER_RAIN, 0x21, Biome.FLORA_JUNGLE, 3);
        add("s2", 0, 1, "Paradise", Biome.CLIMATE_LUSH, Biome.WEATHER_RAIN, 0x21, Biome.FLORA_JUNGLE, 3);
        add("s2", 1, 0, "Chemical", Biome.CLIMATE_TOXIC, Biome.WEATHER_ACID, 0x23, Biome.FLORA_TECH, 1);
        add("s2", 1, 1, "Chemical", Biome.CLIMATE_TOXIC, Biome.WEATHER_ACID, 0x23, Biome.FLORA_TECH, 1);
        add("s2", 2, 0, "Overgrown Ruins", Biome.CLIMATE_LUSH, Biome.WEATHER_RAIN, 0x05, Biome.FLORA_JUNGLE, 3);
        add("s2", 2, 1, "Overgrown Ruins", Biome.CLIMATE_LUSH, Biome.WEATHER_RAIN, 0x06, Biome.FLORA_JUNGLE, 3);
        add("s2", 3, 0, "Casino", Biome.CLIMATE_EXOTIC, Biome.WEATHER_SPARKS, 0x07, Biome.FLORA_TECH, 1);
        add("s2", 3, 1, "Casino", Biome.CLIMATE_EXOTIC, Biome.WEATHER_SPARKS, 0x08, Biome.FLORA_TECH, 1);
        add("s2", 5, 0, "Crystal Mines", Biome.CLIMATE_COLD, Biome.WEATHER_NONE, 0x24, Biome.FLORA_CRYSTAL, 1);
        add("s2", 5, 1, "Crystal Mines", Biome.CLIMATE_COLD, Biome.WEATHER_NONE, 0x24, Biome.FLORA_CRYSTAL, 1);
        add("s2", 6, 0, "Oil Sea", Biome.CLIMATE_HOT, Biome.WEATHER_ASH, 0x22, Biome.FLORA_DESERT, 1);
        add("s2", 6, 1, "Oil Sea", Biome.CLIMATE_HOT, Biome.WEATHER_ASH, 0x22, Biome.FLORA_DESERT, 1);
        add("s2", 7, 0, "Factory", Biome.CLIMATE_RADIOACTIVE, Biome.WEATHER_SPARKS, 0x09, Biome.FLORA_TECH, 0);
        add("s2", 7, 1, "Factory", Biome.CLIMATE_RADIOACTIVE, Biome.WEATHER_SPARKS, 0x0A, Biome.FLORA_TECH, 0);
        add("s2", 7, 2, "Factory", Biome.CLIMATE_RADIOACTIVE, Biome.WEATHER_SPARKS, 0x09, Biome.FLORA_TECH, 0);
        // Sonic 1.
        add("s1", 0, 0, "Green Hills", Biome.CLIMATE_LUSH, Biome.WEATHER_RAIN, 0x20, Biome.FLORA_JUNGLE, 3);
        add("s1", 0, 1, "Green Hills", Biome.CLIMATE_LUSH, Biome.WEATHER_RAIN, 0x20, Biome.FLORA_JUNGLE, 3);
        add("s1", 0, 2, "Green Hills", Biome.CLIMATE_LUSH, Biome.WEATHER_RAIN, 0x20, Biome.FLORA_JUNGLE, 3);
        add("s1", 1, 0, "Marble Ruins", Biome.CLIMATE_HOT, Biome.WEATHER_EMBERS, 0x13, Biome.FLORA_EMBER, 1);
        add("s1", 1, 1, "Marble Ruins", Biome.CLIMATE_HOT, Biome.WEATHER_EMBERS, 0x13, Biome.FLORA_EMBER, 1);
        add("s1", 1, 2, "Marble Ruins", Biome.CLIMATE_HOT, Biome.WEATHER_EMBERS, 0x14, Biome.FLORA_EMBER, 1);
        add("s1", 2, 0, "Neon City", Biome.CLIMATE_EXOTIC, Biome.WEATHER_NONE, 0x08, Biome.FLORA_TECH, 1);
        add("s1", 2, 1, "Neon City", Biome.CLIMATE_EXOTIC, Biome.WEATHER_NONE, 0x08, Biome.FLORA_TECH, 1);
        add("s1", 2, 2, "Neon City", Biome.CLIMATE_EXOTIC, Biome.WEATHER_NONE, 0x08, Biome.FLORA_TECH, 1);
        add("s1", 3, 0, "Drowned Labyrinth", Biome.CLIMATE_TEMPERATE, Biome.WEATHER_RAIN, 0x04, Biome.FLORA_CORAL, 2);
        add("s1", 3, 1, "Drowned Labyrinth", Biome.CLIMATE_TEMPERATE, Biome.WEATHER_RAIN, 0x04, Biome.FLORA_CORAL, 2);
        add("s1", 3, 2, "Drowned Labyrinth", Biome.CLIMATE_TEMPERATE, Biome.WEATHER_RAIN, 0x04, Biome.FLORA_CORAL, 2);
        add("s1", 4, 0, "Starlight Ruins", Biome.CLIMATE_BARREN, Biome.WEATHER_NONE, 0x15, Biome.FLORA_TECH, 1);
        add("s1", 4, 1, "Starlight Ruins", Biome.CLIMATE_BARREN, Biome.WEATHER_NONE, 0x15, Biome.FLORA_TECH, 1);
        add("s1", 4, 2, "Starlight Ruins", Biome.CLIMATE_BARREN, Biome.WEATHER_NONE, 0x15, Biome.FLORA_TECH, 1);
        add("s1", 5, 0, "Scrapyard", Biome.CLIMATE_TOXIC, Biome.WEATHER_ACID, 0x16, Biome.FLORA_TECH, 0);
        add("s1", 5, 1, "Scrapyard", Biome.CLIMATE_TOXIC, Biome.WEATHER_ACID, 0x16, Biome.FLORA_TECH, 0);
    }

    private void add(String game, int zone, int act, String title, int climate, int weather, int music, int flora,
            int lush) {
        all.add(new Biome(game, zone, act, title, climate, weather, music, flora, lush, false));
    }

    private void plain(String game, int zone, int act, String title, int climate, int weather, int music, int flora,
            int lush) {
        all.add(new Biome(game, zone, act, title, climate, weather, music, flora, lush, true));
    }

    public List<Biome> all() {
        return all;
    }

    /** Biomes whose game is in {@code games}. */
    public List<Biome> available(List<String> games) {
        List<Biome> out = new ArrayList<>();
        for (Biome biome : all) {
            if (games.contains(biome.game())) {
                out.add(biome);
            }
        }
        return out;
    }
}
