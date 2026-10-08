package sitarhero.stage;

import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;
import java.util.ArrayList;
import java.util.List;

/** Native-pixel partition and cached nearest-neighbour articulation around a limb's pivot. */
final class PerformerCutout {
    record Mask(int pivotX, int pivotY, int... points) {
        Mask {
            points = points.clone();
            if (points.length < 6 || points.length % 2 != 0) throw new IllegalArgumentException("Polygon required");
        }
        boolean contains(int x, int y) {
            boolean inside = false;
            double px = x + .5, py = y + .5;
            for (int i = 0, j = points.length - 2; i < points.length; j = i, i += 2) {
                int xi = points[i], yi = points[i + 1], xj = points[j], yj = points[j + 1];
                if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) inside = !inside;
            }
            return inside;
        }
        Mask inLayer(int dx, int dy) {
            int[] local = points.clone();
            for (int i = 0; i < local.length; i += 2) { local[i] -= dx; local[i + 1] -= dy; }
            return new Mask(pivotX - dx, pivotY - dy, local);
        }
    }
    record Split(SceneSprite body, List<Limb> limbs) { }
    record Positioned(SceneSprite sprite, int x, int y) { }

    static final class Limb {
        final int x, y;
        final SceneSprite nativePart;
        private final SceneSprite[] turns;
        Limb(SceneSprite nativePart, int x, int y) {
            this.nativePart = nativePart; this.x = x; this.y = y;
            turns = new SceneSprite[19];
            for (int i = 0; i < turns.length; i++) turns[i] = rotate(nativePart, (i - 9) * 10);
        }
        private Limb(Limb source, int dx, int dy) {
            nativePart = source.nativePart; turns = source.turns;
            x = source.x + dx; y = source.y + dy;
        }
        Limb inActor(int dx, int dy) { return new Limb(this, dx, dy); }
        SceneSprite turn(int step) { return turns[Math.clamp(step + 9, 0, turns.length - 1)]; }
    }

    /** Partition each owning frame before compositing: glass/legs behind an arm stay put. */
    static Split splitLayers(List<Positioned> sources, Mask[] masks, int[] owners) {
        List<Positioned> body = new ArrayList<>();
        Limb[] limbs = new Limb[masks.length];
        for (int layer = 0; layer < sources.size(); layer++) {
            Positioned source = sources.get(layer);
            List<Mask> local = new ArrayList<>();
            List<Integer> indices = new ArrayList<>();
            for (int index = 0; index < masks.length; index++) if (owners[index] == layer) {
                local.add(masks[index].inLayer(source.x(), source.y())); indices.add(index);
            }
            Split split = split(source.sprite(), local.toArray(Mask[]::new));
            body.add(new Positioned(split.body(), source.x(), source.y()));
            for (int index = 0; index < indices.size(); index++)
                limbs[indices.get(index)] = split.limbs().get(index).inActor(source.x(), source.y());
        }
        return new Split(compose(body), List.of(limbs));
    }

    static Split split(SceneSprite source, Mask... masks) {
        SceneImage image = source.image();
        int[] body = image.pixels();
        List<Limb> limbs = new ArrayList<>();
        for (Mask mask : masks) {
            int[] selected = new int[body.length];
            int minX = image.width(), minY = image.height(), maxX = -1, maxY = -1;
            for (int y = 0; y < image.height(); y++) for (int x = 0; x < image.width(); x++) {
                int index = y * image.width() + x;
                if ((body[index] >>> 24) == 0 || !mask.contains(x - source.originX(), y - source.originY())) continue;
                selected[index] = body[index]; body[index] = 0;
                minX = Math.min(minX, x); minY = Math.min(minY, y);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
            }
            if (maxX < minX) {
                // Controlled-art test fixtures and unavailable ROM frames may be transparent.
                limbs.add(new Limb(new SceneSprite(new SceneImage(1, 1, new int[1]), mask.pivotX(), mask.pivotY()),
                        mask.pivotX(), mask.pivotY()));
                continue;
            }
            SceneImage cut = new SceneImage(image.width(), image.height(), selected)
                    .crop(minX, minY, maxX - minX + 1, maxY - minY + 1);
            limbs.add(new Limb(new SceneSprite(cut, source.originX() + mask.pivotX() - minX,
                    source.originY() + mask.pivotY() - minY), mask.pivotX(), mask.pivotY()));
        }
        return new Split(new SceneSprite(new SceneImage(image.width(), image.height(), body),
                source.originX(), source.originY()), List.copyOf(limbs));
    }

    static SceneSprite compose(List<Positioned> layers) {
        int left = 0, top = 0, right = 1, bottom = 1;
        for (Positioned layer : layers) {
            SceneSprite sprite = layer.sprite();
            left = Math.min(left, layer.x() - sprite.originX());
            top = Math.min(top, layer.y() - sprite.originY());
            right = Math.max(right, layer.x() - sprite.originX() + sprite.width());
            bottom = Math.max(bottom, layer.y() - sprite.originY() + sprite.height());
        }
        int width = right - left, height = bottom - top;
        int[] pixels = new int[width * height];
        for (Positioned layer : layers) {
            SceneSprite sprite = layer.sprite();
            for (int y = 0; y < sprite.height(); y++) for (int x = 0; x < sprite.width(); x++) {
                int pixel = sprite.image().pixel(x, y);
                if ((pixel >>> 24) != 0) pixels[(y + layer.y() - sprite.originY() - top) * width
                        + x + layer.x() - sprite.originX() - left] = pixel;
            }
        }
        return new SceneSprite(new SceneImage(width, height, pixels), -left, -top);
    }

    private static SceneSprite rotate(SceneSprite source, int degrees) {
        if (degrees == 0) return source;
        double a = Math.toRadians(degrees), cos = Math.cos(a), sin = Math.sin(a);
        int radius = (int) Math.ceil(Math.hypot(Math.max(Math.abs(source.originX()), Math.abs(source.width() - source.originX())),
                Math.max(Math.abs(source.originY()), Math.abs(source.height() - source.originY())))) + 1;
        int side = radius * 2 + 1;
        int[] out = new int[side * side];
        for (int y = 0; y < side; y++) for (int x = 0; x < side; x++) {
            double dx = x - radius, dy = y - radius;
            int sx = (int) Math.round(dx * cos + dy * sin) + source.originX();
            int sy = (int) Math.round(-dx * sin + dy * cos) + source.originY();
            out[y * side + x] = source.image().pixel(sx, sy);
        }
        return new SceneSprite(new SceneImage(side, side, out), radius, radius);
    }
}
