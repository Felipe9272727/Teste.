package com.goose.flappy;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Goose Skies - a real 3D mobile open-world renderer.
 *
 * Gameplay, terrain floor collision, collectibles and motion: native Goose.
 * GPU: Android OpenGL ES 2.0 with vertex-lit geometry, distance fog, procedural
 * 3-D mesh chunks. Chunks are created only when the player crosses a border.
 *
 * No Canvas per frame. No external textures. No model downloads.
 */
public final class FlappyGameView extends GLSurfaceView implements GLSurfaceView.Renderer {
    static { System.loadLibrary("flappygoose"); }
    private native void nativeInit();
    private native void nativeStep(double dt, double dx, double dz, double dy, int turbo);
    private native double nativeX();
    private native double nativeY();
    private native double nativeZ();
    private native double nativeVX();
    private native double nativeVY();
    private native double nativeVZ();
    private native int nativeScore();
    private native double nativeDistance();
    private native double nativeTerrain(double x, double z);
    private native double nativeRingX(int index);
    private native double nativeRingY(int index);
    private native double nativeRingZ(int index);

    public interface StatusListener { void onStatus(int rings, int meters, int targetMeters, String direction); }
    private StatusListener listener;
    private ControlsView controls;
    private boolean gooseReady;
    private int shaderProgram, aPos, aNormal, aColor, uProjection, uModel, uTint, uEye;
    private Mesh gooseSphere, gooseCube, gooseCone, torus, seaPlane;
    private final Mesh[] chunkMeshes = new Mesh[9];
    private final int[] chunkX = new int[9], chunkZ = new int[9];
    private final float[] proj = new float[16], camera = new float[16], vp = new float[16];
    private final float[] model = new float[16];
    private float seconds = 0f, lastHudTime = -5f, lastHeading = 0f;
    private int lastScore = -1, lastDistance = -1;
    private long lastFrame;
    private static final int GRID = 16;
    private static final float TILE = 2.5f;
    private static final float CHUNK = GRID * TILE;
    private float playerX, playerY, playerZ, cameraYaw;
    private float pxVel, pyVel, pzVel;
    private int screenW = 1, screenH = 1;
    private int builtCenterX = Integer.MIN_VALUE, builtCenterZ = Integer.MIN_VALUE;

    public FlappyGameView(Context ctx) {
        super(ctx);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setPreserveEGLContextOnPause(true);
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        Arrays.fill(chunkX, Integer.MIN_VALUE);
        Arrays.fill(chunkZ, Integer.MIN_VALUE);
    }
    public void setControls(ControlsView c) { controls = c; }
    public void setStatusListener(StatusListener s) { listener = s; }

    private static final String VERT =
            "attribute vec3 aPos;\n" +
            "attribute vec3 aNormal;\n" +
            "attribute vec3 aColor;\n" +
            "uniform mat4 uVP;\n" +
            "uniform mat4 uModel;\n" +
            "uniform vec3 uTint;\n" +
            "uniform vec3 uEye;\n" +
            "varying vec3 vCol;\n" +
            "varying float vFog;\n" +
            "void main() {\n" +
            " vec4 world = uModel * vec4(aPos, 1.0);\n" +
            " vec3 N = normalize(mat3(uModel) * aNormal);\n" +
            " float sun = max(dot(N, normalize(vec3(-0.42, 0.90, 0.38))), 0.0);\n" +
            " vCol = aColor * uTint * (0.57 + 0.43 * sun);\n" +
            " vFog = clamp((length(world.xyz - uEye) - 37.0) / 68.0, 0.0, 0.85);\n" +
            " gl_Position = uVP * world;\n" +
            "}\n";
    private static final String FRAG =
            "precision mediump float;\n" +
            "varying vec3 vCol;\n" +
            "varying float vFog;\n" +
            "void main() {\n" +
            " vec3 mist = vec3(0.56, 0.81, 0.87);\n" +
            " gl_FragColor = vec4(mix(vCol, mist, vFog), 1.0);\n" +
            "}\n";

