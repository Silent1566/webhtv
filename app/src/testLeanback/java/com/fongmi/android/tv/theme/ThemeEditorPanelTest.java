package com.fongmi.android.tv.theme;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = App.class)
public class ThemeEditorPanelTest {

    @Test
    public void rebindingPreservesTheSliderAndFocusWhileRemoteKeysKeepEditing() {
        Context context = new ContextThemeWrapper(App.get(), R.style.Theme_App);
        ThemeEditor editor = new ThemeEditor(ThemeProfile.defaultProfile());
        ThemePreviewView[] panel = new ThemePreviewView[1];
        int[] changes = {0};
        panel[0] = ThemePreviewView.createPanel(context, editor, true, editor.preview(true, 0), new ThemePreviewView.Callbacks() {
            @Override public void onColorSlotClicked(ThemeEditor.Slot slot, boolean dark) { }
            @Override public void onDraftChanged() {
                changes[0]++;
                panel[0].render(editor, true, editor.preview(true, 0));
            }
        });
        layout(panel[0]);
        SeekBar bar = panel[0].findViewWithTag("theme-opacity:SCRIM_OPACITY");
        assertNotNull(bar);
        assertTrue("automatic scrim uses the resolved value, not the minimum", bar.getProgress() > 0);
        SeekBar playerScrim = panel[0].findViewWithTag("theme-opacity:PLAYER_SCRIM_OPACITY");
        assertNotNull("the playback menu overlay slider must exist", playerScrim);
        assertEquals("auto strength is 80% of the 20..100 range", 75, playerScrim.getProgress());
        SeekBar liveListScrim = panel[0].findViewWithTag("theme-opacity:LIVE_LIST_SCRIM_OPACITY");
        assertNotNull("the live channel drawer scrim slider must exist", liveListScrim);
        assertEquals("live list overlay defaults to the shipped 80% of the 20..100 range", 75, liveListScrim.getProgress());
        assertTrue(bar.requestFocus());
        int before = bar.getProgress();
        bar.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
        bar.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT));
        assertTrue(bar.getProgress() > before);
        assertEquals(1, changes[0]);
        assertTrue(editor.isDirty());
        assertSame(bar, panel[0].findViewWithTag("theme-opacity:SCRIM_OPACITY"));
        assertSame(bar, panel[0].findFocus());
        bar.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
        assertEquals(2, changes[0]);
    }

    @Test
    public void everyRowShowsResolvedColorsAndSwitchingModesDoesNotPublishTheDraft() {
        Context context = new ContextThemeWrapper(App.get(), R.style.Theme_App);
        String persisted = ThemeProfileCodec.encode(ThemeProfileStore.load());
        ThemeTokens global = ThemeController.current();
        ThemeEditor editor = new ThemeEditor(ThemeProfile.defaultProfile());
        ThemeEditor.Slot[] clicked = {null};
        ThemePreviewView panel = ThemePreviewView.createPanel(context, editor, false, editor.preview(false, 0), new ThemePreviewView.Callbacks() {
            @Override public void onColorSlotClicked(ThemeEditor.Slot slot, boolean dark) { clicked[0] = slot; }
            @Override public void onDraftChanged() { }
        });
        View success = panel.findViewWithTag("theme-slot:SUCCESS");
        success.performClick();
        assertEquals(ThemeEditor.Slot.SUCCESS, clicked[0]);
        List<String> texts = texts(panel);
        assertTrue(texts.stream().anyMatch(text -> text.contains("#146C2E")));
        assertFalse(texts.contains(context.getString(R.string.theme_editor_preview)));
        assertFalse(texts.contains(context.getString(R.string.theme_editor_inherit)));
        editor.replace(ThemePresets.Preset.ROSE.profile());
        ThemeTokens dark = editor.preview(true, 0);
        panel.render(editor, true, dark);
        assertSame(success, panel.findViewWithTag("theme-slot:SUCCESS"));
        assertTrue(texts(panel).contains(ThemeEditorUi.hex(dark.colorSuccess())));
        editor.reset();
        panel.render(editor, false, editor.preview(false, 0));
        assertEquals(persisted, ThemeProfileCodec.encode(ThemeProfileStore.load()));
        assertEquals(global, ThemeController.current());
    }

    private static void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(1100, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(3000, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private static List<String> texts(View view) {
        List<String> result = new ArrayList<>();
        if (view instanceof TextView text) result.add(text.getText().toString());
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) result.addAll(texts(group.getChildAt(i)));
        }
        return result;
    }
}
