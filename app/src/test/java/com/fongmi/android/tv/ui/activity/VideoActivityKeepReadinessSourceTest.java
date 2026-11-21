package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/**
 * 播放页「收藏」的就绪期契约。
 *
 * <p>真机崩溃证据（WebHTV 5.6.0 leanback，UGOOS，Android 14）：
 * {@code VideoActivity.onKeep -> createKeep -> History.getVodPic() NullPointerException}，
 * 进程被系统杀掉。原因是收藏按钮从详情壳子出现那一刻就能按，
 * 而 {@code mHistory} 只在详情回调（{@code setDetail(Vod) -> checkHistory(item)}）里才会赋值，
 * 两个窗口之间的一按就会把 null 当对象用。
 *
 * <p>本契约钉住三件事：
 * <ol>
 *   <li><b>未就绪要说人话</b>：不能静默吞掉按键，否则电视用户会以为遥控器坏了；提示必须是专门的
 *       {@code keep_not_ready}，而不是借投屏那句「播放信息尚未就绪」。</li>
 *   <li><b>未就绪不写脏数据</b>：没有历史就拿不到封面/片名，此时写入会留下一条点不开的收藏，
 *       所以必须在使用 {@code mHistory} 之前返回。</li>
 *   <li><b>取消收藏不被连带挡住</b>：已经有收藏时删除只依赖 key，与详情是否就绪无关，
 *       就绪判断只能拦住「新增」这一支。</li>
 * </ol>
 */
public class VideoActivityKeepReadinessSourceTest {

    private static final String LEANBACK = "app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java";
    private static final String MOBILE = "app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java";

    @Test
    public void tvKeepWaitsForDetailInsteadOfDereferencingMissingHistory() throws Exception {
        assertKeepIsGuarded(LEANBACK, "leanback");
    }

    @Test
    public void mobileKeepWaitsForDetailInsteadOfDereferencingMissingHistory() throws Exception {
        assertKeepIsGuarded(MOBILE, "mobile");
    }

    @Test
    public void keepReadinessHintExistsInEveryShippedLocale() throws Exception {
        assertTrue("英文缺少 keep_not_ready 文案", read("app/src/main/res/values/strings.xml").contains("<string name=\"keep_not_ready\">"));
        assertTrue("简体缺少 keep_not_ready 文案", read("app/src/main/res/values-zh-rCN/strings.xml").contains("<string name=\"keep_not_ready\">"));
        assertTrue("繁体缺少 keep_not_ready 文案", read("app/src/main/res/values-zh-rTW/strings.xml").contains("<string name=\"keep_not_ready\">"));
    }

    private static void assertKeepIsGuarded(String path, String flavor) throws Exception {
        String source = read(path);

        String onKeep = methodBody(source, "private void onKeep()");
        assertTrue(flavor + " 收藏入口必须在新增分支前挡住未就绪状态",
                onKeep.contains("keep == null && !isKeepReady()"));
        assertTrue(flavor + " 未就绪时必须给出专门提示，不能静默吞键",
                onKeep.contains("Notify.show(R.string.keep_not_ready);"));
        int guard = onKeep.indexOf("Notify.show(R.string.keep_not_ready);");
        int add = onKeep.indexOf("createKeep()");
        assertTrue(flavor + " 未就绪提示之后必须直接返回，不得再走到 createKeep()", add > guard);
        assertTrue(flavor + " 未就绪分支必须 return，而不是继续写收藏", onKeep.substring(guard, add).contains("return;"));

        String createKeep = methodBody(source, "private void createKeep()");
        int readiness = createKeep.indexOf("isKeepReady()");
        int firstHistoryRead = createKeep.indexOf("mHistory.getVodPic()");
        assertTrue(flavor + " createKeep() 必须自己再挡一次，防止新增调用点重犯",
                readiness > 0);
        assertTrue(flavor + " 就绪判断必须早于任何 mHistory 读取",
                firstHistoryRead > readiness);

        String ready = methodBody(source, "private boolean isKeepReady()");
        assertTrue(flavor + " 就绪定义必须同时包含历史与站点，站点在接口配置刷新后可能消失",
                ready.contains("mHistory != null") && ready.contains("getSite() != null"));
    }

    /**
     * 取一个方法的方法体：从签名行开始按大括号配平。源码里同一方法名只有一个定义，
     * 用 indexOf 足够，不需要引入语法分析器。
     */
    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("源码里找不到方法：" + signature, start >= 0);
        int open = source.indexOf('{', start);
        assertTrue("方法没有方法体：" + signature, open >= 0);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(open, i + 1);
        }
        throw new AssertionError("方法体没有闭合：" + signature);
    }

    private static String read(String relative) throws Exception {
        Path path = Path.of(relative);
        if (!Files.exists(path) && relative.startsWith("app/")) path = Path.of(relative.substring(4));
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
