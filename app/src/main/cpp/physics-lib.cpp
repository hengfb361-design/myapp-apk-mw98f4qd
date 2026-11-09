#include <jni.h>
#include <cmath>

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_poolmod_DynamicCanvasView_calculateDynamicLines(
        JNIEnv* env,
        jobject /* this */,
        jfloat width,
        jfloat height) {

    float cueX = width * 0.35f;
    float cueY = height * 0.52f;
    float hitX = width * 0.72f;
    float hitY = height * 0.52f;

    float targetEndX = width * 0.86f;
    float targetEndY = height * 0.35f;

    float cushX1 = width * 0.85f;
    float cushY1 = height * 0.75f;
    float cushX2 = width * 0.60f;
    float cushY2 = height * 0.82f;

    jfloat coords[12] = {
        cueX, cueY,
        hitX, hitY,
        targetEndX, targetEndY,
        cushX1, cushY1,
        cushX2, cushY2,
        hitX, hitY
    };

    jfloatArray result = env->NewFloatArray(12);
    env->SetFloatArrayRegion(result, 0, 12, coords);
    return result;
}
