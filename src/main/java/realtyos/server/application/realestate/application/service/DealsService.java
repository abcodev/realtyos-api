package realtyos.server.application.realestate.application.service;

import realtyos.server.application.realestate.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DealsService {

    private static final DateTimeFormatter DEAL_YMD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    private final DataFetchPort fetchPort;
    private final DealsRepository repository;
    private final BgdCodeRepository bgdCodeRepository;

    public void fetchAndSaveDeals() {
//        String dealYmd = YearMonth.now().format(DEAL_YMD_FORMATTER);
        String dealYmd = "202604";
        List<String> bgdCodes = bgdCodeRepository.findDistinctBgdCodes();

        if (bgdCodes.isEmpty()) {
            log.warn("조회할 법정동 코드(bgdCodes)가 존재하지 않습니다.");
            return;
        }

        log.info("실거래 데이터 수집 시작 - 법정동 코드 {}건, 기준년월: {}", bgdCodes.size(), dealYmd);

        bgdCodes.parallelStream().forEach(bgdCode -> {
            try {

                // 신규 Deals 수집 및 저장
                List<Deals> deals = fetchPort.fetchDeals(bgdCode, dealYmd);

                if (!deals.isEmpty()) {
                    List<Deals> detailsWithSido = deals
                            .stream()
                            .map(detail -> Deals.builder()
                                    .id(detail.id())
                                    .sggCode(detail.sggCode())
                                    .umdCode(detail.umdCode())
                                    .landCode(detail.landCode())
                                    .bonbun(detail.bonbun())
                                    .bubun(detail.bubun())
                                    .roadName(detail.roadName())
                                    .roadNameSggCode(detail.roadNameSggCode())
                                    .roadNameCode(detail.roadNameCode())
                                    .roadNameSeq(detail.roadNameSeq())
                                    .roadNamebCode(detail.roadNamebCode())
                                    .roadNameBonbun(detail.roadNameBonbun())
                                    .roadNameBubun(detail.roadNameBubun())
                                    .umdName(detail.umdName())
                                    .aptName(detail.aptName())
                                    .jibun(detail.jibun())
                                    .excluUseArea(detail.excluUseArea())
                                    .dealYear(detail.dealYear())
                                    .dealMonth(detail.dealMonth())
                                    .dealDay(detail.dealDay())
                                    .dealAmount(detail.dealAmount())
                                    .floor(detail.floor())
                                    .buildYear(detail.buildYear())
                                    .aptSeq(detail.aptSeq())
                                    .cdealType(detail.cdealType())
                                    .cdealDay(detail.cdealDay())
                                    .dealingType(detail.dealingType())
                                    .estateAgentSggName(detail.estateAgentSggName())
                                    .rgstDate(detail.rgstDate())
                                    .aptDong(detail.aptDong())
                                    .slerType(detail.slerType())
                                    .buyerType(detail.buyerType())
                                    .landLeaseholdType(detail.landLeaseholdType())
                                    .createdAt(detail.createdAt())
                                    .build())
                            .toList();

                    repository.saveAll(detailsWithSido);
                } else {
                    log.warn("실거래 상세 데이터 없음 - 법정동 코드: {}, 기준년월: {}", bgdCode, dealYmd);
                }

            } catch (Exception e) {
                log.error("실거래 데이터 수집 중 오류 발생 - 법정동 코드: {}, 기준년월: {}", bgdCode, dealYmd, e);
            }
        });

        log.info("실거래 데이터 수집 종료 - 기준년월: {}", dealYmd);
    }

}
