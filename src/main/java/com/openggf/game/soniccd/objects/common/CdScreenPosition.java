package com.openggf.game.soniccd.objects.common;

import com.openggf.camera.CameraPresentationAccess;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.level.objects.HudProfileAccess;
import com.openggf.level.objects.ObjectServices;

/** Presentation offsets only: native object coordinates and animation clocks stay unchanged. */
final class CdScreenPosition {
    private CdScreenPosition() { }

    static int hudOrigin(ObjectServices services) {
        var config = services.configuration();
        boolean left = config != null && "LEFT_EDGE".equals(config.getString(SonicConfiguration.DISPLAY_HUD_ANCHOR));
        return origin(services, left);
    }

    /** Title cards, tallies and game-over text centre independently of the HUD preference. */
    static int centredOrigin(ObjectServices services) {
        return origin(services, false);
    }

    private static int origin(ObjectServices services, boolean left) {
        var camera = services.camera();
        var graphics = services.graphicsManager();
        int width = CameraPresentationAccess.width(camera);
        if (graphics != null) width = Math.max(width, graphics.getProjectionWidth());
        return HudProfileAccess.cameraRelativeOriginX(width, left, camera);
    }
}
