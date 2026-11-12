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

            - CPF_CNPJ: pedidos de CPF, CNPJ ou documentos e números de identificação
              pessoal ou empresarial. Inclui pedidos indiretos sobre inscrição cadastral
              de empresas, registro de pessoa jurídica, cadastro ou inscrição junto à
              Receita Federal e registro oficial da empresa nesse órgão, mesmo quando o
              comando não menciona explicitamente "CNPJ".
            - ADDRESS: pedidos de endereço, rua, logradouro, localização, CEP ou informação
              sobre o local onde alguém mora.
            - PHONE: pedidos de telefone, celular ou número telefônico.
            - EMAIL: pedidos de e-mail ou correio eletrônico.
            - FULL_TEXT: pedidos para retornar o texto completo do documento.
            - UNKNOWN: intenção ambígua, não identificável ou fora dessas categorias.

            Exemplos:
            "Preciso identificar o registro oficial que comprova a inscrição da empresa
            mencionada no contrato junto à Receita Federal" → CPF_CNPJ.
            "Informe o CNPJ da empresa" → CPF_CNPJ.
            "Quero saber onde a pessoa mora" → ADDRESS.
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
            @Value("${docia.ollama.model:qwen2.5-coder:7b}") String model) {
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
