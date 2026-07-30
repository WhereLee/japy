package com.recloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("novel")
public class Novel extends BaseEntity {
    private String title;
    private String author;
    private String description;
    private String fileName;
}
