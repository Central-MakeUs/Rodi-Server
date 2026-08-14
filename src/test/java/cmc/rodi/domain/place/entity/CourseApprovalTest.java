package cmc.rodi.domain.place.entity;

import static org.assertj.core.api.Assertions.assertThat;

import cmc.rodi.domain.member.entity.Member;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/** 코스 승인 상태 전이·soft delete·소유 판정 — 스펙 014·015·016. */
class CourseApprovalTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 14, 10, 0);
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    private Point point() {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(127.0403, 37.5273));
    }

    private Course seeded() {
        return Course.builder().name("운영자 코스").location(point()).distanceMeters(8200).build();
    }

    private Course registeredBy(Member member) {
        return Course.register("압구정로데오역", "설명", "서울특별시 강남구", point(), 8200, member);
    }

    /** 회원 id는 영속화로 채워지므로 단위 테스트에서는 리플렉션으로 넣는다. */
    private Member memberWithId(long id) {
        Member member = Member.createBySocial("course@kakao.com");
        try {
            Field idField = Member.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(member, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return member;
    }

    @Test
    @DisplayName("운영자 시딩 코스는 바로 승인 상태이고 등록자가 없다")
    void 시딩_코스는_승인_상태() {
        Course course = seeded();

        assertThat(course.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(course.isApproved()).isTrue();
        assertThat(course.getCreatedBy()).isNull();
        assertThat(course.getApprovedAt()).isNull(); // 심사를 거치지 않았다
    }

    @Test
    @DisplayName("사용자 등록 코스는 승인 대기로 시작한다")
    void 등록_코스는_승인_대기() {
        Course course = registeredBy(memberWithId(7L));

        assertThat(course.getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(course.isApproved()).isFalse();
        assertThat(course.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("승인하면 승인 시각이 찍히고, 되돌려도 그 시각은 남는다")
    void 승인_시각() {
        Course course = registeredBy(memberWithId(7L));

        course.changeApprovalStatus(ApprovalStatus.APPROVED, NOW);
        assertThat(course.getApprovedAt()).isEqualTo(NOW);

        course.changeApprovalStatus(ApprovalStatus.REJECTED, NOW.plusDays(1));
        assertThat(course.isApproved()).isFalse();
        assertThat(course.getApprovedAt()).isEqualTo(NOW); // 마지막 승인 이력 유지
    }

    @Test
    @DisplayName("같은 상태로 다시 승인해도 승인 시각이 밀리지 않는다")
    void 승인은_멱등() {
        Course course = registeredBy(memberWithId(7L));
        course.changeApprovalStatus(ApprovalStatus.APPROVED, NOW);

        course.changeApprovalStatus(ApprovalStatus.APPROVED, NOW.plusHours(3));

        assertThat(course.getApprovedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("삭제는 시각만 찍고, 다시 삭제해도 최초 시각을 유지한다")
    void 삭제는_멱등() {
        Course course = registeredBy(memberWithId(7L));

        course.delete(NOW);
        assertThat(course.isDeleted()).isTrue();
        assertThat(course.getDeletedAt()).isEqualTo(NOW);

        course.delete(NOW.plusDays(1));
        assertThat(course.getDeletedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("등록자 본인만 소유자이고, 운영자 시딩 코스는 누구의 것도 아니다")
    void 소유_판정() {
        Course mine = registeredBy(memberWithId(7L));

        assertThat(mine.isOwnedBy(7L)).isTrue();
        assertThat(mine.isOwnedBy(8L)).isFalse();
        assertThat(seeded().isOwnedBy(7L)).isFalse();
    }
}
