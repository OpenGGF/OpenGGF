package com.openggf.game.sonic2.competition.view;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.level.animation.AnimatedPatternManager;
import com.openggf.level.animation.AniPlcParser;
import com.openggf.level.animation.AniPlcScriptState;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import static com.openggf.game.sonic2.Sonic2RomAddresses.address;

/** AniArt_Load's native scripts submit into the world residency, never the level atlas. */
public final class Sonic2CompetitionAnimatedArt implements AnimatedPatternManager,
        RewindSnapshottable<Sonic2CompetitionAnimatedArt.State> {
    private final Sonic2CompetitionArtResidency art;
    private final List<AniPlcScriptState> scripts;
    private final Sonic2CompetitionSlotPictures slots;
    public record Counter(int timer, int nextFrame) { }
    public record State(List<Counter> counters, Sonic2CompetitionSlotPictures.State slots) {
        public State { counters = List.copyOf(counters); }
    }
    public Sonic2CompetitionAnimatedArt(Rom rom, int zone, Sonic2CompetitionArtResidency art,
                                       Sonic2CompetitionSlotPictures slots) throws IOException {
        this.art = Objects.requireNonNull(art); this.slots = slots;
        var reader = RomByteReader.fromRom(rom);
        // PLC_DYNANM (s2.asm:86142-86194), Animated_EHZ (s2.asm:86433-86479),
        // Dynamic_CNZ (s2.asm:86332-86343), Animated_CNZ_2P (s2.asm:86688-86733).
        scripts = switch (zone) {
            case 0 -> AniPlcParser.parseScripts(reader, address(reader, 0x3FF94));
            case 11 -> List.of(); // Dynamic_Null: do not execute an empty S2 script list.
            case 12 -> AniPlcParser.parseScripts(reader, address(reader, 0x40160));
            default -> throw new IllegalArgumentException("Not a competition animation zone: " + zone);
        };
    }
    @Override public void update() {
        // Dynamic_Normal (s2.asm:86351-86410): counters advance on submission,
        // even if the native 18-entry upload buffer rejects a command.
        // LevEvents_CNZ (s2.asm:21511-21513) precedes AniArt_Load in the level loop.
        if (slots != null) slots.update();
        for (var script : scripts) script.tickSubmission(tile ->
                art.queueUpload(script.destinationTileIndex(), script.copyFramePayload(tile)));
        art.fields().refresh();
    }
    @Override public String key() { return "sonic2.competition.animated-art"; }
    @Override public State capture() {
        return new State(scripts.stream().map(s -> new Counter(s.getTimer(), s.getFrameIndex())).toList(),
                slots == null ? null : slots.capture());
    }
    @Override public void restore(State state) {
        if (state.counters().size() != scripts.size() || (slots == null) != (state.slots() == null))
            throw new IllegalArgumentException("Animation snapshot belongs to another script set");
        for (int i=0;i<scripts.size();i++) {
            var c = state.counters().get(i); scripts.get(i).restoreCounters(c.timer(),c.nextFrame());
        }
        if (slots != null) slots.restore(state.slots());
        art.fields().rebuild();
    }
}
