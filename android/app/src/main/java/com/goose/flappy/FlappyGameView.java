package com.goose.flappy;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Only Android's mandatory EGL context & MotionEvent adapter.
 * Rendering, OpenGL commands, mesh generation, procedural world, physics,
 * movement, user-interface and input interpretation live in native Goose.
 */
public final class FlappyGameView extends GLSurfaceView implements GLSurfaceView.Renderer {
    static { System.loadLibrary("flappygoose"); }

    private native void nativeCreated();
    private native void nativeResized(int width, int height);
    private native void nativeFrame(double seconds);
    private native void nativeTouch(int action, int finger, double x, double y, int width, int height);

    private long previousNanos;

    public FlappyGameView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setPreserveEGLContextOnPause(true);
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        setFocusable(true);
    }

    @Override public void onSurfaceCreated(GL10 ignored, EGLConfig config) {
        nativeCreated();
        previousNanos = System.nanoTime();
    }
    @Override public void onSurfaceChanged(GL10 ignored, int width, int height) {
        nativeResized(width, height);
    }
    @Override public void onDrawFrame(GL10 ignored) {
        long now = System.nanoTime();
        double dt = Math.max(0.0, Math.min(0.034, (now - previousNanos) / 1e9));
        previousNanos = now;
        nativeFrame(dt);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        final int changedIndex = event.getActionIndex();
        final int w = getWidth();
        final int h = getHeight();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            final int id = event.getPointerId(changedIndex);
            final double x = event.getX(changedIndex), y = event.getY(changedIndex);
            queueEvent(() -> nativeTouch(0, id, x, y, w, h));
        } else if (action == MotionEvent.ACTION_MOVE) {
            // Delivers all active touches. Goose determines the joystick/buttons.
            for (int i = 0; i < event.getPointerCount(); ++i) {
                final int id = event.getPointerId(i);
                final double x = event.getX(i), y = event.getY(i);
                queueEvent(() -> nativeTouch(2, id, x, y, w, h));
            }
        } else if (action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_CANCEL) {
            if (action == MotionEvent.ACTION_CANCEL) {
                for (int i = 0; i < event.getPointerCount(); ++i) {
                    final int id = event.getPointerId(i);
                    queueEvent(() -> nativeTouch(1, id, 0, 0, w, h));
                }
            } else {
                final int id = event.getPointerId(changedIndex);
                queueEvent(() -> nativeTouch(1, id, 0, 0, w, h));
            }
        }
        return true;
    }
}
