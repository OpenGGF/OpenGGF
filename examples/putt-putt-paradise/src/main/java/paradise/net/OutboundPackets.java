package paradise.net;

import java.util.ArrayDeque;
import static paradise.net.GolfPacket.*;

/** Socket-owned mailbox: bounded reliable FIFO and one newest pending full view. */
final class OutboundPackets {
    static final int MAX_RELIABLE_MESSAGES = 64;
    static final int MAX_RELIABLE_BYTES = 4 * 1024 * 1024;
    record Stats(int reliableMessages, int reliableBytes, int pendingFrames, long coalescedFrames) { }
    private final ArrayDeque<byte[]> reliable = new ArrayDeque<>();
    private byte[] view;
    private ShotId viewId;
    private long viewRevision;
    private int reliableBytes;
    private long coalesced;
    private boolean closed;

    boolean offer(GolfPacket packet) {
        byte[] encoded = GolfCodec.encode(packet);
        synchronized (this) {
            if (closed) return false;
            if (packet instanceof ViewFrame frame) {
                if (view != null) {
                    coalesced++;
                    if (frame.id().equals(viewId) && frame.revision() <= viewRevision) return true;
                }
                view = encoded; viewId = frame.id(); viewRevision = frame.revision();
            } else {
                if (reliable.size() >= MAX_RELIABLE_MESSAGES || encoded.length > MAX_RELIABLE_BYTES - reliableBytes) return false;
                reliable.addLast(encoded); reliableBytes += encoded.length;
            }
            notifyAll(); return true;
        }
    }
    synchronized byte[] take() throws InterruptedException {
        while (!closed && reliable.isEmpty() && view == null) wait();
        if (closed) return null;
        if (!reliable.isEmpty()) {
            byte[] next = reliable.removeFirst(); reliableBytes -= next.length; return next;
        }
        byte[] next = view; view = null; viewId = null; return next;
    }
    synchronized Stats stats() { return new Stats(reliable.size(), reliableBytes, view == null ? 0 : 1, coalesced); }
    synchronized void clear() { reliable.clear(); reliableBytes = 0; view = null; viewId = null; }
    synchronized void close() { closed = true; clear(); notifyAll(); }
}
