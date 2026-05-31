package realestate.server.application.rag.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import realestate.server.application.rag.domain.RagDocumentBuildResult;
import realestate.server.application.rag.domain.RagEmbeddingBuildResult;
import realestate.server.application.rag.domain.RagSyncResult;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagSyncService {

    private final RagDocumentBuildService documentBuildService;
    private final RagEmbeddingBuildService embeddingBuildService;

    public RagSyncResult syncDealDocumentsAndEmbeddings(
            int documentLimit,
            int embeddingLimit,
            String embeddingProvider,
            String embeddingModel
    ) {
        RagDocumentBuildResult documentResult = documentBuildService.buildDealDocuments(documentLimit);
        RagEmbeddingBuildResult embeddingResult = embeddingBuildService.buildDocumentEmbeddings(
                embeddingLimit,
                embeddingProvider,
                embeddingModel
        );

        log.info("RAG sync completed - documents: {}, provider: {}, model: {}, embedded: {}, skipped: {}, failed: {}",
                documentResult.upsertedCount(),
                embeddingResult.provider(),
                embeddingResult.model(),
                embeddingResult.embeddedCount(),
                embeddingResult.skippedCount(),
                embeddingResult.failedCount());
        return RagSyncResult.of(documentResult, embeddingResult);
    }
}
