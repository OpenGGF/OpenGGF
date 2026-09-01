package com.openggf.audio.session;

import java.util.List;

public record SmpsWriteProgram(List<SmpsChipWrite> writes) {
    public SmpsWriteProgram {
        writes = List.copyOf(writes);
    }
}
