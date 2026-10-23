package com.openggf.game.sonic1.objects.bosses;

import com.openggf.data.RomByteReader;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreateObjectLinks;

import java.io.IOException;
import java.util.List;

/** The five independent Obj85 rows following the visible/controller Eggman SST. */
public final class FZBossVisualComponent extends AbstractObjectInstance implements RewindRecreatable {
    // REV01 BossFinal_ObjData / ObjData2. Runtime asset/animation bytes come from the active ROM.
    private static final int OBJECT_DATA = 0x1A524;
    private static final int OBJECT_FLAGS = 0x1A560;
    private static final int ANIMATION_EGGMAN = 0x18468;
    private static final int ANIMATION_DAMAGED = 0x1AB8A;

    // Existing structural-parent policy: one FZ encounter, recreated before these later slots.
    private Sonic1FZBossInstance parent;
    private int routine;
    private int x;
    private int y;
    private int priority;
    private int actWidth;
    private int animation;
    private int previousAnimation;
    private int nextFrame;
    private int animationTimer;
    private int mappingFrame;
    private boolean flip;
    private boolean renderedLastPass;
    private boolean displayedThisPass;
    private String artKey;

    FZBossVisualComponent(Sonic1FZBossInstance parent, int routine) {
        this(new ObjectSpawn(0, 0, 0x85, routine, 0, false, 0));
        this.parent = parent;
        loadRomRow();
    }

    /** Side-effect-free probe constructor for the generic rewind recreator. */
    public FZBossVisualComponent(ObjectSpawn spawn) {
        super(spawn, "FZ visual component");
        // Generic constructor probes have an empty subtype; real native rows
        // and rewind recreations always supply their routine in that byte.
        routine = spawn.subtype() == 0 ? 4 : spawn.subtype();
        int row = routine / 2 - 1;
        if (row < 1 || row > 5) throw new IllegalArgumentException("Not a native FZ visual routine: " + routine);
        x = row == 1 ? 0x25B0 : 0x26E0;
        y = row == 1 ? 0x0590 : 0x0596;
        priority = row == 1 ? 1 : 3;
        actWidth = row == 1 ? 18 : routine == 10 ? 32 : 0;
        artKey = switch (routine) {
            case 4 -> ObjectArtKeys.FZ_CYLINDER;
            case 6 -> ObjectArtKeys.FZ_LEGS;
            case 8 -> ObjectArtKeys.FZ_SEGG;
            default -> ObjectArtKeys.EGGMAN;
        };
    }

    private void loadRomRow() {
        int row = routine / 2 - 1;
        RomByteReader reader = romReader();
        if (reader != null) {
            x = reader.readU16BE(OBJECT_DATA + row * 10);
            y = reader.readU16BE(OBJECT_DATA + row * 10 + 2);
            animation = reader.readU8(OBJECT_FLAGS + row * 5 + 1);
            priority = reader.readU8(OBJECT_FLAGS + row * 5 + 2);
            actWidth = reader.readU8(OBJECT_FLAGS + row * 5 + 3);
        }
    }

