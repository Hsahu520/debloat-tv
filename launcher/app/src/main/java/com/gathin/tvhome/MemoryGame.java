package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Poster Memory: sixteen glass cards hide eight pairs of posters from the catalog. Flip two with OK; a pair stays
 * up and glows, a miss flips back. Fewer moves, more stars. Ordinary views (it is turn-based), with 3D flips.
 */
final class MemoryGame extends FrameLayout {

    private static final int COLS = 4, ROWS = 4;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final GridLayout grid;
    private final TextView stats, banner;
    private final List<Card> cards = new ArrayList<>();
    private Card first, second;
    private int moves, matched, best;
    private long started;
    private boolean locked;
    private final SharedPreferences prefs;

    private final class Card extends FrameLayout {
        final Catalog.Title title;
        final ImageView face;
        final View back;
        boolean up, done;

        Card(Context c, Catalog.Title t) {
            super(c);
            title = t;
            float r = Ui.dp(c, 14);
            setClipToOutline(true);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
            });
            setCameraDistance(8000 * Ui.density(c));
            setFocusable(true);
            setClickable(true);
            face = new ImageView(c);
            face.setScaleType(ImageView.ScaleType.CENTER_CROP);
            face.setVisibility(INVISIBLE);
            addView(face, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            back = new View(c);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF1E2A5A, 0xFF3A1E5A, 0xFF5A1E44});
            back.setBackground(g);
            addView(back, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            TextView mark = Ui.text(c, R.font.montserrat_extrabold, 26, 0x66FFFFFF);
            mark.setText("✦");
            mark.setGravity(Gravity.CENTER);
            addView(mark, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            View glass = new View(c);
            glass.setBackground(new Ui.LiquidGlass(c, 14));
            addView(glass, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            Posters.into(face, t);
            setOnFocusChangeListener((v, f) -> {
                ((Ui.LiquidGlass) glass.getBackground()).setFocused(f && !up);
                Ui.springScale(this, f ? 1.08f : 1f);
                setElevation(f ? Ui.dp(c, 14) : Ui.dp(c, 2));
            });
            setOnClickListener(v -> tap(this));
        }

        void flip(boolean show, Runnable after) {
            animate().rotationY(90).setDuration(140).withEndAction(() -> {
                up = show;
                face.setVisibility(show ? VISIBLE : INVISIBLE);
                for (int i = 1; i < getChildCount(); i++) getChildAt(i).setVisibility(show ? INVISIBLE : VISIBLE);
                setRotationY(-90);
                animate().rotationY(0).setDuration(160).withEndAction(after).start();
            }).start();
        }
    }

    MemoryGame(Context c) {
        super(c);
        prefs = c.getSharedPreferences("games", Context.MODE_PRIVATE);
        best = prefs.getInt("memory_best", 0);
        setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF0A0F24, 0xFF140A24, 0xFF05060A}));
        setClipChildren(false);

        LinearLayout side = new LinearLayout(c);
        side.setOrientation(LinearLayout.VERTICAL);
        side.addView(Ui.kicker(c, "Games"));
        TextView title = Ui.text(c, R.font.montserrat_extrabold, 34, 0xFFFFFFFF);
        title.setText("Poster\nMemory");
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = Ui.dp(c, 10);
        side.addView(title, tl);
        TextView how = Ui.text(c, R.font.outfit_light, 14, 0x99FFFFFF);
        how.setText("Find the 8 pairs.\nOK flips a card.");
        how.setLineSpacing(Ui.dp(c, 4), 1f);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.topMargin = Ui.dp(c, 14);
        side.addView(how, hl);
        stats = Ui.text(c, R.font.montserrat_bold, 15, 0xFFFFFFFF);
        stats.setLineSpacing(Ui.dp(c, 8), 1f);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = Ui.dp(c, 28);
        side.addView(stats, sl);
        FrameLayout.LayoutParams sdl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sdl.leftMargin = Ui.dp(c, 60);
        sdl.topMargin = Ui.dp(c, 60);
        addView(side, sdl);

        grid = new GridLayout(c);
        grid.setColumnCount(COLS);
        grid.setRowCount(ROWS);
        grid.setClipChildren(false);
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.END);
        gl.rightMargin = Ui.dp(c, 110);
        addView(grid, gl);

        banner = Ui.text(c, R.font.montserrat_extrabold, 30, 0xFFFFFFFF);
        banner.setGravity(Gravity.CENTER);
        banner.setPadding(Ui.dp(c, 36), Ui.dp(c, 24), Ui.dp(c, 36), Ui.dp(c, 24));
        banner.setBackground(new Ui.LiquidGlass(c, 26).tint(0x3DDC97, 1.2f));
        banner.setVisibility(GONE);
        addView(banner, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        deal();
        tick();
    }

    private void deal() {
        grid.removeAllViews();
        cards.clear();
        List<Catalog.Title> pool = new ArrayList<>(Catalog.all(getContext()));
        Collections.shuffle(pool, new Random());
        List<Catalog.Title> picks = new ArrayList<>();
        for (Catalog.Title t : pool) {
            // Only titles with a real poster: the typographic ones would make pairs too easy to spot.
            try {
                getContext().getAssets().open("posters/" + t.slug + ".jpg").close();
                picks.add(t);
            } catch (Exception ignored) {
            }
            if (picks.size() == 8) break;
        }
        // Posters not fetched yet (tools/fetch_posters.py): play with the lettered cards instead.
        for (Catalog.Title t : pool) {
            if (picks.size() == 8) break;
            if (!picks.contains(t)) picks.add(t);
        }
        List<Catalog.Title> deck = new ArrayList<>(picks);
        deck.addAll(picks);
        Collections.shuffle(deck, new Random());
        int w = Ui.dp(getContext(), 78), hgt = Ui.dp(getContext(), 104), m = Ui.dp(getContext(), 7);
        for (Catalog.Title t : deck) {
            Card card = new Card(getContext(), t);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = w;
            lp.height = hgt;
            lp.setMargins(m, m, m, m);
            grid.addView(card, lp);
            cards.add(card);
        }
        first = second = null;
        moves = matched = 0;
        started = SystemClock.uptimeMillis();
        locked = false;
        banner.setVisibility(GONE);
        cards.get(0).post(() -> cards.get(0).requestFocus());
    }

    private void tap(Card c) {
        if (banner.getVisibility() == VISIBLE) {
            deal();
            return;
        }
        if (locked || c.up || c.done) return;
        if (first == null) {
            first = c;
            c.flip(true, null);
        } else {
            second = c;
            moves++;
            locked = true;
            c.flip(true, () -> {
                if (first.title == second.title) {
                    for (Card k : new Card[]{first, second}) {
                        k.done = true;
                        GradientDrawable ring = new GradientDrawable();
                        ring.setCornerRadius(Ui.dp(getContext(), 14));
                        ring.setStroke(Ui.dp(getContext(), 3), 0xFF3DDC97);
                        k.setForeground(ring);
                        k.animate().scaleX(1.12f).scaleY(1.12f).setDuration(140).withEndAction(() -> Ui.springScale(k, 1f)).start();
                    }
                    matched++;
                    first = second = null;
                    locked = false;
                    if (matched == 8) win();
                } else {
                    h.postDelayed(() -> {
                        first.flip(false, null);
                        second.flip(false, () -> {
                            first = second = null;
                            locked = false;
                        });
                    }, 650);
                }
                updateStats();
            });
        }
        updateStats();
    }

    private void win() {
        int stars = moves <= 12 ? 3 : moves <= 18 ? 2 : 1;
        boolean record = best == 0 || moves < best;
        if (record) {
            best = moves;
            prefs.edit().putInt("memory_best", best).apply();
        }
        long secs = (SystemClock.uptimeMillis() - started) / 1000;
        banner.setText("★★★".substring(0, stars) + "☆☆☆".substring(stars) + "\nAll pairs in " + moves + " moves, " + secs + "s"
                + (record ? "\nNew best!" : "") + "\n\nOK to play again");
        banner.setTextSize(22);
        banner.setVisibility(VISIBLE);
        banner.setScaleX(0.8f);
        banner.setScaleY(0.8f);
        Ui.springScale(banner, 1f);
    }

    private void tick() {
        updateStats();
        h.postDelayed(this::tick, 1000);
    }

    private void updateStats() {
        long secs = (SystemClock.uptimeMillis() - started) / 1000;
        stats.setText("MOVES   " + moves + "\nPAIRS   " + matched + " / 8\nTIME    " + secs / 60 + ":" + String.format("%02d", secs % 60)
                + "\nBEST    " + (best == 0 ? "—" : best + " moves"));
    }

    @Override
    protected void onDetachedFromWindow() {
        h.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }
}
