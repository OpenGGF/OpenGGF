package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.level.CdR11aTerrain;

import java.util.Objects;

/**
 * One R11A Player_GroundCol call over the production native terrain probes.
 * Source: scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * Level/Collision Floor.asm:30-146,227-454. Interleaved unused movement is excluded.
 * This owns neither integration, clocks, air collision nor a whole player dispatch.
 * Inputs and terrain are immutable; an unavailable probe throws without publishing
 * any partial actor or global angle-buffer writes. There is no retained call state.
 */
public final class CdPlayerGroundCollision {
    /**
     * The complete actor fields this boundary can write. Positions are raw native
     * 16:16 longs in Java ints, including all sixteen fraction bits. Other fields
     * are unsigned native bytes; flags is oFlags, not oSprFlags.
     */
    public record State(int xPosition, int yPosition, int angle, int flags, int previousAnimation) {
        public State {
            checkByte(angle);
            checkByte(flags);
            checkByte(previousAnimation);
        }
    }

    /**
     * Read-only actor context: original radius bytes, oSprFlags and oPlayerStick.
     * Radii deliberately remain bytes here: EXT.W interprets $80..$FF as negative.
     * No default radii, sprite-plane flag, stick value or actor state is supplied.
     */
    public record Input(State state, int xRadius, int yRadius, int actorSprFlags, int stick) {
        public Input {
            Objects.requireNonNull(state, "state");
            checkByte(xRadius);
            checkByte(yRadius);
            checkByte(actorSprFlags);
            checkByte(stick);
        }
    }

    /**
     * Final actor values and both native global angle bytes after this call.
     * GroundCol always overwrites both buffers, so no incoming buffers are needed.
     * This describes state effects, not CPU registers or individual memory stores.
     */
    public record Result(State state, int primaryAngle, int secondaryAngle) {
        public Result {
            Objects.requireNonNull(state, "state");
            checkByte(primaryAngle);
            checkByte(secondaryAngle);
        }
    }

    private enum Surface { FLOOR, RIGHT_WALL, CEILING, LEFT_WALL }

    private final CdPlayerTerrain terrain;

    public CdPlayerGroundCollision(CdR11aTerrain terrain) {
        this(new CdPlayerTerrain(terrain));
    }

    public CdPlayerGroundCollision(CdPlayerTerrain terrain) {
        this.terrain = Objects.requireNonNull(terrain, "terrain");
    }

    /** Player_GroundCol (Collision Floor.asm:30-71), including its object bypass. */
    public Result ground(Input input) {
        Objects.requireNonNull(input, "input");
        if ((input.state().flags() & 8) != 0) {
            return new Result(input.state(), 0, 0);
        }
        return switch (quadrant(input.state().angle())) {
            case 0x40 -> walkVertL(input);
            case 0x80 -> walkCeiling(input);
            case 0xC0 -> walkVertR(input);
            default -> walkFloor(input);
        };
    }

    /** Signed byte branches are intentionally different from PickSensor's rounding. */
    private static int quadrant(int angle) {
        int rounded;
        if ((byte) (angle + 0x20) < 0) {
            rounded = angle;
            if ((byte) angle < 0) { rounded = (rounded - 1) & 0xFF; }
            rounded = (rounded + 0x20) & 0xFF;
        } else {
            rounded = angle;
            if ((byte) angle < 0) { rounded = (rounded + 1) & 0xFF; }
            rounded = (rounded + 0x1F) & 0xFF;
        }
        return rounded & 0xC0;
    }

    /** Player_WalkFloor (Collision Floor.asm:80-141): right sensor, then left. */
    private Result walkFloor(Input input) {
        int x = input.state().xPosition() >>> 16;
        int y = input.state().yPosition() >>> 16;
        int rx = (byte) input.xRadius();
        int ry = (byte) input.yRadius();
        int sensorY = word(y + ry);
        var primary = terrain.floor(word(x + rx), sensorY, 13, 0, 16, input.actorSprFlags());
        var secondary = terrain.floor(word(x - rx), sensorY, 13, 0, 16, input.actorSprFlags());
        return pickAndAlign(input, Surface.FLOOR, primary, secondary);
    }

