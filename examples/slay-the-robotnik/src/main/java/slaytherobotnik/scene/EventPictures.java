package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;

/**
 * Makes the picture for an event's art key ({@code EventDef.art()}). To illustrate a new event,
 * write an {@link EventPicture} subclass and add its key here.
 */
final class EventPictures {
    private EventPictures() {
    }

    static EventPicture create(Shell shell, String art) {
        return switch (art) {
            case "event:slot_machine" -> new SlotMachinePicture(shell);
            case "event:emerald_altar" -> new EmeraldAltarPicture(shell);
            case "event:mural" -> new MuralPicture(shell);
            case "event:scrapyard" -> new ScrapyardPicture(shell);
            case "event:tablets" -> new TabletsPicture(shell);
            case "event:collector" -> new CollectorPicture(shell);
            case "event:campfire" -> new CampfirePicture(shell);
            case "event:clogged_pipe" -> new PipePicture(shell);
            case "event:medic" -> new MedicPicture(shell);
            case "event:workbench" -> new WorkbenchPicture(shell);
            case "event:mirror_monitor" -> new MirrorMonitorPicture(shell);
            case "event:terminal" -> new TerminalPicture(shell);
            case "event:lab" -> new LabPicture(shell);
            case "event:hyper_remote" -> new HyperRemotePicture(shell);
            case "event:robotnik_offer" -> new RobotnikOfferPicture(shell);
            case "event:giant_ring" -> new GiantRingPicture(shell);
            case "event:monitor_row" -> new MonitorRowPicture(shell);
            case "event:ring_shrine" -> new RingShrinePicture(shell);
            case "event:mushrooms" -> new MushroomsPicture(shell);
            case "event:waterfall" -> new WaterfallPicture(shell);
            case "event:special_stage" -> new SpecialStagePicture(shell);
            case "event:chao_cart" -> new ChaoCartPicture(shell);
            default -> new StaticPicture();
        };
    }

    /** The picture for an art key with no picture class: a "?" that animates nothing. */
    private static final class StaticPicture extends EventPicture {
        @Override
        void draw(Shell shell, SceneCanvas c, int x, int y, int w, int h) {
            EventArt.drawPlain(shell, c, x, y, w, h);
        }
    }
}
