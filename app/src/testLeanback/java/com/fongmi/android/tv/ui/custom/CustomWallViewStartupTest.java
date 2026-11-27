package com.fongmi.android.tv.ui.custom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.activity.ComponentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.FileUtil;
import com.github.catvod.Init;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowBitmapFactory;
import org.robolectric.shadows.ShadowLooper;

import java.nio.file.Files;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, shadows = CustomWallViewStartupTest.CountingBitmapFactory.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
public class CustomWallViewStartupTest {

    @Before
    public void setUp() throws Exception {
        Init.set(RuntimeEnvironment.getApplication());
        Files.write(FileUtil.getWallCache().toPath(), new byte[]{1});
        CountingBitmapFactory.boundsReads = 0;
        CountingBitmapFactory.pixelReads = 0;
        CountingBitmapFactory.corrupt = false;
        Setting.putWallType(WallMotionPolicy.TYPE_RES);
        Setting.putThemeColor(-1);
    }

    @Test
    public void builtinWallpaperNeverReadsExternalCache() {
        Setting.putWall(Setting.WALL_DREAM_PURPLE);
        startWallpaper();

        assertEquals(0, CountingBitmapFactory.boundsReads);
        assertEquals(0, CountingBitmapFactory.pixelReads);
    }

    @Test
    public void staticWallpaperDecodesPixelsOnceForDisplayAndTheme() {
        Setting.putWall(0);
        Drawable wallpaper = startWallpaper();

        assertEquals("check the cache dimensions once", 1, CountingBitmapFactory.boundsReads);
        assertEquals("share one pixel decode between display and theme", 1, CountingBitmapFactory.pixelReads);
        assertTrue(wallpaper instanceof BitmapDrawable);
        assertNotEquals(defaultColor(), Setting.getWallColor());
    }

    @Test
    public void corruptWallpaperFallsBackWithoutRetryingOrResettingSelection() {
        Setting.putWall(0);
        CountingBitmapFactory.corrupt = true;
        Drawable wallpaper = startWallpaper();

        assertTrue(wallpaper instanceof ColorDrawable);
        assertEquals(defaultColor(), ((ColorDrawable) wallpaper).getColor());
        assertEquals(1, CountingBitmapFactory.boundsReads);
        assertEquals(0, CountingBitmapFactory.pixelReads);
        assertEquals(0, Setting.getWall());
        assertEquals(WallMotionPolicy.TYPE_RES, Setting.getWallType());
    }

    private Drawable startWallpaper() {
        try (ActivityController<ComponentActivity> activity = Robolectric.buildActivity(ComponentActivity.class).setup()) {
            CustomWallView wallpaper = new CustomWallView(activity.get(), null);
            activity.get().setContentView(wallpaper);
            ShadowLooper.idleMainLooper();
            return ((ImageView) wallpaper.findViewById(R.id.image)).getDrawable();
        }
    }

    private static int defaultColor() {
        return Setting.getBuiltInWallColor(Setting.WALL_DREAM_PURPLE);
    }

    @Implements(BitmapFactory.class)
    public static class CountingBitmapFactory extends ShadowBitmapFactory {
        private static int boundsReads;
        private static int pixelReads;
        private static boolean corrupt;

        @Implementation
        protected static Bitmap decodeFile(String path, BitmapFactory.Options options) {
            if (!FileUtil.getWallCache().getAbsolutePath().equals(path)) {
                return ShadowBitmapFactory.decodeFile(path, options);
            }
            if (options.inJustDecodeBounds) {
                boundsReads++;
                options.outWidth = options.outHeight = corrupt ? -1 : 32;
                return null;
            }
            pixelReads++;
            if (corrupt) return null;
            Bitmap bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(0xff346bc4);
            return bitmap;
        }
    }
}
