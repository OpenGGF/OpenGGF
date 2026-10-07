package sitarhero.model;

import java.util.List;

/** Immutable session data, independent of the engine, performer, renderer and input device. */
public record Chart(List<ChartNote> notes, long length, long samplesPerBeat) {
    public Chart {
        notes = List.copyOf(notes);
        if (length <= 0 || samplesPerBeat <= 0) throw new IllegalArgumentException("Invalid chart clock");
        long previous = -1;
        for (ChartNote note : notes) {
            if (note.onset() <= previous || note.end() > length) throw new IllegalArgumentException("Unordered chart");
            previous = note.onset();
        }
    }
}
