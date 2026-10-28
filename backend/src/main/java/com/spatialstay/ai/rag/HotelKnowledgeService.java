package com.spatialstay.ai.rag;

import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBean(VectorStore.class)
public class HotelKnowledgeService {

    private final VectorStore vectorStore;

    public HotelKnowledgeService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void addKnowledge(
            String hotelSlug,
            String category,
            String content
    ) {
        if (
                hotelSlug == null ||
                hotelSlug.isBlank() ||
                content == null ||
                content.isBlank()
        ) {
            return;
        }

        Document document = new Document(
                content.trim(),
                Map.of(
                        "hotelSlug",
                        hotelSlug.trim(),
                        "category",
                        category == null
                                ? "general"
                                : category.trim()
                )
        );

        vectorStore.add(List.of(document));
    }

    public List<Document> search(
            String hotelSlug,
            String question
    ) {
        String resolvedHotelSlug =
                requireText(
                        hotelSlug,
                        "Hotel slug is required."
                );

        String resolvedQuestion =
                requireText(
                        question,
                        "Knowledge question is required."
                );

        SearchRequest request =
                SearchRequest.builder()
                        .query(resolvedQuestion)
                        .topK(4)
                        .similarityThreshold(0.65)
                        .filterExpression(
                                "hotelSlug == '" +
                                        escapeFilterValue(
                                                resolvedHotelSlug
                                        ) +
                                        "'"
                        )
                        .build();

        List<Document> documents =
                vectorStore.similaritySearch(
                        request
                );

        return documents == null
                ? List.of()
                : documents;
    }

    private String requireText(
            String value,
            String message
    ) {
        if (
                value == null ||
                value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    message
            );
        }

        return value.trim();
    }

    private String escapeFilterValue(
            String value
    ) {
        return value.replace(
                "'",
                "''"
        );
    }
}