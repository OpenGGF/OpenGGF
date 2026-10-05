package paradise.net;

import java.util.Objects;
import static paradise.net.GolfPacket.*;

/** Presentation-thread filter; callers reset identity on TurnOpened and preserve revision on reconnect. */
public final class ViewRevisions {
    private ShotId active;
    private long revision = -1;
    public void begin(ShotId id) { begin(id, -1); }
    public void begin(ShotId id, long lastRevision) {
        Objects.requireNonNull(id);
        if (lastRevision < -1) throw new IllegalArgumentException("revision");
        active = id; revision = lastRevision;
    }
    public boolean accept(ViewFrame frame) {
        Objects.requireNonNull(frame);
        if (!frame.id().equals(active) || frame.revision() <= revision) return false;
        revision = frame.revision(); return true;
    }
    public long lastRevision() { return revision; }
    public void clear() { active = null; revision = -1; }
}
