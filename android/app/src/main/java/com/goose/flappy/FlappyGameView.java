package com.goose.flappy;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.view.MotionEvent;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Flappy Goose 3D. Goose runs gravity, pipes, collision, RNG and scoring;
 * this Android class only draws a real 3-D scene using OpenGL ES 2.0.
 * No Canvas, Bitmap allocations, or per-frame Java object creation.
 */
public final class FlappyGameView extends GLSurfaceView implements GLSurfaceView.Renderer {
    static { System.loadLibrary("flappygoose"); }

    private native void nativeInit();
    private native void nativeFlap();
    private native void nativeStep(double dt);
    private native int nativeMode();
    private native int nativeScore();
    private native int nativeBest();
    private native double nativeBirdY();
    private native double nativeBirdVelocity();
    private native double nativePipeX(int index);
    private native double nativePipeGap(int index);

    public interface StatusListener { void onStatus(int mode, int score, int best); }
    private StatusListener listener;
    private final AtomicInteger taps = new AtomicInteger();
    private boolean gooseInitialized;
    private long lastFrame;
    private float seconds;
    private int lastMode = -1, lastScore = -1, lastBest = -1;

    private int program, aPosition, aNormal, uMvp, uModel, uColor;
    private final float[] projection = new float[16];
    private final float[] view = new float[16];
    private final float[] vp = new float[16];
    private final float[] model = new float[16];
    private final float[] mvp = new float[16];
    private Mesh sphere, cylinder, cube;
    private static final float WORLD = 72f;
    private static final float BIRD_X = (90f - 180f) / WORLD;
    private static final float GROUND_Y = (320f - 584f) / WORLD;

