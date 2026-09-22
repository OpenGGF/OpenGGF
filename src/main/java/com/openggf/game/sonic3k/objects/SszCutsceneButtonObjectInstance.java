package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code Obj_SSZCutsceneButton} ({@code $AF}, sonic3k.asm:133742-133749): the grey button
 * cutscene Knuckles lands on at {@code ($3A0,$C7C)} in Sky Sanctuary act 1.
 *
 * <p>It is inert. {@code SetUp_ObjAttributes} with {@code ObjDat_SSZCutsceneButton}
 * ({@code Map_Button} over {@code ArtTile_SSZCutsceneButton}, priority {@code $180},
 * {@code height_pixels $10}, {@code width_pixels 8}, {@code mapping_frame 1}) installs
 * {@code loc_659C6}, whose whole body is {@code jmp (Sprite_OnScreen_Test)}. It is not solid, it
 * has no touch response and it never changes frame: the switch sound and {@code Events_bg+$08}
 * are written by {@link CutsceneKnucklesSszInstance} routine {@code $12}, not by this object.
 *
 * <p>The engine's {@code Sonic3kObjectIds.ICZ_CRUSHING_COLUMN} shares the numeric ID {@code $AF};
 * that is the S3KL name, and this class is registered for the SKL set in zone {@code $0A} only.
 */
public final class SszCutsceneButtonObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    /** {@code ObjDat_SSZCutsceneButton}: {@code dc.w $180}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x180);
    /** {@code dc.b $10, 8, 1, 0}: height, width, mapping frame, render flags. */
    private static final int MAPPING_FRAME = 1;

    public SszCutsceneButtonObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSZCutsceneButton");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        // loc_659C6 is Sprite_OnScreen_Test only; the placement manager owns the range test.
    }

    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public int getOnScreenHalfWidth() { return 8; }
    @Override public int getOnScreenHalfHeight() { return 0x10; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_CUTSCENE_BUTTON);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(MAPPING_FRAME, getX(), getY(), false, false);
        }
    }
}
