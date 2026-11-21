package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.game.soniccd.player.CdSonicAnimation;
import com.openggf.game.soniccd.timetravel.CdTimeTravel;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.CameraRelativeDraw;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.PlayerControlOwners;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.physics.Direction;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/** The Palmtree Panic Act 1 Present encounter, indexed at $2F. */
@CameraRelativeDraw(CameraRelativeDraw.Use.ART_SELECTION)
public final class PpzAmyRose extends CdObject implements ObjectVisualFacing.Source, SpawnRewindRecreatable {
    private static final int GRAB_CUTOFF = (9 * 60 + 50) * 60;

    private enum Routine { INIT, CHASE, HOLD, FOLLOW, WAIT_LAND }

    private final PlayerControlOwners heldPlayers = new PlayerControlOwners();
    private Routine routine = Routine.INIT;
    private int xSubpixel;
    private int ySubpixel;
    private int xSpeed;
    private int ySpeed;
    private boolean facingLeft;
    private boolean waitingBesideSonic;
    private boolean mayHop;
    private boolean hopping;
    private int hops;
    private int landingTimer;
    private int heartTimer;
    private int previousAnimation;
    private int animationIndex;
    private int animationTimer;
    private int mapFrame;
    private int drawFlip;

    public PpzAmyRose(ObjectSpawn spawn) {
        super(spawn, "Amy Rose");
        drawFlip = spawn.renderFlags() & 3;
        facingLeft = (drawFlip & 1) != 0;
    }

