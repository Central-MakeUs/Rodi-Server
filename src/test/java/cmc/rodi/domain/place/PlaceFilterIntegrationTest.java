package cmc.rodi.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.member.service.MemberFilterService;
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
 * 유형 필터(스펙 019) — 인증 회원의 filter_tags에 맞는 place만 목록·검색·마커에 나오는지 실제 DB로 검증. 다른 테스트(서울·부산·대구)와 겹치지 않게
 * 인천 좌표로 격리. {@code @Transactional}로 롤백해 seed 누적 방지.
 */
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class PlaceFilterIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    // 인천 인근 뷰포트 + 현위치
    private static final double SW_LAT = 37.40, SW_LNG = 126.60, NE_LAT = 37.55, NE_LNG = 126.80;
    private static final double ME_LAT = 37.45, ME_LNG = 126.70;

    @Autowired PlaceQueryService placeQueryService;
    @Autowired CourseRepository courseRepository;
    @Autowired ParkingRepository parkingRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired MemberFilterService memberFilterService;

    private static Point point(double lat, double lng) {
        return GEO.createPoint(new Coordinate(lng, lat));
    }

    private PlaceListRequest request(int size, String cursor) {
        return new PlaceListRequest(SW_LAT, SW_LNG, NE_LAT, NE_LNG, ME_LAT, ME_LNG, size, cursor);
    }

    /** seed 장소명이 모두 "인천-"으로 시작해 이 테스트의 장소만 검색된다. */
    private PlaceSearchRequest search(int size, String cursor) {
        return new PlaceSearchRequest("인천-", ME_LAT, ME_LNG, size, cursor);
    }

    private List<String> coordinateNames(Long memberId) {
        return placeQueryService.getAllCoordinates(memberId).stream()
                .map(PlaceCoordinateResponse::name)
                .filter(name -> name.startsWith("인천-"))
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
        near.addTag(PracticeType.STRAIGHT); // 비매칭(필터=INTERSECTION일 때)
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

    private Long memberWithFilter(List<PracticeType> tags) {
        Member member = memberRepository.save(Member.createBySocial("filter@test.com"));
        memberFilterService.updateFilterTags(member.getId(), tags);
        return member.getId();
    }

    @Test
    @DisplayName("목록 필터: 태그가 겹치는 코스만 거리순, 비매칭 코스·주차장은 나오지 않고 totalCount도 매칭 수")
    void 목록_매칭만() {
        seedPlaces();
        Long memberId = memberWithFilter(List.of(PracticeType.INTERSECTION));

        CursorPage<PlaceListItem> page = placeQueryService.getPlaces(request(20, null), memberId);

        assertThat(page.items())
                .extracting(PlaceListItem::name)
                .containsExactly("인천-중-교차로", "인천-원-교차로");
        assertThat(page.totalCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("주차장 매칭: 필터에 PARKING이 있으면 주차장이 나오고, 태그가 겹치는 코스와 함께 거리순")
    void 주차장_매칭() {
        seedPlaces();
        Long parkingOnly = memberWithFilter(List.of(PracticeType.PARKING));

        assertThat(placeQueryService.getPlaces(request(20, null), parkingOnly).items())
                .extracting(PlaceListItem::name)
                .containsExactly("인천-주차장");

        memberFilterService.updateFilterTags(
                parkingOnly, List.of(PracticeType.PARKING, PracticeType.INTERSECTION));
        CursorPage<PlaceListItem> mixed =
                placeQueryService.getPlaces(request(20, null), parkingOnly);
        assertThat(mixed.items())
                .extracting(PlaceListItem::name)
                .containsExactly("인천-주차장", "인천-중-교차로", "인천-원-교차로");
        assertThat(mixed.totalCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("필터 커서: (거리, id) 기준 2페이지가 누락·중복 없이 이어진다")
    void 필터_커서_연속성() {
        seedPlaces();
        Long memberId = memberWithFilter(List.of(PracticeType.PARKING, PracticeType.INTERSECTION));

        CursorPage<PlaceListItem> page1 = placeQueryService.getPlaces(request(2, null), memberId);
        assertThat(page1.items())
                .extracting(PlaceListItem::name)
                .containsExactly("인천-주차장", "인천-중-교차로");
        assertThat(page1.hasNext()).isTrue();
        assertThat(page1.totalCount()).isEqualTo(3L);

        CursorPage<PlaceListItem> page2 =
                placeQueryService.getPlaces(request(2, page1.nextCursor()), memberId);
        assertThat(page2.items()).extracting(PlaceListItem::name).containsExactly("인천-원-교차로");
        assertThat(page2.hasNext()).isFalse();
        assertThat(page2.nextCursor()).isNull();
        assertThat(page2.totalCount()).isNull();
    }

    @Test
    @DisplayName("배포 전 필터 커서(matched|distance)는 잘못된 커서로 거부")
    void 옛_필터_커서_거부() {
        seedPlaces();
        Long memberId = memberWithFilter(List.of(PracticeType.INTERSECTION));
        String legacyCursor = CursorCodec.encode("1|300.0", 1L);

        assertThatThrownBy(() -> placeQueryService.getPlaces(request(2, legacyCursor), memberId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("검색 필터: 키워드 결과 중 매칭 place만, totalCount도 매칭 수. 2페이지 연속")
    void 검색_매칭만() {
        seedPlaces();
        Long memberId = memberWithFilter(List.of(PracticeType.INTERSECTION));

        CursorPage<PlaceListItem> page1 = placeQueryService.searchPlaces(search(1, null), memberId);
        assertThat(page1.items()).extracting(PlaceListItem::name).containsExactly("인천-중-교차로");
        assertThat(page1.totalCount()).isEqualTo(2L);

        CursorPage<PlaceListItem> page2 =
                placeQueryService.searchPlaces(search(1, page1.nextCursor()), memberId);
        assertThat(page2.items()).extracting(PlaceListItem::name).containsExactly("인천-원-교차로");
        assertThat(page2.hasNext()).isFalse();
    }

    @Test
    @DisplayName("마커 필터: 필터가 있으면 매칭 place만, 비로그인이면 전부")
    void 마커_매칭만() {
        seedPlaces();
        Long intersection = memberWithFilter(List.of(PracticeType.INTERSECTION));

        assertThat(coordinateNames(intersection)).containsExactlyInAnyOrder("인천-중-교차로", "인천-원-교차로");

        memberFilterService.updateFilterTags(intersection, List.of(PracticeType.PARKING));
        assertThat(coordinateNames(intersection)).containsExactly("인천-주차장");

        assertThat(coordinateNames(null))
                .containsExactlyInAnyOrder("인천-근-직선", "인천-주차장", "인천-중-교차로", "인천-원-교차로");
    }

    @Test
    @DisplayName("빈 필터 회원·비로그인은 필터 없이 전부 거리순")
    void 필터_없으면_전부_거리순() {
        seedPlaces();
        Long noFilterMember = memberWithFilter(List.of()); // 빈 필터 = 해제

        CursorPage<PlaceListItem> loggedIn =
                placeQueryService.getPlaces(request(20, null), noFilterMember);
        assertThat(loggedIn.items())
                .extracting(PlaceListItem::name)
                .containsExactly("인천-근-직선", "인천-주차장", "인천-중-교차로", "인천-원-교차로");
        assertThat(loggedIn.totalCount()).isEqualTo(4L);

        CursorPage<PlaceListItem> anonymous = placeQueryService.getPlaces(request(20, null), null);
        assertThat(anonymous.items())
                .extracting(PlaceListItem::name)
                .containsExactly("인천-근-직선", "인천-주차장", "인천-중-교차로", "인천-원-교차로");

        assertThat(coordinateNames(noFilterMember)).hasSize(4);
    }
}
