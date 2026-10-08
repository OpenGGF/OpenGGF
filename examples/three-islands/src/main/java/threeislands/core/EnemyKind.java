package threeislands.core;

/**
 * Every foe. Stats come from the zone's tier (see {@link Combatant#enemy}) scaled by the
 * percentages here, so one badnik can appear in several zones. {@code art} names a ROM sprite
 * set the view loads ({@code game:name}, or {@code boss:name} for a multi-part boss built from
 * several ROM sprites); {@code frames} cycles every {@code ticks}. {@code scale} is a percent
 * used for the oversized "rift" bosses.
 */
public enum EnemyKind {
    // ---- South Island (Sonic 1) ----
    MOTOBUG("Motobug", "s1:motobug", "0,1,2", 6, Kinds.WALK, 100, 100, 100, 100, Kinds.NONE, Kinds.NONE,
            Kinds.NONE, Kinds.BASIC, false, 100, "rams"),
    CRABMEAT("Crabmeat", "s1:crabmeat", "0,1,2", 8, Kinds.WALK, 110, 95, 110, 80, Kinds.WATER, Kinds.NONE,
            Kinds.NONE, Kinds.SWEEP, false, 100, "lobs fireballs at"),
    BUZZ_BOMBER("Buzz Bomber", "s1:buzzbomber", "0,1", 2, Kinds.FLY, 80, 105, 80, 130, Kinds.ELEC, Kinds.NONE,
            Kinds.NONE, Kinds.BASIC, false, 100, "fires a stinger at"),
    CHOPPER("Chopper", "s1:chopper", "0,1", 6, Kinds.HOP, 85, 110, 90, 110, Kinds.ELEC, Kinds.WATER,
            Kinds.WATER, Kinds.BASIC, false, 100, "leaps up and bites"),
    NEWTRON("Newtron", "s1:newtron", "1,2,3", 10, Kinds.WALK, 90, 100, 90, 100, Kinds.FIRE, Kinds.NONE,
            Kinds.NONE, Kinds.BASIC, false, 100, "spits a pellet at"),
    CATERKILLER("Caterkiller", "s1:caterkiller", "0,1,2", 8, Kinds.WALK, 105, 95, 100, 90, Kinds.WATER,
            Kinds.FIRE, Kinds.NONE, Kinds.FLURRY, false, 100, "pricks"),
    BASARAN("Basaran", "s1:basaran", "1,2,3", 6, Kinds.FLY, 85, 105, 85, 125, Kinds.ELEC, Kinds.NONE,
            Kinds.NONE, Kinds.BASIC, false, 100, "swoops on"),
    YADRIN("Yadrin", "s1:yadrin", "0,1,2", 8, Kinds.WALK, 115, 100, 120, 80, Kinds.FIRE, Kinds.NONE,
            Kinds.NONE, Kinds.BASIC, false, 100, "charges its spikes into"),
    ROLLER("Roller", "s1:roller", "2,3,4", 4, Kinds.WALK, 100, 110, 110, 120, Kinds.ELEC, Kinds.NONE,
            Kinds.NONE, Kinds.BASIC, false, 100, "rolls into"),
    ORBINAUT_S1("Orbinaut", "s1:orbinaut", "0", 8, Kinds.FLY, 95, 105, 105, 95, Kinds.FIRE, Kinds.NONE,
            Kinds.NONE, Kinds.FLURRY, false, 100, "hurls a spike ball at"),
    BOMB("Bomb", "s1:bomb", "0,1,2", 8, Kinds.WALK, 75, 120, 70, 100, Kinds.WATER, Kinds.FIRE,
            Kinds.FIRE, Kinds.SWEEP, false, 100, "bursts shrapnel over"),

    // ---- West Side Island (Sonic 2) ----
    BUZZER("Buzzer", "s2:buzzer", "0", 8, Kinds.FLY, 85, 105, 85, 125, Kinds.ELEC, Kinds.NONE,
            Kinds.NONE, Kinds.BASIC, false, 100, "fires a needle at"),
    MASHER("Masher", "s2:masher", "0,1", 6, Kinds.HOP, 85, 110, 90, 115, Kinds.ELEC, Kinds.WATER,
            Kinds.WATER, Kinds.BASIC, false, 100, "leaps up and chomps"),
    COCONUTS("Coconuts", "s2:coconuts", "0,1,2", 8, Kinds.WALK, 100, 100, 95, 100, Kinds.FIRE, Kinds.NONE,
            Kinds.NONE, Kinds.SWEEP, false, 100, "pelts coconuts at"),
    SPINY("Spiny", "s2:spiny", "0,1", 8, Kinds.WALK, 105, 100, 110, 85, Kinds.FIRE, Kinds.NONE,
            Kinds.WATER, Kinds.SWEEP, false, 100, "sprays chemical spikes over"),
    GRABBER("Grabber", "s2:grabber", "0,1", 8, Kinds.FLY, 95, 105, 95, 105, Kinds.WATER, Kinds.NONE,
            Kinds.NONE, Kinds.BASIC, false, 100, "snatches"),
    CRAWLTON("Crawlton", "s2:crawlton", "0,1", 10, Kinds.FLY, 95, 115, 90, 120, Kinds.FIRE, Kinds.NONE,
            Kinds.NONE, Kinds.FLURRY, false, 100, "lunges at"),
    FLASHER("Flasher", "s2:flasher", "0,1", 6, Kinds.FLY, 90, 105, 85, 120, Kinds.WATER, Kinds.ELEC,
            Kinds.ELEC, Kinds.SWEEP, false, 100, "flashes a shock across"),

