#include <jni.h>
#include <stdio.h>
#include <stdlib.h>

#include <mpv/client.h>

#include "jni_utils.h"
#include "log.h"
#include "globals.h"

extern "C" {
    jni_func(jint, setOptionString, jstring option, jstring value);

    jni_func(jobject, getPropertyInt, jstring property);
    jni_func(void, setPropertyInt, jstring property, jint value);
    jni_func(jobject, getPropertyDouble, jstring property);
    jni_func(void, setPropertyDouble, jstring property, jdouble value);
    jni_func(jobject, getPropertyBoolean, jstring property);
    jni_func(void, setPropertyBoolean, jstring property, jboolean value);
    jni_func(jstring, getPropertyString, jstring jproperty);
    jni_func(void, setPropertyString, jstring jproperty, jstring jvalue);
    jni_func(void, dumpTrackList);

    jni_func(void, observeProperty, jstring property, jint format);
}

static void log_node(const char *path, const mpv_node &node) {
    if (node.format == MPV_FORMAT_NODE_ARRAY || node.format == MPV_FORMAT_NODE_MAP) {
        mpv_node_list *list = node.u.list;
        if (!list) return;
        for (int i = 0; i < list->num; ++i) {
            char child[256];
            const char *key = node.format == MPV_FORMAT_NODE_MAP && list->keys ? list->keys[i] : nullptr;
            if (key) snprintf(child, sizeof(child), "%s/%s", path, key);
            else snprintf(child, sizeof(child), "%s/%d", path, i);
            log_node(child, list->values[i]);
        }
        return;
    }
    switch (node.format) {
        case MPV_FORMAT_STRING: ALOGV("mpv-node %s string=%s", path, node.u.string ? node.u.string : ""); break;
        case MPV_FORMAT_FLAG: ALOGV("mpv-node %s flag=%d", path, node.u.flag); break;
        case MPV_FORMAT_INT64: ALOGV("mpv-node %s int=%lld", path, static_cast<long long>(node.u.int64)); break;
        case MPV_FORMAT_DOUBLE: ALOGV("mpv-node %s double=%f", path, node.u.double_); break;
        case MPV_FORMAT_NONE: ALOGV("mpv-node %s none", path); break;
        default: ALOGV("mpv-node %s format=%d", path, node.format); break;
    }
}

jni_func(void, dumpTrackList) {
    CHECK_MPV_INIT();
    mpv_node node{};
    int result = mpv_get_property(g_mpv, "track-list", MPV_FORMAT_NODE, &node);
    if (result < 0) {
        ALOGE("mpv track-list node failed: %s", mpv_error_string(result));
        return;
    }
    log_node("track-list", node);
    mpv_free_node_contents(&node);
}

jni_func(jint, setOptionString, jstring joption, jstring jvalue) {
    CHECK_MPV_INIT();

    const char *option = env->GetStringUTFChars(joption, NULL);
    const char *value = env->GetStringUTFChars(jvalue, NULL);

    int result = mpv_set_option_string(g_mpv, option, value);

    env->ReleaseStringUTFChars(joption, option);
    env->ReleaseStringUTFChars(jvalue, value);

    return result;
}

static int common_get_property(JNIEnv *env, jstring jproperty, mpv_format format, void *output)
{
    CHECK_MPV_INIT();

    const char *prop = env->GetStringUTFChars(jproperty, NULL);
    int result = mpv_get_property(g_mpv, prop, format, output);
    if (result == MPV_ERROR_PROPERTY_UNAVAILABLE)
        ALOGV("mpv_get_property(%s) format %d was unavailable", prop, format);
    else if (result < 0)
        ALOGE("mpv_get_property(%s) format %d returned error %s", prop, format, mpv_error_string(result));
    env->ReleaseStringUTFChars(jproperty, prop);

    return result;
}

static int common_set_property(JNIEnv *env, jstring jproperty, mpv_format format, void *value)
{
    CHECK_MPV_INIT();

    const char *prop = env->GetStringUTFChars(jproperty, NULL);
    int result = mpv_set_property(g_mpv, prop, format, value);
    if (result < 0)
        ALOGE("mpv_set_property(%s, %p) format %d returned error %s", prop, value, format, mpv_error_string(result));
    env->ReleaseStringUTFChars(jproperty, prop);

    return result;
}

jni_func(jobject, getPropertyInt, jstring jproperty) {
    int64_t value = 0;
    if (common_get_property(env, jproperty, MPV_FORMAT_INT64, &value) < 0)
        return NULL;
    return env->NewObject(java_Integer, java_Integer_init, (jint)value);
}

jni_func(jobject, getPropertyDouble, jstring jproperty) {
    double value = 0;
    if (common_get_property(env, jproperty, MPV_FORMAT_DOUBLE, &value) < 0)
        return NULL;
    return env->NewObject(java_Double, java_Double_init, (jdouble)value);
}

jni_func(jobject, getPropertyBoolean, jstring jproperty) {
    int value = 0;
    if (common_get_property(env, jproperty, MPV_FORMAT_FLAG, &value) < 0)
        return NULL;
    return env->NewObject(java_Boolean, java_Boolean_init, (jboolean)value);
}

jni_func(jstring, getPropertyString, jstring jproperty) {
    char *value;
    if (common_get_property(env, jproperty, MPV_FORMAT_STRING, &value) < 0)
        return NULL;
    jstring jvalue = env->NewStringUTF(value);
    mpv_free(value);
    return jvalue;
}

jni_func(void, setPropertyInt, jstring jproperty, jint jvalue) {
    int64_t value = static_cast<int64_t>(jvalue);
    common_set_property(env, jproperty, MPV_FORMAT_INT64, &value);
}

jni_func(void, setPropertyDouble, jstring jproperty, jdouble jvalue) {
    double value = static_cast<double>(jvalue);
    common_set_property(env, jproperty, MPV_FORMAT_DOUBLE, &value);
}

jni_func(void, setPropertyBoolean, jstring jproperty, jboolean jvalue) {
    int value = jvalue == JNI_TRUE ? 1 : 0;
    common_set_property(env, jproperty, MPV_FORMAT_FLAG, &value);
}

jni_func(void, setPropertyString, jstring jproperty, jstring jvalue) {
    const char *value = env->GetStringUTFChars(jvalue, NULL);
    common_set_property(env, jproperty, MPV_FORMAT_STRING, &value);
    env->ReleaseStringUTFChars(jvalue, value);
}

jni_func(void, observeProperty, jstring property, jint format) {
    CHECK_MPV_INIT();
    const char *prop = env->GetStringUTFChars(property, NULL);
    int result = mpv_observe_property(g_mpv, 0, prop, (mpv_format)format);
    if (result < 0)
        ALOGE("mpv_observe_property(%s) format %d returned error %s", prop, format, mpv_error_string(result));
    env->ReleaseStringUTFChars(property, prop);
}
