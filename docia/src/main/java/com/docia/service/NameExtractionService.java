package com.docia.service;

import com.docia.dto.DocumentCommandResponse.PageText;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NameExtractionService {

    private static final Pattern PERSON_FIELD_PATTERN = Pattern.compile(
            "(?iu)(nome\\s+completo|nome|respons[aá]vel(?:\\s+legal)?|representante(?:\\s+legal)?|assinado\\s+por|contratante|contratado)\\s*[:\\-]\\s*(.+?)\\s*(?=(?:\\r?\\n|;|$))"
    );
    private static final Pattern COMPANY_KEYWORD = Pattern.compile(
            "(?iu)\\b(?:empresa|institui[cç][aã]o|raz[aã]o\\s+social|nome\\s+fantasia|nome\\s+da\\s+empresa|ltda|s\\.?a\\.?|sociedade|epp|me\\.?|mei)\\b"
    );
    private static final Pattern LEADING_TITLE = Pattern.compile(
            "(?iu)^(?:sr\\.?|sra\\.?|srta\\.?|dr\\.?|dra\\.?|doutor|doutora|senhor|senhora|senhorita|gerente|diretor|diretora|presidente|coordenador|analista|engenheiro|advogado|contador|administrador)\\s+"
    );
    private static final Pattern TRAILING_COMPANY_SUFFIX = Pattern.compile(
            "(?iu)(?:\\s*[,;]|\\s*[-–—]\\s*)(?:\\b(?:empresa|institui[cç][aã]o|raz[aã]o\\s+social|nome\\s+fantasia|ltda|s\\.?a\\.?|sociedade|epp|me\\.?|mei)\\b.*)$"
    );

    public List<NameOccurrence> extract(List<PageText> pages) {
        List<NameOccurrence> results = new ArrayList<>();
        for (PageText page : pages) {
            Set<String> seenOnPage = new HashSet<>();
            Matcher matcher = PERSON_FIELD_PATTERN.matcher(page.text());
            while (matcher.find()) {
                String candidate = sanitize(matcher.group(2));
                if (candidate == null || candidate.isBlank()) {
                    continue;
                }
                String normalized = normalize(candidate);
                if (normalized.isBlank() || seenOnPage.contains(normalized)) {
                    continue;
                }
                if (!isLikelyPersonName(candidate)) {
                    continue;
                }
                seenOnPage.add(normalized);
                results.add(new NameOccurrence(candidate, page.pageNumber()));
            }
        }
        return List.copyOf(results);
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String candidate = value.strip();
        candidate = candidate.replaceAll("\\s*[-–—]\\s*(?=(?:nome(?:\\s+completo)?|respons[aá]vel|representante|assinado\\s+por|contratante|contratado)\\b)", "");
        candidate = candidate.replaceAll("\\s*[:;,.]+$", "");
        candidate = candidate.replaceAll("^['\"`]+|['\"`]+$", "").trim();
        candidate = stripLeadingTitles(candidate);
        candidate = candidate.replaceAll("\\s+", " ").trim();
        return candidate.isBlank() ? null : candidate;
    }

    private String stripLeadingTitles(String value) {
        String candidate = value;
        while (true) {
            Matcher matcher = LEADING_TITLE.matcher(candidate);
            if (!matcher.find()) {
                break;
            }
            String replacement = candidate.substring(matcher.end()).trim();
            if (replacement.equals(candidate)) {
                break;
            }
            candidate = replacement;
        }
        return candidate;
    }

    private boolean isLikelyPersonName(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String candidate = stripLeadingTitles(value).trim();
        if (candidate.isBlank()) {
            return false;
        }
        if (COMPANY_KEYWORD.matcher(candidate).find()) {
            return false;
        }
        if (TRAILING_COMPANY_SUFFIX.matcher(candidate).find()) {
            String beforeSuffix = TRAILING_COMPANY_SUFFIX.matcher(candidate).replaceFirst("").trim();
            if (!beforeSuffix.isBlank()) {
                candidate = beforeSuffix;
            }
        }
        candidate = candidate.replaceAll("[.,;]+$", "").trim();
        if (candidate.isBlank()) {
            return false;
        }
        if (candidate.matches("(?iu).*\\b(?:empresa|institui[cç][aã]o|raz[aã]o\\s+social|nome\\s+fantasia|ltda|s\\.?a\\.?|sociedade|epp|me\\.?|mei)\\b.*")) {
            return false;
        }

        String[] tokens = candidate.split("\\s+");
        if (tokens.length == 0) {
            return false;
        }
        for (String token : tokens) {
            if (token.matches("(?iu)[A-ZÀ-ÖØ-Þ]{2,}") && token.length() <= 2) {
                continue;
            }
            if (token.matches("(?iu)[0-9]+")) {
                return false;
            }
        }
        return candidate.chars().anyMatch(Character::isLetter);
    }

    private String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    public record NameOccurrence(String name, int pageNumber) {
    }
}
