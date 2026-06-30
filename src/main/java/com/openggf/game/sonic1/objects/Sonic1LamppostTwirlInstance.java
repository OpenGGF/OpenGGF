package com.openggf.game.sonic1.objects;

import com.openggf.game.rewind.RewindTransient;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.PlayableEntity;

import java.util.List;

/**
 * Sonic 1 Lamppost twirl sparkle - the orbiting red ball after lamppost activation.
 * <p>
 * From docs/s1disasm/_incObj/79 Lamppost.asm (Lamp_Twirl, routine 6):
 * <ul>
 *   <li>Timer starts at $20, decrements each frame</li>
 *   <li>Angle starts at 0, decrements by $10 each frame</li>
 *   <li>Position: origX + cos(angle-$40)*$C00>>16, origY + sin(angle-$40)*$C00>>16</li>
 *   <li>Uses mapping frame 2 (.redballonly)</li>
 *   <li>Timer goes from $20 to -1 with bpl check, giving 33 frames of visible motion</li>
 * </ul>
 */
public class Sonic1LamppostTwirlInstance extends AbstractObjectInstance implements RewindRecreatable {

    // From disassembly: move.w #$20,lamp_time(a1)
    // Timer counts $20 → 0 (positive, bpl branches), then 0 → -1 (negative, falls through
    // to set routine 4 but still computes motion) = 33 frames total
    private static final int INITIAL_LIFETIME = 0x21; // 33 frames of motion

    // From disassembly: subi.b #$10,obAngle(a0)
    private static final int ANGLE_DECREMENT = 0x10;

    // From disassembly: muls.w #$C00,d1 / muls.w #$C00,d0
    // After swap (>>16), effective radius = $C00 * sin/cos(256-scale) / 65536 = 12 pixels
    private static final int SWING_RADIUS = 0x0C00;

    // Mapping frame for red ball only
    private static final int TWIRL_FRAME = 2;
    @RewindTransient(reason = "Structural parent link; relinked to the nearest live "
            + "S1 lamppost on rewind recreate. Scalar orbit state is reapplied by "
            + "the generic field capturer.")
    private final Sonic1LamppostObjectInstance parent;
    private int centerX;
    private int centerY;
    private int lifetime;
    private int angle;
    private int currentX;
    private int currentY;
    private boolean finished;

    public Sonic1LamppostTwirlInstance(Sonic1LamppostObjectInstance parent) {
        super(createDummySpawn(parent), "LamppostTwirl");
        this.parent = parent;
        this.centerX = parent.getCenterX();
        // From disassembly: subi.w #$18,lamp_origY(a1)
        this.centerY = parent.getCenterY() - Sonic1LamppostObjectInstance.TWIRL_Y_OFFSET;
        this.lifetime = INITIAL_LIFETIME;
        this.angle = 0; // obAngle defaults to 0 in freshly allocated object slot
        this.currentX = centerX;
        this.currentY = centerY;
    }

    private static ObjectSpawn createDummySpawn(Sonic1LamppostObjectInstance parent) {
        return new ObjectSpawn(parent.getCenterX(), parent.getCenterY(), 0x79, 0, 0, false, 0);
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        if (ctx == null || ctx.spawn() == null || ctx.objectServices() == null) {
            return null;
        }
        Sonic1LamppostObjectInstance liveParent =
                findNearestLiveParentForRewind(ctx.objectServices().objectManager(), ctx.spawn());
        return liveParent == null ? null : new Sonic1LamppostTwirlInstance(liveParent);
    }

    private static Sonic1LamppostObjectInstance findNearestLiveParentForRewind(
            ObjectManager objectManager,
            ObjectSpawn spawn) {
        if (objectManager == null || spawn == null) {
            return null;
        }
        Sonic1LamppostObjectInstance best = null;
        long bestDistance = Long.MAX_VALUE;
        for (ObjectInstance instance : objectManager.getActiveObjects()) {
            if (!(instance instanceof Sonic1LamppostObjectInstance parent) || parent.isDestroyed()) {
                continue;
            }
            long dx = parent.getCenterX() - spawn.x();
            long dy = parent.getCenterY() - spawn.y();
            long distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = parent;
            }
        }
        return best;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (finished) {
            return;
        }

        lifetime--;
        if (lifetime < 0) {
            // docs/s1disasm/s1disasm/_incObj/79 Lamppost.asm:116-134:
            // Lamp_Twirl switches the child to Lamp_Finish, then still runs
            // the final CalcSine position update. Lamp_Finish only returns;
            // the child is not deleted here and keeps occupying its object slot.
            finished = true;
            parent.onTwirlComplete();
        }

        // From disassembly:
        // move.b obAngle(a0),d0        ; load current angle
        // subi.b #$10,obAngle(a0)      ; decrement for next frame
        // subi.b #$40,d0               ; offset before CalcSine
        int calcAngle = (angle - 0x40) & 0xFF;
        angle = (angle - ANGLE_DECREMENT) & 0xFF;

        // CalcSine: d0 = sin(angle), d1 = cos(angle)
        // 256-step angle table, convert to radians
        double radians = calcAngle * Math.PI * 2 / 256.0;
        double sinVal = Math.sin(radians);
        double cosVal = Math.cos(radians);

        // muls.w #$C00,d1; swap d1 → cos * $C00 / 65536 = cos * 12
        // muls.w #$C00,d0; swap d0 → sin * $C00 / 65536 = sin * 12
        int xOffset = (int) (cosVal * SWING_RADIUS) >> 8;
        int yOffset = (int) (sinVal * SWING_RADIUS) >> 8;

        currentX = centerX + xOffset;
        currentY = centerY + yOffset;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getCheckpointRenderer();
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(TWIRL_FRAME, currentX, currentY, false, false);
    }

    @Override
    public int getPriorityBucket() {
        // From disassembly: move.b #4,obPriority(a1)
        return RenderPriority.clamp(4);
    }

    @Override
    public String traceDebugDetails() {
        return String.format("twirl time=%d angle=%02X finished=%s", lifetime, angle & 0xFF, finished);
    }
}
