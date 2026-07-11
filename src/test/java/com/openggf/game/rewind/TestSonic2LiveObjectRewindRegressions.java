package com.openggf.game.rewind;

import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.objects.Sonic2ObjectRegistry;
import com.openggf.game.sonic2.objects.badniks.BadnikProjectileInstance;
import com.openggf.game.sonic2.objects.badniks.BuzzerBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.CoconutsBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.MasherBadnikInstance;
import com.openggf.level.objects.AbstractBadnikInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Green-only inventory scaffold for Sonic 2 live-object rewind regressions.
 *
 * <p>The Sonic 2 badnik package currently has four legacy owners that override the
 * no-context {@code captureRewindState()} / {@code restoreRewindState()} pair:
 *
 * <ul>
 *   <li>{@link MasherBadnikInstance}: concrete badnik. It owns a second movement
 *       representation, a {@code SubpixelMotion.State}, plus the jump origin
 *       {@code initialYPos}; both are separate from the inherited
 *       {@code currentX/currentY/xVelocity/yVelocity} view.</li>
 *   <li>{@link BuzzerBadnikInstance}: concrete badnik. Its body has no second
 *       movement representation; it uses only the inherited
 *       {@code currentX/currentY/xVelocity/yVelocity} fields. Its nested flame child
 *       has child-local coordinates, but that is a separate rewind owner.</li>
 *   <li>{@link CoconutsBadnikInstance}: concrete badnik. It has no subpixel or
 *       anchor/origin position copy and uses inherited {@code currentX/currentY}; it
 *       does intentionally shadow the inherited {@code yVelocity} with a local field.</li>
 *   <li>{@link BadnikProjectileInstance}: projectile base for concrete children
 *       nested under their owning badniks; the concrete projectile kinds are selected
 *       by its {@code ProjectileType} enum. It owns
 *       projectile-local {@code currentX/currentY/xVelocity/yVelocity} and a second
 *       {@code SubpixelMotion.State} representation. Badniks create these concrete
 *       projectile children through this common class rather than Java subclasses.</li>
 * </ul>
 *
 * <p>The reported "Snapper fish" maps to Masher in this repository: a repository
 * search contains no Snapper object or class, while EHZ object ID {@code 0x5C} is
 * {@link Sonic2ObjectIds#MASHER} and {@link Sonic2ObjectRegistry} registers that ID as
 * {@link MasherBadnikInstance}. Masher is therefore the concrete Snapper-report
 * reproduction target.
 */
class TestSonic2LiveObjectRewindRegressions {

    @Test
    void legacyOwnersAndSnapperMappingRemainConcrete() {
        List<Class<?>> legacyOwners = List.of(
                MasherBadnikInstance.class,
                BuzzerBadnikInstance.class,
                CoconutsBadnikInstance.class,
                BadnikProjectileInstance.class);

        assertEquals(0x5C, Sonic2ObjectIds.MASHER);
        ObjectSpawn masherSpawn = new ObjectSpawn(0x578, 0x2D0, Sonic2ObjectIds.MASHER,
                0, 0, false, 0);
        assertInstanceOf(MasherBadnikInstance.class, new Sonic2ObjectRegistry().create(masherSpawn));

        assertTrue(legacyOwners.subList(0, 3).stream()
                .allMatch(AbstractBadnikInstance.class::isAssignableFrom));
        assertTrue(legacyOwners.stream().allMatch(owner -> {
            try {
                owner.getDeclaredMethod("captureRewindState");
                owner.getDeclaredMethod("restoreRewindState", PerObjectRewindSnapshot.class);
                return true;
            } catch (NoSuchMethodException exception) {
                return false;
            }
        }));
    }
}
