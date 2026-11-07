package openggf.timeattack.ghost;

/** .ggfghost header (main spec §3). No ROM asset/content bytes — metadata only. */
public record GhostHeader(int formatVersion, String gameId, int zone, int act, String character,
                          String displayName, int firstInputFrame, int finishFrame,
                          int[] splitFrames, byte[] inputRecordingHash,
                          com.openggf.mods.run.TrackKind trackKind,
                          openggf.timeattack.CompetitionRoomSettings.Mode mode,
                          com.openggf.mods.run.MonitorRule monitors, int[] lapFrames) {
    public GhostHeader(int formatVersion, String gameId, int zone, int act, String character,
            String displayName, int firstInputFrame, int finishFrame, int[] splitFrames, byte[] inputRecordingHash) {
        this(formatVersion, gameId, zone, act, character, displayName, firstInputFrame, finishFrame,
                splitFrames, inputRecordingHash, com.openggf.mods.run.TrackKind.ACT,
                openggf.timeattack.CompetitionRoomSettings.Mode.TIME_ATTACK,
                com.openggf.mods.run.MonitorRule.NORMAL, new int[0]);
    }
    @Override public int[] lapFrames() { return lapFrames.clone(); }

    public GhostHeader {
        lapFrames = lapFrames.clone();
        java.util.Objects.requireNonNull(trackKind);
        java.util.Objects.requireNonNull(mode);
        java.util.Objects.requireNonNull(monitors);
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
                && trackKind == g.trackKind && mode == g.mode && monitors == g.monitors
                && java.util.Arrays.equals(lapFrames, g.lapFrames)
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
