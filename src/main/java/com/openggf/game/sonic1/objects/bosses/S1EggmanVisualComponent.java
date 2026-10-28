package com.openggf.game.sonic1.objects.bosses;

import static com.openggf.game.sonic1.Sonic1RomProfile.resolve;

import com.openggf.data.RomByteReader;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreateObjectLinks;

import java.io.IOException;
import java.util.List;

/** A native face, flame or pipe SST, dispatched independently of the ship. */
public final class S1EggmanVisualComponent extends AbstractObjectInstance implements RewindRecreatable {
    static final int FACE = 1;
    static final int FLAME = 2;
    static final int ACCESSORY = 3;
    // REV01 Ani_Eggman: table at $18468; $18484 is normal-face-1's script.
    static final int ANIMATION_TABLE = 0x18468;

    private AbstractS1EggmanBossInstance shipOwner;
    private int component;
    private int x;
    private int y;
    private boolean flip;
    private boolean renderedLastPass;
    private boolean displayedThisPass;
    private int animation;
    private int previousAnimation;
    private int nextFrame;
    private int animationTimer;
    private int mappingFrame;

    S1EggmanVisualComponent(AbstractS1EggmanBossInstance ship, int component) {
        this(new ObjectSpawn(ship.getX(), ship.getY(), ship.getSpawn().objectId(), component,
                0, false, 0));
        shipOwner = ship;
    }

    /** Side-effect-free probe constructor used by the generic rewind recreator. */
    public S1EggmanVisualComponent(ObjectSpawn spawn) {
        super(spawn, "Eggman component");
        component = spawn.subtype();
        x = spawn.x();
        y = spawn.y();
        animation = component == FACE ? Sonic1BossAnimations.ANIM_FACE_NORMAL_1
                : Sonic1BossAnimations.ANIM_BLANK;
    }

    @Override
    public S1EggmanVisualComponent recreateForRewind(RewindRecreateContext ctx) {
        // The compact shipOwner reference is restored by identity after construction.
        return RewindRecreateObjectLinks.nearestObjectUnbounded(ctx,
                        AbstractS1EggmanBossInstance.class, true)
                .map(ship -> new S1EggmanVisualComponent(ship, ctx.spawn().subtype()))
                .orElse(null);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (shipOwner == null || isDestroyed()) {
            setDestroyed(true);
            return;
        }
        boolean parentIdTest = shipOwner instanceof Sonic1LZBossInstance
                || (component == FACE && shipOwner instanceof Sonic1SYZBossInstance);
        if (parentIdTest && shipOwner.isDestroyed()) {
            setDestroyed(true);
            return;
        }
        int phase = shipOwner.getState().routineSecondary;
        boolean escape = phase == shipOwner.getNativeEscapeRoutine();
        if (component == FLAME && shipOwner instanceof Sonic1MZBossInstance) {
            escape = (byte) phase >= 8;
        }
        // SYZ face has only an ID test; the other parts inspect their OWN previous
        // BuildSprites flag, before copying the ship's current coordinates.
        if (escape && !(component == FACE && shipOwner instanceof Sonic1SYZBossInstance)
                && !renderedLastPass) {
            setDestroyed(true);
            return;
        }
        if (component == ACCESSORY) {
            mappingFrame = shipOwner.getAccessoryMappingFrame();
        } else {
            animation = shipOwner.selectComponentAnimation(component, player);
            animate();
        }
        x = shipOwner.getX();
        y = shipOwner.getY();
        flip = (shipOwner.getState().renderFlags & 1) != 0;
        displayedThisPass = true;
    }

    @Override
    public void refreshPostCameraRenderState() {
        if (!displayedThisPass) return;
        displayedThisPass = false;
        // BuildSpr's obActWid=$20 and assumed height=$20. Blank mappings still
        // set bit 7. Use the supported viewport for the engine's extended view.
        var camera = services().camera();
        int sx = (short) (x - camera.getX());
        int sy = (short) (y - camera.getY());
        renderedLastPass = sx >= -32 && sx < camera.getWidth() + 32
                && sy >= -32 && sy < camera.getHeight() + 32;
    }

    private void animate() {
        RomByteReader reader;
        try {
            reader = services().romReader();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read Ani_Eggman from the active ROM", e);
        }
        if (reader == null) {
            return; // ROM-free logic harness: no replacement asset bytes.
        }
        if (animation != previousAnimation) {
            previousAnimation = animation;
            nextFrame = 0;
            animationTimer = 0;
        }
        animationTimer = (byte) (animationTimer - 1);
        if (animationTimer >= 0) {
            return;
        }
        int script = reader.readPointer16(resolve(reader, ANIMATION_TABLE), animation);
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

    int getMappingFrame() { return mappingFrame; }
    int getAnimation() { return animation; }
    int getComponent() { return component; }
    AbstractS1EggmanBossInstance getShipOwner() { return shipOwner; }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public ObjectSpawn getSpawn() { return buildSpawnAt(x, y); }
    @Override public boolean isPersistent() { return true; }
    @Override public int getPriorityBucket() { return component == ACCESSORY || shipOwner == null ? 3 : shipOwner.getPriorityBucket(); }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        var manager = services().renderManager();
        if (manager == null) {
            return;
        }
        var renderer = manager.getRenderer(component == ACCESSORY
                ? ObjectArtKeys.BOSS_WEAPONS : ObjectArtKeys.EGGMAN);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, x, y, flip, false);
        }
    }
}
