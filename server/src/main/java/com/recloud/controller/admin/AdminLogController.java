package com.recloud.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.recloud.common.entity.OperationLog;
import com.recloud.common.result.R;
import com.recloud.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "管理端-操作日志", description = "操作日志查看")
@RestController
@RequestMapping("/admin/logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminLogController {

    private final OperationLogService operationLogService;

    @Operation(summary = "操作日志列表（多维度筛选）")
    @GetMapping
    public R<IPage<OperationLog>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return R.ok(operationLogService.listLogs(page, size, module,
                operator, status, keyword, startTime, endTime));
    }

    @Operation(summary = "获取操作人列表（用于下拉筛选）")
    @GetMapping("/operators")
    public R<List<String>> operators() {
        return R.ok(operationLogService.listOperators());
    }
}
