package cmc.rodi.dev;

import cmc.rodi.domain.place.dto.MarkerClusterRequest;
import cmc.rodi.domain.place.dto.MarkerClusterResponse;
import cmc.rodi.domain.place.service.MarkerClusterProperties;
import cmc.rodi.domain.place.service.MarkerClusterer;
import cmc.rodi.domain.place.service.PlaceQueryService;
import cmc.rodi.global.common.response.ApiResponse;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마커 클러스터링 기본값 튜닝(스펙 021) — 로컬 전용. 지도 페이지와, 슬라이더로 바꾼 설정값으로 실제 서버 계산({@link MarkerClusterer})을 돌리는
 * 엔드포인트를 준다.
 *
 * <p>{@code src/dev} 소스셋이라 배포 jar에 들어가지 않고, 혹시 올라가도 {@code local} 프로필에서만 등록된다. API 문서에는 노출하지 않는다.
 */
@Hidden
@Profile("local")
@RestController
@RequestMapping("/dev")
@RequiredArgsConstructor
public class ClusterTuningController {

    private final PlaceQueryService placeQueryService;
    private final MarkerClusterProperties defaults;

    @GetMapping(value = "/cluster-tuning", produces = MediaType.TEXT_HTML_VALUE)
    public Resource page() {
        return new ClassPathResource("dev/cluster-tuning.html");
    }

    /** 지금 {@code application.yml}에 들어 있는 기본값 — 페이지가 슬라이더 초기값으로 쓴다. */
    @GetMapping("/cluster-tuning/defaults")
    public MarkerClusterProperties defaults() {
        return defaults;
    }

    /** 실제 API({@code GET /api/v1/places/clusters})와 같은 계산을, 넘어온 설정값으로 한다. 비로그인 기준(필터 없음). */
    @GetMapping("/places/clusters")
    public ApiResponse<MarkerClusterResponse> clusters(
            @RequestParam double swLat,
            @RequestParam double swLng,
            @RequestParam double neLat,
            @RequestParam double neLng,
            @RequestParam(required = false) Integer targetColumns,
            @RequestParam(required = false) Double noClusterWidth,
            @RequestParam(required = false) Double clusterInsetRatio,
            @RequestParam(required = false) Integer maxCellLevel) {
        MarkerClusterProperties properties;
        try {
            properties =
                    new MarkerClusterProperties(
                            targetColumns != null ? targetColumns : defaults.targetColumns(),
                            noClusterWidth != null ? noClusterWidth : defaults.noClusterWidth(),
                            clusterInsetRatio != null
                                    ? clusterInsetRatio
                                    : defaults.clusterInsetRatio(),
                            maxCellLevel != null ? maxCellLevel : defaults.maxCellLevel());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return ApiResponse.success(
                placeQueryService.getClusters(
                        new MarkerClusterRequest(swLat, swLng, neLat, neLng),
                        null,
                        new MarkerClusterer(properties)));
    }
}
