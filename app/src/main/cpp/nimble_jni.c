// JNI bridge between Kotlin (app.nimble.engine.CactusNative) and the Cactus C ABI.
//
// The engine is a process-wide singleton: one Needle session at a time, and calls must
// be serialized. Kotlin guarantees that by running every call on one thread.
//
// Strings cross the boundary as NUL-terminated UTF-8 byte arrays (not jstring) so that
// emoji and other 4-byte characters survive (JNI "modified UTF-8" would mangle them).

#include <jni.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>
#include <android/log.h>

#define TAG "nimble-jni"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ---- Cactus C ABI (mirrors cactus/agent/runtime.py in cactus-compute 4.x) ----
extern int cactus_load(const char *data, uint64_t size);
extern const char *cactus_last_error(void);
extern int cactus_init(const char *system, const char *tools_json, const char *tool_index_path);
extern int cactus_complete(const char *text, const float *samples, int sample_count,
                           int max_new_tokens, char *out, int out_size);
extern void cactus_set_threads(int threads);
extern void cactus_reset(void);
extern int cactus_transcribe(const float *samples, int sample_count, const char *language,
                             const char *keywords, int word_timestamps, char *out, int out_size);

#define COMPLETE_BUFFER (64 * 1024)
#define MAX_MAPS 8

// Model files stay memory-mapped for the life of the process. The mapping is file
// backed (pages are evictable), so this costs address space rather than RAM, and it
// keeps the engine safe whether or not it copies the archive on load.
typedef struct { char path[512]; void *addr; size_t size; } mapping_t;
static mapping_t g_maps[MAX_MAPS];
static char g_error[1024];

static void set_error(const char *message) {
    snprintf(g_error, sizeof(g_error), "%s", message ? message : "unknown error");
}

static void set_engine_error(const char *fallback) {
    const char *e = cactus_last_error();
    set_error((e && e[0]) ? e : fallback);
}

static jbyteArray to_bytes(JNIEnv *env, const char *s) {
    size_t n = strlen(s);
    jbyteArray out = (*env)->NewByteArray(env, (jsize) n);
    if (out) (*env)->SetByteArrayRegion(env, out, 0, (jsize) n, (const jbyte *) s);
    return out;
}

// Copies a Java byte[] into a NUL-terminated C string. Returns NULL for a null array.
static char *dup_bytes(JNIEnv *env, jbyteArray array) {
    if (!array) return NULL;
    jsize n = (*env)->GetArrayLength(env, array);
    char *s = (char *) malloc((size_t) n + 1);
    if (!s) return NULL;
    (*env)->GetByteArrayRegion(env, array, 0, n, (jbyte *) s);
    s[n] = '\0';
    return s;
}

JNIEXPORT jint JNICALL
Java_app_nimble_engine_CactusNative_loadModel(JNIEnv *env, jclass cls, jstring jpath) {
    (void) cls;
    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
    if (!path) { set_error("bad path"); return -1; }

    int fd = open(path, O_RDONLY);
    if (fd < 0) {
        set_error("cannot open model file");
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return -1;
    }
    struct stat st;
    if (fstat(fd, &st) != 0 || st.st_size <= 0) {
        close(fd);
        set_error("model file is empty");
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return -1;
    }
    void *addr = mmap(NULL, (size_t) st.st_size, PROT_READ, MAP_PRIVATE, fd, 0);
    close(fd);
    if (addr == MAP_FAILED) {
        set_error("cannot map model file");
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return -1;
    }

    int rc = cactus_load((const char *) addr, (uint64_t) st.st_size);
    if (rc < 0) {
        set_engine_error("cactus_load failed");
        munmap(addr, (size_t) st.st_size);
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return rc;
    }

    // Remember the mapping (replacing an older mapping of the same path).
    int slot = -1;
    for (int i = 0; i < MAX_MAPS; i++) {
        if (g_maps[i].addr && strcmp(g_maps[i].path, path) == 0) { slot = i; break; }
    }
    if (slot < 0) {
        for (int i = 0; i < MAX_MAPS; i++) if (!g_maps[i].addr) { slot = i; break; }
    }
    if (slot >= 0) {
        // An older mapping of the same file is kept alive on purpose: the engine may still
        // reference it. It is only address space.
        snprintf(g_maps[slot].path, sizeof(g_maps[slot].path), "%s", path);
        g_maps[slot].addr = addr;
        g_maps[slot].size = (size_t) st.st_size;
    }
    (*env)->ReleaseStringUTFChars(env, jpath, path);
    return rc;
}

