package cmc.rodi.domain.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.practice.dto.PracticeStatusUpdateRequest;
import cmc.rodi.domain.practice.dto.PracticeVisitResponse;
import cmc.rodi.domain.practice.entity.MemberPractice;
import cmc.rodi.domain.practice.entity.PracticeStatus;
import cmc.rodi.domain.practice.entity.SkipReason;
import cmc.rodi.domain.practice.exception.PracticeErrorCode;
import cmc.rodi.domain.practice.repository.MemberPracticeRepository;
import cmc.rodi.domain.practice.service.PracticeService;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.support.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** 방문 여부 상태 변경·목록 제거 — 횟수 누적, 사유 필수·수정 불가, 소유자 검사, 멱등 삭제. */
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class PracticeStatusIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired PracticeService practiceService;
    @Autowired MemberPracticeRepository memberPracticeRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired MemberRepository memberRepository;

    private Course seedCourse(String name) {
        return courseRepository.save(
                Course.builder()
                        .name(name)
                        .location(GEO.createPoint(new Coordinate(127.0, 37.5)))
                        .build());
    }

    private Member seedMember(String email) {
        return memberRepository.save(Member.createBySocial(email));
    }

    private Long seedPractice(Member member, String courseName) {
        return practiceService
                .register(seedCourse(courseName).getId(), member.getId())
                .practiceId();
    }

    private static PracticeStatusUpdateRequest visited() {
        return new PracticeStatusUpdateRequest(PracticeStatus.VISITED, null, null, null);
    }

    private static PracticeStatusUpdateRequest visited(int certifiedMeters) {
        return new PracticeStatusUpdateRequest(PracticeStatus.VISITED, certifiedMeters, null, null);
    }

    private static PracticeStatusUpdateRequest notVisited(SkipReason reason, String detail) {
        return new PracticeStatusUpdateRequest(PracticeStatus.NOT_VISITED, null, reason, detail);
    }

    @Test
    @DisplayName("VISITED로 바꾸면 횟수가 오르고 방문 시각이 기록된다 — 다시 보내면 또 오른다")
    void 방문_처리() {
        Member me = seedMember("visit@kakao.com");
        Long practiceId = seedPractice(me, "방문 코스");

        practiceService.updateStatus(practiceId, me.getId(), visited());
        MemberPractice after = memberPracticeRepository.findById(practiceId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(PracticeStatus.VISITED);
        assertThat(after.getVisitCount()).isEqualTo(1);
        assertThat(after.getVisitedAt()).isNotNull();

        // 같은 코스 재연습 — 클라이언트가 다시 호출하면 횟수가 또 오른다(중복 방지는 클라 몫)
        practiceService.updateStatus(practiceId, me.getId(), visited());
        assertThat(memberPracticeRepository.findById(practiceId).orElseThrow().getVisitCount())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("인정 주행거리가 필요 거리(코스 5km의 40% = 2km)에 도달하면 서버가 인증으로 판정한다")
    void 방문_인증() {
        Member me = seedMember("certify@kakao.com");
        Course course =
                courseRepository.save(
                        Course.builder()
                                .name("인증 코스")
                                .location(GEO.createPoint(new Coordinate(127.0, 37.5)))
                                .distanceMeters(5_000)
                                .build());
        Long practiceId = practiceService.register(course.getId(), me.getId()).practiceId();

        // 1.9km — 필요 거리(2km)에 못 미쳐 인증 안 됨
        PracticeVisitResponse partial =
                practiceService.updateStatus(practiceId, me.getId(), visited(1_900));
        assertThat(partial.requiredDistanceMeters()).isEqualTo(2_000);
        assertThat(partial.certifiedNow()).isFalse();
        assertThat(partial.verified()).isFalse();

        // 2km — 도달해 인증
        PracticeVisitResponse certified =
                practiceService.updateStatus(practiceId, me.getId(), visited(2_000));
        assertThat(certified.certifiedNow()).isTrue();
        assertThat(certified.verified()).isTrue();

        MemberPractice after = memberPracticeRepository.findById(practiceId).orElseThrow();
        assertThat(after.isVerified()).isTrue();
        assertThat(after.getCertifiedDistanceMeters()).isEqualTo(3_900); // 1900 + 2000
        assertThat(after.getVisitCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("측정 없이 다녀왔어요만 누르면(거리 생략) 인증되지 않는다")
    void 측정_없는_방문() {
        Member me = seedMember("noGps@kakao.com");
        Course course =
                courseRepository.save(
                        Course.builder()
                                .name("경로만 본 코스")
                                .location(GEO.createPoint(new Coordinate(127.0, 37.5)))
                                .distanceMeters(3_000)
                                .build());
        Long practiceId = practiceService.register(course.getId(), me.getId()).practiceId();

        PracticeVisitResponse response =
                practiceService.updateStatus(practiceId, me.getId(), visited());

        assertThat(response.visitCount()).isEqualTo(1); // 연습기록은 남는다
        assertThat(response.addedCertifiedDistanceMeters()).isZero();
        assertThat(response.certifiedNow()).isFalse();
        assertThat(response.verified()).isFalse();
    }

    @Test
    @DisplayName("한 번 인증되면 이후 미인증 방문이 있어도 인증 상태는 유지된다")
    void 인증_유지() {
        Member me = seedMember("keep@kakao.com");
        Course course =
                courseRepository.save(
                        Course.builder()
                                .name("유지 코스")
                                .location(GEO.createPoint(new Coordinate(127.0, 37.5)))
                                .distanceMeters(2_000)
                                .build());
        Long practiceId = practiceService.register(course.getId(), me.getId()).practiceId();

        practiceService.updateStatus(practiceId, me.getId(), visited(800)); // 필요 800m → 인증
        PracticeVisitResponse second =
                practiceService.updateStatus(practiceId, me.getId(), visited(100));

        assertThat(second.certifiedNow()).isFalse(); // 이번 회차는 미달
        assertThat(second.verified()).isTrue(); // 항목은 여전히 인증됨
    }

    @Test
    @DisplayName("NOT_VISITED는 사유가 필수고, 기타면 직접 입력이 저장된다")
    void 미방문_처리() {
        Member me = seedMember("skip@kakao.com");
        Long practiceId = seedPractice(me, "미방문 코스");

        assertThatThrownBy(
                        () ->
                                practiceService.updateStatus(
                                        practiceId, me.getId(), notVisited(null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PracticeErrorCode.SKIP_REASON_REQUIRED);

        practiceService.updateStatus(
                practiceId, me.getId(), notVisited(SkipReason.OTHER, "차가 정비 중이었어요"));

        MemberPractice after = memberPracticeRepository.findById(practiceId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(PracticeStatus.NOT_VISITED);
        assertThat(after.getSkipReason()).isEqualTo(SkipReason.OTHER);
        assertThat(after.getSkipDetail()).isEqualTo("차가 정비 중이었어요");
        assertThat(after.getVisitCount()).isZero();
    }

    @Test
    @DisplayName("이미 저장된 미방문 사유는 덮어쓸 수 없고(409), VISITED로는 바꿀 수 있다(사유는 비워짐)")
    void 미방문_사유_수정불가() {
        Member me = seedMember("skip2@kakao.com");
        Long practiceId = seedPractice(me, "사유 코스");
        practiceService.updateStatus(practiceId, me.getId(), notVisited(SkipReason.TOO_FAR, null));

        assertThatThrownBy(
                        () ->
                                practiceService.updateStatus(
                                        practiceId,
                                        me.getId(),
                                        notVisited(SkipReason.SCHEDULE_DID_NOT_MATCH, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PracticeErrorCode.SKIP_REASON_ALREADY_SET);

        practiceService.updateStatus(practiceId, me.getId(), visited());
        MemberPractice after = memberPracticeRepository.findById(practiceId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(PracticeStatus.VISITED);
        assertThat(after.getSkipReason()).isNull();
        assertThat(after.getSkipDetail()).isNull();
    }

    @Test
    @DisplayName("타인 항목의 상태 변경·삭제는 403")
    void 소유자_검사() {
        Member owner = seedMember("owner2@kakao.com");
        Member other = seedMember("other2@kakao.com");
        Long practiceId = seedPractice(owner, "남의 코스");

        assertThatThrownBy(() -> practiceService.updateStatus(practiceId, other.getId(), visited()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PracticeErrorCode.NOT_PRACTICE_OWNER);
        assertThatThrownBy(() -> practiceService.delete(practiceId, other.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PracticeErrorCode.NOT_PRACTICE_OWNER);
    }

    @Test
    @DisplayName("삭제는 멱등 — 없는 항목을 지워도 성공한다")
    void 삭제_멱등() {
        Member me = seedMember("del2@kakao.com");
        Long practiceId = seedPractice(me, "지울 코스");

        practiceService.delete(practiceId, me.getId());
        assertThat(memberPracticeRepository.findById(practiceId)).isEmpty();

        assertThatCode(() -> practiceService.delete(practiceId, me.getId()))
                .doesNotThrowAnyException();
        assertThatCode(() -> practiceService.delete(999_999L, me.getId()))
                .doesNotThrowAnyException();
    }
}
