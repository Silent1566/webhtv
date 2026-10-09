package com.fongmi.android.tv.ui.dialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.app.Dialog;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;

import androidx.appcompat.widget.LinearLayoutCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogCustomCspBinding;
import com.fongmi.android.tv.ui.custom.CustomNestedScrollView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

/**
 * 用户报告（竖屏手机，见现场照片）：手机版「站点注入」条目较多时，最下方取消/确定被压扁。
 * 设备实测（dev1 模拟器，1080×2160@440dpi 竖屏，5 条注入）：按钮由声明高度 40dp 变成 34.9dp，
 * 最后一张卡片的「启用/修改/首页/删除」被挤出可视区只剩 9dp。
 *
 * <p>根因是竖屏按屏幕比例手算高度：窗口 {@code WRAP_CONTENT} + 滚动区 {@code wrap_content} +
 * {@code 0.58H} 上限 + 无 weight。内容一旦超过窗口可用高度，LinearLayout 无处收缩，底部按钮区
 * 就被摆到窗口下沿之外裁掉。</p>
 *
 * <p>本测试用真实 framework 测量代码把修复后的契约固定下来：</p>
 * <ol>
 *   <li>任意竖向比例下，底部按钮区都保持 40dp 并贴在页面底部，滚动区吃掉剩余空间；</li>
 *   <li>Material 弹窗面板（{@code @id/custom} → customPanel → parentPanel）默认 {@code wrap_content}，
 *       必须一并改成 {@code match_parent}，否则内容比窗口短时页面底部会留空隙、按钮区不贴底；</li>
 *   <li>横屏（电视/横屏手机）仍走 0.76×0.98 比例弹窗，按钮区同样贴底；</li>
 *   <li>变异检验：同一套断言在修复前的参数下必然失败（内容超出窗口 → 按钮区被摆到页面之外），
 *       证明断言确实能抓住本次缺陷，而不是恒真。</li>
 * </ol>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class CustomCspDialogLayoutTest {

    /** 用户现场机型 1080×2400@440dpi ≈ 392.7×872.7dp，另附小屏/大屏/竖屏平板与键盘顶掉 300dp 的窗口。 */
    private static final int[][] PORTRAIT_SCREENS = {
            {393, 873},   // 用户现场机型
            {360, 640},   // 小屏手机
            {412, 915},   // 大屏手机
            {617, 1097},  // 竖屏平板
            {393, 573},   // 用户机型 + 屏幕键盘顶掉 300dp（ADJUST_RESIZE 后的窗口）
    };

    private DialogCustomCspBinding binding;

    @Before
    public void setUp() {
        new App();
        ContextThemeWrapper context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_WebHTV_Dialog);
        binding = DialogCustomCspBinding.inflate(LayoutInflater.from(context));
    }

    @Test
    public void portraitFullPageKeepsFooterAtNaturalHeightForEveryRatio() {
        for (int[] screen : PORTRAIT_SCREENS) {
            int width = dp(screen[0]);
            int available = dp(screen[1]) - statusBarPx();
            ViewGroup panel = panelChain();
            tallList();
            CustomCspDialog.applyPageSizing(hostWindow(), binding.root, binding.contentScroll, false, width, dp(screen[1]));
            measure(panel, width, available);
            String label = screen[0] + "x" + screen[1] + "dp:";
            assertEquals(label + " root 必须撑满整页", available, binding.root.getMeasuredHeight());
            assertEquals(label + " 取消按钮被压扁", dp(40), binding.negative.getMeasuredHeight());
            assertEquals(label + " 确定按钮被压扁", dp(40), binding.positive.getMeasuredHeight());
            assertEquals(label + " 按钮区被压扁", dp(40), binding.footer.getMeasuredHeight());
            assertEquals(label + " 按钮区必须贴底", available - binding.root.getPaddingBottom(), binding.footer.getBottom());
            assertTrue(label + " 滚动区必须吃到剩余空间", binding.contentScroll.getMeasuredHeight() > 0);
            assertTrue(label + " 滚动区不能压在按钮区上", binding.contentScroll.getBottom() <= binding.footer.getTop());
        }
    }

    /**
     * 内容比窗口短时（文本模式 JSON 编辑器、空搜索结果）也必须撑满整页：面板链默认 wrap_content，
     * 靠 {@code expandToWindow} 把 {@code @id/custom → customPanel → parentPanel} 一起改成 match_parent，
     * 否则 root 的 match_parent 会退化成按内容高度，页面底部露出后面的界面、按钮区不贴底。
     */
    @Test
    public void shortContentStillFillsTheWholePage() {
        for (int[] screen : PORTRAIT_SCREENS) {
            int width = dp(screen[0]);
            int available = dp(screen[1]) - statusBarPx();
            ViewGroup panel = panelChain();
            // 面板链先按内容高度固定一次，模拟"内容很短、窗口有余量"的短内容状态。
            ViewGroup.LayoutParams rootParams = binding.root.getLayoutParams();
            rootParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            binding.root.setLayoutParams(rootParams);
            CustomNestedScrollView scroll = binding.contentScroll;
            LinearLayoutCompat.LayoutParams scrollParams = (LinearLayoutCompat.LayoutParams) scroll.getLayoutParams();
            scrollParams.height = dp(120);
            scrollParams.weight = 0;
            scroll.setLayoutParams(scrollParams);
            CustomCspDialog.applyPageSizing(hostWindow(), binding.root, scroll, false, width, dp(screen[1]));
            measure(panel, width, available);
            String label = screen[0] + "x" + screen[1] + "dp:";
            assertEquals(label + " 短内容也必须撑满整页", available, binding.root.getMeasuredHeight());
            assertEquals(label + " 短内容时按钮区仍要贴底", available - binding.root.getPaddingBottom(), binding.footer.getBottom());
            assertEquals(label + " 短内容时按钮区仍保持 40dp", dp(40), binding.footer.getMeasuredHeight());
        }
    }

    /** 电视/横屏保持原有比例弹窗：窗口 0.76×0.98，按钮区同样保持 40dp 并贴底。 */
    @Test
    public void landscapeKeepsProportionalDialogAndPinnedFooter() {
        int screenWidth = dp(686);
        int screenHeight = dp(386);
        int width = (int) (screenWidth * 0.76f);
        int height = (int) (screenHeight * 0.98f);
        ViewGroup panel = panelChain();
        CustomCspDialog.applyPageSizing(hostWindow(), binding.root, binding.contentScroll, true, screenWidth, screenHeight);
        assertEquals("横屏窗口高度仍是 0.98H", height, binding.root.getLayoutParams().height);
        assertEquals("滚动区吃权重", 1f, ((LinearLayoutCompat.LayoutParams) binding.contentScroll.getLayoutParams()).weight, 0f);
        assertEquals("滚动区高度交给权重", 0, ((LinearLayoutCompat.LayoutParams) binding.contentScroll.getLayoutParams()).height);
        measure(panel, width, height);
        assertEquals("横屏按钮区被压扁", dp(40), binding.footer.getMeasuredHeight());
        assertEquals("横屏按钮区必须贴底", height - binding.root.getPaddingBottom(), binding.footer.getBottom());
        assertTrue("横屏滚动区必须吃到剩余空间", binding.contentScroll.getMeasuredHeight() > 0);
    }

    /**
     * 变异检验：换回修复前的竖屏参数（窗口按内容高度、滚动区 0.58H 上限、无 weight），在内容超过
     * 窗口可用高度的机型上按钮区必然被摆到页面之外——正是设备上看到的"被压缩/被裁掉"。
     */
    @Test
    public void legacyProportionalParamsPushFooterOutsideThePage() {
        int width = dp(360);
        int screenHeight = dp(480);
        int available = screenHeight - statusBarPx();
        ViewGroup panel = panelChain();
        tallList();
        ViewGroup.LayoutParams rootParams = binding.root.getLayoutParams();
        rootParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        binding.root.setLayoutParams(rootParams);
        LinearLayoutCompat.LayoutParams scrollParams = (LinearLayoutCompat.LayoutParams) binding.contentScroll.getLayoutParams();
        scrollParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        scrollParams.weight = 0;
        binding.contentScroll.setLayoutParams(scrollParams);
        binding.contentScroll.setMaxHeight((int) (screenHeight * 0.58f));
        measure(panel, width, available);
        assertTrue("旧参数下按钮区应当被挤出页面（用户看到的压扁）",
                binding.footer.getBottom() > binding.root.getMeasuredHeight());
    }

    /** 模拟"条目很多"：列表内容远高于任何窗口可用高度，行为由尺寸策略决定而不是内容撑高。 */
    private void tallList() {
        binding.recycler.setMinimumHeight(dp(2000));
    }

    /** 复刻 MaterialAlertDialog 装载自定义视图的面板链：全部 wrap_content 高度。 */
    private ViewGroup panelChain() {
        FrameLayout decorContent = new FrameLayout(binding.getRoot().getContext());
        LinearLayoutCompat parentPanel = new LinearLayoutCompat(binding.getRoot().getContext());
        parentPanel.setOrientation(LinearLayoutCompat.VERTICAL);
        FrameLayout customPanel = new FrameLayout(binding.getRoot().getContext());
        FrameLayout custom = new FrameLayout(binding.getRoot().getContext());
        // AlertController: custom.addView(view, new LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        detachRoot();
        custom.addView(binding.getRoot(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        customPanel.addView(custom, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        parentPanel.addView(customPanel, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        decorContent.addView(parentPanel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return decorContent;
    }

    /** 每条用例都会把 root 挂进新链，先摘掉旧父容器避免重复 addView 抛异常。 */
    private void detachRoot() {
        ViewGroup oldParent = (ViewGroup) binding.getRoot().getParent();
        if (oldParent != null) oldParent.removeView(binding.getRoot());
    }

    private Window hostWindow() {
        return new Dialog(binding.getRoot().getContext()).getWindow();
    }

    private void measure(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private int statusBarPx() {
        return dp(24);
    }

    private int dp(int value) {
        return Math.round(value * binding.getRoot().getResources().getDisplayMetrics().density);
    }
}
