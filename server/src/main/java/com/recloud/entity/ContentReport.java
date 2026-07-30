package com.recloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("content_report")
public class ContentReport extends BaseEntity {

    /** 举报人ID */
    private Long reporterId;

    /** 举报目标类型：annotation / comment */
    private String targetType;

    /** 举报目标ID */
    private Long targetId;

    /** 举报原因 */
    private String reason;

    /** 状态：pending=待处理 / resolved=已处理 / rejected=已驳回 */
    private String status;

    /** 处理人ID */
    private Long handlerId;

    /** 处理备注 */
    private String handleNote;
}
