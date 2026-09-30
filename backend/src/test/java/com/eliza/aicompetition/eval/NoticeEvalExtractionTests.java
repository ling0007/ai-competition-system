package com.eliza.aicompetition.eval;

import com.eliza.aicompetition.common.FileTextExtractor;
import com.eliza.aicompetition.common.PdfOcrExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoticeEvalExtractionTests {
    @Test
    void txtUsesTikaWithoutVision() throws Exception {
        JsonNode sample = NoticeEvalGold.load().path("samples").get(0);
        PdfOcrExtractor ocr = mock(PdfOcrExtractor.class);
        var extracted = new FileTextExtractor(ocr).extractTextMeasured(
            Files.readAllBytes(NoticeEvalGold.samplePath(sample)), "txt");
        assertFalse(extracted.ocrAttempted());
        assertEquals("TIKA", extracted.path());
        assertTrue(extracted.text().contains("申报"));
        verify(ocr, never()).ocrPdfMeasured(any(), any());
    }

    @Test
    void textPdfUsesOriginalBytesAndDoesNotCallVision() throws Exception {
        JsonNode sample = NoticeEvalGold.load().path("samples").get(1);
        PdfOcrExtractor ocr = mock(PdfOcrExtractor.class);
        var extracted = new FileTextExtractor(ocr).extractTextMeasured(
            Files.readAllBytes(NoticeEvalGold.samplePath(sample)), "pdf");
        assertFalse(extracted.ocrAttempted());
        assertTrue(extracted.text().length() > 100);
        assertTrue(extracted.tikaMs() >= 0);
        assertEquals("TIKA", extracted.path());
        assertEquals(4, extracted.pdfPages());
        verify(ocr, never()).ocrPdfMeasured(any(), any());
    }

    @Test
    void scannedPdfUsesOriginalBytesAndEntersVisionFallback() throws Exception {
        JsonNode sample = NoticeEvalGold.load().path("samples").get(2);
        PdfOcrExtractor ocr = mock(PdfOcrExtractor.class);
        when(ocr.ocrPdfMeasured(any(), org.mockito.ArgumentMatchers.isNull())).thenReturn(
            new PdfOcrExtractor.OcrResult("OCR fixture text", 10, 3, 3, 0, 7));
        var extracted = new FileTextExtractor(ocr).extractTextMeasured(
            Files.readAllBytes(NoticeEvalGold.samplePath(sample)), "pdf");
        assertTrue(extracted.ocrAttempted());
        assertTrue(extracted.text().contains("OCR fixture text"));
        assertEquals(10, extracted.pdfPages());
        assertEquals(3, extracted.ocrSucceededPages());
        assertEquals(7, extracted.ocrSkippedPages());
        verify(ocr).ocrPdfMeasured(any(), org.mockito.ArgumentMatchers.isNull());
    }
}
