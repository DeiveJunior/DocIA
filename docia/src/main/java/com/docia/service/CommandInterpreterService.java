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

            - CPF_CNPJ: pedido de CPF, CNPJ, documento de identificação ou número de
              identificação pessoal ou empresarial.
            - ADDRESS: pedido de endereço, rua, logradouro, localização, CEP ou informação
              sobre o local onde alguém mora.
            - PHONE: pedido de telefone, celular ou número telefônico.
            - EMAIL: pedido de e-mail ou correio eletrônico.
            - FULL_TEXT: pedido para retornar o texto completo do documento.
            - UNKNOWN: intenção ambígua ou não identificável.

            Distinga documento de identificação de endereço: documento ou número de
            identificação corresponde a CPF_CNPJ; local onde alguém mora corresponde a ADDRESS.

            Exemplos:
            "Quero o documento de identificação da pessoa" → CPF_CNPJ.
            "Quero saber onde a pessoa mora" → ADDRESS.
            "Informe o CPF da pessoa" → CPF_CNPJ.
            "Liste os endereços encontrados" → ADDRESS.

            Responda somente com um objeto JSON válido, sem markdown ou texto adicional,
            contendo exatamente a propriedade "intent". Exemplo: {"intent":"EMAIL"}.
            Use somente um destes valores para "intent": FULL_TEXT, EMAIL, PHONE,
            CPF_CNPJ, ADDRESS ou UNKNOWN.
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
