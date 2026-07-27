package com.czl.teamupbackend.service;

import com.czl.teamupbackend.commen.exception.BizException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 对协同服务的受控内部调用。文档真实写入始终在协同服务当前 Y.Doc 内完成。
 */
@Service
@RequiredArgsConstructor
public class CollaborationAgentDocumentGateway {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Value("${collaboration-agent.internal-url:http://127.0.0.1:1235}")
    private String internalUrl;

    @Value("${collaboration-agent.internal-token:teamup-local-collaboration-agent-token}")
    private String internalToken;

    public Map<String, Object> captureSnapshot(Long documentId) {
        return post("/internal/agent-document/snapshot", Map.of("documentId", documentId), -1).payload();
    }

    public PreviewResult preview(Map<String, Object> contentJson, List<Map<String, Object>> operations) {
        GatewayResponse response = post(
            "/internal/agent-document/preview",
            Map.of("contentJson", contentJson, "operations", operations),
            400
        );
        if (response.statusCode() == 400) {
            return new PreviewResult(false, message(response.payload(), "文档草案参数不合法"), Map.of());
        }
        return new PreviewResult(true, "", response.payload());
    }

    public ApplyResult apply(Long documentId, String baseContentHash, List<Map<String, Object>> operations) {
        GatewayResponse response = post(
            "/internal/agent-document/apply",
            Map.of("documentId", documentId, "baseContentHash", baseContentHash, "operations", operations),
            409
        );
        if (response.statusCode() == 409) {
            return new ApplyResult(false, message(response.payload(), "文档已变化，草案无法安全应用"), "");
        }
        return new ApplyResult(true, "", String.valueOf(response.payload().getOrDefault("resultSummary", "文档草案已应用")));
    }

    public byte[] exportPdf(Long documentId, String title) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(internalUrl + "/internal/agent-document/export-pdf"))
                .timeout(Duration.ofSeconds(75))
                .header("Content-Type", "application/json")
                .header("X-Collaboration-Agent-Token", internalToken)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of(
                    "documentId", documentId,
                    "title", title == null ? "协作文档" : title
                ))))
                .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            Map<String, Object> errorBody = objectMapper.readValue(
                new String(response.body(), StandardCharsets.UTF_8), new TypeReference<>() { }
            );
            throw new BizException(502, "协同服务导出 PDF 失败：" + message(errorBody, "HTTP " + response.statusCode()));
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(502, "无法导出协作文档 PDF：" + describeException(exception));
        }
    }

    private GatewayResponse post(String path, Map<String, Object> payload, int allowedErrorStatus) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(internalUrl + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("X-Collaboration-Agent-Token", internalToken)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> body = objectMapper.readValue(response.body(), new TypeReference<>() { });
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new GatewayResponse(response.statusCode(), body);
            }
            if (response.statusCode() == allowedErrorStatus) {
                return new GatewayResponse(response.statusCode(), body);
            }
            throw new BizException(502, "协同服务处理文档草案失败：" + message(body, "HTTP " + response.statusCode()));
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(502, "无法连接协同服务内部 API " + internalUrl + path + "："
                + describeException(exception));
        }
    }

    private String describeException(Exception exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        String message = rootCause.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getMessage();
        }
        if (message == null || message.isBlank()) {
            message = "无错误消息";
        }
        return rootCause.getClass().getSimpleName() + " - " + message;
    }

    private String message(Map<String, Object> payload, String fallback) {
        Object value = payload == null ? null : payload.get("message");
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    public record ApplyResult(boolean applied, String conflictMessage, String resultSummary) {
    }

    public record PreviewResult(boolean valid, String validationMessage, Map<String, Object> payload) {
    }

    private record GatewayResponse(int statusCode, Map<String, Object> payload) {
    }
}
