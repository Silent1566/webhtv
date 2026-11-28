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

    private static Object action(Spider spider, String command) throws Exception {
        return new JSONTokener(spider.action(command)).nextValue();
    }
}
