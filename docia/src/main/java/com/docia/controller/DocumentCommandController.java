package com.docia.controller;

import com.docia.service.DocumentCommandService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/documents")
public class DocumentCommandController {

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private final DocumentCommandService documentCommandService;

    public DocumentCommandController(DocumentCommandService documentCommandService) {
        this.documentCommandService = documentCommandService;
    }

    @PostMapping(value = "/command", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object receiveDocumentAndCommand(
            @RequestPart("document") MultipartFile document,
            @RequestPart("command") String command) {

        if (document == null || document.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie um arquivo PDF não vazio.");
        }

        String originalName = document.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".pdf")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O arquivo deve ter extensão .pdf.");
        }

        if (document.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "O arquivo excede o tamanho máximo permitido de 20MB.");
        }

        String contentType = document.getContentType();
        if (contentType != null && !contentType.equalsIgnoreCase(MediaType.APPLICATION_PDF_VALUE)
                && !contentType.equalsIgnoreCase(MediaType.APPLICATION_OCTET_STREAM_VALUE)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O arquivo enviado não está em formato PDF válido.");
        }

        if (command == null || command.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe o comando a ser executado.");
        }

        return documentCommandService.process(originalName, command.trim(), document);
    }
}
