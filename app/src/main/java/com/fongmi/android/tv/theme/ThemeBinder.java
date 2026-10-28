package com.fongmi.android.tv.theme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.shape.MaterialShapeDrawable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Controlled runtime channel for user theme overrides.
 *
 * <p>Android offers no public API to rewrite a compiled {@code ?attr/color*}
 * value, so the binder walks the already-created view tree and swaps only colors
 * that provably came from a semantic role: a view color is rewritten when it
 * exactly equals a frozen baseline role value and is not stateful. Everything
 * else - player surfaces, media, posters, brand and health colors, focus
 * selectors, unknown colors - is left untouched.
 *
 * <p>This runs on the main thread only, is a no-op when the active tokens equal
 * the baseline, and never touches a view more than once per token signature.
 */
public final class ThemeBinder {

    private static final String IGNORE_TAG = "webhtv:ignore";
    private static final String[] EXEMPT_CLASS_MARKERS = {
            "surface", "texture", "video", "player", "danmaku", "subtitle",
            "karaoke", "wall", "logo", "rating", "media",
    };

    /**
     * 唯一允许改写焦点态描边的「应用表面」selector 名字（见 {@link #isAppSurfaceFocusRing}）。
     *
     * <p>用资源名字而非 {@code R.drawable.*}：这些资源只存在于 leanback 源集，本类在 main 源集
     * 会被 mobile 变体一起编译，硬引用会让 mobile javac 失败。mobile 上解析为 0，白名单为空。
     *
     * <p>新增成员前必须**同时**满足三条，缺一不可：
     * <ol>
     *   <li>宿主画在调色板表面（不是视频画面或固定深色玻璃）；</li>
     *   <li>焦点态环色走 {@code ?attr/tvFocusRing}——<b>不能是 {@code ?attr/colorOnPrimary} 等
     *       「填充色的配对 on-色」</b>。后者是给「焦点态整块填充 {@code colorPrimary}」的控件用的，
     *       环与填充必须成对；把它们改写成本类写入的 {@code colorFocus()} 会让环与填充同色
     *       （默认色板里 focus == primary），焦点环直接消失。
     *       因此 {@code selector_config_history_item} / {@code selector_site_item} /
     *       {@code selector_group_button} 这类「primary 填充 + onPrimary 环」的**不在白名单**；</li>
     *   <li>焦点态宽度已是 {@code @dimen/webhtv_focus_ring_width}（本类无法读回旧宽度，只能按
     *       token 重写，宽度未收敛的族必须排除，否则会重新露出卡片圆角外的月牙）。</li>
     * </ol>
     */
    private static final String[] APP_SURFACE_FOCUS_RINGS = {
            "selector_item",
            "selector_item_round",
            "selector_vod",
            "selector_vod_oval",
            "selector_keyboard",
            "selector_search_hot_word",
            "selector_video_item",
            "selector_tv_search_field",
            "selector_empty_action",
    };

    private static final Map<View, Long> APPLIED = new WeakHashMap<>();
    /**
     * 视图的白名单判定缓存（命中与未命中都记）。只有「背景实例没换且环色 token 没变」时
     * 才复用结论——RecyclerView 会复用 item 视图，换 drawable 或换绑主题都必须重新判定。
     */
    private static final Map<View, RingDecision> RING_DECIDED = new WeakHashMap<>();
    /** {@link #ringColorToken} 解析不出环色时的哨兵值（不是合法 ARGB，与 0x00000000 区分）。 */
    private static final int RING_COLOR_UNSET = 0x01000000;

    /**
     * 一次白名单判定的输入与结论，用于判断缓存是否仍然适用。
     *
     * <p>背景实例用 {@link java.lang.ref.WeakReference} 引用：drawable 会把自己设为宿主的
     * callback，若值里强引用它，就等于「值 → 键」，{@link WeakHashMap} 的键永远不可达回收，
     * 长列表反复回收视图时会持续累积。
     */
    private record RingDecision(java.lang.ref.WeakReference<StateListDrawable> states, int ringColor, boolean member) {
    }

