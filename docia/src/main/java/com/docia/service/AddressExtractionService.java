package com.docia.service;

import com.docia.dto.AddressExtractionResponse.AddressOccurrence;
import com.docia.dto.DocumentCommandResponse.PageText;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AddressExtractionService {

    private static final String STREET_TYPES = "Rua|R\\.?|Avenida|Av\\.?|Travessa|Tv\\.?|Rodovia|Rod\\.?|"
            + "Alameda|Al\\.?|Estrada|Est\\.?|Praça|Pça\\.?|Praca|Largo|Viela|Servidão|Servidao|Via|Passagem";
    private static final Pattern STREET_LINE = Pattern.compile(
            "(?iu)\\b(?:" + STREET_TYPES + ")\\s+[^,;\\r\\n]+(?:[,;][^\\r\\n]*)?");
    private static final Pattern STREET_HEAD = Pattern.compile(
            "(?iu)^((?:" + STREET_TYPES + ")\\s+.+?)(?:\\s+(?:n[ºo.]?\\s*)?(\\d+[A-Za-z]?))?$");
    private static final Pattern NUMBER = Pattern.compile("(?iu)^(?:n[ºo.]?\\s*)?(\\d+[A-Za-z]?)$");
    private static final Pattern ZIP = Pattern.compile("(?<!\\d)(\\d{5}-?\\d{3})(?!\\d)");
    private static final Pattern STATE = Pattern.compile("(?iu)\\b(AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO)\\b");
    private static final Pattern LABEL = Pattern.compile("(?iu)^(bairro|cidade|município|municipio|estado|cep|número|numero|complemento)\\s*:\\s*(.+)$");
    private static final Pattern COMPLEMENT_START = Pattern.compile("(?iu)^(ap(?:artamento)?\\b|apt[ºo]?\\b|sala\\b|bloco\\b|casa\\b|fundos\\b|loja\\b|conjunto\\b|andar\\b|edifício\\b|edificio\\b)");

    public List<AddressOccurrence> extract(List<PageText> pages) {
        List<AddressOccurrence> results = new ArrayList<>();
        for (PageText page : pages) {
            Set<String> seenOnPage = new HashSet<>();
            Matcher matcher = STREET_LINE.matcher(page.text());
            while (matcher.find()) {
                AddressOccurrence address = parseAddress(matcher.group(), page.pageNumber());
                String key = normalize(address.street()) + "|" + normalize(address.number()) + "|"
                        + normalize(address.complement()) + "|" + normalize(address.neighborhood()) + "|"
                        + normalize(address.city()) + "|" + normalize(address.state()) + "|"
                        + normalize(address.zipCode());
                if (seenOnPage.add(key)) {
                    results.add(address);
                }
            }
        }
        return List.copyOf(results);
    }

    private AddressOccurrence parseAddress(String text, int pageNumber) {
        String[] parts = text.strip().split("\\s*[,;]\\s*");
        Matcher head = STREET_HEAD.matcher(parts[0].strip());
        String street = parts[0].strip();
        String number = null;
        if (head.matches()) {
            street = head.group(1).strip();
            number = head.group(2);
        }

        String complement = null;
        String neighborhood = null;
        String city = null;
        String state = null;
        String zipCode = null;

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].strip();
            if (part.isEmpty()) continue;

            Matcher label = LABEL.matcher(part);
            if (label.matches()) {
                String value = label.group(2).strip();
                switch (label.group(1).toLowerCase(Locale.ROOT)) {
                    case "bairro" -> neighborhood = value;
                    case "cidade", "município", "municipio" -> city = value;
                    case "estado" -> state = value;
                    case "cep" -> zipCode = value;
                    case "número", "numero" -> number = value;
                    case "complemento" -> complement = value;
                    default -> { }
                }
                continue;
            }

            Matcher zipMatcher = ZIP.matcher(part);
            if (zipMatcher.find()) {
                zipCode = zipMatcher.group(1);
                part = (part.substring(0, zipMatcher.start()) + part.substring(zipMatcher.end())).strip();
                part = part.replaceAll("^[,\\s-]+|[,\\s-]+$", "").strip();
            }
            Matcher stateMatcher = STATE.matcher(part);
            if (stateMatcher.find()) {
                state = stateMatcher.group(1).toUpperCase(Locale.ROOT);
                String cityPart = part.substring(0, stateMatcher.start()).replaceAll("[-,\\s]+$", "").strip();
                if (!cityPart.isEmpty()) city = cityPart;
                continue;
            }
            if (part.isEmpty()) continue;
            Matcher numberMatcher = NUMBER.matcher(part);
            if (numberMatcher.matches() && number == null) {
                number = numberMatcher.group(1);
            } else if (COMPLEMENT_START.matcher(part).find() && complement == null) {
                complement = part;
            } else if (neighborhood == null) {
                neighborhood = part;
            } else if (city == null) {
                city = part;
            }
        }

        return new AddressOccurrence(street, number, complement, neighborhood, city, state, zipCode, pageNumber);
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("\\s+", " ")
                .strip()
                .toLowerCase(Locale.ROOT);
    }
}
