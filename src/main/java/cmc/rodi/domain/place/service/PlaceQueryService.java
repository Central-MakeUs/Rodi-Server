package cmc.rodi.domain.place.service;

import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.place.dto.CourseDetailResponse;
import cmc.rodi.domain.place.dto.PlaceCoordinateResponse;
import cmc.rodi.domain.place.dto.PlaceListItem;
import cmc.rodi.domain.place.dto.PlaceListRequest;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.Parking;
import cmc.rodi.domain.place.entity.PlaceType;
import cmc.rodi.domain.place.repository.BookmarkRepository;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.place.repository.ParkingRepository;
import cmc.rodi.domain.place.repository.PlaceListRow;
import cmc.rodi.domain.place.repository.PlaceRepository;
import cmc.rodi.global.common.pagination.CursorCodec;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
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
    private final ParkingRepository parkingRepository;
    private final BookmarkRepository bookmarkRepository;

    /** 전체 place의 간단 좌표(마커용). 필터 없이 모두 반환한다. */
    @Transactional(readOnly = true)
    public List<PlaceCoordinateResponse> getAllCoordinates() {
        return placeRepository.findAll().stream().map(PlaceCoordinateResponse::from).toList();
    }

    /** 뷰포트 안 place(코스+주차장)를 현위치 거리순으로 커서 페이징(ADR 0010). */
    @Transactional(readOnly = true)
    public CursorPage<PlaceListItem> getPlaces(PlaceListRequest req) {
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
        Map<Long, Parking> parkingsById = loadParkings(page);
        List<PlaceListItem> items =
                page.stream().map(row -> toItem(row, coursesById, parkingsById)).toList();

        String nextCursor = null;
        if (hasNext) {
            PlaceListRow last = page.get(page.size() - 1);
            nextCursor = CursorCodec.encode(String.valueOf(last.getDistance()), last.getId());
        }

        // totalCount는 첫 페이지(커서 없음)에서만 계산 — 매 페이지 count 쿼리 방지
        if (cursor == null) {
            long totalCount =
                    placeRepository.countInViewport(
                            req.swLat(), req.swLng(), req.neLat(), req.neLng());
            return CursorPage.first(items, hasNext, nextCursor, totalCount);
        }
        return CursorPage.next(items, hasNext, nextCursor);
    }

    /** 코스 상세(#3). placeId가 코스가 아니거나 없으면 404. 북마크수·현재 회원 북마크 여부 포함. */
    @Transactional(readOnly = true)
    public CourseDetailResponse getCourseDetail(Long placeId, Long memberId) {
        // JOINED 상속: 주차장 id면 course 행이 없어 empty → 404 (엔드포인트-타입 검증)
        Course course =
                courseRepository
                        .findById(placeId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        long bookmarkCount = bookmarkRepository.countByPlaceId(placeId);
        boolean bookmarked = bookmarkRepository.existsByMemberIdAndPlaceId(memberId, placeId);
        return CourseDetailResponse.from(course, bookmarkCount, bookmarked);
    }

    /** 페이지의 코스들만 로드(태그·주행거리·설명 채우기용). */
    private Map<Long, Course> loadCourses(List<PlaceListRow> page) {
        return courseRepository.findAllById(idsOfType(page, PlaceType.COURSE)).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));
    }

    /** 페이지의 주차장들만 로드(주차면수·영업시간 채우기용). */
    private Map<Long, Parking> loadParkings(List<PlaceListRow> page) {
        return parkingRepository.findAllById(idsOfType(page, PlaceType.PARKING)).stream()
                .collect(Collectors.toMap(Parking::getId, Function.identity()));
    }

    private List<Long> idsOfType(List<PlaceListRow> page, PlaceType type) {
        return page.stream()
                .filter(r -> type.name().equals(r.getPlaceType()))
                .map(PlaceListRow::getId)
                .toList();
    }

    private PlaceListItem toItem(
            PlaceListRow row, Map<Long, Course> coursesById, Map<Long, Parking> parkingsById) {
        PlaceType type = PlaceType.valueOf(row.getPlaceType());
        long distanceFromMe = Math.round(row.getDistance());
        if (type == PlaceType.COURSE) {
            Course course = coursesById.get(row.getId());
            return new PlaceListItem(
                    row.getId(),
                    type,
                    row.getName(),
                    row.getAddress(),
                    row.getLat(),
                    row.getLng(),
                    distanceFromMe,
                    List.copyOf(course.getTags()), // 코스 연습 태그
                    course.getDescription(),
                    course.getDistanceMeters(),
                    null,
                    null);
        }
        Parking parking = parkingsById.get(row.getId());
        return new PlaceListItem(
                row.getId(),
                type,
                row.getName(),
                row.getAddress(),
                row.getLat(),
                row.getLng(),
                distanceFromMe,
                List.of(PracticeType.PARKING), // 주차장은 항상 주차
                null,
                null,
                parking.getCapacity(),
                openTime(parking.getWeekdayHours()));
    }

    /** 영업시간("00:00-23:59")에서 시작 시각만 추출. 없으면 null. */
    private String openTime(String weekdayHours) {
        if (weekdayHours == null || weekdayHours.isBlank()) {
            return null;
        }
        int dash = weekdayHours.indexOf('-');
        return dash < 0 ? weekdayHours : weekdayHours.substring(0, dash);
    }
}
