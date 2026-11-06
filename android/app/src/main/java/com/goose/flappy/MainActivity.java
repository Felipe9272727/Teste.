package com.goose.flappy;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

/** Android lifecycle shell only. All world/gfx/controls are written in Goose. */
public final class MainActivity extends Activity {
    private FlappyGameView view;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        view = new FlappyGameView(this);
        setContentView(view);
    }
    @Override protected void onPause() {
        super.onPause();
        if (view != null) view.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        if (view != null) view.onResume();
    }
}
