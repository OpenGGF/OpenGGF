package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.TouchResponseProvider;

import java.io.IOException;
import java.util.List;

/** SKL $52, Obj_DEZLightning / loc_478BE..loc_4791A (sonic3k.asm:93567-93611). */
public final class S3kDezLightningObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable, TouchResponseProvider {
    private boolean initialized;
    private boolean waiting;
    private boolean renderedOnScreen;
    private int cooldown;
    private int animationFrame;
    private int animationTimer;
    private int mappingFrame;

    public S3kDezLightningObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZLightning");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialized) {
            initialized = true;
            startFlash();
        } else if (waiting) {
            // loc_4791A: signed word pre-decrement; zero still waits this pass.
            cooldown = (short) (cooldown - 1);
            if (cooldown >= 0) return;
            startFlash();
        }
        advanceAnimation();
    }

    private void startFlash() {
        // loc_478BE reads the previous Render_Sprites result. The first init
        // seeds render_flags=$04, so it must not sound just because it is nearby.
        if (renderedOnScreen) services().playSfx(Sonic3kSfx.LIGHTNING.id);
        cooldown = spawn.subtype() & 0xFF;
        waiting = false;
        // move.w #1,anim means anim=0, prev_anim=1. Animate_Sprite sees a
        // change, clears anim_frame/timer, then executes the first frame now.
        animationFrame = 0;
        animationTimer = 0;
    }

    private void advanceAnimation() {
        animationTimer = (byte) (animationTimer - 1);
        if (animationTimer >= 0) return;
        try {
            var reader = services().romReader();
            int table = Sonic3kConstants.ANI_DEZ_LIGHTNING_ADDR;
            int script = table + reader.readU16BE(table);
            animationTimer = reader.readU8(script);
            int value = reader.readU8(script + 1 + animationFrame);
            if (value == 0xFC) {
                // Animate_Sprite increments routine; loc_478E2 immediately
                // clears it, clears the mapping frame and installs the wait.
                mappingFrame = 0;
                waiting = true;
            } else if (value < 0x80) {
                mappingFrame = value & 0x1F;
                animationFrame = (animationFrame + 1) & 0xFF;
            } else {
                throw new IllegalStateException("Unexpected DEZ lightning animation command: " + value);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("DEZ lightning requires the S3K ROM animation", failure);
        }
    }

    @Override
    public void refreshPostCameraRenderState() {
        // Render_Sprites sets bit 7 before checking the mapping piece count,
        // so the empty wait frame still updates this carried flag.
        if (initialized) renderedOnScreen = isWithinRenderSpriteBounds(8, 0x18);
    }

    @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) {
        return isCoarseXOutOfRange(getX(), cameraX, coarseXCullRange());
    }
    @Override public int getOnScreenHalfWidth() { return 8; }
    @Override public int getOnScreenHalfHeight() { return 0x18; }
    @Override public int getPriorityBucket() { return 5; } // move.w #$280,priority
    @Override public int getCollisionFlags() { return 0x9F; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public boolean requiresRenderFlagForTouch() { return false; }
    // loc_47906 only appends the pointer on pose 3. Collision flags remain
    // $9F in RAM; the next player pass consumes the preceding published list.
    @Override public boolean publishesTouchResponseListEntryThisFrame() {
        return initialized && mappingFrame == 3;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        var renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_LIGHTNING);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, getX(), getY(),
                    (spawn.renderFlags() & 1) != 0, (spawn.renderFlags() & 2) != 0);
        }
    }

    public int mappingFrameForTest() { return mappingFrame; }
    public int cooldownForTest() { return cooldown; }
    public boolean waitingForTest() { return waiting; }
}
