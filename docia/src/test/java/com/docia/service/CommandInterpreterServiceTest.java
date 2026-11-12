package com.docia.service;

import com.docia.service.CommandInterpreterService.Intent;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CommandInterpreterServiceTest {

    private static final String OLLAMA_URL = "http://localhost:11434";
    private static final String MODEL = "qwen2.5-coder:0.5b";

    @Test
    void sendsCommandAndRecognizesAllAllowedIntents() {
        Fixture fixture = createFixture();
        String command = "Extraia os e-mails deste documento.";

        for (Intent intent : Intent.values()) {
            fixture.server().expect(requestTo(OLLAMA_URL + "/api/chat"))
                    .andExpect(method(POST))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.model").value(MODEL))
                    .andExpect(jsonPath("$.stream").value(false))
                    .andExpect(jsonPath("$.options.temperature").value(0.0))
                    .andExpect(jsonPath("$.messages[1].role").value("user"))
                    .andExpect(jsonPath("$.messages[1].content").value(command))
                    .andRespond(withSuccess(ollamaResponse(intent.name()), MediaType.APPLICATION_JSON));

            assertEquals(intent, fixture.service().interpret(command));
        }

        fixture.server().verify();
    }

    @Test
    void returnsUnknownForInvalidIntent() {
        Fixture fixture = createFixture();
        fixture.server().expect(requestTo(OLLAMA_URL + "/api/chat"))
                .andRespond(withSuccess(
                        "{\"message\":{\"content\":\"{\\\"intent\\\":\\\"NOT_AN_INTENT\\\"}\"}}",
                        MediaType.APPLICATION_JSON));

        assertEquals(Intent.UNKNOWN, fixture.service().interpret("Faça algo."));
        fixture.server().verify();
    }

    @Test
    void returnsUnknownForMalformedIntentJson() {
        Fixture fixture = createFixture();
        fixture.server().expect(requestTo(OLLAMA_URL + "/api/chat"))
                .andRespond(withSuccess(
                        "{\"message\":{\"content\":\"não é JSON\"}}",
                        MediaType.APPLICATION_JSON));

        assertEquals(Intent.UNKNOWN, fixture.service().interpret("Faça algo."));
        fixture.server().verify();
    }

    @Test
    void returnsUnknownWhenResponseHasNoMessageContent() {
        Fixture fixture = createFixture();
        fixture.server().expect(requestTo(OLLAMA_URL + "/api/chat"))
                .andRespond(withSuccess(
                        "{\"message\":{\"role\":\"assistant\"},\"done\":true}",
                        MediaType.APPLICATION_JSON));

        assertEquals(Intent.UNKNOWN, fixture.service().interpret("Extraia o texto."));
        fixture.server().verify();
    }

    @Test
    void returnsUnknownForNullOrBlankCommandWithoutHttpRequest() {
        Fixture fixture = createFixture();

        assertEquals(Intent.UNKNOWN, fixture.service().interpret(null));
        assertEquals(Intent.UNKNOWN, fixture.service().interpret(""));
        assertEquals(Intent.UNKNOWN, fixture.service().interpret("   "));

        fixture.server().verify();
    }

    @Test
    void returnsUnknownForCommunicationFailure() {
        Fixture fixture = createFixture();
        fixture.server().expect(requestTo(OLLAMA_URL + "/api/chat"))
                .andRespond(withServerError());

        assertEquals(Intent.UNKNOWN, fixture.service().interpret("Faça algo."));
        fixture.server().verify();
    }

    private Fixture createFixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CommandInterpreterService service =
                new CommandInterpreterService(builder, JsonMapper.builder().build(), OLLAMA_URL, MODEL);
        return new Fixture(service, server);
    }

    private String ollamaResponse(String intent) {
        return """
                {
                  "message": {
                    "role": "assistant",
                    "content": "{\\\"intent\\\":\\\"%s\\\"}"
                  },
                  "done": true
                }
                """.formatted(intent);
    }

    private record Fixture(CommandInterpreterService service, MockRestServiceServer server) {
    }
}
