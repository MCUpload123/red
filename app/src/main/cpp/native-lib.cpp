// file: native-lib.cpp, target: Android arm64/armv7, NDK r25+
#include <jni.h>
#include <android/log.h>
#include <cmath>
#include <random>
#include <vector>

#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "FireAssist", __VA_ARGS__)

static std::mt19937_64 g_rng{ std::random_device{}() };

static inline float rand_unit() {
    static std::uniform_real_distribution<float> d(-1.f, 1.f);
    return d(g_rng);
}

// cubic bezier between 4 points
static inline float bezier(float t, float p0, float p1, float p2, float p3) {
    float u = 1.f - t;
    return u*u*u*p0 + 3.f*u*u*t*p1 + 3.f*u*t*t*p2 + t*t*t*p3;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_smith_fireassist_NativeBridge_smoothPath(
    JNIEnv* env, jobject,
    jfloat cx, jfloat cy, jfloat headX, jfloat headY,
    jlong durationMs, jfloat jitter)
{
    // control points curve slightly away from straight line, human-like
    float dx = headX - cx;
    float dy = headY - cy;
    float len = std::sqrt(dx*dx + dy*dy);
    if (len < 1.f) { jfloatArray r = env->NewFloatArray(0); return r; }

    // perpendicular unit
    float nx = -dy / len;
    float ny =  dx / len;

    // control points pulled 12% off axis
    float bend = len * 0.12f * rand_unit() + jitter * rand_unit();
    float p1x = cx + dx * 0.33f + nx * bend;
    float p1y = cy + dy * 0.33f + ny * bend;
    float p2x = cx + dx * 0.66f + nx * bend * 0.6f;
    float p2y = cy + dy * 0.66f + ny * bend * 0.6f;

    // emit samples every ~8px along path
    int samples = (int)(len / 8.f) + 4;
    if (samples > 32) samples = 32;

    std::vector<float> out;
    out.reserve(samples * 2);
    for (int i = 0; i <= samples; ++i) {
        float t = (float)i / (float)samples;
        float x = bezier(t, cx, p1x, p2x, headX);
        float y = bezier(t, cy, p1y, p2y, headY);
        out.push_back(x);
        out.push_back(y);
    }
    jfloatArray r = env->NewFloatArray((jsize)out.size());
    env->SetFloatArrayRegion(r, 0, (jsize)out.size(), out.data());
    return r;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_smith_fireassist_NativeBridge_predictHead(
    JNIEnv* env, jobject,
    jfloat px, jfloat py, jfloat cx, jfloat cy, jfloat leadMs)
{
    // simple linear lead; for game, dt between samples ~ frame time (ms)
    float vx = cx - px;
    float vy = cy - py;
    float k = leadMs / 33.f;   // 30fps frame tick assumption
    float predX = cx + vx * k;
    float predY = cy + vy * k;
    jfloatArray r = env->NewFloatArray(2);
    jfloat buf[2] = { predX, predY };
    env->SetFloatArrayRegion(r, 0, 2, buf);
    return r;
}