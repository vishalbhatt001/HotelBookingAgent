package com.enterprise.booking.config;

import com.enterprise.booking.agent.toolcalling.PricingToolCallingAiService;
import com.enterprise.booking.agent.toolcalling.ProfileToolCallingAiService;
import com.enterprise.booking.agent.toolcalling.RecommendationToolCallingAiService;
import com.enterprise.booking.agent.toolcalling.SupervisorPlanningTools;
import com.enterprise.booking.agent.toolcalling.SupervisorToolCallingAiService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolCallingConfig {

    @Bean
    public SupervisorToolCallingAiService supervisorToolCallingAiService(
            ChatModel chatModel,
            SupervisorPlanningTools tools
    ) {
        return AiServices.builder(SupervisorToolCallingAiService.class)
                .chatModel(chatModel)
                .tools(tools)
                .build();
    }

    @Bean
    public PricingToolCallingAiService pricingToolCallingAiService(
            ChatModel chatModel,
            SupervisorPlanningTools tools
    ) {
        return AiServices.builder(PricingToolCallingAiService.class)
                .chatModel(chatModel)
                .tools(tools)
                .build();
    }

    @Bean
    public RecommendationToolCallingAiService recommendationToolCallingAiService(
            ChatModel chatModel,
            SupervisorPlanningTools tools
    ) {
        return AiServices.builder(RecommendationToolCallingAiService.class)
                .chatModel(chatModel)
                .tools(tools)
                .build();
    }

    @Bean
    public ProfileToolCallingAiService profileToolCallingAiService(
            ChatModel chatModel,
            SupervisorPlanningTools tools
    ) {
        return AiServices.builder(ProfileToolCallingAiService.class)
                .chatModel(chatModel)
                .tools(tools)
                .build();
    }
}
