package com.safework.system.excel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Excel 导入结果：有任何一行出错就全部不导入，把所有错误一次性返回，用户改完再导 */
@Data
public class ImportResult {
    private int total;
    private int success;
    private List<RowError> errors = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowError {
        /** Excel 里的行号（和用户在 Excel 左侧看到的一致，表头是第 1 行） */
        private int row;
        private String message;
    }

    public void addError(int row, String message) {
        errors.add(new RowError(row, message));
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
