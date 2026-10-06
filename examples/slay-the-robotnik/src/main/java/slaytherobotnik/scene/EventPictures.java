package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;

/** Makes the picture for an event's art key ({@code EventDef.art()}). */
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
            default -> new StaticPicture(art);
        };
    }

    /** An event drawn by {@link EventArt} that animates nothing for its outcomes (yet). */
    private static final class StaticPicture extends EventPicture {
        private final String art;

        StaticPicture(String art) {
            this.art = art;
        }

        @Override
        void draw(Shell shell, SceneCanvas c, int x, int y, int w, int h) {
            EventArt.draw(shell, c, art, x, y, w, h);
        }
    }
}
