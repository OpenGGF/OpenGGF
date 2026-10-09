package starpost.festivals;

import starpost.core.Calendar;

/**
 * One of the year's eight festivals (design doc §8): its date, its posted hours, where the valley
 * gathers for it, and how much of the day it takes when the farmer joins in. Built by
 * {@link FestivalBook}; instance-owned (the mod validator forbids static tables).
 */
public final class Festival {
    public final String id;
    public final String name;
    public final int season;
    public final int day;
    /** Posted hours, minutes since midnight: the farmer can join from {@code open} until {@code close}. */
    public final int open;
    public final int close;
    /** The valley anchor (a {@code people.Anchors} name) where the crowd gathers and the farmer enters. */
    public final String anchor;
    /** Game minutes the festival takes when joined: the clock moves on by this much afterwards. */
    public final int length;
    /** Where it is held, for the board and the morning card. */
    public final String where;
    /** One line about it, for the board's calendar. */
    public final String blurb;

    Festival(String id, String name, int season, int day, int openHhmm, int closeHhmm, String anchor, int length,
            String where, String blurb) {
        this.id = id;
        this.name = name;
        this.season = season;
        this.day = day;
        this.open = openHhmm / 100 * 60 + openHhmm % 100;
        this.close = closeHhmm / 100 * 60 + closeHhmm % 100;
        this.anchor = anchor;
        this.length = length;
        this.where = where;
        this.blurb = blurb;
    }

    public boolean on(int season, int day) {
        return this.season == season && this.day == day;
    }

    public boolean on(Calendar calendar) {
        return on(calendar.season(), calendar.day());
    }

    /** Whether the farmer can join now (on the day, within the posted hours). */
    public boolean openAt(Calendar calendar) {
        return on(calendar) && calendar.minutes() >= open && calendar.minutes() < close;
    }

    /** A night festival is held after dark (the valley is drawn at night). */
    public boolean night() {
        return open >= 19 * 60;
    }

    /** "9AM-2PM". */
    public String hours() {
        return clock(open) + "-" + clock(close);
    }

    /** The clock after the festival: {@link #length} later than it began, and never after 1AM. */
    public int after(int minutes) {
        return Math.min(Calendar.DAY_END - 60, Math.max(minutes, open) + length);
    }

    static String clock(int minutes) {
        int h = minutes / 60 % 24, m = minutes % 60;
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return h12 + (m == 0 ? "" : ":" + (m < 10 ? "0" : "") + m) + (h < 12 ? "AM" : "PM");
    }
}