    /**
     * 每个 {@link Resources} 的白名单焦点环「签名 → 像素指纹」集合，避免每个视图都重复解析资源。
     * 指纹把描边也计入，用于排除"填充/圆角/内边距相同但描边不同"的资源（描边无公开 getter）。
     */
    private static final Map<Resources, Map<Integer, Map<String, java.util.Set<Long>>>> WHITELIST_FINGERPRINTS = new WeakHashMap<>();
    /** 指纹位图边长（px）：足够容纳描边差异，又足够便宜。 */
    private static final int FINGERPRINT_SIZE = 48;
    private static final Map<RecyclerView, RecyclerView.OnChildAttachStateChangeListener> WATCHED = new WeakHashMap<>();
    private static final Map<View, int[]> ROOTS = new WeakHashMap<>();
    private static final Map<ColorStateList, ColorStateList> REBUILT = new WeakHashMap<>();

    private static long cachedSignature;

    private static volatile long lastBindMillis;
    private static volatile int lastBoundViews;
    private static volatile int lastWalkedViews;

    private ThemeBinder() {
    }

    /** Elapsed time of the most recent non-trivial bind, for the performance gate. */
    public static long lastBindMillis() {
        return lastBindMillis;
    }

    /** Number of views rewritten by the most recent non-trivial bind. */
    public static int lastBoundViews() {
        return lastBoundViews;
    }

    public static void bind(View root, ThemeTokens baseline, ThemeTokens active) {
        if (root == null || baseline == null || active == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) return;
        if (baseline.equals(active)) {
            // Default profile: the static Layer 1 theme is already correct, so the
            // binder must cost nothing and change nothing.
            lastBindMillis = 0L;
            lastBoundViews = 0;
            return;
        }
        long started = SystemClock.elapsedRealtimeNanos();
        long signature = signature(active);
        if (signature != cachedSignature) {
            REBUILT.clear();
            cachedSignature = signature;
        }
        ThemeColorIndex index = ThemeColorIndex.of(baseline);
        int bound = walk(root, index, active, signature);
        watchRoot(root, index, active, signature);
        lastBoundViews = bound;
        lastBindMillis = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000L;
    }

