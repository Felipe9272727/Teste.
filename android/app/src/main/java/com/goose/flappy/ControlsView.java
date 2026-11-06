package com.goose.flappy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** On-screen multi-touch controls. No gameplay calculations in Java. */
public final class ControlsView extends View {
    public volatile float stickX, stickY, cameraYaw;
    public volatile boolean rise, dive, turbo;
    private int stickId = -1, cameraId = -1, riseId = -1, diveId = -1, turboId = -1;
    private float lastCameraX;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final float density;

    public ControlsView(Context ctx) {
        super(ctx);
        density = getResources().getDisplayMetrics().density;
        setFocusable(true);
    }

    private float dp(float amount) { return amount * density; }

    private float stickCenterX() { return dp(99); }
    private float stickCenterY() { return getHeight() - dp(110); }
    private float riseX() { return getWidth() - dp(87); }
    private float riseY() { return getHeight() - dp(184); }
    private float diveX() { return getWidth() - dp(87); }
    private float diveY() { return getHeight() - dp(88); }
    private float turboX() { return getWidth() - dp(198); }
    private float turboY() { return getHeight() - dp(103); }

    private boolean near(float x, float y, float targetX, float targetY, float radius) {
        float dx = x - targetX, dy = y - targetY;
        return dx * dx + dy * dy < radius * radius;
    }

    private void start(int id, float x, float y) {
        if (riseId == -1 && near(x, y, riseX(), riseY(), dp(48))) {
            riseId = id; rise = true;
        } else if (diveId == -1 && near(x, y, diveX(), diveY(), dp(48))) {
            diveId = id; dive = true;
        } else if (turboId == -1 && near(x, y, turboX(), turboY(), dp(50))) {
            turboId = id; turbo = true;
        } else if (stickId == -1 && x < getWidth() * .46f) {
            stickId = id; moveStick(x, y);
        } else if (cameraId == -1) {
            cameraId = id; lastCameraX = x;
        }
    }

    private void moveStick(float x, float y) {
        float dx = (x - stickCenterX()) / dp(61);
        float dy = (y - stickCenterY()) / dp(61);
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length > 1) { dx /= length; dy /= length; }
        stickX = dx; stickY = -dy;
    }

    private void move(int id, float x, float y) {
        if (id == stickId) moveStick(x, y);
        if (id == cameraId) {
            cameraYaw += (x - lastCameraX) * .0048f;
            lastCameraX = x;
        }
    }

    private void end(int id) {
        if (id == stickId) { stickId = -1; stickX = 0; stickY = 0; }
        if (id == cameraId) cameraId = -1;
        if (id == riseId) { riseId = -1; rise = false; }
        if (id == diveId) { diveId = -1; dive = false; }
        if (id == turboId) { turboId = -1; turbo = false; }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        int at = e.getActionIndex();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            start(e.getPointerId(at), e.getX(at), e.getY(at));
        } else if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < e.getPointerCount(); i++)
                move(e.getPointerId(i), e.getX(i), e.getY(i));
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            end(e.getPointerId(at));
        } else if (action == MotionEvent.ACTION_CANCEL) {
            stickId = cameraId = riseId = diveId = turboId = -1;
            stickX = 0f; stickY = 0f; rise = false; dive = false; turbo = false;
        }
        invalidate();
        return true;
    }

    private void circle(Canvas c, float x, float y, float r, int color, boolean stroke) {
        p.setColor(color);
        p.setStyle(stroke ? Paint.Style.STROKE : Paint.Style.FILL);
        p.setStrokeWidth(dp(2.6f));
        c.drawCircle(x, y, r, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void label(Canvas c, String s, float x, float y, float size) {
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(dp(size));
        p.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        c.drawText(s, x, y + dp(size * .33f), p);
    }

    private void button(Canvas c, float x, float y, float radius, String label, int mainColor, boolean down) {
        circle(c, x + dp(2), y + dp(4), radius, 0x440C2947, false);
        circle(c, x, y, radius, down ? mainColor : 0xB7195679, false);
        circle(c, x, y, radius - dp(3), down ? 0xCCFFFFFF : 0x66FFFFFF, true);
        label(c, label, x, y, label.equals("TURBO") ? 13 : 13);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float cx = stickCenterX(), cy = stickCenterY();
        circle(c, cx, cy, dp(68), 0x440D2A46, false);
        circle(c, cx, cy, dp(61), 0x99FFFFFF, true);
        circle(c, cx + stickX * dp(47), cy - stickY * dp(47), dp(31), 0xDDFFFFFF, false);
        circle(c, cx + stickX * dp(47), cy - stickY * dp(47), dp(31), 0xFF2A7989, true);
        button(c, riseX(), riseY(), dp(39), "SUBIR", 0xFF41BC8A, rise);
        button(c, diveX(), diveY(), dp(39), "DESCER", 0xFF369DD5, dive);
        button(c, turboX(), turboY(), dp(43), "TURBO", 0xFFDBA646, turbo);
    }
}
