package com.czl.teamupbackend.model.mongo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 文档助手生成草案时冻结的协作文档基线，仅用于预览、冲突校验和审计。
 */
@Data
@Document(collection = "agent_collaboration_snapshots")
public class AgentCollaborationSnapshotDoc {

    @Id
    private String id;

    private Long runId;
    private Long teamId;
    private Long documentId;
    private Long creatorUserId;
    private String baseContentHash;
    private String baseStateVector;
    private String plainText;
    private Map<String, Object> contentJson;
    private List<Map<String, Object>> blocks;
    private LocalDateTime createdAt;
}
