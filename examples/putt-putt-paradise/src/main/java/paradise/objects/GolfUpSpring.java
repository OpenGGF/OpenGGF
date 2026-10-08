package paradise.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.sonic2.objects.SpringObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Golf-only side entry; native top contact, ROM art, strength, animation and sound remain owned by Obj41. */
public final class GolfUpSpring extends SpringObjectInstance {
    // Captured with the object's scalar rewind state, independent of the player's input lock.
    private boolean sideEntryActive;
    public GolfUpSpring(ObjectSpawn spawn, String name) { super(spawn, name); }

    @Override public void update(int vIntRunCount, PlayableEntity entity) {
        short incomingGroundSpeed = entity instanceof AbstractPlayableSprite player ? player.getGSpeed() : 0;
        super.update(vIntRunCount, entity);
        var contact = services().solidExecution().lastCheckpoint().perPlayer().get(entity);
        if (contact == null || contact.kind() != ContactKind.SIDE) { sideEntryActive = false; return; }
        if (!(entity instanceof AbstractPlayableSprite player) || player.getDead() || player.isHurt()
                || !contact.preContact().rolling()) return;
        short incomingXSpeed = contact.preContact().xSpeed();
        if ((player.getCentreX() < spawn.x() ? incomingXSpeed : -incomingXSpeed) <= 0) return;
        if (sideEntryActive && player.getYSpeed() >= 0) return;

        // SolidObject's side stop has already clamped position and cleared speed.
        // Keep that separation, but recover the shot's momentum for the native bounce.
        player.setXSpeed(incomingXSpeed);
        player.setGSpeed(incomingGroundSpeed);
        services().objectManager().solidContacts().releaseObjectPushLatch(entity, this);
        services().objectManager().clearRidingObjectForJump(entity);
        player.setPushing(false);
        if (!sideEntryActive) {
            sideEntryActive = true;
            // loc_189CA's +8px is a top-landing correction; side entry has no top landing.
            NativePositionOps.addYPosPreserveSubpixel(player, -8);
            applyUpSpring(player);
        }
        // The next rising step may still touch the housing. Preserve forward
        // momentum through that side stop without applying another upward impulse.
        player.updateSensors(player.getX(), player.getY());
    }

    @Override public GolfUpSpring recreateForRewind(RewindRecreateContext context) {
        return new GolfUpSpring(context.spawn(), getName());
    }
}
