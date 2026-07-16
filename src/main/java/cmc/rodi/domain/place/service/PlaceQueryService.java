package cmc.rodi.domain.place.service;

import cmc.rodi.domain.place.dto.PlaceCoordinateResponse;
import cmc.rodi.domain.place.dto.PlaceListItem;
import cmc.rodi.domain.place.dto.PlaceListRequest;
import cmc.rodi.domain.place.dto.PlaceListResponse;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.PlaceType;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.place.repository.PlaceListRow;
import cmc.rodi.domain.place.repository.PlaceRepository;
import cmc.rodi.global.common.pagination.CursorCodec;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 장소 조회. 지도 마커 전체 좌표(#1)와 현위치 거리순 목록(#2)을 제공한다. */
@Service
@RequiredArgsConstructor
public class PlaceQueryService {

    private final PlaceRepository placeRepository;
    private final CourseRepository courseRepository;

    /** 전체 place의 간단 좌표(마커용). 필터 없이 모두 반환한다. */
    @Transactional(readOnly = true)
    public List<PlaceCoordinateResponse> getAllCoordinates() {
        return placeRepository.findAll().stream().map(PlaceCoordinateResponse::from).toList();
    }

    /** 뷰포트 안 place(코스+주차장)를 현위치 거리순으로 커서 페이징(ADR 0010). */
    @Transactional(readOnly = true)
    public PlaceListResponse getPlaces(PlaceListRequest req) {
        CursorCodec.Cursor cursor = req.cursor() == null ? null : CursorCodec.decode(req.cursor());
        Double cursorDistance = cursor == null ? null : Double.parseDouble(cursor.sortValue());
        Long cursorId = cursor == null ? null : cursor.id();

        // size+1 조회로 다음 페이지 존재 판별
        List<PlaceListRow> rows =
                placeRepository.findInViewport(
                        req.swLat(),
                        req.swLng(),
                        req.neLat(),
                        req.neLng(),
                        req.lat(),
                        req.lng(),
                        cursorDistance,
                        cursorId,
                        req.size() + 1);

        boolean hasNext = rows.size() > req.size();
        List<PlaceListRow> page = hasNext ? rows.subList(0, req.size()) : rows;

        Map<Long, Course> coursesById = loadCourses(page);
        List<PlaceListItem> items = page.stream().map(row -> toItem(row, coursesById)).toList();

        String nextCursor = null;
        if (hasNext) {
            PlaceListRow last = page.get(page.size() - 1);
            nextCursor = CursorCodec.encode(String.valueOf(last.getDistance()), last.getId());
        }

        long totalCount =
                placeRepository.countInViewport(req.swLat(), req.swLng(), req.neLat(), req.neLng());
        return new PlaceListResponse(totalCount, items, nextCursor);
    }

    /** 페이지의 코스들만 로드(태그·주행거리 채우기용). */
    private Map<Long, Course> loadCourses(List<PlaceListRow> page) {
        List<Long> courseIds =
                page.stream()
                        .filter(r -> PlaceType.COURSE.name().equals(r.getPlaceType()))
                        .map(PlaceListRow::getId)
                        .toList();
        return courseRepository.findAllById(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));
    }

    private PlaceListItem toItem(PlaceListRow row, Map<Long, Course> coursesById) {
        PlaceType type = PlaceType.valueOf(row.getPlaceType());
        long distanceFromMe = Math.round(row.getDistance());
        if (type == PlaceType.COURSE) {
            Course course = coursesById.get(row.getId());
            return new PlaceListItem(
                    row.getId(),
                    type,
                    row.getName(),
                    row.getDescription(),
                    row.getLat(),
                    row.getLng(),
                    distanceFromMe,
                    List.copyOf(course.getTags()),
                    course.getDistanceMeters());
        }
        return new PlaceListItem(
                row.getId(),
                type,
                row.getName(),
                row.getDescription(),
                row.getLat(),
                row.getLng(),
                distanceFromMe,
                null,
                null);
    }
}
