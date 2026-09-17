package com.openggf.game.sonic3k.objects;

/** Read-only accessors for Doomsday route diagnostics and tests. */
public final class DdzDiagnostics {
    private DdzDiagnostics() {
    }

    public static int bodyHitPoints(DdzEndBossBodyObjectInstance body) {
        return (byte) body.hitPoints();
    }

    public static int controllerFlags(DdzFlightControllerObjectInstance controller) {
        return controller.flagsForTest();
    }

    public static int controllerXVel(DdzFlightControllerObjectInstance controller) {
        return controller.xVelocity();
    }

    public static boolean bodyFlashing(DdzEndBossBodyObjectInstance body) {
        return body.flashing();
    }

    public static int bossRoutine(DdzEndBossObjectInstance boss) {
        return boss.routine();
    }
}
