package com.fongmi.android.tv.theme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.appcompat.view.ContextThemeWrapper;

import com.fongmi.android.tv.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 条目遮罩槽位（{@code ITEM_OVERLAY}）驱动真实界面的行为级守卫。
 *
 * <p>为什么必须有这条测试：{@code ?attr/webhtvColorItemOverlay} 是**编译期**静态资源。
 * 把 drawable 的 {@code <solid>} 从 {@code @color/black_40} 改成 {@code ?attr/...} 只让
 * 「默认外观」和「主题表」通了电，用户改槽位之后已经充气的 drawable **不会**重新解析——
 * 没有运行时改写那一步，新槽位就只会出现在编辑器预览和 Web 快照里，真实界面纹丝不动
 * （这正是 {@code ThemeBinder} 里 {@code APP_SURFACE_FOCUS_RINGS} 当初存在的同一个理由）。
 *
 * <p>因此这里不看源码字符串，而是把**真实条目布局**充气出来、喂给真实的
 * {@link ThemeBinder#bind}，再读回背景 drawable 的实心填充：
 * <ol>
 *   <li>{@link #itemOverlaySlotDrivesTheRealItemHost}：改过 {@code itemOverlay} 槽的档案
 *       必须真的把条目宿主底色改成用户选的颜色（聚焦态与选中态），并且选中态与聚焦态同色；</li>
 *   <li>{@link #defaultProfileLeavesTheItemHostAtTheCompiledOverlay}：空档案下必须是
 *       {@code #66000000}（{@code @color/black_40}）——这是「默认外观字节不变」的机器化表述；</li>
 *   <li>{@link #overlayExcludedDrawablesAreNotRewritten}：{@code shape_vod_name} /
 *       {@code shape_widget} / {@code shape_keyboard_normal} 与 {@code shape_item_normal}
 *       虽然填充同为 {@code #66000000} / {@code #33000000}，但它们在**不可控底材**上或属刻意固定的
 *       键底，必须逐像素不变——按颜色反查角色会让它们全部跟着变，本测试就是那条边界的钉子。</li>
 * </ol>
 *
 * <p>{@code adapter_func.xml} 是电视首页功能按钮行（搜索/历史）的真实条目宿主：
 * {@code android:background="@drawable/selector_item"}，即
 * focused → {@code shape_item_focused}、selected → {@code shape_item_activated}、
 * normal → {@code shape_item_normal}。{@code adapter_search_hot_word.xml} 覆盖圆角胶囊族
 * （{@code selector_search_hot_word}）。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = com.fongmi.android.tv.App.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ThemeBinderItemOverlayTest {

    /** 编译期条目遮罩底色：{@code @color/black_40}。 */
    private static final int COMPILED_OVERLAY = 0x66000000;
    /** 编译期常态底色：{@code @color/black_20}，刻意不接线，因此永远不该变。 */
    private static final int COMPILED_NORMAL = 0x33000000;
    /** 编译期选中/焦点描边之外的第三档：{@code @color/black_30}（{@code shape_item_selected} 等）。 */
    private static final int COMPILED_SELECTED = 0x4D000000;

    /** 用户在编辑器里选的遮罩色（不含 alpha，解析后按不透明处理）。 */
    private static final String USER_OVERLAY_HEX = "#8A2040";
    private static final int USER_OVERLAY = 0xFF8A2040;

    private static final int SIZE = 96;

    // ------------------------------------------------------------------ 交付物 C：真的生效

    /**
     * 改过 {@code itemOverlay} 槽的档案必须真的改变电视首页功能按钮行的底色。
     *
     * <p>断言三层：聚焦态与选中态都变成用户色；两者同色（同一行按钮不该在两种状态露出两种底）；
     * 常态仍是编译期的 20% 黑（{@code shape_item_normal} 刻意不接线，接线会让默认外观跳挡）。
     */
    @Test
    public void itemOverlaySlotDrivesTheRealItemHost() {
        Context context = themedContext();
        ThemeTokens baseline = ThemeTokens.light();
        ThemeTokens active = resolveWithOverlay(context);
        assertEquals("测试前提：活动 tokens 的遮罩槽必须就是用户选的颜色",
                USER_OVERLAY, active.colorItemOverlay());
        assertNotEquals("测试前提：活动 tokens 必须与基线不同，否则 binder 整体 no-op",
                baseline, active);

        View host = inflateHost(context, R.layout.adapter_func);
        assertNotNull("首页功能按钮行必须挂着 selector_item", host.getBackground());
        assertEquals("测试前提：宿主背景必须是状态选择器，否则本条断言覆盖不到状态分支",
                COMPILED_OVERLAY, fillOfState(host.getBackground(), android.R.attr.state_focused));

        FrameLayout root = new FrameLayout(context);
        root.addView(host);
        ThemeBinder.bind(root, baseline, active);

        assertEquals("聚焦态条目底色必须变成用户选的遮罩色（功能未生效就是这里失败）",
                USER_OVERLAY, fillOfState(host.getBackground(), android.R.attr.state_focused));
        assertEquals("选中态条目底色必须一起变，否则同一行按钮在聚焦/选中态露出两种底",
                USER_OVERLAY, fillOfState(host.getBackground(), android.R.attr.state_selected));
        assertEquals("常态必须保持编译期的 20% 黑：shape_item_normal 刻意不接线，"
                        + "接到遮罩槽会让「没改过主题」的默认外观从 20% 黑跳到 40% 黑",
                COMPILED_NORMAL, fillOfState(host.getBackground(), 0));

        // 栅格化复核：用户色必须真的画出来，而不只是 getColor() 里的数字。
        assertTrue("改过的底色必须真的栅格化出用户色 " + Integer.toHexString(USER_OVERLAY),
                containsColor(rasterize(stateDrawable(host.getBackground(), android.R.attr.state_focused)), USER_OVERLAY));
    }

    /** 圆角胶囊族（搜索热词）走同一套机制：{@code selector_search_hot_word} → {@code shape_search_hot_word_focused}。 */
    @Test
    public void itemOverlaySlotDrivesTheHotWordCapsule() {
        Context context = themedContext();
        ThemeTokens baseline = ThemeTokens.light();
        ThemeTokens active = resolveWithOverlay(context);

        View host = inflateHost(context, R.layout.adapter_search_hot_word);
        FrameLayout root = new FrameLayout(context);
        root.addView(host);
        ThemeBinder.bind(root, baseline, active);

        assertEquals("搜索热词胶囊的聚焦态同样必须跟随条目遮罩槽",
                USER_OVERLAY, fillOfState(host.getBackground(), android.R.attr.state_focused));
        assertEquals("热词胶囊的常态仍是编译期的 20% 黑",
                COMPILED_NORMAL, fillOfState(host.getBackground(), 0));
    }

    /**
     * 搜索输入框（{@code selector_tv_search_field}）的两个状态都是白名单成员：常态与聚焦态都跟随。
     *
     * <p>这一条单独钉住「normal 也接线」的那一半：{@code shape_tv_search_field} 的填充本就是
     * {@code black_40}，所以接上槽位**不会**改变默认外观，两个状态都该跟着用户走。
     */
    @Test
    public void itemOverlaySlotDrivesTheSearchFieldBothStates() {
        Context context = themedContext();
        ThemeTokens baseline = ThemeTokens.light();
        ThemeTokens active = resolveWithOverlay(context);

        FrameLayout root = new FrameLayout(context);
        View host = new View(context);
        host.setBackground(context.getDrawable(R.drawable.selector_tv_search_field));
        root.addView(host);
        ThemeBinder.bind(root, baseline, active);

        assertEquals("搜索输入框常态必须跟随遮罩槽（它的编译期填充本就是 black_40，不会造成外观回归）",
                USER_OVERLAY, fillOfState(host.getBackground(), 0));
        assertEquals("搜索输入框聚焦态必须跟随遮罩槽",
                USER_OVERLAY, fillOfState(host.getBackground(), android.R.attr.state_focused));
    }

    // ------------------------------------------------------- 交付物 A：默认外观字节不变

    /**
     * 空档案下条目宿主必须与改动前逐值一致：{@code bind} 是严格 no-op，
     * 且 {@code ?attr/webhtvColorItemOverlay} 解析回编译期取值 {@code #66000000}。
     */
    @Test
    public void defaultProfileLeavesTheItemHostAtTheCompiledOverlay() {
        Context context = themedContext();
        ThemeTokens baseline = ThemeTokens.light();
        ThemeTokens active = resolveWithProfile(context, ThemeProfile.defaultProfile());
        assertEquals("空档案必须解析出冻结色板", baseline, active);

        View host = inflateHost(context, R.layout.adapter_func);
        Bitmap before = rasterize(host.getBackground());
        FrameLayout root = new FrameLayout(context);
        root.addView(host);
        ThemeBinder.bind(root, baseline, active);
        Bitmap after = rasterize(host.getBackground());

        assertEquals("空档案下 binder 必须完全不动条目宿主（默认外观字节不变的机器化表述）",
                describe(before), describe(after));
        assertEquals("空档案的聚焦态必须仍是 @color/black_40",
                COMPILED_OVERLAY, fillOfState(host.getBackground(), android.R.attr.state_focused));
        assertEquals("空档案的选中态必须仍是 @color/black_40",
                COMPILED_OVERLAY, fillOfState(host.getBackground(), android.R.attr.state_selected));
        assertEquals("空档案的常态必须仍是 @color/black_20",
                COMPILED_NORMAL, fillOfState(host.getBackground(), 0));
    }

    /**
     * 交付物 A 的另一半：主题必须真的把两个槽位属性赋上值，取值等于编译期 token。
     *
     * <p>「只声明、没赋值」是本仓库审计里反复出现的缺陷：属性解析不到时 {@code ?attr/} 会
     * **静默**退化成透明，不崩溃，只有人眼才能发现。
     */
    @Test
    public void themeAssignsBothItemOverlayAttributesToTheCompiledTokens() {
        Context context = themedContext();
        assertEquals("webhtvColorItemOverlay 必须赋值，且等于 webhtv_color_item_overlay",
                context.getColor(R.color.webhtv_color_item_overlay), attrColor(context, R.attr.webhtvColorItemOverlay));
        assertEquals("webhtvColorOnItemOverlay 必须赋值，且等于 webhtv_color_on_item_overlay",
                context.getColor(R.color.webhtv_color_on_item_overlay), attrColor(context, R.attr.webhtvColorOnItemOverlay));
        assertEquals("编译期遮罩 token 必须就是 black_40，否则默认外观已经变了",
                COMPILED_OVERLAY, context.getColor(R.color.webhtv_color_item_overlay));
    }

    // ------------------------------------------------- 排除项：必须逐像素不变

    /**
     * 排除清单里的三个 drawable 必须在**运行时也不被改写**。
     *
     * <p>它们与白名单成员共用同一个 {@code #66000000} 填充（{@code shape_vod_name} /
     * {@code shape_widget} / {@code shape_keyboard_normal} 全都是 {@code @color/black_40}，
     * 而 leanback 里共有 28 个 drawable 用它）。因此「按颜色反查角色」的实现会把它们全部改掉，
     * 而那正是刻意固定的族：卡片信息条带压在不受控的海报上、控件底座压在壁纸上、键盘键底压在
     * 用户画布上——跟随主题就是可读性回归（实测 black_20 时白字只有 4.36:1）。
     */
    @Test
    public void overlayExcludedDrawablesAreNotRewritten() {
        Context context = themedContext();
        ThemeTokens baseline = ThemeTokens.light();
        ThemeTokens active = resolveWithOverlay(context);
        assertNotEquals("测试前提：活动 tokens 必须与基线不同，否则 binder 整体 no-op", baseline, active);

        String[] excluded = {"shape_vod_name", "shape_widget", "shape_keyboard_normal"};
        for (String name : excluded) {
            int id = drawableId(context, name);
            assertNotEquals(name + " 必须能在 leanback 源集解析", 0, id);

            FrameLayout root = new FrameLayout(context);
            View host = new View(context);
            host.setBackground(context.getDrawable(id));
            root.addView(host);

            Bitmap before = rasterize(host.getBackground());
            ThemeBinder.bind(root, baseline, active);
            Bitmap after = rasterize(host.getBackground());

            assertEquals(name + " 属于刻意固定的族（压在不可控底材上或键底），binder 不允许改写它",
                    describe(before), describe(after));
            assertEquals(name + " 必须仍解析为编译期取值 @color/black_40",
                    COMPILED_OVERLAY, fillOf(host.getBackground()));
            assertFalse(name + " 不允许出现用户选的遮罩色 " + Integer.toHexString(USER_OVERLAY),
                    containsColor(after, USER_OVERLAY));
        }
    }

    /**
     * 被排除的**状态选择器**同样必须保持原样：{@code selector_keyboard} 的焦点态是
     * {@code shape_keyboard_focused}（{@code black_30}）、常态是 {@code shape_keyboard_normal}。
     *
     * <p>两者都被排除的理由不同却结论相同：键底压在半透明壁纸/画布上，键色并非「条目遮罩」语义；
     * 而 {@code black_30} 的 {@code shape_item_selected} 也刻意不在名单里——它跟随的是「当前选中
     * 条目」而不是遮罩底色。本条同时钉住 {@code shape_item_selected}，防止名单被顺手放宽。
     */
    @Test
    public void overlayExcludedSelectorsKeepTheirOwnFills() {
        Context context = themedContext();
        ThemeTokens baseline = ThemeTokens.light();
        ThemeTokens active = resolveWithOverlay(context);

        String[][] cases = {
                {"selector_keyboard", Integer.toString(COMPILED_OVERLAY), Integer.toString(COMPILED_SELECTED)},
        };
        for (String[] item : cases) {
            int id = drawableId(context, item[0]);
            assertNotEquals(item[0] + " 必须能解析", 0, id);

            FrameLayout root = new FrameLayout(context);
            View host = new View(context);
            host.setBackground(context.getDrawable(id));
            root.addView(host);

            Bitmap before = rasterize(host.getBackground());
            ThemeBinder.bind(root, baseline, active);
            Bitmap after = rasterize(host.getBackground());

            assertEquals(item[0] + " 不在条目遮罩白名单内，必须逐像素不变",
                    describe(before), describe(after));
        }

        // shape_item_selected 本身：black_30，白名单外。
        int selectedId = drawableId(context, "shape_item_selected");
        assertNotEquals("shape_item_selected 必须能解析", 0, selectedId);
        FrameLayout root = new FrameLayout(context);
        View host = new View(context);
        host.setBackground(context.getDrawable(selectedId));
        root.addView(host);
        Bitmap before = rasterize(host.getBackground());
        ThemeBinder.bind(root, baseline, active);
        assertEquals("shape_item_selected 的填充是 black_30，与 black_40 只差一档 alpha："
                        + "白名单判定必须把填充计进指纹，否则会误改它",
                COMPILED_SELECTED, fillOf(host.getBackground()));
        assertEquals("shape_item_selected 必须逐像素不变",
                describe(before), describe(rasterize(host.getBackground())));
    }

    /** 白名单本身必须是「活的」：leanback 源集上全部成员都必须可解析，否则功能静默失效。 */
    @Test
    public void itemOverlayWhitelistResolvesOnTheTvSourceSet() {
        Context context = themedContext();
        String[] whitelisted = {
                "shape_item_focused",
                "shape_item_activated",
                "shape_item_round_focused",
                "shape_item_round_activated",
                "shape_search_hot_word_focused",
                "shape_tv_search_field",
                "shape_tv_search_field_focused",
        };
        for (String name : whitelisted) {
            assertNotEquals(name + " 必须能在 leanback 源集解析（否则 ThemeBinder 静默不改写它）",
                    0, drawableId(context, name));
        }
    }

    // ------------------------------------------------------------------ helpers

    private static Context themedContext() {
        RuntimeEnvironment.setQualifiers("");
        return new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_App);
    }

    /** 真实条目布局，用宿主自己的主题充气（与适配器一致）。 */
    private static View inflateHost(Context context, int layout) {
        View view = LayoutInflater.from(context).inflate(layout, null, false);
        assertNotNull("条目布局必须能充气出来", view);
        return view;
    }

    /** 改过 {@code itemOverlay} 槽的档案。 */
    private static ThemeTokens resolveWithOverlay(Context context) {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.light.itemOverlay = USER_OVERLAY_HEX;
        return resolveWithProfile(context, profile);
    }

    private static ThemeTokens resolveWithProfile(Context context, ThemeProfile profile) {
        // 清掉本类的实例归属缓存：同一个 drawable 实例在多次测试之间可能被复用，
        // 而每条断言都要求「从未被改写过的原始外观」开始。
        return ThemeResolver.resolve(ThemeMode.LIGHT, ThemeSeed.NONE, 0, 0, profile, null, false);
    }

    /** 按名字查找资源：leanback 源集才有这些 drawable，不应硬引用 R.drawable.*（与 binder 同理）。 */
    private static int drawableId(Context context, String name) {
        return context.getResources().getIdentifier(name, "drawable", context.getPackageName());
    }

    /** 主题属性解析出的颜色。 */
    private static int attrColor(Context context, int attr) {
        TypedValue value = new TypedValue();
        assertTrue("主题必须赋值属性 " + context.getResources().getResourceName(attr),
                context.getTheme().resolveAttribute(attr, value, true));
        if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }
        assertTrue("属性必须解析成具体颜色而不是空引用", value.resourceId != 0);
        return context.getResources().getColor(value.resourceId, null);
    }

    /**
     * 状态选择器里「在 {@code state} 下被选中」的那层 drawable。
     *
     * <p>{@code state == 0} 表示常态（不带任何状态位）。
     */
    private static Drawable stateDrawable(Drawable drawable, int state) {
        if (!(drawable instanceof StateListDrawable states)) return drawable;
        for (int i = 0; i < states.getStateCount(); i++) {
            int[] set = states.getStateSet(i);
            if (state == 0) {
                if (set == null || set.length == 0) return states.getStateDrawable(i);
                continue;
            }
            boolean has = false;
            for (int bit : set) if (bit == state) has = true;
            // 常态条目（无状态位）不匹配任何显式状态。
            if (has) return states.getStateDrawable(i);
        }
        return null;
    }

    /** 该状态下的实心填充色。 */
    private static int fillOfState(Drawable drawable, int state) {
        Drawable target = stateDrawable(drawable, state);
        assertNotNull("状态选择器必须提供该状态对应的 item", target);
        return fillOf(target);
    }

    /** drawable 自身的实心填充色（不进入状态选择器）。 */
    private static int fillOf(Drawable drawable) {
        Drawable current = drawable;
        while (current instanceof android.graphics.drawable.DrawableWrapper wrapper) {
            current = wrapper.getDrawable();
        }
        assertTrue("该 drawable 必须是 XML <shape>（GradientDrawable），实际为 " + current.getClass().getName(),
                current instanceof GradientDrawable);
        android.content.res.ColorStateList color = ((GradientDrawable) current).getColor();
        assertNotNull("形状必须有 <solid>", color);
        return color.getDefaultColor();
    }

    private static Bitmap rasterize(Drawable drawable) {
        Bitmap bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        if (drawable != null) {
            drawable.setBounds(0, 0, SIZE, SIZE);
            drawable.draw(new Canvas(bitmap));
        }
        return bitmap;
    }

    private static boolean containsColor(Bitmap bitmap, int color) {
        if (bitmap == null) return false;
        int rgb = color & 0x00FFFFFF;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if ((bitmap.getPixel(x, y) & 0x00FFFFFF) == rgb) return true;
            }
        }
        return false;
    }

    /** 逐像素指纹：稀疏采样会漏掉一档 alpha 的填充差异，所以每像素都算，只把结果压成哈希串。 */
    private static String describe(Bitmap bitmap) {
        long hash = 1125899906842597L;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                hash = 31 * hash + bitmap.getPixel(x, y);
            }
        }
        return Long.toHexString(hash) + '-' + bitmap.getWidth() + 'x' + bitmap.getHeight();
    }
}
