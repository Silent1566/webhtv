package com.fongmi.android.tv.ui.dialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.subtitle.model.SubtitleAsset;
import com.fongmi.android.tv.subtitle.model.SubtitleCandidate;
import com.fongmi.android.tv.subtitle.model.SubtitleContext;
import com.fongmi.android.tv.subtitle.model.SubtitleQuery;
import com.fongmi.android.tv.subtitle.provider.SubtitleProvider;
import com.github.catvod.Init;
import com.github.catvod.utils.Prefers;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = SubtitleSettingsDialogTest.TestApp.class,
        qualifiers = "zh-rCN-w1280dp-h720dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SubtitleSettingsDialogTest {

    private ActivityController<FragmentActivity> controller;
    private FragmentActivity activity;
    private AlertDialog dialog;
    private final AtomicInteger saved = new AtomicInteger();

    public static class TestApp extends App {
        @Override
        public void onCreate() {
        }
    }

    @Before
    public void setUp() {
        Init.set(RuntimeEnvironment.getApplication());
        Prefers.getPrefers().edit().clear().commit();
        controller = Robolectric.buildActivity(FragmentActivity.class);
        controller.get().setTheme(R.style.Theme_App);
        activity = controller.setup().get();
    }

    @After
    public void tearDown() {
        if (dialog != null) dialog.dismiss();
        controller.pause().stop().destroy();
    }

    @Test
    public void assrtExplainsTheFieldAndSavesARawApiKey() throws Exception {
        openSource("assrt");
        assertTrue(input().getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod);
        capture("assrt-empty.png");
        input().setText("  new-secret  ");
        confirm();

        assertEquals(1, saved.get());
        assertFalse(dialog.isShowing());
        assertEquals("new-secret", environment("assrt").get("ASSRT_TOKEN").getAsString());
        assertTrue(layout().getHint().toString().contains("API Key"));
        assertNotNull(layout().getHelperText());
        assertTrue(layout().getHelperText().toString().contains("JSON"));
    }

    @Test
    public void editingAssrtKeepsExtraFieldsAndOtherSources() throws Exception {
        Setting.putSubtitleSourceEnvironment("assrt", "{\"ASSRT_TOKEN\":\"old-secret\",\"region\":\"keep\"}");
        Setting.putSubtitleSourceEnvironment("custom", "{\"TOKEN\":\"other-secret\"}");
        openSource("assrt");
        capture("assrt-filled.png");

        assertEquals("old-secret", input().getText().toString());
        input().setText("replacement");
        confirm();

        assertEquals("replacement", environment("assrt").get("ASSRT_TOKEN").getAsString());
        assertEquals("keep", environment("assrt").get("region").getAsString());
        assertEquals("other-secret", environment("custom").get("TOKEN").getAsString());
    }

    @Test
    public void clearingAssrtRemovesOnlyTheToken() {
        Setting.putSubtitleSourceEnvironment("assrt", "{\"ASSRT_TOKEN\":\"old-secret\",\"region\":\"keep\"}");
        openSource("assrt");
        input().setText("   ");
        confirm();

        assertEquals(1, saved.get());
        assertFalse(environment("assrt").has("ASSRT_TOKEN"));
        assertEquals("keep", environment("assrt").get("region").getAsString());
    }

    @Test
    public void assrtStillAcceptsPastedLegacyJson() {
        openSource("assrt");
        input().setText("{\"ASSRT_TOKEN\":\"json-secret\",\"extra\":true}");
        confirm();

        assertEquals(1, saved.get());
        assertEquals("json-secret", environment("assrt").get("ASSRT_TOKEN").getAsString());
        assertTrue(environment("assrt").get("extra").getAsBoolean());
    }

    @Test
    public void invalidAssrtJsonDoesNotOverwriteTheSavedToken() {
        Setting.putSubtitleSourceEnvironment("assrt", "{\"ASSRT_TOKEN\":\"old-secret\"}");
        openSource("assrt");
        for (String invalid : new String[]{"{\"ASSRT_TOKEN\":7}", "{\"ASSRT_TOKEN\":false}",
                "{\"ASSRT_TOKEN\":null}", "{\"ASSRT_TOKEN\":[]}", "{\"ASSRT_TOKEN\":{}}", "[]", "{"}) {
            input().setText(invalid);
            confirm();

            assertTrue(dialog.isShowing());
            assertEquals(0, saved.get());
            assertNotNull(layout().getError());
            assertEquals("old-secret", environment("assrt").get("ASSRT_TOKEN").getAsString());
        }
    }

    @Test
    public void otherSourcesExplainJsonAndRecoverAfterInvalidInput() throws Exception {
        Setting.putSubtitleSourceEnvironment("custom", "{\"TOKEN\":\"old-secret\"}");
        openSource("custom");
        input().setText("not-json");
        confirm();
        capture("custom-error.png");

        assertTrue(dialog.isShowing());
        assertEquals(0, saved.get());
        assertEquals("old-secret", environment("custom").get("TOKEN").getAsString());
        assertNotNull(layout().getHelperText());
        assertTrue(layout().getError().toString().contains("{"));
        input().setText("{\"TOKEN\":\"replacement\"}");
        confirm();
        assertEquals(1, saved.get());
        assertEquals("replacement", environment("custom").get("TOKEN").getAsString());
    }

    @Test
    public void emptyGenericEnvironmentStillClearsTheSource() {
        Setting.putSubtitleSourceEnvironment("custom", "{\"TOKEN\":\"old-secret\"}");
        openSource("custom");
        input().setText("");
        confirm();
        assertEquals(1, saved.get());
        assertEquals("", Setting.getSubtitleSourceEnvironment("custom"));
    }

    @Test
    public void legacyAssrtPreferenceIsPrefilledAndCanBeEdited() {
        Prefers.put("subtitle_assrt_token", "legacy-secret");
        openSource("assrt");
        assertEquals("legacy-secret", input().getText().toString());
        input().setText("replacement");
        confirm();
        assertEquals("replacement", environment("assrt").get("ASSRT_TOKEN").getAsString());
    }

    @Test
    public void genericJsonRejectsNonObjectsWithoutChangingTheSavedSource() {
        Setting.putSubtitleSourceEnvironment("custom", "{\"TOKEN\":\"old-secret\"}");
        openSource("custom");
        for (String invalid : new String[]{"[]", "7", "null", "\"bare-key\""}) {
            input().setText(invalid);
            confirm();
            assertTrue(dialog.isShowing());
            assertEquals(0, saved.get());
            assertEquals("old-secret", environment("custom").get("TOKEN").getAsString());
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w375dp-h812dp-port-mdpi")
    public void phoneWidthShowsAnExampleBeforeTyping() throws Exception {
        openSource("custom");
        assertTrue(layout().getHelperText().toString().contains("\"TOKEN\""));
        capture("custom-phone.png", 375, 812);
    }

    private void openSource(String key) {
        SubtitleProvider provider = new SubtitleProvider() {
            @Override public String getName() { return key; }
            @Override public boolean isEnabled() { return true; }
            @Override public List<SubtitleCandidate> search(SubtitleQuery query, SubtitleContext context) { return Collections.emptyList(); }
            @Override public SubtitleAsset resolve(SubtitleCandidate candidate, SubtitleContext context) { return null; }
        };
        ReflectionHelpers.callStaticMethod(SubtitleSettingsDialog.class, "showSourceEnvironment",
                ReflectionHelpers.ClassParameter.from(FragmentActivity.class, activity),
                ReflectionHelpers.ClassParameter.from(SubtitleProvider.class, provider),
                ReflectionHelpers.ClassParameter.from(Runnable.class, (Runnable) saved::incrementAndGet));
        dialog = (AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
    }

    private TextInputEditText input() {
        return find(dialog.getWindow().getDecorView(), TextInputEditText.class);
    }

    private TextInputLayout layout() {
        return find(dialog.getWindow().getDecorView(), TextInputLayout.class);
    }

    private void confirm() {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    }

    private static JsonObject environment(String key) {
        return JsonParser.parseString(Setting.getSubtitleSourceEnvironment(key)).getAsJsonObject();
    }

    private static <T extends View> T find(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                T result = find(group.getChildAt(i), type);
                if (result != null) return result;
            }
        }
        return null;
    }

    private void capture(String name) throws Exception {
        capture(name, 1280, 720);
    }

    private void capture(String name, int width, int height) throws Exception {
        String directory = System.getenv("WEBHTV_SUBTITLE_CAPTURE_DIR");
        if (directory == null || directory.isEmpty()) return;
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        View root = dialog.getWindow().getDecorView();
        root.measure(View.MeasureSpec.makeMeasureSpec(Math.min(960, width - 32), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST));
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
        preDraw(root);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xff171a22);
        canvas.translate((width - root.getWidth()) / 2f, (height - root.getHeight()) / 2f);
        root.draw(canvas);
        File target = new File(directory);
        if (!target.isDirectory() && !target.mkdirs()) throw new java.io.IOException("Cannot create capture directory");
        try (FileOutputStream stream = new FileOutputStream(new File(target, name))) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new java.io.IOException("Cannot encode capture");
        }
        bitmap.recycle();
    }

    private static void preDraw(View view) {
        if (view instanceof TextView text) text.onPreDraw();
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) preDraw(group.getChildAt(i));
        }
    }
}
