package com.fongmi.quickjs.crawler;

import com.fongmi.quickjs.utils.Module;
import com.whl.quickjs.android.QuickJSLoader;

import dalvik.system.DexClassLoader;

public class Loader {

    private final Module modules = new Module();

    public Loader() {
        QuickJSLoader.init();
    }

    public Spider spider(String api, DexClassLoader dex) {
        return new Spider(api, dex, modules);
    }

    public void clearModules() {
        modules.clear();
    }
}
