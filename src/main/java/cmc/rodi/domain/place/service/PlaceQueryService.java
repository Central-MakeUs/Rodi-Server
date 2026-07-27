package cmc.rodi.domain.place.service;

import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.member.service.MemberFilterService;
import cmc.rodi.domain.place.dto.PlaceCoordinateResponse;
import cmc.rodi.domain.place.dto.PlaceDetailResponse;
import cmc.rodi.domain.place.dto.PlaceListItem;
import cmc.rodi.domain.place.dto.PlaceListRequest;
import cmc.rodi.domain.place.dto.PlaceSearchRequest;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.Parking;
import cmc.rodi.domain.place.entity.Place;
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
    private final MemberFilterService memberFilterService;

    /** 전체 place의 간단 좌표(마커용). 필터 없이 모두 반환한다. */
    @Transactional(readOnly = true)
    public List<PlaceCoordinateResponse> getAllCoordinates() {
        return placeRepository.findAll().stream().map(PlaceCoordinateResponse::from).toList();
    }

    /**
     * 뷰포트 안 place(코스+주차장)를 커서 페이징(ADR 0010). 인증된 회원(memberId≠null)이 필터를 갖고 있으면 필터 매칭 우선→거리순, 아니면
     * 거리순만(비로그인 포함). memberId는 옵셔널 JWT라 null 가능.
     */
    @Transactional(readOnly = true)
    public CursorPage<PlaceListItem> getPlaces(PlaceListRequest req, Long memberId) {
        List<PracticeType> filter = resolveFilter(memberId);
        boolean firstPage = req.cursor() == null;

        if (filter.isEmpty()) {
            CursorCodec.Cursor cursor = firstPage ? null : CursorCodec.decode(req.cursor());
            List<PlaceListRow> rows =
                    placeRepository.findInViewport(
                            req.swLat(),
                            req.swLng(),
                            req.neLat(),
                            req.neLng(),
                            req.lat(),
                            req.lng(),
                            cursor == null ? null : parseCursorDistance(cursor.sortValue()),
                            cursor == null ? null : cursor.id(),
                            req.size() + 1);
            return assemble(
                    rows,
                    req.size(),
                    firstPage,
                    false,
                    () ->
                            placeRepository.countInViewport(
                                    req.swLat(), req.swLng(), req.neLat(), req.neLng()));
        }

        FilterCursor cursor = firstPage ? null : parseFilterCursor(req.cursor());
        List<PlaceListRow> rows =
                placeRepository.findInViewportFiltered(
                        req.swLat(),
                        req.swLng(),
                        req.neLat(),
                        req.neLng(),
                        req.lat(),
                        req.lng(),
                        typeNames(filter),
                        parkingFlag(filter),
                        cursor == null ? null : cursor.matched(),
                        cursor == null ? null : cursor.distance(),
                        cursor == null ? null : cursor.id(),
                        req.size() + 1);
        return assemble(
                rows,
                req.size(),
                firstPage,
                true,
                () ->
                        placeRepository.countInViewport(
                                req.swLat(), req.swLng(), req.neLat(), req.neLng()));
    }

    /**
     * 키워드 검색(스펙 007). 주소(시군구) 또는 장소명 부분 일치, 전국 대상. 인증된 회원이 필터를 가지면 필터 매칭 우선→거리순, 아니면 거리순만. 커서·아이템
     * 규칙은 목록(#2)과 동일.
     */
    @Transactional(readOnly = true)
    public CursorPage<PlaceListItem> searchPlaces(PlaceSearchRequest req, Long memberId) {
        List<PracticeType> filter = resolveFilter(memberId);
        boolean firstPage = req.cursor() == null;
        String pattern = req.likePattern();

        if (filter.isEmpty()) {
            CursorCodec.Cursor cursor = firstPage ? null : CursorCodec.decode(req.cursor());
            List<PlaceListRow> rows =
                    placeRepository.searchByKeyword(
                            pattern,
                            req.lat(),
                            req.lng(),
                            cursor == null ? null : parseCursorDistance(cursor.sortValue()),
                            cursor == null ? null : cursor.id(),
                            req.size() + 1);
            return assemble(
                    rows,
                    req.size(),
                    firstPage,
                    false,
                    () -> placeRepository.countByKeyword(pattern));
        }

        FilterCursor cursor = firstPage ? null : parseFilterCursor(req.cursor());
        List<PlaceListRow> rows =
                placeRepository.searchByKeywordFiltered(
                        pattern,
                        req.lat(),
                        req.lng(),
                        typeNames(filter),
                        parkingFlag(filter),
                        cursor == null ? null : cursor.matched(),
                        cursor == null ? null : cursor.distance(),
                        cursor == null ? null : cursor.id(),
                        req.size() + 1);
        return assemble(
                rows, req.size(), firstPage, true, () -> placeRepository.countByKeyword(pattern));
    }

    /** 인증된 회원의 저장 필터. 비로그인(memberId=null)이면 빈 목록 — 필터 없이 거리순(스펙 007: 필터는 로그인 전용). */
    private List<PracticeType> resolveFilter(Long memberId) {
        return memberId == null ? List.of() : memberFilterService.getFilterTags(memberId);
    }

    private static List<String> typeNames(List<PracticeType> filter) {
        return filter.stream().map(PracticeType::name).toList();
    }

    /** 주차장 매칭 플래그 — 필터에 PARKING이 있으면 1(주차장은 항상 PARKING이라 이 값으로 매칭). */
    private static int parkingFlag(List<PracticeType> filter) {
        return filter.contains(PracticeType.PARKING) ? 1 : 0;
    }

    /**
     * 조회 결과(size+1)를 페이지로 자르고 아이템·다음 커서·totalCount를 조립한다. {@code filtered}면 커서에 matched를 포함
     * (matched|distance), 아니면 distance만. totalCount는 첫 페이지에서만.
     */
    private CursorPage<PlaceListItem> assemble(
            List<PlaceListRow> rows,
            int size,
            boolean firstPage,
            boolean filtered,
            java.util.function.LongSupplier totalCountSupplier) {
        boolean hasNext = rows.size() > size;
        List<PlaceListRow> page = hasNext ? rows.subList(0, size) : rows;
        List<PlaceListItem> items = toItems(page);

        String nextCursor = null;
        if (hasNext) {
            PlaceListRow last = page.get(page.size() - 1);
            String sortValue =
                    filtered
                            ? last.getMatched() + "|" + last.getDistance()
                            : String.valueOf(last.getDistance());
            nextCursor = CursorCodec.encode(sortValue, last.getId());
        }

        if (firstPage) {
            return CursorPage.first(items, hasNext, nextCursor, totalCountSupplier.getAsLong());
        }
        return CursorPage.next(items, hasNext, nextCursor);
    }

    private List<PlaceListItem> toItems(List<PlaceListRow> page) {
        Map<Long, Course> coursesById = loadCourses(page);
        Map<Long, Parking> parkingsById = loadParkings(page);
        return page.stream().map(row -> toItem(row, coursesById, parkingsById)).toList();
    }

    /** 필터 커서 (matched, distance, id). */
    private record FilterCursor(int matched, double distance, long id) {}

    /** 필터 커서 파싱·검증. sortValue는 "matched|distance". 변조로 형식·값이 비정상이면 잘못된 커서(400). */
    private static FilterCursor parseFilterCursor(String rawCursor) {
        CursorCodec.Cursor cursor = CursorCodec.decode(rawCursor);
        String sortValue = cursor.sortValue();
        int sep = sortValue.indexOf('|');
        if (sep < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        int matched;
        double distance;
        try {
            matched = Integer.parseInt(sortValue.substring(0, sep));
            distance = Double.parseDouble(sortValue.substring(sep + 1));
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if ((matched != 0 && matched != 1) || !Double.isFinite(distance) || distance < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return new FilterCursor(matched, distance, cursor.id());
    }

    /** 커서의 거리값 파싱·검증. 변조로 숫자가 아니거나 비정상(NaN·무한·음수)이면 잘못된 커서로 본다. */
    private static Double parseCursorDistance(String sortValue) {
        double distance;
        try {
            distance = Double.parseDouble(sortValue);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (!Double.isFinite(distance) || distance < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return distance;
    }

    /** 장소 상세(#3·#4 통합). placeId가 타입을 결정하므로 조회 후 타입별 블록으로 응답한다. 없으면 404. */
    @Transactional(readOnly = true)
    public PlaceDetailResponse getPlaceDetail(Long placeId, Long memberId) {
        // JOINED 상속이라 findById가 Course/Parking 구현체로 반환된다
        Place place =
                placeRepository
                        .findById(placeId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        long bookmarkCount = bookmarkRepository.countByPlaceId(placeId);
        boolean bookmarked = bookmarkRepository.existsByMemberIdAndPlaceId(memberId, placeId);

        if (place instanceof Course course) {
            return PlaceDetailResponse.ofCourse(course, bookmarkCount, bookmarked);
        }
        if (place instanceof Parking parking) {
            return PlaceDetailResponse.ofParking(parking, bookmarkCount, bookmarked);
        }
        throw new IllegalStateException("알 수 없는 place 타입: " + place.getClass());
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
        long distanceFromMe = Math.round(row.getDistance());
        if (PlaceType.COURSE.name().equals(row.getPlaceType())) {
            return PlaceListItem.ofCourse(coursesById.get(row.getId()), distanceFromMe);
        }
        return PlaceListItem.ofParking(parkingsById.get(row.getId()), distanceFromMe);
    }
}
