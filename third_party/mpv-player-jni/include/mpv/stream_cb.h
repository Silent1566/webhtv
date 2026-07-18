#ifndef MPV_CLIENT_API_STREAM_CB_H_
#define MPV_CLIENT_API_STREAM_CB_H_

#include "client.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef int64_t (*mpv_stream_cb_read_fn)(void *cookie, char *buf, uint64_t nbytes);
typedef int64_t (*mpv_stream_cb_seek_fn)(void *cookie, int64_t offset);
typedef int64_t (*mpv_stream_cb_size_fn)(void *cookie);
typedef void (*mpv_stream_cb_close_fn)(void *cookie);
typedef void (*mpv_stream_cb_cancel_fn)(void *cookie);

typedef enum mpv_stream_cb_control {
    MPV_STREAM_CB_CTRL_GET_TIME_LENGTH = 1,
    MPV_STREAM_CB_CTRL_GET_CURRENT_TIME = 2,
    MPV_STREAM_CB_CTRL_SEEK_TO_TIME = 3,
    MPV_STREAM_CB_CTRL_GET_NUM_CHAPTERS = 4,
    MPV_STREAM_CB_CTRL_GET_CHAPTER_TIME = 5,
    MPV_STREAM_CB_CTRL_GET_LANG = 6,
} mpv_stream_cb_control;

enum {
    MPV_STREAM_CB_CONTROL_UNSUPPORTED = -1,
    MPV_STREAM_CB_CONTROL_ERROR = 0,
    MPV_STREAM_CB_CONTROL_OK = 1,
};

typedef enum mpv_stream_cb_track_type {
    MPV_STREAM_CB_TRACK_VIDEO = 0,
    MPV_STREAM_CB_TRACK_AUDIO = 1,
    MPV_STREAM_CB_TRACK_SUBTITLE = 2,
} mpv_stream_cb_track_type;

typedef struct mpv_stream_cb_lang_req {
    int type;
    int id;
    char name[50];
} mpv_stream_cb_lang_req;

typedef int (*mpv_stream_cb_control_fn)(void *cookie, int command, void *arg);

typedef struct mpv_stream_cb_info {
    void *cookie;
    mpv_stream_cb_read_fn read_fn;
    mpv_stream_cb_seek_fn seek_fn;
    mpv_stream_cb_size_fn size_fn;
    mpv_stream_cb_close_fn close_fn;
    mpv_stream_cb_cancel_fn cancel_fn;
    mpv_stream_cb_control_fn control_fn;
    const char *demuxer;
} mpv_stream_cb_info;

typedef int (*mpv_stream_cb_open_ro_fn)(void *user_data, char *uri, mpv_stream_cb_info *info);

MPV_EXPORT int mpv_stream_cb_add_ro(mpv_handle *ctx, const char *protocol, void *user_data,
                                    mpv_stream_cb_open_ro_fn open_fn);

#ifdef __cplusplus
}
#endif
#endif
