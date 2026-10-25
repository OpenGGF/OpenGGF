package typinghog.play;

import com.openggf.game.GameServices;
import com.openggf.game.sonic2.objects.TornadoObjectInstance;
import com.openggf.game.sonic3k.objects.DdzFlightControllerObjectInstance;
import com.openggf.physics.Direction;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SuperState;
import typinghog.rails.SectionPlan;

/** Preserves an established native flight actor; it never constructs ground or a substitute plane. */
final class SectionFlight {
    private final int x, y, animation, mapping;
    private final boolean air, onObject, flipX, flipY, mappingOwned;
    private final Direction facing;
    private final byte angle;

    static boolean ready(SectionPlan plan, AbstractPlayableSprite player) {
        if (player.getDead() || player.isHurt() || !player.getCode().equals("sonic")) return false;
        var objects = GameServices.level().getObjectManager();
        return switch (plan.mode()) {
            case GROUND -> false;
            case ESCORT -> plan.game().equals("s2") && plan.zone() == 8 && plan.act() == 0
                    && !player.getAir() && objects.getRidingObject(player) instanceof TornadoObjectInstance tornado
                    && !tornado.isDestroyed() && tornado.isSczRideStartPreludeObject()
                    && objects.isRidingObject(player, tornado) && objects.hasObjectStandingBit(player, tornado);
            case FINALE -> plan.game().equals("s3k") && plan.zone() == 12 && plan.act() == 0
                    && player.isSuperSonic() && player.getSuperStateController() != null
                    && player.getSuperStateController().getState() == SuperState.SUPER
                    && player.isObjectControlAllowsCpu()
                    && objects.getActiveObjects().stream().anyMatch(object ->
                            object instanceof DdzFlightControllerObjectInstance && !object.isDestroyed());
        };
    }

    SectionFlight(AbstractPlayableSprite player) {
        x = player.getCentreX(); y = player.getCentreY();
        animation = player.getAnimationId(); mapping = player.getMappingFrame();
        air = player.getAir(); onObject = player.isOnObject();
        facing = player.getDirection(); angle = player.getAngle();
        flipX = player.getRenderHFlip(); flipY = player.getRenderVFlip();
        mappingOwned = player.isObjectMappingFrameControl();
    }

    /** Restores the native flight pose after a homing volley, including its native contact/air state. */
    void present(AbstractPlayableSprite player) {
        player.setRolling(false);
        player.setAir(air); player.setOnObject(onObject);
        NativePositionOps.writeXPosResetSubpixel(player, x);
        NativePositionOps.writeYPosResetSubpixel(player, y);
        player.setAngle(angle); player.setDirection(facing);
        player.setAnimationId(animation); player.setMappingFrame(mapping);
        player.setRenderFlips(flipX, flipY);
        player.setObjectMappingFrameControl(mappingOwned);
    }
}
