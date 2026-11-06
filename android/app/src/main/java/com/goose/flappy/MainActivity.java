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

/** Landscape mobile shell. All game state is still calculated by the native Goose library. */
public final class MainActivity extends Activity {
    private FlappyGameView world;
    private TextView scoreText, distanceText;

    private int dp(float n) {
        return (int)(n * getResources().getDisplayMetrics().density + .5f);
    }

    private TextView text(String value, int size, boolean bold, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(android.graphics.Typeface.create(
            bold ? "sans-serif-black" : "sans-serif-medium",
            bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private GradientDrawable panel() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xC5204B66);
        d.setCornerRadius(dp(15));
        d.setStroke(dp(1), 0x448ED6E4);
        return d;
    }

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF78BFDA);
        world = new FlappyGameView(this);
        root.addView(world, new FrameLayout.LayoutParams(-1, -1));

        ControlsView controls = new ControlsView(this);
        root.addView(controls, new FrameLayout.LayoutParams(-1, -1));
        world.setControls(controls);

        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.VERTICAL);
        title.setPadding(dp(18), dp(5), dp(18), dp(7));
        title.setBackground(panel());
        title.addView(text("GOOSE SKIES", 22, true, Color.WHITE));
        title.addView(text("EXPLORE O MUNDO • COLETE ANÉIS DOURADOS", 10, false, 0xFFFFE8AA));
        FrameLayout.LayoutParams tparams =
            new FrameLayout.LayoutParams(dp(320), dp(67), Gravity.TOP | Gravity.LEFT);
        tparams.leftMargin = dp(15);
        tparams.topMargin = dp(15);
        root.addView(title, tparams);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.VERTICAL);
        stats.setPadding(dp(13), dp(6), dp(12), dp(7));
        stats.setBackground(panel());
        scoreText = text("ANÉIS  0", 20, true, 0xFFFFD574);
        distanceText = text("DISTÂNCIA  0 m", 11, false, Color.WHITE);
        stats.addView(scoreText);
        stats.addView(distanceText);
        FrameLayout.LayoutParams sparams =
            new FrameLayout.LayoutParams(dp(155), dp(64), Gravity.TOP | Gravity.RIGHT);
        sparams.rightMargin = dp(15);
        sparams.topMargin = dp(15);
        root.addView(stats, sparams);

        TextView help = text("JOYSTICK: MOVER   •   ARRASTE O LADO DIREITO: CÂMERA", 11, true, 0xE6FFFFFF);
        help.setGravity(Gravity.CENTER);
        help.setBackground(panel());
        FrameLayout.LayoutParams hparams =
            new FrameLayout.LayoutParams(dp(400), dp(32), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hparams.topMargin = dp(88);
        root.addView(help, hparams);
        world.setStatusListener((collected, meters) -> runOnUiThread(() -> {
            scoreText.setText("ANÉIS  " + collected);
            distanceText.setText("DISTÂNCIA  " + meters + " m");
        }));
        setContentView(root);
    }

    @Override protected void onPause() {
        super.onPause();
        if (world != null) world.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        if (world != null) world.onResume();
    }
}