    // ---- Angel Island (Sonic 3 & Knuckles) ----
    RHINOBOT("Rhinobot", "s3k:rhinobot", "0,1,2,3", 4, Kinds.WALK, 105, 110, 105, 120, Kinds.WATER, Kinds.NONE,
            Kinds.NONE, Kinds.BASIC, false, 100, "charges into"),
    BLOOMINATOR("Bloominator", "s3k:bloominator", "0,1", 12, Kinds.WALK, 95, 100, 100, 80, Kinds.FIRE, Kinds.NONE,
            Kinds.NONE, Kinds.SWEEP, false, 100, "fires spiked seeds at"),
    MONKEY_DUDE("Monkey Dude", "s3k:monkeydude", "0,1,2", 8, Kinds.HOP, 90, 100, 90, 120, Kinds.FIRE, Kinds.NONE,
            Kinds.NONE, Kinds.BASIC, false, 100, "throws a coconut at"),
    JAWZ("Jawz", "s3k:jawz", "0,1", 4, Kinds.FLY, 85, 110, 85, 130, Kinds.ELEC, Kinds.WATER,
            Kinds.WATER, Kinds.BASIC, false, 100, "torpedoes into"),
    BLASTOID("Blastoid", "s3k:blastoid", "0", 8, Kinds.WALK, 115, 100, 125, 70, Kinds.ELEC, Kinds.NONE,
            Kinds.WATER, Kinds.SWEEP, false, 100, "sprays water pellets over"),
    BUGGERNAUT("Buggernaut", "s3k:buggernaut", "0,1", 2, Kinds.FLY, 80, 100, 80, 135, Kinds.FIRE, Kinds.NONE,
            Kinds.NONE, Kinds.FLURRY, false, 100, "buzzes around"),
    TURBO_SPIKER("Turbo Spiker", "s3k:turbospiker", "0,1", 8, Kinds.WALK, 110, 105, 115, 90, Kinds.ELEC, Kinds.WATER,
            Kinds.NONE, Kinds.BASIC, false, 100, "launches its shell at"),
    MEGA_CHOPPER("Mega Chopper", "s3k:megachopper", "0,1", 6, Kinds.HOP, 90, 115, 85, 115, Kinds.ELEC, Kinds.NONE,
            Kinds.NONE, Kinds.FLURRY, false, 100, "gnaws on"),
    POINTDEXTER("Pointdexter", "s3k:pointdexter", "0,1", 10, Kinds.FLY, 100, 105, 110, 95, Kinds.ELEC, Kinds.WATER,
            Kinds.NONE, Kinds.BASIC, false, 100, "puffs its spines at"),
    SNALE_BLASTER("Snale Blaster", "s3k:snaleblaster", "0,1", 12, Kinds.WALK, 115, 105, 125, 75, Kinds.WATER,
            Kinds.NONE, Kinds.FIRE, Kinds.SWEEP, false, 100, "shells the party with"),
    ORBINAUT("Orbinaut", "s3k:orbinaut", "0", 8, Kinds.FLY, 95, 110, 110, 100, Kinds.FIRE, Kinds.NONE,
            Kinds.NONE, Kinds.FLURRY, false, 100, "flings a spike ball at"),
    RIBOT("Ribot", "s3k:ribot", "0,1", 8, Kinds.FLY, 100, 105, 105, 95, Kinds.WATER, Kinds.NONE,
            Kinds.NONE, Kinds.FLURRY, false, 100, "swings its maces at"),
    FLYBOT("Flybot767", "s3k:flybot", "0,1,2", 4, Kinds.FLY, 90, 115, 85, 135, Kinds.ELEC, Kinds.NONE,
            Kinds.NONE, Kinds.BASIC, false, 100, "dive-bombs"),
    EGG_ROBO("Egg Robo", "boss:egg_robo", "0", 8, Kinds.FLY, 120, 115, 115, 110, Kinds.ELEC, Kinds.NONE,
            Kinds.NONE, Kinds.SWEEP, false, 100, "opens fire on"),

