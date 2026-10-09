package threeislands.field;

import threeislands.core.Zone;

/** Local attempts are freely retryable; a solved mechanism latches permanently in the save. */
public final class MechanismPuzzle {
    public enum Rule { RESTORE, SEQUENCE, CIRCUIT }
    public final Rule rule;
    public final String[] labels;
    public final String clue;
    private final int[] order;
    private int cursor, switches;

    private MechanismPuzzle(Rule rule, String clue, String... labels) {
        this.rule = rule; this.clue = clue; this.labels = labels;
        order = labels.length == 4 ? new int[] {2,0,3,1} : labels.length == 3 ? new int[] {2,0,1}
                : labels.length == 2 ? new int[] {1,0} : new int[] {0};
    }
    public String attempt(int index) {
        if (rule == Rule.RESTORE) return "The mechanism locks into place. A distant counterweight shifts.";
        if (rule == Rule.SEQUENCE) {
            if (index != order[cursor]) {
                cursor = index == order[0] ? 1 : 0;
                return cursor == 1 ? "The first tone holds. The new phrase has begun." : "The tones fade. The phrase starts again; nothing is lost.";
            }
            cursor++;
            return cursor == labels.length ? "The whole phrase rings out. The seal withdraws." : "The tone holds. " + cursor + " of " + labels.length + " marks glow.";
        }
        switches ^= 1 << index;
        int lamps = ((switches & 1) != 0 ? 3 : 0) ^ ((switches & 2) != 0 ? 6 : 0) ^ ((switches & 4) != 0 ? 4 : 0);
        return lamps == 7 ? "All three lamps shine. The pressure stabilises and the seal withdraws."
                : "Lamps: " + ((lamps & 1) != 0 ? "lit" : "dark") + " / " + ((lamps & 2) != 0 ? "lit" : "dark")
                + " / " + ((lamps & 4) != 0 ? "lit" : "dark") + ". The controls can be turned again.";
    }
    public boolean solved() {
        if (rule == Rule.SEQUENCE) return cursor == labels.length;
        if (rule == Rule.CIRCUIT) return switches == 5;
        return false;
    }
    public String instructions() {
        return clue + (rule == Rule.CIRCUIT ? " Three lamps must shine together. " + labels[0] + " flips lamps one and two; " + labels[1] + " flips two and three; " + labels[2] + " flips only three. Each control can be reversed."
                : rule == Rule.SEQUENCE ? " Let each tone finish before trying the next. A wrong tone resets the phrase." : " Each restored mechanism stays in place; their order does not matter.");
    }
    public static MechanismPuzzle of(Zone zone, boolean interior) {
        if (!interior) {
            String[] controls = switch (zone) {
                case STAR_LIGHT -> new String[] {"Upper feeder", "Lower feeder"};
                case SPRING_YARD -> new String[] {"Loading brake", "Return brake"};
                case EMERALD_HILL -> new String[] {"Orchard guide rope", "Hill guide rope", "Workshop guide rope"};
                case CHEMICAL_PLANT -> new String[] {"Intake pump", "Feed pump", "Return pump"};
                case MYSTIC_CAVE -> new String[] {"Upper winch", "Deep winch", "Bridge winch"};
                case ANGEL_ISLAND -> new String[] {"Root warden", "Ash warden", "Stone warden"};
                case HYDROCITY -> new String[] {"North intake", "South intake", "Return intake"};
                case LAUNCH_BASE -> new String[] {"Cargo mooring", "Fuel mooring", "Tower mooring"};
                default -> new String[] {"Relay A", "Relay B", "Relay C", "Relay D"};
            };
            return new MechanismPuzzle(Rule.RESTORE, switch (zone) {
            case STAR_LIGHT -> "The skywalk has no power. Its two feeder stations lie off the main promenade.";
            case SPRING_YARD -> "The freight lift is chained down. Release the brakes in the two loading yards.";
            case EMERALD_HILL -> "The orchard ferry is stranded. Repair the three guide ropes in the hillside workshops.";
            case CHEMICAL_PLANT -> "The upper pipeway is flooded. Shut all three feeder pumps along the service branches.";
            case MYSTIC_CAVE -> "The mine bridge has collapsed. Wind the three support winches in the side shafts.";
            case ANGEL_ISLAND -> "Roots bind the eastern stair. Wake the three stone wardens around the terraces.";
            case HYDROCITY -> "The aqueduct is submerged. Close three intakes in the dry galleries to lower the water.";
            case LAUNCH_BASE -> "The gantry cannot turn. Release the three moorings in the maintenance spurs.";
            default -> "The archive district is isolated. Restore four auxiliary relays around the outer ring.";
            }, controls);
        }
        return switch (zone) {
            case GREEN_HILL -> new MechanismPuzzle(Rule.RESTORE, "A small feather is caught under the floodgate. A winch in the side crypt will raise it.", "Floodgate winch");
            case STAR_LIGHT -> new MechanismPuzzle(Rule.SEQUENCE, "The observatory records: 'First the distant star; then the near moon.'", "Moon lens", "Star lens");
            case SPRING_YARD -> new MechanismPuzzle(Rule.CIRCUIT, "A freight diagram shows the lift's three power lamps.", "Freight switch", "Track switch", "Lift switch");
            case EMERALD_HILL -> new MechanismPuzzle(Rule.RESTORE, "The workshop's compass needs three bearings recovered from the abandoned workrooms.", "North bearing", "East bearing", "West bearing");
            case CHEMICAL_PLANT -> new MechanismPuzzle(Rule.CIRCUIT, "The chart links each valve to pressure lamps above the sealed reservoir.", "Intake valve", "Return valve", "Vent valve");
            case MYSTIC_CAVE -> new MechanismPuzzle(Rule.SEQUENCE, "The miner's verse reads: 'Deep below, a spark is born; then the lantern carries it home.'", "Spark brazier", "Lantern brazier", "Deep brazier");
            case ANGEL_ISLAND -> new MechanismPuzzle(Rule.RESTORE, "Three memorial seals have fallen silent. Visit the ancestor alcoves and renew their vows.", "Courage seal", "Mercy seal", "Memory seal");
            case HYDROCITY -> new MechanismPuzzle(Rule.CIRCUIT, "The tide engine balances three chambers. The mural hall opens only at equal pressure.", "Upper sluice", "Lower sluice", "Balance sluice");
            case LAUNCH_BASE -> new MechanismPuzzle(Rule.SEQUENCE, "The flight checklist reads: 'Fuel, ignition, release.' The old terminals still answer.", "Ignition terminal", "Release terminal", "Fuel terminal");
            case DEATH_EGG -> new MechanismPuzzle(Rule.SEQUENCE, "The archive's chronology reads: 'Memory precedes the present. The future comes before the final silence.'", "Present key", "Silence key", "Memory key", "Future key");
        };
    }
}
