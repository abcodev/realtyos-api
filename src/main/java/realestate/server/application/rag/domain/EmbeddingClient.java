package realestate.server.application.rag.domain;

import java.util.List;

public interface EmbeddingClient {

    EmbeddingProvider provider();

    String defaultModel();

    List<List<Double>> embed(String model, List<String> inputs);
}
