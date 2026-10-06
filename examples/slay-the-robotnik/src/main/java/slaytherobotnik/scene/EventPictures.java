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
