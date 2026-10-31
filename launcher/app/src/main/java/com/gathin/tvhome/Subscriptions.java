package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Streaming subscriptions and when they renew, for the screensaver. Streaming apps don't share this with other apps,
 * so it comes from SUBSCRIPTIONS in tv.conf, e.g.
 *   SUBSCRIPTIONS="Netflix=2026-11-15, Prime Video=yearly:03-01, JioHotstar=monthly:5"
 * A date is a one-off end date; monthly:DD and yearly:MM-DD roll over to the next renewal.
 */
final class Subscriptions {
    private Subscriptions() {}

    static final class Sub {
        final String name;
        final int daysLeft;      // negative once a one-off date has passed
        final boolean recurring;
        Sub(String name, int daysLeft, boolean recurring) {
            this.name = name;
            this.daysLeft = daysLeft;
            this.recurring = recurring;
        }

        /** "12 days left", "Renews tomorrow", "Expired". */
        String label() {
            if (daysLeft < 0) return "Expired";
            String verb = recurring ? "Renews" : "Ends";
            if (daysLeft == 0) return verb + " today";
            if (daysLeft == 1) return verb + " tomorrow";
            return daysLeft + " days left";
        }

        /** Green, amber within a week, red on the last day or after. */
        int color() {
            return daysLeft <= 1 ? 0xFFFF6B81 : daysLeft <= 7 ? 0xFFFFC14D : 0xFF6BE3A4;
        }
    }

    static List<Sub> parse(String conf) {
        List<Sub> out = new ArrayList<>();
        if (conf == null) return out;
        Calendar today = midnight(Calendar.getInstance());
        for (String part : conf.split(",")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String name = part.substring(0, eq).trim();
            String when = part.substring(eq + 1).trim().toLowerCase(Locale.ROOT);
            try {
                Calendar due = midnight(Calendar.getInstance());
                boolean recurring = true;
                if (when.startsWith("monthly:")) {
                    int day = Integer.parseInt(when.substring(8).trim());
                    due.set(Calendar.DAY_OF_MONTH, Math.min(day, due.getActualMaximum(Calendar.DAY_OF_MONTH)));
                    if (due.before(today)) {
                        due.set(Calendar.DAY_OF_MONTH, 1);
                        due.add(Calendar.MONTH, 1);
                        due.set(Calendar.DAY_OF_MONTH, Math.min(day, due.getActualMaximum(Calendar.DAY_OF_MONTH)));
                    }
                } else if (when.startsWith("yearly:")) {
                    String[] md = when.substring(7).trim().split("-");
                    due.set(Calendar.MONTH, Integer.parseInt(md[0]) - 1);
                    due.set(Calendar.DAY_OF_MONTH, Integer.parseInt(md[1]));
                    if (due.before(today)) due.add(Calendar.YEAR, 1);
                } else {
                    String[] ymd = when.split("-");
                    due.set(Integer.parseInt(ymd[0]), Integer.parseInt(ymd[1]) - 1, Integer.parseInt(ymd[2]));
                    recurring = false;
                }
                long days = Math.round((due.getTimeInMillis() - today.getTimeInMillis()) / 86_400_000.0);
                out.add(new Sub(name, (int) days, recurring));
            } catch (RuntimeException ignored) {
                // A typo in one entry skips that entry, not the others.
            }
        }
        return out;
    }

    private static Calendar midnight(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }
}