    @Override
    public FZBossVisualComponent recreateForRewind(RewindRecreateContext ctx) {
        return RewindRecreateObjectLinks.nearestObjectUnbounded(ctx, Sonic1FZBossInstance.class, true)
                .map(owner -> {
                    // Generic recreation is outside ObjectConstructionContext. All
                    // ROM-derived metadata and animation fields follow from capture;
                    // construction here must not request ROM services or allocate SSTs.
                    FZBossVisualComponent restored = new FZBossVisualComponent(ctx.spawn());
                    restored.parent = owner;
                    return restored;
                }).orElse(null);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (isDestroyed() || parent == null) return;
        if ((routine == 8 || routine == 12) && parent.isDestroyed()) {
            ObjectLifetimeOps.destroyLatched(this); // These two routines test the parent's object ID.
            return;
        }
        switch (routine) {
            case 4 -> {
                mappingFrame = 11;
                if (player != null && (player.getCentreX() & 0xFFFF) >= x && !renderedLastPass) {
                    ObjectLifetimeOps.destroyLatched(this);
                    return;
                }
                displayedThisPass = true;
                return; // The panel neither follows the controller nor copies its status.
            }
            case 6 -> {
                // BossFinal_Legs advances only after the controller switches to Map_Eggman.
                if (parent.isInEscapeShip()) {
                    followParent();
                    if (animationTimer == 0) animationTimer = 20;
                    animationTimer = (byte) (animationTimer - 1);
                    if (animationTimer <= 0 && ++mappingFrame > 2) {
                        ObjectLifetimeOps.destroyLatched(this);
                        return;
                    }
                }
            }
            case 8 -> {
                if (!parent.isInEscapeShip()) {
                    mappingFrame = 10;
                } else if ((byte) parent.getState().hitCount > 0) {
                    animation = 6;
                    artKey = ObjectArtKeys.EGGMAN;
                    animate(ANIMATION_EGGMAN);
                    followParent();
                } else {
                    // FixBugs=0: the exploding cockpit alone can self-delete offscreen.
                    // FixBugs=1 removes this test and supplies nonzero culling extents.
                    if (!renderedLastPass) {
                        ObjectLifetimeOps.destroyLatched(this);
                        return;
                    }
                    parent.triggerBossDefeatedExplosion(vIntRunCount, x, y);
                    priority = 2;
                    animation = 0;
                    artKey = ObjectArtKeys.FZ_DAMAGED;
                    animate(ANIMATION_DAMAGED);
                    followParent();
                }
            }
            case 10 -> {
                mappingFrame = 0;
                if (parent.getState().routineSecondary == 12 && parent.isInEscapeShip()) {
                    ObjectLifetimeOps.destroyLatched(this);
                    return;
                }
            }
            case 12 -> {
                animation = 7;
                if ((byte) parent.getState().routineSecondary >= 12) {
                    if (parent.getState().xVel != 0) animation = 11;
                    animate(ANIMATION_EGGMAN);
                    followParent();
                }
                // Before phase12 native branches directly to Display, preserving frame0
                // and its fixed position despite assigning animation7.
            }
            default -> throw new IllegalStateException("Unknown FZ component routine " + routine);
        }
        flip = (parent.getState().renderFlags & 1) != 0;
        displayedThisPass = true;
    }

    private void followParent() {
        x = parent.getX();
        y = parent.getY();
    }

    @Override
    public void refreshPostCameraRenderState() {
        if (!displayedThisPass) return;
        displayedThisPass = false;
        var camera = services().camera();
        int sx = (short) (x - camera.getX());
        int sy = (short) (y - camera.getY());
        // ObjData2's height bytes are unused: these components never set custom-height.
        renderedLastPass = sx >= -actWidth && sx < camera.getWidth() + actWidth
                && sy >= -32 && sy < camera.getHeight() + 32;
    }

    private RomByteReader romReader() {
        try { return services().romReader(); }
        catch (IOException e) { throw new IllegalStateException("Cannot read FZ component ROM data", e); }
    }

    private void animate(int table) {
        RomByteReader reader = romReader();
        if (reader == null) return;
        if (animation != previousAnimation) {
            previousAnimation = animation;
            nextFrame = 0;
            animationTimer = 0;
        }
        animationTimer = (byte) (animationTimer - 1);
        if (animationTimer >= 0) return;
        int script = reader.readPointer16(table, animation);
        animationTimer = reader.readU8(script);
        int frame = reader.readU8(script + 1 + nextFrame);
        if (frame == 0xFF) {
            nextFrame = 0;
            frame = reader.readU8(script + 1);
        } else if (frame == 0xFE) {
            nextFrame = (nextFrame - reader.readU8(script + 2 + nextFrame)) & 0xFF;
            frame = reader.readU8(script + 1 + nextFrame);
        }
        mappingFrame = frame & 0x1F;
        nextFrame = (nextFrame + 1) & 0xFF;
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public ObjectSpawn getSpawn() { return buildSpawnAt(x, y); }
    @Override public int getPriorityBucket() { return priority; }
    @Override public int getOnScreenHalfWidth() { return actWidth; }
    @Override public boolean isPersistent() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || services().renderManager() == null) return;
        var renderer = services().renderManager().getRenderer(artKey);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(mappingFrame, x, y, flip, false);
    }
}
