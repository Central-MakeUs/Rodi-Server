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
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 온보딩 제출 요청. 모바일이 로컬에 모은 운전 경험·추가 정보를 한 번에 보낸다. 레벨은 클라이언트가 점수를 변환해 채운 값이며 서버는 저장만 한다(점수는 받지 않음). 추가
 * 정보(연습유형·차종·운전목표)는 선택이라 비어 있을 수 있다.
 */
public record OnboardingRequest(
        @Schema(description = "Q1 실제 운전 기간") @NotNull DrivingPeriod drivingPeriod,
        @Schema(description = "Q2 최근 운전 빈도") @NotNull RecentFrequency recentFrequency,
        @Schema(description = "Q3 도로 주행 경험(복수)") @NotEmpty List<RoadExperience> roadExperiences,
        @Schema(description = "Q4 혼자 운전 범위") @NotNull SoloDrivingRange soloDrivingRange,
        @Schema(description = "Q5 혼자 주차 수준") @NotNull SoloParkingLevel soloParkingLevel,
        @Schema(description = "클라이언트가 변환한 레벨") @NotNull Level level,
        @Schema(description = "선호 연습유형(1~3순위, 순서=우선순위). 선택") @Size(max = 3)
                List<PracticeType> practiceTypes,
        @Schema(description = "차종. 선택") CarType carType,
        @Schema(description = "운전 목표(최대 30자). 선택") @Size(max = 30) String drivingGoal) {}
