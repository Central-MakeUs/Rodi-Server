package cmc.rodi.domain.member.dto;

import cmc.rodi.domain.member.entity.CarType;
import cmc.rodi.domain.member.entity.DrivingPeriod;
import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.member.entity.RecentFrequency;
import cmc.rodi.domain.member.entity.RoadExperience;
import cmc.rodi.domain.member.entity.SoloDrivingRange;
import cmc.rodi.domain.member.entity.SoloParkingLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 온보딩 제출 요청. 모바일이 로컬에 모은 운전 경험·추가 정보를 한 번에 보낸다. 레벨은 클라이언트가 점수를 변환해 채운 값이며 서버는 저장만 한다(점수는 받지 않음).
 *
 * <p>필수는 Q1(운전 기간)·level뿐이다. Q1이 3~9년/10년 이상이면 클라이언트가 후속 질문을 건너뛰고 Navigator로 배정하며(Q2~Q4 없음),
 * Q4-1·Q4-2는 Q3에서 '혼자 연습'을 골랐을 때만 입력되므로 나머지 문항·추가정보는 모두 선택이다.
 */
public record OnboardingRequest(
        @Schema(description = "Q1 실제 운전 기간(필수)") @NotNull DrivingPeriod drivingPeriod,
        @Schema(description = "Q2 최근 운전 빈도. 선택") RecentFrequency recentFrequency,
        @Schema(description = "Q3 도로 주행 경험(복수). 선택") List<RoadExperience> roadExperiences,
        @Schema(description = "Q4-1 혼자 운전 범위(Q3=혼자 연습일 때). 선택") SoloDrivingRange soloDrivingRange,
        @Schema(description = "Q4-2 혼자 주차 수준(Q3=혼자 연습일 때). 선택") SoloParkingLevel soloParkingLevel,
        @Schema(description = "클라이언트가 변환한 레벨(필수)") @NotNull Level level,
        @Schema(description = "선호 연습유형(1~3순위, 순서=우선순위). 선택") @Size(max = 3)
                List<PracticeType> practiceTypes,
        @Schema(description = "차종. 선택") CarType carType,
        @Schema(description = "운전 목표(최대 30자). 선택") @Size(max = 30) String drivingGoal) {}
