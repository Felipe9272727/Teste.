#include <jni.h>
#include <stdint.h>
#include <stdio.h>
#include <GLES2/gl2.h>
#include "flappy.h"

/*
 * Platform adapter only. Gameplay, terrain, touch interpretation, vertex
 * meshing, 3D projection, render commands and HUD are all Goose source.
 * JNI cannot be implemented as ordinary Goose functions because Android
 * passes opaque JNIEnv/jobject ABI arguments.
 */
static int goose_started = 0;

// The GLSL multi-level pointer signature needs a tiny typed GL adapter.
uint32_t gs_compile_shader(uint32_t kind, const uint8_t *source) {
    GLuint shader = glCreateShader((GLenum)kind);
    const GLchar *ptr = (const GLchar *)source;
    glShaderSource(shader, 1, &ptr, NULL);
    glCompileShader(shader);
    GLint success = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &success);
    if (!success) {
        char message[512] = {0};
        glGetShaderInfoLog(shader, sizeof(message), NULL, message);
        fprintf(stderr, "Goose GLSL shader failed: %s\n", message);
        glDeleteShader(shader);
        return 0;
    }
    return (uint32_t)shader;
}

JNIEXPORT void JNICALL
Java_com_goose_flappy_FlappyGameView_nativeCreated(JNIEnv *env, jobject self) {
    if (!goose_started) {
        char *argv[] = { (char *)"goose_skies", NULL };
        goose_init(1, argv);
        goose_started = 1;
    }
    surface_created();
}

JNIEXPORT void JNICALL
Java_com_goose_flappy_FlappyGameView_nativeResized(JNIEnv *env, jobject self, jint w, jint h) {
    surface_resized((int32_t)w, (int32_t)h);
}

JNIEXPORT void JNICALL
Java_com_goose_flappy_FlappyGameView_nativeFrame(JNIEnv *env, jobject self, jdouble delta) {
    render_frame((double)delta);
}

JNIEXPORT void JNICALL
Java_com_goose_flappy_FlappyGameView_nativeTouch(JNIEnv *env, jobject self,
       jint action, jint finger, jdouble x, jdouble y, jint w, jint h) {
    touch_event((int32_t)action, (int32_t)finger, (double)x, (double)y,
                (int32_t)w, (int32_t)h);
}

/* Vertex colors and geometry come from actual Blender-evaluated mesh export.
 * The Goose renderer reads the data once, builds its own VBOs, and draws them.
 * No rendering or game-specific decisions are written in C.
 */
#include "goose_assets.h"
int32_t gs_asset_count(int32_t kind) {
    if (kind < 0 || kind >= 3) return 0;
    return goose_asset_counts[kind];
}
float gs_asset_value(int32_t kind, int32_t vertex, int32_t component) {
    if (kind < 0 || kind >= 3 || component < 0 || component >= 9 ||
        vertex < 0 || vertex >= goose_asset_counts[kind]) return 0.0f;
    const float *data = kind == 0 ? goose_asset_0
                       : kind == 1 ? goose_asset_1 : goose_asset_2;
    return data[vertex * 9 + component];
}

JNIEXPORT jint JNICALL
Java_com_goose_flappy_FlappyGameView_nativeScore(JNIEnv *env, jobject self) {
    return get_score();
}
JNIEXPORT jdouble JNICALL
Java_com_goose_flappy_FlappyGameView_nativeDistance(JNIEnv *env, jobject self) {
    return get_distance();
}
