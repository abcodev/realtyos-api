package realestate.server.application.rag.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import realestate.server.application.rag.domain.RagDocumentBuildResult;
import realestate.server.application.rag.domain.RagDocumentRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RagDocumentBuildService {

    private final RagDocumentRepository ragDocumentRepository;

    @Transactional
    public RagDocumentBuildResult buildDealDocuments(int limit) {
        int upsertedCount = ragDocumentRepository.buildDealDocuments(limit);
        log.info("RAG deal document build completed - upserted: {}, limit: {}", upsertedCount, limit);
        return new RagDocumentBuildResult(upsertedCount);
    }
}