    private static int walk(View root, ThemeColorIndex index, ThemeTokens active, long signature) {
        int bound = 0;
        int walked = 0;
        Deque<View> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            View view = queue.poll();
            if (view == null) continue;
            walked++;
            if (isExempt(view)) continue;
            if (bindView(view, index, active, signature)) bound++;
            if (view instanceof RecyclerView recyclerView) watchChildren(recyclerView, index, active, signature);
            if (view instanceof ViewGroup group) {
                for (int child = 0; child < group.getChildCount(); child++) {
                    queue.add(group.getChildAt(child));
                }
            }
        }
        lastWalkedViews = walked;
        return bound;
    }

    /**
     * Fragments and async content attach after the Activity bind points. The root
     * is re-walked only when its descendant count actually changed, which keeps the
     * scroll path free of repeated traversals.
     */
    private static void watchRoot(View root, ThemeColorIndex index, ThemeTokens active, long signature) {
        if (ROOTS.containsKey(root)) return;
        int[] state = {countDescendants(root)};
        ROOTS.put(root, state);
        root.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (state[0] == countDescendants(view)) return;
            state[0] = countDescendants(view);
            walk(view, index, active, signature);
        });
    }

    private static int countDescendants(View view) {
        if (!(view instanceof ViewGroup group)) return 1;
        int count = 1;
        for (int child = 0; child < group.getChildCount(); child++) count += countDescendants(group.getChildAt(child));
        return count;
    }

    private static void watchChildren(RecyclerView recyclerView, ThemeColorIndex index, ThemeTokens active, long signature) {
        if (WATCHED.containsKey(recyclerView)) return;
        RecyclerView.OnChildAttachStateChangeListener listener = new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(View view) {
                walk(view, index, active, signature);
            }

            @Override
            public void onChildViewDetachedFromWindow(View view) {
                APPLIED.remove(view);
            }
        };
        WATCHED.put(recyclerView, listener);
        recyclerView.addOnChildAttachStateChangeListener(listener);
    }

    private static boolean bindView(View view, ThemeColorIndex index, ThemeTokens active, long signature) {
        Long applied = APPLIED.get(view);
        if (applied != null && applied == signature) return false;
        ThemeRole explicit = ThemeRole.fromTag(view.getTag());
        boolean changed = false;
        if (view instanceof TextView textView) changed |= bindText(textView, explicit, index, active);
        if (view instanceof ImageView imageView) changed |= bindImageTint(imageView, explicit, index, active);
        if (view instanceof MaterialCardView cardView) changed |= bindCard(cardView, explicit, index, active);
        if (view instanceof MaterialButton button) changed |= bindButton(button, explicit, index, active);
        changed |= bindBackgroundTint(view, explicit, index, active);
        changed |= bindBackground(view, explicit, index, active);
        changed |= bindForeground(view, explicit, index, active);
        if (changed) APPLIED.put(view, signature);
        return changed;
    }

    private static boolean bindText(TextView view, ThemeRole explicit, ThemeColorIndex index, ThemeTokens active) {
        boolean changed = false;
        ColorStateList colors = rewrite(view.getTextColors(), explicit, index, active);
        if (colors != null) {
            view.setTextColor(colors);
            changed = true;
        }
        ColorStateList hint = rewrite(view.getHintTextColors(), explicit, index, active);
        if (hint != null) {
            view.setHintTextColor(hint);
            changed = true;
        }
        return changed;
    }

    private static boolean bindImageTint(ImageView view, ThemeRole explicit, ThemeColorIndex index, ThemeTokens active) {
        ColorStateList tint = rewrite(view.getImageTintList(), explicit, index, active);
        if (tint == null) return false;
        view.setImageTintList(tint);
        return true;
    }

    private static boolean bindCard(MaterialCardView view, ThemeRole explicit, ThemeColorIndex index, ThemeTokens active) {
        boolean changed = false;
        ColorStateList background = rewrite(view.getCardBackgroundColor(), explicit, index, active);
        if (background != null) {
            view.setCardBackgroundColor(background);
            changed = true;
        }
        ColorStateList stroke = rewrite(ColorStateList.valueOf(view.getStrokeColor()),
                index.uniqueRoleFor(view.getStrokeColor()) == null ? ThemeRole.OUTLINE : null, index, active);
        if (stroke != null) {
            view.setStrokeColor(stroke);
            changed = true;
        }
        return changed;
    }

    /**
     * Filled and outlined Material buttons keep their fill in a background tint and
     * their border in a stroke colour rather than in a GradientDrawable, so the
     * generic drawable path never reached them and a themed button stayed on the
     * compiled palette. Both channels are still resolved from the same semantic
     * attributes and obey the identical exact-match rule.
     */
    private static boolean bindButton(MaterialButton view, ThemeRole explicit, ThemeColorIndex index, ThemeTokens active) {
        boolean changed = false;
        ColorStateList stroke = rewrite(view.getStrokeColor(), explicit, index, active);
        if (stroke != null) {
            view.setStrokeColor(stroke);
            changed = true;
        }
        ColorStateList icon = rewrite(view.getIconTint(), explicit, index, active);
        if (icon != null) {
            view.setIconTint(icon);
            changed = true;
        }
        return changed;
    }

    /**
     * Covers every view that fills itself through {@code app:backgroundTint} - the
     * Material button family, chips, FABs and text-input boxes - with the same
     * "only an exact baseline role colour" guard the rest of the binder uses.
     */
    private static boolean bindBackgroundTint(View view, ThemeRole explicit, ThemeColorIndex index, ThemeTokens active) {
        ColorStateList tint = rewrite(view.getBackgroundTintList(), explicit, index, active);
        if (tint == null) return false;
        view.setBackgroundTintList(tint);
        return true;
    }

    private static boolean bindBackground(View view, ThemeRole explicit, ThemeColorIndex index, ThemeTokens active) {
        return bindDrawable(view.getBackground(), explicit, index, active, view);
    }

    /**
     * Rewrites a view's {@code android:foreground} drawable.
     *
     * <p>电视卡片把焦点环画在 foreground 而不是 background：{@code selector_vod} /
     * {@code selector_vod_oval} / {@code selector_episode_card} 等都以
     * {@code android:foreground} 覆盖在内容之上，因此只走 background 的旧路径永远碰不到
     * 卡片族的焦点环，用户改「焦点色」时它们恒为原始颜色。
     */
    private static boolean bindForeground(View view, ThemeRole explicit, ThemeColorIndex index, ThemeTokens active) {
        return bindDrawable(view.getForeground(), explicit, index, active, view);
    }

    /**
     * Rewrites a window-level background drawable.
     *
     * <p>{@code MaterialAlertDialogBuilder} builds the dialog panel as a
     * {@link MaterialShapeDrawable} wrapped in an {@link InsetDrawable} and hands it to
     * {@code Window.setBackgroundDrawable}. That drawable belongs to the window, not to
     * any {@code View}, so the ordinary view-tree walk can never reach it - which is why
     * a themed dialog changed its text but kept the compiled panel colour. The builder
     * exposes the very same instance through {@code getBackground()}, so it is rewritten
     * here through one shared code path instead of a second colour model.
     */
    public static boolean bindWindowBackground(Drawable drawable, ThemeTokens baseline, ThemeTokens active) {
        if (drawable == null || baseline == null || active == null) return false;
        if (Looper.myLooper() != Looper.getMainLooper()) return false;
        if (baseline.equals(active)) return false;
        // A window background belongs to no View, so there is no theme to resolve a ring
        // against; the 4-argument overload keeps the fill-only path this entry point has
        // always had.
        boolean changed = bindDrawable(drawable, null, ThemeColorIndex.of(baseline), active);
        // dialogOpacity is documented as "the dialog/BottomSheet shell only, never the text
        // alpha", so it is applied to this window background and nowhere else. Without this
        // the editor's dialog-opacity slider changed only the preview swatch and the Web
        // snapshot while real dialogs stayed fully opaque.
        changed |= applyShellOpacity(drawable, active.dialogOpacity());
        return changed;
    }

    /**
     * Scales the alpha of a window background fill, preserving whatever transparency the
     * drawable already had. A shell opacity of 1.0 is the shipped default and must stay a
     * strict no-op so the frozen palette path remains byte-identical.
     */
    private static boolean applyShellOpacity(Drawable drawable, float opacity) {
        if (drawable == null || opacity >= 1f) return false;
        if (opacity < 0f) return false;
        if (drawable instanceof InsetDrawable inset) {
            return applyShellOpacity(inset.getDrawable(), opacity);
        }
        if (drawable instanceof MaterialShapeDrawable shape) {
            ColorStateList fill = shape.getFillColor();
            if (fill == null) return false;
            int original = fill.getDefaultColor();
            int target = scaleAlpha(original, opacity);
            if (target == original) return false;
            shape.setFillColor(ColorStateList.valueOf(target));
            return true;
        }
        if (drawable instanceof GradientDrawable shape) {
            ColorStateList color = shape.getColor();
            if (color == null) return false;
            int original = color.getDefaultColor();
            int target = scaleAlpha(original, opacity);
            if (target == original) return false;
            shape.setColor(target);
            return true;
        }
        return false;
    }

    /** Package-private so the alpha arithmetic can be asserted without an Android device. */
    static int scaleAlpha(int color, float opacity) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, opacity)));
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    /**
     * Rewrites the solid fill of a background drawable, unwrapping the transparent
     * wrappers Android and Material put around real panels.
     *
     * <p>Two families are covered by the same exact-match rule as everything else:
     * {@link GradientDrawable} (classic XML shapes) and
     * {@link MaterialShapeDrawable} (Chip, text-input box, bottom sheet and the
     * AlertDialog panel). Material's alert background is additionally wrapped in an
     * {@link InsetDrawable} carrying the window insets, so wrappers are unwrapped
     * recursively instead of being treated as an unknown drawable.
     */
    private static boolean bindDrawable(Drawable drawable, ThemeRole explicit, ThemeColorIndex index, ThemeTokens active) {
        return bindDrawable(drawable, explicit, index, active, null);
    }

    /**
     * Same pass, with the owning View available so a focus-ring selector can be resolved
     * against that view's own theme.
     */
    private static boolean bindDrawable(Drawable drawable, ThemeRole explicit, ThemeColorIndex index, ThemeTokens active, View owner) {
        if (drawable == null) return false;
        if (drawable instanceof InsetDrawable inset) {
            return bindDrawable(inset.getDrawable(), explicit, index, active, owner);
        }
        if (drawable instanceof DrawableWrapper wrapper) {
            return bindDrawable(wrapper.getDrawable(), explicit, index, active, owner);
        }
        if (owner != null && drawable instanceof StateListDrawable states) {
            return bindFocusRingStates(owner, states, explicit, index, active);
        }
        if (drawable instanceof GradientDrawable shape) {
            ColorStateList color = rewrite(shape.getColor(), explicit, index, active);
            if (color == null) return false;
            shape.setColor(color);
            return true;
        }
        if (drawable instanceof MaterialShapeDrawable shape) {
            ColorStateList color = rewrite(shape.getFillColor(), explicit, index, active);
            if (color == null) return false;
            shape.setFillColor(color);
            return true;
        }
        return false;
    }

    /**
     * The only focus-ring selectors this binder may rewrite: the "app surface" family.
     *
     * <p>白名单是**资源名字**而不是 {@code R.drawable.*}：这些 selector 只存在于
     * {@code app/src/leanback/res}，而本类位于 {@code app/src/main}（两个 flavor 共享），
     * 直接引用 {@code R.drawable.selector_item} 会让 mobile 变体的 javac 直接失败
     * （与 {@code DanmakuDialog} 的 {@code @id/loadHost} 泄漏同属一类）。因此按名字解析：
     * mobile 上解析为 0 → 白名单为空 → 一律不改写，行为与该 flavor 现状一致。
     *
     * <p>为什么必须白名单而不是「按视图主题判定」：{@code webhtv_styles.xml} 把所有相关主题的
     * {@code tvFocusRing} 都绑到 {@code tv_item_focus_ring}，于是「视图主题解析出应用表面环色」
     * 这个判据对几乎所有宿主恒为真，会把下面这些**刻意不同**的族一并改写：
     * <ul>
     *   <li>使用 {@code ?attr/tvPlayerRing} 的固定深色玻璃族（{@code selector_glass_icon_action}、
     *       {@code selector_control_sheet_button}、{@code shape_subtitle_focused/pressed}）——
     *       浅色表 FOCUS 深蓝 {@code #0B57D0} 画在深靛玻璃上只有 1.83:1，改了等于不可见；</li>
     *   <li>焦点态宽度尚未收敛到 token 的 3dp 卡片族（{@code selector_episode_card} 等）——
     *       它们的声明圆角是按「宿主圆角 − 环宽/2」以 3dp 推导并经栅格化验算的，改宽度会重新
     *       露出「卡片圆角外的月牙」；</li>
     *   <li>本来就没有描边的焦点态（如实心填充 + layer-list 的图标按钮）——改了就凭空多出一圈环。</li>
     * </ul>
     *
     * <p>白名单内每个 selector 的焦点态宽度都已经是 {@code @dimen/webhtv_focus_ring_width}
     * （由 {@code TvFocusRingContractTest} 钉住），因此读不到旧宽度也没有关系：重新写入同一
     * token 就是无变化，唯一真正变化的是环色（跟随用户 FOCUS 槽），这正是用户要的效果。
     *
     * @return true when at least one state item was rewritten
     */
    private static boolean bindFocusRingStates(View view, StateListDrawable states, ThemeRole explicit, ThemeColorIndex index, ThemeTokens active) {
        int color = active.colorFocus();
        if (color == 0) return false;
        if (explicit != ThemeRole.FOCUS && !isAppSurfaceFocusRing(view, states)) return false;
        boolean changed = false;
        // 容器先 mutate：StateListDrawable 的子 drawable 可能与其他视图共享 ConstantState，
        // 不先切断会让首个宿主的改写连带影响其它宿主。
        states.mutate();
        for (int i = 0; i < states.getStateCount(); i++) {
            int[] stateSet = states.getStateSet(i);
            boolean focusedState = false;
            for (int state : stateSet) if (state == android.R.attr.state_focused) focusedState = true;
            if (!focusedState) continue;
            if (setRingStroke(states.getStateDrawable(i), view, color)) changed = true;
        }
        return changed;
    }

    /**
     * Whether {@code states} is one of the whitelisted app-surface focus-ring selectors.
     *
     * <p>判定走「焦点环公开签名」比对（见 {@link #ringSignature}）。注意必须在
     * {@code mutate()} **之前**调用：{@code mutate()} 会就地改写子 drawable 的填充/描边，
     * 签名随即变化，之后再比就永远不匹配了。
     *
     * <p>白名单之外还有一道**主题前置判定**：视图自身主题解析出的 {@code ?attr/tvFocusRing}
     * 必须就是 {@code R.color.tv_item_focus_ring}。原因是白名单里的
     * {@code selector_video_item} 是**双宿主**资源——选集芯片在普通页面用它，而
     * {@code dialog_episode_list} / {@code dialog_quick_search} 两处固定深色玻璃对话框的根节点
     * 用 {@code @style/ThemeOverlay.WebHTV.GlassFocusRings} 把 {@code tvFocusRing} 重绑到了
     * {@code tv_player_focus_ring}。玻璃子树里只看内容签名会命中并改写成 {@code colorFocus()}
     * （浅色表深蓝 {@code #0B57D0} 贴在深靛玻璃上仅 1.83:1，等于不可见）。
     *
     * <p>这条判据单独使用会**恒真**（所有相关主题都把 {@code tvFocusRing} 绑到该资源），
     * 所以只能作为白名单的必要条件（AND），用来把玻璃子树挡在外面。
     */
    private static boolean isAppSurfaceFocusRing(View view, StateListDrawable states) {
        if (view == null || states == null) return false;
        // 缓存只对「同一个背景实例 + 同一个传入主题」有效：RecyclerView 会复用 item 视图，
        // 描边改写或换绑主题都会让先前的结论失效。
        Context context = view.getContext();
        if (context == null) return false;
        int ringColor = ringColorToken(context);
        if (ringColor == RING_COLOR_UNSET) return false;
        RingDecision cached = RING_DECIDED.get(view);
        if (cached != null && cached.ringColor() == ringColor && cached.states().get() == states) {
            return cached.member();
        }
        // 先做主题前置判定：玻璃子树会把 tvFocusRing 重绑到视频层环色（浅色表 #447BF5），
        // 那里必须保持原样，否则深蓝环贴固定深色玻璃不可见（实测 1.83:1）。
        boolean member = ringColor == context.getResources().getColor(R.color.tv_item_focus_ring, null)
                && matchesWhitelist(context, states);
        RING_DECIDED.put(view, new RingDecision(new java.lang.ref.WeakReference<>(states), ringColor, member));
        return member;
    }

    /** 视图主题解析出的 {@code ?attr/tvFocusRing} 颜色；解析不出时返回 {@link #RING_COLOR_UNSET}。 */
    private static int ringColorToken(Context context) {
        android.util.TypedValue value = new android.util.TypedValue();
        try {
            if (!context.getTheme().resolveAttribute(R.attr.tvFocusRing, value, true)) return RING_COLOR_UNSET;
            if (value.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT
                    && value.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) return value.data;
            if (value.resourceId != 0) return context.getResources().getColor(value.resourceId, null);
        } catch (RuntimeException ignored) {
            return RING_COLOR_UNSET;
        }
        return RING_COLOR_UNSET;
    }

    /**
     * 焦点环的公开可读签名：填充色 + 圆角 + 内边距 + 是否椭圆。
     *
     * <p>为什么用内容签名而不是 {@code ConstantState} 身份比较：身份比较依赖
     * {@code Resources} 对同一 (id, 主题) 复用同一个 ConstantState，这在真机上成立，但在
     * Robolectric 下不成立（每次 {@code getDrawable} 都可能新建 ConstantState），于是守卫测试
     * 无法验证正向路径。签名只用 {@link GradientDrawable} 的公开 getter。
     *
     * <p><b>必须包含填充色</b>：{@code shape_search_hot_word_focused} 与
     * {@code selector_glass_icon_action} / {@code shape_subtitle_*} 同为 8dp 圆角、内边距相近，
     * 只有实心色不同（{@code black_40} vs {@code white_15}）；漏掉颜色就会把固定深色玻璃族
     * 误判成应用表面族并改写成 colorFocus（浅色表在深靛玻璃上 1.83:1，等于不可见）。
     */
    private static String ringSignature(Drawable drawable) {
        if (drawable instanceof DrawableWrapper wrapper) return ringSignature(wrapper.getDrawable());
        if (!(drawable instanceof GradientDrawable shape)) return null;
        ColorStateList fill = shape.getColor();
        Rect padding = new Rect();
        shape.getPadding(padding);
        float radius = shape.getCornerRadius();
        boolean oval = shape.getShape() == GradientDrawable.OVAL;
        return (fill == null ? "null" : Integer.toHexString(fill.getDefaultColor()))
                + '|' + (oval ? "oval" : Float.toString(radius))
                + '|' + padding.left + ',' + padding.top + ',' + padding.right + ',' + padding.bottom;
    }

    /** 焦点态子项的签名；找不到焦点态时返回 null（这类 selector 本就不该被改写）。 */
    private static String focusedRingSignature(StateListDrawable states) {
        for (int i = 0; i < states.getStateCount(); i++) {
            for (int state : states.getStateSet(i)) {
                if (state != android.R.attr.state_focused) continue;
                String signature = ringSignature(states.getStateDrawable(i));
                if (signature != null) return signature;
            }
        }
        return null;
    }

    private static boolean matchesWhitelist(Context context, StateListDrawable states) {
        String probe = focusedRingSignature(states);
        if (probe == null) return false;
        // 两级判定：先用廉价签名做预筛（绝大多数视图在这里就返回 false，不做任何绘制），
        // 只有签名命中时才做一次小位图指纹确认。
        //
        // 为什么还需要第二级：描边宽度/颜色没有公开 getter，而
        // {@code selector_lab_icon_btn} 的焦点态与 {@code shape_search_hot_word_focused}
        // 填充/圆角/内边距完全相同（black_40 + 8dp + 无内边距），只有描边不同
        //（2dp {@code @color/accent} vs token {@code ?attr/tvFocusRing}）。位图指纹把描边也
        // 算进去，因此能把这类"签名相同但描边不同"的资源排除掉。
        java.util.Set<Long> expected = whitelistFingerprints(context, ringColorToken(context)).get(probe);
        if (expected == null) return false;
        Long actual = ringFingerprint(states);
        return actual != null && expected.contains(actual);
    }

    /** 焦点态栅格化后的像素指纹；拿不到可复制实例时返回 null（此时按不匹配处理）。 */
    private static Long ringFingerprint(StateListDrawable states) {
        Drawable focused = null;
        for (int i = 0; i < states.getStateCount(); i++) {
            for (int state : states.getStateSet(i)) {
                if (state == android.R.attr.state_focused) focused = states.getStateDrawable(i);
            }
            if (focused != null) break;
        }
        if (focused == null) return null;
        // 复制一份再绘制：直接给共享实例 setBounds 会污染其它宿主。
        Drawable.ConstantState state = focused.getConstantState();
        if (state == null) return null;
        Drawable copy = state.newDrawable();
        if (copy == null) return null;
        return fingerprint(copy);
    }

    private static Long fingerprint(Drawable drawable) {
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                FINGERPRINT_SIZE, FINGERPRINT_SIZE, android.graphics.Bitmap.Config.ARGB_8888);
        try {
            drawable.setBounds(0, 0, FINGERPRINT_SIZE, FINGERPRINT_SIZE);
            drawable.draw(new android.graphics.Canvas(bitmap));
            long hash = 1125899906842597L;
            for (int y = 0; y < FINGERPRINT_SIZE; y++) {
                for (int x = 0; x < FINGERPRINT_SIZE; x++) {
                    hash = 31 * hash + bitmap.getPixel(x, y);
                }
            }
            return hash;
        } catch (RuntimeException ignored) {
            return null;
        } finally {
            bitmap.recycle();
        }
    }

    /**
     * 白名单每个签名对应的像素指纹集合，按 {@code (Resources, 环色 token)} 解析一次后缓存。
     *
     * <p>缓存键**必须**带上环色：{@code ContextThemeWrapper.getResources()} 返回的是同一个
     * {@link Resources} 实例，但白名单资源的描边色取决于解析主题（应用表面 {@code #0B57D0}
     * vs 玻璃子树 {@code #447BF5}）。只按 {@code Resources} 做键会让结果与首次调用顺序绑定：
     * 先到者写入，后到者永远拿不到自己的指纹，功能静默失效。
     */
    private static Map<String, java.util.Set<Long>> whitelistFingerprints(Context context, int ringColor) {
        Resources resources = context.getResources();
        Map<Integer, Map<String, java.util.Set<Long>>> byRing = WHITELIST_FINGERPRINTS.get(resources);
        if (byRing == null) {
            byRing = new java.util.HashMap<>();
            WHITELIST_FINGERPRINTS.put(resources, byRing);
        }
        Map<String, java.util.Set<Long>> cached = byRing.get(ringColor);
        if (cached != null) return cached;
        Map<String, java.util.Set<Long>> resolved = new java.util.HashMap<>();
        for (String name : APP_SURFACE_FOCUS_RINGS) {
            int id = resources.getIdentifier(name, "drawable", context.getPackageName());
            if (id == 0) continue;
            Drawable candidate;
            try {
                candidate = context.getDrawable(id);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (!(candidate instanceof StateListDrawable)) continue;
            // 用原始实例取签名与指纹（指纹内部自己复制，不污染原实例）。
            String signature = focusedRingSignature((StateListDrawable) candidate);
            Long print = ringFingerprint((StateListDrawable) candidate);
            if (signature == null || print == null) continue;
            resolved.computeIfAbsent(signature, key -> new java.util.HashSet<>()).add(print);
        }
        byRing.put(ringColor, resolved);
        return resolved;
    }

    /**
     * Applies the themed focus ring colour to one state drawable.
     *
     * <p>{@code GradientDrawable} 没有公开的 stroke 宽度/颜色 getter（已对 android-36 的
     * {@code android.jar} 用 javap 核实：只有 {@code setStroke} 系列，没有 {@code getStroke*}），
     * 而仓库契约禁止在 binder 里用反射或隐藏 API，所以宽度只能按唯一 token 重新写入。
     *
     * <p><b>因此调用方必须先把资源收窄到{@link #APP_SURFACE_FOCUS_RINGS}白名单</b>：只有那些
     * selector 的焦点态宽度本就等于该 token，重写宽度才真是无变化。白名单之外若也调用本方法，
     * 就会把 3dp 卡片环等未收敛族强制改成 1.5dp（会重新露出卡片圆角外的月牙）。
     */
    private static boolean setRingStroke(Drawable drawable, View view, int color) {
        if (drawable instanceof DrawableWrapper wrapper) return setRingStroke(wrapper.getDrawable(), view, color);
        if (drawable instanceof GradientDrawable shape) {
            shape.mutate();
            shape.setStroke(focusRingWidthPx(view), color);
            return true;
        }
        return false;
    }

    /** The single TV ring width token, in pixels, resolved through the view's own resources. */
    private static int focusRingWidthPx(View view) {
        return Math.max(1, Math.round(view.getResources().getDimension(R.dimen.webhtv_focus_ring_width)));
    }

    /**
     * Returns a recolored copy of the list, preserving its state behaviour, or null
     * when the colour is unknown/unchanged or the state shape cannot be rebuilt.
     */
    private static ColorStateList rewrite(ColorStateList source, ThemeRole explicit, ThemeColorIndex index, ThemeTokens active) {
        if (source == null) return null;
        if (!source.isStateful()) {
            Integer replacement = singleColor(source.getDefaultColor(), explicit, index, active);
            return replacement == null ? null : ColorStateList.valueOf(replacement);
        }
        ColorStateList cached = REBUILT.get(source);
        if (cached != null) return cached;
        int[][] states = ThemeColorIndex.stateSkeleton();
        int[] colors = new int[states.length];
        boolean changed = false;
        // Material state lists usually vary alpha only. Resolve the role from the
        // default colour first, then keep each state's original alpha so pressed /
        // disabled / focused states stay distinguishable.
        int defaultColor = source.getDefaultColor();
        Integer defaultReplacement = singleColor(defaultColor, explicit, index, active);
        for (int position = 0; position < states.length; position++) {
            int original = resolvedColor(source, states[position]);
            Integer replacement = singleColor(original, explicit, index, active);
            if (replacement == null && defaultReplacement != null && sameRgb(original, defaultColor)) {
                replacement = withAlpha(defaultReplacement, original >>> 24);
            }
            colors[position] = replacement == null ? original : replacement;
            changed |= replacement != null;
        }
        if (!changed) return null;
        // ColorStateList exposes a public (int[][], int[]) constructor, so the list
        // can be rebuilt directly without XML inflation or hidden platform APIs.
        ColorStateList rebuilt = new ColorStateList(states, colors);
        REBUILT.put(source, rebuilt);
        return rebuilt;
    }

    private static boolean sameRgb(int first, int second) {
        return (first & 0x00FFFFFF) == (second & 0x00FFFFFF);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static int resolvedColor(ColorStateList source, int[] state) {
        int color = source.getColorForState(state, source.getDefaultColor());
        return color == 0 ? source.getDefaultColor() : color;
    }

    private static Integer singleColor(int current, ThemeRole explicit, ThemeColorIndex index, ThemeTokens active) {
        if (explicit != null) {
            Integer baseline = index.baselineOf(explicit);
            int target = explicit.colorOf(active);
            if (target == 0 || target == current) return null;
            if (baseline != null && baseline != current) return null;
            return target;
        }
        return index.replacementFor(current, active);
    }

    private static boolean isExempt(View view) {
        Object tag = view.getTag();
        if (tag instanceof String value && IGNORE_TAG.equals(value.trim().toLowerCase(Locale.ROOT))) return true;
        String name = view.getClass().getName().toLowerCase(Locale.ROOT);
        for (String marker : EXEMPT_CLASS_MARKERS) {
            if (name.contains(marker)) return true;
        }
        return false;
    }

    private static long signature(ThemeTokens tokens) {
        // ThemeTokens is a content-based record; its generated hashCode is stable
        // across the desugared Android runtime. Java-16 record reflection is
        // deliberately avoided because it does not exist on Android API < 33.
        return tokens.hashCode();
    }
}
