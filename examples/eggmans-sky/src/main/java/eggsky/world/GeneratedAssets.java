package eggsky.world;

import com.openggf.mods.scene.SceneImage;
import eggsky.space.GalaxyMode;
import eggsky.space.PlanetTexture;
import java.util.LinkedHashMap;
import java.util.Map;

/** Current planet and bounded derived pictures; gameplay and expedition state remain in Game/Player. */
public final class GeneratedAssets {
    private Planet planet;
    private SceneImage galaxyImage;
    private long galaxyImageSeed;
    public final LinkedHashMap<GenerationKey, PlanetTexture> orbitTextures = new LinkedHashMap<>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<GenerationKey, PlanetTexture> entry) {
            return size() > 24;
        }
    };

    public Planet planet() { return planet; }
    public Planet find(GenerationKey key) {
        return planet != null && planet.generationKey.equals(key) ? planet : null;
    }
    public Planet remember(Planet next) { planet = next; return next; }
    public SceneImage galaxyImage(long seed) {
        if (galaxyImage == null || galaxyImageSeed != seed) {
            galaxyImage = GalaxyMode.galaxyImage(seed);
            galaxyImageSeed = seed;
        }
        return galaxyImage;
    }
    /** Drop lookup references; callers may still hold host-owned images. */
    public void clear() { planet = null; orbitTextures.clear(); galaxyImage = null; }
}
