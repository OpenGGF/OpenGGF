package com.openggf.game.sonic3k.resources;

import com.openggf.game.timing.HardwareWorkHandle;

import java.util.List;
import java.util.Objects;

/** Physical direct-FIFO membership, separate from timing payload ownership. */
public record S3kKosDecompressionQueueSnapshot(List<Entry> physicalEntries) {
    public S3kKosDecompressionQueueSnapshot {
        physicalEntries = List.copyOf(Objects.requireNonNull(physicalEntries, "physicalEntries"));
    }

    public record Entry(HardwareWorkHandle handle, S3kKosDecompressionDescriptor descriptor) {
        public Entry {
            Objects.requireNonNull(handle, "handle");
            Objects.requireNonNull(descriptor, "descriptor");
        }
    }
}