JNIEXPORT jbyteArray JNICALL
Java_app_nimble_engine_CactusNative_lastError(JNIEnv *env, jclass cls) {
    (void) cls;
    return to_bytes(env, g_error);
}

JNIEXPORT jint JNICALL
Java_app_nimble_engine_CactusNative_init(JNIEnv *env, jclass cls, jbyteArray jsystem, jbyteArray jtools) {
    (void) cls;
    char *system = dup_bytes(env, jsystem);
    char *tools = dup_bytes(env, jtools);
    int rc = cactus_init(system ? system : "", tools ? tools : "[]", NULL);
    if (rc < 0) set_engine_error("cactus_init failed");
    free(system);
    free(tools);
    return rc;
}

JNIEXPORT jbyteArray JNICALL
Java_app_nimble_engine_CactusNative_complete(JNIEnv *env, jclass cls, jbyteArray jtext, jint max_new_tokens) {
    (void) cls;
    char *text = dup_bytes(env, jtext);
    char *out = (char *) calloc(COMPLETE_BUFFER, 1);
    if (!out) { free(text); set_error("out of memory"); return NULL; }

    int rc = cactus_complete(text ? text : "", NULL, 0, (int) max_new_tokens, out, COMPLETE_BUFFER);
    free(text);
    if (rc < 0) {
        out[COMPLETE_BUFFER - 1] = '\0';
        if (out[0]) set_error(out); else set_engine_error("cactus_complete failed");
        LOGE("complete failed (%d): %s", rc, g_error);
        free(out);
        return NULL;
    }
    out[COMPLETE_BUFFER - 1] = '\0';
    jbyteArray result = to_bytes(env, out);
    free(out);
    return result;
}

JNIEXPORT void JNICALL
Java_app_nimble_engine_CactusNative_reset(JNIEnv *env, jclass cls) {
    (void) env; (void) cls;
    cactus_reset();
}

JNIEXPORT void JNICALL
Java_app_nimble_engine_CactusNative_setThreads(JNIEnv *env, jclass cls, jint threads) {
    (void) env; (void) cls;
    cactus_set_threads((int) threads);
}

JNIEXPORT jbyteArray JNICALL
Java_app_nimble_engine_CactusNative_transcribe(JNIEnv *env, jclass cls, jfloatArray jsamples, jbyteArray jlanguage) {
    (void) cls;
    jsize count = (*env)->GetArrayLength(env, jsamples);
    if (count <= 0) { set_error("no audio"); return NULL; }

    float *samples = (float *) malloc(sizeof(float) * (size_t) count);
    if (!samples) { set_error("out of memory"); return NULL; }
    (*env)->GetFloatArrayRegion(env, jsamples, 0, count, samples);

    char *language = dup_bytes(env, jlanguage);
    // Same sizing rule as cactus/agent/whistle.py: 4 KB of output per second of audio.
    int size = (int) (count / 16000 + 1) * 4096;
    if (size < COMPLETE_BUFFER) size = COMPLETE_BUFFER;
    char *out = (char *) calloc((size_t) size, 1);
    if (!out) { free(samples); free(language); set_error("out of memory"); return NULL; }

    int rc = cactus_transcribe(samples, (int) count, language, NULL, 0, out, size);
    free(samples);
    free(language);
    if (rc < 0) {
        set_engine_error("cactus_transcribe failed");
        free(out);
        return NULL;
    }
    out[size - 1] = '\0';
    jbyteArray result = to_bytes(env, out);
    free(out);
    return result;
}
