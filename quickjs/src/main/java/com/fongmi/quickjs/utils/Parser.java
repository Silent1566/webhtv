package com.fongmi.quickjs.utils;

import android.text.TextUtils;

import com.fongmi.quickjs.bean.Cache;
import com.fongmi.quickjs.bean.Info;
import com.github.catvod.utils.UriUtil;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Parser {

    private final Pattern url = Pattern.compile("url\\((.*?)\\)", Pattern.MULTILINE | Pattern.DOTALL);
    private final Pattern noAdd = Pattern.compile(":eq|:lt|:gt|:first|:last|:not|:even|:odd|:has|:contains|:matches|:empty|^body$|^#");
    private final Pattern joinUrl = Pattern.compile("(url|src|href|-original|-src|-play|-url|style)$|^(data-|url-|src-)", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private final Pattern specialUrl = Pattern.compile("^(ftp|magnet|thunder|ws):", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private final Cache cache;

    public Parser() {
        cache = new Cache();
    }

    public String joinUrl(String parent, String child) {
        return UriUtil.resolve(parent, child);
    }

    public List<String> pdfa(String html, String rule) {
        Document doc = cache.getPdfa(html);
        rule = parseHikerToJq(rule, false);
        String[] parses = rule.split(" ");
        Elements elements = new Elements();
        for (String parse : parses) {
            elements = parseOneRule(doc, parse, elements);
            if (elements.isEmpty()) return Collections.emptyList();
        }
        List<String> items = new ArrayList<>();
        for (Element element : elements) items.add(element.outerHtml());
        return items;
    }

    public String pdfh(String html, String rule, String addUrl) {
        Document doc = cache.getPdfh(html);
        if ("body&&Text".equals(rule) || "Text".equals(rule)) return doc.text();
        if ("body&&Html".equals(rule) || "Html".equals(rule)) return doc.html();
        String option = "";
        if (rule.contains("&&")) {
            String[] rs = rule.split("&&");
            option = rs[rs.length - 1];
            List<String> excludes = new ArrayList<>(Arrays.asList(rs));
            excludes.remove(rs.length - 1);
            rule = TextUtils.join("&&", excludes);
        }
        rule = parseHikerToJq(rule, true);
        String[] parses = rule.split(" ");
        Elements elements = new Elements();
        for (String parse : parses) {
            elements = parseOneRule(doc, parse, elements);
            if (elements.isEmpty()) return "";
        }
        if (TextUtils.isEmpty(option)) return elements.outerHtml();
        if ("Text".equals(option)) return elements.text();
        if ("Html".equals(option)) return elements.html();
        return parseAttr(elements, option, addUrl);
    }

    public List<String> pdfl(String html, String rule, String texts, String urls, String urlKey) {
        String[] parses = parseHikerToJq(rule, false).split(" ");
        Elements elements = new Elements();
        for (String parse : parses) {
            elements = parseOneRule(cache.getPdfa(html), parse, elements);
            if (elements.isEmpty()) return Collections.emptyList();
        }
        List<String> items = new ArrayList<>();
        for (Element element : elements) {
            String item = element.outerHtml();
            items.add(pdfh(item, texts, "").trim() + '$' + pdfh(item, urls, urlKey));
        }
        return items;
    }

    private Info getParseInfo(String rule) {
        Info info = new Info(rule);
        if (rule.contains(":eq")) {
            info.setRule(rule.split(":")[0]);
            info.setInfo(rule.split(":")[1]);
        } else if (rule.contains("--")) {
            String[] rules = rule.split("--");
            info.setExcludes(rules);
            info.setRule(rules[0]);
        }
        return info;
    }

    private String parseHikerToJq(String parse, boolean first) {
        if (!parse.contains("&&")) {
            String[] split = parse.split(" ");
            if (!noAdd.matcher(split[split.length - 1]).find() && first) parse = parse + ":eq(0)";
            return parse;
        }
        String[] parses = parse.split("&&");
        List<String> items = new ArrayList<>();
        for (int i = 0; i < parses.length; i++) {
            String[] split = parses[i].split(" ");
            if (noAdd.matcher(split[split.length - 1]).find()) {
                items.add(parses[i]);
            } else {
                if (!first && i >= parses.length - 1) items.add(parses[i]);
                else items.add(parses[i] + ":eq(0)");
            }
        }
        return TextUtils.join(" ", items);
    }

    private Elements parseOneRule(Document doc, String parse, Elements elements) {
        Info info = getParseInfo(parse);
        elements = elements.isEmpty() ? doc.select(info.rule) : elements.select(info.rule);
        if (parse.contains(":eq")) {
            if (info.index < 0) elements = elements.eq(elements.size() + info.index);
            else elements = elements.eq(info.index);
        }
        if (info.excludes != null && !elements.isEmpty()) {
            elements = elements.clone();
            for (String exclude : info.excludes) elements.select(exclude).remove();
        }
        return elements;
    }

    private String parseAttr(Elements elements, String option, String addUrl) {
        String result = "";
        for (String item : option.split("[||]")) {
            result = elements.attr(item);
            if (item.toLowerCase().contains("style") && result.contains("url(")) {
                Matcher matcher = url.matcher(result);
                if (matcher.find()) result = matcher.group(1);
                result = result.replaceAll("^['|\"](.*)['|\"]$", "$1");
            }
            if (!TextUtils.isEmpty(result) && !TextUtils.isEmpty(addUrl) && joinUrl.matcher(item).find() && !specialUrl.matcher(result).find()) {
                result = result.contains("http") ? result.substring(result.indexOf("http")) : joinUrl(addUrl, result);
            }
            if (!TextUtils.isEmpty(result)) return result;
        }
        return result;
    }
}
