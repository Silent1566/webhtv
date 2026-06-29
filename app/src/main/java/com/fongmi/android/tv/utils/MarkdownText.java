package com.fongmi.android.tv.utils;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MarkdownText {

    private static final Pattern LINK = Pattern.compile("\\[([^]]+)]\\(([^)]+)\\)");
    private static final Pattern BOLD = Pattern.compile("\\*\\*([^*]+)\\*\\*|__([^_]+)__");
    private static final Pattern CODE = Pattern.compile("`([^`]+)`");

    private MarkdownText() {
    }

    public static CharSequence render(String markdown, String fallback) {
        String text = TextUtils.isEmpty(markdown) ? fallback : markdown;
        if (TextUtils.isEmpty(text)) return "";
        SpannableStringBuilder builder = new SpannableStringBuilder();
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String raw : lines) appendLine(builder, raw);
        trimEnd(builder);
        return builder;
    }

    private static void appendLine(SpannableStringBuilder builder, String raw) {
        String line = raw.trim();
        if (line.isEmpty()) {
            appendNewLine(builder);
            return;
        }
        int start = builder.length();
        int level = headingLevel(line);
        if (level > 0) line = line.substring(level).trim();
        else if (line.startsWith("- ") || line.startsWith("* ")) line = "• " + line.substring(2).trim();
        else if (line.startsWith("> ")) line = line.substring(2).trim();
        builder.append(cleanInline(line));
        if (level > 0) {
            builder.setSpan(new StyleSpan(Typeface.BOLD), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new RelativeSizeSpan(level <= 2 ? 1.12f : 1.05f), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        appendNewLine(builder);
    }

    private static int headingLevel(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == '#') count++;
        return count > 0 && count < line.length() && line.charAt(count) == ' ' ? count : 0;
    }

    private static String cleanInline(String line) {
        line = replaceGroups(LINK.matcher(line));
        line = replaceGroups(BOLD.matcher(line));
        line = replaceGroups(CODE.matcher(line));
        return line;
    }

    private static String replaceGroups(Matcher matcher) {
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String replacement = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement == null ? "" : replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static void appendNewLine(SpannableStringBuilder builder) {
        builder.append('\n');
    }

    private static void trimEnd(SpannableStringBuilder builder) {
        while (builder.length() > 0 && builder.charAt(builder.length() - 1) == '\n') builder.delete(builder.length() - 1, builder.length());
    }
}
