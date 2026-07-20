package cmc.rodi.domain.place.dto;

import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.Parking;
import cmc.rodi.domain.place.entity.PlaceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 목록 아이템(현위치 목록 #2·저장 목록 공용). 공통 필드 + 코스 전용(description·distanceMeters) + 주차장
 * 전용(capacity·openTime). {@code distanceFromMe}는 현위치 목록에서만 값이 있고, 저장 목록에선 null이다.
 */
public record PlaceListItem(
        @Schema(description = "장소 id") Long id,
        @Schema(description = "유형") PlaceType type,
        @Schema(description = "장소명") String name,
        @Schema(description = "시군구 단위 주소", example = "서울특별시 강남구") String address,
        @Schema(description = "위도") double lat,
        @Schema(description = "경도") double lng,
        @Schema(description = "현위치까지 거리(m). 저장 목록 등 현위치가 없으면 null") Long distanceFromMe,
        @Schema(description = "연습 유형(코스=태그들, 주차장=[PARKING])") List<PracticeType> practiceTypes,
        @Schema(description = "설명(코스만)") String description,
        @Schema(description = "코스 주행거리(m)(코스만)") Integer distanceMeters,
        @Schema(description = "총 주차면수(주차장만)") Integer capacity,
        @Schema(description = "영업시작 시각(주차장만)", example = "00:00") String openTime) {

    /** 코스 엔티티 → 아이템. {@code distanceFromMe}는 현위치 거리(없으면 null). */
    public static PlaceListItem ofCourse(Course course, Long distanceFromMe) {
        // PostGIS는 x=경도, y=위도
        return new PlaceListItem(
                course.getId(),
                PlaceType.COURSE,
                course.getName(),
                course.getAddress(),
                course.getLocation().getY(),
                course.getLocation().getX(),
                distanceFromMe,
                List.copyOf(course.getTags()),
                course.getDescription(),
                course.getDistanceMeters(),
                null,
                null);
    }

    /** 주차장 엔티티 → 아이템. 주차장은 연습유형이 항상 [PARKING]. */
    public static PlaceListItem ofParking(Parking parking, Long distanceFromMe) {
        return new PlaceListItem(
                parking.getId(),
                PlaceType.PARKING,
                parking.getName(),
                parking.getAddress(),
                parking.getLocation().getY(),
                parking.getLocation().getX(),
                distanceFromMe,
                List.of(PracticeType.PARKING),
                null,
                null,
                parking.getCapacity(),
                openTime(parking.getWeekdayHours()));
    }

    /** 영업시간("00:00-23:59")에서 시작 시각만 추출. 없으면 null. */
    private static String openTime(String weekdayHours) {
        if (weekdayHours == null || weekdayHours.isBlank()) {
            return null;
        }
        int dash = weekdayHours.indexOf('-');
        return dash < 0 ? weekdayHours : weekdayHours.substring(0, dash);
    }
}
