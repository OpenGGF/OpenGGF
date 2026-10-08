package paradise;

import com.openggf.audio.GameSound;
import com.openggf.game.GameServices;
import com.openggf.game.mode.CourseControl;
import paradise.model.GolfRules;
import paradise.model.GolfShot;
import paradise.model.ShotMeter;

/** Golf's native-physics adapter: shot tuning and sound choices stay in creator code. */
final class GolfSwing {
    private GolfSwing() { }

    static void launch(CourseControl course, GolfShot shot) {
        var player = GameServices.camera().getFocusedSprite();
        var velocity = GolfRules.launchVelocity(shot.direction(), shot.elevationDegrees(),
                shot.speedFixed(), player.getAngle(), shot.spin());
        course.launchRolling(shot.direction(), velocity.x(), velocity.y(), velocity.ground(), !shot.isPutt());
        GameServices.audio().playSfx(GameSound.SPINDASH_RELEASE);
    }

    static void chargeSound() {
        // Native SMPS already supplies the increasing spindash pitch.
        GameServices.audio().playSfx(GameSound.SPINDASH_CHARGE);
    }

    static void resumeVerticalDrive(ShotMeter.State state) {
        if (state.stage() != ShotMeter.Stage.WATCH || state.shot() == null
                || state.shot().elevationDegrees() != GolfRules.MAX_ELEVATION_DEGREES) return;
        var player = GameServices.camera().getFocusedSprite();
        if (player.getAir() && player.getYSpeed() < 0 && player.getXSpeed() == 0
                && !player.getDead() && !player.isHurt() && !player.getSpringing()) {
            // Retry a wall-stopped forward bias while ascending. Native collision
            // still clamps each step; never drive during descent or overwrite a bounce.
            player.setXSpeed((short) GolfRules.launchVelocity(state.shot().direction(), 90,
                    state.shot().speedFixed(), 0, state.shot().spin()).x());
        }
    }

    static boolean applyLandingSpin(CourseControl course, GolfShot shot) {
        var player = GameServices.camera().getFocusedSprite();
        if (player.getAir() || !course.playerState().floorSupport() || player.getDead()
                || player.isHurt() || player.getSpringing()) return false;
        int ground = GolfRules.landingSpeed(player.getGSpeed(), shot);
        double angle = (player.getAngle() & 255) * Math.PI / 128;
        player.setGSpeed((short) ground);
        player.setXSpeed((short) Math.round(ground * Math.cos(angle)));
        player.setYSpeed((short) Math.round(ground * Math.sin(angle)));
        return true;
    }
}