    private static int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] result = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, result, 0);
        if (result[0] == 0)
            throw new IllegalStateException("Shader: " + GLES20.glGetShaderInfoLog(shader));
        return shader;
    }

    @Override public void onSurfaceCreated(GL10 unused, EGLConfig cfg) {
        GLES20.glClearColor(0.50f, 0.78f, 0.88f, 1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glDisable(GLES20.GL_BLEND);
        int vs = compileShader(GLES20.GL_VERTEX_SHADER, VERT);
        int fs = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAG);
        shaderProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(shaderProgram, vs);
        GLES20.glAttachShader(shaderProgram, fs);
        GLES20.glLinkProgram(shaderProgram);
        int[] linked = new int[1];
        GLES20.glGetProgramiv(shaderProgram, GLES20.GL_LINK_STATUS, linked, 0);
        if (linked[0] != GLES20.GL_TRUE)
            throw new IllegalStateException("Program: " + GLES20.glGetProgramInfoLog(shaderProgram));
        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);
        GLES20.glUseProgram(shaderProgram);
        aPos = GLES20.glGetAttribLocation(shaderProgram, "aPos");
        aNormal = GLES20.glGetAttribLocation(shaderProgram, "aNormal");
        aColor = GLES20.glGetAttribLocation(shaderProgram, "aColor");
        uProjection = GLES20.glGetUniformLocation(shaderProgram, "uVP");
        uModel = GLES20.glGetUniformLocation(shaderProgram, "uModel");
        uTint = GLES20.glGetUniformLocation(shaderProgram, "uTint");
        uEye = GLES20.glGetUniformLocation(shaderProgram, "uEye");
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glEnableVertexAttribArray(aNormal);
        GLES20.glEnableVertexAttribArray(aColor);
        Builder b = new Builder();
        b.sphere(0,0,0, .5f,.5f,.5f, 10,16, 1,1,1);
        gooseSphere = b.mesh();
        b = new Builder();
        b.box(0,0,0, 1,1,1, 1,1,1);
        gooseCube = b.mesh();
        b = new Builder();
        b.cone(0, -.5f, 0, .5f, 1f, 12, 1,1,1);
        gooseCone = b.mesh();
        torus = makeTorus();
        b = new Builder();
        b.quad(-.5f,0,-.5f, -.5f,0,.5f, .5f,0,.5f, .5f,0,-.5f, 1,1,1);
        seaPlane = b.mesh();
        Arrays.fill(chunkMeshes, null);
        builtCenterX = builtCenterZ = Integer.MIN_VALUE;
        Arrays.fill(chunkX, Integer.MIN_VALUE);
        Arrays.fill(chunkZ, Integer.MIN_VALUE);
        if (!gooseReady) { nativeInit(); gooseReady = true; }
        lastFrame = System.nanoTime();
    }

    @Override public void onSurfaceChanged(GL10 ignored, int width, int height) {
        screenW = Math.max(1,width);
        screenH = Math.max(1,height);
        GLES20.glViewport(0, 0, screenW, screenH);
        Matrix.perspectiveM(proj, 0, 57f, screenW / (float) screenH, .15f, 150f);
    }

    private static double terrain(double x, double z) {
        return -.10 + Math.sin(x*.075)*Math.cos(z*.052)*1.50
            + Math.sin(x*.023 + z*.019)*1.38
            + Math.sin(z*.178 - x*.113)*.22;
    }

    private void draw(Mesh mesh, float x, float y, float z,
                      float sx, float sy, float sz, float yaw, float tilt,
                      float r, float g, float b) {
        if (mesh == null) return;
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, x,y,z);
        if (yaw != 0f) Matrix.rotateM(model, 0, yaw, 0,1,0);
        if (tilt != 0f) Matrix.rotateM(model, 0, tilt, 0,0,1);
        Matrix.scaleM(model, 0, sx,sy,sz);
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0);
        GLES20.glUniform3f(uTint, r,g,b);
        mesh.buffer.position(0);
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 36, mesh.buffer);
        mesh.buffer.position(3);
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, 36, mesh.buffer);
        mesh.buffer.position(6);
        GLES20.glVertexAttribPointer(aColor, 3, GLES20.GL_FLOAT, false, 36, mesh.buffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.count);
    }

    @Override public void onDrawFrame(GL10 unused) {
        long current = System.nanoTime();
        double dt = Math.max(0.0, Math.min(.034, (current - lastFrame) / 1000000000.0));
        lastFrame = current;
        seconds += (float) dt;
        float stickX = controls != null ? controls.stickX : 0f;
        float stickForward = controls != null ? controls.stickY : 0f;
        cameraYaw = controls != null ? controls.cameraYaw : 0f;
        float sn = (float)Math.sin(cameraYaw);
        float cs = (float)Math.cos(cameraYaw);
        double axisX = stickX * cs + stickForward * sn;
        double axisZ = stickX * sn - stickForward * cs;
        double height = controls == null ? 0f : (controls.rise ? 1f : 0f) - (controls.dive ? 1f : 0f);
        int boost = controls != null && controls.turbo ? 1 : 0;
        nativeStep(dt, axisX, axisZ, height, boost);

        playerX = (float) nativeX();
        playerY = (float) nativeY();
        playerZ = (float) nativeZ();
        pxVel = (float) nativeVX();
        pyVel = (float) nativeVY();
        pzVel = (float) nativeVZ();

        float lookX = playerX + sn * 3.7f;
        float lookZ = playerZ - cs * 3.7f;
        float eyeX = playerX - sn * 9.5f;
        float eyeZ = playerZ + cs * 9.5f;
        float eyeY = playerY + 5.8f;
        Matrix.setLookAtM(camera, 0, eyeX, eyeY, eyeZ,
                lookX, playerY + 1.4f, lookZ, 0,1,0);
        Matrix.multiplyMM(vp, 0, proj, 0, camera, 0);

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glUseProgram(shaderProgram);
        GLES20.glUniformMatrix4fv(uProjection, 1, false, vp, 0);
        GLES20.glUniform3f(uEye, eyeX,eyeY,eyeZ);

        // The water is a wide plane under grass valleys.
        draw(seaPlane,playerX,-.47f,playerZ, 230,1,230,0,0, .24f,.61f,.82f);
        ensureChunks((int)Math.floor(playerX / CHUNK), (int)Math.floor(playerZ / CHUNK));
        for (int i=0;i<9;i++)
            draw(chunkMeshes[i],0,0,0,1,1,1,0,0,1,1,1);

        drawClouds();
        drawLandmarks();
        drawRings();
        drawGoose();

        int score = nativeScore();
        int meters = (int)nativeDistance();
        if (listener != null && (seconds - lastHudTime > .45f || score != lastScore)) {
            lastHudTime = seconds;
            lastScore = score;
            lastDistance = meters;
            float targetDistance=Float.MAX_VALUE, targetX=playerX, targetZ=playerZ;
            for (int i=0;i<3;i++) {
                float dx=(float)nativeRingX(i)-playerX;
                float dz=(float)nativeRingZ(i)-playerZ;
                float dy=(float)nativeRingY(i)-playerY;
                float d=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
                if (d<targetDistance) {
                    targetDistance=d;
                    targetX=playerX+dx;
                    targetZ=playerZ+dz;
                }
            }
            double direction=Math.atan2(targetX-playerX,-(targetZ-playerZ))-cameraYaw;
            double degrees=Math.atan2(Math.sin(direction),Math.cos(direction));
            String hint;
            if (degrees>2.75 || degrees < -2.75) hint="ATRÁS";
            else if(degrees>1.9) hint="DIREITA / ATRÁS";
            else if(degrees>0.52) hint="DIREITA";
            else if(degrees< -1.9) hint="ESQUERDA / ATRÁS";
            else if(degrees< -0.52) hint="ESQUERDA";
            else hint="EM FRENTE";
            listener.onStatus(score, meters, Math.round(targetDistance), hint);
        }
    }

    private void ensureChunks(int centerX, int centerZ) {
        if (centerX == builtCenterX && centerZ == builtCenterZ) return;
        builtCenterX = centerX;
        builtCenterZ = centerZ;
        int newIndex = 0;
        Mesh[] nextMeshes = new Mesh[9];
        int[] nextXs = new int[9];
        int[] nextZs = new int[9];
        boolean unchanged = true;
        for (int dz=-1;dz<=1;dz++) for (int dx=-1;dx<=1;dx++) {
            int cx=centerX+dx, cz=centerZ+dz;
            nextXs[newIndex]=cx;
            nextZs[newIndex]=cz;
            Mesh found=null;
            for (int j=0;j<9;j++) {
                if (chunkMeshes[j]!=null && chunkX[j]==cx && chunkZ[j]==cz) {
                    found=chunkMeshes[j];
                    break;
                }
            }
            if (found == null) {
                found = makeChunk(cx,cz);
                unchanged = false;
            }
            nextMeshes[newIndex++] = found;
        }
        if (!unchanged) {
            System.arraycopy(nextMeshes,0,chunkMeshes,0,9);
            System.arraycopy(nextXs,0,chunkX,0,9);
            System.arraycopy(nextZs,0,chunkZ,0,9);
        }
    }

    private Mesh makeChunk(int cx, int cz) {
        Builder b = new Builder();
        float xStart = cx * CHUNK, zStart = cz * CHUNK;
        for (int iz=0;iz<GRID;iz++) for(int ix=0;ix<GRID;ix++) {
            float x=xStart+ix*TILE, z=zStart+iz*TILE;
            float h00=(float)terrain(x,z);
            float h01=(float)terrain(x,z+TILE);
            float h10=(float)terrain(x+TILE,z);
            float h11=(float)terrain(x+TILE,z+TILE);
            float avg=(h00+h01+h10+h11)*.25f;
            float grassNoise=(float)(Math.sin(x*.91 + z*.27) * .037);
            float r,g,bl;
            if (avg < -.60f) { r=.62f;g=.74f;bl=.61f; }
            else if (avg < .18f) { r=.90f;g=.80f;bl=.55f; }
            else if (avg > 2.16f) { r=.60f;g=.70f;bl=.68f; }
            else { r=.30f+grassNoise;g=.66f+grassNoise;bl=.36f+grassNoise; }
            b.tri(x,h00,z,x,h01,z+TILE,x+TILE,h11,z+TILE,r,g,bl);
            b.tri(x,h00,z,x+TILE,h11,z+TILE,x+TILE,h10,z,r,g,bl);
        }
        Random rnd = new Random(cx*14356193L ^ cz*73856093L ^ 918273L);
        for (int i=0;i<22;i++) {
            float x=xStart+3f+rnd.nextFloat()*(CHUNK-6f);
            float z=zStart+3f+rnd.nextFloat()*(CHUNK-6f);
            float y=(float)terrain(x,z);
            if (y < .20f) continue;
            // Keep the spawn meadow free for the player.
            if (x*x+z*z<85f) continue;
            if (rnd.nextFloat()<.67f) {
                float size=1.05f+rnd.nextFloat()*1.2f;
                b.cylinder(x,y,z,.16f*size,1.65f*size,7,.41f,.32f,.23f);
                b.cone(x,y+1.1f*size,z, .92f*size, 2.30f*size,7,
                    .13f,.48f+.15f*rnd.nextFloat(),.32f);
                b.cone(x,y+2.2f*size,z, .70f*size, 1.68f*size,7,
                    .16f,.63f,.40f);
            } else {
                float sz=.4f+rnd.nextFloat()*.6f;
                b.sphere(x,y+sz*.25f,z,sz,sz*.65f,sz*.82f,4,6,
                    .47f,.54f,.49f);
            }
        }
        // Ruins, wooden cabins and watchtowers appear in the world in a reproducible way.
        if ((Math.abs(cx*13+cz*7) % 11)==3) {
            float x=xStart+CHUNK*.52f;
            float z=zStart+CHUNK*.48f;
            float y=(float)terrain(x,z);
            if (y>-.15f) {
                b.box(x,y+1.15f,z,3.5f,2.3f,3.4f,.79f,.62f,.40f);
                b.cone(x,y+2.2f,z,3.1f,1.85f,4,.86f,.38f,.28f);
                b.box(x,y+.7f,z-1.76f,.9f,1.4f,.12f,.30f,.43f,.50f);
            }
        }
        if (cx==0 && cz==0) {
            float x=10f,z=10f,y=(float)terrain(x,z);
            b.cylinder(x,y,z,1.1f,9f,12,.90f,.86f,.69f);
            b.cone(x,y+8.9f,z,1.8f,3f,12,.88f,.39f,.24f);
            b.box(x,y+7.4f,z,2.3f,1f,2.3f,.23f,.35f,.43f);
        }
        return b.mesh();
    }

    private void drawClouds() {
        for (int i=0;i<7;i++) {
            int cell = (int)Math.floor(playerX/35f) + i - 3;
            float cloudX=cell*35f + (float)Math.sin(cell*23.1f)*8f + seconds*.15f;
            float cloudZ=playerZ-38f+(float)Math.sin(cell*13.1f)*25f;
            float cloudY=18f+(float)Math.sin(cell*3.3f)*3f;
            draw(gooseSphere,cloudX,cloudY,cloudZ,7.0f,1.7f,3.0f,0,0,.98f,.99f,1f);
            draw(gooseSphere,cloudX+2f,cloudY+.9f,cloudZ,4.4f,2.6f,2.9f,0,0,1f,1f,1f);
        }
    }

    private void drawLandmarks() {
        // A volumetric sun stays on the horizon; it never blocks exploration.
        draw(gooseSphere,playerX+18f,36f,playerZ-59f,9,9,9,0,0,1f,.94f,.57f);
        // Shadow under the goose makes the flying height readable.
        float floor=(float)terrain(playerX,playerZ)+.04f;
        draw(gooseSphere,playerX,floor,playerZ,2.1f,.07f,1.5f,0,0,.20f,.43f,.31f);
    }

    private void drawRings() {
        for (int i=0;i<3;i++) {
            float x=(float)nativeRingX(i);
            float y=(float)nativeRingY(i);
            float z=(float)nativeRingZ(i);
            float bob=(float)Math.sin(seconds*3f+i)*.22f;
            draw(torus,x,y+bob,z,1f,1f,1f,seconds*47f+i*90f,0,
                    1f,.81f,.24f);
            draw(gooseSphere,x,y+bob+1.3f,z,.27f,.27f,.27f,0,0,
                    1f,.97f,.50f);
        }
    }

    private void drawGoose() {
        float vx=pxVel, vz=pzVel;
        if (vx*vx+vz*vz>.08f)
            lastHeading=(float)(-Math.atan2(vx,-vz)*180.0/Math.PI);
        float wingFlap=(float)Math.sin(seconds*13.2)*22f;
        float bank=Math.max(-12f,Math.min(12f,pxVel*1.9f));
        birdPart(gooseSphere,0,0,0,1.15f,.71f,1.45f,bank,.93f,.95f,1f);
        birdPart(gooseSphere,0,.54f,-.56f,.46f,1.1f,.44f,0,.96f,.97f,1f);
        birdPart(gooseSphere,0,1.14f,-.77f,.69f,.62f,.70f,0,1f,1f,1f);
        birdPart(gooseCube,0,1.01f,-1.19f,.45f,.19f,.50f,0,1f,.54f,.15f);
        birdPart(gooseSphere,-.22f,1.24f,-1.13f,.14f,.18f,.15f,0,.16f,.21f,.26f);
        birdPart(gooseSphere,.22f,1.24f,-1.13f,.14f,.18f,.15f,0,.16f,.21f,.26f);
        birdPart(gooseSphere,-.73f,.10f,-.02f,.85f,.25f,.85f,wingFlap,.84f,.90f,.97f);
        birdPart(gooseSphere,.73f,.10f,-.02f,.85f,.25f,.85f,-wingFlap,.84f,.90f,.97f);
        birdPart(gooseCone,0,.07f,1.00f,.46f,.57f,.55f,0,.86f,.90f,.96f);
        birdPart(gooseCube,-.28f,-.60f,.12f,.16f,.40f,.19f,0,1f,.55f,.20f);
        birdPart(gooseCube,.28f,-.60f,.12f,.16f,.40f,.19f,0,1f,.55f,.20f);
    }

    private void birdPart(Mesh mesh,float lx,float ly,float lz,
                          float sx,float sy,float sz,float roll,float r,float g,float b) {
        double yaw=Math.toRadians(lastHeading);
        float sn=(float)Math.sin(yaw), cs=(float)Math.cos(yaw);
        float wx=playerX+lx*cs+lz*sn;
        float wz=playerZ-lx*sn+lz*cs;
        draw(mesh,wx,playerY+ly,wz,sx,sy,sz,lastHeading,roll,r,g,b);
    }

    private static final class Mesh {
        final FloatBuffer buffer;
        final int count;
        Mesh(float[] data,int length) {
            buffer=ByteBuffer.allocateDirect(length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            buffer.put(data,0,length);
            buffer.position(0);
            count=length/9;
        }
    }

    private static final class Builder {
        float[] data=new float[4096];
        int size;
        void vertex(float x,float y,float z,float nx,float ny,float nz,
                    float r,float g,float b) {
            if(size+9>data.length) data=Arrays.copyOf(data,data.length*2);
            data[size++]=x;data[size++]=y;data[size++]=z;
            data[size++]=nx;data[size++]=ny;data[size++]=nz;
            data[size++]=r;data[size++]=g;data[size++]=b;
        }
        void tri(float ax,float ay,float az,float bx,float by,float bz,
                 float cx,float cy,float cz,float r,float g,float b) {
            float ux=bx-ax,uy=by-ay,uz=bz-az;
            float vx=cx-ax,vy=cy-ay,vz=cz-az;
            float nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
            float len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(len<.00001f){nx=0;ny=1;nz=0;} else {nx/=len;ny/=len;nz/=len;}
            vertex(ax,ay,az,nx,ny,nz,r,g,b);
            vertex(bx,by,bz,nx,ny,nz,r,g,b);
            vertex(cx,cy,cz,nx,ny,nz,r,g,b);
        }
        void quad(float ax,float ay,float az,float bx,float by,float bz,
                  float cx,float cy,float cz,float dx,float dy,float dz,
                  float r,float g,float b) {
            tri(ax,ay,az,bx,by,bz,cx,cy,cz,r,g,b);
            tri(ax,ay,az,cx,cy,cz,dx,dy,dz,r,g,b);
        }
        void box(float x,float y,float z,float sx,float sy,float sz,float r,float g,float b) {
            float lx=x-sx*.5f,hx=x+sx*.5f;
            float ly=y-sy*.5f,hy=y+sy*.5f;
            float lz=z-sz*.5f,hz=z+sz*.5f;
            quad(lx,ly,hz,hx,ly,hz,hx,hy,hz,lx,hy,hz,r,g,b);
            quad(hx,ly,lz,lx,ly,lz,lx,hy,lz,hx,hy,lz,r,g,b);
            quad(lx,ly,lz,lx,ly,hz,lx,hy,hz,lx,hy,lz,r,g,b);
            quad(hx,ly,hz,hx,ly,lz,hx,hy,lz,hx,hy,hz,r,g,b);
            quad(lx,hy,hz,hx,hy,hz,hx,hy,lz,lx,hy,lz,r,g,b);
            quad(lx,ly,lz,hx,ly,lz,hx,ly,hz,lx,ly,hz,r,g,b);
        }
        void cylinder(float x,float y,float z,float radius,float height,int edges,
                      float r,float g,float b) {
            for(int i=0;i<edges;i++){
                double a=i*Math.PI*2/edges,c=(i+1)*Math.PI*2/edges;
                float x0=x+(float)Math.cos(a)*radius,z0=z+(float)Math.sin(a)*radius;
                float x1=x+(float)Math.cos(c)*radius,z1=z+(float)Math.sin(c)*radius;
                quad(x0,y,z0,x1,y,z1,x1,y+height,z1,x0,y+height,z0,r,g,b);
            }
        }
        void cone(float x,float y,float z,float radius,float height,int edges,
                  float r,float g,float b) {
            for(int i=0;i<edges;i++){
                double a=i*Math.PI*2/edges,c=(i+1)*Math.PI*2/edges;
                float x0=x+(float)Math.cos(a)*radius,z0=z+(float)Math.sin(a)*radius;
                float x1=x+(float)Math.cos(c)*radius,z1=z+(float)Math.sin(c)*radius;
                tri(x0,y,z0,x1,y,z1,x,y+height,z,r,g,b);
            }
        }
        void sphere(float x,float y,float z,float sx,float sy,float sz,
                    int stacks,int slices,float r,float g,float b) {
            for(int j=0;j<stacks;j++){
                double v0=-Math.PI*.5+Math.PI*j/stacks;
                double v1=-Math.PI*.5+Math.PI*(j+1)/stacks;
                for(int i=0;i<slices;i++){
                    double a=i*Math.PI*2/slices,c=(i+1)*Math.PI*2/slices;
                    float ax=x+(float)(Math.cos(v0)*Math.cos(a))*sx;
                    float ay=y+(float)Math.sin(v0)*sy;
                    float az=z+(float)(Math.cos(v0)*Math.sin(a))*sz;
                    float bx=x+(float)(Math.cos(v0)*Math.cos(c))*sx;
                    float by=ay;
                    float bz=z+(float)(Math.cos(v0)*Math.sin(c))*sz;
                    float cx=x+(float)(Math.cos(v1)*Math.cos(c))*sx;
                    float cy=y+(float)Math.sin(v1)*sy;
                    float cz=z+(float)(Math.cos(v1)*Math.sin(c))*sz;
                    float dx=x+(float)(Math.cos(v1)*Math.cos(a))*sx;
                    float dy=cy;
                    float dz=z+(float)(Math.cos(v1)*Math.sin(a))*sz;
                    tri(ax,ay,az,bx,by,bz,cx,cy,cz,r,g,b);
                    tri(ax,ay,az,cx,cy,cz,dx,dy,dz,r,g,b);
                }
            }
        }
        Mesh mesh() { return new Mesh(data,size); }
    }

    private static Mesh makeTorus() {
        Builder b=new Builder();
        final int steps=28, tubeSteps=8;
        for (int i=0;i<steps;i++) for(int j=0;j<tubeSteps;j++) {
            double a=i*2*Math.PI/steps,c=(i+1)*2*Math.PI/steps;
            double u=j*2*Math.PI/tubeSteps,v=(j+1)*2*Math.PI/tubeSteps;
            float x0=(float)((.93+.105*Math.cos(u))*Math.cos(a));
            float y0=(float)((.93+.105*Math.cos(u))*Math.sin(a));
            float z0=(float)(.105*Math.sin(u));
            float x1=(float)((.93+.105*Math.cos(u))*Math.cos(c));
            float y1=(float)((.93+.105*Math.cos(u))*Math.sin(c));
            float z1=z0;
            float x2=(float)((.93+.105*Math.cos(v))*Math.cos(c));
            float y2=(float)((.93+.105*Math.cos(v))*Math.sin(c));
            float z2=(float)(.105*Math.sin(v));
            float x3=(float)((.93+.105*Math.cos(v))*Math.cos(a));
            float y3=(float)((.93+.105*Math.cos(v))*Math.sin(a));
            float z3=z2;
            b.quad(x0,y0,z0,x1,y1,z1,x2,y2,z2,x3,y3,z3,1,1,1);
        }
        return b.mesh();
    }
}
