package paradise.net;

import java.util.Objects;
import static paradise.net.GolfPacket.*;

/** Caller-owned reliable cue watermark. Local previews reserve their cue IDs through the same method. */
public final class SoundCues {
    private ShotId active;
    private long lastCue = -1;
    public void begin(ShotId id, long lastPlayedCue) {
        Objects.requireNonNull(id);
        if (lastPlayedCue < -1) throw new IllegalArgumentException("cue watermark");
        active = id; lastCue = lastPlayedCue;
    }
    public boolean accept(SoundCue cue) {
        Objects.requireNonNull(cue);
        if (!cue.id().equals(active) || cue.cueId() <= lastCue) return false;
        lastCue = cue.cueId(); return true;
    }
    public long lastCue() { return lastCue; }
    public void clear() { active = null; lastCue = -1; }
}
