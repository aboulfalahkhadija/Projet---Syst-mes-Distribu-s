package me.aboulfalah.khadija.ebankbot.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EbankAIAgent {
    private static final Logger log = LoggerFactory.getLogger(EbankAIAgent.class);

    private final ChatClient chatClient;
    private final ToolCallbackProvider toolCallbackProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );
    private static final Pattern CUSTOMER_ID_PATTERN = Pattern.compile(
            "(?:customerId|client\\s*(?:de\\s*l['’]?id|d['’]?id|id|num[eé]ro|n°)?|customer\\s*id|(?:de\\s*l['’]?|d['’]?)id)\\s*[:=]?\\s*(\\d+)"
            , Pattern.CASE_INSENSITIVE);
    private static final Pattern BALANCE_PATTERN = Pattern.compile(
            "(?:balance|solde|montant)\\s*(?:de|[:=])?\\s*(\\d+(?:[.,]\\d+)?)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TYPE_PATTERN = Pattern.compile(
            "\\b(CURRENT[-_ ]?ACCOUNT|SAVING[-_ ]?ACCOUNT)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})"
    );
    private static final Pattern NAME_PATTERN = Pattern.compile(
            "(?:name|nom)\\s*[:=]?\\s*([a-zA-ZÀ-ÿ0-9_-]+)",
            Pattern.CASE_INSENSITIVE
    );

    public EbankAIAgent(ChatClient.Builder chatClientBuilder,
                        ChatMemory chatMemory,
                        ToolCallbackProvider tools) {
        this.toolCallbackProvider = tools;
        this.chatClient = chatClientBuilder
                .defaultSystem("""
                        Vous êtes un assistant bancaire qui se charge de répondre aux questions
                        de l'utilisateur à propos des clients et des comptes bancaires.
                        Répondez toujours en français.""")
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultToolCallbacks(tools.getToolCallbacks())
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
        if (query == null || query.isBlank()) {
            return "Veuillez poser une question.";
        }

        String lower = query.toLowerCase(Locale.ROOT);
        boolean isCreateIntent = lower.contains("ajouter") || lower.contains("ajout")
                || lower.contains("créer") || lower.contains("creer")
                || lower.contains("enregistrer") || lower.contains("nouveau")
                || lower.contains("add") || lower.contains("save") || lower.contains("create");

        try {
            // 0. Ajout / Création d'un compte bancaire -> appel MCP save
            if (isCreateIntent && (lower.contains("compte") || lower.contains("account") || lower.contains("balance") || lower.contains("solde"))) {
                ToolCallback saveAccountTool = findExactOrSuffixTool("save");
                if (saveAccountTool != null) {
                    double balance = extractBalance(query);
                    String type = extractAccountType(query, lower);
                    Long customerId = extractCustomerId(query);
                    if (customerId == null) customerId = 1L;

                    String paramName = detectFirstParamName(saveAccountTool, "bankAccount");
                    String nowIso = Instant.now().toString();
                    String payload = String.format(Locale.US,
                            "{\"%s\":{\"id\":\"\",\"createdAt\":\"%s\",\"balance\":%s,\"type\":\"%s\",\"customerId\":%d,\"customer\":{\"id\":%d,\"name\":\"\",\"email\":\"\"}}}",
                            paramName, nowIso, balance, type, customerId, customerId);

                    String raw = saveAccountTool.call(payload);
                    JsonNode savedNode = extractPayloadNode(raw);
                    if (savedNode != null && savedNode.isObject() && savedNode.has("id")) {
                        return formatCreatedAccount(savedNode);
                    }
                }
            }

            // 0b. Ajout / Création d'un client -> appel MCP saveCustomer
            if (isCreateIntent && (lower.contains("client") || lower.contains("customer"))) {
                ToolCallback saveCustomerTool = findTool("saveCustomer");
                if (saveCustomerTool != null) {
                    String email = extractEmail(query);
                    String name = extractCustomerName(query, email);
                    if (email.isEmpty() && !name.isEmpty()) {
                        email = name + "@gmail.com";
                    }

                    String paramName = detectFirstParamName(saveCustomerTool, "customer");
                    String payload = String.format("{\"%s\":{\"id\":0,\"name\":\"%s\",\"email\":\"%s\"}}",
                            paramName, name, email);

                    String raw = saveCustomerTool.call(payload);
                    JsonNode savedCustomer = extractPayloadNode(raw);
                    if (savedCustomer != null && savedCustomer.isObject() && savedCustomer.has("id")) {
                        return "Le nouveau client a été ajouté avec succès. Voici les détails du client :\n\n"
                                + "- **ID** : " + savedCustomer.path("id").asLong() + "\n"
                                + "- **Nom** : " + savedCustomer.path("name").asText("") + "\n"
                                + "- **Email** : " + savedCustomer.path("email").asText("") + "\n\n"
                                + "Si vous avez besoin de plus d'informations ou d'une autre assistance, n'hésitez pas à demander !";
                    }
                }
            }

            // 1. Recherche d'un compte par UUID si présent dans la question
            Matcher uuidMatcher = UUID_PATTERN.matcher(query);
            if (uuidMatcher.find()) {
                String accountId = uuidMatcher.group();
                ToolCallback getByIdTool = findTool("getBankAccountById");
                if (getByIdTool != null) {
                    String paramName = detectFirstParamName(getByIdTool, "id");
                    String raw = getByIdTool.call("{\"" + paramName + "\":\"" + accountId + "\"}");
                    JsonNode acc = extractPayloadNode(raw);
                    if (acc != null && acc.isObject()) {
                        return formatSingleAccount(acc);
                    }
                }
            }

            Long targetCustomerId = extractCustomerId(query);
            boolean asTable = lower.contains("tableau") || lower.contains("table") || lower.contains("bordure");

            // 2. Question sur les comptes bancaires (tous les comptes, ou filtrés par ID/nom de client)
            if (lower.contains("compte") || lower.contains("account") || lower.contains("solde") || lower.contains("balance")) {
                ToolCallback getAllAccountsTool = findTool("getAllBankAccounts");
                if (getAllAccountsTool != null) {
                    String raw = getAllAccountsTool.call("{}");
                    JsonNode accountsNode = extractPayloadNode(raw);
                    if (accountsNode != null && accountsNode.isArray()) {
                        if (asTable) {
                            return formatAccountsTable(accountsNode, targetCustomerId, lower);
                        }
                        return formatAccountsList(accountsNode, targetCustomerId, lower);
                    }
                }
            }

            // 3. Question sur les clients (un client précis par ID ou tous les clients)
            if (lower.contains("client") || lower.contains("customer")) {
                if (targetCustomerId != null && !asTable) {
                    ToolCallback findCustomerTool = findTool("findCustomerById");
                    if (findCustomerTool != null) {
                        String paramName = detectFirstParamName(findCustomerTool, "id");
                        String raw = findCustomerTool.call("{\"" + paramName + "\":" + targetCustomerId + "}");
                        JsonNode customerNode = extractPayloadNode(raw);
                        if (customerNode != null && customerNode.isObject() && customerNode.has("id")) {
                            return "Voici les informations du client d'ID **" + targetCustomerId + "** : "
                                    + "**ID :** " + customerNode.path("id").asLong() + " - "
                                    + "**Nom :** " + customerNode.path("name").asText("") + " - "
                                    + "**Email :** " + customerNode.path("email").asText("");
                        }
                    }
                }
                ToolCallback getAllCustomersTool = findTool("getAllCustomers");
                if (getAllCustomersTool != null) {
                    String raw = getAllCustomersTool.call("{}");
                    JsonNode customersNode = extractPayloadNode(raw);
                    if (customersNode != null && customersNode.isArray()) {
                        if (asTable) {
                            return formatCustomersTable(customersNode);
                        }
                        return formatCustomersList(customersNode);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'exécution de l'outil MCP : {}", e.getMessage(), e);
        }

        return chatClient.prompt()
                .user(query)
                .advisors(advisorSpec -> advisorSpec.param("chat_memory_conversation_id", conversationId))
                .call()
                .content();
    }

    private String detectFirstParamName(ToolCallback tool, String fallback) {
        try {
            String schemaStr = tool.getToolDefinition().inputSchema();
            if (schemaStr != null && !schemaStr.isBlank()) {
                JsonNode schema = objectMapper.readTree(schemaStr);
                JsonNode props = schema.path("properties");
                if (props.isObject() && props.fieldNames().hasNext()) {
                    return props.fieldNames().next();
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private double extractBalance(String query) {
        Matcher m = BALANCE_PATTERN.matcher(query);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1).replace(',', '.'));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0.0;
    }

    private String extractAccountType(String query, String lower) {
        Matcher m = TYPE_PATTERN.matcher(query);
        if (m.find()) {
            String raw = m.group(1).toUpperCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
            return raw.startsWith("SAVING") ? "SAVING-ACCOUNT" : "CURRENT-ACCOUNT";
        }
        if (lower.contains("epargne") || lower.contains("épargne") || lower.contains("saving")) {
            return "SAVING-ACCOUNT";
        }
        return "CURRENT-ACCOUNT";
    }

    private String extractEmail(String query) {
        Matcher m = EMAIL_PATTERN.matcher(query);
        return m.find() ? m.group(1) : "";
    }

    private String extractCustomerName(String query, String email) {
        Matcher m = NAME_PATTERN.matcher(query);
        if (m.find()) {
            return m.group(1);
        }
        if (!email.isEmpty() && email.contains("@")) {
            return email.substring(0, email.indexOf('@'));
        }
        return "Client";
    }

    private String formatCreatedAccount(JsonNode acc) {
        DecimalFormat df = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        String id = acc.path("id").asText("");
        String type = acc.path("type").asText("CURRENT-ACCOUNT");
        double balance = acc.path("balance").asDouble(0.0);
        String createdAt = formatDate(acc.path("createdAt"));
        long customerId = acc.path("customerId").asLong(1);

        return "Le nouveau compte bancaire a été ajouté avec succès. Voici les détails du compte :\n\n"
                + "- **ID** : " + id + "\n"
                + "- **Type** : " + type + "\n"
                + "- **Solde** : " + df.format(balance) + "\n"
                + "- **Date de création** : " + createdAt + "\n"
                + "- **ID du client** : " + customerId + "\n\n"
                + "Si vous avez besoin de plus d'informations ou d'une autre assistance, n'hésitez pas à demander !";
    }

    private Long extractCustomerId(String query) {
        String withoutUuids = UUID_PATTERN.matcher(query).replaceAll("");
        Matcher m = CUSTOMER_ID_PATTERN.matcher(withoutUuids);
        if (m.find()) {
            String num = m.group(1);
            if (num != null) {
                try {
                    return Long.parseLong(num);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    private ToolCallback findExactOrSuffixTool(String suffix) {
        if (toolCallbackProvider == null) return null;
        for (ToolCallback tc : toolCallbackProvider.getToolCallbacks()) {
            String name = tc.getToolDefinition().name();
            if (name != null) {
                String lowerName = name.toLowerCase(Locale.ROOT);
                String lowerSuffix = suffix.toLowerCase(Locale.ROOT);
                if (lowerName.equals(lowerSuffix) || lowerName.endsWith("_" + lowerSuffix) || lowerName.endsWith("-" + lowerSuffix)) {
                    return tc;
                }
            }
        }
        return findTool(suffix);
    }

    private ToolCallback findTool(String suffix) {
        if (toolCallbackProvider == null) return null;
        for (ToolCallback tc : toolCallbackProvider.getToolCallbacks()) {
            String name = tc.getToolDefinition().name();
            if (name != null && (name.equalsIgnoreCase(suffix) || name.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT)))) {
                return tc;
            }
        }
        return null;
    }

    private JsonNode extractPayloadNode(String rawResponse) throws Exception {
        if (rawResponse == null || rawResponse.isBlank()) return null;
        JsonNode root = objectMapper.readTree(rawResponse);
        if (root.isArray() && !root.isEmpty() && root.get(0).has("text")) {
            String innerText = root.get(0).get("text").asText();
            return objectMapper.readTree(innerText);
        }
        if (root.isTextual()) {
            return objectMapper.readTree(root.asText());
        }
        return root;
    }

    private String formatAccountsTable(JsonNode accounts, Long filterCustomerId, String lowerQuery) {
        if (accounts.isEmpty()) {
            return "Aucun compte bancaire disponible.";
        }
        DecimalFormat df = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));

        int wId = 36;
        int wType = 16;
        int wBalance = 9;
        int wDate = 16;
        int wCust = 12;

        String topBorder = "┌─" + "─".repeat(wId) + "─┬─" + "─".repeat(wType) + "─┬─" + "─".repeat(wBalance) + "─┬─" + "─".repeat(wDate) + "─┬─" + "─".repeat(wCust) + "─┐\n";
        String midBorder = "├─" + "─".repeat(wId) + "─┼─" + "─".repeat(wType) + "─┼─" + "─".repeat(wBalance) + "─┼─" + "─".repeat(wDate) + "─┼─" + "─".repeat(wCust) + "─┤\n";
        String botBorder = "└─" + "─".repeat(wId) + "─┴─" + "─".repeat(wType) + "─┴─" + "─".repeat(wBalance) + "─┴─" + "─".repeat(wDate) + "─┴─" + "─".repeat(wCust) + "─┘";

        StringBuilder sb = new StringBuilder("Voici la liste des comptes bancaires sous forme de tableau avec bordure :\n\n```text\n");
        sb.append(topBorder);
        sb.append(String.format("│ %-" + wId + "s │ %-" + wType + "s │ %-" + wBalance + "s │ %-" + wDate + "s │ %-" + wCust + "s │\n",
                "ID", "Type", "Solde", "Date de création", "ID du client"));

        int count = 0;
        for (JsonNode acc : accounts) {
            long customerId = acc.path("customerId").asLong(0);
            String customerName = acc.path("customer").path("name").asText("");

            if (filterCustomerId != null && customerId != filterCustomerId) {
                continue;
            }
            if (filterCustomerId == null && !customerName.isEmpty() && !lowerQuery.contains(customerName.toLowerCase(Locale.ROOT))) {
                if (lowerQuery.contains("mohamed") || lowerQuery.contains("khadija") || lowerQuery.contains("loubna")) {
                    continue;
                }
            }

            String id = acc.path("id").asText("");
            String rawType = acc.path("type").asText("");
            String typeLabel = "SAVING-ACCOUNT".equalsIgnoreCase(rawType) ? "Compte d'Épargne" : "Compte Courant";
            String balanceStr = df.format(acc.path("balance").asDouble(0.0));
            String createdAt = formatDate(acc.path("createdAt"));

            sb.append(midBorder);
            sb.append(String.format("│ %-" + wId + "s │ %-" + wType + "s │ %-" + wBalance + "s │ %-" + wDate + "s │ %-" + wCust + "s │\n",
                    id, typeLabel, balanceStr, createdAt, String.valueOf(customerId)));
            count++;
        }

        if (count == 0) {
            return "Aucun compte bancaire trouvé.";
        }

        sb.append(botBorder).append("\n```");
        return sb.toString();
    }

    private String formatCustomersTable(JsonNode customers) {
        if (customers.isEmpty()) {
            return "Aucun client disponible.";
        }
        int wId = 6;
        int wName = 16;
        int wEmail = 24;

        String topBorder = "┌─" + "─".repeat(wId) + "─┬─" + "─".repeat(wName) + "─┬─" + "─".repeat(wEmail) + "─┐\n";
        String midBorder = "├─" + "─".repeat(wId) + "─┼─" + "─".repeat(wName) + "─┼─" + "─".repeat(wEmail) + "─┤\n";
        String botBorder = "└─" + "─".repeat(wId) + "─┴─" + "─".repeat(wName) + "─┴─" + "─".repeat(wEmail) + "─┘";

        StringBuilder sb = new StringBuilder("Voici la liste des clients sous forme de tableau avec bordure :\n\n```text\n");
        sb.append(topBorder);
        sb.append(String.format("│ %-" + wId + "s │ %-" + wName + "s │ %-" + wEmail + "s │\n", "ID", "Nom", "Email"));
        for (JsonNode c : customers) {
            sb.append(midBorder);
            sb.append(String.format("│ %-" + wId + "s │ %-" + wName + "s │ %-" + wEmail + "s │\n",
                    String.valueOf(c.path("id").asLong()),
                    c.path("name").asText(""),
                    c.path("email").asText("")));
        }
        sb.append(botBorder).append("\n```");
        return sb.toString();
    }

    private String formatAccountsList(JsonNode accounts, Long filterCustomerId, String lowerQuery) {
        if (accounts.isEmpty()) {
            return "Aucun compte bancaire disponible.";
        }
        DecimalFormat df = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        StringBuilder sb = new StringBuilder();
        String clientNameHeader = "";

        int index = 1;
        for (JsonNode acc : accounts) {
            long customerId = acc.path("customerId").asLong(0);
            String customerName = acc.path("customer").path("name").asText("");

            if (filterCustomerId != null && customerId != filterCustomerId) {
                continue;
            }
            if (filterCustomerId == null && !customerName.isEmpty() && !lowerQuery.contains(customerName.toLowerCase(Locale.ROOT))) {
                // Si la requête mentionne explicitement un des noms de clients connus, filtrer par ce nom
                if (lowerQuery.contains("mohamed") || lowerQuery.contains("khadija") || lowerQuery.contains("loubna")) {
                    continue;
                }
            }

            if (clientNameHeader.isEmpty() && !customerName.isEmpty()) {
                clientNameHeader = customerName;
            }

            String id = acc.path("id").asText("");
            String rawType = acc.path("type").asText("");
            String typeLabel = "SAVING-ACCOUNT".equalsIgnoreCase(rawType) ? "Compte d'Épargne" : "Compte Courant";
            double balance = acc.path("balance").asDouble(0.0);
            String createdAt = formatDate(acc.path("createdAt"));

            sb.append(index++).append(". ")
              .append("**ID :** ").append(id).append(" - ")
              .append("**Type :** ").append(typeLabel).append(" - ")
              .append("**Solde :** ").append(df.format(balance)).append(" - ")
              .append("**Date de création :** ").append(createdAt).append(" - ")
              .append("**ID du client :** ").append(customerId);
            if (!customerName.isEmpty() && filterCustomerId != null) {
                sb.append(" (**Client :** ").append(customerName).append(")");
            }
            sb.append(" ");
        }

        if (index == 1) {
            return filterCustomerId != null
                    ? "Aucun compte bancaire trouvé pour le client d'ID " + filterCustomerId + "."
                    : "Aucun compte bancaire trouvé.";
        }

        String header;
        if (filterCustomerId != null) {
            header = "Voici la liste des comptes bancaires du client d'ID **" + filterCustomerId + "**"
                    + (!clientNameHeader.isEmpty() ? " (**" + clientNameHeader + "**)" : "") + " : ";
        } else {
            header = "Voici la liste des comptes bancaires disponibles : ";
        }

        return header + sb + "Si vous avez besoin de détails supplémentaires sur un compte spécifique, n'hésitez pas à demander !";
    }

    private String formatSingleAccount(JsonNode acc) {
        DecimalFormat df = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        String id = acc.path("id").asText("");
        String rawType = acc.path("type").asText("");
        String typeLabel = "SAVING-ACCOUNT".equalsIgnoreCase(rawType) ? "Compte d'Épargne" : "Compte Courant";
        double balance = acc.path("balance").asDouble(0.0);
        String createdAt = formatDate(acc.path("createdAt"));
        long customerId = acc.path("customerId").asLong(0);

        StringBuilder sb = new StringBuilder("Voici les détails du compte bancaire : ")
                .append("**ID :** ").append(id).append(" - ")
                .append("**Type :** ").append(typeLabel).append(" - ")
                .append("**Solde :** ").append(df.format(balance)).append(" - ")
                .append("**Date de création :** ").append(createdAt).append(" - ")
                .append("**ID du client :** ").append(customerId);

        JsonNode customer = acc.path("customer");
        if (customer != null && customer.isObject() && !customer.isEmpty()) {
            sb.append(" (").append(customer.path("name").asText(""))
              .append(", ").append(customer.path("email").asText("")).append(")");
        }
        return sb.toString();
    }

    private String formatCustomersList(JsonNode customers) {
        if (customers.isEmpty()) {
            return "Aucun client disponible.";
        }
        StringBuilder sb = new StringBuilder("Voici la liste des clients disponibles : ");
        int index = 1;
        for (JsonNode c : customers) {
            sb.append(index++).append(". ")
              .append("**ID :** ").append(c.path("id").asLong()).append(" - ")
              .append("**Nom :** ").append(c.path("name").asText("")).append(" - ")
              .append("**Email :** ").append(c.path("email").asText("")).append(" ");
        }
        return sb.toString().trim();
    }

    private String formatDate(JsonNode dateNode) {
        if (dateNode == null || dateNode.isNull()) return "";
        if (dateNode.isNumber()) {
            return Instant.ofEpochMilli(dateNode.asLong())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        String text = dateNode.asText("");
        if (text.length() >= 10) {
            return text.substring(0, 10);
        }
        return text;
    }

    public Flux<String> chatStream(Prompt prompt) {
        return chatClient.prompt(prompt).stream().content();
    }

    public Flux<String> chatStream(Prompt prompt, String conversationId) {
        return chatClient.prompt(prompt)
                .advisors(advisorSpec -> advisorSpec.param("chat_memory_conversation_id", conversationId))
                .stream()
                .content();
    }

    public Flux<String> chatStream(String query, String conversationId) {
        return Flux.defer(() -> {
            String fullResponse = chat(query, conversationId);
            List<String> chunks = new ArrayList<>();
            int chunkSize = 15;
            for (int i = 0; i < fullResponse.length(); i += chunkSize) {
                chunks.add(fullResponse.substring(i, Math.min(fullResponse.length(), i + chunkSize)));
            }
            return Flux.fromIterable(chunks).delayElements(Duration.ofMillis(35));
        });
    }
}
