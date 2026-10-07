package com.openggf.sprites.playable;

import com.openggf.game.ModApi;
import com.openggf.physics.Direction;
import com.openggf.physics.GroundSensor;
import com.openggf.physics.Sensor;

/** Paired floor/ceiling probes follow profile radii; side probes retain a fixed reach. */
@ModApi
public record PlayableSensorSpec(byte pushRadius, byte groundPushYOffset) {
    public PlayableSensorSpec {
        if (pushRadius <= 0 || groundPushYOffset < 0) {
            throw new IllegalArgumentException("Sensor reach must be positive and ground offset nonnegative");
        }
    }
    public static PlayableSensorSpec standard() { return new PlayableSensorSpec((byte) 10, (byte) 8); }

    Sensor[][] createSensors(AbstractPlayableSprite sprite) {
        Sensor[] ground = new Sensor[] {
                new GroundSensor(sprite, Direction.DOWN, (byte) 0, (byte) 0, true),
                new GroundSensor(sprite, Direction.DOWN, (byte) 0, (byte) 0, true) };
        Sensor[] ceiling = new Sensor[] {
                new GroundSensor(sprite, Direction.UP, (byte) 0, (byte) 0, false),
                new GroundSensor(sprite, Direction.UP, (byte) 0, (byte) 0, false) };
        Sensor[] push = new Sensor[] {
                new GroundSensor(sprite, Direction.LEFT, (byte) -pushRadius, (byte) 0, false),
                new GroundSensor(sprite, Direction.RIGHT, pushRadius, (byte) 0, false) };
        return new Sensor[][] { ground, ceiling, push };
    }
}
