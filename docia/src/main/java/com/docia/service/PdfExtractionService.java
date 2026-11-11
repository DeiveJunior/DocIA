package com.docia.service;

import com.docia.dto.DocumentCommandResponse.PageText;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfExtractionService {

    public ExtractedDocument extract(MultipartFile file) {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            if (document.isEncrypted()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "PDF protegido por senha não é suportado.");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            List<PageText> pages = new ArrayList<>();
            boolean hasText = false;

            for (int pageNumber = 1; pageNumber <= document.getNumberOfPages(); pageNumber++) {
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                String text = stripper.getText(document).strip();
                if (!text.isBlank()) {
                    hasText = true;
                }
                pages.add(new PageText(pageNumber, text));
            }

            if (!hasText) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "O PDF não contém texto extraível. PDFs digitalizados exigem OCR, que não está disponível.");
            }

            return new ExtractedDocument(document.getNumberOfPages(), List.copyOf(pages));
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ResponseStatusException responseException) {
                throw responseException;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O arquivo não é um PDF válido ou está corrompido.", exception);
        }
    }

    public record ExtractedDocument(int pageCount, List<PageText> pages) {
    }
}
