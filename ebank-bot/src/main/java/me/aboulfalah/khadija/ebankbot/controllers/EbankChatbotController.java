package me.aboulfalah.khadija.ebankbot.controllers;

import me.aboulfalah.khadija.ebankbot.agents.EbankAIAgent;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class EbankChatbotController {

    private final EbankAIAgent ebankAIAgent;

    public EbankChatbotController(EbankAIAgent ebankAIAgent) {
        this.ebankAIAgent = ebankAIAgent;
    }

    @GetMapping(value = "/chat", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public String chat(
            @RequestParam(name = "query", defaultValue = "bonjour") String query,
            @RequestParam(name = "conversationId", defaultValue = "default") String conversationId) {

        // Vous pouvez maintenant appeler directement la version avec String
        return ebankAIAgent.chat(query, conversationId);
    }
    @GetMapping(value = "/chatStream", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public Flux<String> chatStream(
            @RequestParam(name = "query", defaultValue = "bonjour") String query,
            @RequestParam(name = "conversationId", defaultValue = "default") String conversationId) {

        // Vous pouvez maintenant appeler directement la version avec String
        return ebankAIAgent.chatStream(query, conversationId);
    }
}