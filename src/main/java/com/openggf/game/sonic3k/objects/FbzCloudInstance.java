package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.scroll.FbzCloudPositionSource;
import com.openggf.game.sonic3k.scroll.SwScrlFbz;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Screen-space {@code Obj_FBZCloud}; its position is authored by CloudDeform. */
public final class FbzCloudInstance extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private final int selector;
    private final int addressSlot;
    private final int mappingFrame;
    private int worldX;
    private int worldY;

    public FbzCloudInstance(int selector) {
        this(new ObjectSpawn(0, 0, 0, selector, 0, false, 0));
    }

    public FbzCloudInstance(ObjectSpawn spawn) {
        super(spawn, "FBZCloud");
        selector = spawn.subtype();
        if (selector < 0 || selector > 9) throw new IllegalArgumentException("FBZ cloud selector: " + selector);
        addressSlot = 9 - selector;
        mappingFrame = SwScrlFbz.cloudMappingFrameForSelector(selector);
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        resolveWorldPosition();
    }

    private void resolveWorldPosition() {
        Position position = currentWorldPosition();
        worldX = position.x();
        worldY = position.y();
        updateDynamicSpawn(worldX, worldY);
    }

    private record Position(int x, int y) { }

    private Position currentWorldPosition() {
        var handler = services().parallaxManager().getHandler(services().featureZoneId());
        if (!(handler instanceof FbzCloudPositionSource source)) return new Position(worldX, worldY);
        SwScrlFbz.CloudPosition screen = source.cloudPositionAtAddressSlot(addressSlot);
        // CloudDeform writes VDP screen coordinates ($80 bias on both axes).
        return new Position((services().camera().getX() + screen.x() - 0x80) & 0xFFFF,
                (services().camera().getY() + screen.y() - 0x80) & 0xFFFF);
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var projected = com.openggf.level.LevelSpritePresentation.spritePositionForDraw(
                services().levelManager(), new com.openggf.level.scroll.ScrollSpriteKey("s3k/fbz-cloud", addressSlot));
        // CloudDeform runs after object updates. Resolve here as well so the first
        // rendered frame (and a rewind-render without an intervening update) uses
        // the current HScroll/address-table output rather than yesterday's cache.
        // A cinematic projection owns only this draw's coordinates. In particular,
        // never publish the temporary director camera into the object's native RAM.
        Position current = projected == null ? currentWorldPosition() : new Position(projected.x(), projected.y());
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ_CLOUD);
        if (renderer != null && renderer.isReady()) {
            // FBZ_CLOUD filters native frames 1/2/3 into sheet indices 0/1/2.
            renderer.drawFrameIndex(mappingFrame - 1, current.x(), current.y(), false, false);
        }
    }

    public int selector() { return selector; }
    public int addressSlot() { return addressSlot; }
    public int mappingFrame() { return mappingFrame; }
    @Override public int getX() { return worldX; }
    @Override public int getY() { return worldY; }
    @Override public int getOnScreenHalfWidth() { return 0x2C; }
    @Override public int getOnScreenHalfHeight() { return 0x0C; }
    @Override public int getPriorityBucket() { return 7; }
}
