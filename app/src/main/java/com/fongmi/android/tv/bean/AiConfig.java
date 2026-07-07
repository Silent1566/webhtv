package com.fongmi.android.tv.bean;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class AiConfig {

    private static final Gson GSON = new Gson();
    public static final String PROTOCOL_OPENAI_RESPONSES = "openai_responses";
    public static final String PROTOCOL_OPENAI_CHAT = "openai_chat";
    public static final String PROTOCOL_ANTHROPIC_MESSAGES = "anthropic_messages";
    public static final String PROTOCOL_GEMINI_NATIVE = "gemini_native";
    public static final String DEFAULT_PROTOCOL = PROTOCOL_OPENAI_RESPONSES;
    public static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/responses";
    public static final String DEFAULT_OPENAI_CHAT_ENDPOINT = "https://api.openai.com/v1/chat/completions";
    public static final String DEFAULT_ANTHROPIC_ENDPOINT = "https://api.anthropic.com/v1/messages";
    public static final String DEFAULT_GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta";
    public static final String DEFAULT_MODEL = "gpt-4.1-mini";
    public static final int DEFAULT_RECOMMEND_PROMPT_VERSION = 2;
    public static final String LEGACY_RECOMMEND_PROMPT_V1 = "你是一位专业的影视剧推荐专家，熟悉全球影视内容，包括电视剧、电影、动漫、纪录片等。你的任务是根据用户提供的观影历史和搜索记录，分析用户的偏好，并输出个性化的影视推荐列表。只返回可解析 JSON，不要解释，建议格式为 {\"items\":[{\"title\":\"片名\",\"year\":2024,\"mediaType\":\"movie 或 tv\",\"reason\":\"一句推荐理由\"}]}。优先推荐不同于历史记录和当前影片的作品，推荐数量由提示词自行决定。";
    public static final String DEFAULT_RECOMMEND_PROMPT = "你是一位专业的影视推荐专家，熟悉全球电影、电视剧、动漫、纪录片、综艺及短剧内容。"
            + "你的任务是根据用户的当前影片、播放历史、搜索记录、观看进度和内容元数据，分析用户偏好，并输出个性化影视推荐。"
            + "请重点分析用户偏好的题材、类型、国家/地区、语言、年代、叙事风格、节奏和受众倾向；"
            + "播放历史中的观看深度、已看集数、观看时长、完播率和最近观看时间权重更高；"
            + "搜索记录代表兴趣意向，但不等同于已观看；当前影片代表即时兴趣，应提高相似题材、相似气质、相似受众作品的权重。"
            + "优先推荐不同于当前影片和播放历史的作品，避免推荐用户已经看过或正在看的片名、别名和易混淆续作。"
            + "推荐结果应兼顾相似偏好和适度拓展，不要全部集中在同一题材；如果历史中包含乱码、异常标题、合集、非影视内容，请降低其权重。"
            + "推荐数量为 12-24 部，默认推荐 16 部；若用户历史较少则推荐 12 部，历史丰富则推荐 18-24 部。"
            + "只返回可解析 JSON，不要解释、Markdown 或多余文本，格式为 {\"items\":[{\"title\":\"片名\",\"year\":2024,\"mediaType\":\"movie 或 tv\",\"reason\":\"一句推荐理由\"}]}。"
            + "mediaType 只能使用 movie 或 tv，reason 控制在 20-45 个中文字符，并说明它为什么适合这个用户。";
    public static final int DEFAULT_TITLE_EXTRACTION_PROMPT_VERSION = 2;
    public static final String LEGACY_TITLE_EXTRACTION_PROMPT_V1 = "你是影视资源标题解析器。你的任务是从网盘/资源站标题中提取真实影视作品名。\n"
            + "要求：只返回严格 JSON，不要 Markdown，不要解释；不要保留清晰度、编码、字幕、语言版本、更新状态、集数、合集、资源组、平台名；"
            + "如果标题是拼音缩写、谐音、防和谐写法，请尽量还原为中文正式片名；学习样本来自用户本机手动修正，相关时优先，但不要强行套用；不要编造 TMDB ID。\n"
            + "返回格式：{\"canonicalTitle\":\"剧名\",\"originalTitle\":\"\",\"mediaType\":\"tv|movie|unknown\",\"year\":0,\"seasonNumber\":-1,\"episodeNumber\":-1,\"episodeTitle\":\"\",\"aliases\":[\"别名\"],\"confidence\":0.0,\"reasonCode\":\"clean|homophone|pinyin|uncertain\"}";
    public static final String DEFAULT_TITLE_EXTRACTION_PROMPT = "你是影视资源标题解析器。你的任务是从网盘、资源站、推送链接标题中提取真实影视作品名。\n"
            + "要求：只返回严格 JSON，不要 Markdown，不要解释；不要保留清晰度、编码、字幕、语言版本、更新状态、集数、合集、资源组、平台名、线路名或分类词。"
            + "标题中的 #、空格、点、短横线、竖线常用于分隔或防和谐；如果两侧中文片段能组成正式片名，请合并还原，例如“凡人#修仙传”应提取为“凡人修仙传”。"
            + "开头或结尾的单字母、A/B/C、动漫、电影、电视剧、剧集等通常是资源分组或分类，不属于作品名。"
            + "如果标题是拼音缩写、谐音、防和谐写法，请尽量还原为中文正式片名；学习样本来自用户本机手动修正，相关时优先，但不要强行套用；不要编造 TMDB ID。\n"
            + "返回格式：{\"canonicalTitle\":\"剧名\",\"originalTitle\":\"\",\"mediaType\":\"tv|movie|unknown\",\"year\":0,\"seasonNumber\":-1,\"episodeNumber\":-1,\"episodeTitle\":\"\",\"aliases\":[\"别名\"],\"confidence\":0.0,\"reasonCode\":\"clean|homophone|pinyin|separator|uncertain\"}";

    @SerializedName("enabled")
    private boolean enabled;
    @SerializedName(value = "protocol", alternate = {"apiFormat", "format"})
    private String protocol;
    @SerializedName("endpoint")
    private String endpoint;
    @SerializedName("apiKey")
    private String apiKey;
    @SerializedName("model")
    private String model;
    @SerializedName(value = "customUserAgent", alternate = {"userAgent", "ua"})
    private String customUserAgent;
    @SerializedName("recommendPrompt")
    private String recommendPrompt;
    @SerializedName("recommendPromptVersion")
    private int recommendPromptVersion;
    @SerializedName("recommendPromptCustom")
    private boolean recommendPromptCustom;
    @SerializedName("titleExtractionPrompt")
    private String titleExtractionPrompt;
    @SerializedName("titleExtractionPromptVersion")
    private int titleExtractionPromptVersion;
    @SerializedName("titleExtractionPromptCustom")
    private boolean titleExtractionPromptCustom;

    public static AiConfig objectFrom(String json) {
        try {
            AiConfig config = GSON.fromJson(json, AiConfig.class);
            return config == null ? new AiConfig().sanitize() : config.sanitize();
        } catch (Throwable e) {
            return new AiConfig().sanitize();
        }
    }

    public AiConfig sanitize() {
        protocol = isSupportedProtocol(protocol) ? protocol.trim() : DEFAULT_PROTOCOL;
        endpoint = trimOr(endpoint, defaultEndpoint(protocol));
        apiKey = trimOr(apiKey, "");
        model = trimOr(model, DEFAULT_MODEL);
        customUserAgent = trimOr(customUserAgent, "");
        sanitizeRecommendPrompt();
        sanitizeTitleExtractionPrompt();
        return this;
    }

    public boolean isReady() {
        sanitize();
        return enabled && !isEmpty(endpoint) && !isEmpty(apiKey) && !isEmpty(model);
    }

    public boolean isModelFetchReady() {
        sanitize();
        return !isEmpty(endpoint) && !isEmpty(apiKey);
    }

    public String toJson() {
        return GSON.toJson(sanitize());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getCustomUserAgent() {
        return customUserAgent;
    }

    public void setCustomUserAgent(String customUserAgent) {
        this.customUserAgent = customUserAgent;
    }

    public String getRecommendPrompt() {
        return recommendPrompt;
    }

    public void setRecommendPrompt(String recommendPrompt) {
        String value = recommendPrompt == null ? "" : recommendPrompt.trim();
        if (isEmpty(value) || isBuiltInRecommendPrompt(value)) {
            resetRecommendPrompt();
        } else {
            this.recommendPrompt = value;
            this.recommendPromptCustom = true;
            this.recommendPromptVersion = DEFAULT_RECOMMEND_PROMPT_VERSION;
        }
    }

    public int getRecommendPromptVersion() {
        return recommendPromptVersion;
    }

    public boolean isRecommendPromptCustom() {
        return recommendPromptCustom;
    }

    public void resetRecommendPrompt() {
        recommendPrompt = DEFAULT_RECOMMEND_PROMPT;
        recommendPromptCustom = false;
        recommendPromptVersion = DEFAULT_RECOMMEND_PROMPT_VERSION;
    }

    public String getTitleExtractionPrompt() {
        return titleExtractionPrompt;
    }

    public void setTitleExtractionPrompt(String titleExtractionPrompt) {
        String value = titleExtractionPrompt == null ? "" : titleExtractionPrompt.trim();
        if (isEmpty(value) || isBuiltInTitleExtractionPrompt(value)) {
            resetTitleExtractionPrompt();
        } else {
            this.titleExtractionPrompt = value;
            this.titleExtractionPromptCustom = true;
            this.titleExtractionPromptVersion = DEFAULT_TITLE_EXTRACTION_PROMPT_VERSION;
        }
    }

    public int getTitleExtractionPromptVersion() {
        return titleExtractionPromptVersion;
    }

    public boolean isTitleExtractionPromptCustom() {
        return titleExtractionPromptCustom;
    }

    public void resetTitleExtractionPrompt() {
        titleExtractionPrompt = DEFAULT_TITLE_EXTRACTION_PROMPT;
        titleExtractionPromptCustom = false;
        titleExtractionPromptVersion = DEFAULT_TITLE_EXTRACTION_PROMPT_VERSION;
    }

    public static String defaultEndpoint(String protocol) {
        if (PROTOCOL_OPENAI_CHAT.equals(protocol)) return DEFAULT_OPENAI_CHAT_ENDPOINT;
        if (PROTOCOL_ANTHROPIC_MESSAGES.equals(protocol)) return DEFAULT_ANTHROPIC_ENDPOINT;
        if (PROTOCOL_GEMINI_NATIVE.equals(protocol)) return DEFAULT_GEMINI_ENDPOINT;
        return DEFAULT_ENDPOINT;
    }

    public static boolean isSupportedProtocol(String protocol) {
        if (protocol == null) return false;
        String value = protocol.trim();
        return PROTOCOL_OPENAI_RESPONSES.equals(value)
                || PROTOCOL_OPENAI_CHAT.equals(value)
                || PROTOCOL_ANTHROPIC_MESSAGES.equals(value)
                || PROTOCOL_GEMINI_NATIVE.equals(value);
    }

    private static String trimOr(String value, String fallback) {
        return isEmpty(value) ? fallback : value.trim();
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void sanitizeRecommendPrompt() {
        String value = recommendPrompt == null ? "" : recommendPrompt.trim();
        if (isEmpty(value) || isBuiltInRecommendPrompt(value)) {
            resetRecommendPrompt();
            return;
        }
        recommendPrompt = value;
        recommendPromptCustom = true;
        if (recommendPromptVersion <= 0) recommendPromptVersion = DEFAULT_RECOMMEND_PROMPT_VERSION;
    }

    private void sanitizeTitleExtractionPrompt() {
        String value = titleExtractionPrompt == null ? "" : titleExtractionPrompt.trim();
        if (isEmpty(value) || isBuiltInTitleExtractionPrompt(value)) {
            resetTitleExtractionPrompt();
            return;
        }
        titleExtractionPrompt = value;
        titleExtractionPromptCustom = true;
        if (titleExtractionPromptVersion <= 0) titleExtractionPromptVersion = DEFAULT_TITLE_EXTRACTION_PROMPT_VERSION;
    }

    public static boolean isBuiltInRecommendPrompt(String prompt) {
        if (prompt == null) return false;
        String value = prompt.trim();
        if (DEFAULT_RECOMMEND_PROMPT.equals(value)) return true;
        return LEGACY_RECOMMEND_PROMPT_V1.equals(value);
    }

    public static boolean isBuiltInTitleExtractionPrompt(String prompt) {
        if (prompt == null) return false;
        String value = prompt.trim();
        if (DEFAULT_TITLE_EXTRACTION_PROMPT.equals(value)) return true;
        return LEGACY_TITLE_EXTRACTION_PROMPT_V1.equals(value);
    }

    public static String[] systemRecommendPromptsForCache() {
        return new String[]{DEFAULT_RECOMMEND_PROMPT, LEGACY_RECOMMEND_PROMPT_V1};
    }

    public static String[] systemTitleExtractionPromptsForCache() {
        return new String[]{DEFAULT_TITLE_EXTRACTION_PROMPT, LEGACY_TITLE_EXTRACTION_PROMPT_V1};
    }
}
