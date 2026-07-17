package com.czl.teamupbackend.service.impl;

import com.czl.teamupbackend.event.TeamWorkProfileExtractionRequestedEvent;
import com.czl.teamupbackend.model.mongo.TeamWorkProfileDoc;
import com.czl.teamupbackend.repository.TeamWorkProfileRepository;
import com.czl.teamupbackend.service.TeamWorkProfileService;
import com.czl.teamupbackend.service.TeamRedisCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 从稳定的文档/聊天来源中提取团队工作画像。这里不保存小组资料、任务、进度或截止时间，
 * 只沉淀成员工作偏好和长期协作经验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamWorkProfileServiceImpl implements TeamWorkProfileService {

    private static final int MAX_SOURCE_CONTENT_LENGTH = 14_000;
    private static final int MAX_PROCESSED_SOURCES = 200;
    private static final int MAX_SAVE_RETRIES = 3;
    private static final String STATUS_SUGGESTED = "SUGGESTED";

    private static final String EXTRACTION_PROMPT = """
        你是 TeamUp 的团队协作记忆提取器。请从来源内容中提取“长期有价值的协作工作画像”，只输出一个合法 JSON 对象，不要 Markdown、解释或代码块。

        严禁提取或重复：小组名称/描述、正式成员角色、任务列表、任务状态、任务分配、项目目标、交付物、Deadline、文档摘要。
        只允许提取以下字段：
        1. memberWorkProfiles：成员已明确表达或有充分证据支持的工作偏好、技能标签、可用时间、沟通偏好、反馈偏好、本人声明的约束。memberWorkProfiles 必须有 userId；没有可靠 userId 时不要输出该项。
        2. teamWorkingAgreements：团队已形成的沟通、会议、决策、交接、评审协作约定。
        3. sharedConventions：长期复用的文档、代码、命名、评审或协作规范。
        4. recurringCollaborationRisks：反复出现的协作风险及已经明确的缓解方式。
        5. retrospectiveInsights：复盘中可复用的有效做法或失败教训。
        6. openCoordinationTopics：尚未决定、但需要团队协商的协作议题，不能写成任务。
        7. extraMemory：上述字段无法表达、但确实能提升后续协作建议的团队事实。

        所有内容必须严格来自来源内容，不得臆测成员性格、能力高低或隐私信息。所有 status 一律使用 SUGGESTED；只有后续由用户确认的业务流程才能升级为 CONFIRMED。每个事实必须有 key 和 content；没有可提取信息时返回所有空数组。

        输出 JSON Schema：
        {
          "memberWorkProfiles": [{"userId": 0, "skillTags": [], "preferredWorkTypes": [], "availabilityWindows": [], "communicationPreference": "", "feedbackPreference": "", "selfDeclaredConstraints": [], "status": "SUGGESTED", "confidence": 0.0}],
          "teamWorkingAgreements": [{"key": "", "content": "", "status": "SUGGESTED", "confidence": 0.0}],
          "sharedConventions": [{"key": "", "content": "", "status": "SUGGESTED", "confidence": 0.0}],
          "recurringCollaborationRisks": [{"key": "", "content": "", "status": "SUGGESTED", "confidence": 0.0}],
          "retrospectiveInsights": [{"key": "", "content": "", "status": "SUGGESTED", "confidence": 0.0}],
          "openCoordinationTopics": [{"key": "", "content": "", "status": "SUGGESTED", "confidence": 0.0}],
          "extraMemory": [{"key": "", "content": "", "status": "SUGGESTED", "confidence": 0.0}]
        }

        来源类型：%s
        来源标题：%s
        来源用户ID：%s
        来源内容：
        %s
        """;

    private final TeamWorkProfileRepository teamWorkProfileRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;
    private final TeamRedisCacheService teamRedisCacheService;

    @Value("${spring.ai.openai.summary.model}")
    private String summaryModel;

    @Override
    public void requestExtraction(
        Long teamId,
        String sourceType,
        String sourceId,
        String sourceTitle,
        Long sourceUserId,
        String sourceContent
    ) {
        String normalizedContent = normalizeContent(sourceContent);
        if (teamId == null || teamId <= 0 || !StringUtils.hasText(sourceType)
            || !StringUtils.hasText(sourceId) || !StringUtils.hasText(normalizedContent)) {
            return;
        }
        applicationEventPublisher.publishEvent(new TeamWorkProfileExtractionRequestedEvent(
            teamId,
            sourceType.trim(),
            sourceId.trim(),
            StringUtils.hasText(sourceTitle) ? sourceTitle.trim() : "未命名来源",
            sourceUserId,
            truncate(normalizedContent, MAX_SOURCE_CONTENT_LENGTH),
            sha256(normalizedContent)
        ));
    }

    @Override
    public void processExtraction(TeamWorkProfileExtractionRequestedEvent event) {
        if (event == null || event.teamId() == null || !StringUtils.hasText(event.sourceHash())) {
            return;
        }
        if (isProcessed(event.teamId(), event)) {
            log.debug("Team work profile source already processed, teamId={}, sourceType={}, sourceId={}",
                event.teamId(), event.sourceType(), event.sourceId());
            return;
        }
        ExtractionPayload extraction = extract(event);
        mergeWithRetry(event, extraction);
    }

    @Override
    public Map<String, Object> getAgentView(Long teamId) {
        Map<String, Object> cached = teamRedisCacheService.get(
            teamRedisCacheService.teamWorkProfileKey(teamId), Map.class);
        if (cached != null) {
            return cached;
        }
        Optional<TeamWorkProfileDoc> optionalProfile = teamWorkProfileRepository.findByTeamId(teamId);
        if (optionalProfile.isEmpty()) {
            Map<String, Object> result = emptyAgentView(teamId);
            teamRedisCacheService.put(teamRedisCacheService.teamWorkProfileKey(teamId), result,
                TeamRedisCacheService.TEAM_WORK_PROFILE_TTL);
            return result;
        }
        TeamWorkProfileDoc profile = optionalProfile.get();
        Map<String, Object> result = new HashMap<>();
        result.put("teamId", profile.getTeamId());
        result.put("updatedAt", profile.getUpdatedAt());
        result.put("memberWorkProfiles", memberProfilesToView(profile.getMemberWorkProfiles()));
        result.put("teamWorkingAgreements", factsToView(profile.getTeamWorkingAgreements()));
        result.put("sharedConventions", factsToView(profile.getSharedConventions()));
        result.put("recurringCollaborationRisks", factsToView(profile.getRecurringCollaborationRisks()));
        result.put("retrospectiveInsights", factsToView(profile.getRetrospectiveInsights()));
        result.put("openCoordinationTopics", factsToView(profile.getOpenCoordinationTopics()));
        result.put("extraMemory", factsToView(profile.getExtraMemory()));
        teamRedisCacheService.put(teamRedisCacheService.teamWorkProfileKey(teamId), result,
            TeamRedisCacheService.TEAM_WORK_PROFILE_TTL);
        return result;
    }

    private ExtractionPayload extract(TeamWorkProfileExtractionRequestedEvent event) {
        String prompt = String.format(
            EXTRACTION_PROMPT,
            event.sourceType(),
            event.sourceTitle(),
            event.sourceUserId() == null ? "未知" : event.sourceUserId(),
            event.sourceContent()
        );
        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .model(summaryModel)
            .temperature(0.1)
            .build();
        String content = chatClientBuilder.build()
            .prompt()
            .user(prompt)
            .options(options)
            .call()
            .content();
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("团队工作画像模型返回为空");
        }
        try {
            return objectMapper.readValue(stripCodeFence(content), ExtractionPayload.class);
        } catch (Exception e) {
            throw new IllegalStateException("团队工作画像模型返回不是合法JSON", e);
        }
    }

    private void mergeWithRetry(TeamWorkProfileExtractionRequestedEvent event, ExtractionPayload extraction) {
        for (int attempt = 1; attempt <= MAX_SAVE_RETRIES; attempt++) {
            TeamWorkProfileDoc profile = teamWorkProfileRepository.findByTeamId(event.teamId()).orElseGet(() -> newProfile(event.teamId()));
            if (hasProcessed(profile, event)) {
                return;
            }
            merge(profile, extraction, event);
            try {
                teamWorkProfileRepository.save(profile);
                teamRedisCacheService.evictTeamWorkProfileAfterCommit(event.teamId());
                log.info("Team work profile extracted, teamId={}, sourceType={}, sourceId={}",
                    event.teamId(), event.sourceType(), event.sourceId());
                return;
            } catch (OptimisticLockingFailureException | DuplicateKeyException e) {
                if (attempt == MAX_SAVE_RETRIES) {
                    throw e;
                }
                log.debug("Retry team work profile merge, teamId={}, attempt={}", event.teamId(), attempt);
            }
        }
    }

    private void merge(TeamWorkProfileDoc profile, ExtractionPayload extraction, TeamWorkProfileExtractionRequestedEvent event) {
        LocalDateTime now = LocalDateTime.now();
        TeamWorkProfileDoc.SourceRef sourceRef = sourceRef(event, now);
        mergeMemberProfiles(profile.getMemberWorkProfiles(), extraction.getMemberWorkProfiles(), sourceRef, now);
        mergeFacts(profile.getTeamWorkingAgreements(), extraction.getTeamWorkingAgreements(), sourceRef, now);
        mergeFacts(profile.getSharedConventions(), extraction.getSharedConventions(), sourceRef, now);
        mergeFacts(profile.getRecurringCollaborationRisks(), extraction.getRecurringCollaborationRisks(), sourceRef, now);
        mergeFacts(profile.getRetrospectiveInsights(), extraction.getRetrospectiveInsights(), sourceRef, now);
        mergeFacts(profile.getOpenCoordinationTopics(), extraction.getOpenCoordinationTopics(), sourceRef, now);
        mergeFacts(profile.getExtraMemory(), extraction.getExtraMemory(), sourceRef, now);
        profile.getProcessedSources().add(processedSource(event, now));
        if (profile.getProcessedSources().size() > MAX_PROCESSED_SOURCES) {
            profile.setProcessedSources(new ArrayList<>(profile.getProcessedSources().subList(
                profile.getProcessedSources().size() - MAX_PROCESSED_SOURCES,
                profile.getProcessedSources().size()
            )));
        }
        profile.setUpdatedAt(now);
    }

    private void mergeMemberProfiles(
        List<TeamWorkProfileDoc.MemberWorkProfile> targets,
        List<TeamWorkProfileDoc.MemberWorkProfile> candidates,
        TeamWorkProfileDoc.SourceRef sourceRef,
        LocalDateTime now
    ) {
        if (candidates == null) {
            return;
        }
        for (TeamWorkProfileDoc.MemberWorkProfile candidate : candidates) {
            if (candidate == null || candidate.getUserId() == null || candidate.getUserId() <= 0) {
                continue;
            }
            TeamWorkProfileDoc.MemberWorkProfile target = targets.stream()
                .filter(item -> candidate.getUserId().equals(item.getUserId()))
                .findFirst()
                .orElseGet(() -> {
                    TeamWorkProfileDoc.MemberWorkProfile created = new TeamWorkProfileDoc.MemberWorkProfile();
                    created.setUserId(candidate.getUserId());
                    targets.add(created);
                    return created;
                });
            target.setSkillTags(mergeTexts(target.getSkillTags(), candidate.getSkillTags()));
            target.setPreferredWorkTypes(mergeTexts(target.getPreferredWorkTypes(), candidate.getPreferredWorkTypes()));
            target.setAvailabilityWindows(mergeTexts(target.getAvailabilityWindows(), candidate.getAvailabilityWindows()));
            target.setSelfDeclaredConstraints(mergeTexts(target.getSelfDeclaredConstraints(), candidate.getSelfDeclaredConstraints()));
            target.setCommunicationPreference(preferExisting(target.getCommunicationPreference(), candidate.getCommunicationPreference()));
            target.setFeedbackPreference(preferExisting(target.getFeedbackPreference(), candidate.getFeedbackPreference()));
            target.setStatus(mergeStatus(target.getStatus()));
            target.setConfidence(maxConfidence(target.getConfidence(), candidate.getConfidence()));
            target.setSourceRefs(addSourceRef(target.getSourceRefs(), sourceRef));
            target.setUpdatedAt(now);
        }
    }

    private void mergeFacts(
        List<TeamWorkProfileDoc.WorkProfileFact> targets,
        List<TeamWorkProfileDoc.WorkProfileFact> candidates,
        TeamWorkProfileDoc.SourceRef sourceRef,
        LocalDateTime now
    ) {
        if (candidates == null) {
            return;
        }
        for (TeamWorkProfileDoc.WorkProfileFact candidate : candidates) {
            if (candidate == null || !StringUtils.hasText(candidate.getContent())) {
                continue;
            }
            String key = StringUtils.hasText(candidate.getKey()) ? candidate.getKey().trim() : "general";
            String content = candidate.getContent().trim();
            TeamWorkProfileDoc.WorkProfileFact target = targets.stream()
                .filter(item -> normalizeFact(item.getKey(), item.getContent()).equals(normalizeFact(key, content)))
                .findFirst()
                .orElseGet(() -> {
                    TeamWorkProfileDoc.WorkProfileFact created = new TeamWorkProfileDoc.WorkProfileFact();
                    created.setKey(key);
                    created.setContent(content);
                    targets.add(created);
                    return created;
                });
            target.setStatus(mergeStatus(target.getStatus()));
            target.setConfidence(maxConfidence(target.getConfidence(), candidate.getConfidence()));
            target.setSourceRefs(addSourceRef(target.getSourceRefs(), sourceRef));
            target.setUpdatedAt(now);
        }
    }

    private boolean isProcessed(Long teamId, TeamWorkProfileExtractionRequestedEvent event) {
        return teamWorkProfileRepository.findByTeamId(teamId)
            .map(profile -> hasProcessed(profile, event))
            .orElse(false);
    }

    private boolean hasProcessed(TeamWorkProfileDoc profile, TeamWorkProfileExtractionRequestedEvent event) {
        return profile.getProcessedSources() != null && profile.getProcessedSources().stream().anyMatch(source ->
            event.sourceType().equals(source.getSourceType())
                && event.sourceId().equals(source.getSourceId())
                && event.sourceHash().equals(source.getSourceHash())
        );
    }

    private TeamWorkProfileDoc newProfile(Long teamId) {
        TeamWorkProfileDoc profile = new TeamWorkProfileDoc();
        profile.setTeamId(teamId);
        profile.setCreatedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        return profile;
    }

    private TeamWorkProfileDoc.SourceRef sourceRef(TeamWorkProfileExtractionRequestedEvent event, LocalDateTime now) {
        TeamWorkProfileDoc.SourceRef sourceRef = new TeamWorkProfileDoc.SourceRef();
        sourceRef.setSourceType(event.sourceType());
        sourceRef.setSourceId(event.sourceId());
        sourceRef.setSourceHash(event.sourceHash());
        sourceRef.setExtractedAt(now);
        return sourceRef;
    }

    private TeamWorkProfileDoc.ProcessedSource processedSource(TeamWorkProfileExtractionRequestedEvent event, LocalDateTime now) {
        TeamWorkProfileDoc.ProcessedSource source = new TeamWorkProfileDoc.ProcessedSource();
        source.setSourceType(event.sourceType());
        source.setSourceId(event.sourceId());
        source.setSourceHash(event.sourceHash());
        source.setProcessedAt(now);
        return source;
    }

    private List<String> mergeTexts(List<String> existing, List<String> additions) {
        List<String> result = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
        if (additions == null) {
            return result;
        }
        for (String value : additions) {
            if (StringUtils.hasText(value) && result.stream().noneMatch(item -> item.equalsIgnoreCase(value.trim()))) {
                result.add(value.trim());
            }
        }
        return result;
    }

    private List<TeamWorkProfileDoc.SourceRef> addSourceRef(
        List<TeamWorkProfileDoc.SourceRef> existing,
        TeamWorkProfileDoc.SourceRef sourceRef
    ) {
        List<TeamWorkProfileDoc.SourceRef> result = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
        boolean alreadyExists = result.stream().anyMatch(item -> sourceRef.getSourceType().equals(item.getSourceType())
            && sourceRef.getSourceId().equals(item.getSourceId()) && sourceRef.getSourceHash().equals(item.getSourceHash()));
        if (!alreadyExists) {
            result.add(sourceRef);
        }
        return result;
    }

    private String preferExisting(String existing, String candidate) {
        return StringUtils.hasText(existing) ? existing : StringUtils.hasText(candidate) ? candidate.trim() : null;
    }

    private String mergeStatus(String existing) {
        return StringUtils.hasText(existing) ? existing : STATUS_SUGGESTED;
    }

    private Double maxConfidence(Double existing, Double candidate) {
        double existingValue = existing == null ? 0D : existing;
        double candidateValue = candidate == null ? 0D : Math.max(0D, Math.min(1D, candidate));
        return Math.max(existingValue, candidateValue);
    }

    private String normalizeFact(String key, String content) {
        return (key == null ? "" : key.trim()).toLowerCase(Locale.ROOT) + "|"
            + (content == null ? "" : content.trim()).toLowerCase(Locale.ROOT);
    }

    private String normalizeContent(String content) {
        return content == null ? "" : content.replace("\u0000", "").trim();
    }

    private String truncate(String content, int maxLength) {
        return content.length() <= maxLength ? content : content.substring(0, maxLength);
    }

    private String sha256(String content) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String stripCodeFence(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("```")) {
            int lineEnd = trimmed.indexOf('\n');
            trimmed = lineEnd >= 0 ? trimmed.substring(lineEnd + 1) : trimmed;
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }

    private Map<String, Object> emptyAgentView(Long teamId) {
        Map<String, Object> result = new HashMap<>();
        result.put("teamId", teamId);
        result.put("memberWorkProfiles", List.of());
        result.put("teamWorkingAgreements", List.of());
        result.put("sharedConventions", List.of());
        result.put("recurringCollaborationRisks", List.of());
        result.put("retrospectiveInsights", List.of());
        result.put("openCoordinationTopics", List.of());
        result.put("extraMemory", List.of());
        return result;
    }

    private List<Map<String, Object>> memberProfilesToView(List<TeamWorkProfileDoc.MemberWorkProfile> profiles) {
        if (profiles == null) {
            return List.of();
        }
        return profiles.stream().map(profile -> {
            Map<String, Object> item = new HashMap<>();
            item.put("userId", profile.getUserId());
            item.put("skillTags", profile.getSkillTags());
            item.put("preferredWorkTypes", profile.getPreferredWorkTypes());
            item.put("availabilityWindows", profile.getAvailabilityWindows());
            item.put("communicationPreference", profile.getCommunicationPreference());
            item.put("feedbackPreference", profile.getFeedbackPreference());
            item.put("selfDeclaredConstraints", profile.getSelfDeclaredConstraints());
            item.put("status", profile.getStatus());
            item.put("confidence", profile.getConfidence());
            return item;
        }).toList();
    }

    private List<Map<String, Object>> factsToView(List<TeamWorkProfileDoc.WorkProfileFact> facts) {
        if (facts == null) {
            return List.of();
        }
        return facts.stream().map(fact -> {
            Map<String, Object> item = new HashMap<>();
            item.put("key", fact.getKey());
            item.put("content", fact.getContent());
            item.put("status", fact.getStatus());
            item.put("confidence", fact.getConfidence());
            return item;
        }).toList();
    }

    @Data
    private static class ExtractionPayload {
        private List<TeamWorkProfileDoc.MemberWorkProfile> memberWorkProfiles = new ArrayList<>();
        private List<TeamWorkProfileDoc.WorkProfileFact> teamWorkingAgreements = new ArrayList<>();
        private List<TeamWorkProfileDoc.WorkProfileFact> sharedConventions = new ArrayList<>();
        private List<TeamWorkProfileDoc.WorkProfileFact> recurringCollaborationRisks = new ArrayList<>();
        private List<TeamWorkProfileDoc.WorkProfileFact> retrospectiveInsights = new ArrayList<>();
        private List<TeamWorkProfileDoc.WorkProfileFact> openCoordinationTopics = new ArrayList<>();
        private List<TeamWorkProfileDoc.WorkProfileFact> extraMemory = new ArrayList<>();
    }
}
