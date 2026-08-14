package cmc.rodi.domain.place.entity;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.entity.PracticeType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.locationtech.jts.geom.Point;

/**
 * 코스(place 상속). 주행거리·주의사항 + 경로점(1:N) + 연습태그(N:M, {@link PracticeType} 재사용).
 *
 * <p>사용자가 등록할 수 있고(스펙 014), <b>관리자 승인({@link ApprovalStatus#APPROVED})을 받은 코스만</b> 전체 목록·검색에 노출된다.
 * 삭제는 {@code deletedAt}을 찍는 soft delete다 — 행을 지우면 다른 사용자의 북마크·후기·연습기록까지 함께 사라져, 담아둔 사람은 항목이 없어진 이유를
 * 알 수 없다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "course")
@DiscriminatorValue("COURSE")
@PrimaryKeyJoinColumn(name = "place_id")
public class Course extends Place {

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "distance_meters")
    private Integer distanceMeters;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private ApprovalStatus approvalStatus;

    /** 등록자. <b>NULL이면 운영자 시딩분</b>이라 사용자가 삭제할 수 없다(탈퇴로 NULL이 된 코스도 같이 취급). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_member_id")
    private Member createdBy;

    /** 마지막 승인 시각. 다른 상태로 바뀌어도 지우지 않는다(승인 이력). */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** 삭제 시각(soft delete). NULL이면 살아 있다. */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** 주의사항(칩). course_caution(course_id, caution, seq)에 순서대로 저장. */
    @ElementCollection
    @CollectionTable(name = "course_caution", joinColumns = @JoinColumn(name = "course_id"))
    @OrderColumn(name = "seq")
    @Column(name = "caution", length = 100)
    private List<String> cautions = new ArrayList<>();

    /**
     * 연습 태그. course_practice_type(course_id, practice_type)에 enum 이름으로 저장.
     *
     * <p>목록 응답(장소 목록·연습 목록)이 코스마다 태그를 읽어 항목 수만큼 쿼리가 나가므로 배치로 묶는다. 컬렉션이라 JOIN FETCH로는 페이지네이션과 함께 쓸 수
     * 없다(중복 행 → 메모리 페이징).
     */
    @BatchSize(size = 100)
    @ElementCollection
    @CollectionTable(name = "course_practice_type", joinColumns = @JoinColumn(name = "course_id"))
    @Column(name = "practice_type", length = 30)
    @Enumerated(EnumType.STRING)
    private Set<PracticeType> tags = new LinkedHashSet<>();

    /** 경로점(시작·경유·도착). sequence 오름차순. */
    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence asc")
    private List<Waypoint> waypoints = new ArrayList<>();

    /**
     * 운영자 시딩 코스. 등록자가 없고 <b>바로 승인 상태</b>로 만들어진다 — 심사를 거칠 주체가 없는 데이터다.
     *
     * <p>사용자 등록은 {@link #register}를 쓴다.
     */
    @Builder
    private Course(
            String name,
            String description,
            String address,
            Point location,
            Integer distanceMeters) {
        super(name, address, location);
        this.description = description;
        this.distanceMeters = distanceMeters;
        this.approvalStatus = ApprovalStatus.APPROVED;
    }

    private Course(
            String name,
            String description,
            String address,
            Point location,
            Integer distanceMeters,
            Member createdBy) {
        super(name, address, location);
        this.description = description;
        this.distanceMeters = distanceMeters;
        this.createdBy = createdBy;
        this.approvalStatus = ApprovalStatus.PENDING;
    }

    /** 사용자 등록 코스(스펙 014). 관리자가 승인하기 전까지 전체 목록·검색에 나오지 않는다. */
    public static Course register(
            String name,
            String description,
            String address,
            Point location,
            Integer distanceMeters,
            Member createdBy) {
        return new Course(name, description, address, location, distanceMeters, createdBy);
    }

    /** 코스는 등록된 주행거리를 그대로 쓴다(미등록이면 null). */
    @Override
    public Integer drivingDistanceMeters() {
        return distanceMeters;
    }

    public void addTag(PracticeType tag) {
        this.tags.add(tag);
    }

    /** 주의사항 추가(입력 순서대로 저장). */
    public void addCaution(String caution) {
        this.cautions.add(caution);
    }

    /** 경로점 추가(cascade로 함께 저장). 양방향 연관을 여기서 세팅한다. */
    public void addWaypoint(WaypointType type, short sequence, Point location, String name) {
        this.waypoints.add(
                Waypoint.builder()
                        .course(this)
                        .waypointType(type)
                        .sequence(sequence)
                        .location(location)
                        .name(name)
                        .build());
    }

    /**
     * 승인 상태 변경(스펙 016). 전이에 제약을 두지 않는다 — 승인을 되돌릴 수 있어야 운영 실수를 고친다.
     *
     * <p>같은 상태로 다시 부르면 <b>아무 일도 하지 않는다</b>. 재승인으로 {@code approvedAt}이 밀리면 "언제 승인됐는지"가 흔들린다.
     */
    public void changeApprovalStatus(ApprovalStatus status, LocalDateTime now) {
        if (this.approvalStatus == status) {
            return;
        }
        this.approvalStatus = status;
        if (status == ApprovalStatus.APPROVED) {
            this.approvedAt = now;
        }
    }

    /** 삭제 표시(soft delete). 이미 삭제됐으면 최초 시각을 유지한다. */
    public void delete(LocalDateTime now) {
        if (deletedAt == null) {
            this.deletedAt = now;
        }
    }

    @Override
    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isApproved() {
        return approvalStatus == ApprovalStatus.APPROVED;
    }

    /** 등록자 본인인지. <b>운영자 시딩분(등록자 없음)은 누구의 것도 아니다</b> — 사용자가 삭제할 수 없다. */
    public boolean isOwnedBy(Long memberId) {
        return createdBy != null && createdBy.getId().equals(memberId);
    }

    @Override
    public PlaceType getPlaceType() {
        return PlaceType.COURSE;
    }
}