    /** Player_WalkVertR (Collision Floor.asm:254-314): upper sensor, then lower. */
    private Result walkVertR(Input input) {
        int x = input.state().xPosition() >>> 16;
        int y = input.state().yPosition() >>> 16;
        int rx = (byte) input.xRadius();
        int ry = (byte) input.yRadius();
        int sensorX = word(x + ry);
        var primary = terrain.wall(sensorX, word(y - rx), 13, 0, 16, input.actorSprFlags());
        var secondary = terrain.wall(sensorX, word(y + rx), 13, 0, 16, input.actorSprFlags());
        return pickAndAlign(input, Surface.RIGHT_WALL, primary, secondary);
    }

    /** Player_WalkCeiling (Collision Floor.asm:323-384): right sensor, then left. */
    private Result walkCeiling(Input input) {
        int x = input.state().xPosition() >>> 16;
        int y = input.state().yPosition() >>> 16;
        int rx = (byte) input.xRadius();
        int ry = (byte) input.yRadius();
        int sensorY = word(y - ry) ^ 0xF;
        var primary = terrain.floor(word(x + rx), sensorY, 13, 0x1000, -16, input.actorSprFlags());
        var secondary = terrain.floor(word(x - rx), sensorY, 13, 0x1000, -16, input.actorSprFlags());
        return pickAndAlign(input, Surface.CEILING, primary, secondary);
    }

    /** Player_WalkVertL (Collision Floor.asm:393-454): upper sensor, then lower. */
    private Result walkVertL(Input input) {
        int x = input.state().xPosition() >>> 16;
        int y = input.state().yPosition() >>> 16;
        int rx = (byte) input.xRadius();
        int ry = (byte) input.yRadius();
        int sensorX = word(x - ry) ^ 0xF;
        var primary = terrain.wall(sensorX, word(y - rx), 13, 0x800, -16, input.actorSprFlags());
        var secondary = terrain.wall(sensorX, word(y + rx), 13, 0x800, -16, input.actorSprFlags());
        return pickAndAlign(input, Surface.LEFT_WALL, primary, secondary);
    }

    private static Result pickAndAlign(Input input, Surface surface,
                                       CdPlayerTerrain.Result primary, CdPlayerTerrain.Result secondary) {
        // GroundCol initializes both buffers to 3 (:39-42). A primitive may leave
        // its byte unwritten; an actual zero write must not be confused with that.
        int primaryAngle = primary.angleWrite().orElse(3);
        int secondaryAngle = secondary.angleWrite().orElse(3);
        // Player_PickSensor (:227-245): signed CMP.W/BLE keeps secondary on ties.
        boolean useSecondary = secondary.distance() <= primary.distance();
        int distance = useSecondary ? secondary.distance() : primary.distance();
        int selectedAngle = useSecondary ? secondaryAngle : primaryAngle;
        State old = input.state();
        int angle = (selectedAngle & 1) == 0 ? selectedAngle : (old.angle() + 0x20) & 0xC0;
        int x = old.xPosition();
        int y = old.yPosition();
        int flags = old.flags();
        int previousAnimation = old.previousAnimation();

        // Each walk routine selects angle before testing distance. Below -14,
        // only that angle/buffers change; nonzero stick affects positive ledges.
        if (distance > 14 && input.stick() == 0) {
            flags = (flags | 2) & ~0x20;
            previousAnimation = 1;
        } else if (distance != 0 && distance >= -14) {
            switch (surface) {
                case FLOOR -> y = addIntegerWord(y, distance);
                case RIGHT_WALL -> x = addIntegerWord(x, distance);
                case CEILING -> y = addIntegerWord(y, -distance);
                case LEFT_WALL -> x = addIntegerWord(x, -distance);
            }
        }
        return new Result(new State(x, y, angle, flags, previousAnimation), primaryAngle, secondaryAngle);
    }

    /** ADD.W/SUB.W on oX/oY changes only the high word of the native 16:16 long. */
    private static int addIntegerWord(int position, int delta) {
        return (word((position >>> 16) + delta) << 16) | (position & 0xFFFF);
    }

    private static int word(int value) { return value & 0xFFFF; }

    private static void checkByte(int value) {
        if ((value & ~0xFF) != 0) {
            throw new IllegalArgumentException("Expected an unsigned native byte");
        }
    }
}
