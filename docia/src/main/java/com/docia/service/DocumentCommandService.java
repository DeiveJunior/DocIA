package com.docia.service;

import com.docia.dto.DocumentCommandResponse;
import com.docia.dto.EmailExtractionResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentCommandService {

    private final PdfExtractionService pdfExtractionService;
    private final EmailExtractionService emailExtractionService;

    public DocumentCommandService(PdfExtractionService pdfExtractionService,
                                  EmailExtractionService emailExtractionService) {
        this.pdfExtractionService = pdfExtractionService;
        this.emailExtractionService = emailExtractionService;
    }

    public Object process(String filename, String command, MultipartFile document) {
        PdfExtractionService.ExtractedDocument extracted = pdfExtractionService.extract(document);
        if (requestsEmailExtraction(command)) {
            return new EmailExtractionResponse(
                    filename,
                    command,
                    "EXTRACTED",
                    extracted.pageCount(),
                    emailExtractionService.extract(extracted.pages())
            );
        }

        return new DocumentCommandResponse(
                filename,
                command,
                "EXTRACTED",
                extracted.pageCount(),
                extracted.pages()
        );
    }

    private boolean requestsEmailExtraction(String command) {
        return command.matches("(?is).*\\b(?:e[\\s-]?mails?)\\b.*");
    }
}