    // ObjAmyRose (Level/Palmtree Panic/Objects/Amy Rose/Main.asm:7-33).
    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (timeAttackMode()) {
            writePalette(true);
            return;
        }
        if (!(player instanceof AbstractPlayableSprite sonic)) return;
        switch (routine) {
            case INIT -> init(sonic);
            case CHASE -> chase(sonic);
            case HOLD -> hold(sonic);
            case FOLLOW -> follow();
            case WAIT_LAND -> waitLand();
        }
        makeHeart();
        var camera = services().camera();
        if (camera != null && isCustomOutOfRange(camera.getX())) writePalette(true);
    }

    // ObjAmyRose_Init (Level/Palmtree Panic/Objects/Amy Rose/Main.asm:36-71).
    private void init(AbstractPlayableSprite sonic) {
        tileWord = PpzObjectArt.AMY_TILE;
        priority = 1;
        width = 0xC;
        yRadius = 0x10;
        writePalette(false);
        int distance = floorDistance();
        while (distance != 0) {
            y = (y + distance) & 0xFFFF;
            distance = floorDistance();
        }
        facePlayer(sonic);
        if (playerDistance(sonic) < 0x70) routine = Routine.CHASE;
        animate(5);
    }

    // ObjAmyRose_Main (Level/Palmtree Panic/Objects/Amy Rose/Main.asm:76-178).
    private void chase(AbstractPlayableSprite sonic) {
        facePlayer(sonic);
        if (hopping) {
            bump();
            return;
        }
        int distance = playerDistance(sonic);
        if (waitingBesideSonic ? distance < 0x20 : sonic.getXSpeed() == 0 && distance < 0xA) {
            waitingBesideSonic = true;
            stand();
            return;
        }
        waitingBesideSonic = false;
        xSpeed = Math.clamp(xSpeed + (facingLeft ? -0x10 : 0x10), -0x200, 0x200);
        if (!insideTravelLimit()) {
            bump();
            return;
        }
        int floor = floorDistance();
        if (floor < -7 || floor >= 7) {
            bump();
            return;
        }
        y = (y + floor) & 0xFFFF;
        moveX();
        checkGrab(sonic);
        animate(2);
    }

    private void stand() {
        xSpeed = 0;
        y = (y + floorDistance()) & 0xFFFF;
        animate(1);
    }

    // ObjAmyRose_Main (Level/Palmtree Panic/Objects/Amy Rose/Main.asm:168-219).
    private void bump() {
        xSpeed = 0;
        if (!mayHop) {
            stand();
            return;
        }
        if (!hopping) {
            if (hops >= 3) {
                landingTimer += 4;
                if (landingTimer >= 0x100) hops = 0;
                landingTimer &= 0xFF;
                animate(4);
                return;
            }
            ySpeed = -0x300;
            hopping = true;
        }
        moveYWithGravity();
        mapFrame = ySpeed < 0 ? 6 : 4;
        if (floorDistance() < 0) {
            ySpeed = 0;
            hopping = false;
            hops = (hops + 1) & 0xFF;
        }
    }

    private boolean insideTravelLimit() {
        return xSpeed < 0 ? x > ((spawn.x() - 0x130) & 0xFFFF)
                : x <= ((spawn.x() + 0x90) & 0xFFFF);
    }

    // ObjAmyRose_CheckGrabSonic (Level/Palmtree Panic/Objects/Amy Rose/Main.asm:393-481).
    private void checkGrab(AbstractPlayableSprite sonic) {
        if (!insideTravelLimit() || nearTimeOver() || sonic.isDebugMode()) return;
        int playerX = sonic.getCentreX() & 0xFFFF;
        int dx = sonic.getDirection() == Direction.LEFT ? x - playerX : playerX - x;
        int dy = (short) (sonic.getCentreY() - y + 8);
        if (dx < 0xC || dx >= 0x18 || dy < 0 || dy >= 0x10) return;
        if (sonic.getAir() || sonic.getRolling() || sonic.isHurt() || Math.abs(sonic.getXSpeed()) >= 0x680
                || sonic.hasShield() || CdTimeTravel.starsActive(services().gameModule())
                || sonic.getInvincibleFrames() > 0) {
            routine = Routine.FOLLOW;
            return;
        }
        sonic.setRollingFlagPreserveRadii(false);
        mayHop = true;
        ySpeed = 0;
        xSpeed = 0;
        mapFrame = 7;
        routine = Routine.HOLD;
        services().playMusic(0x7C); // SCMD_GIGGLESFX (_Include/System.inc:268).
    }

    // ObjAmyRose_HoldSonic (Level/Palmtree Panic/Objects/Amy Rose/Main.asm:224-262).
    private void hold(AbstractPlayableSprite sonic) {
        heldPlayers.acquire(sonic).setObjectControlled(true);
        sonic.setAnimationId(CdSonicAnimation.IDLE);
        slowSonic(sonic);
        facePlayer(sonic);
        x = (sonic.getCentreX() + (sonic.getDirection() == Direction.LEFT ? 0xC : -0xC)) & 0xFFFF;
        y = sonic.getCentreY() & 0xFFFF;
        if (sonic.isJumpJustPressed()) {
            releaseWithJump(sonic);
            routine = Routine.FOLLOW;
        } else if (nearTimeOver()) {
            releaseWithJump(sonic);
            routine = Routine.CHASE;
        } else {
            animate(3);
        }
    }

    // ObjAmyRose_SlowSonic (Level/Palmtree Panic/Objects/Amy Rose/Main.asm:316-341).
    private void slowSonic(AbstractPlayableSprite sonic) {
        if (xSpeed == 0) return; // The routine tests Amy's velocity, which the grab clears.
        int speed = sonic.getXSpeed();
        NativePositionOps.addXPos16_16(sonic, speed << 8);
        int floor = ObjectTerrainUtils.checkFloorDist(services().levelManager(),
                sonic.getCentreX(), sonic.getCentreY() + sonic.getYRadius()).distance();
        NativePositionOps.addYPosPreserveSubpixel(sonic, floor);
        sonic.setXSpeed((short) (speed < 0 ? Math.min(0, speed + 0x40) : Math.max(0, speed - 0x40)));
    }

    // Player_Jump2 (Level/Palmtree Panic/Objects/Amy Rose/Main.asm:346-388).
    private void releaseWithJump(AbstractPlayableSprite sonic) {
        heldPlayers.relinquish(sonic).setObjectControlled(false);
        int angle = (sonic.getAngle() - 0x40) & 0xFF;
        sonic.setXSpeed((short) (sonic.getXSpeed() + ((TrigLookupTable.cosHex(angle) * 0x680) >> 8)));
        sonic.setYSpeed((short) (sonic.getYSpeed() + ((TrigLookupTable.sinHex(angle) * 0x680) >> 8)));
        sonic.setAir(true);
        sonic.setPushing(false);
        sonic.setJumping(true);
        sonic.setStickToConvex(false);
        sonic.applyCustomRadii(9, 0x13);
        if (sonic.getRolling()) {
            sonic.setRollingJump(true);
        } else {
            sonic.applyCustomRadii(7, 0xE);
            NativePositionOps.addYPosPreserveSubpixel(sonic, 5);
            sonic.setRollingFlagPreserveRadii(true);
            sonic.setAnimationId(CdSonicAnimation.ROLL);
        }
    }

    @Override
    public void releasePlayersForVisualDirection() {
        for (var player : heldPlayers.players()) player.setObjectControlled(false);
        heldPlayers.clear();
    }

    @Override
    public void onUnload() {
        releasePlayersForVisualDirection();
        super.onUnload();
    }

    // ObjAmyRose_FollowSonic (Level/Palmtree Panic/Objects/Amy Rose/Main.asm:267-308) falls into WaitLand.
    private void follow() {
        mapFrame = 6;
        xSpeed = Math.abs((short) (x - spawn.x())) >= 0x80 ? 0 : (facingLeft ? 0x80 : -0x80);
        ySpeed = -0x300;
        routine = Routine.WAIT_LAND;
        waitLand();
    }

    private void waitLand() {
        moveX();
        moveYWithGravity();
        if (ySpeed >= 0) mapFrame = 7;
        if (floorDistance() >= 0) return;
        xSpeed = 0;
        ySpeed = 0;
        landingTimer += 0x10;
        if (landingTimer >= 0x100) routine = Routine.CHASE;
        landingTimer &= 0xFF;
    }

    private boolean nearTimeOver() {
        var state = services().levelGamestate();
        return state != null && state.getTimerFrames() >= GRAB_CUTOFF;
    }

    private int playerDistance(AbstractPlayableSprite sonic) {
        return Math.abs((short) (sonic.getCentreX() - x));
    }

    // ObjAmyRose_SetFacing (Level/Palmtree Panic/Objects/Amy Rose/Main.asm:620-645).
    private void facePlayer(AbstractPlayableSprite sonic) {
        facingLeft = x >= (sonic.getCentreX() & 0xFFFF);
        drawFlip = (drawFlip & 2) | (facingLeft ? 1 : 0);
    }

    private int floorDistance() {
        return ObjectTerrainUtils.checkFloorDist(services().levelManager(), x, y + yRadius).distance();
    }

    // ObjectMove (Level/Palmtree Panic/Objects/Amy Rose/Main.asm:514-535).
    private void moveX() {
        xSubpixel += xSpeed << 8;
        x = (x + (xSubpixel >> 16)) & 0xFFFF;
        xSubpixel &= 0xFFFF;
    }

    private void moveYWithGravity() {
        ySubpixel += ySpeed << 8;
        y = (y + (ySubpixel >> 16)) & 0xFFFF;
        ySubpixel &= 0xFFFF;
        ySpeed = (short) (ySpeed + 0x40);
    }

    // AnimateObjSimple (Level/Palmtree Panic/Objects/Amy Rose/Main.asm:541-580).
    private void animate(int animation) {
        var art = artProvider();
        if (art == null) return;
        int table = PpzObjectCatalogue.entry(art.payload()).amyAnimations();
        if (table < 0) return;
        if (animation != previousAnimation) {
            previousAnimation = animation;
            animationIndex = 0;
            animationTimer = 0;
        }
        animationTimer = (byte) (animationTimer - 1);
        if (animationTimer >= 0) return;
        var image = art.image();
        int script = table + (short) image.u16(table + animation * 2);
        int frame = image.u8(script + animationIndex);
        if (frame >= 0x80) {
            animationIndex = 0;
            frame = image.u8(script);
        }
        mapFrame = frame & 0x1F;
        int statusFlip = (spawn.renderFlags() & 2) | (facingLeft ? 1 : 0);
        drawFlip = ((frame >> 5) ^ statusFlip) & 3;
        animationTimer = image.u8(script + animationIndex + 1);
        animationIndex += 2;
    }

    // ObjAmyRose_MakeHearts (Level/Palmtree Panic/Objects/Amy Rose/Main.asm:584-615).
    private void makeHeart() {
        boolean holding = routine == Routine.HOLD;
        heartTimer += holding ? 0x10 : 6;
        if (heartTimer < 0x100) return;
        heartTimer &= 0xFF;
        int dx = facingLeft ? -0xA : 8;
        if (holding) dx = -dx;
        findObjSlot(new Heart(new ObjectSpawn((x + dx) & 0xFFFF, (y - 0xC) & 0xFFFF,
                0x30, 0, 0, false, 0)));
    }

    // ObjAmyRose_ResetPal (Level/Palmtree Panic/Objects/Amy Rose/Main.asm:655-661).
    private void writePalette(boolean restore) {
        var art = artProvider();
        if (art == null) return;
        var entry = PpzObjectCatalogue.entry(art.payload());
        int source = restore ? entry.amyRestorePalette() : entry.amyPalette();
        if (source < 0) return;
        byte[] palette = new byte[32];
        for (int i = 0; i < palette.length; i++) palette[i] = (byte) art.image().u8(source + i);
        services().updatePalette(1, palette);
    }

    // DespawnObjTimeWarp (Level/Object Spawner.asm:442-445).
    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        if (timeAttackMode()) return false;
        var time = CdTimeTravel.of(services().gameModule());
        return (time != null && time.warping()) || super.isCustomOutOfRange(cameraX);
    }

    @Override protected boolean calledDrawObjectThisFrame() { return !timeAttackMode(); }
    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected boolean drawsBeforeDespawnCheck() { return true; }

    @Override
    public ObjectVisualFacing.Basis nativeFacing() {
        return (drawFlip & 1) != 0 ? ObjectVisualFacing.Basis.LEFT : ObjectVisualFacing.Basis.RIGHT;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (tileWord == 0 || timeAttackMode()) return;
        String key = PpzObjectArt.key(artProvider(), PpzObjectArt.AMY, tileWord, services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, (drawFlip & 1) != 0, (drawFlip & 2) != 0);
    }

    @CameraRelativeDraw(CameraRelativeDraw.Use.ART_SELECTION)
    static final class Heart extends CdObject implements ObjectVisualFacing.NonDirectional, SpawnRewindRecreatable {
        private int age;
        private int xSubpixel;
        private int ySubpixel;

        Heart(ObjectSpawn spawn) {
            super(spawn, "Amy heart");
        }

        // ObjAmyHeart_Init and ObjAmyHeart_Main (Level/Palmtree Panic/Objects/Amy Rose/Main.asm:684-731).
        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            tileWord = PpzObjectArt.HEART_TILE;
            priority = 3;
            if (age < 0x6E) {
                xSubpixel += (TrigLookupTable.sinHex((age * 3) & 0xFF) >> 2) << 8;
                x = (x + (xSubpixel >> 16)) & 0xFFFF;
                xSubpixel &= 0xFFFF;
                ySubpixel -= 0x60 << 8;
                y = (y + (ySubpixel >> 16)) & 0xFFFF;
                ySubpixel &= 0xFFFF;
            }
            if (++age == 0x78) setDestroyed(true);
        }

        @Override protected boolean despawnsByCheckObjDespawn() { return true; }
        @Override protected boolean drawsBeforeDespawnCheck() { return true; }

        @Override
        public boolean isCustomOutOfRange(int cameraX) {
            var time = CdTimeTravel.of(services().gameModule());
            return (time != null && time.warping()) || super.isCustomOutOfRange(cameraX);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (tileWord == 0) return;
            String key = PpzObjectArt.key(artProvider(), PpzObjectArt.HEART, tileWord, services().camera().getX());
            int frame = age < 0x14 ? 8 : age < 0x6E ? 9 : 0xA;
            if (key != null) drawFrame(key, frame, false, false);
        }
    }
}
