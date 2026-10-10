package mutators;

import com.openggf.game.patch.LogicalRom;
import com.openggf.game.mutators.MonitorContent;
import com.openggf.mods.mutators.*;
import java.util.EnumSet;
import java.util.Set;

/** The example owns native-game metadata; shared engine policy consumers use semantic values. */
public final class NativeLabProfile implements MutatorSupportProfile {
    private final String game;
    private final java.util.function.BooleanSupplier donorActive;
    public NativeLabProfile(String game) { this(game, () -> false); }
    public NativeLabProfile(String game, java.util.function.BooleanSupplier donorActive) {
        if (!Set.of("s1", "s2", "s3k").contains(game)) throw new IllegalArgumentException("Native Lab requires a stock game");
        this.game = game;
        this.donorActive = java.util.Objects.requireNonNull(donorActive);
    }
    public String game() { return game; }
    public String patchId() { return "example-mutators:lab" + (game.equals("s2") ? "" : "-" + game); }
    public LogicalRom rom() { return switch (game) { case "s1" -> LogicalRom.S1; case "s2" -> LogicalRom.S2; default -> LogicalRom.S3K; }; }
    public String gameTitle() { return switch (game) { case "s1" -> "Sonic 1"; case "s2" -> "Sonic 2"; default -> "Sonic 3 & Knuckles"; }; }
    @Override public String startLabel() {
        return switch (game) { case "s1" -> "Start Green Hill"; case "s2" -> "Start Emerald Hill"; default -> "Start Angel Island"; };
    }
    @Override public String locationLabel(int zone, int act) {
        // Progression remains native; this label must never pretend later acts are the opening zone.
        return gameTitle() + " / " + (zone == 0 ? switch (game) {
            case "s1" -> "Green Hill"; case "s2" -> "Emerald Hill"; default -> "Angel Island";
        } : "Zone " + (zone + 1)) + " " + (act + 1);
    }
    @Override public Set<MutatorCapability> capabilities(int zone, int act) {
        if (donorActive.getAsBoolean()) return Set.of();
        var available = EnumSet.allOf(MutatorCapability.class);
        if (!game.equals("s3k")) available.remove(MutatorCapability.NO_BONUS_STAGES);
        return Set.copyOf(available);
    }
    @Override public boolean supportsPlayer(String key, boolean leader) {
        return Set.of("sonic", "tails", "knuckles").contains(key);
    }
    @Override public boolean supportsPlayer(MutatorCapability capability, String key, boolean leader) {
        return switch (capability) {
            case BIG_HEAD, DRY_SONIC_GRAVITY -> key.equals("sonic");
            case PLAYER_STEALTH -> supportsPlayer(key, leader);
            default -> false;
        };
    }
    @Override public String optionUnavailableReason(String localId, String optionId) {
        if (localId.equals("no-bonus-stages") && !game.equals("s3k"))
            return gameTitle()+" has no bonus stages. This switch applies to Sonic 3 & Knuckles.";
        if (!localId.equals("no-powerups") || optionId.isEmpty()) return "";
        MonitorContent content;
        try { content = MonitorContent.valueOf(optionId.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { return "Unknown native monitor type."; }
        return monitorContents().contains(content) ? "" : "This monitor type is not present in " + gameTitle() + ".";
    }
    public Set<MonitorContent> monitorContents() {
        return switch (game) {
            case "s1" -> Set.of(MonitorContent.STATIC, MonitorContent.EGGMAN, MonitorContent.LIFE,
                    MonitorContent.SPEED_SHOES, MonitorContent.BASIC_SHIELD, MonitorContent.INVINCIBILITY,
                    MonitorContent.RINGS, MonitorContent.S_MONITOR, MonitorContent.GOGGLES, MonitorContent.BROKEN_SHELL);
            case "s2" -> Set.of(MonitorContent.STATIC, MonitorContent.LIFE, MonitorContent.EGGMAN,
                    MonitorContent.RINGS, MonitorContent.SPEED_SHOES, MonitorContent.BASIC_SHIELD,
                    MonitorContent.INVINCIBILITY, MonitorContent.TELEPORT, MonitorContent.RANDOM, MonitorContent.BROKEN_SHELL);
            default -> Set.of(MonitorContent.EGGMAN, MonitorContent.LIFE, MonitorContent.RINGS,
                    MonitorContent.SPEED_SHOES, MonitorContent.FIRE_SHIELD, MonitorContent.LIGHTNING_SHIELD,
                    MonitorContent.BUBBLE_SHIELD, MonitorContent.INVINCIBILITY, MonitorContent.SUPER);
        };
    }
    /** Native ROM sound IDs work with the title's cached sources before gameplay profile binding. */
    public int cueId(MutatorConfigurationScreen.Cue cue) {
        // Owning enums: Sonic1Sfx SWITCH/RING/SPRING/BREAK_ITEM;
        // Sonic2Sfx BLIP/RING_RIGHT/SPINDASH_RELEASE/ERROR;
        // Sonic3kSfx SWITCH/RING_RIGHT/DASH/ERROR. No synthetic/fallback WAV assets.
        return switch (game) {
            case "s1" -> switch (cue) { case NAVIGATE -> 0xCD; case CONFIRM -> 0xB5; case START -> 0xCC; case ERROR -> 0xC1; };
            case "s2" -> switch (cue) { case NAVIGATE -> 0xCD; case CONFIRM -> 0xB5; case START -> 0xBC; case ERROR -> 0xED; };
            default -> switch (cue) { case NAVIGATE -> 0x5B; case CONFIRM -> 0x33; case START -> 0xB6; case ERROR -> 0xB2; };
        };
    }
}
