package com.fongmi.android.tv;

import com.fongmi.android.tv.bean.Update;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class UpdaterFutureTest {

    @Test
    public void completedFutureIsReturnedAfterSharedDeadlineExpires() throws Exception {
        Update expected = Update.empty(Update.CHANNEL_BETA);
        Future<Update> future = CompletableFuture.completedFuture(expected);
        Method method = Updater.class.getDeclaredMethod("awaitUpdate", Future.class, String.class, long.class);
        method.setAccessible(true);

        Update actual = (Update) method.invoke(Updater.create(), future, Update.CHANNEL_BETA, Long.MIN_VALUE);

        assertSame(expected, actual);
    }

    @Test
    public void updateDeadlineCoversTheCompleteFallbackRequestChain() throws Exception {
        Field requestTimeout = Updater.class.getDeclaredField("GITHUB_REQUEST_TIMEOUT_MS");
        Field updateTimeout = Updater.class.getDeclaredField("UPDATE_CHECK_TIMEOUT_MS");
        requestTimeout.setAccessible(true);
        updateTimeout.setAccessible(true);

        long perRequest = requestTimeout.getLong(null);
        long total = updateTimeout.getLong(null);

        assertTrue("Fixed manifest, mirror, release API, asset API and release notes must fit in the channel deadline",
                total >= perRequest * 5);
    }
}
