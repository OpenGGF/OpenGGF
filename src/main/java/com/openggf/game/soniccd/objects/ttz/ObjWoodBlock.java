package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Tidal Tempest's wood block, object $44 in the R42A index ({@code WoodBlockObject}, objects/r4/wood_block.asm:3-146): a $20 wide
 * raft that floats on the water line ({@code water_y}). It exists only while the water's target is $780 or $5E0
 * ({@code WoodBlockObject_0_Routine0}, :23-41); once the water has reached its target ({@code static_water_y}) it is
 * the rising raft ($780, routine 4: top-solid; the player standing on it lowers the target to $5E0) or the lowered one ($5E0,
 * routines 6 and 8: it follows the water and is pushed toward the player's next X a pixel at a time while the wall
 * ahead is clear, and the player riding it takes its X, :85-146). It despawns by its own X ({@code obj.var_36}).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjWoodBlock extends TtzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    static final int TILE = 0x0350;
    /** {@code water_speed} in the act 2 index: 1 (move.b #1,water_speed, r4/main.asm:209); only act 1's future raises it. */
    private static final int WATER_SPEED = 1;
    private int routine;
    private int originX;
    private int mapFrame;
    /** obj.y_speed: {@code -water_speed} as the word read at :85-90 (the byte 1 sits in its high byte: -$100). The block never moves by it; SolidObject carries the rider with it. */
    private int ySpeed;

    public ObjWoodBlock(ObjectSpawn spawn) {
        super(spawn, "WoodBlock");
    }

    private int targetWaterY() {
        var water = services().waterSystem();
        return water == null ? 0 : water.getWaterLevelTarget(services().featureZoneId(), services().featureActId());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {                                             // WoodBlockObject_0_Routine0 (:23-39)
            routine = 2;
            priority = 3;
            width = 0x10;
            yRadius = 8;
            tileWord = TILE;
            originX = x;
            int target = targetWaterY();
            if (target != 0x780 && target != 0x5E0) {                   // cmpi.w #$780 / #$5E0, target_water_y; else DeleteObject
                setDestroyed(true);
                return;
            }
        }
        if (routine == 2) {                                             // WoodBlockObject_0_Routine2 (:41-69)
            y = waterY() & 0xFFFF;
            if (staticWaterY() != targetWaterY()) return;
            int level = staticWaterY();
            if (level == 0x780) routine = 4;
            else if (level == 0x5E0) routine = 6;
            else { setDestroyed(true); return; }
        }
        AbstractPlayableSprite sprite = player instanceof AbstractPlayableSprite p ? p : null;
        if (routine == 4) {                                             // WoodBlockObject_0_Routine4 (:71-82)
            y = waterY() & 0xFFFF;
            if (solidObject(Entry.TOP)) {                               // TopSolidObject; beq.s locret
                routine = 6;
                if (sprite != null) x = sprite.getCentreX() & 0xFFFF;   // move.w obj.x(a1),obj.x(a0)
                var water = services().waterSystem();
                if (water != null) water.setWaterLevelTarget(services().featureZoneId(), services().featureActId(), 0x5E0);
            }
            return;
        }
        if (routine == 6) {                                             // WoodBlockObject_0_Routine6 (:85-90)
            routine = 8;
            ySpeed = -(WATER_SPEED << 8);                               // move.w water_speed,d0; neg.w d0; move.w d0,obj.y_speed(a0)
        }
        if (routine == 8) follow(sprite);
    }

    /** WoodBlockObject_0_Routine8 (objects/r4/wood_block.asm:91-146). */
    private void follow(AbstractPlayableSprite sprite) {
        y = waterY() & 0xFFFF;
        if (sprite != null) {
            int d4 = (sprite.getXSpeed() >> 8) + (sprite.getCentreX() & 0xFFFF) - x;   // move.b obj.x_speed(a1),d4; ext.w; add; sub
            d4 = (short) d4;
            if (d4 != 0) {
                int step = d4 > 0 ? 1 : -1;
                int d5 = d4 > 0 ? width : -width;
                int count = Math.abs(d4);
                int probeY = y + 7;                                     // addq.w #7,obj.var_34(a0)
                while (true) {                                          // loc_20EF18
                    int probeX = (x + d5) & 0xFFFF;
                    int d1 = step > 0
                            ? ObjectTerrainUtils.checkRightWallDist(probeX, probeY).distance()      // CheckBlockRight3
                            : ObjectTerrainUtils.checkLeftWallDist(probeX ^ 0xF, probeY).distance();   // CheckBlockLeft3, eori.w #$F,d3
                    if (d1 < 0) break;
                    if (--count <= 0) break;
                    x = (x + step) & 0xFFFF;
                }
            }
        }
        if (solidObject(Entry.TOP) && sprite != null) {                 // loc_20EF4A
            NativePositionOps.writeXPosPreserveSubpixel(sprite, x);     // move.w obj.x(a0),obj.x(a1)
        }
    }

    @Override public int solidWidth() { return width; }
    @Override public int solidYRadius() { return yRadius; }
    @Override public int solidYVelocity() { return ySpeed; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override protected int despawnX() { return originX; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        drawSheet(TtzObjectArt.WOOD_BLOCK, mapFrame, false, false);
    }
}
