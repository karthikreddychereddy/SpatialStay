package com.spatialstay.ai;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.Map;

import jakarta.servlet.http.HttpSession;

import java.util.ArrayDeque;
import java.util.Deque;

@RestController
@RequestMapping("/api/ai")
@ConditionalOnProperty(
        name = "app.ai.enabled",
        havingValue = "true"
)
public class AiController {

    private final AiSupervisor aiSupervisor;
    private final AiConversationContext conversationContext;

    public AiController(
            AiSupervisor aiSupervisor,
            AiConversationContext conversationContext
    ) {
        this.aiSupervisor = aiSupervisor;
        this.conversationContext = conversationContext;
    }

    private static final String AI_HISTORY_SESSION_KEY =
        "SPATIALSTAY_AI_HISTORY";

    @PostMapping("/chat")
    public AiChatModels.ChatResponse chat(
            @Valid @RequestBody AiChatModels.ChatRequest request,
            HttpSession session
    ) {
        Deque<AiConversationContext.ConversationMessage> history =
                getHistory(session);

        AiChatModels.SupervisorResponse response =
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

        session.setAttribute(
                AI_HISTORY_SESSION_KEY,
                history
        );

        return new AiChatModels.ChatResponse(
                response.message(),
                response.responseType(),
                response.hotels(),
                response.rooms(),
                response.availability()
        );
    }

    @DeleteMapping("/conversation")
    public AiChatModels.ConversationResetResponse resetConversation(
            HttpSession session
    ) {
        session.removeAttribute(
                AI_HISTORY_SESSION_KEY
        );

        return new AiChatModels.ConversationResetResponse(
                "AI conversation cleared."
        );
    }

    @ExceptionHandler(
            IllegalArgumentException.class
    )
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

    @ExceptionHandler(
            IllegalStateException.class
    )
    public ResponseEntity<Map<String, String>>
    handleUnavailable(
            IllegalStateException exception
    ) {

        return ResponseEntity
                .status(
                        HttpStatus.SERVICE_UNAVAILABLE
                )
                .body(
                        Map.of(
                                "error",
                                "AI_UNAVAILABLE",
                                "message",
                                safeMessage(
                                        exception,
                                        "SpatialStay AI is temporarily unavailable."
                                )
                        )
                );
    }
    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<Map<String, String>> handleProviderUnavailable(
            AiProviderException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(
                        Map.of(
                                "error",
                                "AI_UNAVAILABLE",
                                "message",
                                "SpatialStay AI is temporarily unavailable. Please try again later."
                        )
                );
    }

    @SuppressWarnings("unchecked")
    private Deque<AiConversationContext.ConversationMessage> getHistory(
            HttpSession session
    ) {
        Object existing =
                session.getAttribute(AI_HISTORY_SESSION_KEY);

        if (existing instanceof Deque<?> deque) {
            return (Deque<AiConversationContext.ConversationMessage>) deque;
        }

        Deque<AiConversationContext.ConversationMessage> history =
                conversationContext.newHistory();

        session.setAttribute(
                AI_HISTORY_SESSION_KEY,
                history
        );

        return history;
    }

    private String safeMessage(
            Exception exception,
            String fallback
    ) {

        String message =
                exception.getMessage();

        if (
                message == null ||
                message.isBlank()
        ) {
            return fallback;
        }

        return message;
    }
}