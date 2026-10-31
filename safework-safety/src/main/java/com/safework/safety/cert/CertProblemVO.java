package com.safework.safety.cert;

import java.util.List;

/**
 * 开票资质校验结果中的一个人
 *
 * @param role     APPLICANT / GUARDIAN
 * @param roleName 作业负责人 / 监护人
 * @param userName 姓名
 * @param problems 不合格的原因，如“没有【有限空间作业安全培训合格证】”
 */
public record CertProblemVO(String role, String roleName, String userName, List<String> problems) {
}
