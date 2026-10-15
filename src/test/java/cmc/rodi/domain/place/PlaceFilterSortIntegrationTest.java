package cmc.rodi.domain.place;

import static org.assertj.core.api.Assertions.assertThat;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.member.service.MemberFilterService;
import cmc.rodi.domain.place.dto.PlaceListItem;
import cmc.rodi.domain.place.dto.PlaceListRequest;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.Parking;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.place.repository.ParkingRepository;
import cmc.rodi.domain.place.service.PlaceQueryService;
import cmc.rodi.global.common.pagination.CursorPage;
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
 * Unit 2: 홈 정렬 필터(스펙 007) — 인증 회원의 filter_tags에 따라 매칭 place 우선→거리순, 비매칭도 후순위로 전부 노출을 실제 DB로 검증. 다른
 * 테스트(서울·부산·대구)와 겹치지 않게 인천 좌표로 격리. {@code @Transactional}로 롤백해 seed 누적 방지.
 */
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class PlaceFilterSortIntegrationTest {

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
    @DisplayName("필터 매칭 우선: 매칭 코스가 더 멀어도 먼저, 비매칭도 후순위로 전부 노출(숨김 없음)")
    void 매칭_우선_정렬() {
        seedPlaces();
        Long memberId = memberWithFilter(List.of(PracticeType.INTERSECTION));

        CursorPage<PlaceListItem> page = placeQueryService.getPlaces(request(20, null), memberId);

        // 매칭(교차로) 먼저 거리순 → 비매칭 거리순. 개수 그대로(4개, 숨김 없음)
        assertThat(page.items())
                .extracting(PlaceListItem::name)
                .containsExactly("인천-중-교차로", "인천-원-교차로", "인천-근-직선", "인천-주차장");
        assertThat(page.totalCount()).isEqualTo(4L);
    }

    @Test
    @DisplayName("주차장 매칭: 필터에 PARKING이 있으면 주차장이 더 가까운 코스보다 앞선다")
    void 주차장_매칭() {
        seedPlaces();
        Long memberId = memberWithFilter(List.of(PracticeType.PARKING));

        CursorPage<PlaceListItem> page = placeQueryService.getPlaces(request(20, null), memberId);

        // 매칭은 주차장뿐 → 먼저. 나머지 코스는 비매칭이라 거리순 후순위
        assertThat(page.items().get(0).name()).isEqualTo("인천-주차장");
        assertThat(page.items())
                .extracting(PlaceListItem::name)
                .containsExactly("인천-주차장", "인천-근-직선", "인천-중-교차로", "인천-원-교차로");
    }

    @Test
    @DisplayName("필터 커서: (matched, 거리, id) 기준 2페이지 연속성 — 매칭 경계에서 누락·중복 없음")
    void 필터_커서_연속성() {
        seedPlaces();
        Long memberId = memberWithFilter(List.of(PracticeType.INTERSECTION));

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
    @DisplayName("빈 필터 회원·비로그인은 필터 없이 순수 거리순")
    void 필터_없으면_거리순() {
        seedPlaces();
        Long noFilterMember = memberWithFilter(List.of()); // 빈 필터 = 해제

        CursorPage<PlaceListItem> loggedIn =
                placeQueryService.getPlaces(request(20, null), noFilterMember);
        assertThat(loggedIn.items())
                .extracting(PlaceListItem::name)
                .containsExactly("인천-근-직선", "인천-주차장", "인천-중-교차로", "인천-원-교차로");

        CursorPage<PlaceListItem> anonymous = placeQueryService.getPlaces(request(20, null), null);
        assertThat(anonymous.items())
                .extracting(PlaceListItem::name)
                .containsExactly("인천-근-직선", "인천-주차장", "인천-중-교차로", "인천-원-교차로");
    }
}
