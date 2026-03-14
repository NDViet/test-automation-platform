package com.platform.ingestion.parser;

import com.platform.common.enums.SourceFormat;
import com.platform.ingestion.exception.ParseException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ResultParserFactory {

    private final Map<SourceFormat, ResultParser> parsers;

    public ResultParserFactory(List<ResultParser> parserList) {
        this.parsers = parserList.stream()
                .collect(Collectors.toMap(ResultParser::supportedFormat, Function.identity()));
    }

    public ResultParser getParser(SourceFormat format) {
        ResultParser parser = parsers.get(format);
        if (parser == null) {
            throw new ParseException("No parser registered for format: " + format, format);
        }
        return parser;
    }
}
