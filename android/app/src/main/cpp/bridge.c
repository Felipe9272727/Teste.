#include <jni.h>
#include <stdint.h>
#include "flappy.h"

// JNI only transports controller state and Goose world positions.
// All flight physics, terrain ground height, ring detection and score live in Goose.
JNIEXPORT void JNICALL Java_com_goose_flappy_FlappyGameView_nativeInit(JNIEnv *env, jobject self) {
    char *argv[] = { "goose_skies", NULL };
    goose_init(1, argv);
    reset_world(17012026);
}
JNIEXPORT void JNICALL Java_com_goose_flappy_FlappyGameView_nativeStep(
    JNIEnv *env, jobject self, jdouble dt, jdouble dx, jdouble dz, jdouble dy, jint turbo) {
    step_world((double)dt, (double)dx, (double)dz, (double)dy, (int32_t)turbo);
}
JNIEXPORT jdouble JNICALL Java_com_goose_flappy_FlappyGameView_nativeX(JNIEnv *env, jobject self) {
    return player_x();
}
JNIEXPORT jdouble JNICALL Java_com_goose_flappy_FlappyGameView_nativeY(JNIEnv *env, jobject self) {
    return player_y();
}
JNIEXPORT jdouble JNICALL Java_com_goose_flappy_FlappyGameView_nativeZ(JNIEnv *env, jobject self) {
    return player_z();
}
JNIEXPORT jdouble JNICALL Java_com_goose_flappy_FlappyGameView_nativeVX(JNIEnv *env, jobject self) {
    return player_vx();
}
JNIEXPORT jdouble JNICALL Java_com_goose_flappy_FlappyGameView_nativeVY(JNIEnv *env, jobject self) {
    return player_vy();
}
JNIEXPORT jdouble JNICALL Java_com_goose_flappy_FlappyGameView_nativeVZ(JNIEnv *env, jobject self) {
    return player_vz();
}
JNIEXPORT jint JNICALL Java_com_goose_flappy_FlappyGameView_nativeScore(JNIEnv *env, jobject self) {
    return (jint)score();
}
JNIEXPORT jdouble JNICALL Java_com_goose_flappy_FlappyGameView_nativeDistance(JNIEnv *env, jobject self) {
    return distance();
}
JNIEXPORT jdouble JNICALL Java_com_goose_flappy_FlappyGameView_nativeTerrain(
    JNIEnv *env, jobject self, jdouble x, jdouble z) {
    return terrain_height((double)x, (double)z);
}
JNIEXPORT jdouble JNICALL Java_com_goose_flappy_FlappyGameView_nativeRingX(
    JNIEnv *env, jobject self, jint index) {
    return ring_x((int32_t)index);
}
JNIEXPORT jdouble JNICALL Java_com_goose_flappy_FlappyGameView_nativeRingY(
    JNIEnv *env, jobject self, jint index) {
    return ring_y((int32_t)index);
}
JNIEXPORT jdouble JNICALL Java_com_goose_flappy_FlappyGameView_nativeRingZ(
    JNIEnv *env, jobject self, jint index) {
    return ring_z((int32_t)index);
}
