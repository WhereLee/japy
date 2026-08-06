package com.japy.module.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 小说审核记录（每次扫描一条，全程留痕） */
@Data
@TableName("jf_novel_audit")
public class NovelAudit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long novelId;
    private String auditType;   // UPLOAD上传 / RESCAN重扫
    private String ruleHits;    // JSON: [{"word":"xxx","category":"色情","count":3}]
    private String result;      // PENDING待审 PASS通过 REJECT驳回 TAKEDOWN下架
    private Long auditorId;     // 处理人
    private LocalDateTime auditTime;
    private String remark;
    private LocalDateTime createTime;
}
