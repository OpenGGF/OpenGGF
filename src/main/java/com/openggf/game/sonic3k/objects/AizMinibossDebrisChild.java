package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * AIZ Miniboss (0x90) - Miniature background aircraft.
 * Six instances spawned at trigger, each flying right across the sky.
 *
 * ROM: loc_68DFA (init) → loc_68E7E (Obj_Wait) → loc_68E8C (MoveSprite2)
 * - Init sets $2E = x_vel (staggered wait: faster craft wait LONGER)
 * - After wait: MoveSprite2 applies horizontal velocity, then Child_Draw_Sprite2 tests parent $38 bit4
 * - Slower aircraft (x_vel=$100) appear first, fastest ($200) last
 */
public class AizMinibossDebrisChild extends AbstractObjectInstance
        implements RewindRecreatable {
    private AizMinibossCutsceneInstance parent;
    private boolean retirementPending;
    private boolean drawPublished;
    private int worldX;
    private int worldY;
    private int xFixed;
    private int yFixed;
    // xVel/mappingFrame are non-final so the rewind field capturer reapplies them
    // after a pure spawn-shell recreation.
    private int xVel;
    private int waitTimer;
    private boolean moving;
    private int mappingFrame;

    /** Legacy standalone helper; native cutscene admission supplies parent3 below. */
    public AizMinibossDebrisChild(int x, int y, int xVel, int mappingFrame) {
        this(null, x, y, xVel, mappingFrame);
    }

    public AizMinibossDebrisChild(AizMinibossCutsceneInstance parent, int x, int y, int xVel, int mappingFrame) {
        this(new ObjectSpawn(x, y, 0x90, 0, 0, false, 0));
        this.parent = parent;
        this.worldX = x;
        this.worldY = y;
        this.xFixed = x << 16;
        this.yFixed = y << 16;
        this.xVel = xVel;
        // ROM: move.w word_68E60(pc,d0.w),$2E(a0) — wait = x_vel value
        this.waitTimer = xVel;
        this.moving = false;
        this.mappingFrame = mappingFrame;
    }

    private AizMinibossDebrisChild(ObjectSpawn spawn) {
        super(spawn, "AIZMinibossDebris");
        worldX = spawn.x(); worldY = spawn.y();
        xFixed = worldX << 16; yFixed = worldY << 16;
    }

    @Override
    public AizMinibossDebrisChild recreateForRewind(RewindRecreateContext ctx) {
        // Pure shell; captured exact parent3 and native motion hydrate before dispatch.
        return new AizMinibossDebrisChild(ctx.spawn());
    }

    @Override public boolean isPersistent() {
        // Native Wait/Move has no range tail; Child_Draw_Sprite2 owns retirement.
        return parent != null;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        drawPublished = false;
        if (retirementPending) {
            com.openggf.level.objects.ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (!moving) {
            // ROM: loc_68E7E → Obj_Wait, decrements $2E each frame
            waitTimer--;
            if (waitTimer < 0) {
                moving = true;
            }
            return;
        }

        // ROM: loc_68E8C → MoveSprite2 (no gravity — horizontal flight only)
        xFixed += (xVel << 8);
        worldX = xFixed >> 16;

        if (parent != null) {
            // loc_68E8C moves first, then Child_Draw_Sprite2 observes $38 bit4.
            retirementPending = (parent.getCustomFlag(0x38) & 0x10) != 0;
        } else if (!isOnScreen(256)) {
            setDestroyed(true); // legacy independent helper has no native parent3
        }
        drawPublished = !retirementPending && !isDestroyed();
    }

    @Override
    public int getPriorityBucket() {
        // ROM: ObjDat3_6904A priority $380 / $80 = 7
        return 7;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawPublished || isDestroyed()) {
            return; // Wait has no Draw; bit4/GoDelete2 also returns before Draw
        }
        ObjectRenderManager rm = services().renderManager();
        if (rm == null) {
            return;
        }
        PatternSpriteRenderer renderer = rm.getRenderer(Sonic3kObjectArtKeys.AIZ_MINIBOSS_SMALL);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, worldX, worldY, false, false);
    }

    @Override
    public int getX() {
        return worldX;
    }

    @Override
    public int getY() {
        return worldY;
    }
}
