package com.czl.teamupbackend.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 小组知识库文档元数据表
 * </p>
 *
 * @author czl
 * @since 2026-04-28
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("document")
public class Document implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 雪花ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 小组ID
     */
    @TableField("team_id")
    private Long teamId;

    /**
     * 文档标题
     */
    @TableField("title")
    private String title;

    /**
     * 文档业务类型：1-资料文档 2-协作文档快照 3-Agent知识库文档
     */
    @TableField("type")
    private Integer type;

    /**
     * 对象存储路径（OSS Key/URL）
     */
    @TableField("storage_path")
    private String storagePath;

    /**
     * 文件类型：pdf/docx/md/txt等
     */
    @TableField("file_type")
    private String fileType;

    /**
     * 文件大小(字节)
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * 上传人ID
     */
    @TableField("creator_id")
    private Long creatorId;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;


}
