package cmc.rodi.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.dto.CourseReportRequest;
import cmc.rodi.domain.place.entity.ApprovalStatus;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.CourseIssue;
import cmc.rodi.domain.place.entity.CourseReport;
import cmc.rodi.domain.place.entity.Parking;
import cmc.rodi.domain.place.exception.CourseErrorCode;
import cmc.rodi.domain.place.repository.CourseReportRepository;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.place.repository.ParkingRepository;
import cmc.rodi.domain.place.service.CourseReportService;
import cmc.rodi.domain.review.dto.ReviewFormResponse.Question;
import cmc.rodi.domain.review.service.ReviewService;
import cmc.rodi.global.common.form.FormResponse;
import cmc.rodi.global.common.form.FormType;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import cmc.rodi.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** 코스 신고(스펙 025) — 신고 폼(후기 폼과 같은 정의), 저장, 멱등, 본인 코스, 노출 불가 코스. */
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class CourseReportIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 10, 0);

    @Autowired CourseReportService courseReportService;
    @Autowired ReviewService reviewService;
    @Autowired CourseReportRepository courseReportRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired ParkingRepository parkingRepository;
    @Autowired MemberRepository memberRepository;
    @PersistenceContext EntityManager em;

    private Member reporter;
    private Member owner;

    @BeforeEach
    void setUp() {
        reporter = memberRepository.save(Member.createBySocial("reporter@kakao.com"));
        owner = memberRepository.save(Member.createBySocial("owner@kakao.com"));
    }

    private Point point() {
        return GEO.createPoint(new Coordinate(127.0, 37.5));
    }

    private Course seededCourse() {
        return courseRepository.save(Course.builder().name("운영자 코스").location(point()).build());
    }

    private Course ownedCourse(ApprovalStatus status) {
        Course course =
                courseRepository.save(
                        Course.register("사용자 코스", "설명", "서울특별시", point(), 1000, owner));
        course.changeApprovalStatus(status, NOW);
        return course;
    }

    private static CourseReportRequest request(String detail, CourseIssue... issues) {
        return new CourseReportRequest(List.of(issues), detail);
    }

    private void assertError(Runnable action, Object code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(code);
    }

    @Test
    @DisplayName("신고 폼은 복수 선택·필수이고, 후기 작성 폼의 문제 항목 문항과 제목·선택지가 같다")
    void 신고_폼() {
        Course course = seededCourse();

        FormResponse form = courseReportService.getReportForm();
        Question reviewQuestion =
                reviewService.getReviewForm(course.getId()).steps().stream()
                        .flatMap(step -> step.questions().stream())
                        .filter(q -> q.code().equals(CourseIssue.QUESTION_CODE))
                        .findFirst()
                        .orElseThrow();

        assertThat(form.questionId()).isEqualTo("courseIssues");
        assertThat(form.type()).isEqualTo(FormType.MULTI_SELECT);
        assertThat(form.required()).isTrue();
        assertThat(form.title()).isEqualTo("해당 코스에 어떤 문제가 있나요?");
        assertThat(form.options())
                .extracting("code")
                .containsExactly(
                        "VEHICLE_RESTRICTED",
                        "ROAD_INFO_MISMATCH",
                        "CONSTRUCTION",
                        "NOT_FOR_PRACTICE",
                        "OTHER");
        assertThat(form.options().get(4).textInputMaxLength()).isEqualTo(30);
        assertThat(reviewQuestion.title()).isEqualTo(form.title());
        assertThat(reviewQuestion.options()).isEqualTo(form.options());
    }

    @Test
    @DisplayName("신고하면 문제 항목·기타 입력이 직접 신고로 저장된다")
    void 신고_저장() {
        Course course = seededCourse();

        courseReportService.report(
                course.getId(),
                reporter.getId(),
                request("주말엔 입구가 잠겨 있어요", CourseIssue.ROAD_INFO_MISMATCH, CourseIssue.OTHER));
        em.flush();
        em.clear();

        CourseReport saved = courseReportRepository.findAll().get(0);
        assertThat(saved.isFromReview()).isFalse();
        assertThat(saved.getReporter().getId()).isEqualTo(reporter.getId());
        assertThat(saved.getIssues())
                .containsExactlyInAnyOrder(CourseIssue.ROAD_INFO_MISMATCH, CourseIssue.OTHER);
        assertThat(saved.getDetail()).isEqualTo("주말엔 입구가 잠겨 있어요");
    }

    @Test
    @DisplayName("같은 코스를 다시 신고하면 첫 신고를 유지한다")
    void 재신고_멱등() {
        Course course = seededCourse();
        courseReportService.report(
                course.getId(), reporter.getId(), request(null, CourseIssue.CONSTRUCTION));

        courseReportService.report(
                course.getId(), reporter.getId(), request(null, CourseIssue.NOT_FOR_PRACTICE));
        em.flush();
        em.clear();

        List<CourseReport> reports = courseReportRepository.findAll();
        assertThat(reports).hasSize(1);
        assertThat(reports.get(0).getIssues()).containsExactly(CourseIssue.CONSTRUCTION);
    }

    @Test
    @DisplayName("승인된 남의 코스는 신고할 수 있다")
    void 승인된_사용자_코스() {
        Course course = ownedCourse(ApprovalStatus.APPROVED);

        courseReportService.report(
                course.getId(), reporter.getId(), request(null, CourseIssue.CONSTRUCTION));

        assertThat(courseReportRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("본인이 등록한 코스는 400 COURSE_400_1")
    void 본인_코스_불가() {
        Course course = ownedCourse(ApprovalStatus.APPROVED);

        assertError(
                () ->
                        courseReportService.report(
                                course.getId(), owner.getId(), request(null, CourseIssue.OTHER)),
                CourseErrorCode.SELF_REPORT_NOT_ALLOWED);
    }

    @Test
    @DisplayName("남의 미승인 코스·없는 id·주차장 id는 404 COURSE_404_1")
    void 노출_불가_코스() {
        Course pending = ownedCourse(ApprovalStatus.PENDING);
        Parking parking =
                parkingRepository.save(Parking.builder().name("주차장").location(point()).build());

        for (Long id : List.of(pending.getId(), parking.getId(), Long.MAX_VALUE)) {
            assertError(
                    () ->
                            courseReportService.report(
                                    id, reporter.getId(), request(null, CourseIssue.CONSTRUCTION)),
                    CourseErrorCode.COURSE_NOT_FOUND);
        }
        assertThat(courseReportRepository.count()).isZero();
    }

    @Test
    @DisplayName("삭제된 코스는 404 COURSE_404_2")
    void 삭제된_코스() {
        Course course = seededCourse();
        course.delete(NOW);

        assertError(
                () ->
                        courseReportService.report(
                                course.getId(),
                                reporter.getId(),
                                request(null, CourseIssue.CONSTRUCTION)),
                CourseErrorCode.COURSE_DELETED);
    }

    @Test
    @DisplayName("없는 회원은 404")
    void 없는_회원() {
        Course course = seededCourse();

        assertError(
                () ->
                        courseReportService.report(
                                course.getId(), Long.MAX_VALUE, request(null, CourseIssue.OTHER)),
                ErrorCode.ENTITY_NOT_FOUND);
    }
}
