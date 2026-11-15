package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * The Act 3 drain puzzle, object $36 ({@code DrainObject}, objects/r3/boss.asm:1288-1293): the subtype picks the routine, negative
 * the {@link Switch}, zero a {@link Light}, positive the {@link Block}. The switch is placed in the layout (subtype $FF); it
 * spawns the block at (X $380, Y $4F8) and, on each step on it, an indicator light at X $380 and Y $498, $4B0, $4C8. The third
 * step raises the block (animation 0, then the block throws the player up while its frame is 2); the fourth resets everything.
 * Block and lights are deleted as soon as the switch's slot stops holding object $36.
 */
public final class CczDrain {
    private CczDrain() { }

    /** DrainObject's dispatch on the subtype byte (objects/r3/boss.asm:1288-1293). */
    public static CczObject create(ObjectSpawn spawn) {
        int subtype = (byte) spawn.subtype();
        if (subtype < 0) return new Switch(spawn);
        if (subtype == 0) return new Light(spawn, null);
        return new Block(spawn, null);
    }

    /** Indicator-light Y positions by stage 0, 2, 4 (DrainSwitch_LightYPositions, objects/r3/drain_switch.asm:134-138). */
    private static final int[] LIGHT_Y = {0x498, 0x4B0, 0x4C8};

    /** The drain switch (objects/r3/drain_switch.asm:13-139): $20 square trigger zone around its centre, $80 frame cooldown. */
    @com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
    public static final class Switch extends CczObject implements ObjectVisualFacing.NonDirectional {
        private int cooldown;            // drain_switch.cooldown
        private int stage = -2;          // drain_switch.stage
        private boolean playerInside;    // drain_switch.player_inside
        // Links are typed fields (light_1 the newest), captured by object id for rewind; an array of links is not.
        private Block block;
        private Light light1;
        private Light light2;
        private Light light3;

        public Switch(ObjectSpawn spawn) {
            super(spawn, "Drain switch");
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (routine == 0) {
                if (CczStage.standDownFromDrain(this, services())) return;
                init();
            }
            main(player);
        }

        /** DrainSwitch_Init (:30-58); falls into DrainSwitch_Main. */
        private void init() {
            routine = 2;
            priority = 3;
            width = 0x10;
            yRadius = 0x10;
            tileWord = CczObjectArt.DRAIN_SWITCH_TILE;
            stage = -2;
            if ((spawn.subtype() & 0xFF) == 0xFF) {
                cooldown = 0x80;
                playerInside = true;
            }
            var objects = services().objectManager();
            if (objects == null) return;
            block = new Block(new ObjectSpawn(0x380, 0x4F8, 0x36, 1, 0, false, 0), this);
            objects.addDynamicObject(block);
        }

        /** DrainSwitch_Main (:60-72). */
        private void main(PlayableEntity player) {
            checkPlayer(player);
            if (cooldown == 0) return;
            cooldown = (cooldown - 1) & 0xFFFF;
            if (animator == null) animator = CczObjectArt.animator(provider(), "drainSwitchAnimations", 0);
            animate();
        }

        /** DrainSwitch_CheckPlayer (:74-110). */
        private void checkPlayer(PlayableEntity player) {
            if (!(player instanceof AbstractPlayableSprite sprite)) return;
            int dy = (y - sprite.getCentreY() + 0x10) & 0xFFFF;
            int dx = (x - sprite.getCentreX() + 0x10) & 0xFFFF;
            if (dy >= 0x20 || dx >= 0x20) {
                playerInside = false;                                      // .Outside: sf (a5)
                return;
            }
            if (playerInside) return;
            playerInside = true;
            if (animator == null) animator = CczObjectArt.animator(provider(), "drainSwitchAnimations", 0);
            if (animator != null) {                                        // move.w #$FF / #$1FF,obj.anim_id
                animator.anim = sprite.getYSpeed() < 0 ? 1 : 0;
                animator.restart();
            }
            int previous = cooldown;
            cooldown = 0x80;
            if (previous == 0) advanceStage();
        }

        /** DrainSwitch_AdvanceStage and _Reset (:112-163). */
        private void advanceStage() {
            if (stage == 4) {
                stage = -2;
                for (Light light : new Light[] {light1, light2, light3}) if (light != null) light.remove();
                if (block != null) block.lower();
                return;
            }
            stage += 2;
            light3 = light2;
            light2 = light1;
            var objects = services().objectManager();
            if (objects != null) {
                Light light = new Light(new ObjectSpawn(0x380, LIGHT_Y[stage / 2], 0x36, 0, 0, false, 0), this);
                light1 = light;
                objects.addDynamicObject(light);
            }
            if (stage == 4 && block != null) block.raise();
        }

        @Override protected boolean despawnsByCheckObjDespawn() { return true; }

