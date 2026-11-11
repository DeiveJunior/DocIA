package com.docia.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfExtractionServiceTest {

    private final PdfExtractionService service = new PdfExtractionService();

    @Test
    void extractsTextAndKeepsPageBoundaries() throws IOException {
        var result = service.extract(pdf("First page text", "Second page text"));

        assertEquals(2, result.pageCount());
        assertEquals(2, result.pages().size());
        assertTrue(result.pages().get(0).text().contains("First page text"));
        assertTrue(result.pages().get(1).text().contains("Second page text"));
        assertEquals(1, result.pages().get(0).pageNumber());
        assertEquals(2, result.pages().get(1).pageNumber());
    }

    @Test
    void rejectsCorruptedPdf() {
        var file = new MockMultipartFile("document", "broken.pdf", "application/pdf",
                "not a pdf".getBytes());

        var exception = assertThrows(ResponseStatusException.class, () -> service.extract(file));
        assertEquals(400, exception.getStatusCode().value());
    }

    @Test
    void rejectsPdfWithoutExtractableText() throws IOException {
        var file = pdfWithoutText();

        var exception = assertThrows(ResponseStatusException.class, () -> service.extract(file));
        assertEquals(422, exception.getStatusCode().value());
    }

    private MockMultipartFile pdf(String... pageTexts) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String pageText : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(50, 700);
                    content.showText(pageText);
                    content.endText();
                }
            }
            document.save(output);
            return new MockMultipartFile("document", "test.pdf", "application/pdf", output.toByteArray());
        }
    }

    private MockMultipartFile pdfWithoutText() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return new MockMultipartFile("document", "empty.pdf", "application/pdf", output.toByteArray());
        }
    }
}
