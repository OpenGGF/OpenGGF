package com.openggf.game.sonic2.competition.view;

import com.openggf.game.rewind.RewindSnapshottable;
import java.util.List;

/** Last completed BuildSprites output. VInt publication never observes an in-progress builder. */
public final class Sonic2CompetitionSpritePages implements RewindSnapshottable<Sonic2CompetitionSpritePages.Pair> {
    public record Pair(Sonic2CompetitionSatBuilder.Page first, Sonic2CompetitionSatBuilder.Page second) { }
    private final Sonic2CompetitionSatBuilder builder;
    private Pair completed = new Pair(Sonic2CompetitionSatBuilder.Page.empty(0), Sonic2CompetitionSatBuilder.Page.empty(1));
    public Sonic2CompetitionSpritePages(Sonic2CompetitionSatBuilder builder) { this.builder = builder; }
    public void prepare(Sonic2CompetitionDisplayInputs.Build input) {
        var first = builder.buildPlayer1(input);
        var second = builder.buildPlayer2(first, input);
        completed = new Pair(first, second);
    }
    public Pair completed() { return completed; }
    public List<Sonic2CompetitionDisplayInputs.Visibility> visibility() { return completed.second().visibility(); }
    @Override public String key() { return "sonic2.competition.sprite-pages"; }
    @Override public Pair capture() { return completed; }
    @Override public void restore(Pair state) { completed = java.util.Objects.requireNonNull(state); }
}
