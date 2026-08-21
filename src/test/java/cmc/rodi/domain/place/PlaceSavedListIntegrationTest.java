package cmc.rodi.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.dto.PlaceListItem;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.Parking;
import cmc.rodi.domain.place.entity.PlaceType;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.place.repository.ParkingRepository;
import cmc.rodi.domain.place.service.BookmarkQueryService;
import cmc.rodi.domain.place.service.BookmarkService;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.support.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** U3: 저장한 장소 목록(#3) — 최신 저장순·커서 2페이지·totalCount·distanceFromMe null·코스/주차장 폴리모픽·401. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlaceSavedListIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired BookmarkQueryService bookmarkQueryService;
    @Autowired BookmarkService bookmarkService;
    @Autowired CourseRepository courseRepository;
    @Autowired ParkingRepository parkingRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired MockMvc mockMvc;

    private static Point point(double lat, double lng) {
        return GEO.createPoint(new Coordinate(lng, lat));
    }

    @Test
    @DisplayName("저장 목록: 최신 저장순·커서 2페이지·totalCount·거리 null·폴리모픽")
    void 저장목록() {
        Member me = memberRepository.save(Member.createBySocial("saved@kakao.com"));

        Course c1 =
                courseRepository.save(
                        Course.builder().name("코스1").location(point(37.5, 127.0)).build());
        Parking p1 =
                parkingRepository.save(
                        Parking.builder()
                                .name("주차장1")
                                .location(point(37.6, 127.1))
                                .capacity(100)
                                .build());
        Course c2 =
                courseRepository.save(
                        Course.builder().name("코스2").location(point(37.7, 127.2)).build());
        c2.addTag(PracticeType.STRAIGHT);
        courseRepository.save(c2);

        // 저장 순서: c1 → p1 → c2 (최신 = c2)
        bookmarkService.bookmark(c1.getId(), me.getId());
        bookmarkService.bookmark(p1.getId(), me.getId());
        bookmarkService.bookmark(c2.getId(), me.getId());

        CursorPage<PlaceListItem> page1 = bookmarkQueryService.getSavedPlaces(me.getId(), 2, null);

        assertThat(page1.totalCount()).isEqualTo(3L);
        assertThat(page1.hasNext()).isTrue();
        assertThat(page1.nextCursor()).isNotNull();
        assertThat(page1.items()).extracting(PlaceListItem::name).containsExactly("코스2", "주차장1");
        // 현위치 없음 → 거리 null
        assertThat(page1.items()).allSatisfy(i -> assertThat(i.distanceFromMe()).isNull());

        PlaceListItem course = page1.items().get(0);
        assertThat(course.type()).isEqualTo(PlaceType.COURSE);
        assertThat(course.practiceTypes()).containsExactly(PracticeType.STRAIGHT);

        PlaceListItem parking = page1.items().get(1);
        assertThat(parking.type()).isEqualTo(PlaceType.PARKING);
        assertThat(parking.practiceTypes()).containsExactly(PracticeType.PARKING);
        assertThat(parking.capacity()).isEqualTo(100);

        CursorPage<PlaceListItem> page2 =
                bookmarkQueryService.getSavedPlaces(me.getId(), 2, page1.nextCursor());
        assertThat(page2.items()).extracting(PlaceListItem::name).containsExactly("코스1");
        assertThat(page2.hasNext()).isFalse();
        assertThat(page2.nextCursor()).isNull();
        assertThat(page2.totalCount()).isNull(); // 다음 페이지엔 생략
    }

    @Test
    @DisplayName("미인증: 토큰 없이 저장 목록 조회 시 401")
    void 미인증_401() throws Exception {
        mockMvc.perform(get("/api/v1/places/bookmarks")).andExpect(status().isUnauthorized());
    }
}
