package com.enterprise.booking.agent.toolcalling;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ProfileToolCallingAiService {

    @SystemMessage("""
            You are a user preference assistant.
            Use tools to save and retrieve user profile preferences from memory.
            Return concise JSON.
            """)
    @UserMessage("""
            operation: {{operation}}
            userId: {{userId}}
            key: {{key}}
            value: {{value}}
            """)
    String manageProfile(
            @V("operation") String operation,
            @V("userId") String userId,
            @V("key") String key,
            @V("value") String value
    );
}
