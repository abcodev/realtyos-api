package realtyos.server.application.realestate.application.service;

import realtyos.server.application.realestate.domain.SggCodeClient;
import realtyos.server.application.realestate.domain.SggCodePage;
import realtyos.server.application.realestate.domain.SggCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SggCodeSyncService {

    private final SggCodeClient sggCodeClient;
    private final SggCodeRepository sggCodeRepository;

    public void syncSggCodes() {
        log.info("Vworld SggCode 데이터 수집 및 동기화 시작");

        int pageNum = 1;
        int pageSize = 1000;
        int totalSaved = 0;

        while (true) {
            SggCodePage page = sggCodeClient.fetchSggCodes(pageNum, pageSize);
            if (!page.valid()) {
                log.warn("Vworld API 응답이 없거나 상태가 정상이 아닙니다. Page: {}", pageNum);
                break;
            }

            sggCodeRepository.saveAll(page.codes());
            totalSaved += page.codes().size();

            log.info("Vworld SggCode 데이터 저장 완료 - Page: {}, Size: {}", pageNum, page.codes().size());

            if (page.currentPage() >= page.totalPages()) {
                break;
            }

            pageNum++;
        }

        log.info("Vworld SggCode 데이터 수집 및 동기화 종료 - 총 {} 건 저장/수정 완료", totalSaved);
    }
}
