package com.goose.flappy;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Minimal polished Android UI shell.
 * World, graphics, Blender meshes, camera and player physics are still Goose.
 * Android uses native text to keep typography sharp on phone screens.
 */
public final class MainActivity extends Activity {
    private FlappyGameView game;
    private TextView ringNumber, distanceNumber, fpsNumber;
    private FrameLayout root;
    private int dp(float x) {
        return Math.round(x * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable rounded(int first, int second, int border, float radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{first,second});
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1),border);
        return g;
    }

    private TextView text(String value,int sp,int color,boolean heavy) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(sp);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create(heavy?"sans-serif-black":"sans-serif-medium",
                heavy ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }

    private TextView kicker(String value) {
        TextView t = text(value, 10, 0xFF9CDDE6, true);
        t.setLetterSpacing(0.12f);
        return t;
    }

    private FrameLayout.LayoutParams at(int width, int height, int gravity,int left,int top,int right,int bottom) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                width == -1 ? -1 : dp(width),height == -1 ? -1 : dp(height), gravity);
        p.setMargins(dp(left),dp(top),dp(right),dp(bottom));
        return p;
    }

    private LinearLayout column() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

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

        root=new FrameLayout(this);
        game=new FlappyGameView(this);
        root.addView(game,new FrameLayout.LayoutParams(-1,-1));

        // Editorial top-left panel, not a giant game-pausing modal.
        LinearLayout heading=column();
        heading.setPadding(dp(16),dp(10),dp(16),dp(10));
        heading.setBackground(rounded(0xE81A3E4B,0xCE102A35,0x447CCBD2,16));
        heading.addView(kicker("G O O S E   S K I E S"),
                new LinearLayout.LayoutParams(-1,dp(17)));
        TextView headline=text("WILD HORIZONS",23,Color.WHITE,true);
        headline.setLetterSpacing(-0.02f);
        heading.addView(headline,new LinearLayout.LayoutParams(-1,dp(32)));
        TextView subtitle=text("VOE  •  EXPLORE  •  DESCUBRA",10,0xFFEEDCA7,false);
        subtitle.setLetterSpacing(.04f);
        heading.addView(subtitle,new LinearLayout.LayoutParams(-1,dp(17)));
        root.addView(heading,at(254,86,Gravity.TOP|Gravity.LEFT,16,16,0,0));

        // Compact, high-contrast mission HUD.
        LinearLayout stats=new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setGravity(Gravity.CENTER_VERTICAL);
        stats.setBackground(rounded(0xED173B47,0xDA234E59,0x4464B7BF,16));
        stats.setPadding(dp(14),dp(8),dp(14),dp(8));
        LinearLayout col1=column();
        col1.addView(kicker("✦ ANÉIS"),new LinearLayout.LayoutParams(dp(90),dp(17)));
        ringNumber=text("00",26,0xFFFFD37A,true);
        col1.addView(ringNumber,new LinearLayout.LayoutParams(dp(86),dp(33)));
        stats.addView(col1,new LinearLayout.LayoutParams(dp(91),-1));
        View divider=new View(this);
        divider.setBackgroundColor(0x666DC8D2);
        LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(dp(1),dp(39));
        vp.setMargins(dp(4),0,dp(15),0);
        stats.addView(divider,vp);
        LinearLayout col2=column();
        col2.addView(kicker("DISTÂNCIA"),new LinearLayout.LayoutParams(dp(93),dp(17)));
        distanceNumber=text("0 m",23,Color.WHITE,true);
        col2.addView(distanceNumber,new LinearLayout.LayoutParams(dp(95),dp(33)));
        stats.addView(col2,new LinearLayout.LayoutParams(dp(96),-1));
        root.addView(stats,at(232,74,Gravity.RIGHT|Gravity.TOP,0,16,16,0));

        // First impression: one line of instructions, not boxes covering gameplay.
        TextView help=text("JOYSTICK: VOAR     •     ARRASTE À DIREITA: CÂMERA",11,Color.WHITE,true);
        help.setGravity(Gravity.CENTER);
        help.setBackground(rounded(0xA3173B49,0xB2173B49,0x5075C8C5,12));
        root.addView(help,at(360,29,Gravity.TOP|Gravity.CENTER_HORIZONTAL,0,20,0,0));

        // FPS badge: measured FPS, not a guessed benchmark.
        fpsNumber=text("-- FPS",11,0xFFC7F5E4,true);
        fpsNumber.setGravity(Gravity.CENTER);
        fpsNumber.setBackground(rounded(0xCB163943,0xD1194545,0x547CC4BF,11));
        root.addView(fpsNumber,at(70,25,Gravity.BOTTOM|Gravity.RIGHT,0,0,16,14));

        // Gooseland world controls are rendered by Goose with correctly aligned circles.
        // These text labels are non-interactive and do not steal multitouch from GLSurfaceView.
        TextView move=text("MOVER",11,0xFFE9FAFF,true);
        move.setGravity(Gravity.CENTER);
        root.addView(move,at(78,20,Gravity.BOTTOM|Gravity.LEFT,31,0,0,10));

        TextView turbo=text("TURBO",12,0xFFFFE5A1,true);
        turbo.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams turboP=at(80,23,Gravity.BOTTOM|Gravity.RIGHT,0,0,134,3);
        root.addView(turbo,turboP);
        root.addOnLayoutChangeListener((v,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
            int w=right-left,h=bottom-top;
            // Goose touch / GPU overlay position uses 75% width and 83% screen height.
            FrameLayout.LayoutParams p=(FrameLayout.LayoutParams) turbo.getLayoutParams();
            p.gravity=Gravity.TOP|Gravity.LEFT;
            p.leftMargin=Math.round(w*.75f)-dp(40);
            p.topMargin=Math.round(h*.83f)+dp(27);
            turbo.setLayoutParams(p);
        });

        game.setStatusListener((rings,meters,fps)->{
            ringNumber.setText(String.format(java.util.Locale.ROOT,"%02d",rings));
            distanceNumber.setText(meters+" m");
            fpsNumber.setText(fps+" FPS");
        });
        setContentView(root);
    }

    @Override protected void onPause() {
        super.onPause();
        if(game!=null)game.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        if(game!=null)game.onResume();
    }
}
