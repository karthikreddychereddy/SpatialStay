package com.spatialstay.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@Component
public class AiConversationContext {

    private static final int MAX_MESSAGES = 10;

    public record ConversationMessage(
            Role role,
            String content
    ) {
    }

    public enum Role {
        USER,
        ASSISTANT
    }

    public void addUserMessage(
            Deque<ConversationMessage> history,
            String message
    ) {
        add(history, Role.USER, message);
    }

    public void addAssistantMessage(
            Deque<ConversationMessage> history,
            String message
    ) {
        add(history, Role.ASSISTANT, message);
    }

    public List<ConversationMessage> snapshot(
            Deque<ConversationMessage> history
    ) {
        return List.copyOf(history);
    }

    public Deque<ConversationMessage> newHistory() {
        return new ArrayDeque<>();
    }

    private void add(
            Deque<ConversationMessage> history,
            Role role,
            String message
    ) {
        if (message == null || message.isBlank()) {
            return;
        }

        history.addLast(
                new ConversationMessage(
                        role,
                        message.trim()
                )
        );

        while (history.size() > MAX_MESSAGES) {
            history.removeFirst();
        }
    }
}