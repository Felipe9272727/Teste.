#include <jni.h>
#include <stdint.h>
#include "flappy.h"

// Todas estas chamadas acontecem na mesma thread de renderizacao.
JNIEXPORT void JNICALL
Java_com_goose_flappy_FlappyGameView_nativeInit(JNIEnv *env, jobject self) {
    char *argv[] = { "flappy_goose", NULL };
    goose_init(1, argv);
    reset_game(17012026);
}
JNIEXPORT void JNICALL
Java_com_goose_flappy_FlappyGameView_nativeShutdown(JNIEnv *env, jobject self) {
    // Goose v2026.3 nao tem goose_exit; runtime vive ate o fim do processo.
}
JNIEXPORT void JNICALL
Java_com_goose_flappy_FlappyGameView_nativeFlap(JNIEnv *env, jobject self) {
    flap();
}
JNIEXPORT void JNICALL
Java_com_goose_flappy_FlappyGameView_nativeStep(JNIEnv *env, jobject self, jdouble dt) {
    step_game((double)dt);
}
JNIEXPORT jint JNICALL
Java_com_goose_flappy_FlappyGameView_nativeMode(JNIEnv *env, jobject self) {
    return (jint)get_mode();
}
JNIEXPORT jint JNICALL
Java_com_goose_flappy_FlappyGameView_nativeScore(JNIEnv *env, jobject self) {
    return (jint)get_score();
}
JNIEXPORT jint JNICALL
Java_com_goose_flappy_FlappyGameView_nativeBest(JNIEnv *env, jobject self) {
    return (jint)get_best();
}
JNIEXPORT jdouble JNICALL
Java_com_goose_flappy_FlappyGameView_nativeBirdY(JNIEnv *env, jobject self) {
    return (jdouble)get_bird_y();
}
JNIEXPORT jdouble JNICALL
Java_com_goose_flappy_FlappyGameView_nativeBirdVelocity(JNIEnv *env, jobject self) {
    return (jdouble)get_bird_velocity();
}
JNIEXPORT jdouble JNICALL
Java_com_goose_flappy_FlappyGameView_nativePipeX(JNIEnv *env, jobject self, jint index) {
    return (jdouble)get_pipe_x((int32_t)index);
}
JNIEXPORT jdouble JNICALL
Java_com_goose_flappy_FlappyGameView_nativePipeGap(JNIEnv *env, jobject self, jint index) {
    return (jdouble)get_pipe_gap((int32_t)index);
}