package com.spatialstay.ai;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/internal/ai/voice")
@ConditionalOnProperty(
        name = "app.ai.enabled",
        havingValue = "true"
)
public class AiVoiceController {

    private static final int MAX_VOICE_SESSIONS = 100;

    private final AiSupervisor aiSupervisor;
    private final AiConversationContext conversationContext;
    private final String voiceInternalKey;

    private final Map<
            String,
            Deque<AiConversationContext.ConversationMessage>
            > conversations = new ConcurrentHashMap<>();

    public AiVoiceController(
            AiSupervisor aiSupervisor,
            AiConversationContext conversationContext,
            @Value("${app.ai.voice.internal-key}")
            String voiceInternalKey
    ) {
        this.aiSupervisor = aiSupervisor;
        this.conversationContext = conversationContext;
        this.voiceInternalKey = voiceInternalKey;
    }

    @PostMapping
    public AiChatModels.VoiceResponse chat(
            @RequestHeader("X-SpatialStay-Voice-Key")
            String suppliedKey,
            @Valid @RequestBody
            AiChatModels.VoiceRequest request
    ) {
        requireInternalKey(suppliedKey);

        String sessionId = request.sessionId().trim();

        Deque<AiConversationContext.ConversationMessage> history =
                getHistory(sessionId);

        AiChatModels.SupervisorResponse response;

        synchronized (history) {
            response =
                    aiSupervisor.chat(
                            request.message(),
                            conversationContext.snapshot(history)
                    );

            conversationContext.addUserMessage(
                    history,
                    request.message()
            );

            conversationContext.addAssistantMessage(
                    history,
                    response.message()
            );
        }

        return new AiChatModels.VoiceResponse(
                response.message(),
                response.responseType(),
                response.hotels(),
                response.rooms(),
                response.availability()
        );
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> clearConversation(
            @RequestHeader("X-SpatialStay-Voice-Key")
            String suppliedKey,
            @PathVariable String sessionId
    ) {
        requireInternalKey(suppliedKey);

        if (sessionId != null && !sessionId.isBlank()) {
            conversations.remove(sessionId.trim());
        }

        return ResponseEntity.noContent().build();
    }

    private Deque<AiConversationContext.ConversationMessage> getHistory(
            String sessionId
    ) {
        if (!conversations.containsKey(sessionId)
                && conversations.size() >= MAX_VOICE_SESSIONS) {
            throw new IllegalStateException(
                    "Too many active voice conversations."
            );
        }

        return conversations.computeIfAbsent(
                sessionId,
                ignored -> conversationContext.newHistory()
        );
    }

    private void requireInternalKey(
            String suppliedKey
    ) {
        if (voiceInternalKey == null
                || voiceInternalKey.isBlank()) {
            throw new IllegalStateException(
                    "Voice integration is not configured."
            );
        }

        if (suppliedKey == null
                || suppliedKey.isBlank()) {
            throw new VoiceAuthenticationException();
        }

        boolean matches =
                MessageDigest.isEqual(
                        voiceInternalKey.getBytes(
                                StandardCharsets.UTF_8
                        ),
                        suppliedKey.getBytes(
                                StandardCharsets.UTF_8
                        )
                );

        if (!matches) {
            throw new VoiceAuthenticationException();
        }
    }

    @ExceptionHandler(VoiceAuthenticationException.class)
    public ResponseEntity<Map<String, String>>
    handleUnauthorized(
            VoiceAuthenticationException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(
                        Map.of(
                                "error",
                                "VOICE_UNAUTHORIZED",
                                "message",
                                "Unauthorized voice service request."
                        )
                );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>>
    handleInvalidRequest(
            IllegalArgumentException exception
    ) {
        return ResponseEntity
                .badRequest()
                .body(
                        Map.of(
                                "error",
                                "INVALID_AI_REQUEST",
                                "message",
                                safeMessage(
                                        exception,
                                        "The request could not be processed."
                                )
                        )
                );
    }

    @ExceptionHandler({
            IllegalStateException.class,
            AiProviderException.class
    })
    public ResponseEntity<Map<String, String>>
    handleUnavailable(
            Exception exception
    ) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(
                        Map.of(
                                "error",
                                "AI_UNAVAILABLE",
                                "message",
                                "SpatialStay AI is temporarily unavailable."
                        )
                );
    }

    private String safeMessage(
            Exception exception,
            String fallback
    ) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return fallback;
        }

        return message;
    }

    private static final class VoiceAuthenticationException
            extends RuntimeException {

        private VoiceAuthenticationException() {
            super("Unauthorized voice service request.");
        }
    }
}