package com.eliza.aicompetition.service;

import com.eliza.aicompetition.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NoticeDeadlineParsingTests {

    @Test
    void explicitTimeIsPreservedAcrossSupportedFormats() {
        LocalDateTime expected = LocalDateTime.of(2027, 6, 30, 18, 0);
        for (String value : new String[] {
            "2027-06-30 18:00", "2027/06/30 18:00",
            "2027年06月30日 18:00", "2027.06.30 18:00",
            "2027-06-30T18:00:00"
        }) {
            assertEquals(expected, NoticeService.parseDeadline(value), value);
        }
    }

    @Test
    void onlyDateGetsDefaultEndOfDay() {
        LocalDateTime expected = LocalDateTime.of(2027, 6, 30, 23, 59, 59);
        for (String value : new String[] {
            "2027-06-30", "2027/06/30", "2027年06月30日", "2027.06.30"
        }) {
            assertEquals(expected, NoticeService.parseDeadline(value), value);
        }
        assertNull(NoticeService.parseDeadline(null));
    }

    @Test
    void invalidDatesDoNotSilentlyNormalizeOrBecomeDateOnly() {
        assertThrows(BusinessException.class, () -> NoticeService.parseDeadline("2027-02-30 18:00"));
        assertThrows(BusinessException.class, () -> NoticeService.parseDeadline("2027-06-30 25:00"));
        assertThrows(BusinessException.class, () -> NoticeService.parseDeadline("2027-06-30 rubbish"));
    }
}
