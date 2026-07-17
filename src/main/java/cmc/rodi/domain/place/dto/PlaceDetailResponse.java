package cmc.rodi.domain.place.dto;

import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.Parking;
import cmc.rodi.domain.place.entity.PlaceType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 장소 상세(통합). placeId가 타입을 결정하므로 엔드포인트를 나누지 않고, 공통 필드 + 타입별 블록(course·parking)으로 응답한다. 해당 없는 블록은
 * null이라 클라이언트는 {@code type}으로 분기한다.
 */
public record PlaceDetailResponse(
        @Schema(description = "장소 id") Long id,
        @Schema(description = "유형") PlaceType type,
        @Schema(description = "장소명") String name,
        @Schema(description = "시군구 단위 주소", example = "서울특별시 강남구") String address,
        @Schema(description = "위도") double lat,
        @Schema(description = "경도") double lng,
        @Schema(description = "연습 유형(코스=태그들, 주차장=[PARKING] 고정)") List<PracticeType> practiceTypes,
        @Schema(description = "북마크 수") long bookmarkCount,
        @Schema(description = "현재 회원의 북마크 여부") @JsonProperty("isBookmarked") boolean bookmarked,
        @Schema(description = "코스 블록(주차장이면 null)") CourseDetail course,
        @Schema(description = "주차장 블록(코스면 null)") ParkingDetail parking) {

    public static PlaceDetailResponse ofCourse(
            Course course, long bookmarkCount, boolean bookmarked) {
        // PostGIS는 x=경도, y=위도
        return new PlaceDetailResponse(
                course.getId(),
                PlaceType.COURSE,
                course.getName(),
                course.getAddress(),
                course.getLocation().getY(),
                course.getLocation().getX(),
                List.copyOf(course.getTags()), // 코스는 등록된 연습 태그
                bookmarkCount,
                bookmarked,
                CourseDetail.from(course),
                null);
    }

    public static PlaceDetailResponse ofParking(
            Parking parking, long bookmarkCount, boolean bookmarked) {
        return new PlaceDetailResponse(
                parking.getId(),
                PlaceType.PARKING,
                parking.getName(),
                parking.getAddress(),
                parking.getLocation().getY(),
                parking.getLocation().getX(),
                List.of(PracticeType.PARKING), // 주차장은 항상 주차
                bookmarkCount,
                bookmarked,
                null,
                ParkingDetail.from(parking));
    }
}
