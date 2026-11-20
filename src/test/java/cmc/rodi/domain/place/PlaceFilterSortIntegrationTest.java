package cmc.rodi.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.member.service.MemberSortService;
import cmc.rodi.domain.place.dto.PlaceCoordinateResponse;
import cmc.rodi.domain.place.dto.PlaceListItem;
import cmc.rodi.domain.place.dto.PlaceListRequest;
import cmc.rodi.domain.place.dto.PlaceSearchRequest;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.Parking;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.place.repository.ParkingRepository;
import cmc.rodi.domain.place.service.PlaceQueryService;
import cmc.rodi.global.common.pagination.CursorCodec;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.support.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 유형 정렬·필터(스펙 007·019)를 실제 DB로 검증한다. 필터({@code practiceTypes} 파라미터: 맞는 장소만)와 정렬(인증 회원의 sort_tags:
 * 맞는 장소 먼저, 나머지도 전부)은 독립적으로 함께 적용된다. 다른 테스트(서울·부산·대구)와 겹치지 않게 인천 좌표로 격리. {@code @Transactional}로
 * 롤백해 seed 누적 방지.
 */
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class PlaceFilterSortIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    // 인천 인근 뷰포트 + 현위치
    private static final double SW_LAT = 37.40, SW_LNG = 126.60, NE_LAT = 37.55, NE_LNG = 126.80;
    private static final double ME_LAT = 37.45, ME_LNG = 126.70;
    private static final String KEYWORD = "인천";
    private static final List<String> ALL_BY_DISTANCE =
            List.of("인천-근-직선", "인천-주차장", "인천-중-교차로", "인천-원-교차로");

    @Autowired PlaceQueryService placeQueryService;
    @Autowired CourseRepository courseRepository;
    @Autowired ParkingRepository parkingRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired MemberSortService memberSortService;

    private static Point point(double lat, double lng) {
        return GEO.createPoint(new Coordinate(lng, lat));
    }

    private PlaceListRequest request(int size, String cursor) {
        return new PlaceListRequest(SW_LAT, SW_LNG, NE_LAT, NE_LNG, ME_LAT, ME_LNG, size, cursor);
    }

    private PlaceListRequest filtered(int size, String cursor, PracticeType... types) {
        return new PlaceListRequest(
                SW_LAT, SW_LNG, NE_LAT, NE_LNG, ME_LAT, ME_LNG, size, cursor, List.of(types));
    }

    private PlaceSearchRequest searchFiltered(int size, String cursor, PracticeType... types) {
        return new PlaceSearchRequest(KEYWORD, ME_LAT, ME_LNG, size, cursor, List.of(types));
    }

    private List<String> coordinateNames(PracticeType... types) {
        return placeQueryService.getAllCoordinates(List.of(types)).stream()
                .map(PlaceCoordinateResponse::name)
                .filter(n -> n != null && n.startsWith(KEYWORD + "-"))
                .toList();
    }

    /** 현위치에서 가까운 순: near(≈100m) < parking(≈150m) < mid(≈300m) < far(≈600m). */
    private void seedPlaces() {
        Course near =
                Course.builder()
                        .name("인천-근-직선")
                        .address("인천광역시 미추홀구")
                        .location(point(37.4509, 126.7000))
                        .build();
        near.addTag(PracticeType.STRAIGHT); // 비매칭(기준=INTERSECTION일 때)
        courseRepository.save(near);

        parkingRepository.save(
                Parking.builder()
                        .name("인천-주차장")
                        .address("인천광역시 남동구")
                        .location(point(37.4513, 126.7005))
                        .capacity(50)
                        .build());

        Course mid =
                Course.builder()
                        .name("인천-중-교차로")
                        .address("인천광역시 연수구")
                        .location(point(37.4527, 126.7000))
                        .build();
        mid.addTag(PracticeType.INTERSECTION); // 매칭
        courseRepository.save(mid);

        Course far =
                Course.builder()
                        .name("인천-원-교차로")
                        .address("인천광역시 부평구")
                        .location(point(37.4554, 126.7000))
                        .build();
        far.addTag(PracticeType.INTERSECTION); // 매칭
        courseRepository.save(far);
    }

    private Long memberWithSortTags(List<PracticeType> tags) {
        Member member = memberRepository.save(Member.createBySocial("sort@test.com"));
        memberSortService.updateSortTags(member.getId(), tags);
        return member.getId();
    }

    // ── 정렬(sort_tags) ──

    @Test
    @DisplayName("정렬: 매칭 코스가 더 멀어도 먼저, 비매칭도 후순위로 전부 노출(숨김 없음)")
    void 매칭_우선_정렬() {
        seedPlaces();
        Long memberId = memberWithSortTags(List.of(PracticeType.INTERSECTION));

        CursorPage<PlaceListItem> page = placeQueryService.getPlaces(request(20, null), memberId);

        // 매칭(교차로) 먼저 거리순 → 비매칭 거리순. 개수 그대로(4개, 숨김 없음)
        assertThat(page.items())
                .extracting(PlaceListItem::name)
                .containsExactly("인천-중-교차로", "인천-원-교차로", "인천-근-직선", "인천-주차장");
        assertThat(page.totalCount()).isEqualTo(4L);
    }

    @Test
    @DisplayName("정렬 주차장 매칭: 기준에 PARKING이 있으면 주차장이 더 가까운 코스보다 앞선다")
    void 정렬_주차장_매칭() {
        seedPlaces();
        Long memberId = memberWithSortTags(List.of(PracticeType.PARKING));

        CursorPage<PlaceListItem> page = placeQueryService.getPlaces(request(20, null), memberId);

        // 매칭은 주차장뿐 → 먼저. 나머지 코스는 비매칭이라 거리순 후순위
        assertThat(page.items())
                .extracting(PlaceListItem::name)
                .containsExactly("인천-주차장", "인천-근-직선", "인천-중-교차로", "인천-원-교차로");
    }

    @Test
    @DisplayName("정렬 커서: (matched, 거리, id) 기준 2페이지 연속성 — 매칭 경계에서 누락·중복 없음")
    void 정렬_커서_연속성() {
        seedPlaces();
        Long memberId = memberWithSortTags(List.of(PracticeType.INTERSECTION));

        CursorPage<PlaceListItem> page1 = placeQueryService.getPlaces(request(2, null), memberId);
        assertThat(page1.items())
                .extracting(PlaceListItem::name)
                .containsExactly("인천-중-교차로", "인천-원-교차로"); // 매칭 2개
        assertThat(page1.hasNext()).isTrue();

        CursorPage<PlaceListItem> page2 =
                placeQueryService.getPlaces(request(2, page1.nextCursor()), memberId);
        assertThat(page2.items())
                .extracting(PlaceListItem::name)
                .containsExactly("인천-근-직선", "인천-주차장"); // 비매칭 2개
        assertThat(page2.hasNext()).isFalse();
        assertThat(page2.nextCursor()).isNull();
        assertThat(page2.totalCount()).isNull();
    }

    @Test
    @DisplayName("빈 sort_tags 회원·비로그인은 순수 거리순")
    void 정렬_기준_없으면_거리순() {
        seedPlaces();
        Long noSortMember = memberWithSortTags(List.of()); // 빈 값 = 해제

        assertThat(placeQueryService.getPlaces(request(20, null), noSortMember).items())
                .extracting(PlaceListItem::name)
                .containsExactlyElementsOf(ALL_BY_DISTANCE);
        assertThat(placeQueryService.getPlaces(request(20, null), null).items())
                .extracting(PlaceListItem::name)
                .containsExactlyElementsOf(ALL_BY_DISTANCE);
    }

    // ── 커서 ──

    @Test
    @DisplayName("거리만 있는 옛 커서도 matched=0으로 받아 새 커서와 같은 다음 페이지를 준다")
    void 옛_거리_커서_호환() {
        seedPlaces();

        CursorPage<PlaceListItem> page1 = placeQueryService.getPlaces(request(2, null), null);
        assertThat(page1.items())
                .extracting(PlaceListItem::name)
                .containsExactly("인천-근-직선", "인천-주차장");

        // 배포 전 서버가 정렬 없이 발급한 형식: sortValue = distance
        CursorCodec.Cursor next = CursorCodec.decode(page1.nextCursor());
        String legacy =
                CursorCodec.encode(
                        next.sortValue().substring(next.sortValue().indexOf('|') + 1), next.id());

        assertThat(placeQueryService.getPlaces(request(2, legacy), null).items())
                .extracting(PlaceListItem::name)
                .containsExactly("인천-중-교차로", "인천-원-교차로");
        assertThat(placeQueryService.getPlaces(request(2, page1.nextCursor()), null).items())
                .extracting(PlaceListItem::name)
                .containsExactly("인천-중-교차로", "인천-원-교차로");
    }

    @Test
    @DisplayName("다음 커서는 정렬 유무와 관계없이 matched|distance 형식이다")
    void 커서_형식_통일() {
        seedPlaces();

        String cursor = placeQueryService.getPlaces(request(1, null), null).nextCursor();

        assertThat(CursorCodec.decode(cursor).sortValue()).startsWith("0|");
    }

    @Test
    @DisplayName("형식·값이 비정상인 커서(matched 0·1 외, 숫자 아님, 음수 거리)는 거부")
    void 비정상_커서_거부() {
        seedPlaces();

        for (String sortValue : List.of("2|100.0", "x|100.0", "1|abc", "0|-1.0", "NaN")) {
            String cursor = CursorCodec.encode(sortValue, 1L);
            assertThatThrownBy(() -> placeQueryService.getPlaces(request(2, cursor), null))
                    .as(sortValue)
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ── 필터(practiceTypes 파라미터) ──

    @Test
    @DisplayName("목록 필터: 태그가 겹치는 코스만 거리순, 비매칭 코스·주차장은 나오지 않고 totalCount도 매칭 수")
    void 목록_필터_매칭만() {
        seedPlaces();

        CursorPage<PlaceListItem> page =
                placeQueryService.getPlaces(filtered(20, null, PracticeType.INTERSECTION), null);

        assertThat(page.items())
                .extracting(PlaceListItem::name)
                .containsExactly("인천-중-교차로", "인천-원-교차로");
        assertThat(page.totalCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("필터 주차장 매칭: PARKING이 있으면 주차장이 나오고, 태그가 겹치는 코스와 함께 거리순")
    void 필터_주차장_매칭() {
        seedPlaces();

        assertThat(
                        placeQueryService
                                .getPlaces(filtered(20, null, PracticeType.PARKING), null)
                                .items())
                .extracting(PlaceListItem::name)
                .containsExactly("인천-주차장");

        CursorPage<PlaceListItem> mixed =
                placeQueryService.getPlaces(
                        filtered(20, null, PracticeType.PARKING, PracticeType.INTERSECTION), null);
        assertThat(mixed.items())
                .extracting(PlaceListItem::name)
                .containsExactly("인천-주차장", "인천-중-교차로", "인천-원-교차로");
        assertThat(mixed.totalCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("필터 커서: (거리, id) 기준 2페이지가 누락·중복 없이 이어진다")
    void 필터_커서_연속성() {
        seedPlaces();
        PracticeType[] types = {PracticeType.PARKING, PracticeType.INTERSECTION};

        CursorPage<PlaceListItem> page1 =
                placeQueryService.getPlaces(filtered(2, null, types), null);
        assertThat(page1.items())
                .extracting(PlaceListItem::name)
                .containsExactly("인천-주차장", "인천-중-교차로");
        assertThat(page1.hasNext()).isTrue();
        assertThat(page1.totalCount()).isEqualTo(3L);

        CursorPage<PlaceListItem> page2 =
                placeQueryService.getPlaces(filtered(2, page1.nextCursor(), types), null);
        assertThat(page2.items()).extracting(PlaceListItem::name).containsExactly("인천-원-교차로");
        assertThat(page2.hasNext()).isFalse();
        assertThat(page2.nextCursor()).isNull();
        assertThat(page2.totalCount()).isNull();
    }

    // ── 필터 + 정렬 ──

    @Test
    @DisplayName("필터와 정렬 함께: 필터 매칭 장소만, 그 안에서 sort_tags 매칭이 먼저")
    void 필터_정렬_함께() {
        seedPlaces();
        PracticeType[] filter = {PracticeType.PARKING, PracticeType.INTERSECTION};

        Long intersectionFirst = memberWithSortTags(List.of(PracticeType.INTERSECTION));
        CursorPage<PlaceListItem> page =
                placeQueryService.getPlaces(filtered(20, null, filter), intersectionFirst);
        assertThat(page.items())
                .extracting(PlaceListItem::name)
                .containsExactly("인천-중-교차로", "인천-원-교차로", "인천-주차장");
        assertThat(page.totalCount()).isEqualTo(3L);

        // 정렬 기준이 필터 밖(STRAIGHT)이면 필터 결과 안에 매칭이 없어 거리순
        memberSortService.updateSortTags(intersectionFirst, List.of(PracticeType.STRAIGHT));
        assertThat(
                        placeQueryService
                                .getPlaces(filtered(20, null, filter), intersectionFirst)
                                .items())
                .extracting(PlaceListItem::name)
                .containsExactly("인천-주차장", "인천-중-교차로", "인천-원-교차로");
    }

    @Test
    @DisplayName("필터와 정렬 함께: 정렬 경계를 넘는 2페이지가 누락·중복 없이 이어진다")
    void 필터_정렬_커서_연속성() {
        seedPlaces();
        Long memberId = memberWithSortTags(List.of(PracticeType.PARKING));
        PracticeType[] filter = {PracticeType.PARKING, PracticeType.INTERSECTION};

        CursorPage<PlaceListItem> page1 =
                placeQueryService.getPlaces(filtered(2, null, filter), memberId);
        assertThat(page1.items())
                .extracting(PlaceListItem::name)
                .containsExactly("인천-주차장", "인천-중-교차로"); // 매칭(주차장) → 비매칭 첫 번째
        assertThat(page1.totalCount()).isEqualTo(3L);

        CursorPage<PlaceListItem> page2 =
                placeQueryService.getPlaces(filtered(2, page1.nextCursor(), filter), memberId);
        assertThat(page2.items()).extracting(PlaceListItem::name).containsExactly("인천-원-교차로");
        assertThat(page2.hasNext()).isFalse();
    }

    @Test
    @DisplayName("같은 기준이면 필터 결과와 정렬의 매칭 그룹이 같은 장소 집합이다(두 매칭 술어의 규칙 일치)")
    void 필터_정렬_매칭_규칙_일치() {
        seedPlaces();
        List<List<PracticeType>> criteria =
                List.of(
                        List.of(PracticeType.INTERSECTION),
                        List.of(PracticeType.PARKING),
                        List.of(PracticeType.STRAIGHT, PracticeType.PARKING),
                        List.of(PracticeType.U_TURN));

        for (List<PracticeType> types : criteria) {
            Long memberId = memberWithSortTags(types);
            List<String> filteredNames =
                    placeQueryService
                            .getPlaces(filtered(20, null, types.toArray(PracticeType[]::new)), null)
                            .items()
                            .stream()
                            .map(PlaceListItem::name)
                            .toList();
            List<String> sortedNames =
                    placeQueryService.getPlaces(request(20, null), memberId).items().stream()
                            .map(PlaceListItem::name)
                            .toList();

            assertThat(sortedNames.subList(0, filteredNames.size()))
                    .as(types.toString())
                    .containsExactlyInAnyOrderElementsOf(filteredNames);
        }
    }

    @Test
    @DisplayName("검색 필터: 키워드 결과 중 매칭 place만, totalCount도 매칭 수. 2페이지 연속")
    void 검색_필터_매칭만() {
        seedPlaces();
        Long memberId = memberWithSortTags(List.of()); // 검색은 로그인 필수, 정렬 기준 없음

        CursorPage<PlaceListItem> page1 =
                placeQueryService.searchPlaces(
                        searchFiltered(1, null, PracticeType.INTERSECTION), memberId);
        assertThat(page1.items()).extracting(PlaceListItem::name).containsExactly("인천-중-교차로");
        assertThat(page1.totalCount()).isEqualTo(2L);

        CursorPage<PlaceListItem> page2 =
                placeQueryService.searchPlaces(
                        searchFiltered(1, page1.nextCursor(), PracticeType.INTERSECTION), memberId);
        assertThat(page2.items()).extracting(PlaceListItem::name).containsExactly("인천-원-교차로");
        assertThat(page2.hasNext()).isFalse();
    }

    @Test
    @DisplayName("검색 필터+정렬: 필터 매칭 장소만, 그 안에서 sort_tags 매칭이 먼저")
    void 검색_필터_정렬_함께() {
        seedPlaces();
        Long memberId = memberWithSortTags(List.of(PracticeType.PARKING));

        CursorPage<PlaceListItem> page =
                placeQueryService.searchPlaces(
                        searchFiltered(20, null, PracticeType.PARKING, PracticeType.INTERSECTION),
                        memberId);

        assertThat(page.items())
                .extracting(PlaceListItem::name)
                .containsExactly("인천-주차장", "인천-중-교차로", "인천-원-교차로");
        assertThat(page.totalCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("검색 정렬: 파라미터가 없으면 sort_tags 매칭 우선, 비매칭도 전부")
    void 검색_정렬() {
        seedPlaces();
        Long memberId = memberWithSortTags(List.of(PracticeType.INTERSECTION));

        CursorPage<PlaceListItem> page =
                placeQueryService.searchPlaces(
                        new PlaceSearchRequest(KEYWORD, ME_LAT, ME_LNG, 20, null), memberId);

        assertThat(page.items())
                .extracting(PlaceListItem::name)
                .containsExactly("인천-중-교차로", "인천-원-교차로", "인천-근-직선", "인천-주차장");
        assertThat(page.totalCount()).isEqualTo(4L);
    }

    @Test
    @DisplayName("마커 필터: 파라미터가 있으면 매칭 place만, 없으면 전부")
    void 마커_필터() {
        seedPlaces();

        assertThat(coordinateNames(PracticeType.INTERSECTION))
                .containsExactlyInAnyOrder("인천-중-교차로", "인천-원-교차로");
        assertThat(coordinateNames(PracticeType.PARKING)).containsExactly("인천-주차장");
        assertThat(coordinateNames()).containsExactlyInAnyOrderElementsOf(ALL_BY_DISTANCE);
    }
}
