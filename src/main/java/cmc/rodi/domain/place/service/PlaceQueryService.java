package cmc.rodi.domain.place.service;

import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.member.service.MemberSortService;
import cmc.rodi.domain.place.dto.MarkerClusterRequest;
import cmc.rodi.domain.place.dto.MarkerClusterResponse;
import cmc.rodi.domain.place.dto.PlaceCoordinateResponse;
import cmc.rodi.domain.place.dto.PlaceDetailResponse;
import cmc.rodi.domain.place.dto.PlaceListItem;
import cmc.rodi.domain.place.dto.PlaceListRequest;
import cmc.rodi.domain.place.dto.PlaceSearchRequest;
import cmc.rodi.domain.place.dto.PlaceSuggestion;
import cmc.rodi.domain.place.dto.RelatedSearchRequest;
import cmc.rodi.domain.place.dto.RelatedSearchResponse;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.Parking;
import cmc.rodi.domain.place.entity.Place;
import cmc.rodi.domain.place.entity.PlaceType;
import cmc.rodi.domain.place.exception.CourseErrorCode;
import cmc.rodi.domain.place.repository.BookmarkRepository;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.place.repository.ParkingRepository;
import cmc.rodi.domain.place.repository.PlaceListRow;
import cmc.rodi.domain.place.repository.PlaceMarkerRow;
import cmc.rodi.domain.place.repository.PlaceNameMatchRow;
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
    private final MemberSortService memberSortService;
    private final RegionSearchIndex regionSearchIndex;
    private final MarkerClusterer markerClusterer;

    /**
     * 전체 place의 간단 좌표(마커용). 미승인·삭제된 코스는 제외한다(스펙 014). {@code practiceTypes}가 있으면 그에 맞는 place만(스펙
     * 019), 비어 있으면 전부. 순서가 의미 없어 sort_tags는 쓰지 않는다.
     */
    @Transactional(readOnly = true)
    public List<PlaceCoordinateResponse> getAllCoordinates(List<PracticeType> practiceTypes) {
        List<Place> places =
                practiceTypes.isEmpty()
                        ? placeRepository.findAllVisible()
                        : placeRepository.findAllVisibleFiltered(
                                practiceTypes, practiceTypes.contains(PracticeType.PARKING));
        return places.stream().map(PlaceCoordinateResponse::from).toList();
    }

    /**
     * 마커 클러스터 검색(스펙 021). 검색 범위의 장소와 확대 단계별 묶음을 한 번에 준다. 인증된 회원이 필터를 가지면 필터에 맞는 장소만 묶는다(비로그인·빈 필터는
     * 전부). memberId는 옵셔널 JWT라 null 가능.
     */
    @Transactional(readOnly = true)
    public MarkerClusterResponse getClusters(MarkerClusterRequest req, Long memberId) {
        return getClusters(req, memberId, markerClusterer);
    }

    /** 클러스터러를 바꿔 끼우는 판 — 로컬 튜닝 페이지가 슬라이더로 바꾼 설정값으로 계산할 때 쓴다. */
    @Transactional(readOnly = true)
    public MarkerClusterResponse getClusters(
            MarkerClusterRequest req, Long memberId, MarkerClusterer clusterer) {
        List<PracticeType> filter = resolveSortTags(memberId);
        List<PlaceMarkerRow> rows =
                filter.isEmpty()
                        ? placeRepository.findMarkersInBounds(
                                req.swLat(), req.swLng(), req.neLat(), req.neLng())
                        : placeRepository.findMarkersInBoundsFiltered(
                                req.swLat(),
                                req.swLng(),
                                req.neLat(),
                                req.neLng(),
                                filter.stream().map(PracticeType::name).toList(),
                                filter.contains(PracticeType.PARKING) ? 1 : 0);
        List<MarkerClusterer.Point> points =
                rows.stream()
                        .map(r -> new MarkerClusterer.Point(r.getId(), r.getLat(), r.getLng()))
                        .toList();
        return new MarkerClusterResponse(
                rows.stream().map(PlaceCoordinateResponse::from).toList(),
                clusterer.cluster(points));
    }

    /**
     * 뷰포트 안 place(코스+주차장)를 커서 페이징(ADR 0010). 필터와 정렬을 함께 적용한다(스펙 019).
     *
     * <ul>
     *   <li>필터 — {@code practiceTypes} 파라미터가 있으면 맞는 place만, 없으면 전부.
     *   <li>정렬 — 인증된 회원(memberId≠null)의 sort_tags가 있으면 맞는 place가 먼저, 그다음 나머지. 같은 그룹 안은 거리순.
     * </ul>
     *
     * memberId는 옵셔널 JWT라 null 가능(비로그인은 정렬 없음).
     */
    @Transactional(readOnly = true)
    public CursorPage<PlaceListItem> getPlaces(PlaceListRequest req, Long memberId) {
        TypeCriteria filter = TypeCriteria.of(req.practiceTypes());
        TypeCriteria sort = TypeCriteria.of(resolveSortTags(memberId));
        PlaceCursor cursor = req.cursor() == null ? null : parseCursor(req.cursor());
        List<PlaceListRow> rows =
                placeRepository.findInViewport(
                        req.swLat(),
                        req.swLng(),
                        req.neLat(),
                        req.neLng(),
                        req.lat(),
                        req.lng(),
                        filter.used(),
                        filter.names(),
                        filter.parkingFlag(),
                        sort.used(),
                        sort.names(),
                        sort.parkingFlag(),
                        cursor == null ? null : cursor.matched(),
                        cursor == null ? null : cursor.distance(),
                        cursor == null ? null : cursor.id(),
                        req.size() + 1);
        return assemble(
                rows,
                req.size(),
                cursor == null,
                () ->
                        placeRepository.countInViewport(
                                req.swLat(),
                                req.swLng(),
                                req.neLat(),
                                req.neLng(),
                                filter.used(),
                                filter.names(),
                                filter.parkingFlag()));
    }

    /**
     * 키워드 검색(스펙 007). 주소(시군구) 또는 장소명 부분 일치, 전국 대상. 필터·정렬과 커서·아이템 규칙은 목록({@link #getPlaces})과 동일.
     */
    @Transactional(readOnly = true)
    public CursorPage<PlaceListItem> searchPlaces(PlaceSearchRequest req, Long memberId) {
        TypeCriteria filter = TypeCriteria.of(req.practiceTypes());
        TypeCriteria sort = TypeCriteria.of(resolveSortTags(memberId));
        String pattern = req.likePattern();
        PlaceCursor cursor = req.cursor() == null ? null : parseCursor(req.cursor());
        List<PlaceListRow> rows =
                placeRepository.searchByKeyword(
                        pattern,
                        req.lat(),
                        req.lng(),
                        filter.used(),
                        filter.names(),
                        filter.parkingFlag(),
                        sort.used(),
                        sort.names(),
                        sort.parkingFlag(),
                        cursor == null ? null : cursor.matched(),
                        cursor == null ? null : cursor.distance(),
                        cursor == null ? null : cursor.id(),
                        req.size() + 1);
        return assemble(
                rows,
                req.size(),
                cursor == null,
                () ->
                        placeRepository.countByKeyword(
                                pattern, filter.used(), filter.names(), filter.parkingFlag()));
    }

    /**
     * 연관 검색어(스펙 009). 지역은 관련도순 최대 4개(첫 페이지에서만, 페이지네이션 없음), 장소명은 이름 관련도순(POSITION) 커서 페이지. 코스+주차장 전부
     * 대상.
     */
    @Transactional(readOnly = true)
    public RelatedSearchResponse relatedSearch(RelatedSearchRequest req) {
        boolean firstPage = req.cursor() == null;
        List<String> regions = firstPage ? regionSearchIndex.search(req.keyword()) : List.of();

        String pattern = req.likePattern();
        CursorCodec.Cursor cursor = firstPage ? null : CursorCodec.decode(req.cursor());
        Integer cursorMatchPos = cursor == null ? null : parseCursorMatchPos(cursor.sortValue());
        Long cursorId = cursor == null ? null : cursor.id();

        List<PlaceNameMatchRow> rows =
                placeRepository.searchByNameRelevance(
                        pattern, req.keyword(), cursorMatchPos, cursorId, req.size() + 1);
        boolean hasNext = rows.size() > req.size();
        List<PlaceNameMatchRow> page = hasNext ? rows.subList(0, req.size()) : rows;
        List<PlaceSuggestion> items =
                page.stream()
                        .map(r -> new PlaceSuggestion(r.getId(), r.getName(), r.getAddress()))
                        .toList();

        String nextCursor = null;
        if (hasNext) {
            PlaceNameMatchRow last = page.get(page.size() - 1);
            nextCursor = CursorCodec.encode(String.valueOf(last.getMatchPos()), last.getId());
        }

        CursorPage<PlaceSuggestion> places =
                firstPage
                        ? CursorPage.first(
                                items,
                                hasNext,
                                nextCursor,
                                placeRepository.countByNameRelevance(pattern))
                        : CursorPage.next(items, hasNext, nextCursor);
        return new RelatedSearchResponse(regions, places);
    }

    /**
     * 연관 검색어 커서의 matchPos 파싱·검증. PostgreSQL POSITION()은 1-based라 유효한 매칭 위치는 항상 1 이상이다(0은 "매칭 안 됨"을
     * 뜻해 실제 결과에 나올 수 없는 값). 변조로 숫자가 아니거나 1 미만이면 잘못된 커서로 본다.
     */
    private static Integer parseCursorMatchPos(String sortValue) {
        int matchPos;
        try {
            matchPos = Integer.parseInt(sortValue);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, e);
        }
        if (matchPos < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return matchPos;
    }

    /** 인증된 회원의 정렬 기준(sort_tags). 비로그인(memberId=null)이면 빈 목록 — 정렬 없이 거리순. */
    private List<PracticeType> resolveSortTags(Long memberId) {
        return memberId == null ? List.of() : memberSortService.getSortTags(memberId);
    }

    /**
     * 쿼리에 넘길 연습유형 기준(필터 또는 정렬). 기준이 없으면 {@code used=false}로 끈다. 이때도 빈 {@code IN ()}은 SQL 오류라 어떤
     * 태그와도 맞지 않는 자리표시 값을 넘긴다.
     */
    private record TypeCriteria(boolean used, List<String> names, int parkingFlag) {

        private static final List<String> UNUSED = List.of("__UNUSED__");

        static TypeCriteria of(List<PracticeType> types) {
            if (types.isEmpty()) {
                return new TypeCriteria(false, UNUSED, 0);
            }
            // 주차장은 항상 PARKING이라 이 플래그로 매칭한다
            return new TypeCriteria(
                    true,
                    types.stream().map(PracticeType::name).toList(),
                    types.contains(PracticeType.PARKING) ? 1 : 0);
        }
    }

    /**
     * 조회 결과(size+1)를 페이지로 자르고 아이템·다음 커서(matched|distance)·totalCount를 조립한다. totalCount는 첫 페이지에서만.
     */
    private CursorPage<PlaceListItem> assemble(
            List<PlaceListRow> rows,
            int size,
            boolean firstPage,
            java.util.function.LongSupplier totalCountSupplier) {
        boolean hasNext = rows.size() > size;
        List<PlaceListRow> page = hasNext ? rows.subList(0, size) : rows;
        List<PlaceListItem> items = toItems(page);

        String nextCursor = null;
        if (hasNext) {
            PlaceListRow last = page.get(page.size() - 1);
            nextCursor =
                    CursorCodec.encode(last.getMatched() + "|" + last.getDistance(), last.getId());
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

    /** 목록·검색 커서 (matched, distance, id). */
    private record PlaceCursor(int matched, double distance, long id) {}

    /**
     * 목록·검색 커서 파싱·검증. sortValue는 "matched|distance". 거리만 있는 형식("distance")도 matched=0으로 받는다 — 스펙
     * 019 이전 서버가 정렬 없이 발급한 커서이고, 그때는 모든 행이 matched=0이라 해석이 정확하다(배포 순간 스크롤이 끊기지 않게). 변조로 형식·값이 비정상이면
     * 잘못된 커서(400).
     */
    private static PlaceCursor parseCursor(String rawCursor) {
        CursorCodec.Cursor cursor = CursorCodec.decode(rawCursor);
        String sortValue = cursor.sortValue();
        int sep = sortValue.indexOf('|');
        int matched;
        double distance;
        try {
            matched = sep < 0 ? 0 : Integer.parseInt(sortValue.substring(0, sep));
            distance = Double.parseDouble(sortValue.substring(sep + 1));
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, e);
        }
        if ((matched != 0 && matched != 1) || !Double.isFinite(distance) || distance < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return new PlaceCursor(matched, distance, cursor.id());
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
            checkCourseVisible(course, memberId);
            return PlaceDetailResponse.ofCourse(course, bookmarkCount, bookmarked);
        }
        if (place instanceof Parking parking) {
            return PlaceDetailResponse.ofParking(parking, bookmarkCount, bookmarked);
        }
        throw new IllegalStateException("알 수 없는 place 타입: " + place.getClass());
    }

    /**
     * 상세를 열어줄 코스인지 판정한다(스펙 014).
     *
     * <p><b>삭제와 미승인의 응답이 다른 이유</b> — 삭제는 이미 북마크·연습 목록에 담아둔 사용자가 탭했을 때 "삭제된 코스입니다"를 보여줘야 해서 전용 코드로
     * 구분한다. 반면 미승인은 존재 자체를 숨겨야 하므로 <b>없는 장소와 똑같은 404</b>로 응답한다 — 코드가 갈리면 id를 훑어 심사 중인 코스가 있는지 알아낼 수
     * 있다.
     */
    private static void checkCourseVisible(Course course, Long memberId) {
        if (course.isDeleted()) {
            throw new BusinessException(CourseErrorCode.COURSE_DELETED);
        }
        if (!course.isApproved() && !course.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }
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
