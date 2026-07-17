package com.openggf.physics;

@com.openggf.game.ModApi
public final class SensorResult {
    private byte angle;
    private byte distance;
    private int tileId;
    private Direction direction;
    private boolean foregroundAngleWritten;
    private byte foregroundAngle;
    private boolean backgroundScanExecuted;
    private boolean backgroundAngleWritten;
    private byte backgroundAngle;
    private boolean restoreForegroundAngleState;

    public SensorResult() {}

    public SensorResult(byte angle, byte distance, int tileId, Direction direction) {
        set(angle, distance, tileId, direction);
    }

    public SensorResult set(byte angle, byte distance, int tileId, Direction direction) {
        this.angle = angle;
        this.distance = distance;
        this.tileId = tileId;
        this.direction = direction;
        this.foregroundAngleWritten = tileId != 0;
        this.foregroundAngle = angle;
        this.backgroundScanExecuted = false;
        this.backgroundAngleWritten = false;
        this.backgroundAngle = 0;
        this.restoreForegroundAngleState = false;
        return this;
    }

    public byte angle() { return angle; }
    public byte distance() { return distance; }
    public int tileId() { return tileId; }
    public Direction direction() { return direction; }
    public boolean foregroundAngleWritten() { return foregroundAngleWritten; }
    public byte foregroundAngle() { return foregroundAngle; }
    public boolean backgroundScanExecuted() { return backgroundScanExecuted; }
    public boolean backgroundAngleWritten() { return backgroundAngleWritten; }
    public byte backgroundAngle() { return backgroundAngle; }
    public boolean restoreForegroundAngleState() { return restoreForegroundAngleState; }

    SensorResult setNativeAngleWriteTrace(boolean foregroundAngleWritten,
                                          byte foregroundAngle,
                                          boolean backgroundScanExecuted,
                                          boolean backgroundAngleWritten,
                                          byte backgroundAngle,
                                          boolean restoreForegroundAngleState) {
        this.foregroundAngleWritten = foregroundAngleWritten;
        this.foregroundAngle = foregroundAngle;
        this.backgroundScanExecuted = backgroundScanExecuted;
        this.backgroundAngleWritten = backgroundAngleWritten;
        this.backgroundAngle = backgroundAngle;
        this.restoreForegroundAngleState = restoreForegroundAngleState;
        return this;
    }

    public SensorResult copyFrom(SensorResult other) {
        this.angle = other.angle;
        this.distance = other.distance;
        this.tileId = other.tileId;
        this.direction = other.direction;
        this.foregroundAngleWritten = other.foregroundAngleWritten;
        this.foregroundAngle = other.foregroundAngle;
        this.backgroundScanExecuted = other.backgroundScanExecuted;
        this.backgroundAngleWritten = other.backgroundAngleWritten;
        this.backgroundAngle = other.backgroundAngle;
        this.restoreForegroundAngleState = other.restoreForegroundAngleState;
        return this;
    }
}
