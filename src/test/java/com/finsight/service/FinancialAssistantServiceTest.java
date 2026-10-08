package com.finsight.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import tools.jackson.databind.ObjectMapper;
import com.finsight.exception.TooManyRequestsException;

class FinancialAssistantServiceTest {
    @Test
    void disabledAssistantDoesNotReadOrSendFinancialContext() {
        FinancialAssistantContextService context = mock(FinancialAssistantContextService.class);
        AssistantRateLimiter limiter = mock(AssistantRateLimiter.class);
        FinancialAssistantService service = new FinancialAssistantService("", "gemini-2.5-flash", limiter, context,
                new ObjectMapper(), mock(HttpClient.class));

        var response = service.ask("user@example.com", "How am I doing?");

        assertEquals("DISABLED", response.status());
        verifyNoInteractions(limiter);
        verifyNoInteractions(context);
    }

    @Test
    void sendsQuestionToGeminiAndParsesCandidateText() throws Exception {
        FinancialAssistantContextService context = mock(FinancialAssistantContextService.class);
        AssistantRateLimiter limiter = mock(AssistantRateLimiter.class);
        HttpClient client = mock(HttpClient.class);
        ObjectMapper mapper = new ObjectMapper();
        @SuppressWarnings("unchecked")
        HttpResponse<String> providerResponse = mock(HttpResponse.class);
        when(context.readOnlyContext("user@example.com")).thenReturn(java.util.Map.of("monthlySpend", 125));
        when(providerResponse.statusCode()).thenReturn(200);
        when(providerResponse.body()).thenReturn("""
                {"candidates":[{"content":{"parts":[{"text":"Your recent spend is stable."}]}}]}
                """);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(providerResponse);
        FinancialAssistantService service = new FinancialAssistantService("test-key", "gemini-test-model",
                limiter, context, mapper, client);

        var response = service.ask("user@example.com", "How is my spending?");

        assertEquals("READY", response.status());
        assertEquals("Your recent spend is stable.", response.answer());
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requestCaptor.getValue();
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-test-model:generateContent",
                request.uri().toString());
        assertEquals("test-key", request.headers().firstValue("x-goog-api-key").orElseThrow());
        var body = mapper.readTree(readRequestBody(request));
        assertEquals(600, body.path("generationConfig").path("maxOutputTokens").asInt());
        assertTrue(body.path("contents").get(0).path("parts").get(0).path("text").asText()
                .startsWith("User question (untrusted):\nHow is my spending?\n\nRead-only financial context (JSON):"));
        assertTrue(body.path("systemInstruction").path("parts").get(0).path("text").asText()
                .contains("fixed read-only finance calculations"));
        verify(context).readOnlyContext("user@example.com");
    }

    private String readRequestBody(HttpRequest request) throws Exception {
        CompletableFuture<String> body = new CompletableFuture<>();
        StringBuilder content = new StringBuilder();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                content.append(new String(bytes, StandardCharsets.UTF_8));
            }

            @Override
            public void onError(Throwable throwable) {
                body.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                body.complete(content.toString());
            }
        });
        return body.get(1, TimeUnit.SECONDS);
    }

    @Test
    void appliesPerUserRateLimitBeforeCallingProvider() {
        AssistantRateLimiter limiter = new AssistantRateLimiter(1);
        limiter.acquire("user@example.com");
        assertThrows(TooManyRequestsException.class, () -> limiter.acquire("user@example.com"));
    }
}
