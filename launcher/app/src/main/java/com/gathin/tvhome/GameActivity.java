package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

/** Hosts one game full screen: "memory" or "synth" in the "game" extra. Back returns to the hub. */
public class GameActivity extends Activity {

    private GameSurface surface;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String id = getIntent().getStringExtra("game");
        View v;
        if ("synth".equals(id)) v = surface = new SynthwaveGame(this);
        else v = new MemoryGame(this);
        setContentView(v);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        if (surface != null && e.getRepeatCount() == 0) {
            surface.key(keyCode);
            return true;
        }
        return super.onKeyDown(keyCode, e);
    }
}
