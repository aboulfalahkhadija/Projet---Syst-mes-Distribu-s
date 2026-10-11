package me.aboulfalah.khadija.ebankbot.telegram;

import jakarta.annotation.PostConstruct;
import me.aboulfalah.khadija.ebankbot.agents.EbankAIAgent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Component
public class TelegramBot extends TelegramLongPollingBot {

    @Value("${telegram.token}")
    private String telegramBotToken;

    @Value("${telegram.bot.username:ebank_bot}")
    private String botUsername;

    private final EbankAIAgent aiAgent;

    public TelegramBot(EbankAIAgent aiAgent) {
        this.aiAgent = aiAgent;
    }

    @Override
    public String getBotToken() {
        return this.telegramBotToken;
    }

    @Override
    public String getBotUsername() {
        return this.botUsername;
    }

    @PostConstruct
    public void registerTelegramBot() {
        try {
            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(this);
        } catch (TelegramApiException e) {
            throw new RuntimeException("Erreur lors de l'enregistrement du bot Telegram", e);
        }
    }

    @Override
    public void onUpdateReceived(Update telegramRequest) {
        try {
            if (!telegramRequest.hasMessage()) return;

            Long chatId = telegramRequest.getMessage().getChatId();
            String query = telegramRequest.getMessage().getText();

            // Si l'utilisateur a envoyé une photo accompagnée d'une légende
            if (query == null && telegramRequest.getMessage().hasPhoto()) {
                query = telegramRequest.getMessage().getCaption();
            }

            if (query == null || query.isBlank()) {
                sendTextMessage(chatId, "Veuillez poser une question par texte.");
                return;
            }

            // Déclenche l'indicateur "en train d'écrire..." sur Telegram
            sendTypingQuestion(chatId);

            // Appel de l'agent en passant chatId sous forme de conversationId
            String answer = aiAgent.chat(query, String.valueOf(chatId));

            sendTextMessage(chatId, answer);

        } catch (Exception e) {
            e.printStackTrace();
            if (telegramRequest.hasMessage()) {
                sendTextMessage(telegramRequest.getMessage().getChatId(), "Désolé, une erreur est survenue lors du traitement.");
            }
        }
    }

    private void sendTypingQuestion(Long chatId) {
        SendChatAction sendChatAction = new SendChatAction();
        sendChatAction.setChatId(chatId.toString());
        sendChatAction.setAction(ActionType.TYPING);
        try {
            execute(sendChatAction);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendTextMessage(Long chatId, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId.toString());
        sendMessage.setText(text);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}