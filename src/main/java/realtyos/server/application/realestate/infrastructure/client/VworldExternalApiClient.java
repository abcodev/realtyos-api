package realtyos.server.application.realestate.infrastructure.client;

import realtyos.server.application.realestate.domain.SggCode;
import realtyos.server.application.realestate.domain.SggCodeClient;
import realtyos.server.application.realestate.domain.SggCodePage;
import realtyos.server.application.realestate.infrastructure.client.dto.VworldApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class VworldExternalApiClient implements SggCodeClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${external.api.realestate.vworld-service-key}")
    private String vworldServiceKey;

    @Override
    public SggCodePage fetchSggCodes(int page, int size) {
        RestClient restClient = restClientBuilder.build();

        URI uri = UriComponentsBuilder.fromUriString("https://api.vworld.kr/req/data")
                .queryParam("service", "data")
                .queryParam("request", "GetFeature")
                .queryParam("data", "LT_C_ADSIGG_INFO")
                .queryParam("key", vworldServiceKey)
                .queryParam("format", "json")
                .queryParam("geomFilter", "BOX(124,33,132,39)")
                .queryParam("page", page)
                .queryParam("size", size)
                .build()
                .toUri();

        log.info("Fetching Vworld SggCode data from: {}", uri);

        VworldApiResponse response = restClient.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(VworldApiResponse.class);
        return toPage(response);
    }

    private SggCodePage toPage(VworldApiResponse response) {
        if (response == null || response.getResponse() == null
                || !"OK".equals(response.getResponse().getStatus())) {
            return SggCodePage.invalid();
        }

        VworldApiResponse.Result result = response.getResponse().getResult();
        if (result == null || result.getFeatureCollection() == null
                || result.getFeatureCollection().getFeatures() == null) {
            return SggCodePage.invalid();
        }

        List<SggCode> codes = result.getFeatureCollection().getFeatures().stream()
                .filter(Objects::nonNull)
                .map(VworldApiResponse.Feature::getProperties)
                .filter(Objects::nonNull)
                .map(props -> SggCode.builder()
                        .sggCd(props.getSig_cd())
                        .fullNm(props.getFull_nm())
                        .sigKorNm(props.getSig_kor_nm())
                        .sigEngNm(props.getSig_eng_nm())
                        .build())
                .toList();
        VworldApiResponse.Page page = response.getResponse().getPage();
        if (page == null) {
            return new SggCodePage(codes, 1, 1, true);
        }
        return new SggCodePage(codes, parseInt(page.getCurrent(), 1), parseInt(page.getTotal(), 1), true);
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
