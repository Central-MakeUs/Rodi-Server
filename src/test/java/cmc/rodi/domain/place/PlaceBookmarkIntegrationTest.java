package cmc.rodi.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.dto.PlaceDetailResponse;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.repository.BookmarkRepository;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.place.service.BookmarkService;
import cmc.rodi.domain.place.service.PlaceQueryService;
import cmc.rodi.global.exception.BusinessException;
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

/** U7: 북마크(#5) — 저장(멱등)·해제(멱등)·상세 반영·404·401. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlaceBookmarkIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired BookmarkService bookmarkService;
    @Autowired PlaceQueryService placeQueryService;
    @Autowired CourseRepository courseRepository;
    @Autowired BookmarkRepository bookmarkRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired MockMvc mockMvc;

    private static Point point(double lat, double lng) {
        return GEO.createPoint(new Coordinate(lng, lat));
    }

    private Course seedCourse(String name) {
        return courseRepository.save(
                Course.builder().name(name).location(point(37.5, 127.0)).build());
    }

    @Test
    @DisplayName("저장 → 상세에 반영(count 1·isBookmarked) → 재저장 멱등 → 해제 → 재해제 멱등")
    void 북마크_전체_흐름() {
        Course course = seedCourse("북마크 코스");
        Member me = memberRepository.save(Member.createBySocial("bm@kakao.com"));

        // 저장
        bookmarkService.bookmark(course.getId(), me.getId());
        PlaceDetailResponse after = placeQueryService.getPlaceDetail(course.getId(), me.getId());
        assertThat(after.bookmarked()).isTrue();
        assertThat(after.bookmarkCount()).isEqualTo(1);

        // 재저장(멱등) — 예외 없이 통과하고 count도 그대로 1
        assertThatCode(() -> bookmarkService.bookmark(course.getId(), me.getId()))
                .doesNotThrowAnyException();
        assertThat(bookmarkRepository.countByPlaceId(course.getId())).isEqualTo(1);

        // 해제
        bookmarkService.unbookmark(course.getId(), me.getId());
        PlaceDetailResponse removed = placeQueryService.getPlaceDetail(course.getId(), me.getId());
        assertThat(removed.bookmarked()).isFalse();
        assertThat(removed.bookmarkCount()).isZero();

        // 재해제(멱등)
        assertThatCode(() -> bookmarkService.unbookmark(course.getId(), me.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("북마크는 회원별로 독립 — 내 해제가 남의 북마크에 영향 없음")
    void 회원별_독립() {
        Course course = seedCourse("공용 코스");
        Member me = memberRepository.save(Member.createBySocial("a@kakao.com"));
        Member other = memberRepository.save(Member.createBySocial("b@kakao.com"));

        bookmarkService.bookmark(course.getId(), me.getId());
        bookmarkService.bookmark(course.getId(), other.getId());
        assertThat(bookmarkRepository.countByPlaceId(course.getId())).isEqualTo(2);

        bookmarkService.unbookmark(course.getId(), me.getId());

        assertThat(placeQueryService.getPlaceDetail(course.getId(), me.getId()).bookmarked())
                .isFalse();
        PlaceDetailResponse asOther =
                placeQueryService.getPlaceDetail(course.getId(), other.getId());
        assertThat(asOther.bookmarked()).isTrue();
        assertThat(asOther.bookmarkCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("없는 장소 북마크 시 404")
    void 없는_장소_404() {
        Member me = memberRepository.save(Member.createBySocial("nf@kakao.com"));

        assertThatThrownBy(() -> bookmarkService.bookmark(999_999L, me.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("미인증: 토큰 없이 저장·해제 시 401")
    void 미인증_401() throws Exception {
        Course course = seedCourse("인증 코스");

        mockMvc.perform(post("/api/v1/places/{placeId}/bookmark", course.getId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/places/{placeId}/bookmark", course.getId()))
                .andExpect(status().isUnauthorized());
    }
}
