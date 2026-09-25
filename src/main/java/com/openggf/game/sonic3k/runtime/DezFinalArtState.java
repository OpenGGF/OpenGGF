package com.openggf.game.sonic3k.runtime;

import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.game.sonic3k.Sonic3kPlcLoader;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.level.objects.ObjectServices;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Final DEZ module-job ownership survives the root-to-escape SST handoff.
 * Native Queue_Kos_Module owns a global FIFO; retaining prepared-job handles on
 * a deleting sprite would lose the engine's matching publication/claim state.
 * This zone owner does not introduce any readiness gate or change native waits.
 */
public final class DezFinalArtState {
    private final List<Long> pending=new ArrayList<>();

    public void queueModule(ObjectServices services,int source,int tile) {
        try {
            var rom=services.rom(); var physical=services.kosinskiModuleQueue();
            if(physical!=null) {
                Sonic3kPlcLoader.bindRuntimePatternDmaTarget(physical,services);
                physical.enqueue(rom,source,tile*32);
            }
            try {
                pending.add(S3kRuntimeArtCoordinator.from(services).moduleQueue().queue(rom,source,tile).ordinal());
            } catch(IllegalStateException unavailable) {
                // Bare object fixtures may lack timing; production owns both queues.
                if(!"runtime-art coordination is unavailable in these object services".equals(unavailable.getMessage())) throw unavailable;
            }
        } catch(IOException failure) { throw new UncheckedIOException(failure); }
    }
    public void service(ObjectServices services) {
        if(pending.isEmpty()) return;
        var queue=S3kRuntimeArtCoordinator.from(services).moduleQueue();
        for(var iterator=pending.iterator();iterator.hasNext();) {
            long ordinal=iterator.next();
            var handle=services.hardwareTiming().pendingHandle(HardwareWorkKind.KOS_MODULE_QUEUE,ordinal).orElseThrow();
            if(queue.isReady(handle)) { queue.claim(handle); iterator.remove(); }
        }
    }
    /** Load_PLC_Raw remains synchronous in the existing Nemesis loader; no FIFO timing claim. */
    public void loadRaw(ObjectServices services,int source,int tile) {
        if(!(services.currentLevel() instanceof Sonic3kLevel level)) return;
        try {
            var changed=Sonic3kPlcLoader.applyRawToLevel(List.of(new Sonic3kPlcLoader.RawPlcEntry(tile,source)),level,services.rom());
            Sonic3kPlcLoader.refreshAffectedRenderers(changed,services.levelManager());
        } catch(IOException failure) { throw new UncheckedIOException(failure); }
    }
    public int pendingCount() { return pending.size(); }
    public int snapshotBytes() { return Integer.BYTES+pending.size()*Long.BYTES; }
    public void captureTo(ByteBuffer buffer) {
        buffer.putInt(pending.size()); for(long ordinal:pending) buffer.putLong(ordinal);
    }
    public void restoreFrom(ByteBuffer buffer) {
        pending.clear(); int count=buffer.getInt();
        for(int i=0;i<count;i++) pending.add(buffer.getLong());
    }
}
