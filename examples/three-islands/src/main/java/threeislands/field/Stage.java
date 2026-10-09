package threeislands.field;

import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneLevelKit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import threeislands.core.Zone;

/**
 * A zone drawn from its level kit: the stock background with its parallax bands, then the act's
 * own foreground blocks around a camera. Used by the field, by battles (fought where the party
 * stood) and behind story scenes.
 */
public final class Stage {
    public final Zone zone;
    public final SceneLevelKit kit;
    public final FieldPath path;
    public final FieldArt fieldArt;
    private final int size;
    private final int[] area;
    private double camX;
    private double camY;
    private int contentHeight = -1;
    private int skyColour;
    private int groundColour;
    private double groundCamY = Double.NaN;

    public Stage(Zone zone, SceneLevelKit kit, FieldPath path) {
        this.zone = zone;
        this.kit = kit;
        this.path = path;
        this.fieldArt = new FieldArt(zone, kit);
        this.size = kit.blockSize();
        this.area = KitTerrain.extent(kit);
    }

    /** Builds the route for a zone's act: from its start position to near the act's end. */
    public static FieldPath route(Zone zone, SceneLevelKit kit) {
        int[] area = KitTerrain.extent(kit);
        int end = area[0] + area[2] - 220;
        return FieldPath.build(new KitTerrain(kit), zone.startX, end, zone.startY);
    }

    public double camX() { return camX; }
    public double camY() { return camY; }

    /**
     * Points the camera so level point ({@code x}, {@code feetY}) sits at screen
     * ({@code anchorX}, {@code anchorY}); {@code ease} 1 snaps, smaller values glide.
     */
    public void look(double x, double feetY, int anchorX, int anchorY, int width, int height, double ease) {
        double tx = x - anchorX;
        double ty = feetY - anchorY;
        tx = Math.max(area[0], Math.min(area[0] + area[2] - width, tx));
        ty = Math.max(area[1], Math.min(area[1] + area[3] - height, ty));
        camX += (tx - camX) * ease;
        camY += (ty - camY) * ease;
        if (ease >= 1) {
            camX = tx;
            camY = ty;
        }
    }

    public int sx(double levelX) { return (int) Math.round(levelX - camX); }
    public int sy(double levelY) { return (int) Math.round(levelY - camY); }

    public void draw(SceneCanvas c, long ticks) {
        int w = c.width();
        int h = c.height();
        c.clear(0x000000);
        SceneBackdrop backdrop = kit.backdrop();
        if (backdrop != null && backdrop.bands().size() > 1) {
            // Sonic 3 & Knuckles zone pictures: parallax bands laid out for the stock screen.
            int span = Math.max(0, backdrop.image().height() - h);
            c.drawBackdrop(backdrop, Math.min(span, 128), camX, ticks);
        } else if (backdrop != null) {
            plane(c, backdrop, w, h, ticks);
        }
        int c0 = (int) Math.floor(camX / size);
        int c1 = (int) Math.floor((camX + w) / size);
        int r0 = (int) Math.floor(camY / size);
        int r1 = (int) Math.floor((camY + h) / size);
        for (int row = Math.max(0, r0); row <= r1; row++) {
            for (int col = Math.max(0, c0); col <= c1; col++) {
                int block = kit.block(col, row);
                if (block == 0) continue;
                c.draw(kit.blockImage(block), (float) (col * size - Math.round(camX)), (float) (row * size - Math.round(camY)));
            }
        }
    }

    /**
     * A Sonic 1 or Sonic 2 background plane, laid out as one picture. Its scenery is anchored
     * by the bottom of its content (flat rows below are trimmed), which sits at the screen's
     * bottom when the camera is at the start of the route and drifts slowly with height; above
     * and below it the plane's own top and bottom colours continue. This follows Eggman's Sky,
     * which draws the same detached planes.
     */
    private void plane(SceneCanvas c, SceneBackdrop backdrop, int w, int h, long ticks) {
        var image = backdrop.image();
        if (contentHeight < 0) {
            contentHeight = image.height();
            while (contentHeight > 32) {
                int first = image.pixel(0, contentHeight - 1);
                boolean flat = true;
                for (int x = 1; x < image.width() && flat; x += 2) flat = image.pixel(x, contentHeight - 1) == first;
                if (!flat) break;
                contentHeight--;
            }
            skyColour = image.pixel(0, 0) | 0xFF000000;
            groundColour = image.pixel(0, contentHeight - 1) | 0xFF000000;
        }
        if (Double.isNaN(groundCamY)) groundCamY = path.heightAt(path.startX()) - 150;
        int bgY = (int) Math.round(h - contentHeight + (groundCamY - camY) * 0.12);
        c.clear(skyColour & 0xFFFFFF);
        c.drawBackdrop(backdrop, 0, bgY, w, contentHeight, 0, camX, ticks);
        if (bgY + contentHeight < h) c.fill(0, bgY + contentHeight, w, h - bgY - contentHeight, groundColour);
    }

    /**
     * Block ids near the route, in walking order, for warming the kit's block pictures during a
     * loading screen so walking never decodes art.
     */
    public List<Integer> blocksNearRoute(int width, int height) {
        Set<Integer> ids = new LinkedHashSet<>();
        for (int x = path.startX() - width; x <= path.endX() + width; x += size) {
            int y = path.heightAt(Math.max(path.startX(), Math.min(path.endX(), x)));
            for (int dy = -height; dy <= height; dy += size) {
                int block = kit.block(Math.floorDiv(x, size), Math.floorDiv(y + dy, size));
                if (block != 0) ids.add(block);
            }
        }
        return new ArrayList<>(ids);
    }
}
