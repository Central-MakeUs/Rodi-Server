package cmc.rodi.domain.place.dto;

import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.WaypointType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 장소 상세의 코스 전용 블록({@link PlaceDetailResponse#course()}). 주차장이면 null. */
public record CourseDetail(
        @Schema(description = "설명") String description,
        @Schema(description = "주의사항(칩, 입력 순서)") List<String> cautions,
        @Schema(description = "주행거리(m)") Integer distanceMeters,
        @Schema(description = "경로점(sequence 오름차순)") List<WaypointItem> waypoints) {

    /** 경로점. */
    public record WaypointItem(
            @Schema(description = "유형") WaypointType type,
            @Schema(description = "순서") short sequence,
            @Schema(description = "위도") double lat,
            @Schema(description = "경도") double lng,
            @Schema(description = "지점명(없을 수 있음)") String name) {}

    public static CourseDetail from(Course course) {
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
        return new CourseDetail(
                course.getDescription(),
                List.copyOf(course.getCautions()),
                course.getDistanceMeters(),
                waypoints);
    }
}
