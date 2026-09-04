package com.fongmi.android.tv.ui.helper;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TouchOptimizationHelperSourceTest {

    @Test
    public void storesOriginalStateOnceAndRestoresRecyclerChildren() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/ui/helper/TouchOptimizationHelper.java");

        assertTrue(source.contains("WeakHashMap"));
        assertTrue(source.contains("containsKey"));
        assertTrue(source.contains("sync(view)"));
        assertFalse(source.contains("View.generateViewId"));
    }

    @Test
    public void inputViewsAreSkippedAsSubtrees() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/ui/helper/TouchOptimizationHelper.java");

        assertTrue(source.contains("if (isInputView(view)) return;"));
        assertTrue(source.contains("view.onCheckIsTextEditor()"));
    }

    @Test
    public void mobileFlavorDoesNotTraverseViews() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/ui/helper/TouchOptimizationHelper.java");

        assertTrue(source.contains("if (!Util.isLeanback()) return;"));
    }

    @Test
    public void touchOptimizationToggleCanEnableModeWithOneTouch() throws Exception {
        Path layout = path("app/src/leanback/res/layout/activity_setting.xml");
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(layout.toFile());
        var nodes = document.getElementsByTagName("androidx.appcompat.widget.LinearLayoutCompat");

        for (int i = 0; i < nodes.getLength(); i++) {
            var attributes = nodes.item(i).getAttributes();
            var id = attributes.getNamedItem("android:id");
            if (id == null || !"@+id/touchOptimization".equals(id.getNodeValue())) continue;
            assertTrue("true".equals(attributes.getNamedItem("android:focusable").getNodeValue()));
            assertTrue("false".equals(attributes.getNamedItem("android:focusableInTouchMode").getNodeValue()));
            return;
        }
        throw new AssertionError("touchOptimization row not found");
    }

    @Test
    public void activityAppliesOptimizationToNewContentAndRegistersFragmentsEarly() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/base/BaseActivity.java");
        int register = source.indexOf("registerFragmentLifecycleCallbacks();");
        int content = source.indexOf("setContentView(getBinding().getRoot());");
        int method = source.indexOf("public void setContentView(View view)");
        int methodEnd = source.indexOf("protected FragmentActivity getActivity()", method);
        int wall = source.indexOf("addCustomWall();", method);
        int sync = source.indexOf("TouchOptimizationHelper.sync(getWindow().getDecorView());", method);

        assertTrue(register >= 0 && content >= 0 && register < content);
        assertTrue(method >= 0 && methodEnd > method);
        assertTrue(method < wall && wall < sync && sync < methodEnd);
    }

    @Test
    public void recyclerListenersAreAttachedOnlyWhenOptimizationIsEnabled() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/ui/helper/TouchOptimizationHelper.java");

        assertTrue(source.contains("if (optimize && view instanceof RecyclerView recycler) attachListener(recycler);"));
    }

    @Test
    public void highFrequencyDirectDialogsAndPlaybackSheetsAreSynchronized() throws Exception {
        String helper = read("app/src/main/java/com/fongmi/android/tv/ui/helper/TouchOptimizationHelper.java");
        String lightDialog = read("app/src/main/java/com/fongmi/android/tv/ui/dialog/LightDialog.java");
        String siteDialog = read("app/src/leanback/java/com/fongmi/android/tv/ui/dialog/SiteDialog.java");
        String videoActivity = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");

        assertTrue(helper.contains("public static void sync(Dialog dialog)"));
        assertTrue(lightDialog.contains("TouchOptimizationHelper.sync(dialog);"));
        assertTrue(siteDialog.contains("TouchOptimizationHelper.sync(directDialog);"));
        assertTrue(videoActivity.contains("private void syncAudioDialog(Dialog dialog)"));
        assertTrue(videoActivity.contains("TouchOptimizationHelper.sync(dialog);"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(path(path), StandardCharsets.UTF_8);
    }

    private static Path path(String value) {
        Path direct = Path.of(value);
        return Files.exists(direct) ? direct : Path.of(value.substring("app/".length()));
    }
}
