package com.openggf.audio.session;

import java.util.List;

public record SmpsWriteProgram(List<SmpsChipWrite> writes) {
    public static final SmpsWriteProgram EMPTY =
            new SmpsWriteProgram(List.of());

    public SmpsWriteProgram {
        writes = List.copyOf(writes);
    }
}
