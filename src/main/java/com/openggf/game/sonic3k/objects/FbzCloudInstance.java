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

    @Override public void update(int frameCounter, PlayableEntity player) {
        resolveWorldPosition();
    }

    private void resolveWorldPosition() {
        var handler = services().parallaxManager().getHandler(services().featureZoneId());
        if (!(handler instanceof FbzCloudPositionSource source)) return;
        SwScrlFbz.CloudPosition screen = source.cloudPositionAtAddressSlot(addressSlot);
        worldX = (services().camera().getX() + screen.x()) & 0xFFFF;
        worldY = (services().camera().getY() + screen.y()) & 0xFFFF;
        updateDynamicSpawn(worldX, worldY);
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        // CloudDeform runs after object updates. Resolve here as well so the first
        // rendered frame (and a rewind-render without an intervening update) uses
        // the current HScroll/address-table output rather than yesterday's cache.
        resolveWorldPosition();
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ_CLOUD);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, worldX, worldY, false, false);
        }
    }

    public int selector() { return selector; }
    public int addressSlot() { return addressSlot; }
    public int mappingFrame() { return mappingFrame; }
    @Override public int getX() { return worldX; }
    @Override public int getY() { return worldY; }
    @Override public int getPriorityBucket() { return 7; }
}
