package com.safework.ai.core;

import com.safework.training.core.QuestionChecker;
import com.safework.training.core.QuestionDraft;
import com.safework.training.enums.QuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("解析 AI 生成的题目")
class AiQuestionParserTest {

    @Test
    @DisplayName("标准格式：前后有说明文字、包在代码块里也能解析")
    void standard() {
        String reply = """
                好的，以下是题目：
                ```json
                {"questions":[
                  {"type":"SINGLE","content":"氧含量合格范围？","options":["19.5%~23.5%","18%~21%","21%~25%","16%以上"],"answer":"A","analysis":"制度 2.2"},
                  {"type":"MULTI","content":"救援器材包括？","options":["三脚架","安全绳","过滤式面具","呼吸器"],"answer":"ABD","analysis":"制度 2.4"},
                  {"type":"JUDGE","content":"可以通入纯氧通风。","options":["正确","错误"],"answer":"B","analysis":"严禁通入纯氧"}
                ]}
                ```
                希望对你有帮助。""";
        List<QuestionDraft> list = AiQuestionParser.parse(reply);
        assertEquals(3, list.size());
        for (QuestionDraft d : list) {
            assertNull(QuestionChecker.check(d.type(), d.content(), d.options(), d.answer()), d.content());
        }
        assertEquals("ABD", list.get(1).answer());
        assertEquals("制度 2.4", list.get(1).analysis());
    }

    @Test
    @DisplayName("不规范写法：中文题型、选项带字母前缀、题干带序号、答案是数组")
    void sloppy() {
        String reply = """
                [{"type":"多选题","content":"1. 动火作业正确的有？","options":["A. 封堵地漏","B、设专人监火","(C) 监火人兼做其他工作","D：清除可燃物"],"answer":["a","b","D"]}]""";
        QuestionDraft d = AiQuestionParser.parse(reply).get(0);
        assertEquals(QuestionType.MULTI, d.type());
        assertEquals("动火作业正确的有？", d.content());
        assertEquals(List.of("封堵地漏", "设专人监火", "监火人兼做其他工作", "清除可燃物"), d.options());
        assertEquals("ABD", d.answer());
        assertNull(d.analysis());
    }

    @Test
    @DisplayName("判断题答案写成“正确 / 错 / true”；选项被忽略，固定为【正确、错误】")
    void judge() {
        String reply = """
                {"questions":[
                  {"type":"JUDGE","content":"甲","answer":"正确"},
                  {"type":"判断","content":"乙","options":["对","错"],"answer":"错"},
                  {"type":"TRUE_FALSE","content":"丙","answer":true}
                ]}""";
        List<QuestionDraft> list = AiQuestionParser.parse(reply);
        assertEquals(List.of("A", "B", "A"), list.stream().map(QuestionDraft::answer).toList());
        list.forEach(d -> assertEquals(QuestionChecker.JUDGE_OPTIONS, d.options()));
    }

    @Test
    @DisplayName("答案直接写了选项内容：按内容找回字母")
    void answerByText() {
        String reply = """
                {"questions":[{"type":"SINGLE","content":"几米以上算高处作业？","options":["1.5 米","2 米","3 米","5 米"],"answer":"2 米"}]}""";
        assertEquals("B", AiQuestionParser.parse(reply).get(0).answer());
    }

    @Test
    @DisplayName("格式错误的题解析出来后会被校验拦下：多选只有一个答案、类型不认识")
    void invalidCaughtByChecker() {
        String reply = """
                {"questions":[
                  {"type":"MULTI","content":"甲","options":["a","b","c","d"],"answer":"A"},
                  {"type":"填空","content":"乙","options":[],"answer":"x"}
                ]}""";
        List<QuestionDraft> list = AiQuestionParser.parse(reply);
        assertEquals(2, list.size());
        assertNotNull(QuestionChecker.check(list.get(0).type(), list.get(0).content(), list.get(0).options(), list.get(0).answer()));
        assertNull(list.get(1).type());
        assertNotNull(QuestionChecker.check(list.get(1).type(), list.get(1).content(), list.get(1).options(), list.get(1).answer()));
    }

    @Test
    @DisplayName("完全不是 JSON：返回空列表，不抛异常")
    void garbage() {
        assertTrue(AiQuestionParser.parse("抱歉，我无法完成。").isEmpty());
        assertTrue(AiQuestionParser.parse(null).isEmpty());
        assertTrue(AiQuestionParser.parse("{\"questions\": [ {\"type\": ").isEmpty());
    }
}
