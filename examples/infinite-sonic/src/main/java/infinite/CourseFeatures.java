package infinite;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.graphics.GraphicsManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Course replacement for the S1 zone features. Outside the ending, the stock provider
 * only owns LZ/SBZ3 water: dynamic heights, wind tunnels and water slides. Slides and
 * tunnels match ROM layout block IDs, which generated blocks reuse for other terrain,
 * and underwater top speed is below the minimum scroll, so the course is dry.
 */
final class CourseFeatures implements ZoneFeatureProvider {
    @Override public void initZoneFeatures(Rom rom, int zoneIndex, int actIndex, int cameraX) { }
    @Override public void update(AbstractPlayableSprite player, int cameraX, int zoneIndex) { }
    @Override public void reset() { }
    @Override public boolean hasCollisionFeatures(int zoneIndex) { return false; }
    @Override public boolean hasWater(int zoneIndex) { return false; }
    @Override public int getWaterLevel(int zoneIndex, int actIndex) { return Integer.MAX_VALUE; }
    @Override public void render(Camera camera, int frameCounter) { }
    @Override public int ensurePatternsCached(GraphicsManager graphicsManager, int baseIndex) { return baseIndex; }
    // Matches Sonic1ZoneFeatureProvider: every S1 background wraps horizontally.
    @Override public boolean bgWrapsHorizontally() { return true; }
}
