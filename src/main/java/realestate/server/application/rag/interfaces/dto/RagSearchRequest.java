package realestate.server.application.rag.interfaces.dto;

public record RagSearchRequest(
        String query,
        Integer topK
) {
}
