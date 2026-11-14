package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.sprites.NativePositionOps;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;

import java.util.List;

/**
 * Pressure elevator, object $49 in R42C and R42D ({@code PressureElevatorObject}, objects/r4/pressure_elevator.asm:3-125): a top-solid
 * $40 wide platform (tile $4310, priority 3) that waits at its start height until the player is more than
 * $E0 pixels below it, then drops to $E0 above the player (routine 2), accelerates down by $400 (16.16) a
 * frame to 1 pixel a frame (routine 6), and when the player lands pushes the pair 2 and 4 pixels down, plays
 * {@code FM $BF} and rises (routine A, 2 pixels a frame) back to its start, dropping again if the player
 * steps off. It ends in CheckObjectDespawn (the rise ends in a draw, a 2 pixel move and the despawn).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjPressureElevator extends TtzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    private int routine;
    private int homeY;
    private int yFixed;
    private int speed;        // oVar2C (16.16)
    private int ySpeed;       // oYVel
    private int mapFrame;

    public ObjPressureElevator(ObjectSpawn spawn) {
        super(spawn, "PressureElevator");
        yFixed = y << 16;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override public int solidYVelocity() { return ySpeed; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        var p = mainPlayer();
        switch (routine) {
            case 0 -> {
                routine = 2;
                priority = 3;
                width = 0x20;
                tileWord = TtzObjectArt.PRESSURE_TILE;
                homeY = y;
                yRadius = 0x21;
            }
            case 2 -> {
                if (p == null) return;
                int d0 = p.getCentreY() - 0xE0;
                if (d0 > homeY) {                                   // cmp.w obj.var_2a(a0),d0; ble
                    routine = 4;
                    y = d0;
                    yFixed = y << 16;
                }
            }
            case 4, 6 -> descend(p);
            case 10 -> rise(p);
            case 8 -> {                                             // Routine8 falls into RoutineA
                routine = 10;
                mapFrame |= 1;
                yRadius = 0x1F;
                ySpeed = -0x200;
                rise(p);
            }
            default -> { }
        }
    }

    /** Routines 4 and 6 (objects/r4/pressure_elevator.asm:48-96). */
    private void descend(PlayableEntity p) {
        if (routine == 4) {
            routine = 6;
            yRadius = 0x21;
            mapFrame &= ~1;
            speed = 0;
            ySpeed = 0;
        }
        yFixed += speed;
        y = yFixed >> 16;
        speed += 0x400;
        if (speed > 0x10000) speed = 0x10000;
        if (!renderFlagOnScreen() && p != null && Math.abs(y - p.getCentreY()) > 0xE0) {
            routine = 2;                                            // loc_20F8B0: back to the wait
            return;
        }
        if (solidObject(Entry.TOP) && p != null) {
            routine = 8;
            ObjectManagerInternalAccess.releaseFromReevaluatingSolid(services().objectManager(), p, this);
            yRadius = 0x1F;
            y += 2;
            yFixed = y << 16 | (yFixed & 0xFFFF);
            NativePositionOps.writeYPosPreserveSubpixel(p, p.getCentreY() + 4);
            services().playSfx(0xBF);
        }
    }

    /** PressureElevatorObject_0_RoutineA (objects/r4/pressure_elevator.asm:104-125). */
    private void rise(PlayableEntity p) {
        if (y <= homeY) {
            routine = 2;
        } else if (!solidObject(Entry.TOP)) {
            routine = 4;
            y -= 2;
            yFixed = y << 16 | (yFixed & 0xFFFF);
            if (p != null) NativePositionOps.writeYPosPreserveSubpixel(p, p.getCentreY() - 4);
        }
        yFixed -= 0x20000;                                          // subi.l #$20000,obj.y(a0)
        y = yFixed >> 16;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        drawSheet(TtzObjectArt.PRESSURE, mapFrame, false, false);
    }
}
