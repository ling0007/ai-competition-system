package com.eliza.aicompetition.service;

import com.eliza.aicompetition.common.AiDiagnosticCode;
import com.eliza.aicompetition.config.LlmProperties;
import com.eliza.aicompetition.dto.ai.AiParseResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Phase63LongNoticeTests {
    private AiService service(RestTemplate http) {
        return new AiService(http, new LlmProperties("https://unused.invalid", "test-key",
            "text", 0.1, 2048, "vision", 4096), new ObjectMapper());
    }

    @Test
    void laterPageFieldsAndDuplicateMaterialsAreMerged() {
        RestTemplate http = mock(RestTemplate.class);
        AtomicInteger calls = new AtomicInteger();
        when(http.postForEntity(anyString(), any(), eq(Map.class))).thenAnswer(invocation -> {
            int index = calls.incrementAndGet();
            String json = index == 1
                ? "{\"title\":\"比赛通知\",\"organizer\":\"主办单位\",\"deadline\":null,\"targetGroup\":null,\"keyPoints\":null,\"materials\":[{\"name\":\"项目计划书\",\"description\":null,\"isRequired\":false}]}"
                : "{\"title\":null,\"organizer\":null,\"deadline\":\"2026-02-02 18:00\",\"targetGroup\":null,\"keyPoints\":null,\"materials\":[{\"name\":\"项目计划书\",\"description\":null,\"isRequired\":true},{\"name\":\"Beta版程序\",\"description\":null,\"isRequired\":true}]}";
            return ResponseEntity.ok(Map.of("choices", List.of(Map.of("message", Map.of("content", json)))));
        });
        String text = "【PDF第1页】\n" + "甲".repeat(3900) + "\n【PDF第5页】\n" + "乙".repeat(3900);
        var call = service(http).parseNoticeMeasured(text);
        assertFalse(call.degraded());
        assertTrue(call.callCount() >= 2);
        AiParseResult result = call.result();
        assertEquals("2026-02-02 18:00", result.deadline());
        assertEquals(2, result.materials().size());
        assertTrue(result.materials().get(0).isRequired());
        assertTrue(result.materials().get(1).description().contains("PDF第5页"));
    }

    @Test
    void textBeyondEightChunksFailsWithoutSilentTruncation() {
        RestTemplate http = mock(RestTemplate.class);
        var call = service(http).parseNoticeMeasured("文".repeat(33_000));
        assertTrue(call.degraded());
        assertEquals(AiDiagnosticCode.INPUT_TOO_LONG, call.fallbackReason());
        assertEquals(0, call.callCount());
        verifyNoInteractions(http);
    }

    @Test
    void firstPageMultilineFormalTitleRestoresThemeOmittedByModel() {
        RestTemplate http = mock(RestTemplate.class);
        String shortened = "关于举办第十九届全国大学生软件创新大赛参赛通知";
        when(http.postForEntity(anyString(), any(), eq(Map.class))).thenReturn(ResponseEntity.ok(Map.of(
            "choices", List.of(Map.of("message", Map.of("content",
                "{\"title\":\"" + shortened + "\",\"organizer\":null,\"deadline\":null,"
                    + "\"targetGroup\":null,\"keyPoints\":null,\"materials\":[]}"))))));
        String text = """
            【PDF第1页】
            示范性软件学院联盟
            关于举办第十九届全国大学生软件创新大赛
            “软件定义世界，创新引领未来”
            参赛通知
            为了进一步提升大学生创新思维。
            """;

        var result = service(http).parseNoticeMeasured(text).result();

        assertEquals("关于举办第十九届全国大学生软件创新大赛“软件定义世界，创新引领未来”参赛通知", result.title());
    }
}
