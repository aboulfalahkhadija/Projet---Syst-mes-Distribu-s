package me.aboulfalah.khadija.ebankbot.controllers;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EbankChatbotController {

    private final ChatClient chatClient;

    public EbankChatbotController(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @GetMapping("/chat")
    public String chat(
            @RequestParam(name = "query", defaultValue = "bonjour") String query,
            @RequestParam(name = "conversationId", defaultValue = "default") String conversationId) {

        return chatClient.prompt()
                .user(query)
                .advisors(advisorSpec -> advisorSpec.param("chat_memory_conversation_id", conversationId))
                .call()
                .content();
    }
}