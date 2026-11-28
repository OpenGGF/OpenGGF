package com.openggf.game.sonic2.specialstage;

import com.openggf.game.SpecialStageViewport;
import com.openggf.graphics.GLCommandable;

import java.util.Objects;

import static org.lwjgl.opengl.GL11.GL_SCISSOR_BOX;
import static org.lwjgl.opengl.GL11.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL11.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glGetIntegerv;
import static org.lwjgl.opengl.GL11.glIsEnabled;
import static org.lwjgl.opengl.GL11.glScissor;

/**
 * Destination clip for the Sonic 2 special-stage sprite table: the native H32 display.
 *
 * <p>SpecialStage selects 32-cell horizontal resolution with VDP register $8C08
 * (s2.asm:6564), so the hardware shows sprite pixels only in a 256-pixel raster.
 * Sprite pieces may straddle that edge: Obj5F's flying START pieces keep displaying
 * until their centre leaves 0..$100 ({@code loc_710A}, s2.asm:9637-9653), and the
 * pillarbox around the centred H32 region is not part of the display. SAT admission
 * keeps whole source columns of a straddling piece; this scissor supplies the
 * per-pixel raster edge for the replay that follows.</p>
 *
 * <p>Deferred and nested: {@link #begin()} intersects any scissor already active,
 * and {@link #end()} restores it, including when a later command fails.</p>
 */
public final class Sonic2SpecialStageRasterClip {
    private final int logicalWidth;
    private final int x;
    private final int[] previous = new int[4];
    private boolean enabled;
    private boolean armed;

    private final GLCommandable begin = (cameraX, cameraY, cameraWidth, cameraHeight) -> open();
    private final GLCommandable end = new GLCommandable() {
        @Override public void execute(int x, int y, int width, int height) { restore(); }
        @Override public void unwindAfterFailure(int x, int y, int width, int height) { restore(); }
    };

    /** Clips to {@code viewport.innerH32()} at the full native display height. */
    public Sonic2SpecialStageRasterClip(SpecialStageViewport viewport) {
        Objects.requireNonNull(viewport, "viewport");
        this.logicalWidth = viewport.logicalWidth();
        this.x = viewport.innerH32OriginX();
    }

    /** Command that opens the H32 raster scissor; register it before the SAT replay. */
    public GLCommandable begin() {
        return begin;
    }

    /** Command that restores the prior scissor; register it after the SAT replay. */
    public GLCommandable end() {
        return end;
    }

    /** Physical scissor box for a logical H32 region inside a physical draw viewport. */
    static int[] physicalBox(int[] actualViewport, int logicalWidth, int x) {
        int y = 0;
        int width = SpecialStageViewport.H32_WIDTH;
        int height = SpecialStageViewport.NATIVE_HEIGHT;
        double sx = actualViewport[2] / (double) logicalWidth;
        double sy = actualViewport[3] / (double) SpecialStageViewport.NATIVE_HEIGHT;
        int left = actualViewport[0] + (int) Math.floor(x * sx);
        int right = actualViewport[0] + (int) Math.ceil((x + width) * sx);
        int bottom = actualViewport[1] + (int) Math.floor((SpecialStageViewport.NATIVE_HEIGHT - y - height) * sy);
        int top = actualViewport[1] + (int) Math.ceil((SpecialStageViewport.NATIVE_HEIGHT - y) * sy);
        return new int[] {left, bottom, right, top};
    }

    private void open() {
        int[] actualViewport = new int[4];
        glGetIntegerv(GL_VIEWPORT, actualViewport);
        glGetIntegerv(GL_SCISSOR_BOX, previous);
        enabled = glIsEnabled(GL_SCISSOR_TEST);
        armed = true;
        int[] box = physicalBox(actualViewport, logicalWidth, x);
        int left = box[0], bottom = box[1], right = box[2], top = box[3];
        if (enabled) {
            left = Math.max(left, previous[0]); right = Math.min(right, previous[0] + previous[2]);
            bottom = Math.max(bottom, previous[1]); top = Math.min(top, previous[1] + previous[3]);
        }
        glScissor(left, bottom, Math.max(0, right - left), Math.max(0, top - bottom));
        glEnable(GL_SCISSOR_TEST);
    }

    private void restore() {
        if (!armed) return;
        glScissor(previous[0], previous[1], previous[2], previous[3]);
        if (enabled) glEnable(GL_SCISSOR_TEST); else glDisable(GL_SCISSOR_TEST);
        armed = false;
    }
}
