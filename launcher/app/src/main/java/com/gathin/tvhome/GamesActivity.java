package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The games hub: three big glass cards, each with its own art and best score. OK opens the game (GameActivity);
 * inside a game, Back comes here.
 */
public class GamesActivity extends Activity {

    private LinearLayout row;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF0A0F24, 0xFF160A2A, 0xFF05060A}));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.addView(Ui.kicker(this, "Game night"));
        TextView h1 = Ui.text(this, R.font.montserrat_extrabold, 34, 0xFFFFFFFF);
        h1.setText("Pick a game");
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.topMargin = Ui.dp(this, 10);
        head.addView(h1, hl);
        FrameLayout.LayoutParams hdl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hdl.leftMargin = Ui.dp(this, 60);
        hdl.topMargin = Ui.dp(this, 44);
        root.addView(head, hdl);

        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setClipChildren(false);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL);
        rl.topMargin = Ui.dp(this, 40);
        root.addView(row, rl);
        root.setClipChildren(false);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Rebuilt each time so the best scores are fresh after a game.
        row.removeAllViews();
        android.content.SharedPreferences p = getSharedPreferences("games", MODE_PRIVATE);
        int memBest = p.getInt("memory_best", 0);
        row.addView(card("memory", "Poster Memory", "Match 8 pairs of posters",
                memBest == 0 ? "NEW" : "BEST " + memBest + " MOVES", new int[]{0xFF1E2A5A, 0xFF5A1E44}, "✦"));
        row.addView(card("synth", "Synthwave Run", "Neon lanes, endless sunset",
                "BEST " + p.getInt("synth_best", 0), new int[]{0xFF2B0A4A, 0xFFFF2BD6}, "▲"));
        row.getChildAt(0).requestFocus();
    }

    private View card(String id, String name, String sub, String score, int[] colors, String glyph) {
        FrameLayout c = new FrameLayout(this);
        float r = Ui.dp(this, 26);
        c.setClipToOutline(true);
        c.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        });
        c.setFocusable(true);
        c.setClickable(true);
        c.setElevation(Ui.dp(this, 4));
        c.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors));

        TextView art = Ui.text(this, R.font.montserrat_extrabold, 84, 0x40FFFFFF);
        art.setText(glyph);
        art.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams al = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 170), Gravity.TOP);
        c.addView(art, al);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 18));
        info.setBackground(new Ui.LiquidGlass(this, 20));
        TextView n = Ui.text(this, R.font.montserrat_extrabold, 20, 0xFFFFFFFF);
        n.setText(name);
        info.addView(n);
        TextView s = Ui.text(this, R.font.outfit_regular, 13, 0xCCFFFFFF);
        s.setText(sub);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = Ui.dp(this, 6);
        info.addView(s, sl);
        TextView sc = Ui.kicker(this, score);
        sc.setTextSize(11);
        sc.setTextColor(0xFFFFD23F);
        LinearLayout.LayoutParams scl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scl.topMargin = Ui.dp(this, 12);
        info.addView(sc, scl);
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        il.leftMargin = il.rightMargin = il.bottomMargin = Ui.dp(this, 12);
        c.addView(info, il);

        View rim = new View(this);
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(r);
        ring.setStroke(Ui.dp(this, 2.5f), 0xFFFFFFFF);
        rim.setBackground(ring);
        rim.setAlpha(0f);
        c.addView(rim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        c.setOnFocusChangeListener((v, f) -> {
            Ui.springScale(v, f ? 1.08f : 1f);
            v.setElevation(Ui.dp(this, f ? 24 : 4));
            rim.animate().alpha(f ? 1f : 0f).setDuration(160).start();
            ((Ui.LiquidGlass) info.getBackground()).setFocused(false);
        });
        c.setOnClickListener(v -> startActivity(new Intent(this, GameActivity.class).putExtra("game", id)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 230), Ui.dp(this, 300));
        lp.leftMargin = lp.rightMargin = Ui.dp(this, 16);
        c.setLayoutParams(lp);
        return c;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, e);
    }
}
