package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.*;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.*;
import java.io.IOException;
import java.util.List;

/** CutsceneKnux_LRZ2 ($63A6A): Knuckles pushes the boulder after the camera rises. */
public final class CutsceneKnucklesLrz2Instance extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private int routine;
    private final S3kRawAnimation.State animation = new S3kRawAnimation.State();
    private transient S3kRawAnimation scripts;

    public CutsceneKnucklesLrz2Instance(ObjectSpawn spawn) { super(spawn, "LRZ2CutsceneKnucklesActor"); }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (player instanceof com.openggf.sprites.playable.AbstractPlayableSprite sprite && "knuckles".equals(sprite.getCode())) { setDestroyed(true); return; }
        var runtime = S3kRuntimeStates.currentLrz(services().zoneRuntimeRegistry()).orElseThrow();
        if (routine == 0) {
            routine = 2;
            animation.mappingFrame = 0x56;
            try {
                byte[] palette = services().romReader().slice(Sonic3kConstants.PAL_CUTSCENE_KNUX_ADDR, 32);
                S3kPaletteWriteSupport.applyContiguousPatch(services().paletteOwnershipRegistryOrNull(),
                        services().currentLevel(), services().graphicsManager(),
                        S3kPaletteOwners.LRZ_CUTSCENE_KNUCKLES, S3kPaletteOwners.PRIORITY_CUTSCENE_OVERRIDE,
                        1, 0, palette);
            } catch (IOException ex) { throw new IllegalStateException("LRZ cutscene palette", ex); }
            return;
        }
        if (routine == 2) {
            if ((runtime.cutsceneFlags() & 2) == 0) return;
            routine = 4;
            animation.mappingFrame = 0xDE;
            animation.script = 0x66891;
        }
        if (routine == 4 || routine == 6) {
            if (scripts == null) {
                try { scripts = S3kRawAnimation.load(services().romReader(), 0x66891, 0x1E); }
                catch (IOException ex) { throw new IllegalStateException("LRZ cutscene animation", ex); }
            }
            scripts.animateRaw2MultiDelay(animation, () -> {
                if (routine == 4) {
                    routine = 6;
                    runtime.setCutsceneFlag(2);
                    S3kRawAnimation.set(animation, 0x668A7);
                } else routine = 8;
            });
        }
    }

    @Override public int getX() { return 0x3A38; }
    @Override public int getY() { return 0xEC; }
    @Override public boolean isPersistent() { return true; }
    @Override public boolean isHighPriority() { return true; }
    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(0x180); }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        var renderer = getRenderer(Sonic3kObjectArtKeys.LRZ_CUTSCENE_KNUCKLES);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(animation.mappingFrame, getX(), getY(), true, false);
    }
}
