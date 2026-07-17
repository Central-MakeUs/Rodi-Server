package cmc.rodi.domain.place.dto;

import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.WaypointType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 코스 상세(#3). 태그·주의사항·경로점(순서)·북마크수·북마크 여부. */
public record CourseDetailResponse(
        @Schema(description = "장소 id") Long id,
        @Schema(description = "코스명") String name,
        @Schema(description = "시군구 단위 주소", example = "서울특별시 영등포구") String address,
        @Schema(description = "설명") String description,
        @Schema(description = "주의사항(칩)") List<String> cautions,
        @Schema(description = "주행거리(m)") Integer distanceMeters,
        @Schema(description = "연습 유형") List<PracticeType> practiceTypes,
        @Schema(description = "경로점(sequence 오름차순)") List<WaypointItem> waypoints,
        @Schema(description = "북마크 수") long bookmarkCount,
        @Schema(description = "현재 회원의 북마크 여부") @JsonProperty("isBookmarked") boolean bookmarked) {

    /** 경로점. */
    public record WaypointItem(
            @Schema(description = "유형") WaypointType type,
            @Schema(description = "순서") short sequence,
            @Schema(description = "위도") double lat,
            @Schema(description = "경도") double lng,
            @Schema(description = "지점명(없을 수 있음)") String name) {}

    public static CourseDetailResponse from(Course course, long bookmarkCount, boolean bookmarked) {
        List<WaypointItem> waypoints =
                course.getWaypoints().stream()
                        .map(
                                w ->
                                        new WaypointItem(
                                                w.getWaypointType(),
                                                w.getSequence(),
                                                w.getLocation().getY(), // 위도
                                                w.getLocation().getX(), // 경도
                                                w.getName()))
                        .toList();
        return new CourseDetailResponse(
                course.getId(),
                course.getName(),
                course.getAddress(),
                course.getDescription(),
                List.copyOf(course.getCautions()),
                course.getDistanceMeters(),
                List.copyOf(course.getTags()),
                waypoints,
                bookmarkCount,
                bookmarked);
    }
}
