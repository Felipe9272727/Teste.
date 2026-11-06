package com.goose.flappy;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Touch-first Android shell around a true native Goose game + OpenGL ES 3D scene. */
public final class MainActivity extends Activity {
    private FlappyGameView game;
    private TextView scoreLabel, recordLabel, centerTitle, centerHelp;
    private LinearLayout centerPanel;

    private TextView label(String text, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(color);
        v.setTextSize(size);
        v.setGravity(Gravity.CENTER);
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        v.setShadowLayer(2f, 0f, 2f, 0x66000000);
        return v;
    }

    private int dp(float d) { return (int) (d * getResources().getDisplayMetrics().density + .5f); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        FrameLayout root = new FrameLayout(this);
        game = new FlappyGameView(this);
        root.addView(game, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setGravity(Gravity.CENTER);
        scoreLabel = label("0", 50, Color.WHITE, true);
        recordLabel = label("RECORDE 0", 13, 0xFF275F6C, true);
        top.addView(scoreLabel, new LinearLayout.LayoutParams(-1, dp(64)));
        top.addView(recordLabel, new LinearLayout.LayoutParams(-1, dp(28)));
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(-1, dp(100), Gravity.TOP);
        topParams.topMargin = dp(23);
        root.addView(top, topParams);

        centerPanel = new LinearLayout(this);
        centerPanel.setOrientation(LinearLayout.VERTICAL);
        centerPanel.setGravity(Gravity.CENTER);
        centerPanel.setPadding(dp(20), dp(16), dp(20), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xDEFFFFFF);
        bg.setCornerRadius(dp(20));
        centerPanel.setBackground(bg);
        centerTitle = label("FLAPPY GOOSE 3D", 27, 0xFF185B6E, true);
        centerHelp = label("TOQUE PARA VOAR", 17, 0xFF286F5C, true);
        centerPanel.addView(centerTitle, new LinearLayout.LayoutParams(-1, dp(57)));
        centerPanel.addView(centerHelp, new LinearLayout.LayoutParams(-1, dp(44)));
        FrameLayout.LayoutParams panelParams =
                new FrameLayout.LayoutParams(dp(288), dp(144), Gravity.CENTER);
        root.addView(centerPanel, panelParams);

        // The GL renderer only crosses into Android UI on score/game-state changes.
        game.setStatusListener((mode, score, best) -> runOnUiThread(() -> {
            scoreLabel.setText(Integer.toString(score));
            recordLabel.setText("RECORDE " + best);
            if (mode == 1) {
                centerPanel.setVisibility(View.GONE);
            } else {
                centerPanel.setVisibility(View.VISIBLE);
                if (mode == 2) {
                    centerTitle.setText("FIM DE JOGO");
                    centerHelp.setText("TOQUE PARA RECOMEÇAR");
                } else {
                    centerTitle.setText("FLAPPY GOOSE 3D");
                    centerHelp.setText("TOQUE PARA VOAR");
                }
            }
        }));
        setContentView(root);
    }

    @Override protected void onPause() {
        super.onPause();
        if (game != null) game.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (game != null) game.onResume();
    }
}
