package cmc.rodi.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.dto.CourseApprovalRequest;
import cmc.rodi.domain.place.dto.CourseApprovalResponse;
import cmc.rodi.domain.place.dto.MyCourseItem;
import cmc.rodi.domain.place.dto.PlaceListItem;
import cmc.rodi.domain.place.dto.PlaceListRequest;
import cmc.rodi.domain.place.entity.ApprovalStatus;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.Parking;
import cmc.rodi.domain.place.exception.CourseErrorCode;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.place.repository.ParkingRepository;
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

/** 관리자 코스 승인 상태 변경(스펙 016) — 자유 전이·멱등·전체 조회 즉시 반영. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class CourseApprovalIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);
    private static final double SW_LAT = 36.20, SW_LNG = 127.20, NE_LAT = 36.50, NE_LNG = 127.50;
    private static final double ME_LAT = 36.35, ME_LNG = 127.35;

    @Autowired CourseService courseService;
    @Autowired CourseQueryService courseQueryService;
    @Autowired PlaceQueryService placeQueryService;
    @Autowired CourseRepository courseRepository;
    @Autowired ParkingRepository parkingRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired MockMvc mockMvc;

    private Member owner;

    private static Point point(double lat, double lng) {
        return GEO.createPoint(new Coordinate(lng, lat));
    }

    @BeforeEach
    void setUp() {
        owner = memberRepository.save(Member.createBySocial("approval@kakao.com"));
    }

    private Course course(String name, ApprovalStatus status) {
        Course course =
                Course.register(
                        name,
                        "설명",
                        "대전광역시 유성구",
                        point(36.35, 127.35),
                        3000,
                        owner);
        course.addTag(PracticeType.STRAIGHT);
        course.changeApprovalStatus(status, LocalDateTime.now());
        return courseRepository.save(course);
    }

    private List<String> visibleNames() {
        CursorPage<PlaceListItem> page =
                placeQueryService.getPlaces(
                        new PlaceListRequest(
                                SW_LAT, SW_LNG, NE_LAT, NE_LNG, ME_LAT, ME_LNG, 50, null),
                        null);
        return page.items().stream().map(PlaceListItem::name).toList();
    }

    @Test
    @DisplayName("PENDING을 APPROVED로 바꾸면 approvedAt이 채워지고 전체 목록에 즉시 노출된다")
    void 승인() {
        Course course = course("승인대상", ApprovalStatus.PENDING);
        assertThat(visibleNames()).doesNotContain("승인대상");

        CourseApprovalResponse response =
                courseService.changeApprovalStatus(
                        course.getId(), new CourseApprovalRequest(ApprovalStatus.APPROVED));

        assertThat(response.approvalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(response.approvedAt()).isNotNull();
        assertThat(visibleNames()).contains("승인대상");
    }

    @Test
    @DisplayName("APPROVED를 REJECTED로 되돌리면 전체 조회에서 빠지고 내 코스 목록에는 반려로 남는다")
    void 반려로_되돌림() {
        Course course = course("반려전환", ApprovalStatus.APPROVED);
        assertThat(visibleNames()).contains("반려전환");

        courseService.changeApprovalStatus(
                course.getId(), new CourseApprovalRequest(ApprovalStatus.REJECTED));

        assertThat(visibleNames()).doesNotContain("반려전환");
        assertThat(courseQueryService.getMyCourses(owner.getId(), null, 20, null).items())
                .extracting(MyCourseItem::approvalStatus)
                .containsExactly(ApprovalStatus.REJECTED);
    }

    @Test
    @DisplayName("같은 상태로 다시 보내면 approvedAt이 바뀌지 않는다")
    void 같은_상태_멱등() {
        Course course = course("멱등", ApprovalStatus.PENDING);
        CourseApprovalResponse first =
                courseService.changeApprovalStatus(
                        course.getId(), new CourseApprovalRequest(ApprovalStatus.APPROVED));

        CourseApprovalResponse second =
                courseService.changeApprovalStatus(
                        course.getId(), new CourseApprovalRequest(ApprovalStatus.APPROVED));

        assertThat(second.approvedAt()).isEqualTo(first.approvedAt());
    }

    @Test
    @DisplayName("없는 코스·주차장 id는 COURSE_404_1, 삭제된 코스는 COURSE_404_2")
    void 오류() {
        Parking parking =
                parkingRepository.save(
                        Parking.builder().name("주차장").location(point(36.36, 127.36)).build());
        Course deleted = course("삭제됨", ApprovalStatus.APPROVED);
        deleted.delete(LocalDateTime.now());

        CourseApprovalRequest request = new CourseApprovalRequest(ApprovalStatus.APPROVED);

        assertThatThrownBy(() -> courseService.changeApprovalStatus(999_999L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CourseErrorCode.COURSE_NOT_FOUND);
        assertThatThrownBy(() -> courseService.changeApprovalStatus(parking.getId(), request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CourseErrorCode.COURSE_NOT_FOUND);
        assertThatThrownBy(() -> courseService.changeApprovalStatus(deleted.getId(), request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CourseErrorCode.COURSE_DELETED);
    }

    @Test
    @DisplayName("미인증: 토큰 없이 승인 상태 변경 호출 시 401")
    void 미인증_401() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/admin/courses/{courseId}/approval", 1L)
                                .contentType("application/json")
                                .content("{\"approvalStatus\":\"APPROVED\"}"))
                .andExpect(status().isUnauthorized());
    }
}
