package com.safework.ticket.resp;

import com.safework.ticket.entity.RespBook;
import com.safework.ticket.entity.RespSign;

import java.util.List;

/** 安全生产责任制的返回对象 */
public final class RespVOs {

    private RespVOs() {
    }

    /** 岗位 + 人员数 + 职责 */
    public record PostDuty(Long postId, String postName, Integer status, int memberCount, List<String> duties, String updaterName) {
    }

    /** 责任书 + 签署、考核进度 */
    public record Book(RespBook book, int total, int signed, int late, int assessed) {
    }

    /** 一份责任书的完整内容（本人签署、管理员查看时用），签名图片只在这里返回 */
    public record SignDetail(RespBook book, RespSign sign, List<String> duties, String signImage, boolean canSign) {
    }

    /**
     * 考核参考数据：这个人在这一年里的安全表现，从各模块统计出来，给考核打分做参考
     *
     * @param hazardsReported 上报隐患
     * @param rectifyTotal    负责整改的隐患（期限在这一年）
     * @param rectifyOnTime   其中按期提交整改的
     * @param checkTotal      已到期的安全检查任务
     * @param checkOnTime     其中按时完成的
     * @param ticketsDone     作为申请人或监护人完成的作业票
     */
    public record Reference(int hazardsReported, int rectifyTotal, int rectifyOnTime, Integer rectifyRate,
                            int checkTotal, int checkOnTime, Integer checkRate, int ticketsDone) {
    }

    public record SignPage(List<RespSign> records, long total) {
    }
}
