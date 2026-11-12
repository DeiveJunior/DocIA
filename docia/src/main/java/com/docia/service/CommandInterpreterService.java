package com.docia.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

@Service
public class CommandInterpreterService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandInterpreterService.class);

    private static final String SYSTEM_PROMPT = """
            Classifique o comando do usuário em exatamente uma destas intenções:
            FULL_TEXT, EMAIL, PHONE, CPF_CNPJ, ADDRESS ou UNKNOWN.
            Responda somente com um objeto JSON válido, sem markdown ou texto adicional,
            contendo exatamente a propriedade "intent". Exemplo: {"intent":"EMAIL"}.
            Use UNKNOWN se não for possível identificar a intenção.
            """;

    private final RestClient restClient;
    private final JsonMapper objectMapper;
    private final String model;

    public CommandInterpreterService(
            RestClient.Builder restClientBuilder,
            JsonMapper objectMapper,
            @Value("${docia.ollama.url:http://localhost:11434}") String ollamaUrl,
            @Value("${docia.ollama.model:qwen2.5-coder:0.5b}") String model) {
        this.restClient = restClientBuilder.baseUrl(ollamaUrl).build();
        this.objectMapper = objectMapper;
        this.model = model;
    }

    public Intent interpret(String command) {
        if (command == null || command.isBlank()) {
            LOGGER.error("Comando vazio ou ausente; retornando UNKNOWN.");
            return Intent.UNKNOWN;
        }

        try {
            String responseBody = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(createRequest(command))
                    .retrieve()
                    .body(String.class);

            return parseIntent(responseBody);
        } catch (RestClientException | JacksonException | IllegalArgumentException exception) {
            LOGGER.error("Falha ao interpretar o comando com Ollama; retornando UNKNOWN.", exception);
            return Intent.UNKNOWN;
        }
    }

    private Map<String, Object> createRequest(String command) {
        return Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", command)
                ),
                "stream", false,
                "format", "json",
                "options", Map.of("temperature", 0.0)
        );
    }

    private Intent parseIntent(String responseBody) throws JacksonException {
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalArgumentException("Resposta vazia do Ollama.");
        }

        JsonNode response = objectMapper.readTree(responseBody);
        JsonNode content = response == null ? null : response.path("message").path("content");
        if (content == null || !content.isTextual()) {
            throw new IllegalArgumentException("Resposta do Ollama sem conteúdo textual.");
        }

        JsonNode result = objectMapper.readTree(content.asText());
        if (result == null || !result.isObject() || result.size() != 1
                || !result.has("intent") || !result.get("intent").isTextual()) {
            throw new IllegalArgumentException("JSON de intenção inválido.");
        }

        return Intent.valueOf(result.get("intent").asText());
    }

    public enum Intent {
        FULL_TEXT,
        EMAIL,
        PHONE,
        CPF_CNPJ,
        ADDRESS,
        UNKNOWN
    }
}
