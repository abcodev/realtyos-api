package realestate.server.application.realestate.interfaces.dto;

import realestate.server.application.realestate.domain.AptPblanc;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "아파트 공고 관심 정보 응답 DTO")
public record AptPblancSummaryResponse(
        @Schema(description = "ID")
        Long id,

        @Schema(description = "공급회사명")
        String companyName,
        @Schema(description = "공급유형")
        String type,
        @Schema(description = "이름")
        String houseName,
        @Schema(description = "주소")
        String address,
        @Schema(description = "지역명")
        String areaName,
        @Schema(description = "청약 시작일")
        String subscriptionStartDate,
        @Schema(description = "청약 종료일")
        String subscriptionEndDate,

        @Schema(description = "청약홈 url")
        String subscriptionHomeUrl
) {

    public static AptPblancSummaryResponse from(AptPblanc aptPblanc) {
        String earliestStartDate = getEarliestStartDate(aptPblanc);
        return new AptPblancSummaryResponse(
                aptPblanc.id(),
                aptPblanc.bsnsMbyNm(),
                aptPblanc.houseDtlSecdNm(),
                aptPblanc.houseNm(),
                aptPblanc.hssplyAdres(),
                aptPblanc.subscrptAreaCodeNm(),
                earliestStartDate,
                aptPblanc.rceptEndde(),
                aptPblanc.pblancUrl()
                );
    }

    private static String getEarliestStartDate(AptPblanc aptPblanc) {
        return java.util.stream.Stream.of(
                aptPblanc.rceptBgnde(),
                aptPblanc.spsplyRceptBgnde(),
                aptPblanc.gnrlRnk1CrspareaRcptde(),
                aptPblanc.gnrlRnk1EtcGgRcptde(),
                aptPblanc.gnrlRnk1EtcAreaRcptde(),
                aptPblanc.gnrlRnk2CrspareaRcptde(),
                aptPblanc.gnrlRnk2EtcGgRcptde(),
                aptPblanc.gnrlRnk2EtcAreaRcptde())
                .filter(java.util.Objects::nonNull)
                .filter(s -> !s.isBlank())
                .min(String::compareTo)
                .orElse("");
    }

}
