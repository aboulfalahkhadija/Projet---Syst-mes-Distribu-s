package me.aboulfalah.khadija.ebankbot.discord;

import jakarta.annotation.PostConstruct;
import me.aboulfalah.khadija.ebankbot.agents.EbankAIAgent;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

@Component
public class DiscordBot extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(DiscordBot.class);

    @Value("${discord.token}")
    private String discordToken;

    private final EbankAIAgent ebankAIAgent;

    public DiscordBot(EbankAIAgent ebankAIAgent) {
        this.ebankAIAgent = ebankAIAgent;
    }

    @PostConstruct
    public void startBot() {
        try {
            JDABuilder.createDefault(
                            discordToken,
                            EnumSet.of(
                                    GatewayIntent.GUILD_MESSAGES,
                                    GatewayIntent.DIRECT_MESSAGES,
                                    GatewayIntent.MESSAGE_CONTENT
                            )
                    )
                    .addEventListeners(this)
                    .build();
            log.info("Discord Bot démarré et connecté avec succès.");
        } catch (Exception e) {
            log.error("Erreur lors de l'initialisation du bot Discord : {}", e.getMessage(), e);
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignorer les messages envoyés par les bots pour éviter les boucles infinies
        if (event.getAuthor().isBot()) return;

        String query = event.getMessage().getContentDisplay();
        if (query == null || query.isBlank()) return;

        // Retirer une éventuelle mention (@ebank-app) pour ne garder que le prompt utile
        query = query.replace("@ebank-app", "").trim();
        if (query.isBlank()) return;

        // Déclencher l'indicateur "en train d'écrire..." sur le salon Discord
        event.getChannel().sendTyping().queue();

        String conversationId = event.getChannel().getId();
        String response = ebankAIAgent.chat(query, conversationId);

        if (response == null || response.isBlank()) {
            response = "Je n'ai pas pu obtenir de réponse.";
        }

        // Découpage automatique respectant la limite de 2000 caractères imposée par Discord
        if (response.length() <= 2000) {
            event.getChannel().sendMessage(response).queue();
        } else {
            for (int i = 0; i < response.length(); i += 2000) {
                String chunk = response.substring(i, Math.min(response.length(), i + 2000));
                event.getChannel().sendMessage(chunk).queue();
            }
        }
    }
}