package com.goose.flappy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.concurrent.atomic.AtomicInteger;

public final class FlappyGameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    static { System.loadLibrary("flappygoose"); }

    // A simulacao/colisao e em Goose. Java apenas apresenta os pixels/toque.
    private native void nativeInit();
    private native void nativeShutdown();
    private native void nativeFlap();
    private native void nativeStep(double dt);
    private native int nativeMode();
    private native int nativeScore();
    private native int nativeBest();
    private native double nativeBirdY();
    private native double nativeBirdVelocity();
    private native double nativePipeX(int index);
    private native double nativePipeGap(int index);

    private final SurfaceHolder holder;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();
    private final AtomicInteger pendingFlaps = new AtomicInteger();
    private volatile boolean running = false;
    private Thread renderThread;
    private float elapsed = 0f;
    private final Typeface heavy = Typeface.create("sans-serif-black", Typeface.BOLD);
    private final Typeface normal = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private LinearGradient sky;

    public FlappyGameView(Context context) {
        super(context);
        holder = getHolder();
        holder.addCallback(this);
        setFocusable(true);
    }

    @Override public void surfaceCreated(SurfaceHolder surfaceHolder) {
        if (running) return;
        running = true;
        renderThread = new Thread(this, "Goose-Game-Thread");
        renderThread.start();
    }

    @Override public void surfaceChanged(SurfaceHolder h, int format, int width, int height) {}

    @Override public void surfaceDestroyed(SurfaceHolder h) {
        running = false;
        if (renderThread != null) {
            boolean interrupted = false;
            while (true) {
                try { renderThread.join(); break; }
                catch (InterruptedException e) { interrupted = true; }
            }
            if (interrupted) Thread.currentThread().interrupt();
            renderThread = null;
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            pendingFlaps.incrementAndGet();
            return true;
        }
        return true;
    }

    @Override public void run() {
        nativeInit();
        long last = System.nanoTime();
        try {
            while (running) {
                long now = System.nanoTime();
                double dt = Math.min((now - last) / 1_000_000_000.0, 0.035);
                last = now;

                if (pendingFlaps.getAndSet(0) > 0) nativeFlap();
                nativeStep(dt);
                elapsed += (float) dt;

                Canvas c = null;
                try {
                    if (holder.getSurface().isValid()) c = holder.lockCanvas();
                    if (c != null) render(c);
                } finally {
                    if (c != null) holder.unlockCanvasAndPost(c);
                }

                try { Thread.sleep(16); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
        } finally {
            nativeShutdown();
        }
    }

    private void color(int color) {
        p.setShader(null);
        p.clearShadowLayer();
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        p.setStrokeWidth(1f);
    }

    private void rect(Canvas c, float x0, float y0, float x1, float y1, int col) {
        color(col);
        c.drawRect(x0, y0, x1, y1, p);
    }

    private void round(Canvas c, float x0, float y0, float x1, float y1, float radius, int col) {
        color(col);
        tmp.set(x0, y0, x1, y1);
        c.drawRoundRect(tmp, radius, radius, p);
    }

    private void oval(Canvas c, float x0, float y0, float x1, float y1, int col) {
        color(col);
        tmp.set(x0, y0, x1, y1);
        c.drawOval(tmp, p);
    }

    private void txt(Canvas c, String text, float cx, float y, float size, int fill, boolean bold) {
        color(fill);
        p.setTypeface(bold ? heavy : normal);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(size);
        c.drawText(text, cx, y, p);
    }

    private void cloud(Canvas c, float x, float y, float scale) {
        c.save();
        c.translate(x, y);
        c.scale(scale, scale);
        oval(c, -24, 2, 18, 19, 0x99FFFFFF);
        oval(c, -9, -10, 23, 18, 0xCCFFFFFF);
        oval(c, 10, 0, 41, 19, 0xD9FFFFFF);
        c.restore();
    }

    private void drawSky(Canvas c) {
        if (sky == null) {
            sky = new LinearGradient(0, 0, 0, 585, 0xFF67CAE5, 0xFFD9F5F7,
                    Shader.TileMode.CLAMP);
        }
        p.setShader(sky);
        c.drawRect(0, 0, 360, 585, p);
        p.setShader(null);
        oval(c, 271, 62, 329, 120, 0x50FFFFFF);
        oval(c, 283, 73, 317, 107, 0xFFFFF1AF);

        float drift = (elapsed * 9f) % 450f;
        cloud(c, 70f - drift, 105, 1.1f);
        cloud(c, 350f - drift, 175, 0.85f);
        cloud(c, 520f - drift, 80, 0.8f);

        // Colinas do fundo com duas camadas.
        for (int i = -1; i <= 4; i++) {
            oval(c, i * 125f - 45, 498, i * 125f + 150, 660, 0xFFB9EABC);
        }
        for (int i = -1; i <= 4; i++) {
            oval(c, i * 136f - 15, 528, i * 136f + 155, 660, 0xFF86CF9E);
        }
    }

    private void pipe(Canvas c, float x, float gap) {
        float w = 64f;
        float half = 84f;
        float upEnd = gap - half;
        float lowStart = gap + half;
        if (x > 370f || x + w < -10f) return;

        // Bordas escuras, luz lateral e boca do tubo.
        rect(c, x - 2, -20, x + w + 2, upEnd, 0xFF256D50);
        rect(c, x + 4, -20, x + w - 5, upEnd, 0xFF42BA77);
        rect(c, x + 12, -20, x + 23, upEnd, 0xFF7BDF9B);
        round(c, x - 7, upEnd - 24, x + w + 7, upEnd + 1, 5, 0xFF216C4B);
        round(c, x - 3, upEnd - 21, x + w + 3, upEnd - 2, 4, 0xFF4ED18A);
        rect(c, x + 8, upEnd - 18, x + 19, upEnd - 4, 0xFF89EBAA);

        rect(c, x - 2, lowStart, x + w + 2, 585, 0xFF256D50);
        rect(c, x + 4, lowStart, x + w - 5, 585, 0xFF42BA77);
        rect(c, x + 12, lowStart, x + 23, 585, 0xFF7BDF9B);
        round(c, x - 7, lowStart - 1, x + w + 7, lowStart + 24, 5, 0xFF216C4B);
        round(c, x - 3, lowStart + 2, x + w + 3, lowStart + 21, 4, 0xFF4ED18A);
        rect(c, x + 8, lowStart + 4, x + 19, lowStart + 18, 0xFF89EBAA);
    }

    private void ground(Canvas c) {
        rect(c, 0, 580, 360, 587, 0xFF3A8D5B);
        rect(c, 0, 587, 360, 640, 0xFFFFE3A8);
        rect(c, 0, 595, 360, 598, 0xFFCBAD78);
        float slide = (elapsed * 72f) % 36f;
        for (int x = -40; x < 400; x += 36) {
            rect(c, x - slide, 600, x - slide + 18, 605, 0xFFD2AF78);
            rect(c, x + 16 - slide, 622, x + 25 - slide, 625, 0xFFE9C793);
        }
    }

    private void bird(Canvas c, float y, float velocity, int mode) {
        float bob = mode == 0 ? (float)Math.sin(elapsed * 3.8) * 5f : 0f;
        c.save();
        c.rotate(Math.max(-27f, Math.min(75f, velocity * .13f)), 90, y + bob);
        c.translate(90, y + bob);
        oval(c, -17, -14, 19, 16, 0xFF8D5B18);
        oval(c, -16, -15, 17, 14, 0xFFFFD447);
        oval(c, -5, 3, 13, 14, 0xFFFFEA9A);
        oval(c, -17, -1, 4, 12, 0xFFEDAB2F);
        oval(c, -12, 0, 1, 8, 0xFFFFC34F);
        oval(c, 2, -13, 18, 4, Color.WHITE);
        oval(c, 9, -7, 16, 2, 0xFF1A2834);
        oval(c, 11, -6, 13, -3, Color.WHITE);
        Path beak = new Path();
        beak.moveTo(14, 2); beak.lineTo(30, 7);
        beak.lineTo(15, 11); beak.close();
        color(0xFFF47C3E); c.drawPath(beak, p);
        c.restore();
    }

    private void render(Canvas c) {
        final int width = c.getWidth(), height = c.getHeight();
        c.drawColor(0xFF1E5269);
        float s = Math.min(width / 360f, height / 640f);
        c.save();
        c.translate((width - s * 360) / 2f, (height - s * 640) / 2f);
        c.scale(s, s);

        int mode = nativeMode();
        int score = nativeScore();
        drawSky(c);
        for (int i = 0; i < 3; i++) {
            pipe(c, (float)nativePipeX(i), (float)nativePipeGap(i));
        }
        ground(c);
        bird(c, (float)nativeBirdY(), (float)nativeBirdVelocity(), mode);

        // HUD com sombra para legibilidade.
        txt(c, "" + score, 180, 66, 44, 0xFF31647B, true);
        txt(c, "" + score, 180, 62, 44, Color.WHITE, true);
        txt(c, "RECORDE  " + nativeBest(), 180, 84, 12, 0xFF397689, true);

        if (mode == 0) {
            round(c, 36, 198, 324, 349, 18, 0xE6FFFFFF);
            txt(c, "FLAPPY GOOSE", 180, 244, 30, 0xFF2A6476, true);
            txt(c, "FEITO EM GOOSE!", 180, 271, 14, 0xFF3B9773, true);
            txt(c, "TOQUE PARA VOAR", 180, 313, 19, 0xFF173D50, true);
        } else if (mode == 2) {
            round(c, 43, 196, 317, 354, 18, 0xEDFFFFFF);
            txt(c, "FIM DE JOGO", 180, 244, 29, 0xFF2A6476, true);
            txt(c, "PONTOS: " + score, 180, 280, 21, 0xFF2D8C76, true);
            txt(c, "TOQUE PARA RECOMEÇAR", 180, 322, 16, 0xFF173D50, true);
        } else {
            txt(c, "TAP!", 180, 562, 13, 0xFF397689, true);
        }
        c.restore();
    }
}