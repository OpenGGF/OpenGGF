package com.openggf.game.sonic2.competition.view;

import com.openggf.data.Rom;
import com.openggf.game.ScrollHandlerProvider;
import com.openggf.game.sonic2.competition.Sonic2CompetitionLevelProfile;
import com.openggf.game.sonic2.competition.Sonic2CompetitionSession;
import com.openggf.game.sonic2.competition.scroll.Sonic2CompetitionScroll;
import com.openggf.level.scroll.ZoneScrollHandler;
import com.openggf.level.scroll.ScrollOutputRebuilder;
import java.io.IOException;
import java.util.Objects;

/** Read-only generic scroll output; only the native completed camera pass deforms A1's owner. */
public final class Sonic2CompetitionScrollSourceAdapter implements ScrollHandlerProvider {
    private final Sonic2CompetitionScroll owner;
    private final ZoneConstants constants;
    private final Handler handler = new Handler();
    private Sonic2CompetitionLevelProfile profile;
    public Sonic2CompetitionScrollSourceAdapter(Rom rom, ZoneConstants constants) throws IOException {
        owner = new Sonic2CompetitionScroll(rom); this.constants = Objects.requireNonNull(constants);
    }
    @Override public void load(Rom rom) { }
    @Override public ZoneConstants getZoneConstants() { return constants; }
    @Override public ZoneScrollHandler getHandler(int zone) { return handler; }
    public Sonic2CompetitionScroll owner() { return owner; }
    public void initialize(Sonic2CompetitionLevelProfile profile, Sonic2CompetitionSession session) {
        this.profile = Objects.requireNonNull(profile);
        int x = session.camera(0).getX() & 65535, y = session.camera(0).getY() & 65535;
        int bgY = switch (profile.romZone()) {
            case 0, 12 -> 0;
            case 11 -> y / (profile.act() == 0 ? 3 : 6) - (profile.act() == 0 ? 320 : 16);
            default -> throw new IllegalArgumentException("Unsupported native competition zone");
        };
        // InitCameraValues (s2.asm:14882-14902), InitCam_EHZ (s2.asm:14926-14940),
        // InitCam_MCZ (s2.asm:15025-15042), InitCam_CNZ (s2.asm:15044-15049).
        int bg2Y = profile.romZone() == 0 ? 0 : y << 16;
        var first = new Sonic2CompetitionScroll.BackgroundBank(0, bgY << 16, x << 16, bg2Y, x << 16, 0);
        var second = new Sonic2CompetitionScroll.BackgroundBank(profile.romZone() == 12 ? x << 16 : 0,
                bgY << 16, x << 16, bg2Y, x << 16, 0);
        owner.initializeLevel(zone(), first, second);
    }
    /**
     * LoadLevelBlocks_2P (s2.asm:86878-86898), shipped fixBugs=0, writes beyond
     * Block_Table into TempArray_LayerDef (s2.constants.asm:1088-1093). Translate
     * only that actual loaded span into the deformation owner's semantic words.
     * Level (s2.asm:4930-4941) performs this AFTER the initial deformation/clears.
     */
    public void applyLoadedLayerDefinition(com.openggf.game.sonic2.competition.Sonic2CompetitionNativeArt art) {
        int first = 0x9000 + (short) art.profile().animationPatch().destinationByteOffset();
        int end = first + art.animationCopiedWords() * 2;
        if (end <= 0xA800 || first >= 0xA830) return;
        var state = owner.capture();
        short[] scratch = state.scratch();
        for (int word = 0; word < scratch.length; word++) {
            int address = 0xA800 + word * 2;
            if (address >= first && address + 2 <= end) scratch[word] = (short) art.readWord(address);
        }
        // A1's immutable load/restore DTO is the published state-seeding seam;
        // no parallel scratch owner or mutable RAM image is introduced here.
        owner.restore(new Sonic2CompetitionScroll.State(state.p1(), state.p2(), scratch,
                state.horizontal(), state.v1(), state.v2()));
    }
    public void completeCameraPass(Sonic2CompetitionSession session, int vintRunCount) {
        if (profile == null) return;
        owner.update(zone(), profile.act(), camera(session, 0), camera(session, 1), vintRunCount);
    }
    private Sonic2CompetitionScroll.Zone zone() {
        return switch (profile.romZone()) {
            case 0 -> Sonic2CompetitionScroll.Zone.EHZ;
            case 11 -> Sonic2CompetitionScroll.Zone.MCZ;
            case 12 -> Sonic2CompetitionScroll.Zone.CNZ;
            default -> throw new IllegalStateException("Unsupported native zone");
        };
    }
    private static Sonic2CompetitionScroll.Camera camera(Sonic2CompetitionSession session, int slot) {
        return new Sonic2CompetitionScroll.Camera(session.camera(slot).getX(), session.camera(slot).getY());
    }
    private final class Handler implements ZoneScrollHandler, ScrollOutputRebuilder {
        public void update(int[] output, int x, int y, int frame, int act) { copy(output); }
        public void rebuildOutput(int[] output, int x, int y, int frame, int act) { copy(output); }
        private void copy(int[] output) { System.arraycopy(owner.view(0).horizontalScroll(), 0, output, 0, 224); }
        public short getVscrollFactorBG() { return (short) (owner.view(0).backgroundVScroll() >> 1); }
        public short getVscrollFactorFG() { return (short) (owner.view(0).foregroundVScroll() >> 1); }
        public int getMinScrollOffset() { return 0; }
        public int getMaxScrollOffset() { return 0; }
        public Object captureRewindState() { return owner.capture(); }
        public void restoreRewindState(Object state) {
            if (state != null) owner.restore((Sonic2CompetitionScroll.State) state);
        }
    }
}
