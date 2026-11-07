package com.safework.safety.point;

import com.safework.safety.entity.PointRecord;
import com.safework.safety.entity.PointRedeem;
import com.safework.safety.entity.PointReward;

import java.util.List;

/** 隐患随手拍积分的返回对象 */
public final class PointVOs {

    private PointVOs() {
    }

    /** 排行榜一行：名次、员工、部门、期内得分、期内被确认的隐患数 */
    public record RankRow(int rank, Long userId, String userName, String deptName, int points, int hazards) {
    }

    public record Ranking(String period, String label, List<RankRow> rows, int totalPoints, int totalHazards) {
    }

    /**
     * 我的积分
     *
     * @param monthRank 本月名次，没得分为 null
     */
    public record Mine(int balance, int earned, int monthPoints, Integer monthRank, Integer yearRank, List<PointRecord> records,
                       List<PointRedeem> redeems) {
    }

    public record RecordPage(List<PointRecord> records, long total) {
    }

    /** 奖品 + 我能不能兑换（积分够、有库存） */
    public record RewardRow(PointReward reward, boolean affordable) {
    }

    /** 总览：今年发出的积分、被确认的隐患、兑换待发放、已发放金额 */
    public record Overview(int yearPoints, int yearHazards, int reporters, int pending, int issued, java.math.BigDecimal issuedAmount) {
    }
}
