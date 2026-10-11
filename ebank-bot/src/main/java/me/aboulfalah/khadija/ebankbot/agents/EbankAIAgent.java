package me.aboulfalah.khadija.ebankbot.agents;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;

@Service
public class EbankAIAgent {
    private final ChatClient chatClient;

    public EbankAIAgent(ChatClient.Builder chatClientBuilder,
                        ChatMemory chatMemory,
                        ToolCallbackProvider tools) {
        this.chatClient = chatClientBuilder
                .defaultSystem("""
                        Vous êtes un assistant qui se charge de répondre aux questions
                        de l'utilisateur à propos des clients et des comptes bancaires,
                        en fonction du texte fourni.
                        Si aucun contexte n'est fourni, réponds avec : je ne sais pas""")
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(tools)
                .build();
    }

    // Version acceptant un objet Prompt complet
    public String chat(Prompt prompt, String conversationId) {
        return chatClient.prompt(prompt)
                .advisors(advisorSpec -> advisorSpec.param("chat_memory_conversation_id", conversationId))
                .call()
                .content();
    }

    // Version surchargée acceptant directement un String
    public String chat(String query, String conversationId) {
        return chatClient.prompt()
                .user(query)
                .advisors(advisorSpec -> advisorSpec.param("chat_memory_conversation_id", conversationId))
                .call()
                .content();
    }
}