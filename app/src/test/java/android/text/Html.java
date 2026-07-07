package android.text;

public final class Html {

    private Html() {
    }

    public static Spanned fromHtml(String source) {
        String text = source == null ? "" : source.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]+>", "");
        return SpannedString.valueOf(text);
    }
}
