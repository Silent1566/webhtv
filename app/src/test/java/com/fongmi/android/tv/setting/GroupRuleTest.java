package com.fongmi.android.tv.setting;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.bean.GroupRule;

public class GroupRuleTest {

    @Test
    public void bracketBuiltinExtractsTags() {
        GroupRule rule = GroupRule.builtin(GroupRuleConfig.BUILTIN_BRACKET, "方括号标签", "\\[([^\\]]+)\\]", false);
        assertEquals(List.of("主力", "短剧"), rule.extract("[主力][短剧]我的站"));
    }

    @Test
    public void pipeBuiltinExtractsSuffixAfterPipe() {
        GroupRule rule = GroupRule.builtin(GroupRuleConfig.BUILTIN_PIPE, "竖线后缀", "(?i)(?:[|｜])\\s*([^|｜]+?)\\s*$", false);
        assertEquals(List.of("秒播"), rule.extract("⭐夏天|秒播"));
        assertEquals(List.of("4K"), rule.extract("💥木偶|4K"));
        assertEquals(List.of("4K"), rule.extract("💥玩偶|4K"));
        assertEquals(List.of("1080P"), rule.extract("某某｜1080P"));
        assertTrue(rule.extract("普通线路").isEmpty());
    }

    @Test
    public void boxBuiltinExtractsLastSegment() {
        GroupRule rule = GroupRule.builtin(GroupRuleConfig.BUILTIN_BOX, "框线分隔", "(?i)┆\\s*([^┆]+)\\s*$", false);
        assertEquals(List.of("4K"), rule.extract("👽️┆玩偶┆4K"));
        assertEquals(List.of("4K"), rule.extract("🪵┆木偶┆4K"));
        assertEquals(List.of("蓝光"), rule.extract("来源┆蓝光"));
    }

    @Test
    public void bulletBuiltinExtractsSuffixAfterBullet() {
        GroupRule rule = GroupRule.builtin(GroupRuleConfig.BUILTIN_BULLET, "圆点后缀", "(?i)(?:[•·])\\s*([^•·]+?)\\s*$", false);
        assertEquals(List.of("APP"), rule.extract("热播 • APP"));
        assertEquals(List.of("4K"), rule.extract("蜡笔 • 4K"));
        assertEquals(List.of("APP"), rule.extract("热播·APP"));
        assertTrue(rule.extract("普通线路").isEmpty());
    }

    @Test
    public void builtinsAreEnabledByDefault() {
        for (GroupRule rule : GroupRuleConfig.builtins()) {
            assertTrue(rule.getId(), rule.isEnabled());
            assertTrue(rule.getId(), rule.isValid());
        }
    }

    @Test
    public void customRuleUsesFirstCaptureGroup() {
        GroupRule rule = GroupRule.createUser("自定义", "(?i)【(.+?)】");
        assertTrue(rule.isValid());
        assertEquals(List.of("HDR"), rule.extract("电影【HDR】"));
    }

    @Test
    public void invalidRegexIsRejected() {
        GroupRule rule = GroupRule.createUser("坏规则", "(");
        assertFalse(rule.isValid());
        assertTrue(rule.extract("任意文本").isEmpty());
    }

    @Test
    public void interfaceArrayFromFillsDefaults() {
        String json = "[{\"name\":\"接口规则\",\"regex\":\"#(.+)$\"}]";
        List<GroupRule> rules = GroupRule.arrayFromJson(json);
        assertEquals(1, rules.size());
        assertFalse(rules.get(0).getId().isEmpty());
        assertEquals(GroupRule.SOURCE_INTERFACE, rules.get(0).getSource());
        assertTrue(rules.get(0).isEnabled());
        assertEquals(List.of("分组A"), rules.get(0).extract("前缀#分组A"));
    }
}
