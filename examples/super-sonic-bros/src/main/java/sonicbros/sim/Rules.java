package sonicbros.sim;

/** How a match is played: the mode and its limits, items, and handicaps. Mutable menu state. */
public final class Rules {
    public enum Mode { STOCK, TIME, STAMINA }

    public Mode mode = Mode.STOCK;
    public int stocks = 3;
    public int minutes = 3;
    public int stamina = 150;
    /** 0 off, 1 low, 2 medium, 3 high. */
    public int itemFrequency = 2;
    /** Bit per {@link Item} kind: which monitors can drop. */
    public int itemMask = (1 << Item.KINDS) - 1;
    public boolean tapJump = true;
    public boolean teams;
    public boolean friendlyFire;
    public double damageRatio = 1.0;
    /** Index into the stage list; -1 random. */
    public int stage = -1;

    public Rules copy() {
        Rules r = new Rules();
        r.mode = mode;
        r.stocks = stocks;
        r.minutes = minutes;
        r.stamina = stamina;
        r.itemFrequency = itemFrequency;
        r.itemMask = itemMask;
        r.tapJump = tapJump;
        r.teams = teams;
        r.friendlyFire = friendlyFire;
        r.damageRatio = damageRatio;
        r.stage = stage;
        return r;
    }

    /** One-line description for menus and results. */
    public String describe() {
        return switch (mode) {
            case STOCK -> stocks + (stocks == 1 ? " STOCK" : " STOCKS");
            case TIME -> minutes + " MIN TIME";
            case STAMINA -> stamina + " HP STAMINA";
        };
    }

    /** Serialises to key=value lines for the settings file. */
    public String save() {
        return "mode=" + mode + "\nstocks=" + stocks + "\nminutes=" + minutes + "\nstamina=" + stamina
                + "\nitems=" + itemFrequency + "\nitemMask=" + itemMask + "\ntapJump=" + tapJump + "\nteams=" + teams
                + "\nfriendlyFire=" + friendlyFire + "\ndamageRatio=" + damageRatio + "\nstage=" + stage + "\n";
    }

    /** Reads what {@link #save} wrote, ignoring anything malformed. */
    public void load(String text) {
        if (text == null) return;
        for (String line : text.split("\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            try {
                switch (key) {
                    case "mode" -> mode = Mode.valueOf(value);
                    case "stocks" -> stocks = clamp(Integer.parseInt(value), 1, 99);
                    case "minutes" -> minutes = clamp(Integer.parseInt(value), 1, 99);
                    case "stamina" -> stamina = clamp(Integer.parseInt(value), 50, 300);
                    case "items" -> itemFrequency = clamp(Integer.parseInt(value), 0, 3);
                    case "itemMask" -> itemMask = Integer.parseInt(value) & ((1 << Item.KINDS) - 1);
                    case "tapJump" -> tapJump = Boolean.parseBoolean(value);
                    case "teams" -> teams = Boolean.parseBoolean(value);
                    case "friendlyFire" -> friendlyFire = Boolean.parseBoolean(value);
                    case "damageRatio" -> damageRatio = Math.max(0.5, Math.min(2.0, Double.parseDouble(value)));
                    case "stage" -> stage = Integer.parseInt(value);
                    default -> { }
                }
            } catch (RuntimeException ignored) {
                // Keep the default for a malformed line.
            }
        }
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
