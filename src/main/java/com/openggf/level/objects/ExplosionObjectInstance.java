package com.openggf.level.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.game.PlayableEntity;

import java.util.List;
import java.util.logging.Logger;

public class ExplosionObjectInstance extends AbstractObjectInstance implements SpawnServicesRewindRecreatable {
    private static final Logger LOGGER = Logger.getLogger(ExplosionObjectInstance.class.getName());
    private final ObjectRenderManager renderManager;
    private final DestructionEffects.AnimalFactory animalFactory;
    private final DestructionEffects.PointsFactory pointsFactory;
    private final int pointsValue;
    private final boolean pointsAllocatedBeforeAnimal;
    private int pendingSfxId = -1;
    private int animFrame = 0;
    private boolean spawnedDestructionChildren;

    /**
     * ROM-faithful animation timing for Obj27 (badnik-death explosion).
     * <p>
     * The ROM animate routine is identical across S1/S2/S3K:
     * <pre>
     *   subq.b  #1,anim_frame_duration(a0)   ; predecrement
     *   bpl.s   +                            ; still >= 0 -> just display
     *   move.b  #7,anim_frame_duration(a0)   ; reload
     *   addq.b  #1,mapping_frame(a0)         ; advance frame
     *   cmpi.b  #5,mapping_frame(a0)         ; reached frame 5?
     *   beq.w   DeleteObject                 ; if so, delete (not displayed)
     * + jmpto   DisplaySprite
     * </pre>
     * docs/s2disasm/s2.asm:46678-46686, docs/s1disasm/_incObj/24, 27 &amp; 3F
     * Explosions.asm (ExItem_Animate), docs/skdisasm/sonic3k.asm:42199-42208.
     * <p>
     * The ROM init routine falls through into the animate routine on the same
     * frame the object is allocated, so the first {@link #update} both seeds and
     * applies the first predecrement — matching the engine's same-frame child
     * execution in {@code ObjectManager}. Only the initial duration differs per
     * game (S1 = 7, S2/S3K = 3); see {@link com.openggf.game.GameModule#explosionInitialAnimDuration()}.
     */
    private static final int RELOAD_DURATION = 7;
    private static final int FINAL_MAPPING_FRAME = 5;
    private int animFrameDuration = -1; // resolved lazily from the game module on first update

    public ExplosionObjectInstance(int id, int x, int y, ObjectRenderManager renderManager) {
        this(id, x, y, renderManager, -1);
    }

    public ExplosionObjectInstance(ObjectSpawn spawn, ObjectServices services) {
        this(spawn.objectId(), spawn.x(), spawn.y(),
                services != null ? services.renderManager() : null, -1);
    }

    /**
     * Creates an explosion with an optional sound effect.
     * ROM: Explosion objects play their SFX in the init routine (e.g. sfx_Break, sfx_Bomb).
     *
     * @param sfxId SFX ID to play on creation, or -1 for no sound
     */
    public ExplosionObjectInstance(int id, int x, int y, ObjectRenderManager renderManager, int sfxId) {
        super(new ObjectSpawn(x, y, id, 0, 0, false, 0), "Explosion");
        this.renderManager = renderManager;
        this.animalFactory = null;
        this.pointsFactory = null;
        this.pointsValue = 0;
        this.pointsAllocatedBeforeAnimal = false;
        this.pendingSfxId = sfxId;
        playPendingSfxIfPossible();
    }

    public ExplosionObjectInstance(int id, int x, int y, ObjectRenderManager renderManager,
            DestructionEffects.AnimalFactory animalFactory,
            DestructionEffects.PointsFactory pointsFactory,
            int pointsValue,
            boolean pointsAllocatedBeforeAnimal) {
        super(new ObjectSpawn(x, y, id, 0, 0, false, 0), "Explosion");
        this.renderManager = renderManager;
        this.animalFactory = animalFactory;
        this.pointsFactory = pointsFactory;
        this.pointsValue = pointsValue;
        this.pointsAllocatedBeforeAnimal = pointsAllocatedBeforeAnimal;
    }

    @Override
    public void setServices(ObjectServices services) {
        super.setServices(services);
        playPendingSfxIfPossible();
    }

    private void playPendingSfxIfPossible() {
        if (pendingSfxId < 0) {
            return;
        }
        try {
            ObjectServices ctx = tryServices();
            if (ctx != null) {
                ctx.playSfx(pendingSfxId);
                pendingSfxId = -1;
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to play explosion sound: " + e.getMessage());
        }
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        spawnDestructionChildrenOnce();
        if (animFrameDuration < 0) {
            animFrameDuration = resolveInitialAnimDuration();
        }
        // ROM: subq.b #1,anim_frame_duration / bpl.s + (still showing this frame)
        animFrameDuration--;
        if (animFrameDuration >= 0) {
            return;
        }
        // ROM: reload, advance mapping_frame, delete when it reaches frame 5.
        animFrameDuration = RELOAD_DURATION;
        animFrame++;
        if (animFrame >= FINAL_MAPPING_FRAME) {
            ObjectLifetimeOps.expireDynamic(this);
        }
    }

    private void spawnDestructionChildrenOnce() {
        if (spawnedDestructionChildren || (animalFactory == null && pointsFactory == null)) {
            return;
        }
        spawnedDestructionChildren = true;
        ObjectServices svc = tryServices();
        if (svc == null) {
            return;
        }
        ObjectManager objectManager = svc.objectManager();
        if (objectManager == null) {
            return;
        }

        int x = spawn.x();
        int y = spawn.y();
        if (pointsAllocatedBeforeAnimal && pointsFactory != null) {
            objectManager.createDynamicObject(() -> pointsFactory.create(
                    new ObjectSpawn(x, y, 0x29, 0, 0, false, 0), svc, pointsValue));
        }
        // S3K Obj_Explosion routine 0 allocates Obj_Animal before initializing
        // its animation/SFX (docs/skdisasm/sonic3k.asm:42157-42180).
        if (animalFactory != null) {
            objectManager.createDynamicObject(() -> animalFactory.create(
                    new ObjectSpawn(x, y, 0x28, 0, 0, false, 0), svc));
        }
        if (!pointsAllocatedBeforeAnimal && pointsFactory != null) {
            objectManager.createDynamicObject(() -> pointsFactory.create(
                    new ObjectSpawn(x, y, 0x29, 0, 0, false, 0), svc, pointsValue));
        }
    }

    /**
     * Per-game initial {@code anim_frame_duration} (ROM {@code move.b #N}).
     * Resolved from the active {@link com.openggf.game.GameModule} so the shared
     * class stays game-agnostic; falls back to the S2/S3K value (3) when the
     * module is unavailable (e.g. unit tests with no module registered).
     */
    private int resolveInitialAnimDuration() {
        try {
            ObjectServices ctx = tryServices();
            if (ctx != null && ctx.gameModule() != null) {
                return ctx.gameModule().explosionInitialAnimDuration();
            }
        } catch (Exception ignored) {
            // fall through to default
        }
        return 3;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || animFrame >= FINAL_MAPPING_FRAME)
            return;
        if (renderManager == null || renderManager.getExplosionRenderer() == null) {
            return;
        }
        renderManager.getExplosionRenderer().drawFrameIndex(animFrame, spawn.x(), spawn.y(), false, false);
    }
}