    public FlappyGameView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        setFocusable(true);
    }

    public void setStatusListener(StatusListener listener) { this.listener = listener; }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            taps.incrementAndGet();
            return true;
        }
        return true;
    }

    private static final String VS =
            "attribute vec3 aPosition;\n" +
            "attribute vec3 aNormal;\n" +
            "uniform mat4 uModel;\n" +
            "uniform mat4 uMvp;\n" +
            "varying vec3 vNormal;\n" +
            "void main() {\n" +
            "  vNormal = mat3(uModel) * aNormal;\n" +
            "  gl_Position = uMvp * vec4(aPosition,1.0);\n" +
            "}\n";
    private static final String FS =
            "precision mediump float;\n" +
            "varying vec3 vNormal;\n" +
            "uniform vec4 uColor;\n" +
            "void main() {\n" +
            "  vec3 n = normalize(vNormal);\n" +
            "  float light = max(dot(n, normalize(vec3(-0.45,0.8,0.9))), 0.0);\n" +
            "  float shade = 0.56 + light * 0.44;\n" +
            "  gl_FragColor = vec4(uColor.rgb * shade, uColor.a);\n" +
            "}\n";

    private static int shader(int type, String source) {
        int id = GLES20.glCreateShader(type);
        GLES20.glShaderSource(id, source);
        GLES20.glCompileShader(id);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("OpenGL shader: " + GLES20.glGetShaderInfoLog(id));
        return id;
    }

    @Override public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLES20.glClearColor(0.43f, 0.81f, 0.94f, 1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glDisable(GLES20.GL_BLEND);
        int vert = shader(GLES20.GL_VERTEX_SHADER, VS);
        int frag = shader(GLES20.GL_FRAGMENT_SHADER, FS);
        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vert);
        GLES20.glAttachShader(program, frag);
        GLES20.glLinkProgram(program);
        int[] linked = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
        if (linked[0] == 0) throw new IllegalStateException("OpenGL program: " + GLES20.glGetProgramInfoLog(program));
        GLES20.glDeleteShader(vert);
        GLES20.glDeleteShader(frag);
        GLES20.glUseProgram(program);
        aPosition = GLES20.glGetAttribLocation(program, "aPosition");
        aNormal = GLES20.glGetAttribLocation(program, "aNormal");
        uMvp = GLES20.glGetUniformLocation(program, "uMvp");
        uModel = GLES20.glGetUniformLocation(program, "uModel");
        uColor = GLES20.glGetUniformLocation(program, "uColor");
        GLES20.glEnableVertexAttribArray(aPosition);
        GLES20.glEnableVertexAttribArray(aNormal);
        sphere = createSphere(12, 16);
        cylinder = createCylinder(16);
        cube = createCube();
        if (!gooseInitialized) {
            nativeInit();
            gooseInitialized = true;
        }
        lastFrame = System.nanoTime();
    }

    @Override public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float aspect = (float) width / Math.max(1, height);
        float z = Math.max(11.8f, (5.35f / aspect) / (2f * (float) Math.tan(Math.toRadians(22.5))));
        Matrix.perspectiveM(projection, 0, 45f, aspect, .1f, 90f);
        Matrix.setLookAtM(view, 0, 0.82f, 0.40f, z, 0f, 0f, 0f, 0f, 1f, 0f);
        Matrix.multiplyMM(vp, 0, projection, 0, view, 0);
    }

    private static float worldX(double pixel) { return ((float) pixel - 180f) / WORLD; }
    private static float worldY(double pixel) { return (320f - (float) pixel) / WORLD; }

    @Override public void onDrawFrame(GL10 unused) {
        long now = System.nanoTime();
        double dt = Math.max(0.0, Math.min(0.033, (now - lastFrame) * 1e-9));
        lastFrame = now;
        seconds += (float) dt;
        if (taps.getAndSet(0) > 0) nativeFlap();
        nativeStep(dt);

        int mode = nativeMode(), score = nativeScore(), best = nativeBest();
        if (listener != null && (mode != lastMode || score != lastScore || best != lastBest)) {
            lastMode = mode;
            lastScore = score;
            lastBest = best;
            listener.onStatus(mode, score, best);
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glUseProgram(program);
        drawScenery();
        for (int i = 0; i < 3; i++) drawPipe(nativePipeX(i), nativePipeGap(i));
        drawBird(nativeBirdY(), nativeBirdVelocity(), mode);
    }

    private void draw(Mesh mesh, float x, float y, float z, float sx, float sy, float sz,
                      float angle, float r, float g, float b) {
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, x, y, z);
        if (angle != 0f) Matrix.rotateM(model, 0, angle, 0f, 0f, 1f);
        Matrix.scaleM(model, 0, sx, sy, sz);
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0);
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0);
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0);
        GLES20.glUniform4f(uColor, r, g, b, 1f);
        mesh.vertices.position(0);
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 24, mesh.vertices);
        mesh.vertices.position(3);
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, 24, mesh.vertices);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.count);
    }

    private void drawScenery() {
        // True 3D: land, hills and floating volumetric clouds have different depths.
        draw(cube, 0f, GROUND_Y - .50f, -1f, 18f, 1.0f, 6f, 0f, .93f, .79f, .49f);
        draw(cube, 0f, GROUND_Y - .06f, -1f, 18f, .15f, 6f, 0f, .33f, .76f, .37f);
        draw(sphere, -3.8f, -3.8f, -7f, 2.2f, 1.7f, 1.1f, 0f, .51f, .82f, .61f);
        draw(sphere, -0.4f, -4.1f, -7f, 2.5f, 1.8f, 1.1f, 0f, .42f, .75f, .52f);
        draw(sphere, 3.1f, -4.0f, -7f, 2.7f, 1.7f, 1.1f, 0f, .52f, .83f, .63f);
        float c1 = 3.4f - ((seconds * .20f) % 9.0f);
        float c2 = 6.0f - ((seconds * .13f) % 10.0f);
        cloud(c1, 2.3f, -5.5f, .66f);
        cloud(c2, .9f, -7f, .90f);
        // 3D white sun, far behind gameplay.
        draw(sphere, 2.1f, 3.2f, -9f, .52f, .52f, .52f, 0f, 1f, .97f, .65f);
    }

    private void cloud(float x, float y, float z, float size) {
        draw(sphere, x, y, z, size, size * .45f, size * .39f, 0f, 1f, 1f, 1f);
        draw(sphere, x - size * .35f, y - size * .17f, z, size * .6f, size * .39f, size * .38f, 0f, 1f, 1f, 1f);
        draw(sphere, x + size * .4f, y - size * .18f, z, size * .62f, size * .34f, size * .38f, 0f, 1f, 1f, 1f);
    }

    private void drawPipe(double pipeX, double gap) {
        if (pipeX < -80 || pipeX > 550) return;
        float x = worldX(pipeX + 32);
        float upperEdge = (float) gap - 84f;
        float lowerEdge = (float) gap + 84f;
        float upperLen = Math.max(0f, upperEdge / WORLD);
        float lowerLen = Math.max(0f, (584f - lowerEdge) / WORLD);
        float upperCenter = worldY(upperEdge * .5f);
        float lowerCenter = worldY((584f + lowerEdge) * .5f);
        float z = -.50f;
        if (upperLen > 0f) draw(cylinder, x, upperCenter, z, .43f, upperLen, .43f, 0f, .14f, .68f, .31f);
        if (lowerLen > 0f) draw(cylinder, x, lowerCenter, z, .43f, lowerLen, .43f, 0f, .14f, .68f, .31f);
        draw(cylinder, x, worldY(upperEdge - 7f), z, .51f, .27f, .51f, 0f, .22f, .82f, .41f);
        draw(cylinder, x, worldY(lowerEdge + 7f), z, .51f, .27f, .51f, 0f, .22f, .82f, .41f);
    }

    private void drawBird(double py, double vy, int mode) {
        float bob = mode == 0 ? (float) Math.sin(seconds * 4f) * .08f : 0f;
        float y = worldY(py) + bob;
        float angle = Math.max(-22f, Math.min(65f, (float) vy * -.07f));
        float flapAngle = (float) Math.sin(seconds * 19f) * 25f;
        draw(sphere, BIRD_X, y, .30f, .27f, .23f, .24f, angle, 1f, .80f, .15f);
        draw(sphere, BIRD_X - .07f, y - .06f, .52f, .15f, .10f, .08f, flapAngle, 1f, .61f, .12f);
        draw(sphere, BIRD_X + .11f, y + .075f, .50f, .091f, .096f, .045f, 0f, 1f, 1f, 1f);
        draw(sphere, BIRD_X + .141f, y + .07f, .547f, .042f, .052f, .022f, 0f, .10f, .15f, .20f);
        draw(cube, BIRD_X + .26f, y - .015f, .39f, .22f, .09f, .16f, -6f, 1f, .37f, .09f);
    }

    private static final class Mesh {
        final FloatBuffer vertices;
        final int count;
        Mesh(float[] v) {
            vertices = ByteBuffer.allocateDirect(v.length * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            vertices.put(v);
            vertices.position(0);
            count = v.length / 6;
        }
    }

    private static void vertex(float[] a, int offset, float x, float y, float z,
                               float nx, float ny, float nz) {
        a[offset] = x; a[offset + 1] = y; a[offset + 2] = z;
        a[offset + 3] = nx; a[offset + 4] = ny; a[offset + 5] = nz;
    }

    private static Mesh createCylinder(int sides) {
        float[] a = new float[sides * 6 * 6];
        int p = 0;
        for (int i = 0; i < sides; i++) {
            float t0 = (float) (2 * Math.PI * i / sides);
            float t1 = (float) (2 * Math.PI * (i + 1) / sides);
            float x0 = (float) Math.cos(t0), z0 = (float) Math.sin(t0);
            float x1 = (float) Math.cos(t1), z1 = (float) Math.sin(t1);
            vertex(a,p,x0,-.5f,z0,x0,0,z0);p+=6;
            vertex(a,p,x1,-.5f,z1,x1,0,z1);p+=6;
            vertex(a,p,x1,.5f,z1,x1,0,z1);p+=6;
            vertex(a,p,x0,-.5f,z0,x0,0,z0);p+=6;
            vertex(a,p,x1,.5f,z1,x1,0,z1);p+=6;
            vertex(a,p,x0,.5f,z0,x0,0,z0);p+=6;
        }
        return new Mesh(a);
    }

    private static Mesh createSphere(int stacks, int slices) {
        float[] a = new float[stacks * slices * 6 * 6];
        int p = 0;
        for (int j = 0; j < stacks; j++) {
            float v0 = (float) (-Math.PI / 2 + Math.PI * j / stacks);
            float v1 = (float) (-Math.PI / 2 + Math.PI * (j + 1) / stacks);
            for (int i = 0; i < slices; i++) {
                float u0 = (float) (2 * Math.PI * i / slices);
                float u1 = (float) (2 * Math.PI * (i + 1) / slices);
                float x00 = (float) (Math.cos(v0)*Math.cos(u0));
                float y00 = (float) Math.sin(v0);
                float z00 = (float) (Math.cos(v0)*Math.sin(u0));
                float x01 = (float) (Math.cos(v0)*Math.cos(u1));
                float y01 = y00, z01 = (float) (Math.cos(v0)*Math.sin(u1));
                float x10 = (float) (Math.cos(v1)*Math.cos(u0));
                float y10 = (float) Math.sin(v1);
                float z10 = (float) (Math.cos(v1)*Math.sin(u0));
                float x11 = (float) (Math.cos(v1)*Math.cos(u1));
                float y11 = y10, z11 = (float) (Math.cos(v1)*Math.sin(u1));
                vertex(a,p,x00,y00,z00,x00,y00,z00);p+=6;
                vertex(a,p,x01,y01,z01,x01,y01,z01);p+=6;
                vertex(a,p,x11,y11,z11,x11,y11,z11);p+=6;
                vertex(a,p,x00,y00,z00,x00,y00,z00);p+=6;
                vertex(a,p,x11,y11,z11,x11,y11,z11);p+=6;
                vertex(a,p,x10,y10,z10,x10,y10,z10);p+=6;
            }
        }
        return new Mesh(a);
    }

    private static Mesh createCube() {
        float[][] face = {
            {0,0,1,  -1,-1,1,   1,-1,1,   1,1,1,    -1,1,1},
            {0,0,-1, 1,-1,-1, -1,-1,-1, -1,1,-1,   1,1,-1},
            {1,0,0,  1,-1,1,   1,-1,-1,  1,1,-1,    1,1,1},
            {-1,0,0, -1,-1,-1, -1,-1,1, -1,1,1,    -1,1,-1},
            {0,1,0,  -1,1,1,   1,1,1,    1,1,-1,   -1,1,-1},
            {0,-1,0, -1,-1,-1, 1,-1,-1,  1,-1,1,   -1,-1,1}
        };
        float[] a = new float[36 * 6]; int p = 0;
        int[] tri = {0,1,2, 0,2,3};
        for (float[] f : face) for (int k : tri) {
            vertex(a,p,f[3+k*3],f[4+k*3],f[5+k*3],f[0],f[1],f[2]);p+=6;
        }
        return new Mesh(a);
    }
}
