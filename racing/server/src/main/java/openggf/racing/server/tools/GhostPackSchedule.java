package openggf.racing.server.tools;

import com.openggf.net.ghost.GhostFrame;
import com.openggf.net.hub.TrackValidationProfile;
import java.util.List;

/**
 * Deterministic cosmetic runner timing for the capacity-video task, October 2026.
 * Mirrored pairs surround the live runner; smooth surges move runners between
 * near/far relevance and the camera. This schedules recorded poses, never physics.
 */
public final class GhostPackSchedule {
    private GhostPackSchedule() { }

    public static int sourceFrame(int runner, int elapsed, int frames) {
        if (runner < 0 || elapsed < 0 || frames < 2) throw new IllegalArgumentException("runner/time/route");
        int pair = runner / 2;
        double sign = runner % 2 == 0 ? 1 : -1;
        double drift = 0.04 + (pair % 8) * 0.01;
        double phase = (pair % 16) * Math.PI / 8;
        double amplitude = pair % 3 == 0 ? 240 : pair % 3 == 1 ? 120 : 40;
        double period = 2000 + (pair % 5) * 120;
        double offset = drift * elapsed + amplitude
                * (Math.sin(2 * Math.PI * elapsed / period + phase) - Math.sin(phase));
        // The maximum derivative of the offset is < 0.87: every runner
        // advances monotonically, including delayed/surging profiles.
        return Math.clamp((int) Math.floor(elapsed + sign * offset), 0, frames - 1);
    }

    /** Retain intermediate recorded poses when retiming would exceed ingress speed bounds. */
    public static int admittedSourceFrame(int runner, int elapsed, List<GhostFrame> route, int previous) {
        int target = Math.min(sourceFrame(runner, elapsed, route.size()), previous + 2);
        GhostFrame before = route.get(previous);
        int max = TrackValidationProfile.GLOBAL_SPEED_CEILING_PX_PER_FRAME;
        while (target > previous) {
            GhostFrame after = route.get(target);
            if (Math.abs(after.x() - before.x()) <= max && Math.abs(after.y() - before.y()) <= max)
                return target;
            target--;
        }
        if (sourceFrame(runner, elapsed, route.size()) > previous && previous + 1 < route.size()) {
            GhostFrame next = route.get(previous + 1);
            if (Math.abs(next.x() - before.x()) > max || Math.abs(next.y() - before.y()) > max)
                throw new IllegalArgumentException("recorded route itself exceeds ghost speed admission");
        }
        return previous;
    }

    public static int finishFrame(int runner, int frames) {
        int low = 0, high = frames * 2;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (sourceFrame(runner, middle, frames) >= frames - 1) high = middle;
            else low = middle + 1;
        }
        return low;
    }
}
