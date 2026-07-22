package com.fongmi.android.tv.ui.adapter;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertTrue;

public class VodAdapterLayoutTest {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    @Test
    public void mobileCategoryTitlesMarqueeOnlyWhenOverflowing() throws Exception {
        assertTitleMarquee("list", "adapter_vod_list.xml", "VodListHolder.java");
        assertTitleMarquee("rect", "adapter_vod_rect.xml", "VodRectHolder.java");
        assertTitleMarquee("oval", "adapter_vod_oval.xml", "VodOvalHolder.java");
    }

    private static void assertTitleMarquee(String style, String layoutName, String holderName) throws Exception {
        Element name = findById(parseLayout(read(findMobileResPath().resolve(Path.of("layout", layoutName)))), "@+id/name");
        String holder = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "holder", holderName)));

        assertTrue(style + " category title must marquee only when its text overflows",
                "marquee".equals(androidAttribute(name, "ellipsize"))
                        && "marquee_forever".equals(androidAttribute(name, "marqueeRepeatLimit"))
                        && "true".equals(androidAttribute(name, "scrollHorizontally"))
                        && "true".equals(androidAttribute(name, "singleLine")));
        assertTrue(style + " category holder must activate the title marquee",
                holder.contains("binding.name.setSelected(true);"));
    }

    private static Element parseLayout(String layout) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(layout.getBytes(StandardCharsets.UTF_8)))
                .getDocumentElement();
    }

    private static Element findById(Element root, String id) {
        if (id.equals(androidAttribute(root, "id"))) return root;
        NodeList nodes = root.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if (id.equals(androidAttribute(element, "id"))) return element;
        }
        throw new AssertionError("Missing layout view " + id);
    }

    private static String androidAttribute(Element element, String name) {
        return element.getAttributeNS(ANDROID_NS, name);
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path findMobileJavaPath() {
        Path moduleRelative = Path.of("src", "mobile", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "java");
    }

    private static Path findMobileResPath() {
        Path moduleRelative = Path.of("src", "mobile", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "res");
    }
}
