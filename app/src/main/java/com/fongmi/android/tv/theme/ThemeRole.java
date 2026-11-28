package com.fongmi.android.tv.theme;

import java.util.Locale;

/**
 * The bounded set of semantic roles the controlled binder may rewrite.
 *
 * <p>Only the 13 user-editable color slots and the six foreground roles the
 * resolver derives from them are listed. Player, media, logo, rating, health and
 * brand colors are intentionally absent so a profile can never reach them.
 *
 * <p>{@link #ITEM_OVERLAY} / {@link #ON_ITEM_OVERLAY} 是条目遮罩族的两个角色：它们有独立的
 * 用户槽位，但不参与基线色改写（理由见 {@link #isUserSlot()}）。
 */
public enum ThemeRole {
    PRIMARY,
    PRIMARY_CONTAINER,
    SECONDARY_CONTAINER,
    FOCUS,
    SURFACE,
    SURFACE_CONTAINER,
    SURFACE_CONTAINER_HIGH,
    ON_SURFACE,
    ON_SURFACE_VARIANT,
    OUTLINE,
    ERROR,
    SUCCESS,
    WARNING,
    ON_PRIMARY,
    ON_PRIMARY_CONTAINER,
    ON_SECONDARY_CONTAINER,
    ON_ERROR,
    ON_SUCCESS,
    ON_WARNING,
    /**
     * 条目遮罩底色（默认 {@code #66000000} = {@code @color/black_40}）。
     */
    ITEM_OVERLAY,
    /** 遮罩上的选中/聚焦文字色（默认等于 onPrimary）。 */
    ON_ITEM_OVERLAY;

    public static final String TAG_PREFIX = "webhtv:";

    /** Resolves an explicit {@code android:tag="webhtv:primary"} marker. */
    public static ThemeRole fromTag(Object tag) {
        if (!(tag instanceof String value)) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith(TAG_PREFIX)) return null;
        String name = normalized.substring(TAG_PREFIX.length()).trim();
        if (name.isEmpty()) return null;
        for (ThemeRole role : values()) {
            if (role.name().toLowerCase(Locale.ROOT).equals(name)) return role;
        }
        return null;
    }

    /** The active value of this role, or 0 when the role is not bound. */
    public int colorOf(ThemeTokens tokens) {
        if (tokens == null) return 0;
        return switch (this) {
            case PRIMARY -> tokens.colorPrimary();
            case PRIMARY_CONTAINER -> tokens.colorPrimaryContainer();
            case SECONDARY_CONTAINER -> tokens.colorSecondaryContainer();
            case FOCUS -> tokens.colorFocus();
            case SURFACE -> tokens.colorSurface();
            case SURFACE_CONTAINER -> tokens.colorSurfaceContainer();
            case SURFACE_CONTAINER_HIGH -> tokens.colorSurfaceContainerHigh();
            case ON_SURFACE -> tokens.colorOnSurface();
            case ON_SURFACE_VARIANT -> tokens.colorOnSurfaceVariant();
            case OUTLINE -> tokens.colorOutline();
            case ERROR -> tokens.colorError();
            case SUCCESS -> tokens.colorSuccess();
            case WARNING -> tokens.colorWarning();
            case ON_PRIMARY -> tokens.colorOnPrimary();
            case ON_PRIMARY_CONTAINER -> tokens.colorOnPrimaryContainer();
            case ON_SECONDARY_CONTAINER -> tokens.colorOnSecondaryContainer();
            case ON_ERROR -> tokens.colorOnError();
            case ON_SUCCESS -> tokens.colorOnSuccess();
            case ON_WARNING -> tokens.colorOnWarning();
            case ITEM_OVERLAY -> tokens.colorItemOverlay();
            case ON_ITEM_OVERLAY -> tokens.colorOnItemOverlay();
        };
    }

    /**
     * 绑定器可以按基线色改写、且用户档案可以直接覆盖的 13 个颜色槽。
     *
     * <p>条目遮罩族的两个角色同样是用户可编辑槽，却刻意不计入这里：它们的默认值（遮罩
     * {@code #66000000} = {@code @color/black_40}、文字色 = onPrimary 的白/深蓝）在仓库里
     * 被大量**固定**资源共用——键盘键底、搜索框、输入框、玻璃面板都填 {@code black_40}。
     * 一旦入表，用户改一次遮罩色就会把那些刻意固定的深玻璃一并改掉，而这正是 ThemeBinder
     * 白名单机制要阻止的事。遮罩目前只走 token / 预览链路，因此这里保持原有的 13 个槽。
     */
    public boolean isUserSlot() {
        return ordinal() <= WARNING.ordinal();
    }

    /**
     * 是否参与「按基线色反查角色」的颜色改写。
     *
     * <p>不参与的家族（理由各自见下）：
     * <ul>
     *   <li>条目遮罩族：默认值在仓库里被大量**固定**资源共用，入表会让一次遮罩编辑连带改掉
     *       刻意固定的深玻璃族。</li>
     *   <li>FOCUS（2026-11-25）：产品把默认焦点色定为纯白（对齐上游 FongMi/TV），而
     *       {@code webhtv_on_wallpaper} 两张表恒为 {@code #FFFFFF}。若 FOCUS 入表，壁纸白字
     *       会被「按基线色反查」当成基线焦点色、在用户改焦点色时连带改写。焦点色的运行时
     *       改写本就全部走专用通道——{@code ThemeBinder.bindStateList} 的环白名单与
     *       {@code ThemeController.focusRingColor()} 的代码路径——不依赖通用索引。</li>
     * </ul>
     */
    public boolean rewritesBaseline() {
        return this != ITEM_OVERLAY && this != ON_ITEM_OVERLAY && this != FOCUS;
    }
}
