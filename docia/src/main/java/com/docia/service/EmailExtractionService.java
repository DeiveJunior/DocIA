package com.docia.service;

import com.docia.dto.DocumentCommandResponse.PageText;
import com.docia.dto.EmailExtractionResponse.EmailOccurrence;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EmailExtractionService {

    // Deliberately excludes dots and separators at the start/end of local parts and domain labels,
    // so sentence punctuation adjacent to an address is not included in the match.
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?i)(?<![A-Z0-9._%+-])([A-Z0-9]+(?:[._%+-][A-Z0-9]+)*@"
                    + "[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?"
                    + "(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+)(?![A-Z0-9-])");

    public List<EmailOccurrence> extract(List<PageText> pages) {
        List<EmailOccurrence> occurrences = new ArrayList<>();
        for (PageText page : pages) {
            Set<String> seenOnPage = new HashSet<>();
            Matcher matcher = EMAIL_PATTERN.matcher(page.text());
            while (matcher.find()) {
                String email = matcher.group(1);
                if (seenOnPage.add(email.toLowerCase(Locale.ROOT))) {
                    occurrences.add(new EmailOccurrence(email, page.pageNumber()));
                }
            }
        }
        return List.copyOf(occurrences);
    }
}
