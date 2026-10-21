package com.openggf.game;

/**
 * Immutable per-character physics constants.
 * All values are in subpixels (256 subpixels = 1 pixel).
 *
 * <p>Replaces the role of {@code defineSpeeds()} as the authoritative source
 * for character physics when a GameModule is active.
 */
@ModApi
public record PhysicsProfile(
        short runAccel,
        short runDecel,
        short friction,
        short max,
        short jump,
        short slopeRunning,
        short slopeRollingUp,
        short slopeRollingDown,
        short rollDecel,
        short minStartRollSpeed,
        short minRollSpeed,
        short maxRoll,
        short rollHeight,
        short runHeight,
        short standXRadius,
        short standYRadius,
        short rollXRadius,
        short rollYRadius,
        // Tails' S2/S3K balance routine has only one facing-edge balance state:
        // object and terrain edge branches immediately face toward the edge
        // (s2.asm:39733-39743; sonic3k.asm:27882-27899). Sonic/Knuckles keep
        // the four-state facing-away balance handling.
        boolean singleFacingBalance,
        // On-object balance edge threshold used by S2/S3K extended-balance mode.
        // ROM: s2.asm:36287-36296 (Sonic, #2), s2.asm:39361-39368 (Tails, #4),
        // sonic3k.asm:22500 (Sonic, #2), sonic3k.asm:27865 (Tails, #4),
        // sonic3k.asm:31850 (Knuckles, #2). Unused in S1 (single-state balance
        // uses a hard-coded #4; see s1disasm/_incObj/01 Sonic.asm:392).
        short onObjectBalanceShift
) {
    /** Copies this profile while changing only the running speed cap. */
    public PhysicsProfile withMax(int speed) { return toBuilder().max(speed).build(); }

    /** Starts a grouped, range-checked edit without copying positional record components. */
    public Builder toBuilder() { return new Builder(this); }

    /** Starts from the standard Sonic 2 shape and tuning. */
    public static Builder builder() { return SONIC_2_SONIC.toBuilder(); }

    @ModApi
    public static final class Builder {
        private short accel, decel, friction, max, jump;
        private short slope, slopeUp, slopeDown, rollDecel, minStart, minRoll, maxRoll;
        private short rollHeight, runHeight, standX, standY, rollX, rollY, balanceShift;
        private boolean singleFacing;
        private Builder(PhysicsProfile p) {
            accel=p.runAccel(); decel=p.runDecel(); friction=p.friction(); max=p.max(); jump=p.jump();
            slope=p.slopeRunning(); slopeUp=p.slopeRollingUp(); slopeDown=p.slopeRollingDown();
            rollDecel=p.rollDecel(); minStart=p.minStartRollSpeed(); minRoll=p.minRollSpeed(); maxRoll=p.maxRoll();
            rollHeight=p.rollHeight(); runHeight=p.runHeight(); standX=p.standXRadius(); standY=p.standYRadius();
            rollX=p.rollXRadius(); rollY=p.rollYRadius(); balanceShift=p.onObjectBalanceShift(); singleFacing=p.singleFacingBalance();
        }
        public Builder movement(int acceleration, int deceleration, int friction, int maxSpeed, int jumpSpeed) {
            short a=value(acceleration), d=value(deceleration), f=value(friction), m=value(maxSpeed), j=value(jumpSpeed);
            this.accel=a; this.decel=d; this.friction=f; this.max=m; this.jump=j; return this;
        }
        public Builder max(int speed) { max=value(speed); return this; }
        public Builder slopes(int running, int rollingUp, int rollingDown) {
            short a=value(running), b=value(rollingUp), c=value(rollingDown);
            slope=a; slopeUp=b; slopeDown=c; return this;
        }
        public Builder rolling(int deceleration, int startSpeed, int stopSpeed, int maxSpeed) {
            short a=value(deceleration), b=value(startSpeed), c=value(stopSpeed), d=value(maxSpeed);
            rollDecel=a; minStart=b; minRoll=c; maxRoll=d; return this;
        }
        public Builder shape(int standingWidthRadius, int standingHeightRadius,
                             int rollingWidthRadius, int rollingHeightRadius) {
            short sx=radius(standingWidthRadius), sy=radius(standingHeightRadius);
            short rx=radius(rollingWidthRadius), ry=radius(rollingHeightRadius);
            standX=sx; standY=sy; rollX=rx; rollY=ry; runHeight=(short)(2*sy); rollHeight=(short)(2*ry); return this;
        }
        public Builder balance(boolean singleFacing, int onObjectShift) {
            balanceShift=radius(onObjectShift); this.singleFacing=singleFacing; return this;
        }
        public PhysicsProfile build() {
            return new PhysicsProfile(accel,decel,friction,max,jump,slope,slopeUp,slopeDown,
                    rollDecel,minStart,minRoll,maxRoll,rollHeight,runHeight,standX,standY,rollX,rollY,
                    singleFacing,balanceShift);
        }
        private static short value(int value) {
            if (value < 0 || value > Short.MAX_VALUE) throw new IllegalArgumentException("Physics value outside 0..32767: " + value);
            return (short)value;
        }
        private static short radius(int value) {
            if (value < 1 || value > 127) throw new IllegalArgumentException("Radius outside 1..127: " + value);
            return (short)value;
        }
    }

    // Sonic 2 Sonic (also identical for S1 Sonic; S3K canonical reset profile)
    // S3K uses this as the "reset" profile after water/shoes events (sonic3k.asm:22289)
    public static final PhysicsProfile SONIC_2_SONIC = new PhysicsProfile(
            (short) 12,    // runAccel (0x0C)
            (short) 128,   // runDecel (0x80)
            (short) 12,    // friction (0x0C)
            (short) 1536,  // max (0x600)
            (short) 1664,  // jump (0x680)
            (short) 32,    // slopeRunning (0x20)
            (short) 20,    // slopeRollingUp (0x14)
            (short) 80,    // slopeRollingDown (0x50)
            (short) 32,    // rollDecel (0x20)
            (short) 128,   // minStartRollSpeed (0x80)
            (short) 128,   // minRollSpeed (0x80)
            (short) 4096,  // maxRoll (0x1000)
            (short) 28,    // rollHeight
            (short) 38,    // runHeight
            (short) 9,     // standXRadius
            (short) 19,    // standYRadius (0x13)
            (short) 7,     // rollXRadius
            (short) 14,    // rollYRadius
            false,          // singleFacingBalance
            (short) 2      // onObjectBalanceShift (s2.asm:36287, sonic3k.asm:22500)
    );

    // Sonic 2 Tails (differs in runHeight, standYRadius; minStartRollSpeed matches Sonic's $80)
    // ROM Tails_Roll (s2.asm:39962): cmpi.w #$80,d0 — identical threshold to Sonic_Roll (s2.asm:36983)
    public static final PhysicsProfile SONIC_2_TAILS = new PhysicsProfile(
            (short) 12,    // runAccel
            (short) 128,   // runDecel
            (short) 12,    // friction
            (short) 1536,  // max
            (short) 1664,  // jump
            (short) 32,    // slopeRunning
            (short) 20,    // slopeRollingUp
            (short) 80,    // slopeRollingDown
            (short) 32,    // rollDecel
            (short) 128,   // minStartRollSpeed (0x80, same as Sonic — s2.asm:39962)
            (short) 128,   // minRollSpeed
            (short) 4096,  // maxRoll
            (short) 28,    // rollHeight
            (short) 30,    // runHeight (Tails is shorter: 2 * 15)
            (short) 9,     // standXRadius
            (short) 15,    // standYRadius (0x0F, shorter than Sonic)
            (short) 7,     // rollXRadius
            (short) 14,    // rollYRadius
            true,           // singleFacingBalance (s2.asm:39733-39743)
            (short) 4      // onObjectBalanceShift (s2.asm:39361, sonic3k.asm:27865)
    );

    // S3K Knuckles (lower jump than Sonic: $600 vs $680)
    // ROM: Knux_Jump (sonic3k.asm:32494) move.w #$600,d2
    // All other values identical to Sonic's canonical profile.
    public static final PhysicsProfile SONIC_3K_KNUCKLES = new PhysicsProfile(
            (short) 12,    // runAccel (0x0C)
            (short) 128,   // runDecel (0x80)
            (short) 12,    // friction (0x0C)
            (short) 1536,  // max (0x600)
            (short) 1536,  // jump (0x600) — lower than Sonic's 0x680
            (short) 32,    // slopeRunning
            (short) 20,    // slopeRollingUp
            (short) 80,    // slopeRollingDown
            (short) 32,    // rollDecel
            (short) 128,   // minStartRollSpeed
            (short) 128,   // minRollSpeed
            (short) 4096,  // maxRoll
            (short) 28,    // rollHeight
            (short) 38,    // runHeight
            (short) 9,     // standXRadius
            (short) 19,    // standYRadius (0x13)
            (short) 7,     // rollXRadius
            (short) 14,    // rollYRadius
            false,          // singleFacingBalance
            (short) 2      // onObjectBalanceShift (sonic3k.asm:31850)
    );

    // S3K Super Sonic (higher speeds: max=0xA00, accel=0x30, decel=0x100)
    public static final PhysicsProfile SONIC_3K_SUPER_SONIC = new PhysicsProfile(
            (short) 0x30,  // runAccel
            (short) 0x100, // runDecel
            (short) 0x30,  // friction (same as accel for Super)
            (short) 0xA00, // max
            (short) 0x800, // jump (ROM: Super Sonic override)
            (short) 32,    // slopeRunning
            (short) 20,    // slopeRollingUp
            (short) 80,    // slopeRollingDown
            (short) 32,    // rollDecel
            (short) 128,   // minStartRollSpeed
            (short) 128,   // minRollSpeed
            (short) 4096,  // maxRoll
            (short) 28,    // rollHeight
            (short) 38,    // runHeight
            (short) 9,     // standXRadius
            (short) 19,    // standYRadius
            (short) 7,     // rollXRadius
            (short) 14,    // rollYRadius
            false,          // singleFacingBalance
            (short) 2      // onObjectBalanceShift (SuperSonic_Balance shares Sonic shift)
    );

    // S3K Sonic Competition mode — Character_Speeds table (sonic3k.asm:202403, loaded at line 21467)
    // Only used by Sonic2P_Index (Competition mode init, line 21457), NOT normal single-player.
    // Differs from canonical in accel ($10 vs $C), decel ($20 vs $80).
    public static final PhysicsProfile SONIC_3K_SONIC_INIT = new PhysicsProfile(
            (short) 0x10,  // runAccel (Character_Speeds word 2)
            (short) 0x20,  // runDecel (Character_Speeds word 3)
            (short) 12,    // friction (0x0C, not set by Character_Speeds — keeps canonical)
            (short) 0x600, // max (Character_Speeds word 1)
            (short) 1664,  // jump (0x680)
            (short) 32,    // slopeRunning
            (short) 20,    // slopeRollingUp
            (short) 80,    // slopeRollingDown
            (short) 32,    // rollDecel
            (short) 128,   // minStartRollSpeed
            (short) 128,   // minRollSpeed
            (short) 4096,  // maxRoll
            (short) 28,    // rollHeight
            (short) 38,    // runHeight
            (short) 9,     // standXRadius
            (short) 19,    // standYRadius
            (short) 7,     // rollXRadius
            (short) 14,    // rollYRadius
            false,          // singleFacingBalance
            (short) 2      // onObjectBalanceShift
    );

    // S3K Tails Competition mode — Character_Speeds table (sonic3k.asm:202405)
    // Only used by Competition mode. max=$4C0, accel=$1C, decel=$70.
    public static final PhysicsProfile SONIC_3K_TAILS_INIT = new PhysicsProfile(
            (short) 0x1C,  // runAccel (Character_Speeds word 2)
            (short) 0x70,  // runDecel (Character_Speeds word 3)
            (short) 12,    // friction (0x0C, not set by Character_Speeds)
            (short) 0x4C0, // max (Character_Speeds word 1)
            (short) 1664,  // jump
            (short) 32,    // slopeRunning
            (short) 20,    // slopeRollingUp
            (short) 80,    // slopeRollingDown
            (short) 32,    // rollDecel
            (short) 264,   // minStartRollSpeed (Tails-specific)
            (short) 128,   // minRollSpeed
            (short) 4096,  // maxRoll
            (short) 28,    // rollHeight
            (short) 30,    // runHeight (Tails shorter)
            (short) 9,     // standXRadius
            (short) 15,    // standYRadius (0x0F, shorter than Sonic)
            (short) 7,     // rollXRadius
            (short) 14,    // rollYRadius
            true,           // singleFacingBalance (sonic3k.asm:27882-27899)
            (short) 4      // onObjectBalanceShift
    );

    // S3K Super Tails (sonic3k.asm:26365-26367: max=$800, accel=$18, decel=$C0)
    public static final PhysicsProfile SONIC_3K_SUPER_TAILS = new PhysicsProfile(
            (short) 0x18,  // runAccel
            (short) 0xC0,  // runDecel
            (short) 0x18,  // friction (same as accel for Super)
            (short) 0x800, // max
            (short) 0x680, // jump (Tails_Jump has no Super override)
            (short) 32,    // slopeRunning
            (short) 20,    // slopeRollingUp
            (short) 80,    // slopeRollingDown
            (short) 32,    // rollDecel
            (short) 264,   // minStartRollSpeed (Tails-specific)
            (short) 128,   // minRollSpeed
            (short) 4096,  // maxRoll
            (short) 28,    // rollHeight
            (short) 30,    // runHeight (Tails shorter)
            (short) 9,     // standXRadius
            (short) 15,    // standYRadius (Tails)
            (short) 7,     // rollXRadius
            (short) 14,    // rollYRadius
            true,           // singleFacingBalance (Tails routine)
            (short) 4      // onObjectBalanceShift
    );

    // S3K Super/Hyper Knuckles (sonic3k.asm:32651-32653: max=$800, accel=$18, decel=$C0)
    public static final PhysicsProfile SONIC_3K_SUPER_KNUCKLES = new PhysicsProfile(
            (short) 0x18,  // runAccel
            (short) 0xC0,  // runDecel
            (short) 0x18,  // friction (same as accel for Super)
            (short) 0x800, // max
            (short) 0x600, // jump (Knux_Jump has no Super override)
            (short) 32,    // slopeRunning
            (short) 20,    // slopeRollingUp
            (short) 80,    // slopeRollingDown
            (short) 32,    // rollDecel
            (short) 128,   // minStartRollSpeed
            (short) 128,   // minRollSpeed
            (short) 4096,  // maxRoll
            (short) 28,    // rollHeight
            (short) 38,    // runHeight
            (short) 9,     // standXRadius
            (short) 19,    // standYRadius
            (short) 7,     // rollXRadius
            (short) 14,    // rollYRadius
            false,         // singleFacingBalance
            (short) 2      // onObjectBalanceShift
    );

    // S2 Super Sonic (same values as S3K: max=0xA00, accel=0x30, decel=0x100)
    // ROM ref: s2.asm:37015 — move.w #$800,d2
    public static final PhysicsProfile SONIC_2_SUPER_SONIC = new PhysicsProfile(
            (short) 0x30,  // runAccel
            (short) 0x100, // runDecel
            (short) 0x30,  // friction (same as accel for Super)
            (short) 0xA00, // max
            (short) 0x800, // jump (2048 — Super Sonic higher jump)
            (short) 32,    // slopeRunning
            (short) 20,    // slopeRollingUp
            (short) 80,    // slopeRollingDown
            (short) 32,    // rollDecel
            (short) 128,   // minStartRollSpeed
            (short) 128,   // minRollSpeed
            (short) 4096,  // maxRoll
            (short) 28,    // rollHeight
            (short) 38,    // runHeight
            (short) 9,     // standXRadius
            (short) 19,    // standYRadius
            (short) 7,     // rollXRadius
            (short) 14,    // rollYRadius
            false,          // singleFacingBalance
            (short) 2      // onObjectBalanceShift (SuperSonic_Balance shares Sonic shift)
    );
}
