package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Swipe & rate: one poster at a time. Right = love it, Left = not for me, Up = undo, Down = skip, OK = watch now.
 * Every vote reshapes the For You rows and the taste meter at the bottom.
 */
public class SwipeActivity extends Activity {

    private static final int CARD_W = 270;
    private FrameLayout stage;
    private ImageView backdrop;
    private TextView counts, empty;
    private LinearLayout meter;
    private Taste taste;
    private List<Catalog.Title> deck;
    private int index;
    private final Deque<Object[]> history = new ArrayDeque<>();
    private boolean busy;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        taste = new Taste(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.FIT_XY);
        backdrop.setAlpha(0.6f);
        root.addView(backdrop, match());
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xB3000000, 0x66000000, 0xE6000000}));
        root.addView(scrim, match());

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.addView(Ui.kicker(this, "Swipe & rate"));
        TextView h1 = Ui.text(this, R.font.montserrat_extrabold, 26, 0xFFFFFFFF);
        h1.setText("Love it or\nleave it");
        h1.setLineSpacing(0, 1.05f);
        LinearLayout.LayoutParams h1l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        h1l.topMargin = Ui.dp(this, 10);
        head.addView(h1, h1l);
        TextView keys = Ui.text(this, R.font.outfit_light, 13, 0x99FFFFFF);
        keys.setText("◀  Nope        ▶  Love\nOK  Watch now\n▲  Undo        ▼  Skip");
        keys.setLineSpacing(Ui.dp(this, 6), 1f);
        keys.setLetterSpacing(0.04f);
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        kl.topMargin = Ui.dp(this, 10);
        head.addView(keys, kl);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.leftMargin = Ui.dp(this, 60);
        hl.topMargin = Ui.dp(this, 40);
        root.addView(head, hl);

        counts = Ui.text(this, R.font.montserrat_bold, 15, 0xFFFFFFFF);
        counts.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 10));
        counts.setBackground(new Ui.LiquidGlass(this, 20));
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        cl.topMargin = Ui.dp(this, 44);
        cl.rightMargin = Ui.dp(this, 56);
        root.addView(counts, cl);

        root.addView(sideHint("✕", "NOPE", 0xFFFF4D6D), sideParams(Gravity.START));
        root.addView(sideHint("♥", "LOVE", 0xFF3DDC97), sideParams(Gravity.END));

        stage = new FrameLayout(this);
        stage.setClipChildren(false);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(Ui.dp(this, CARD_W), Ui.dp(this, CARD_W * 1.5f), Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        sl.topMargin = Ui.dp(this, 84);
        root.addView(stage, sl);

        empty = Ui.text(this, R.font.montserrat_bold, 20, 0xFFFFFFFF);
        empty.setGravity(Gravity.CENTER);
        empty.setText("You've rated every title! ✨\nYour For You rows are now tuned to you.");
        empty.setVisibility(View.GONE);
        root.addView(empty, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        meter = new LinearLayout(this);
        meter.setOrientation(LinearLayout.HORIZONTAL);
        meter.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        ml.bottomMargin = Ui.dp(this, 26);
        root.addView(meter, ml);

        setContentView(root);
        deck = taste.unseen();
        index = 0;
        layoutStack(false);
        refreshMeter();
    }

    private View sideHint(String glyph, String word, int color) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        TextView g = Ui.text(this, R.font.montserrat_extrabold, 30, color);
        g.setText(glyph);
        g.setGravity(Gravity.CENTER);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor((color & 0xFFFFFF) | 0x22000000);
        ring.setStroke(Ui.dp(this, 1.5f), (color & 0xFFFFFF) | 0x99000000);
        g.setBackground(ring);
        l.addView(g, new LinearLayout.LayoutParams(Ui.dp(this, 76), Ui.dp(this, 76)));
        TextView w = Ui.kicker(this, word);
        w.setTextColor(color);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wl.topMargin = Ui.dp(this, 10);
        l.addView(w, wl);
        return l;
    }

    private FrameLayout.LayoutParams sideParams(int side) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, side | Gravity.CENTER_VERTICAL);
        p.leftMargin = p.rightMargin = Ui.dp(this, 190);
        p.topMargin = Ui.dp(this, 40);
        return p;
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    /** Up to three cards: the top one full size, two peeking behind it smaller and dimmer. */
    private void layoutStack(boolean animateIn) {
        stage.removeAllViews();
        int n = Math.min(3, deck.size() - index);
        empty.setVisibility(n == 0 ? View.VISIBLE : View.GONE);
        for (int k = n - 1; k >= 0; k--) {
            View card = makeCard(deck.get(index + k));
            float s = 1f - 0.07f * k;
            card.setScaleX(s);
            card.setScaleY(s);
            card.setTranslationY(-Ui.dp(this, 30) * k);
            card.setAlpha(k == 0 ? 1f : 0.55f - 0.15f * k);
            stage.addView(card);
            if (k == 0 && animateIn) {
                card.setScaleX(0.93f);
                card.setScaleY(0.93f);
                card.setTranslationY(-Ui.dp(this, 30));
                Ui.spring(card, DynamicAnimation.SCALE_X, 1f, SpringForce.STIFFNESS_MEDIUM, 0.6f);
                Ui.spring(card, DynamicAnimation.SCALE_Y, 1f, SpringForce.STIFFNESS_MEDIUM, 0.6f);
                Ui.spring(card, DynamicAnimation.TRANSLATION_Y, 0f, SpringForce.STIFFNESS_MEDIUM, 0.6f);
            }
        }
        if (n > 0) {
            Catalog.Title t = deck.get(index);
            Posters.load(this, t, bmp -> {
                if (index < deck.size() && deck.get(index) == t) backdrop.setImageBitmap(Posters.backdrop(bmp));
            });
        }
        counts.setText("♥ " + taste.liked().size() + "     ✕ " + taste.disliked().size() + "     " + Math.max(0, deck.size() - index) + " left");
    }

    private View makeCard(Catalog.Title t) {
        FrameLayout card = new FrameLayout(this);
        card.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        float radius = Ui.dp(this, 22);
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius); }
        });
        card.setElevation(Ui.dp(this, 24));
        card.setBackgroundColor(0xFF11141C);
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Posters.into(img, t);
        card.addView(img, match());
        View fade = new View(this);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x00000000, 0xE6000000}));
        card.addView(fade, match());

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 16));
        info.setBackground(new Ui.LiquidGlass(this, 18));
        TextView name = Ui.text(this, R.font.montserrat_extrabold, 20, 0xFFFFFFFF);
        name.setText(t.name);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(name);
        TextView meta = Ui.text(this, R.font.outfit_regular, 12, 0xCCFFFFFF);
        meta.setText(t.meta() + "  ·  " + Catalog.platformLabel(t.platform));
        meta.setMaxLines(2);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ml.topMargin = Ui.dp(this, 6);
        info.addView(meta, ml);
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        il.leftMargin = il.rightMargin = il.bottomMargin = Ui.dp(this, 10);
        card.addView(info, il);

        TextView stamp = Ui.text(this, R.font.montserrat_extrabold, 30, 0xFFFFFFFF);
        stamp.setLetterSpacing(0.12f);
        stamp.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
        stamp.setAlpha(0f);
        stamp.setTag("stamp");
        FrameLayout.LayoutParams stl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        stl.topMargin = Ui.dp(this, 40);
        card.addView(stamp, stl);
        return card;
    }

    private void vote(boolean love) {
        if (busy || index >= deck.size()) return;
        busy = true;
        Catalog.Title t = deck.get(index);
        if (love) taste.like(t);
        else taste.dislike(t);
        history.push(new Object[]{t, love});
        View top = stage.getChildAt(stage.getChildCount() - 1);
        TextView stamp = top.findViewWithTag("stamp");
        int color = love ? 0xFF3DDC97 : 0xFFFF4D6D;
        stamp.setText(love ? "LOVE" : "NOPE");
        stamp.setTextColor(color);
        GradientDrawable box = new GradientDrawable();
        box.setCornerRadius(Ui.dp(this, 10));
        box.setStroke(Ui.dp(this, 3), color);
        stamp.setBackground(box);
        stamp.setRotation(love ? -12 : 12);
        stamp.animate().alpha(1f).setDuration(120).start();
        float dir = love ? 1 : -1;
        top.animate().translationX(dir * Ui.dp(this, 720)).translationY(Ui.dp(this, 40)).rotation(dir * 22f)
                .alpha(0f).setStartDelay(160).setDuration(360).setInterpolator(new AccelerateInterpolator(1.4f))
                .withEndAction(() -> {
                    index++;
                    busy = false;
                    layoutStack(true);
                    refreshMeter();
                }).start();
    }

    private void skip() {
        if (busy || index >= deck.size()) return;
        busy = true;
        View top = stage.getChildAt(stage.getChildCount() - 1);
        top.animate().translationY(Ui.dp(this, 500)).alpha(0f).setDuration(300).withEndAction(() -> {
            deck.add(deck.remove(index));   // to the back of the deck
            busy = false;
            layoutStack(true);
        }).start();
    }

    private void undo() {
        if (busy || history.isEmpty()) return;
        Object[] last = history.pop();
        Catalog.Title t = (Catalog.Title) last[0];
        taste.forget(t, (Boolean) last[1]);
        index = Math.max(0, index - 1);
        deck.add(index, t);
        if (index + 1 < deck.size() && deck.get(index + 1) == t) deck.remove(index + 1);
        layoutStack(true);
        refreshMeter();
    }

    /** Six small glass chips, one per genre, filled to its weight: the household's taste at a glance. */
    private void refreshMeter() {
        meter.removeAllViews();
        for (String g : taste.topGenres()) {
            FrameLayout chip = new FrameLayout(this);
            chip.setBackground(new Ui.LiquidGlass(this, 14));
            float w = taste.weight(g);
            View fill = new View(this);
            GradientDrawable f = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{(Posters.genreColor(g) & 0xFFFFFF) | 0x99000000, (Posters.genreColor(g) & 0xFFFFFF) | 0x33000000});
            f.setCornerRadius(Ui.dp(this, 14));
            fill.setBackground(f);
            int width = Ui.dp(this, 112);
            chip.addView(fill, new FrameLayout.LayoutParams(Math.round(width * Math.min(1f, w / 3f)), ViewGroup.LayoutParams.MATCH_PARENT));
            TextView label = Ui.text(this, R.font.montserrat_bold, 11, 0xFFFFFFFF);
            label.setLetterSpacing(0.1f);
            label.setText(Catalog.genreLabel(g).toUpperCase());
            label.setGravity(Gravity.CENTER);
            chip.addView(label, match());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, Ui.dp(this, 28));
            lp.leftMargin = lp.rightMargin = Ui.dp(this, 5);
            meter.addView(chip, lp);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT: vote(true); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: vote(false); return true;
            case KeyEvent.KEYCODE_DPAD_UP: undo(); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: skip(); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (index < deck.size()) Watch.open(this, deck.get(index));
                else startActivity(new Intent(this, DiscoverActivity.class));
                return true;
            case KeyEvent.KEYCODE_MENU:   // start the taste profile over
                getSharedPreferences("taste", MODE_PRIVATE).edit().clear().apply();
                history.clear();
                deck = taste.unseen();
                index = 0;
                layoutStack(true);
                refreshMeter();
                android.widget.Toast.makeText(this, "Taste reset. Swipe away!", android.widget.Toast.LENGTH_SHORT).show();
                return true;
            case KeyEvent.KEYCODE_BACK: finish(); return true;
        }
        return super.onKeyDown(keyCode, e);
    }
}
