package cmc.rodi.domain.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.practice.dto.PracticeStatusUpdateRequest;
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
        return new PracticeStatusUpdateRequest(PracticeStatus.VISITED, null, null);
    }

    private static PracticeStatusUpdateRequest notVisited(SkipReason reason, String detail) {
        return new PracticeStatusUpdateRequest(PracticeStatus.NOT_VISITED, reason, detail);
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
