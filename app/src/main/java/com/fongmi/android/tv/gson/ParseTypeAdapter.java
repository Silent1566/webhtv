package com.fongmi.android.tv.gson;

import android.text.TextUtils;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

public class ParseTypeAdapter implements JsonDeserializer<Integer> {

    @Override
    public Integer deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json == null || json.isJsonNull()) return 0;
        try {
            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) return json.getAsInt();
            if (!json.isJsonPrimitive()) return 0;
            return parse(json.getAsString());
        } catch (Throwable e) {
            return 0;
        }
    }

    private int parse(String value) {
        if (TextUtils.isEmpty(value)) return 0;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
