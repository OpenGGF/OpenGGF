package starpost.people.cast;

/** Sonic 1 songs the heart events play (sound driver ids, bgm_* in the Sonic 1 disassembly). */
final class Tunes {
    static final String S1 = "s1";
    static final int SYZ = 0x85;
    static final int SBZ = 0x86;
    static final int SLZ = 0x84;
    static final int TITLE = 0x8A;
    static final int ENDING = 0x8B;
    static final int BOSS = 0x8C;
    static final int CONTINUE = 0x90;
    static final int EMERALD = 0x93;

    // Weekdays (Calendar.weekday(): 0 Monday ... 6 Sunday).
    static final int MON = 0;
    static final int TUE = 1;
    static final int WED = 2;
    static final int THU = 3;
    static final int FRI = 4;
    static final int SAT = 5;
    static final int SUN = 6;

    private Tunes() {
    }
}
