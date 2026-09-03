package com.openggf.audio.session;

import com.openggf.audio.rewind.SmpsDriverSnapshot;

import java.util.Objects;

public interface SmpsLogicalTransitionPolicy {
    Result prepareMusicStart(
            SmpsDriverSnapshot current,
            SmpsDriverSnapshot.SequencerEntry incomingMusic);

    Result prepareOverrideRestore(
            SmpsDriverSnapshot current,
            SmpsDriverSnapshot saved);

    record Result(
            SmpsDriverSnapshot logical,
            SmpsWriteProgram firstServiceWrites) {
        public Result {
            Objects.requireNonNull(logical, "logical");
            Objects.requireNonNull(firstServiceWrites,
                    "firstServiceWrites");
        }
    }
}
