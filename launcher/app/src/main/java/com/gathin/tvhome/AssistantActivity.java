package com.gathin.tvhome;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Jarvis: listens through the remote's microphone (the TV's own speech service), shows the words live under the
 * HUD, then plays, opens, searches or suggests (Brain). Replies are spoken with the TV's text-to-speech voice.
 * OK listens again; Back leaves.
 */
public class AssistantActivity extends Activity {

    private static final String KATNISS = "com.google.android.katniss";
    private static final String KATNISS_RECOGNIZER = "com.google.android.apps.tvsearch.voice.recognition.KatnissRecognitionService";

    private final Handler h = new Handler(Looper.getMainLooper());
    private JarvisView hud;
    private TextView status, heard, reply, sideLeft, sideRight;
    private LinearLayout results;
    private SpeechRecognizer sr;
    private TextToSpeech tts;
    private boolean ttsReady, listening;
    private Brain brain;
    private Runnable pending;
    private int accent;
    private float rmsShown;
    private LinearLayout bottom;
    private boolean compact;

    private static final String[] EXAMPLES = {"Play Panchayat", "Kuch funny dikhao", "Hindi thriller suggest karo", "Open Netflix"};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        brain = new Brain(this);
        FrameLayout root = new FrameLayout(this);

        // The HUD and the backdrop render on their own thread, behind every other view.
        hud = new JarvisView(this);
        hud.place(Ui.dp(this, 44), Ui.dp(this, 330));
        root.addView(hud, match());

        // Top-left identity and status, top-right live readouts: the HUD frame.
        LinearLayout tl = new LinearLayout(this);
        tl.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.text(this, R.font.montserrat_extrabold, 22, 0xFFFFFFFF);
        // Spaced by hand: letterSpacing on this font under-measures the width and clips the last letters.
        name.setText("J . A . R . V . I . S .");
        name.setSingleLine(true);
        tl.addView(name, new LinearLayout.LayoutParams(Ui.dp(this, 380), ViewGroup.LayoutParams.WRAP_CONTENT));
        status = Ui.kicker(this, "Online");
        status.setTextColor(0xFF27C4FF);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = Ui.dp(this, 10);
        tl.addView(status, sl);
        sideLeft = Ui.text(this, R.font.montserrat_medium, 11, 0x8027C4FF);
        sideLeft.setLetterSpacing(0.18f);
        sideLeft.setLineSpacing(Ui.dp(this, 7), 1f);
        LinearLayout.LayoutParams sll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sll.topMargin = Ui.dp(this, 26);
        tl.addView(sideLeft, sll);
        FrameLayout.LayoutParams tll = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tll.leftMargin = Ui.dp(this, 60);
        tll.topMargin = Ui.dp(this, 44);
        root.addView(tl, tll);

        sideRight = Ui.text(this, R.font.montserrat_medium, 11, 0x8027C4FF);
        sideRight.setLetterSpacing(0.18f);
        sideRight.setGravity(Gravity.END);
        sideRight.setLineSpacing(Ui.dp(this, 7), 1f);
        FrameLayout.LayoutParams srl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        srl.rightMargin = Ui.dp(this, 60);
        srl.topMargin = Ui.dp(this, 48);
        root.addView(sideRight, srl);

        bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        heard = Ui.text(this, R.font.outfit_light, 28, 0xFFFFFFFF);
        heard.setGravity(Gravity.CENTER);
        heard.setMaxLines(2);
        bottom.addView(heard, new LinearLayout.LayoutParams(Ui.dp(this, 760), ViewGroup.LayoutParams.WRAP_CONTENT));
        reply = Ui.text(this, R.font.montserrat_medium, 15, 0xFF27C4FF);
        reply.setIncludeFontPadding(true);
        reply.setMinHeight(Ui.dp(this, 22));
        reply.setGravity(Gravity.CENTER);
        reply.setLetterSpacing(0.04f);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(Ui.dp(this, 760), ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.topMargin = Ui.dp(this, 12);
        bottom.addView(reply, rl);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.HORIZONTAL);
        results.setGravity(Gravity.CENTER);
        results.setClipChildren(false);
        results.setClipToPadding(false);
        LinearLayout.LayoutParams resl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        resl.topMargin = Ui.dp(this, 26);
        bottom.addView(results, resl);
        // A fixed height, so the column is never squeezed to what is left below its top margin when it lifts.
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 360), Gravity.TOP);
        bl.topMargin = Ui.dp(this, 384);
        bottom.setClipChildren(false);
        root.addView(bottom, bl);
        root.setClipChildren(false);

        setContentView(root);
        tts = new TextToSpeech(this, st -> {
            if (st == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("en", "IN"));
                tts.setPitch(0.92f);
                tts.setSpeechRate(1.04f);
                ttsReady = true;
            }
        });
        tickReadouts();
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    /** The small HUD readouts at the sides; once a second, cheap. */
    private void tickReadouts() {
        sideLeft.setText("SYS  ONLINE\nMIC  REMOTE\nLANG  EN-IN  ·  HI\nTITLES  " + Catalog.all(this).size());
        String bars = "▮▮▮▮▮▮▮▮▮▮".substring(0, Math.max(1, Math.round(rmsShown * 10)));
        sideRight.setText(DateFormat.format("hh:mm:ss a", new Date()) + "\n" + DateFormat.format("EEE dd MMM", new Date()).toString().toUpperCase()
                + "\nINPUT  " + (listening ? bars : "—") + "\nCORE  " + (listening ? "ACTIVE" : "STANDBY"));
        h.postDelayed(this::tickReadouts, 1000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String typed = getIntent().getStringExtra("q");
        if (typed != null) {
            getIntent().removeExtra("q");
            showIdle();
            h.postDelayed(() -> handle(typed), 400);
            return;
        }
        if (compact) return;   // back from an app with an answer on screen: keep it, OK talks again
        showIdle();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 7);
        } else {
            h.postDelayed(this::listen, 500);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        if (r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) listen();
        else fail("I need the microphone permission to hear you.");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (pending != null) h.removeCallbacks(pending);
        stopListening();
        if (tts != null) tts.stop();
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        if (sr != null) sr.destroy();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    private void showIdle() {
        accent = 0;
        hud.setState(JarvisView.State.IDLE, 0);
        status.setText("ONLINE  ·  PRESS OK TO TALK");
        status.setTextColor(0xFF27C4FF);
        heard.setText("How can I help, " + firstName() + "?");
        reply.setText("Try:  " + String.join("   ·   ", EXAMPLES));
        reply.setTextColor(0x9927C4FF);
        results.removeAllViews();
        setCompact(false);
    }

    /** With posters to show, the HUD shrinks and lifts so the row fits under it, the way Siri makes room. */
    private void setCompact(boolean on) {
        if (compact == on) return;
        compact = on;
        hud.setCompact(on, Ui.dp(this, 8));
        Ui.spring(bottom, androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_Y, on ? -Ui.dp(this, 150) : 0, 300f, 0.8f);
    }

    private String firstName() {
        String n = getString(R.string.greeting_name).trim();
        if (n.isEmpty()) return "boss";
        return n.split("[\\s\\-&]+")[0];
    }

    private SpeechRecognizer recognizer() {
        if (sr != null) return sr;
        // The TV's search service owns the remote's mic; ask it first, then whatever the system default is.
        try {
            if (getPackageManager().getServiceInfo(new ComponentName(KATNISS, KATNISS_RECOGNIZER), 0) != null) {
                sr = SpeechRecognizer.createSpeechRecognizer(this, new ComponentName(KATNISS, KATNISS_RECOGNIZER));
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // fall through
        }
        if (sr == null) sr = SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(listener);
        return sr;
    }

    private void listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            fail("Speech recognition is not available on this TV.");
            return;
        }
        if (tts != null) tts.stop();
        results.removeAllViews();
        setCompact(false);
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN");
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        listening = true;
        hud.setState(JarvisView.State.LISTENING, 0);
        status.setText("LISTENING…");
        status.setTextColor(0xFF3BF0FF);
        heard.setText("…");
        reply.setText("Speak now. Hold the remote close.");
        reply.setTextColor(0x993BF0FF);
        try {
            recognizer().startListening(i);
        } catch (RuntimeException e) {
            fail("Couldn't start the microphone.");
        }
    }

    private void stopListening() {
        listening = false;
        if (sr != null) {
            try {
                sr.cancel();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private final RecognitionListener listener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) { status.setText("LISTENING…"); }
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rms) {
            hud.setRms(rms);
            rmsShown = Math.max(0, Math.min(1, (rms + 2) / 10f));
        }
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {
            listening = false;
            hud.setState(JarvisView.State.THINKING, 0);
            status.setText("PROCESSING…");
            status.setTextColor(0xFFFFB020);
        }
        @Override public void onError(int error) {
            listening = false;
            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                fail("I didn't hear anything. Press OK and try again.");
            } else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                fail("I need the microphone permission to hear you.");
            } else {
                fail("Voice service error " + error + ". Press OK to retry.");
            }
        }
        @Override public void onResults(Bundle r) {
            listening = false;
            ArrayList<String> list = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (list == null || list.isEmpty()) {
                fail("I didn't catch that. Press OK and try again.");
                return;
            }
            handle(list.get(0));
        }
        @Override public void onPartialResults(Bundle r) {
            ArrayList<String> list = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (list != null && !list.isEmpty() && !list.get(0).isEmpty()) heard.setText("“" + list.get(0) + "”");
        }
        @Override public void onEvent(int type, Bundle params) {}
    };

    private void fail(String msg) {
        if (results.getChildCount() > 0) return;   // an answer is on screen; a late recogniser error must not hide it
        hud.setState(JarvisView.State.ERROR, 0);
        status.setText("STANDBY  ·  PRESS OK TO TALK");
        status.setTextColor(0xFFFF4D6D);
        reply.setText(msg);
        reply.setTextColor(0xFFFF8FA3);
        h.postDelayed(() -> {
            if (!listening) hud.setState(JarvisView.State.IDLE, 0);
        }, 1600);
    }

    /** Public for testing over ADB: am start with --es q "play panchayat" on a debuggable build. */
    void handle(String text) {
        android.util.Log.i("Jarvis", "handle: " + text);
        heard.setText("“" + text + "”");
        hud.setState(JarvisView.State.THINKING, 0);
        status.setText("PROCESSING…");
        h.postDelayed(() -> act(brain.think(text)), 650);
    }

    private void act(Brain.Answer a) {
        accent = a.title != null ? Catalog.platformColor(a.platform) : a.platform != null ? Catalog.platformColor(a.platform) : 0xFF3DDC97;
        hud.setState(JarvisView.State.ANSWER, accent);
        status.setText("RESPONDING");
        status.setTextColor(accent);
        reply.setText(a.say);
        reply.setTextColor(lighten(accent));
        say(a.say);
        results.removeAllViews();

        setCompact(a.title != null || a.picks != null || a.choices != null);
        if (a.title != null) {
            PosterCard card = addPoster(a.title);
            card.requestFocus();
            final Catalog.Title t = a.title;
            final String platform = a.platform;
            pending = () -> {
                if (platform.equals(t.platform)) Watch.open(this, t);
                else Watch.search(this, platform, t.name);
            };
            h.postDelayed(pending, 2200);
        } else if (a.openApp) {
            pending = () -> Watch.launch(this, Watch.pkg(a.platform));
            h.postDelayed(pending, 1200);
        } else if (a.query != null && a.platform != null) {
            pending = () -> Watch.search(this, a.platform, a.query);
            h.postDelayed(pending, 1600);
        } else if (a.picks != null) {
            for (Catalog.Title t : a.picks) addPoster(t);
            if (results.getChildCount() > 0) results.getChildAt(0).requestFocus();
        } else if (a.choices != null) {
            for (String p : a.choices) {
                TextView b = Ui.glassButton(this, Catalog.platformLabel(p));
                b.setOnClickListener(v -> Watch.search(this, p, a.query));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 44));
                lp.leftMargin = lp.rightMargin = Ui.dp(this, 8);
                results.addView(b, lp);
            }
            results.getChildAt(0).requestFocus();
        } else {
            hud.setState(JarvisView.State.ERROR, 0);
        }
    }

    private PosterCard addPoster(Catalog.Title t) {
        PosterCard card = new PosterCard(this, 84);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(card.getLayoutParams().width, card.getLayoutParams().height);
        lp.leftMargin = lp.rightMargin = Ui.dp(this, 9);
        card.bind(t);
        card.setOnFocusChangeListener((v, f) -> card.setLit(f));
        card.setOnClickListener(v -> {
            if (pending != null) h.removeCallbacks(pending);
            Watch.open(this, t);
        });
        card.setAlpha(0f);
        card.setTranslationY(Ui.dp(this, 20));
        card.animate().alpha(1f).translationY(0).setStartDelay(results.getChildCount() * 60L).setDuration(300).start();
        results.addView(card, lp);
        return card;
    }

    private static int lighten(int c) {
        int r = (((c >> 16) & 0xFF) + 255) / 2, g = (((c >> 8) & 0xFF) + 255) / 2, b = ((c & 0xFF) + 255) / 2;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void say(String s) {
        if (ttsReady) tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "jarvis");
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        View f = getCurrentFocus();
        boolean onResult = f != null && f.getParent() == results;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (onResult) return super.onKeyDown(keyCode, e);
                if (pending != null) h.removeCallbacks(pending);
                listen();
                return true;
            case KeyEvent.KEYCODE_SEARCH:
            case KeyEvent.KEYCODE_VOICE_ASSIST:
            case KeyEvent.KEYCODE_ASSIST:
                if (pending != null) h.removeCallbacks(pending);
                listen();
                return true;
            case KeyEvent.KEYCODE_BACK:
                finish();
                return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String q = intent.getStringExtra("q");
        android.util.Log.i("Jarvis", "onNewIntent q=" + q);
        if (q != null) {
            stopListening();
            h.removeCallbacksAndMessages(null);
            tickReadouts();
            handle(q);
        }
    }
}
