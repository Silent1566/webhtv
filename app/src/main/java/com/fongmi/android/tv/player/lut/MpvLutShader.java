package com.fongmi.android.tv.player.lut;

import java.io.File;

public class MpvLutShader {

    private final File file;
    private final String presetName;
    private final int strength;
    private final int size;
    private final boolean preview;

    public MpvLutShader(File file, String presetName, int strength, int size, boolean preview) {
        this.file = file;
        this.presetName = presetName;
        this.strength = strength;
        this.size = size;
        this.preview = preview;
    }

    public File getFile() {
        return file;
    }

    public String getPath() {
        return file.getAbsolutePath();
    }

    public String getPresetName() {
        return presetName;
    }

    public int getStrength() {
        return strength;
    }

    public int getSize() {
        return size;
    }

    public boolean isPreview() {
        return preview;
    }

    public String diagnostics() {
        return presetName + " " + strength + "% " + size + "^3";
    }
}
