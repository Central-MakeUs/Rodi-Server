package cmc.rodi.domain.place.dto;

import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.place.entity.PracticeCategory;
import cmc.rodi.domain.place.entity.WaypointType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;

/**
 * 코스 등록 요청(스펙 014).
 *
 * <p><b>경로점을 하나의 배열로 받는다</b> — {@code waypoint} 테이블이 세 유형을 한 테이블에 담고, 등록 화면의 경로 목록·카카오맵 검색 결과도 순서
 * 있는 한 줄기라 매핑이 1:1이다. 대신 잘못된 조합(출발지 없음, 도착지가 중간에 있음 등)을 서버가 막는다.
 *
 * <p>좌표·주소·주행거리는 앱이 카카오맵에서 받은 값을 그대로 보낸다. 서버는 형식만 검증하고 저장한다.
 */
public record CourseRegisterRequest(
        @Schema(description = "코스명. 생략하면 출발지 지점명을 쓴다", example = "압구정로데오역") @Size(max = 255)
                String name,
        @Schema(description = "시/도 + 시군구", example = "서울특별시 강남구") @NotBlank @Size(max = 100)
                String address,
        @Schema(description = "주행거리(m). 앱의 길찾기 결과", example = "8200") @NotNull @Positive
                Integer distanceMeters,
        @Schema(description = "경로점(순서대로 START … DESTINATION)")
                @NotNull
                @Size(min = MIN_WAYPOINTS, max = MAX_WAYPOINTS)
                @Valid
                List<WaypointRequest> waypoints,
        @Schema(description = "연습유형(최대 3개)") @NotEmpty @Size(max = PracticeCategory.MAX_SELECT)
                List<PracticeType> practiceTypes,
        @Schema(description = "한줄 소개", example = "차선이 넓고, 직선 구간이 길어요.")
                @NotBlank
                @Size(min = DESCRIPTION_MIN_LENGTH, max = DESCRIPTION_MAX_LENGTH)
                String description,
        @Schema(description = "주의사항(선택)", example = "갑자기 나오는 자전거 주의!")
                @Size(max = CAUTION_MAX_LENGTH)
                String caution) {

    /** 출발지·도착지 2개 + 경유지 최대 3개. */
    public static final int MAX_VIA = 3;

    public static final int MIN_WAYPOINTS = 2;
    public static final int MAX_WAYPOINTS = MIN_WAYPOINTS + MAX_VIA;

    public static final int DESCRIPTION_MIN_LENGTH = 10;
    public static final int DESCRIPTION_MAX_LENGTH = 30;
    public static final int CAUTION_MAX_LENGTH = 100;

    public CourseRegisterRequest {
        name = blankToNull(name);
        caution = blankToNull(caution);
    }

    /** 경로점 하나. 지점명은 출발지만 필수다(코스명이 되기 때문). */
    public record WaypointRequest(
            @Schema(description = "유형") @NotNull WaypointType type,
            @Schema(description = "위도", example = "37.5273")
                    @NotNull
                    @DecimalMin("-90.0")
                    @DecimalMax("90.0")
                    Double lat,
            @Schema(description = "경도", example = "127.0403")
                    @NotNull
                    @DecimalMin("-180.0")
                    @DecimalMax("180.0")
                    Double lng,
            @Schema(description = "지점명", example = "압구정로데오역") @Size(max = 255) String name) {

        public WaypointRequest {
            name = blankToNull(name);
        }
    }

    /**
     * 경로 구성 검증 — 첫 항목이 출발지, 마지막이 도착지, 각각 정확히 1개, 가운데는 전부 경유지.
     *
     * <p>배열 길이는 {@code @Size}가 이미 막으므로 여기서는 순서·유형만 본다.
     */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "경로는 출발지로 시작해 도착지로 끝나야 하고, 가운데는 경유지여야 합니다")
    public boolean isRouteWellFormed() {
        if (waypoints == null || waypoints.size() < MIN_WAYPOINTS) {
            return true; // 다른 제약이 잡는다
        }
        if (waypoints.stream().anyMatch(w -> w.type() == null)) {
            return true; // @NotNull이 잡는다
        }
        int last = waypoints.size() - 1;
        for (int i = 0; i <= last; i++) {
            WaypointType expected =
                    i == 0
                            ? WaypointType.START
                            : (i == last ? WaypointType.DESTINATION : WaypointType.VIA);
            if (waypoints.get(i).type() != expected) {
                return false;
            }
        }
        return true;
    }

    /** 코스명의 기본값이 되므로 출발지 지점명은 반드시 있어야 한다. */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "출발지 지점명은 필수입니다")
    public boolean isStartNamed() {
        return waypoints == null || waypoints.isEmpty() || waypoints.get(0).name() != null;
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "연습유형은 중복될 수 없습니다")
    public boolean isPracticeTypesDistinct() {
        return practiceTypes == null || new HashSet<>(practiceTypes).size() == practiceTypes.size();
    }

    /** 저장할 코스명. 요청에 없으면 출발지 지점명을 쓴다(등록 화면에 코스명 입력란이 없다). */
    @JsonIgnore
    public String resolveName() {
        return name != null ? name : waypoints.get(0).name();
    }

    @JsonIgnore
    public WaypointRequest start() {
        return waypoints.get(0);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
