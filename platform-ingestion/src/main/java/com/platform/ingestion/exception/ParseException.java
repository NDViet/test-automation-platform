package com.platform.ingestion.exception;

import com.platform.common.enums.SourceFormat;

public class ParseException extends RuntimeException {

    private final SourceFormat format;

    public ParseException(String message, SourceFormat format) {
        super(message);
        this.format = format;
    }

    public ParseException(String message, SourceFormat format, Throwable cause) {
        super(message, cause);
        this.format = format;
    }

    public SourceFormat getFormat() { return format; }
}
