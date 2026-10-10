package threeislands.field;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneLevelKit;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import threeislands.core.Zone;

class FieldArtTest {
    @Test
    void distantSceneryCoversTheViewportEvenInSouthernRooms() {
        int[] pixels = new int[256 * 256];
        Arrays.setAll(pixels, i -> 0xFF304050 + i % 3);
        SceneImage terrain = new SceneImage(256, 256, pixels);
        SceneImage sky = new SceneImage(256, 512, new int[256 * 512]);
        SceneBackdrop backdrop = new SceneBackdrop(sky, List.of(
                new SceneBackdrop.Band(0, 256, 0.25, 0),
                new SceneBackdrop.Band(256, 256, 0.5, 0)));
        SceneLevelKit kit = (SceneLevelKit) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {SceneLevelKit.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "palette" -> new int[] {0xFF304050};
                    case "backdrop" -> backdrop;
                    case "blockImage" -> terrain;
                    case "blockCount" -> 2;
                    case "blockSize" -> 256;
                    case "columns", "rows" -> 1;
                    case "block" -> 0;
                    default -> throw new AssertionError(method.getName());
                });
        FieldArt art = new FieldArt(Zone.SPRING_YARD, kit, null);
        Field field = new Field(Zone.SPRING_YARD, null);
        for (int width : new int[] {320, 400}) for (int cameraY : new int[] {0, 300, 900, 3000}) {
            boolean[] covered = new boolean[width * 224];
            SceneCanvas canvas = (SceneCanvas) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {SceneCanvas.class}, (proxy, method, args) -> {
                        if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
                        if (method.getName().equals("width")) return width;
                        if (method.getName().equals("height")) return 224;
                        if (method.getName().equals("drawRegion") && args[0] == sky) {
                            int sourceY = (int) args[2], sourceH = (int) args[4];
                            assertTrue(sourceY >= 0 && sourceY + sourceH <= sky.height());
                            int x = ((Number) args[5]).intValue(), y = ((Number) args[6]).intValue();
                            int w = ((Number) args[7]).intValue(), h = ((Number) args[8]).intValue();
                            for (int py = Math.max(0, y); py < Math.min(224, y + h); py++) {
                                for (int px = Math.max(0, x); px < Math.min(width, x + w); px++) {
                                    covered[py * width + px] = true;
                                }
                            }
                        }
                        return null;
                    });
            art.draw(canvas, field, 600, cameraY, 120);
            for (boolean pixel : covered) assertTrue(pixel,
                    "Background must cover the whole viewport at width " + width + ", camera Y " + cameraY);
        }
    }
}
