package com.fongmi.android.tv.bean;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AiConfigTest {

    @Test
    public void objectFrom_usesSafeDefaultsAndRequiresExplicitEnable() {
        AiConfig config = AiConfig.objectFrom("");

        assertEquals(AiConfig.PROTOCOL_OPENAI_RESPONSES, config.getProtocol());
        assertEquals(AiConfig.DEFAULT_ENDPOINT, config.getEndpoint());
        assertEquals(AiConfig.DEFAULT_MODEL, config.getModel());
        assertEquals("", config.getCustomUserAgent());
        assertFalse(config.isReady());
    }

    @Test
    public void isReady_requiresEnabledEndpointKeyAndModel() {
        AiConfig config = AiConfig.objectFrom("{\"enabled\":true,\"endpoint\":\"https://api.openai.com/v1/responses\",\"apiKey\":\"sk-test\",\"model\":\"gpt-4.1-mini\"}");

        assertTrue(config.isReady());
    }

    @Test
    public void objectFrom_supportsProtocolSpecificEndpointDefaultAndUserAgentAlias() {
        AiConfig config = AiConfig.objectFrom("{\"enabled\":true,\"apiFormat\":\"openai_chat\",\"endpoint\":\"\",\"apiKey\":\"sk-test\",\"model\":\"gpt-test\",\"userAgent\":\" claude-cli/2.1.161 \"}");

        assertEquals(AiConfig.PROTOCOL_OPENAI_CHAT, config.getProtocol());
        assertEquals(AiConfig.DEFAULT_OPENAI_CHAT_ENDPOINT, config.getEndpoint());
        assertEquals("claude-cli/2.1.161", config.getCustomUserAgent());
        assertTrue(config.isReady());
    }

    @Test
    public void objectFrom_unknownProtocolFallsBackToResponses() {
        AiConfig config = AiConfig.objectFrom("{\"protocol\":\"unknown\",\"endpoint\":\"\",\"apiKey\":\"sk-test\"}");

        assertEquals(AiConfig.PROTOCOL_OPENAI_RESPONSES, config.getProtocol());
        assertEquals(AiConfig.DEFAULT_ENDPOINT, config.getEndpoint());
    }

    @Test
    public void objectFrom_upgradesLegacyDefaultRecommendPrompt() {
        AiConfig config = AiConfig.objectFrom("{\"recommendPrompt\":\"" + AiConfig.LEGACY_RECOMMEND_PROMPT_V1.replace("\"", "\\\"") + "\"}");

        assertEquals(AiConfig.DEFAULT_RECOMMEND_PROMPT, config.getRecommendPrompt());
        assertEquals(AiConfig.DEFAULT_RECOMMEND_PROMPT_VERSION, config.getRecommendPromptVersion());
        assertFalse(config.isRecommendPromptCustom());
    }

    @Test
    public void objectFrom_preservesLegacyCustomRecommendPrompt() {
        AiConfig config = AiConfig.objectFrom("{\"recommendPrompt\":\"请优先推荐冷门悬疑片\"}");

        assertEquals("请优先推荐冷门悬疑片", config.getRecommendPrompt());
        assertTrue(config.isRecommendPromptCustom());
    }

    @Test
    public void setRecommendPrompt_marksCurrentDefaultAsSystemPrompt() {
        AiConfig config = AiConfig.objectFrom("{}");
        config.setRecommendPrompt("请优先推荐冷门悬疑片");
        config.setRecommendPrompt(AiConfig.DEFAULT_RECOMMEND_PROMPT);

        assertEquals(AiConfig.DEFAULT_RECOMMEND_PROMPT, config.getRecommendPrompt());
        assertFalse(config.isRecommendPromptCustom());
    }

    @Test
    public void objectFrom_upgradesLegacyDefaultTitleExtractionPrompt() {
        AiConfig config = AiConfig.objectFrom("{\"titleExtractionPrompt\":\"" + AiConfig.LEGACY_TITLE_EXTRACTION_PROMPT_V1.replace("\"", "\\\"") + "\"}");

        assertEquals(AiConfig.DEFAULT_TITLE_EXTRACTION_PROMPT, config.getTitleExtractionPrompt());
        assertEquals(AiConfig.DEFAULT_TITLE_EXTRACTION_PROMPT_VERSION, config.getTitleExtractionPromptVersion());
        assertFalse(config.isTitleExtractionPromptCustom());
    }

    @Test
    public void objectFrom_preservesLegacyCustomTitleExtractionPrompt() {
        AiConfig config = AiConfig.objectFrom("{\"titleExtractionPrompt\":\"只提取中文正式片名\"}");

        assertEquals("只提取中文正式片名", config.getTitleExtractionPrompt());
        assertTrue(config.isTitleExtractionPromptCustom());
    }

    @Test
    public void setTitleExtractionPrompt_marksCurrentDefaultAsSystemPrompt() {
        AiConfig config = AiConfig.objectFrom("{}");
        config.setTitleExtractionPrompt("优先还原被分隔符拆开的中文片名");
        config.setTitleExtractionPrompt(AiConfig.DEFAULT_TITLE_EXTRACTION_PROMPT);

        assertEquals(AiConfig.DEFAULT_TITLE_EXTRACTION_PROMPT, config.getTitleExtractionPrompt());
        assertFalse(config.isTitleExtractionPromptCustom());
    }
}
