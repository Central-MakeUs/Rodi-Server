package cmc.rodi.domain.member.entity;

import cmc.rodi.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 온보딩 원자료(운전 경험·추가 정보). member와 1:1(member_id 공유 PK)이며 저장 목적이라 마이페이지엔 노출하지 않는다. 복수/순위 응답은 별도 테이블 없이
 * jsonb 배열로 한 행에 저장한다(순서 보존).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "member_onboarding")
public class MemberOnboarding extends BaseEntity {

    /** member_id = PK이자 FK. {@link MapsId}로 연결된 member의 id를 그대로 쓴다. */
    @Id private Long memberId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id")
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "driving_period", nullable = false, length = 20)
    private DrivingPeriod drivingPeriod; // Q1 (필수)

    @Enumerated(EnumType.STRING)
    @Column(name = "recent_frequency", length = 20)
    private RecentFrequency recentFrequency; // Q2 (선택)

    @Enumerated(EnumType.STRING)
    @Column(name = "solo_driving_range", length = 20)
    private SoloDrivingRange soloDrivingRange; // Q4-1 (Q3=혼자 연습일 때, 선택)

    @Enumerated(EnumType.STRING)
    @Column(name = "solo_parking_level", length = 20)
    private SoloParkingLevel soloParkingLevel; // Q4-2 (Q3=혼자 연습일 때, 선택)

    /** Q3 도로 주행 경험(복수, 선택). jsonb 배열 예: ["SOLO"]. 미입력 시 null. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "road_experiences", columnDefinition = "jsonb")
    private List<RoadExperience> roadExperiences;

    /** 선호 연습유형. jsonb 배열이며 순서=우선순위(index 0 = 1순위), 최대 3개. 선택 안 하면 빈 배열. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "practice_types", nullable = false, columnDefinition = "jsonb")
    private List<PracticeType> practiceTypes;

    @Enumerated(EnumType.STRING)
    @Column(name = "car_type", length = 20)
    private CarType carType; // 선택

    /** 온보딩 완료 시각. 행 존재 자체가 완료를 뜻해 재제출 거부에 쓰인다. */
    @Column(name = "onboarded_at", nullable = false)
    private LocalDateTime onboardedAt;

    @Builder
    private MemberOnboarding(
            Member member,
            DrivingPeriod drivingPeriod,
            RecentFrequency recentFrequency,
            SoloDrivingRange soloDrivingRange,
            SoloParkingLevel soloParkingLevel,
            List<RoadExperience> roadExperiences,
            List<PracticeType> practiceTypes,
            CarType carType,
            LocalDateTime onboardedAt) {
        this.member = member;
        this.drivingPeriod = drivingPeriod;
        this.recentFrequency = recentFrequency;
        this.soloDrivingRange = soloDrivingRange;
        this.soloParkingLevel = soloParkingLevel;
        this.roadExperiences = roadExperiences;
        this.practiceTypes = practiceTypes != null ? practiceTypes : List.of();
        this.carType = carType;
        this.onboardedAt = onboardedAt;
    }
}
