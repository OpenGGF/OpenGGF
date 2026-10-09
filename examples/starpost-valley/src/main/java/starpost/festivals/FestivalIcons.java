package starpost.festivals;

import com.openggf.mods.scene.SceneImage;
import starpost.art.ItemIcons;
import starpost.core.Item;

/** Icons for the festivals' Records: a disc with a label in the festival's colours (original UI art). */
final class FestivalIcons implements ItemIcons.Source {
    @Override
    public SceneImage icon(Item item) {
        return switch (item.id()) {
            case "record_special_stage" -> ItemIcons.picture(disc(), 0xFF6DB6FF, 0xFF2449DB);
            case "record_migration" -> ItemIcons.picture(disc(), 0xFF92FFFF, 0xFF2492B6);
            case "record_slots" -> ItemIcons.picture(disc(), 0xFFFF92DB, 0xFFB62492);
            case "record_death_egg" -> ItemIcons.picture(disc(), 0xFFFF4924, 0xFF6D0000);
            case "record_icecap_s3" -> ItemIcons.picture(disc(), 0xFFDBFFFF, 0xFF6DB6DB);
            default -> null;
        };
    }

    /** A vinyl disc: Green Hill's darkest brown with a groove's sheen, the label in the festival's colours. */
    private static String[] disc() {
        return new String[] {
            "................",
            ".....kkkkkk.....",
            "...kkkkkkkkkk...",
            "..kkmkkkkkkkkk..",
            ".kkmkkkFFkkkkkk.",
            ".kmkkkFFFFkkkkk.",
            "kkkkkFFfwFFkkkkk",
            "kkkkkFFwfFFkkkkk",
            ".kkkkkFFFFkkkmk.",
            ".kkkkkkFFkkkmkk.",
            "..kkkkkkkkkmkk..",
            "...kkkkkkkkkk...",
            ".....kkkkkk.....",
        };
    }
}
