package com.safework.training.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safework.training.core.Grader;
import com.safework.training.core.PaperItem;
import com.safework.training.core.QuestionChecker;
import com.safework.training.dto.PaperVO;
import com.safework.training.dto.ResultVO;
import com.safework.training.entity.TrainExamRecord;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 试卷、答案的 JSON 读写，以及“答题视图 / 成绩视图”的转换 */
@Component
public class PaperCodec {

    private final ObjectMapper json = new ObjectMapper();

    public List<PaperItem> paper(TrainExamRecord r) {
        try {
            return json.readValue(r.getPaper(), new TypeReference<List<PaperItem>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("试卷数据损坏：" + r.getId(), e);
        }
    }

    public String writePaper(List<PaperItem> paper) {
        return write(paper);
    }

    public Map<Integer, String> answers(TrainExamRecord r) {
        if (!StringUtils.hasText(r.getAnswers())) {
            return new TreeMap<>();
        }
        try {
            return json.readValue(r.getAnswers(), new TypeReference<TreeMap<Integer, String>>() {
            });
        } catch (JsonProcessingException e) {
            return new TreeMap<>();
        }
    }

    public String writeAnswers(Map<Integer, String> answers) {
        return write(answers);
    }

    /** 只保留试卷上存在的题号，答案标准化（"c,a" → "AC"），空答案去掉 */
    public Map<Integer, String> clean(List<PaperItem> paper, Map<Integer, String> answers) {
        Map<Integer, String> result = new TreeMap<>();
        if (answers == null) {
            return result;
        }
        for (PaperItem item : paper) {
            String a = QuestionChecker.normalizeAnswer(answers.get(item.index()));
            if (!a.isEmpty()) {
                result.put(item.index(), a);
            }
        }
        return result;
    }

    /** 答题视图：去掉正确答案和解析 */
    public PaperVO toPaperVO(TrainExamRecord r, Integer durationMinutes, Integer passScore) {
        List<PaperVO.Question> items = paper(r).stream()
                .map(i -> new PaperVO.Question(i.index(), i.type(), i.content(), i.options(), i.score()))
                .toList();
        return new PaperVO(r.getId(), r.getExamTitle(), r.getAttemptNo(), r.getDeadline(), LocalDateTime.now(),
                durationMinutes, r.getTotalScore(), passScore, items, answers(r));
    }

    /** 成绩视图：showAnswer 为 false 时不给正确答案和解析 */
    public ResultVO toResultVO(TrainExamRecord r, Integer passScore, boolean showAnswer) {
        Map<Integer, String> answers = answers(r);
        List<ResultVO.Item> items = paper(r).stream().map(i -> {
            String mine = answers.get(i.index());
            boolean correct = Grader.isCorrect(i.answer(), mine);
            return new ResultVO.Item(i.index(), i.type(), i.content(), i.options(), i.score(), mine, correct,
                    showAnswer ? i.answer() : null, showAnswer ? i.analysis() : null);
        }).toList();
        long cost = r.getStartTime() == null || r.getSubmitTime() == null ? 0
                : Math.max(0, Duration.between(r.getStartTime(), r.getSubmitTime()).getSeconds());
        int correctCount = (int) items.stream().filter(ResultVO.Item::correct).count();
        return new ResultVO(r.getId(), r.getExamTitle(), r.getUserName(), r.getAttemptNo(), r.getScore(), r.getTotalScore(),
                passScore, Integer.valueOf(1).equals(r.getPassed()), Integer.valueOf(1).equals(r.getAutoSubmit()),
                r.getStartTime(), r.getSubmitTime(), cost, showAnswer, correctCount, items);
    }

    private String write(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
