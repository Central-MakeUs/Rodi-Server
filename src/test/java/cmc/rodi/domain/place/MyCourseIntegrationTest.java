package cmc.rodi.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.dto.MyCourseItem;
import cmc.rodi.domain.place.dto.PlaceListItem;
import cmc.rodi.domain.place.entity.ApprovalStatus;
import cmc.rodi.domain.place.entity.Bookmark;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.Parking;
import cmc.rodi.domain.place.exception.CourseErrorCode;
import cmc.rodi.domain.place.repository.BookmarkRepository;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.place.repository.ParkingRepository;
import cmc.rodi.domain.place.service.BookmarkQueryService;
import cmc.rodi.domain.place.service.CourseQueryService;
import cmc.rodi.domain.place.service.CourseService;
import cmc.rodi.domain.place.service.PlaceQueryService;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.transaction.annotation.Transactional;

/** 내 코스 목록·삭제(스펙 015) — 상태 필터·커서·soft delete와 담아둔 사용자에게 남는 삭제 표시. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class MyCourseIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired CourseService courseService;
    @Autowired CourseQueryService courseQueryService;
    @Autowired PlaceQueryService placeQueryService;
    @Autowired BookmarkQueryService bookmarkQueryService;
    @Autowired CourseRepository courseRepository;
    @Autowired ParkingRepository parkingRepository;
    @Autowired BookmarkRepository bookmarkRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired MockMvc mockMvc;

    private Member me;
    private Member other;

    private static Point point(double lat, double lng) {
        return GEO.createPoint(new Coordinate(lng, lat));
    }

    @BeforeEach
    void setUp() {
        me = memberRepository.save(Member.createBySocial("mine@kakao.com"));
        other = memberRepository.save(Member.createBySocial("other@kakao.com"));
    }

    private Course register(String name, Member owner, ApprovalStatus status) {
        Course course = Course.register(name, "설명", "서울특별시 강남구", point(37.51, 127.03), 3000, owner);
        course.changeApprovalStatus(status, LocalDateTime.now());
        course.addTag(PracticeType.STRAIGHT);
        return courseRepository.save(course);
    }

    private List<String> myCourseNames(ApprovalStatus status) {
        return courseQueryService.getMyCourses(me.getId(), status, 20, null).items().stream()
                .map(MyCourseItem::name)
                .toList();
    }

    @Test
    @DisplayName("목록: 내가 등록한 코스만 최신순으로, 남의 코스·운영자 코스는 빠진다")
    void 목록() {
        register("내코스1", me, ApprovalStatus.PENDING);
        register("내코스2", me, ApprovalStatus.APPROVED);
        register("남의코스", other, ApprovalStatus.APPROVED);
        courseRepository.save(
                Course.builder().name("운영자코스").location(point(37.51, 127.03)).build());

        assertThat(myCourseNames(null)).containsExactly("내코스2", "내코스1"); // 최신 등록순
    }

    @Test
    @DisplayName("목록: 상태로 걸러 보고, totalCount도 필터 적용 개수다")
    void 상태_필터() {
        register("대기1", me, ApprovalStatus.PENDING);
        register("대기2", me, ApprovalStatus.PENDING);
        register("승인1", me, ApprovalStatus.APPROVED);
        register("반려1", me, ApprovalStatus.REJECTED);

        assertThat(myCourseNames(ApprovalStatus.PENDING)).containsExactly("대기2", "대기1");
        assertThat(myCourseNames(ApprovalStatus.APPROVED)).containsExactly("승인1");
        assertThat(myCourseNames(ApprovalStatus.REJECTED)).containsExactly("반려1");

        CursorPage<MyCourseItem> pending =
                courseQueryService.getMyCourses(me.getId(), ApprovalStatus.PENDING, 20, null);
        assertThat(pending.totalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("목록: size만큼 끊어 커서로 이어지고 중복이 없다")
    void 커서_페이징() {
        register("코스1", me, ApprovalStatus.PENDING);
        register("코스2", me, ApprovalStatus.PENDING);
        register("코스3", me, ApprovalStatus.PENDING);

        CursorPage<MyCourseItem> page1 = courseQueryService.getMyCourses(me.getId(), null, 2, null);
        assertThat(page1.items()).extracting(MyCourseItem::name).containsExactly("코스3", "코스2");
        assertThat(page1.hasNext()).isTrue();
        assertThat(page1.totalCount()).isEqualTo(3);

        CursorPage<MyCourseItem> page2 =
                courseQueryService.getMyCourses(me.getId(), null, 2, page1.nextCursor());
        assertThat(page2.items()).extracting(MyCourseItem::name).containsExactly("코스1");
        assertThat(page2.hasNext()).isFalse();
        assertThat(page2.totalCount()).isNull(); // 첫 페이지에서만
    }

    @Test
    @DisplayName("목록: 항목은 코스명·상태·등록일시만 담는다")
    void 항목_구성() {
        Course course = register("항목확인", me, ApprovalStatus.REJECTED);

        MyCourseItem item =
                courseQueryService.getMyCourses(me.getId(), null, 20, null).items().get(0);

        assertThat(item.courseId()).isEqualTo(course.getId());
        assertThat(item.name()).isEqualTo("항목확인");
        assertThat(item.approvalStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(item.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("삭제: 승인 상태와 무관하게 지워지고 내 목록에서 빠진다(행은 남는다)")
    void 삭제() {
        Course pending = register("대기삭제", me, ApprovalStatus.PENDING);
        Course approved = register("승인삭제", me, ApprovalStatus.APPROVED);

        courseService.delete(pending.getId(), me.getId());
        courseService.delete(approved.getId(), me.getId());

        assertThat(myCourseNames(null)).isEmpty();
        assertThat(courseRepository.findById(pending.getId())).isPresent(); // soft delete
        assertThat(courseRepository.findById(approved.getId()).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    @DisplayName("삭제: 승인된 코스를 지워도 남의 북마크는 남고, 저장 목록에 isDeleted로 표시된다")
    void 삭제해도_북마크는_남는다() {
        Course course = register("담긴코스", me, ApprovalStatus.APPROVED);
        bookmarkRepository.save(Bookmark.builder().member(other).place(course).build());

        courseService.delete(course.getId(), me.getId());

        assertThat(bookmarkRepository.existsByMemberIdAndPlaceId(other.getId(), course.getId()))
                .isTrue();
        List<PlaceListItem> saved =
                bookmarkQueryService.getSavedPlaces(other.getId(), 20, null).items();
        assertThat(saved)
                .singleElement()
                .satisfies(
                        item -> {
                            assertThat(item.id()).isEqualTo(course.getId());
                            assertThat(item.isDeleted()).isTrue();
                        });
    }

    @Test
    @DisplayName("삭제: 살아 있는 코스·주차장의 저장 목록 표시는 false다")
    void 삭제_아닌_항목() {
        Course course = register("살아있는코스", me, ApprovalStatus.APPROVED);
        Parking parking =
                parkingRepository.save(
                        Parking.builder().name("주차장").location(point(37.52, 127.04)).build());
        bookmarkRepository.save(Bookmark.builder().member(me).place(course).build());
        bookmarkRepository.save(Bookmark.builder().member(me).place(parking).build());

        assertThat(bookmarkQueryService.getSavedPlaces(me.getId(), 20, null).items())
                .extracting(PlaceListItem::isDeleted)
                .containsOnly(false);
    }

    @Test
    @DisplayName("삭제: 남의 코스·운영자 코스는 403")
    void 삭제_권한() {
        Course othersCourse = register("남의코스", other, ApprovalStatus.APPROVED);
        Course seeded =
                courseRepository.save(
                        Course.builder().name("운영자코스").location(point(37.51, 127.03)).build());

        assertThatThrownBy(() -> courseService.delete(othersCourse.getId(), me.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CourseErrorCode.NOT_COURSE_OWNER);
        assertThatThrownBy(() -> courseService.delete(seeded.getId(), me.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CourseErrorCode.NOT_COURSE_OWNER);
    }

    @Test
    @DisplayName("삭제: 없는 코스·이미 삭제한 코스는 멱등하게 성공한다")
    void 삭제_멱등() {
        Course course = register("두번삭제", me, ApprovalStatus.PENDING);
        courseService.delete(course.getId(), me.getId());
        LocalDateTime first =
                courseRepository.findById(course.getId()).orElseThrow().getDeletedAt();

        courseService.delete(course.getId(), me.getId()); // 두 번째
        courseService.delete(999_999L, me.getId()); // 없는 id

        assertThat(courseRepository.findById(course.getId()).orElseThrow().getDeletedAt())
                .isEqualTo(first);
    }

    @Test
    @DisplayName("삭제: 주차장 id를 보내면 404")
    void 삭제_주차장() {
        Parking parking =
                parkingRepository.save(
                        Parking.builder().name("주차장").location(point(37.52, 127.04)).build());

        assertThatThrownBy(() -> courseService.delete(parking.getId(), me.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CourseErrorCode.COURSE_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제된 코스는 전체 검색·상세에서도 사라진다")
    void 삭제_후_비노출() {
        Course course = register("삭제후검색", me, ApprovalStatus.APPROVED);
        courseService.delete(course.getId(), me.getId());

        assertThatThrownBy(() -> placeQueryService.getPlaceDetail(course.getId(), me.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CourseErrorCode.COURSE_DELETED);
    }

    @Test
    @DisplayName("미인증: 토큰 없이 목록·삭제 시 401")
    void 미인증_401() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/courses")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/courses/1")).andExpect(status().isUnauthorized());
    }
}
