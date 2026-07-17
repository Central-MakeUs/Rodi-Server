package cmc.rodi.domain.place.dto;

import cmc.rodi.domain.place.entity.Parking;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 장소 상세의 주차장 전용 블록({@link PlaceDetailResponse#parking()}). 코스면 null. 저장은 개별 컬럼이지만 응답에선 요금·영업시간을
 * feeInfo·operatingHours로 묶는다.
 */
public record ParkingDetail(
        @Schema(description = "도로명 주소") String roadAddress,
        @Schema(description = "지번 주소") String lotAddress,
        @Schema(description = "관리번호") String managementNo,
        @Schema(description = "주차장 유형", example = "노외") String parkingType,
        @Schema(description = "총 주차면수") Integer capacity,
        @Schema(description = "무료 여부(무료만 true, 유료·혼합은 false)") @JsonProperty("isFree") boolean free,
        @Schema(description = "장애인 주차구역 여부") Boolean hasAccessibleSpace,
        @Schema(description = "전화") String phone,
        @Schema(description = "운영기관") String operator,
        @Schema(description = "비고") String note,
        @Schema(description = "요금 정보") FeeInfo feeInfo,
        @Schema(description = "영업시간") OperatingHours operatingHours) {

    /** 요금(저장은 flat 컬럼). */
    public record FeeInfo(
            @Schema(description = "기본 시간(분)") Integer baseMinutes,
            @Schema(description = "기본 요금(원)") Integer baseFee,
            @Schema(description = "추가 단위(분)") Integer addUnitMinutes,
            @Schema(description = "추가 요금(원)") Integer addUnitFee,
            @Schema(description = "일일권 시간") Integer dayTicketHours,
            @Schema(description = "일일권 요금(원)") Integer dayTicketFee,
            @Schema(description = "월정기(원)") Integer monthlyFee) {}

    /** 영업시간(예: "00:00-23:59"). */
    public record OperatingHours(
            @Schema(description = "평일") String weekday,
            @Schema(description = "토요일") String saturday,
            @Schema(description = "공휴일") String holiday) {}

    public static ParkingDetail from(Parking parking) {
        return new ParkingDetail(
                parking.getRoadAddress(),
                parking.getLotAddress(),
                parking.getManagementNo(),
                parking.getParkingType(),
                parking.getCapacity(),
                // 무료만 true. 유료(false)·요금 혼합(null)은 false
                Boolean.TRUE.equals(parking.getIsFree()),
                parking.getHasAccessibleSpace(),
                parking.getPhone(),
                parking.getOperator(),
                parking.getNote(),
                new FeeInfo(
                        parking.getBaseMinutes(),
                        parking.getBaseFee(),
                        parking.getAddUnitMinutes(),
                        parking.getAddUnitFee(),
                        parking.getDayTicketHours(),
                        parking.getDayTicketFee(),
                        parking.getMonthlyFee()),
                new OperatingHours(
                        parking.getWeekdayHours(),
                        parking.getSaturdayHours(),
                        parking.getHolidayHours()));
    }
}
