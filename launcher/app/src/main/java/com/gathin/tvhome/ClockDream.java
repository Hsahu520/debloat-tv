package com.gathin.tvhome;

import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Random;

/**
 * Screensaver: only the salutation ("GOOD EVENING" and the name from tv.conf), the time, the date and the weather,
 * over a photo (assets/home2/screensaver.jpg) or, without one, a painted rainy window. The block drifts to a new spot every minute so nothing burns in.
 */
public class ClockDream extends DreamService {

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private LinearLayout block;
    private TextView greeting, nameLine, weather;
    private LinearLayout subs;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);

        FrameLayout root = new FrameLayout(this);
        root.addView(new RainGlassView(this, "home2/screensaver.jpg"), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        android.view.View dim = new android.view.View(this);
        // Dark on the left where the text sits, clear on the right so the picture shows.
        dim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xD9000000, 0x8C000000, 0x00000000}));
        root.addView(dim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.START);
        greeting = Ui.text(this, R.font.montserrat_bold, 22, 0xE6FFFFFF);
        greeting.setAllCaps(true);
        greeting.setSingleLine(true);
        block.addView(greeting);
        nameLine = Ui.text(this, R.font.montserrat_extrabold, 34, 0xFFFFFFFF);
        nameLine.setAllCaps(true);
        nameLine.setSingleLine(true);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nl.topMargin = Ui.dp(this, 4);
        block.addView(nameLine, nl);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTypeface(Ui.font(this, R.font.montserrat_extrabold));
        clock.setTextSize(110);
        clock.setTextColor(0xFFFFFFFF);
        clock.setIncludeFontPadding(false);
        clock.setShadowLayer(24, 0, 4, 0x80000000);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.topMargin = Ui.dp(this, 14);
        block.addView(clock, cl);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTypeface(Ui.font(this, R.font.montserrat_medium));
        date.setTextSize(20);
        date.setTextColor(0xCCFFFFFF);
        date.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dl.topMargin = Ui.dp(this, 10);
        block.addView(date, dl);
        weather = Ui.text(this, R.font.montserrat_medium, 18, 0xE6FFFFFF);
        weather.setPadding(Ui.dp(this, 20), Ui.dp(this, 10), Ui.dp(this, 20), Ui.dp(this, 10));
        weather.setBackground(new Ui.LiquidGlass(this, 22));
        weather.setText(Weather.city(this));
        if (Weather.city(this).isEmpty()) weather.setVisibility(android.view.View.GONE);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wl.topMargin = Ui.dp(this, 22);
        block.addView(weather, wl);
        // Streaming subscriptions and the days left on each (SUBSCRIPTIONS in tv.conf).
        subs = new LinearLayout(this);
        subs.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = Ui.dp(this, 14);
        block.addView(subs, sl);
        // Full width with centred children: a wrap_content column kept the width of its first layout and cut the
        // greeting once the name was filled in.
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.START);
        bl.leftMargin = Ui.dp(this, 80);
        root.addView(block, bl);
        setContentView(root);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        tick();
    }

    /** Once a minute: salutation for the hour, fresh weather (cached 30 min), and a gentle move. */
    private void tick() {
        int hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String part = hr >= 5 && hr < 12 ? "Good Morning" : hr >= 12 && hr < 17 ? "Good Afternoon"
                : hr >= 17 && hr < 21 ? "Good Evening" : "Good Night";
        String name = getString(R.string.greeting_name).trim();
        greeting.setText(part);
        nameLine.setText(name);
        nameLine.setVisibility(name.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        Weather.get(this, (temp, cond, city) -> weather.setText(temp + "   " + cond + "   ·   " + city));
        fillSubscriptions();
        float range = Ui.dp(this, 40);
        block.animate().translationX(rnd.nextFloat() * range).translationY((rnd.nextFloat() * 2 - 1) * range * 1.5f)
                .setDuration(4000).start();
        h.postDelayed(this::tick, 60_000);
    }

    private void fillSubscriptions() {
        subs.removeAllViews();
        for (Subscriptions.Sub sub : Subscriptions.parse(getString(R.string.subscriptions))) {
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.VERTICAL);
            chip.setPadding(Ui.dp(this, 16), Ui.dp(this, 9), Ui.dp(this, 16), Ui.dp(this, 9));
            chip.setBackground(new Ui.LiquidGlass(this, 18));
            TextView n = Ui.text(this, R.font.montserrat_bold, 13, 0xFFFFFFFF);
            n.setText(sub.name);
            n.setSingleLine(true);
            chip.addView(n);
            TextView d = Ui.text(this, R.font.montserrat_medium, 12, sub.color());
            d.setText(sub.label());
            d.setSingleLine(true);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dl.topMargin = Ui.dp(this, 3);
            chip.addView(d, dl);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (subs.getChildCount() > 0) cl.leftMargin = Ui.dp(this, 10);
            subs.addView(chip, cl);
        }
        subs.setVisibility(subs.getChildCount() == 0 ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    @Override
    public void onDreamingStopped() {
        h.removeCallbacksAndMessages(null);
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        h.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }
}
