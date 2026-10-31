package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * For You: a cinematic hero for the focused title over its own colours, and rows of posters picked from the
 * household's taste (Taste). OK opens the title in its app; the pills open the swipe deck and the assistant.
 */
public class DiscoverActivity extends Activity {

    private ImageView backA, backB;
    private boolean showingA = true;
    private TextView heroTitle, heroMeta, heroPlatform;
    private View platformDot;
    private LinearLayout hero;
    private RecyclerView rows;
    private Taste taste;
    private Catalog.Title shown;
    private View swipePill;

    private static final class Row {
        final String label;
        final List<Catalog.Title> items;
        Row(String label, List<Catalog.Title> items) { this.label = label; this.items = items; }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        taste = new Taste(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        backA = backdropView();
        backB = backdropView();
        root.addView(backA);
        root.addView(backB);

        View scrimLeft = new View(this);
        scrimLeft.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF2000000, 0xB3000000, 0x33000000}));
        root.addView(scrimLeft, match());
        View scrimBottom = new View(this);
        scrimBottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x99000000, 0xF2000000}));
        root.addView(scrimBottom, match());

        // Hero: kicker, big title, meta line, platform chip.
        hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(Ui.dp(this, 560), ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.leftMargin = Ui.dp(this, 60);
        hl.topMargin = Ui.dp(this, 40);
        String name = getString(R.string.greeting_name).trim();
        hero.addView(Ui.kicker(this, name.isEmpty() ? "For you" : "For you  ·  " + name));
        heroTitle = Ui.text(this, R.font.montserrat_extrabold, 40, 0xFFFFFFFF);
        heroTitle.setMaxLines(2);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        heroTitle.setLetterSpacing(-0.01f);
        heroTitle.setShadowLayer(12, 0, 2, 0x99000000);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = Ui.dp(this, 12);
        hero.addView(heroTitle, tl);
        heroMeta = Ui.text(this, R.font.outfit_regular, 15, 0xCCFFFFFF);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ml.topMargin = Ui.dp(this, 12);
        hero.addView(heroMeta, ml);

        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 14), 0);
        chip.setBackground(new Ui.LiquidGlass(this, 14));
        platformDot = new View(this);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        platformDot.setBackground(dot);
        chip.addView(platformDot, new LinearLayout.LayoutParams(Ui.dp(this, 8), Ui.dp(this, 8)));
        heroPlatform = Ui.text(this, R.font.montserrat_bold, 12, 0xFFFFFFFF);
        heroPlatform.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pl.leftMargin = Ui.dp(this, 8);
        chip.addView(heroPlatform, pl);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 28));
        cl.topMargin = Ui.dp(this, 14);
        hero.addView(chip, cl);
        TextView hint = Ui.text(this, R.font.outfit_light, 12, 0x80FFFFFF);
        hint.setText("OK  to watch     ·     Hold OK  to like");
        hint.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams hil = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hil.topMargin = Ui.dp(this, 14);
        hero.addView(hint, hil);
        root.addView(hero, hl);

        // Top right: the other two experiences.
        LinearLayout pills = new LinearLayout(this);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        TextView swipe = Ui.glassButton(this, "Swipe & rate");
        TextView ask = Ui.glassButton(this, "Ask Jarvis");
        swipe.setOnClickListener(v -> startActivity(new Intent(this, SwipeActivity.class)));
        ask.setOnClickListener(v -> startActivity(new Intent(this, AssistantActivity.class)));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 44));
        sp.leftMargin = Ui.dp(this, 12);
        pills.addView(swipe, sp);
        pills.addView(ask, new LinearLayout.LayoutParams(sp));
        FrameLayout.LayoutParams ppl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        ppl.topMargin = Ui.dp(this, 36);
        ppl.rightMargin = Ui.dp(this, 56);
        root.addView(pills, ppl);
        swipePill = swipe;
        View.OnKeyListener pillKeys = (v, k, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (k == KeyEvent.KEYCODE_DPAD_DOWN) { focusFirstPoster(); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_UP) return true;
            return (k == KeyEvent.KEYCODE_DPAD_LEFT && v == swipe) || (k == KeyEvent.KEYCODE_DPAD_RIGHT && v == ask);
        };
        swipe.setOnKeyListener(pillKeys);
        ask.setOnKeyListener(pillKeys);

        rows = new RecyclerView(this);
        rows.setLayoutManager(new LinearLayoutManager(this));
        rows.setClipToPadding(false);
        rows.setClipChildren(false);
        rows.setPadding(0, 0, 0, Ui.dp(this, 120));
        rows.setItemAnimator(null);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        rl.topMargin = Ui.dp(this, 250);
        root.addView(rows, rl);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Taste may have changed in the swipe deck: rebuild the rows each time this screen comes back.
        rows.setAdapter(new RowsAdapter(buildRows()));
        rows.post(this::focusFirstPoster);
    }

    private void focusFirstPoster() {
        rows.scrollToPosition(0);
        rows.post(() -> {
            RecyclerView.ViewHolder first = rows.findViewHolderForAdapterPosition(0);
            if (first != null) {
                RecyclerView strip = first.itemView.findViewById(R.id.poster_tag);
                if (strip != null && strip.getChildCount() > 0) strip.getChildAt(0).requestFocus();
            }
        });
    }

    private List<Row> buildRows() {
        List<Row> out = new ArrayList<>();
        out.add(new Row("Top picks for you", cap(interleave(taste.ranked(null, false)), 16)));
        List<String> top = taste.topGenres();
        for (int i = 0; i < 4; i++) {
            String g = top.get(i);
            out.add(new Row("Because you love " + Catalog.genreLabel(g), cap(taste.ranked(g, false), 14)));
        }
        out.add(new Row("Hindi hits", cap(byLang("hi"), 14)));
        out.add(new Row("English binge", cap(byLang("en"), 14)));
        List<Catalog.Title> liked = taste.likedTitles();
        if (!liked.isEmpty()) out.add(1, new Row("Your likes", liked));
        return out;
    }

    /** Hindi and English alternating, each in taste order, so both kinds of picks show up. */
    private static List<Catalog.Title> interleave(List<Catalog.Title> ranked) {
        List<Catalog.Title> hi = new ArrayList<>(), en = new ArrayList<>(), out = new ArrayList<>();
        for (Catalog.Title t : ranked) ("hi".equals(t.lang) ? hi : en).add(t);
        for (int i = 0; i < Math.max(hi.size(), en.size()); i++) {
            if (i < hi.size()) out.add(hi.get(i));
            if (i < en.size()) out.add(en.get(i));
        }
        return out;
    }

    private List<Catalog.Title> byLang(String lang) {
        List<Catalog.Title> o = new ArrayList<>();
        for (Catalog.Title t : taste.ranked(null, true)) if (lang.equals(t.lang)) o.add(t);
        return o;
    }

    private static <T> List<T> cap(List<T> l, int n) { return l.size() > n ? new ArrayList<>(l.subList(0, n)) : l; }

    private ImageView backdropView() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.FIT_XY);
        v.setAlpha(0f);
        v.setLayoutParams(match());
        return v;
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void showHero(Catalog.Title t) {
        if (t == shown) return;
        shown = t;
        heroTitle.setText(t.name);
        heroMeta.setText(t.meta());
        heroPlatform.setText(Catalog.platformLabel(t.platform).toUpperCase());
        ((GradientDrawable) platformDot.getBackground()).setColor(Catalog.platformColor(t.platform));
        hero.setTranslationX(Ui.dp(this, 10));
        hero.setAlpha(0.4f);
        hero.animate().translationX(0).alpha(1f).setDuration(260).start();
        Posters.load(this, t, bmp -> {
            if (shown != t) return;
            Bitmap small = Posters.backdrop(bmp);
            ImageView in = showingA ? backB : backA, out = showingA ? backA : backB;
            showingA = !showingA;
            in.setImageBitmap(small);
            in.animate().alpha(0.75f).setDuration(420).start();
            out.animate().alpha(0f).setDuration(420).start();
        });
    }

    private void play(Catalog.Title t) {
        if (!Watch.open(this, t)) {
            android.widget.Toast.makeText(this, Catalog.platformLabel(t.platform) + " is not installed", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void like(PosterCard card) {
        Catalog.Title t = card.title;
        boolean was = taste.liked().contains(t.slug);
        if (was) taste.forget(t, true);
        else taste.like(t);
        android.widget.Toast.makeText(this, was ? "Removed from your likes" : "♥  Added " + t.name + " to your likes",
                android.widget.Toast.LENGTH_SHORT).show();
        card.animate().scaleX(1.22f).scaleY(1.22f).setDuration(120).withEndAction(() -> Ui.springScale(card, 1.12f)).start();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    // --- rows ---

    private final class RowsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<Row> data;
        RowsAdapter(List<Row> d) { data = d; }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            LinearLayout row = new LinearLayout(DiscoverActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView label = Ui.text(DiscoverActivity.this, R.font.montserrat_bold, 16, 0xE6FFFFFF);
            label.setPadding(Ui.dp(DiscoverActivity.this, 60), 0, 0, 0);
            row.addView(label);
            RecyclerView strip = new RecyclerView(DiscoverActivity.this);
            strip.setId(R.id.poster_tag);
            strip.setLayoutManager(new LinearLayoutManager(DiscoverActivity.this, LinearLayoutManager.HORIZONTAL, false));
            strip.setClipToPadding(false);
            strip.setClipChildren(false);
            strip.setItemAnimator(null);
            int pad = Ui.dp(DiscoverActivity.this, 22);
            strip.setPadding(Ui.dp(DiscoverActivity.this, 52), pad, Ui.dp(DiscoverActivity.this, 300), pad);
            row.addView(strip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerView.ViewHolder(row) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            LinearLayout row = (LinearLayout) h.itemView;
            ((TextView) row.getChildAt(0)).setText(data.get(pos).label);
            RecyclerView strip = (RecyclerView) row.getChildAt(1);
            strip.setAdapter(new StripAdapter(data.get(pos).items));
        }

        @Override public int getItemCount() { return data.size(); }
    }

    private final class StripAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<Catalog.Title> items;
        StripAdapter(List<Catalog.Title> i) { items = i; }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            PosterCard card = new PosterCard(DiscoverActivity.this, 104);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(card.getLayoutParams().width, card.getLayoutParams().height);
            lp.leftMargin = lp.rightMargin = Ui.dp(DiscoverActivity.this, 8);
            card.setLayoutParams(lp);
            card.setOnFocusChangeListener((v, f) -> {
                card.setLit(f);
                if (f) {
                    showHero(card.title);
                    // Keep the focused row just under the hero.
                    View row = (View) v.getParent().getParent();
                    int target = 0;
                    rows.smoothScrollBy(0, row.getTop() - target);
                }
            });
            card.setOnClickListener(v -> play(card.title));
            // Up from the first row goes to the pills, not to whatever the focus finder picks.
            card.setOnKeyListener((v, k, e) -> {
                if (e.getAction() == KeyEvent.ACTION_DOWN && k == KeyEvent.KEYCODE_DPAD_UP
                        && rows.getChildAdapterPosition((View) v.getParent().getParent()) == 0) {
                    swipePill.requestFocus();
                    return true;
                }
                return false;
            });
            card.setOnLongClickListener(v -> {
                like(card);
                return true;
            });
            return new RecyclerView.ViewHolder(card) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            ((PosterCard) h.itemView).bind(items.get(pos));
        }

        @Override public int getItemCount() { return items.size(); }
    }
}
