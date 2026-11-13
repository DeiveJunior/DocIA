package com.docia.service;

import com.docia.dto.AddressExtractionResponse;
import com.docia.dto.DocumentCommandResponse.PageText;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeneralDocumentAnswerServiceTest {

    private static final String OLLAMA_URL = "http://localhost:11434";

    @Test
    void sendsCommandPdfPagesAndJavaResultsToOllama() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo(OLLAMA_URL + "/api/chat"))
                .andExpect(jsonPath("$.messages[1].content")
                        .value(org.hamcrest.Matchers.containsString("Qual é meu endereço completo?")))
                .andExpect(jsonPath("$.messages[1].content")
                        .value(org.hamcrest.Matchers.containsString("[Página 2]")))
                .andExpect(jsonPath("$.messages[1].content")
                        .value(org.hamcrest.Matchers.containsString("bairro Potecas")))
                .andExpect(jsonPath("$.messages[1].content")
                        .value(org.hamcrest.Matchers.containsString("Rua Anélio")))
                .andExpect(jsonPath("$.messages[0].content")
                        .value(org.hamcrest.Matchers.containsString("ignore")))
                .andRespond(withSuccess(
                        ollamaResponse(
                                "ANSWERED",
                                "Rua Anélio, bairro Potecas, São José",
                                "[{\"pageNumber\":2}]"),
                        MediaType.APPLICATION_JSON));

        var result = fixture.service().answer(
                "Qual é meu endereço completo?",
                List.of(new PageText(2, "Rua Anélio, bairro Potecas, cidade São José")),
                new AddressExtractionResponse(
                        "sample.pdf",
                        "endereço",
                        "EXTRACTED",
                        2,
                        List.of(new AddressExtractionResponse.AddressOccurrence(
                                "Rua Anélio", null, null, null, null, null, null, 2))));

        assertEquals("ANSWERED", result.status());
        assertEquals("Rua Anélio, bairro Potecas, São José", result.answer());
        assertEquals(List.of(2), result.sourcePages());
        fixture.server().verify();
    }

    @Test
    void rejectsPageReferencesNotPresentInPdf() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo(OLLAMA_URL + "/api/chat"))
                .andRespond(withSuccess(
                        ollamaResponse(
                                "ANSWERED",
                                "O endereço está no documento.",
                                "[{\"pageNumber\":1},{\"pageNumber\":99}]"),
                        MediaType.APPLICATION_JSON));

        var result = fixture.service().answer(
                "Qual é o endereço?",
                List.of(new PageText(1, "Rua A."), new PageText(2, "Bairro Centro.")),
                null);

        assertEquals("ANSWERED", result.status());
        assertEquals(List.of(1), result.sourcePages());
        fixture.server().verify();
    }

    @Test
    void returnsNotFoundWhenDocumentDoesNotSupportRequestedInformation() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo(OLLAMA_URL + "/api/chat"))
                .andRespond(withSuccess(
                        ollamaResponse(
                                "NOT_FOUND",
                                "Não foi possível encontrar essa informação.",
                                "[]"),
                        MediaType.APPLICATION_JSON));

        var result = fixture.service().answer(
                "Qual é o número da casa?",
                List.of(new PageText(1, "Rua A, sem número informado.")),
                null);

        assertEquals("NOT_FOUND", result.status());
        assertTrue(result.sourcePages().isEmpty());
        fixture.server().verify();
    }

    @Test
    void treatsInvalidOllamaResponseAsUnavailable() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo(OLLAMA_URL + "/api/chat"))
                .andRespond(withSuccess(
                        "{\"message\":{\"content\":\"isto não é JSON\"}}",
                        MediaType.APPLICATION_JSON));

        var result = fixture.service().answer(
                "Faça um resumo.",
                List.of(new PageText(1, "Texto do documento.")),
                null);

        assertEquals("AI_UNAVAILABLE", result.status());
        fixture.server().verify();
    }

    @Test
    void distinguishesOllamaUnavailability() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo(OLLAMA_URL + "/api/chat"))
                .andRespond(withServerError());

        var result = fixture.service().answer(
                "Faça um resumo.",
                List.of(new PageText(1, "Texto do documento.")),
                null);

        assertEquals("AI_UNAVAILABLE", result.status());
        fixture.server().verify();
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeneralDocumentAnswerService service = new GeneralDocumentAnswerService(
                builder, JsonMapper.builder().build(), OLLAMA_URL, "test-model");
        return new Fixture(service, server);
    }

    private String ollamaResponse(String status, String answer, String sources) {
        String content = "{\"status\":\"" + status
                + "\",\"answer\":\"" + answer
                + "\",\"sources\":" + sources + "}";
        String escapedContent = content.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"message\":{\"content\":\"" + escapedContent + "\"}}";
    }

    private record Fixture(GeneralDocumentAnswerService service, MockRestServiceServer server) {
    }
}