        /**
         * The block and lights test the switch's slot every frame and delete themselves once it no longer holds $36
         * (drain_block.asm, drain_light.asm). When this switch leaves the object list their links are cleared here, so
         * no captured link outlives it; each still deletes itself on its own next frame, as native does.
         */
        @Override
        protected void onRemovedFromObjectManager() {
            if (block != null) block.drainSwitch = null;
            for (Light light : new Light[] {light1, light2, light3}) if (light != null) light.drainSwitch = null;
            block = null;
            light1 = light2 = light3 = null;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            drawSheet(CczObjectArt.DRAIN_SWITCH, mapFrame);
        }

        public int stage() { return stage; }
        public int cooldown() { return cooldown; }
    }

    /**
     * The drain block (objects/r3/drain_block.asm:5-86): a $10 by 8 platform that is top-solid only while its mapping frame
     * is 2, and throws a player landing on it up at -$400 with zero X speed (he leaves the ground and any push or object
     * state). The switch raises it (animation 0) on the third step and lowers it (animation 1) on the reset.
     */
    @com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
    public static final class Block extends CczObject implements ReevaluatingSolidObject,
            ObjectVisualFacing.NonDirectional, RewindRecreatable {
        /** The switch that allocated it ({@code obj.var_34}); a rewind restores it by object id. */
        private Switch drainSwitch;
        private boolean routineChanged;

        public Block(ObjectSpawn spawn, Switch drainSwitch) {
            super(spawn, "Drain block");
            this.drainSwitch = drainSwitch;
        }

        /** A shell; the generic capture restores its routine, animation and switch link afterwards. */
        @Override
        public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
            return new Block(ctx.spawn(), null);
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            switch (routine) {
                case 0 -> {                                                // DrainBlock_Init (:35-45), falls into Solid
                    if (CczStage.standDownFromDrain(this, services())) return;
                    routine = 2;
                    priority = 3;
                    tileWord = CczObjectArt.DRAIN_BLOCK_TILE;
                    width = 0x10;
                    yRadius = 8;
                    solid(player);
                }
                case 2, 10 -> solid(player);
                case 4 -> {                                                // DrainBlock_Raise
                    anim(0);
                    routine += 4;
                    animateBlock();
                }
                case 6 -> {                                                // DrainBlock_Lower
                    anim(1);
                    routine += 2;
                    animateBlock();
                }
                default -> animateBlock();
            }
            if (drainSwitch == null || drainSwitch.isDestroyed()) setDestroyed(true);
        }

        void raise() { routine = 4; }

        void lower() { routine = 6; }

        private void anim(int anim) {
            if (animator == null) animator = CczObjectArt.animator(provider(), "drainBlockAnimations", 0);
            if (animator != null) {
                animator.anim = anim;
                animator.restart();                                        // move.w #$FF / #$1FF,obj.anim_id
            }
        }

        private void animateBlock() {
            if (animator == null) animator = CczObjectArt.animator(provider(), "drainBlockAnimations", 0);
            animate();
        }

        /** DrainBlock_Solid (:47-65). */
        private void solid(PlayableEntity player) {
            if (mapFrame != 2) return;
            if (!solidObject(Entry.TOP) || !(player instanceof AbstractPlayableSprite sprite)) return;
            clearStoodOn();
            sprite.setAir(true);
            sprite.setOnObject(false);
            sprite.setPushing(false);
            sprite.setXSpeed((short) 0);
            sprite.setYSpeed((short) -0x400);
        }

        @Override public int solidWidth() { return width; }
        @Override public int solidYRadius() { return yRadius; }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            drawSheet(CczObjectArt.DRAIN_BLOCK, mapFrame);
        }
    }

    /** The indicator light (objects/r3/drain_light.asm:5-47): an 8 by 8 sprite that sits until the switch resets. */
    @com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
    public static final class Light extends CczObject implements ObjectVisualFacing.NonDirectional, RewindRecreatable {
        /** The switch that allocated it; a rewind restores it by object id. */
        private Switch drainSwitch;

        public Light(ObjectSpawn spawn, Switch drainSwitch) {
            super(spawn, "Drain light");
            this.drainSwitch = drainSwitch;
        }

        /** A shell; the generic capture restores its routine and switch link afterwards. */
        @Override
        public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
            return new Light(ctx.spawn(), null);
        }

        void remove() { routine = 4; }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            switch (routine) {
                case 0 -> {                                                // DrainLight_Init (:30-37)
                    if (CczStage.standDownFromDrain(this, services())) return;
                    routine = 2;
                    priority = 3;
                    width = 8;
                    yRadius = 8;
                    tileWord = CczObjectArt.DRAIN_LIGHT_TILE;
                }
                case 2 -> { }
                default -> {
                    setDestroyed(true);                                    // DrainLight_Delete
                    return;
                }
            }
            if (drainSwitch == null || drainSwitch.isDestroyed()) setDestroyed(true);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            drawSheet(CczObjectArt.DRAIN_LIGHT, mapFrame);
        }
    }
}
