package com.openggf.game.ghost;

/** .ggfghost header (main spec §3). No ROM asset/content bytes — metadata only. */
public record GhostHeader(int formatVersion, String gameId, int zone, int act, String character,
                          String displayName, int firstInputFrame, int finishFrame,
                          int[] splitFrames, byte[] inputRecordingHash) {
    public GhostHeader {
        splitFrames = splitFrames.clone();           // defensive: header is immutable
        inputRecordingHash = inputRecordingHash.clone();
    }

    @Override
    public int[] splitFrames() {
        return splitFrames.clone();
    }

    @Override
    public byte[] inputRecordingHash() {
        return inputRecordingHash.clone();
    }

    public int finalTimeFrames() {
        return finishFrame - firstInputFrame;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GhostHeader g && formatVersion == g.formatVersion
                && gameId.equals(g.gameId) && zone == g.zone && act == g.act
                && character.equals(g.character) && displayName.equals(g.displayName)
                && firstInputFrame == g.firstInputFrame && finishFrame == g.finishFrame
                && java.util.Arrays.equals(splitFrames, g.splitFrames)
                && java.util.Arrays.equals(inputRecordingHash, g.inputRecordingHash);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(gameId, zone, act, character, finishFrame);
    }
}
