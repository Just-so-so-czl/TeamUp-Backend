package com.czl.teamupbackend.model.mongo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 团队在协作过程中沉淀的工作画像，不存储小组主数据、任务或任务进度。
 */
@Data
@Document(collection = "team_work_profiles")
public class TeamWorkProfileDoc {

    @Id
    private String id;

    @Version
    private Long version;

    @Indexed(unique = true)
    private Long teamId;

    private List<MemberWorkProfile> memberWorkProfiles = new ArrayList<>();

    private List<WorkProfileFact> teamWorkingAgreements = new ArrayList<>();

    private List<WorkProfileFact> sharedConventions = new ArrayList<>();

    private List<WorkProfileFact> recurringCollaborationRisks = new ArrayList<>();

    private List<WorkProfileFact> retrospectiveInsights = new ArrayList<>();

    private List<WorkProfileFact> openCoordinationTopics = new ArrayList<>();

    private List<WorkProfileFact> extraMemory = new ArrayList<>();

    /** 已成功处理的来源，按 sourceType + sourceId + sourceHash 幂等。 */
    private List<ProcessedSource> processedSources = new ArrayList<>();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Data
    public static class MemberWorkProfile {
        private Long userId;
        private List<String> skillTags = new ArrayList<>();
        private List<String> preferredWorkTypes = new ArrayList<>();
        private List<String> availabilityWindows = new ArrayList<>();
        private String communicationPreference;
        private String feedbackPreference;
        private List<String> selfDeclaredConstraints = new ArrayList<>();
        private String status;
        private Double confidence;
        private LocalDateTime validUntil;
        private List<SourceRef> sourceRefs = new ArrayList<>();
        private LocalDateTime updatedAt;
    }

    @Data
    public static class WorkProfileFact {
        private String key;
        private String content;
        private String status;
        private Double confidence;
        private List<SourceRef> sourceRefs = new ArrayList<>();
        private LocalDateTime updatedAt;
    }

    @Data
    public static class SourceRef {
        private String sourceType;
        private String sourceId;
        private String sourceHash;
        private LocalDateTime extractedAt;
    }

    @Data
    public static class ProcessedSource {
        private String sourceType;
        private String sourceId;
        private String sourceHash;
        private LocalDateTime processedAt;
    }
}
