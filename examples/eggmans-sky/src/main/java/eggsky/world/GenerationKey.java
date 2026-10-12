package eggsky.world;

/** Lookup identity for generated data; durable discovery and loot IDs remain coordinates. */
public record GenerationKey(long galaxySeed, int galaxyNumber, long systemId, int planetIndex,
        long planetSeed, String biome, int schema) {
    public static final int SCHEMA = 1;
}
