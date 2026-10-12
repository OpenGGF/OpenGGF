package starpost.festivals;

import static starpost.core.Calendar.SPRING;

import starpost.people.Cast;
import starpost.people.VillagerDef;

/**
 * The Flicky post's festival letters (design doc §18), added to the cast's mail by
 * {@code Cast}: the Signpost Board's opening notice, Robotnik's first special order, and the
 * Star Light Feast's secret friend, one letter per villager, sent when the draw on Winter 18 sets
 * its flag ({@link Festivals#SECRET_FLAG}).
 */
public final class FestivalMail {
    private FestivalMail() {
    }

    public static void define(Cast cast) {
        cast.letter("board_notice", "post").on(SPRING, 2).yearOne()
                .text("NOTICE: THE SIGNPOST BOARD BY THE LAMPPOST INN NOW TAKES REQUESTS FROM THE NEIGHBOURS "
                        + "AND KEEPS THE FESTIVAL CALENDAR. FIRST UP: THE RING HUNT, SPRING 13.");
        cast.letter("board_robotnik", "robotnik").on(SPRING, 8).yearOne()
                .text("DEAR FARMER. I HAVE PINNED A MODEST ORDER TO YOUR QUAINT LITTLE BOARD. THE EGG TRUCK "
                        + "PAYS HANDSOMELY. NO QUESTIONS. - DR. I. ROBOTNIK, ROBOMART");
        for (VillagerDef v : cast.all()) {
            if (v.isPet() || v.body().equals("totem")) {
                continue;
            }
            cast.letter(Festivals.SECRET_FLAG + v.id, "post").whenFlag(Festivals.SECRET_FLAG + v.id)
                    .text("THE STAR LIGHT FEAST IS ON WINTER 25. YOUR SECRET FRIEND THIS YEAR IS " + v.name
                            + ". FIND THEM A GIFT THEY WILL LOVE, AND TELL NOBODY. - THE FEAST COMMITTEE");
        }
    }
}
