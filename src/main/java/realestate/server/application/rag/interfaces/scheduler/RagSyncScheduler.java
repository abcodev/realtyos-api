package realestate.server.application.rag.interfaces.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import realestate.server.application.rag.application.RagSyncService;

@Slf4j
@Component
@RequiredArgsConstructor
public class RagSyncScheduler {

    private final RagSyncService syncService;

    @Value("${rag.sync.enabled:false}")
    private boolean enabled;

    @Value("${rag.sync.document-limit:0}")
    private int documentLimit;

    @Value("${rag.sync.embedding-limit:1000}")
    private int embeddingLimit;

    @Value("${rag.sync.embedding-provider:OLLAMA}")
    private String embeddingProvider;

    @Value("${rag.sync.embedding-model:nomic-embed-text}")
    private String embeddingModel;

    @Scheduled(cron = "${rag.sync.cron:0 30 4 * * ?}", zone = "${rag.sync.zone:Asia/Seoul}")
    public void syncDailyRagDocuments() {
        if (!enabled) {
            return;
        }

        try {
            syncService.syncDealDocumentsAndEmbeddings(
                    documentLimit,
                    embeddingLimit,
                    embeddingProvider,
                    embeddingModel
            );
        } catch (Exception e) {
            log.error("RAG 실거래가 문서/임베딩 동기화 실패", e);
        }
    }
}
