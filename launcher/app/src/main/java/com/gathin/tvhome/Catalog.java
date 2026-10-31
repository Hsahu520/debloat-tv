package com.gathin.tvhome;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The hand-picked titles behind For You, Swipe, the assistant and the memory game: assets/catalog.json, with a
 * poster per title in assets/posters/ (tools/fetch_posters.py). Platform is where the title streams in India.
 */
final class Catalog {

    private static final java.util.Set<String> STOP = new java.util.HashSet<>(java.util.Arrays.asList("the", "and", "of", "in"));

    static final String[] GENRES = {"comedy", "scifi", "action", "thriller", "crime", "romance"};

    static final class Title {
        final String name, lang, platform, slug;
        final int year;
        final String[] genres;

        Title(String name, int year, String lang, String[] genres, String platform) {
            this.name = name;
            this.year = year;
            this.lang = lang;
            this.genres = genres;
            this.platform = platform;
            this.slug = slug(name);
        }

        boolean has(String genre) {
            for (String g : genres) if (g.equals(genre)) return true;
            return false;
        }

        /** "2023 · Crime · Thriller · Hindi" */
        String meta() {
            StringBuilder b = new StringBuilder().append(year);
            for (String g : genres) b.append("  ·  ").append(genreLabel(g));
            b.append("  ·  ").append("hi".equals(lang) ? "Hindi" : "English");
            return b.toString();
        }
    }

    private static List<Title> all;

    static synchronized List<Title> all(Context c) {
        if (all != null) return all;
        List<Title> out = new ArrayList<>();
        try (InputStream in = c.getAssets().open("catalog.json")) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            for (int n; (n = in.read(b)) > 0; ) buf.write(b, 0, n);
            JSONArray arr = new JSONArray(buf.toString("UTF-8"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                JSONArray g = o.getJSONArray("g");
                String[] genres = new String[g.length()];
                for (int k = 0; k < genres.length; k++) genres[k] = g.getString(k);
                out.add(new Title(o.getString("t"), o.getInt("y"), o.getString("l"), genres, o.getString("p")));
            }
        } catch (Exception ignored) {
            // An unreadable catalog leaves the screens empty rather than crashing Home.
        }
        all = Collections.unmodifiableList(out);
        return all;
    }

    static Title bySlug(Context c, String slug) {
        for (Title t : all(c)) if (t.slug.equals(slug)) return t;
        return null;
    }

    static String genreLabel(String g) {
        switch (g) {
            case "scifi": return "Sci-Fi";
            case "comedy": return "Comedy";
            case "action": return "Action";
            case "thriller": return "Thriller";
            case "crime": return "Crime";
            case "romance": return "Romance";
            default: return g;
        }
    }

    static String platformLabel(String p) {
        switch (p) {
            case "netflix": return "Netflix";
            case "prime": return "Prime Video";
            case "hotstar": return "JioHotstar";
            case "sonyliv": return "SonyLIV";
            case "youtube": return "YouTube";
            default: return p;
        }
    }

    /** The platform's brand colour, for the small badge and the assistant's answer glow. */
    static int platformColor(String p) {
        switch (p) {
            case "netflix": return 0xFFE50914;
            case "prime": return 0xFF1A98FF;
            case "hotstar": return 0xFF7B2FF7;
            case "sonyliv": return 0xFFF5A623;
            case "youtube": return 0xFFFF0033;
            default: return 0xFFFFFFFF;
        }
    }

    static String slug(String s) {
        return norm(s).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    /** Lower case, accents off ("Shōgun" → "shogun"), for matching what was said against titles. */
    static String norm(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT);
    }

    /** Best title for a spoken phrase, or null when nothing is close enough. */
    static Title match(Context c, String phrase) {
        String q = " " + norm(phrase).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim() + " ";
        if (q.trim().isEmpty()) return null;
        Title best = null;
        double bestScore = 0;
        for (Title t : all(c)) {
            String n = norm(t.name).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
            double score;
            if (q.contains(" " + n + " ")) {
                score = 1.0 + n.length() / 100.0;   // whole title said: longest wins ("citadel honey bunny" over "citadel")
            } else {
                String[] words = n.split(" ");
                int hit = 0, sig = 0;
                for (String w : words) {
                    if ((w.length() < 3 || STOP.contains(w)) && words.length > 1) continue;
                    sig++;
                    if (q.contains(" " + w + " ")) hit++;
                }
                score = sig == 0 ? 0 : 0.85 * hit / sig;
            }
            if (score > bestScore) {
                bestScore = score;
                best = t;
            }
        }
        return bestScore >= 0.6 ? best : null;
    }
}
