package com.docia.service;

import com.docia.dto.DocumentCommandResponse.PageText;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailExtractionServiceTest {

    private final EmailExtractionService service = new EmailExtractionService();

    @Test
    void extractsValidEmailWithoutAdjacentPunctuation() {
        var result = service.extract(List.of(new PageText(1,
                "Contato: (user.name+tag@example.com), ou <support@example.org>!")));

        assertEquals(List.of("user.name+tag@example.com", "support@example.org"),
                result.stream().map(email -> email.email()).toList());
        assertEquals(List.of(1, 1), result.stream().map(email -> email.pageNumber()).toList());
    }

    @Test
    void extractsMultipleEmails() {
        var result = service.extract(List.of(new PageText(1,
                "a@example.com e b@example.org")));

        assertEquals(2, result.size());
    }

    @Test
    void reportsEmailsOnTheirRespectivePages() {
        var result = service.extract(List.of(
                new PageText(1, "one@example.com"),
                new PageText(2, "two@example.org")));

        assertEquals(2, result.size());
        assertEquals(1, result.get(0).pageNumber());
        assertEquals(2, result.get(1).pageNumber());
    }

    @Test
    void ignoresDuplicatesOnSamePageRegardlessOfCase() {
        var result = service.extract(List.of(new PageText(1,
                "User@Example.com user@example.COM USER@example.com")));

        assertEquals(1, result.size());
        assertEquals("User@Example.com", result.get(0).email());
    }

    @Test
    void returnsEmptyListWhenNoEmailIsFound() {
        assertEquals(List.of(), service.extract(List.of(new PageText(1, "Sem endereço eletrônico."))));
    }
}
