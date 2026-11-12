package com.docia.service;

import com.docia.dto.DocumentCommandResponse;
import com.docia.dto.EmailExtractionResponse;
import com.docia.dto.DocumentCommandResponse.PageText;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DocumentCommandServiceTest {

    @Test
    void returnsStructuredEmailResponseAndExtractsPdfOnce() {
        AtomicInteger extractionCount = new AtomicInteger();
        var document = new PdfExtractionService() {
            @Override
            public ExtractedDocument extract(org.springframework.web.multipart.MultipartFile file) {
                extractionCount.incrementAndGet();
                return new ExtractedDocument(1, List.of(new PageText(1, "a@example.com")));
            }
        };
        var service = new DocumentCommandService(document, new EmailExtractionService());

        Object response = service.process("sample.pdf", "find emails",
                new MockMultipartFile("document", "sample.pdf", "application/pdf", new byte[]{1}));

        var emailResponse = assertInstanceOf(EmailExtractionResponse.class, response);
        assertEquals(1, emailResponse.emails().size());
        assertEquals(1, extractionCount.get());
    }

    @Test
    void preservesFullTextResponseForOtherCommands() {
        var extracted = new PdfExtractionService.ExtractedDocument(1,
                List.of(new PageText(1, "document text")));
        var document = new PdfExtractionService() {
            @Override
            public ExtractedDocument extract(org.springframework.web.multipart.MultipartFile file) {
                return extracted;
            }
        };
        var service = new DocumentCommandService(document, new EmailExtractionService());

        Object response = service.process("sample.pdf", "read full text",
                new MockMultipartFile("document", "sample.pdf", "application/pdf", new byte[]{1}));

        var textResponse = assertInstanceOf(DocumentCommandResponse.class, response);
        assertEquals("document text", textResponse.pages().get(0).text());
    }
}
