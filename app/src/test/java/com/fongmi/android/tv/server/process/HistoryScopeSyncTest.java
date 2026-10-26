package com.fongmi.android.tv.server.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fongmi.android.tv.api.config.VodSiteCatalog;
import com.fongmi.android.tv.bean.History;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 桥接推送"按源落库"的协议契约。
 *
 * <p>真正的落库路径（History.syncTo / VodSiteCatalog.findConfig 命中分支）要 Room 与运行中的
 * 上下文，纯 JVM 单测无法构造；这里锁定的是不依赖 Android 的部分：新协议开关的判定、
 * 未知配置标识的报错、以及按 cid 落库的入口签名存在。
 */
public class HistoryScopeSyncTest {

    private static Map<String, String> params(String configKey) {
        Map<String, String> params = new HashMap<>();
        params.put("type", "history");
        params.put("mode", "1");
        if (configKey != null) params.put("configKey", configKey);
        return params;
    }

    @Test
    public void scopedPushRequiresNonBlankConfigKey() {
        assertTrue(Action.isScopedHistoryPush(params("bridge-1")));
        assertFalse(Action.isScopedHistoryPush(params(null)));
        assertFalse(Action.isScopedHistoryPush(params("")));
        assertFalse(Action.isScopedHistoryPush(params("   ")));
        assertFalse(Action.isScopedHistoryPush(null));
    }

    @Test
    public void scopedPushDoesNotDependOnLegacyConfigField() {
        Map<String, String> params = params("bridge-1");
        assertFalse(params.containsKey("config"));
        assertTrue(Action.isScopedHistoryPush(params));
    }

    @Test
    public void blankConfigKeyIsRejectedBeforeTouchingDatabase() {
        for (String key : new String[]{null, "", "  "}) {
            try {
                VodSiteCatalog.findConfig(key);
                fail("expected IllegalArgumentException for key=" + key);
            } catch (IllegalArgumentException e) {
                assertEquals("缺少配置标识", e.getMessage());
            }
        }
    }

    @Test
    public void cidScopedHistoryEntryPointsExist() throws Exception {
        Method syncTo = History.class.getMethod("syncTo", int.class, List.class);
        assertNotNull(syncTo);
        assertTrue(java.lang.reflect.Modifier.isStatic(syncTo.getModifiers()));

        Method findByName = History.class.getMethod("findByName", int.class, String.class);
        assertNotNull(findByName);
        assertTrue(java.lang.reflect.Modifier.isStatic(findByName.getModifiers()));
        assertEquals(List.class, findByName.getReturnType());
    }

    @Test
    public void legacySyncStillTargetsActiveSource() throws Exception {
        Method legacy = History.class.getMethod("sync", List.class);
        assertTrue(java.lang.reflect.Modifier.isStatic(legacy.getModifiers()));
    }
}
