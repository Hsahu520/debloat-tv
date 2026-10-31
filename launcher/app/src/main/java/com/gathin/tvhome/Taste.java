package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * What the household likes. Starts with the six genres weighted equally, then every swipe moves the genre weights:
 * a like adds to each of the title's genres, a dislike takes a little away and hides the title. Kept in
 * SharedPreferences on the TV; nothing leaves it.
 */
final class Taste {

    private static final String PREFS = "taste";
    private final SharedPreferences p;
    private final Context c;

    Taste(Context c) {
        this.c = c.getApplicationContext();
        p = this.c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    float weight(String genre) { return p.getFloat("w_" + genre, 1f); }

    Set<String> liked() { return new HashSet<>(p.getStringSet("liked", Collections.emptySet())); }

    Set<String> disliked() { return new HashSet<>(p.getStringSet("disliked", Collections.emptySet())); }

    boolean seen(Catalog.Title t) { return liked().contains(t.slug) || disliked().contains(t.slug); }

    void like(Catalog.Title t) { vote(t, true); }

    void dislike(Catalog.Title t) { vote(t, false); }

    private void vote(Catalog.Title t, boolean up) {
        Set<String> liked = liked(), disliked = disliked();
        liked.remove(t.slug);
        disliked.remove(t.slug);
        (up ? liked : disliked).add(t.slug);
        SharedPreferences.Editor e = p.edit().putStringSet("liked", liked).putStringSet("disliked", disliked);
        for (String g : t.genres) {
            float w = weight(g) + (up ? 0.35f : -0.2f);
            e.putFloat("w_" + g, Math.max(0.1f, Math.min(4f, w)));
        }
        e.putInt("lang_" + t.lang, p.getInt("lang_" + t.lang, 0) + (up ? 1 : -1));
        e.apply();
    }

    /** Undo the last vote on a title (the swipe deck's Back). */
    void forget(Catalog.Title t, boolean wasLike) {
        Set<String> liked = liked(), disliked = disliked();
        liked.remove(t.slug);
        disliked.remove(t.slug);
        SharedPreferences.Editor e = p.edit().putStringSet("liked", liked).putStringSet("disliked", disliked);
        for (String g : t.genres) e.putFloat("w_" + g, Math.max(0.1f, weight(g) - (wasLike ? 0.35f : -0.2f)));
        e.putInt("lang_" + t.lang, p.getInt("lang_" + t.lang, 0) - (wasLike ? 1 : -1));
        e.apply();
    }

    double score(Catalog.Title t) {
        double s = 0;
        for (String g : t.genres) s += weight(g);
        s /= t.genres.length;                    // the average genre weight...
        s += 0.08 * (t.genres.length - 1);       // ...with only a small edge for genre-mixing titles
        s += 0.08 * Math.signum(p.getInt("lang_" + t.lang, 0));
        s += (t.year - 2015) * 0.015;   // a nudge towards newer titles
        return s;
    }

    /** The titles in taste order, disliked ones left out. Shuffled a little per day so the rows feel alive. */
    List<Catalog.Title> ranked(String genre, boolean includeLiked) {
        Set<String> no = disliked(), yes = liked();
        Random daily = new Random(Calendar.getInstance().get(Calendar.DAY_OF_YEAR) * 31L + (genre == null ? 0 : genre.hashCode()));
        List<Catalog.Title> out = new ArrayList<>();
        List<double[]> keys = new ArrayList<>();
        for (Catalog.Title t : Catalog.all(c)) {
            if (no.contains(t.slug)) continue;
            if (!includeLiked && yes.contains(t.slug)) continue;
            if (genre != null && !t.has(genre)) continue;
            out.add(t);
            keys.add(new double[]{score(t) + daily.nextDouble() * 0.45});
        }
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < out.size(); i++) idx.add(i);
        idx.sort((a, b) -> Double.compare(keys.get(b)[0], keys.get(a)[0]));
        List<Catalog.Title> sorted = new ArrayList<>();
        for (int i : idx) sorted.add(out.get(i));
        return sorted;
    }

    /** Genres from most to least liked. */
    List<String> topGenres() {
        List<String> g = new ArrayList<>();
        Collections.addAll(g, Catalog.GENRES);
        g.sort((a, b) -> Float.compare(weight(b), weight(a)));
        return g;
    }

    /** Titles not voted on yet, best guesses first: the swipe deck. */
    List<Catalog.Title> unseen() {
        List<Catalog.Title> out = new ArrayList<>();
        for (Catalog.Title t : ranked(null, false)) if (!seen(t)) out.add(t);
        return out;
    }

    List<Catalog.Title> likedTitles() {
        List<Catalog.Title> out = new ArrayList<>();
        Set<String> yes = liked();
        for (Catalog.Title t : Catalog.all(c)) if (yes.contains(t.slug)) out.add(t);
        return out;
    }
}
