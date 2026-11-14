package com.openggf.game.sonic3k.ending;

import com.openggf.camera.Camera;
import com.openggf.camera.NativeViewportFraming;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectServices;

/**
 * Where the ending's sprites stand in a picture wider than the cartridge's 320 pixels.
 *
 * <p>The ending scripts are written in native screen coordinates: an aircraft waits 64
 * pixels left of the screen, turns round 96 pixels past its right edge, a cloud is deleted
 * 64 pixels beyond it. Those words are also the scripts' clock (a routine ends when X
 * passes a constant), so they are never changed here. Drawn as they stand, a wider picture
 * would show the waiting and turning sprites in its margins, and a sprite leaving the
 * picture would vanish in the middle of it.
 *
 * <p>Instead every sprite that moves with a leader (an aircraft with its pilot, rider,
 * flames and tow) is drawn displaced by one amount shared by the whole group, taken from the
 * native extent of the group: the whole margin while its tail is still outside a native
 * edge or once its head has reached one, nothing while it is clear of both edges, and an
 * eased share inside {@link #BAND} pixels of an edge. A group therefore enters and leaves at
 * the real edge of the picture on the frame it would enter or leave the cartridge's, keeps
 * its shape, and stands where the cartridge puts it whenever it is clear of the edges.
 * Independent particles (clouds) are spread evenly across the wider picture instead.
 *
 * <p>This is presentation only: at 320 pixels the margin is zero and so is every result.
 */
public final class EndingWideStage {
    /** Native pixels inside each edge across which a crossing group is carried to the real edge. */
    static final int BAND = 32;
    private static final int NATIVE_WIDTH = 320;
    /** VDP sprite coordinates put the left edge of the screen at $80. */
    private static final int VDP_ORIGIN = 0x80;

    private EndingWideStage() { }

    /**
     * Picture beside the native window, on each side. Every ending read of the wider picture
     * goes through here. Today the camera is as wide as the picture, so this one number is
     * both how far the native window stands into the picture (where native sprites and plane
     * images are placed) and how much picture lies beside it (how far a crossing group is
     * carried, and which plane cells count as beside the window).
     */
    public static int margin(Camera camera) {
        return margin(camera.getWidth());
    }

    /** The margin of a picture this many pixels wide. */
    public static int margin(int pictureWidth) {
        return NativeViewportFraming.inset(pictureWidth);
    }

    /** A 16.16 VDP sprite X word ($80 at the left edge) as a 16.16 native screen position. */
    public static int screenFromVdp(int xFixed) {
        return ((short) (xFixed >> 16) - VDP_ORIGIN) << 16 | (xFixed & 0xFFFF);
    }

    /** A 16.16 world X as a 16.16 native screen position, given the native window's left edge. */
    public static int screenFromWorld(int xFixed, int nativeLeft) {
        return (short) ((xFixed >> 16) - nativeLeft) << 16 | (xFixed & 0xFFFF);
    }

    /** Pixels to add to the drawn X of this sprite: its group's displacement, or none if it has no group. */
    public static int groupShift(ObjectServices services, ObjectInstance sprite) {
        int margin = margin(services.camera());
        if (margin == 0 || !(sprite instanceof EndingFlightChildHost member)) return 0;
        EndingFlightChildHost leader = member.stageLeader();
        if (leader == null) return 0;
        int anchor = leader.stageScreenX(), min = anchor, max = anchor, leaderX = leader.getX();
        for (var object : services.objectManager().getActiveObjects()) {
            if (object == leader || !(object instanceof EndingFlightChildHost other)
                    || (object instanceof AbstractObjectInstance instance && instance.isDestroyed())
                    || other.stageLeader() != leader) continue;
            int x = anchor + ((short) (other.getX() - leaderX) << 16);
            min = Math.min(min, x);
            max = Math.max(max, x);
        }
        return shift(margin, min, max);
    }

    /** Pixels to add to the drawn X of an independent particle at this 16.16 VDP X. */
    public static int particleShift(Camera camera, int xFixed) {
        return spread(margin(camera), screenFromVdp(xFixed));
    }

    /**
     * Displacement of a group whose members' 16.16 native screen positions span
     * {@code [min, max]}. Never decreasing as the group moves right, so it cannot turn back.
     */
    static int shift(int margin, int min, int max) {
        if (margin <= 0) return 0;
        double tail = min / 65536.0, head = max / 65536.0;
        double left = tail >= BAND ? 0 : tail <= 0 ? 1 : ease((BAND - tail) / BAND);
        double right = head <= NATIVE_WIDTH - BAND ? 0 : head >= NATIVE_WIDTH ? 1
                : ease((head - (NATIVE_WIDTH - BAND)) / BAND);
        return (int) Math.round(margin * (right - left));
    }

    /** The native width stretched evenly to the picture's; a particle beyond an edge keeps the whole margin. */
    static int spread(int margin, int screenX) {
        if (margin <= 0) return 0;
        double offset = screenX / 65536.0 - NATIVE_WIDTH / 2.0;
        return (int) Math.round(margin * Math.clamp(offset / (NATIVE_WIDTH / 2.0), -1.0, 1.0));
    }

    /** Smoothstep: the group moves at its native speed at both ends of the band. */
    private static double ease(double t) {
        return t * t * (3 - 2 * t);
    }
}
