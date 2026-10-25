package com.openggf.game.mode;

import com.openggf.game.session.GameplayModeContext;

/**
 * Test-only access to the package-private {@link CourseControl} constructor, so a probe in
 * another package can capture and restore course checkpoints through exactly the operations a
 * level controller receives (owner/level/act/layout-version checks included) without installing
 * a controller. Origin: Typing of the Hedgehog rails lane (2026-10-09).
 */
public final class CourseControlProbeAccess {
    private CourseControlProbeAccess() { }

    public static CourseControl open(GameplayModeContext context) {
        return new CourseControl(context);
    }
}
