package realtyos.server.application.rag.application;

final class RagModelName {

    private RagModelName() {
    }

    static String of(String provider, String model) {
        if (provider == null || provider.isBlank()) {
            return model == null || model.isBlank() ? null : model;
        }
        if (model == null || model.isBlank()) {
            return provider;
        }
        return provider + ":" + model;
    }
}
