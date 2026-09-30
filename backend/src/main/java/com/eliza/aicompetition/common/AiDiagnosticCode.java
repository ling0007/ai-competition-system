package com.eliza.aicompetition.common;

/** Stable task diagnostics, independent of localized user-facing error summaries. */
public enum AiDiagnosticCode {
    API_KEY_MISSING,
    MODEL_HTTP_ERROR,
    MODEL_TIMEOUT,
    MODEL_TRANSPORT_ERROR,
    MODEL_INVALID_RESPONSE,
    MODEL_ERROR,
    UNSPECIFIED_FALLBACK,
    OCR_PARTIAL,
    OCR_PAGE_LIMIT,
    OCR_BYTE_LIMIT,
    OCR_CALL_LIMIT,
    OCR_BUDGET_EXHAUSTED,
    OCR_FAILED,
    INPUT_TOO_LONG,
    INPUT_UNREADABLE,
    NO_REVIEWABLE_CONTENT,
    INPUT_CHANGED,
    INVALID_MODEL_DATE,
    TASK_TIMEOUT,
    WORKER_INTERRUPTED,
    TASK_ERROR
}
