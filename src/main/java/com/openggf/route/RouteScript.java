package com.openggf.route;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.recording.RecordedFrameInput;

import java.util.List;

/** Tape ↔ route script text through the existing {@link InputScriptCompiler} grammar. */
final class RouteScript {
    private RouteScript() {
    }

    /** Run-length rows with explicit physical letters on both lanes. */
    static String write(InputTape tape, List<String> headerComments) {
        var out = new StringBuilder();
        for (String comment : headerComments) {
            for (String line : comment.split("\n", -1)) out.append("# ").append(line).append('\n');
        }
        int i = 0;
        while (i < tape.size()) {
            PadRow row = tape.row(i);
            int run = 1;
            while (i + run < tape.size() && tape.row(i + run).equals(row)) run++;
            out.append(run).append(' ').append(PadRow.letters(row.p1()));
            if (row.p2() != 0) out.append(" / ").append(PadRow.letters(row.p2()));
            out.append('\n');
            i += run;
        }
        return out.toString();
    }

    static InputTape read(String script) {
        var builder = new InputTape.Builder();
        for (RecordedFrameInput frame : InputScriptCompiler.compile(script)) {
            builder.add(PadRow.fromEngine(new Bk2FrameInput(frame.frame(), frame.p1InputMask(), frame.p1ActionMask(),
                    frame.p1Start(), frame.p2InputMask(), frame.p2ActionMask(), frame.p2Start(), "")));
        }
        return builder.build();
    }
}
