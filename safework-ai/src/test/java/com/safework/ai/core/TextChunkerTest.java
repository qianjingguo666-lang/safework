package com.safework.ai.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("文档切分")
class TextChunkerTest {

    private static final String DOC = """
            # 受限空间作业安全管理制度
            一、适用范围
            本制度适用于公司内所有受限空间作业。受限空间是指封闭或部分封闭、进出口受限但人员可以进入的空间。
            三、气体检测
            作业前必须先通风，再检测，后作业。检测时间不得早于作业开始前30分钟。氧含量应在19.5%至23.5%之间。可燃气体浓度不得超过爆炸下限的10%。作业中断超过30分钟，必须重新检测。
            四、应急救援
            发生事故时严禁盲目施救。救援人员必须佩戴正压式空气呼吸器。
            """ + "这是一段很长的没有标点的文字".repeat(60);

    private final TextChunker chunker = new TextChunker(120, 30);

    private List<TextChunker.Chunk> split() {
        return chunker.split(List.of(new TextChunker.PageText(null, DOC)));
    }

    @Test
    @DisplayName("长文档切成多个片段，每段不会超出上限太多")
    void splitsIntoBoundedChunks() {
        List<TextChunker.Chunk> chunks = split();
        assertTrue(chunks.size() >= 4, "片段数 " + chunks.size());
        assertTrue(chunks.stream().allMatch(c -> c.content().length() <= 120 + 30 + 20));
    }

    @Test
    @DisplayName("每个片段带上所属章节标题，检索时能看出出处")
    void chunksCarryHeading() {
        List<TextChunker.Chunk> chunks = split();
        assertTrue(chunks.stream().anyMatch(c -> c.content().startsWith("【三、气体检测】")));
        assertTrue(chunks.stream().anyMatch(c -> c.content().startsWith("【四、应急救援】") && c.content().contains("盲目施救")));
    }

    @Test
    @DisplayName("标题不会单独成为一个片段")
    void headingNotAlone() {
        assertTrue(split().stream().noneMatch(c -> c.content().equals("【受限空间作业安全管理制度】")));
    }

    @Test
    @DisplayName("PDF 分页时保留页码")
    void keepsPageNumber() {
        List<TextChunker.Chunk> chunks = chunker.split(List.of(
                new TextChunker.PageText(1, "第一页内容。"),
                new TextChunker.PageText(2, "第二页内容，严禁盲目施救。")));
        assertEquals(2, chunks.size());
        assertEquals(2, chunks.get(1).pageNo());
    }
}
