package com.eliza.aicompetition.common;

import org.apache.pdfbox.Loader;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * Extracts plain text from binary file content (DOCX, PDF, XLSX)
 * stored in the file_asset.file_blob column, for LLM consumption.
 */
@Component
public class FileTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(FileTextExtractor.class);

    /** Safety cap — prevents huge extracted text from blowing up the LLM prompt. */
    private static final int CHAR_LIMIT = 100_000;

    private final Parser parser = new AutoDetectParser(TikaConfig.getDefaultConfig());
    private final PdfOcrExtractor pdfOcrExtractor;

    public FileTextExtractor(PdfOcrExtractor pdfOcrExtractor) {
        this.pdfOcrExtractor = pdfOcrExtractor;
    }

    /**
     * Extract plain text from binary file bytes.
     * For PDF files, falls back to OCR if Tika/PdfBox cannot extract text.
     *
     * @param fileBytes     the LONGBLOB content from file_asset.file_blob
     * @param fileExtension file extension without dot (e.g. "docx", "pdf", "xlsx"), may be null
     * @return extracted text, or an error-description string if extraction fails;
     *         never returns null
     */
    public String extractText(byte[] fileBytes, String fileExtension) {
        return extractTextMeasured(fileBytes, fileExtension).text();
    }

    /** OCR duration is included in totalMs; tikaMs and ocrMs identify the slow stage. */
    public record ExtractionResult(String text, long totalMs, long tikaMs, long ocrMs, boolean ocrAttempted,
                                   String path, long fileBytes, int pdfPages, int ocrAttemptedPages,
                                   int ocrSucceededPages, int ocrFailedPages, int ocrSkippedPages,
                                   int visionCalls, String failureReason) {
        public ExtractionResult(String text, long totalMs, long tikaMs, long ocrMs, boolean ocrAttempted) {
            this(text, totalMs, tikaMs, ocrMs, ocrAttempted, "UNKNOWN", 0, 0, 0, 0, 0, 0, 0, null);
        }
        public ExtractionResult(String text, long totalMs, long tikaMs, long ocrMs, boolean ocrAttempted,
                                String path, long fileBytes, int pdfPages, int ocrAttemptedPages,
                                int ocrSucceededPages, int ocrFailedPages, int ocrSkippedPages) {
            this(text, totalMs, tikaMs, ocrMs, ocrAttempted, path, fileBytes, pdfPages,
                ocrAttemptedPages, ocrSucceededPages, ocrFailedPages, ocrSkippedPages, 0, null);
        }
    }

    public ExtractionResult extractTextMeasured(byte[] fileBytes, String fileExtension) {
        return extractTextMeasured(fileBytes, fileExtension, null);
    }

    public ExtractionResult extractTextMeasured(byte[] fileBytes, String fileExtension, Long taskId) {
        long started = System.nanoTime();
        if (fileBytes == null || fileBytes.length == 0) {
            return new ExtractionResult("[File is empty]", elapsedMs(started), 0, 0, false);
        }

        boolean pdf = "pdf".equalsIgnoreCase(fileExtension);
        int pdfPages = 0;
        if (pdf) {
            try (var document = Loader.loadPDF(fileBytes)) {
                pdfPages = document.getNumberOfPages();
            } catch (IOException invalidPdf) {
                log.warn("PDF page count unavailable taskId={} errorType={}", taskId, invalidPdf.getClass().getSimpleName());
            }
        }

        // 1. Try Tika text extraction first
        long tikaStarted = System.nanoTime();
        String tikaText = extractWithTika(fileBytes, fileExtension, taskId);
        long tikaMs = elapsedMs(tikaStarted);

        // 2. If Tika failed and it's a PDF, try OCR fallback
        if (isExtractionFailure(tikaText) && pdf) {
            log.info("Tika failed to extract PDF text taskId={}; attempting OCR", taskId);
            long ocrStarted = System.nanoTime();
            PdfOcrExtractor.OcrResult ocr = pdfOcrExtractor.ocrPdfMeasured(fileBytes, taskId);
            String ocrText = ocr.text();
            long ocrMs = elapsedMs(ocrStarted);
            if (ocrText != null && !ocrText.isBlank()) {
                log.info("OCR fallback taskId={} successPages={} failedPages={} skippedPages={}",
                    taskId, ocr.succeededPages(), ocr.failedPages(), ocr.skippedPages());
                return new ExtractionResult(ocrText, elapsedMs(started), tikaMs, ocrMs, true,
                    "OCR", fileBytes.length, Math.max(pdfPages, ocr.totalPages()), ocr.attemptedPages(),
                    ocr.succeededPages(), ocr.failedPages(), ocr.skippedPages(), ocr.visionCalls(), ocr.failureReason());
            }
            log.warn("OCR fallback failed taskId={}", taskId);
            return new ExtractionResult("[OCR unavailable: " + ocr.failureReason() + "; manual text required]", elapsedMs(started), tikaMs, ocrMs, true,
                "OCR", fileBytes.length, Math.max(pdfPages, ocr.totalPages()), ocr.attemptedPages(),
                ocr.succeededPages(), ocr.failedPages(), ocr.skippedPages(), ocr.visionCalls(), ocr.failureReason());
        }

        return new ExtractionResult(tikaText, elapsedMs(started), tikaMs, 0, false,
            "TIKA", fileBytes.length, pdfPages, 0, 0, 0, 0);
    }

    private static long elapsedMs(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    /**
     * Core Tika extraction.
     */
    private String extractWithTika(byte[] fileBytes, String fileExtension, Long taskId) {
        try (InputStream input = new ByteArrayInputStream(fileBytes);
             TikaInputStream tikaInput = TikaInputStream.get(input)) {

            Metadata metadata = new Metadata();
            if (fileExtension != null && !fileExtension.isBlank()) {
                metadata.set(Metadata.CONTENT_TYPE, guessMimeType(fileExtension));
            }

            ContentHandler handler = new BodyContentHandler(CHAR_LIMIT);
            ParseContext context = new ParseContext();
            parser.parse(tikaInput, handler, metadata, context);

            String text = handler.toString().trim();
            if (text.isEmpty()) {
                return "[No extractable text found in file]";
            }
            return text;

        } catch (TikaException | IOException | SAXException e) {
            log.warn("Text extraction failed taskId={} ext={} errorType={}", taskId, fileExtension,
                e.getClass().getSimpleName());
            return "[Text extraction failed]";
        }
    }

    /**
     * Check if Tika extraction produced a failure placeholder rather than real content.
     */
    private boolean isExtractionFailure(String text) {
        return text.startsWith("[No extractable text")
            || text.startsWith("[Text extraction failed")
            || text.startsWith("[File is empty");
    }

    private String guessMimeType(String ext) {
        return switch (ext.toLowerCase()) {
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc"  -> "application/msword";
            case "pdf"  -> "application/pdf";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xls"  -> "application/vnd.ms-excel";
            case "txt"  -> "text/plain";
            default     -> "application/octet-stream";
        };
    }
}