    // ---- Bosses ----
    GIGA_MOTOBUG("Giga Motobug", "s1:motobug", "0,1,2", 6, Kinds.WALK, 650, 120, 110, 90, Kinds.NONE, Kinds.NONE,
            Kinds.NONE, Kinds.CHARGER, true, 200, "rams"),
    BOMB_KING("Bomb King", "s1:bomb", "0,1,2", 8, Kinds.WALK, 700, 120, 115, 85,
            Kinds.WATER, Kinds.FIRE, Kinds.FIRE, Kinds.CHARGER, true, 200, "blasts"),
    EGG_MOBILE("Dr. Eggman", "s1:eggman", "0", 8, Kinds.WALK, 760, 125, 120, 100, Kinds.ELEC, Kinds.NONE,
            Kinds.NONE, Kinds.CHARGER, true, 100, "fires his blaster at"),
    COCONUTS_CHIEF("Coconuts Chief", "s2:coconuts", "0,1,2", 8, Kinds.WALK, 700, 120, 115, 95, Kinds.FIRE,
            Kinds.NONE, Kinds.NONE, Kinds.CHARGER, true, 200, "hurls a giant coconut at"),
    GRABBER_QUEEN("Grabber Queen", "s2:grabber", "0,1", 8, Kinds.FLY, 760, 125, 120, 100, Kinds.ELEC, Kinds.WATER,
            Kinds.WATER, Kinds.CHARGER, true, 200, "drips Mega Mack over"),
    SILVER_SONIC("Silver Sonic", "s2:silver", "5", 8, Kinds.WALK, 820, 130, 130, 120, Kinds.ELEC, Kinds.NONE,
            Kinds.NONE, Kinds.CHARGER, true, 100, "spin-dashes into"),
    KNUCKLES_RIVAL("Knuckles", "s3k:knuckles", "0", 8, Kinds.WALK, 700, 120, 125, 95, Kinds.NONE, Kinds.NONE,
            Kinds.NONE, Kinds.RIVAL, true, 100, "punches"),
    FLAME_CRAFT("Flame Craft", "boss:flame_craft", "0", 8, Kinds.FLY, 860, 130, 125, 100, Kinds.WATER, Kinds.FIRE,
            Kinds.FIRE, Kinds.CHARGER, true, 100, "bombs"),
    SCREW_MOBILE("Screw Mobile", "boss:screw_mobile", "0", 8, Kinds.FLY, 900, 130, 130, 100, Kinds.ELEC,
            Kinds.WATER, Kinds.WATER, Kinds.CHARGER, true, 100, "drops depth charges on"),
    BEAM_ROCKET("Beam Rocket", "boss:beam_rocket", "0", 8, Kinds.FLY, 940, 135, 130, 105, Kinds.WATER, Kinds.ELEC,
            Kinds.ELEC, Kinds.CHARGER, true, 100, "sweeps a laser over"),
    MECHA_SONIC("Mecha Sonic", "s3k:mecha", "0", 8, Kinds.WALK, 900, 140, 135, 140, Kinds.ELEC, Kinds.NONE,
            Kinds.NONE, Kinds.CHARGER, true, 100, "slashes"),
    CONVERGENCE_ENGINE("Convergence Engine", "boss:big_arm", "0", 8, Kinds.FLY, 1500, 145, 140, 110, Kinds.NONE,
            Kinds.NONE, Kinds.NONE, Kinds.CHARGER, true, 150, "crushes");

    public final String label;
    public final String art;
    public final String frames;
    public final int ticks;
    public final int motion;
    public final int hpPct;
    public final int atkPct;
    public final int defPct;
    public final int spdPct;
    public final int weakIndex;
    public final int resistIndex;
    public final int attackIndex;
    public final int pattern;
    public final boolean boss;
    public final int scale;
    public final String verb;

    EnemyKind(String label, String art, String frames, int ticks, int motion, int hpPct, int atkPct, int defPct,
            int spdPct, int weakIndex, int resistIndex, int attackIndex, int pattern, boolean boss, int scale,
            String verb) {
        this.label = label;
        this.art = art;
        this.frames = frames;
        this.ticks = ticks;
        this.motion = motion;
        this.hpPct = hpPct;
        this.atkPct = atkPct;
        this.defPct = defPct;
        this.spdPct = spdPct;
        this.weakIndex = weakIndex;
        this.resistIndex = resistIndex;
        this.attackIndex = attackIndex;
        this.pattern = pattern;
        this.boss = boss;
        this.scale = scale;
        this.verb = verb;
    }

    public Element weakness() { return Element.values()[weakIndex]; }
    public Element resistance() { return Element.values()[resistIndex]; }
    public Element attackElement() { return Element.values()[attackIndex]; }

    /** Animation frames as integers. */
    public int[] frameList() {
        String[] parts = frames.split(",");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Integer.parseInt(parts[i].trim());
        return out;
    }
}
