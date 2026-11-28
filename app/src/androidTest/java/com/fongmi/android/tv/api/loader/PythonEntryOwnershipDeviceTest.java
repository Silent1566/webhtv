package com.fongmi.android.tv.api.loader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.subtitle.source.PythonSubtitleScriptRuntime;
import com.fongmi.chaquo.Loader;
import com.github.catvod.Init;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import fi.iki.elonen.NanoHTTPD;

@RunWith(AndroidJUnit4.class)
public final class PythonEntryOwnershipDeviceTest {
    private static final String SOURCE = """
            import builtins
            import sys
            flag = '_webhtv_entry_ownership_failure'
            counter = 0
            if getattr(builtins, flag, False):
                delattr(builtins, flag)
                counter = 999
                raise RuntimeError('entry ownership fixture failure')
            class Spider:
                def init(self, config):
                    return {'module': __name__, 'path': __file__}
                def search(self, request):
                    global counter
                    counter += 1
                    return {'counter': counter}
                def action(self, cmd):
                    global counter
                    if cmd == 'tick':
                        counter += 1
                        return counter
                    if cmd == 'state':
                        return counter
                    if cmd == 'module':
                        return __name__
                    if cmd == 'path':
                        return __file__
                    if cmd.startswith('registered:'):
                        return cmd.split(':', 1)[1] in sys.modules
                    if cmd == 'owned-count':
                        return sum(getattr(item, '__file__', None) == __file__
                                   for item in tuple(sys.modules.values()))
                    if cmd == 'arm':
                        setattr(builtins, flag, True)
                    if cmd == 'disarm' and hasattr(builtins, flag):
                        delattr(builtins, flag)
                    return True
                def destroy(self):
                    pass
            """;

    private static final String RETIRE_SOURCE = """
            import builtins
            import sys
            from threading import Event
            from types import SimpleNamespace
            gate_key = '_webhtv_python_retire_fixture'
            gate = getattr(builtins, gate_key, None)
            if gate is None:
                gate = SimpleNamespace(entered=Event(), release=Event(), destroyed=0, old_module=None)
                setattr(builtins, gate_key, gate)
            class Spider:
                def init(self, role):
                    self.role = role
                    if role == 'retired':
                        gate.old_module = __name__
                        gate.entered.set()
                        if not gate.release.wait(20):
                            raise RuntimeError('fixture release timed out')
                def action(self, cmd):
                    if cmd == 'await-old':
                        return gate.entered.wait(5)
                    if cmd == 'release':
                        gate.release.set()
                        return True
                    if cmd == 'destroyed':
                        return gate.destroyed
                    if cmd == 'old-registered':
                        return gate.old_module in sys.modules
                    if cmd == 'reset':
                        delattr(builtins, gate_key)
                        return True
                    if cmd == 'role':
                        return self.role
                    raise ValueError(cmd)
                def destroy(self):
                    if getattr(self, 'role', None) == 'retired':
                        gate.destroyed += 1
            """;

    @Before
    public void setContext() {
        Init.set(InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext());
    }

    @Test
    public void videoAndSubtitleUsingOneSourceOwnSeparateEntryModules() throws Exception {
        PyLoader video = new PyLoader();
        PythonSubtitleScriptRuntime subtitle = new PythonSubtitleScriptRuntime();
        Spider vod = video.getSpider("probe", "video", SOURCE, "{}");
        assertFalse(vod instanceof SpiderNull);
        try {
            assertEquals(1, action(vod, "tick"));
            assertEquals(2, action(vod, "tick"));
            String videoName = (String) action(vod, "module");
            JSONObject sub = new JSONObject(subtitle.init("subtitle", SOURCE, "{}"));
            String subtitleName = sub.getString("module");
            assertNotEquals(videoName, subtitleName);
            assertEquals(action(vod, "path"), sub.getString("path"));
            assertEquals(1, new JSONObject(subtitle.search("subtitle", "{}")).getInt("counter"));
            assertEquals(2, action(vod, "state"));

            subtitle.destroy("subtitle");
            assertEquals(false, action(vod, "registered:" + subtitleName));
            assertEquals(true, action(vod, "registered:" + videoName));
            assertEquals(3, action(vod, "tick"));
            video.clear();
            assertEquals(false, action(vod, "registered:" + videoName));
        } finally {
            subtitle.destroy("subtitle");
            video.clear();
        }
    }

    @Test
    public void failedReloadLeavesLiveEntryAndRegistrationCountIntact() throws Exception {
        com.fongmi.chaquo.Spider old = new Loader().spider(SOURCE);
        try {
            assertEquals(1, action(old, "tick"));
            Object registrations = action(old, "owned-count");
            assertEquals(true, action(old, "arm"));
            RuntimeException failure = assertThrows(RuntimeException.class, () -> new Loader().spider(SOURCE));
            assertTrue(String.valueOf(failure), failure.getMessage().contains("entry ownership fixture failure"));
            assertEquals(1, action(old, "state"));
            assertEquals(registrations, action(old, "owned-count"));
        } finally {
            action(old, "disarm");
            old.destroy();
        }
    }

    @Test
    public void retiredPythonInitDoesNotBlockNewOwnerOrOfflineFallback() throws Exception {
        var constructor = BaseLoader.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        BaseLoader base = constructor.newInstance();
        Spider control = new Loader().spider(RETIRE_SOURCE + "\n# barrier observer\n");
        var offline = new AtomicBoolean();
        var offlineRequests = new AtomicInteger();
        NanoHTTPD source = new NanoHTTPD("127.0.0.1", 0) {
            @Override
            public Response serve(IHTTPSession session) {
                if (offline.get()) {
                    offlineRequests.incrementAndGet();
                    return newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE, MIME_PLAINTEXT, "offline");
                }
                return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, RETIRE_SOURCE);
            }
        };
        var worker = Executors.newSingleThreadExecutor();
        Future<?> cleanup = null;
        try {
            source.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            String api = "http://127.0.0.1:" + source.getListeningPort() + "/retire-fixture.py";
            var late = worker.submit(() -> base.getSpider("probe", "same", api, "retired", ""));
            assertEquals(true, action(control, "await-old"));
            assertEquals(true, action(control, "old-registered"));
            offline.set(true);
            cleanup = base.clear("python-retire-device");
            cleanup.get(5, TimeUnit.SECONDS);
            assertFalse(late.isDone());
            Spider fresh = base.getSpider("probe", "same", api, "current", "");
            assertFalse(fresh instanceof SpiderNull);
            assertTrue(offlineRequests.get() > 0);
            assertEquals("current", action(fresh, "role"));

            action(control, "release");
            assertTrue(late.get(10, TimeUnit.SECONDS) instanceof SpiderNull);
            assertEquals(1, action(control, "destroyed"));
            assertEquals(false, action(control, "old-registered"));
            assertEquals("current", action(base.getSpider("probe", "same", api, "unused", ""), "role"));
        } finally {
            try {
                action(control, "release");
                worker.shutdown();
                assertTrue(worker.awaitTermination(23, TimeUnit.SECONDS));
                if (cleanup != null) cleanup.get(10, TimeUnit.SECONDS);
                base.clear("python-retire-device-cleanup").get(10, TimeUnit.SECONDS);
            } finally {
                worker.shutdownNow();
                source.stop();
                action(control, "reset");
                control.destroy();
            }
        }
    }

    private static Object action(Spider spider, String command) throws Exception {
        return new JSONTokener(spider.action(command)).nextValue();
    }
}
