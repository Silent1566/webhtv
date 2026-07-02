package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.gson.HeaderAdapter;
import com.fongmi.android.tv.gson.ParseTypeAdapter;
import com.fongmi.android.tv.impl.Diffable;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.utils.Util;
import com.google.gson.JsonElement;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Parse implements Diffable<Parse> {

    @SerializedName("name")
    private String name;
    @SerializedName("type")
    @JsonAdapter(ParseTypeAdapter.class)
    private Integer type;
    @SerializedName("url")
    private String url;
    @SerializedName("ext")
    private Ext ext;

    private boolean selected;
    private String click;

    public static Parse objectFrom(JsonElement element) {
        return App.gson().fromJson(element, Parse.class);
    }

    public static Parse get(Integer type, String url) {
        Parse parse = new Parse();
        parse.setType(type);
        parse.setUrl(url);
        return parse;
    }

    public static Parse god() {
        Parse parse = new Parse();
        parse.setName(ResUtil.getString(R.string.parse_god));
        parse.setType(4);
        return parse;
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? "" : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getType() {
        return type == null ? 0 : type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getUrl() {
        return TextUtils.isEmpty(url) ? "" : UrlUtil.convert(url);
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Ext getExt() {
        return ext = ext == null ? new Ext() : ext;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public void setSelected(Parse item) {
        this.selected = item.equals(this);
    }

    public String getClick() {
        return TextUtils.isEmpty(click) ? "" : click;
    }

    public void setClick(String click) {
        this.click = click;
    }

    public Map<String, String> getHeader() {
        return getExt().getHeader();
    }

    public void setHeader(Map<String, String> header) {
        if (getHeader().isEmpty()) getExt().setHeader(header);
    }

    public boolean isEmpty() {
        return getType() == 0 && getUrl().isEmpty();
    }

    public String extUrl() {
        int index = getUrl().indexOf("?");
        if (getExt().isEmpty() || index == -1) return getUrl();
        return getUrl().substring(0, index + 1) + "cat_ext=" + Util.base64(getExt().toString(), Util.URL_SAFE) + "&" + getUrl().substring(index + 1);
    }

    public HashMap<String, String> mixMap() {
        HashMap<String, String> map = new HashMap<>();
        map.put("type", getType().toString());
        map.put("ext", getExt().toString());
        map.put("url", getUrl());
        return map;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Parse it)) return false;
        return Objects.equals(getName(), it.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName());
    }

    @Override
    public boolean isSameItem(Parse other) {
        return equals(other);
    }

    @Override
    public boolean isSameContent(Parse other) {
        return equals(other);
    }

    public static class Ext {

        @SerializedName("flag")
        private List<String> flag;
        @SerializedName("header")
        @JsonAdapter(HeaderAdapter.class)
        private Map<String, String> header;

        public List<String> getFlag() {
            return flag == null ? Collections.emptyList() : flag;
        }

        public void setFlag(List<String> flag) {
            this.flag = flag;
        }

        public Map<String, String> getHeader() {
            return header == null ? new HashMap<>() : header;
        }

        public void setHeader(Map<String, String> header) {
            this.header = header;
        }

        public boolean isEmpty() {
            return getFlag().isEmpty() && getHeader().isEmpty();
        }

        @NonNull
        @Override
        public String toString() {
            return App.gson().toJson(this);
        }
    }
}
