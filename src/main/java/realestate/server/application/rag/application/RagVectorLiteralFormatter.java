package realestate.server.application.rag.application;

import java.util.List;
import java.util.stream.Collectors;

final class RagVectorLiteralFormatter {

    private RagVectorLiteralFormatter() {
    }

    static String toVectorLiteral(List<Double> embedding) {
        return embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }
}
