package com.spatialstay.ai.rag;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.rag.seed-development-data",
        havingValue = "true"
)
public class HotelKnowledgeSeeder implements CommandLineRunner {

    private final HotelKnowledgeService knowledgeService;

    public HotelKnowledgeSeeder(
            HotelKnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Override
    public void run(String... args) {

        List<KnowledgeSeed> seeds = List.of(

                new KnowledgeSeed(
                        "skyline-grand",
                        "CHECK_IN",
                        """
                        Skyline Grand's standard check-in time is 2:00 PM.
                        Guests expecting to arrive late should inform the
                        property in advance. Late arrival guidance does not
                        guarantee early or late check-in availability.
                        """
                ),

                new KnowledgeSeed(
                        "skyline-grand",
                        "PETS",
                        """
                        Skyline Grand does not allow pets inside guest rooms.
                        Service-animal requirements should be discussed
                        directly with the property before arrival.
                        """
                ),

                new KnowledgeSeed(
                        "skyline-grand",
                        "PARKING",
                        """
                        Skyline Grand provides on-site parking for registered
                        hotel guests. Parking availability can vary and should
                        not be interpreted as a room reservation.
                        """
                ),

                new KnowledgeSeed(
                        "skyline-grand",
                        "ACCESSIBILITY",
                        """
                        Skyline Grand provides elevator access to guest floors.
                        Guests requiring specific accessibility arrangements
                        should confirm their individual requirements with the
                        property before arrival.
                        """
                ),

                new KnowledgeSeed(
                        "azure-bay",
                        "CHECK_IN",
                        """
                        Azure Bay's standard check-in time is 3:00 PM.
                        Guests arriving substantially later should notify
                        the property in advance.
                        """
                ),

                new KnowledgeSeed(
                        "ocean-vista",
                        "CHECK_IN",
                        """
                        Ocean Vista's standard check-in time is 2:00 PM.
                        Special arrival arrangements should be confirmed
                        with the property.
                        """
                )
        );

        for (KnowledgeSeed seed : seeds) {
            knowledgeService.addKnowledge(
                    seed.hotelSlug(),
                    seed.category(),
                    seed.content()
            );
        }
    }

    private record KnowledgeSeed(
            String hotelSlug,
            String category,
            String content) {
    }
}