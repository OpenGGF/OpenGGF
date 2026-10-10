package typinghog.rails;

import java.util.List;

/** Behaviour checks for the engine-free rail format; run by the engine's TestTypingHedgehogRailFormat. */
public final class RailChecks {
    public static void main(String[] args) {
        retiredFormatRejected(); forwardRails(); forwardRejects(); qualification();
        System.out.println("4 rail format checks passed");
    }

    private static void eq(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) throw new AssertionError(expected + " != " + actual);
    }

    private static void yes(boolean value, String what) { if (!value) throw new AssertionError(what); }

    private static void rejects(String text, String why) {
        try {
            Rail.parse(text);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("accepted a rail with " + why);
    }

    /** A spindash countdown's 180 rows: DOWN, a DOWN+JUMP pulse every sixth row from row 2. */
    static String spindashCountdown() {
        StringBuilder out = new StringBuilder("2:2");
        for (int k = 0; k < 29; k++) out.append(",1:12,5:2");
        return out.append(",1:12,3:2").toString();
    }

    static final String SHA1 = "8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9";

    /** A forward rail: 60 entry rows, the countdown, then four 300-row legs with a stand after each. */
    static String forward(String launch, String countdown, String extra, int stands) {
        StringBuilder program = new StringBuilder("60:0," + countdown);
        StringBuilder events = new StringBuilder();
        int row = 60 + 180;
        for (int i = 1; i <= stands; i++) {
            program.append(",300:8,2:0");
            row += 300;
            events.append("stand ").append(i).append(' ').append(row).append(" 2 ")
                    .append(i == 1 ? "BuzzerBadnikInstance@18/L12 CoconutsBadnikInstance@19/L20" : "-").append('\n');
            row += 2;
        }
        program.append(",100:8");
        row += 100;
        return "format 2\npolicy forward-v1\ncombat live-restore-v1\ncourse regular\nlaunch " + launch
                + "\nprofile intros=skip width=320\ngame s2\nzone 0\nact 1\nteam sonic,tails\nrom 7B905383\nrom-sha1 " + SHA1
                + "\nengine test\ncountdown 60\n" + events + "endpoint boss-arena " + row + "\nvalidated " + row
                + "\n" + extra + "program " + program + "\n";
    }

    /** The recording-derived format 1 rails are retired: only forward courses play. */
    public static void retiredFormatRejected() {
        rejects("game s1\nzone 0\nact 2\nvalidated 50\nprogram 20:8,30:18\nstand 10\nstand 30\nboss 50\n",
                "the retired format 1 (no format line)");
        rejects("format 1\ngame s1\nzone 0\nact 2\nprogram 50:8\n", "format 1");
    }

    public static void forwardRails() {
        Rail rail = Rail.parse(forward("native-spindash", spindashCountdown(), "", 4));
        var plan = rail.plan();
        eq(60, plan.countdownRow());
        eq(240, plan.releaseRow());
        eq(4, plan.stands().size());
        eq(540, plan.stands().get(0).row());
        eq(List.of("BuzzerBadnikInstance/L12", "CoconutsBadnikInstance/L20"),
                plan.stands().get(0).pool().stream().map(Rail.Body::key).toList());
        eq(18, plan.stands().get(0).pool().get(0).slot());
        eq("skip", plan.profile().get("intros"));
        eq(List.of(540, 842, 1144, 1446), plan.stands().stream().map(Rail.Stand::row).toList());
        eq(rail.validated(), plan.endpoint().row());
        eq(0x12, rail.maskAt(62));
        eq(0x02, rail.maskAt(63));
        eq(0x08, rail.maskAt(240));
        // Sonic 1 crouches through its countdown.
        eq("native-run-roll", Rail.parse(forward("native-run-roll", "180:2", "", 4)).plan().launch());
    }

    public static void forwardRejects() {
        String good = forward("native-spindash", spindashCountdown(), "", 4);
        rejects(good.replace("policy forward-v1", "policy wander"), "an unknown policy");
        rejects(good.replace("combat live-restore-v1", "combat freeze"), "an unknown combat mode");
        rejects(good.replace("rom-sha1 " + SHA1 + "\n", ""), "no ROM SHA-1");
        rejects(good.replace("endpoint boss-arena", "endpoint somewhere"), "an unknown endpoint");
        rejects(good.replace("countdown 60", "countdown 50"), "a countdown over entry rows that do not crouch");
        rejects(forward("native-run-roll", spindashCountdown(), "", 4), "charge pulses under a run-roll launch");
        rejects(good.replace(",300:8,2:0,300:8", ",300:2,2:0,300:8"), "a release row without RIGHT");
        rejects(good.replace("stand 2 842", "stand 1 842"), "a duplicate stand id");
        rejects(good.replace("stand 2 842 2", "stand 2 841 2"), "a cleanup row that moves");
        rejects(good.replace("stand 1 540", "stand 1 200"), "a stand inside the countdown");
        rejects(good.replace("validated", "boss 9999\nvalidated"), "a retired format-1 boss row");
        rejects(good.replace("countdown 60\n", ""), "no countdown");
        rejects(good.replace("program 60:0", "program 60:20"), "a mask outside the pad bits");
        String length = good.substring(good.indexOf("validated ") + 10, good.indexOf('\n', good.indexOf("validated ")));
        rejects(good.replace("validated " + length, "validated " + (Integer.parseInt(length) - 1)),
                "a validated length short of the program");
        rejects(good.replace("format 2", "format 3"), "an unsupported format");
    }

    public static void qualification() {
        yes(Rail.parse(forward("native-spindash", spindashCountdown(), "", 4)).plan().qualifies(), "four regular fights qualify");
        yes(Rail.parse(forward("native-spindash", spindashCountdown(), "", 5)).plan().qualifies(), "five regular fights qualify");
        yes(!Rail.parse(forward("native-spindash", spindashCountdown(), "", 3)).plan().qualifies(), "three fights do not");
        yes(!Rail.parse(forward("native-spindash", spindashCountdown(), "", 4).replace("course regular", "course pilot"))
                .plan().qualifies(), "a pilot course never qualifies");
    }
}
