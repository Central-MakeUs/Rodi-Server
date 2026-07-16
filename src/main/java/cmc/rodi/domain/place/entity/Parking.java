package cmc.rodi.domain.place.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

/**
 * 주차장(place 상속). 공영주차장 open DB 컬럼과 1:1로 개별 컬럼에 저장한다(jsonb 아님). 응답에서만 feeInfo·operatingHours로 묶는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "parking")
@DiscriminatorValue("PARKING")
@PrimaryKeyJoinColumn(name = "place_id")
public class Parking extends Place {

    @Column(name = "road_address", length = 255)
    private String roadAddress;

    @Column(name = "lot_address", length = 255)
    private String lotAddress;

    @Column(name = "management_no", length = 50)
    private String managementNo;

    @Column(name = "parking_type", length = 30)
    private String parkingType; // 노외/노상/부설 등

    private Integer capacity; // 주차면수

    @Column(name = "is_free")
    private Boolean isFree;

    @Column(name = "has_accessible_space")
    private Boolean hasAccessibleSpace;

    @Column(length = 30)
    private String phone;

    @Column(length = 100)
    private String operator;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "payment_methods", length = 100)
    private String paymentMethods; // 예: "카드"

    // 요금
    @Column(name = "base_minutes")
    private Integer baseMinutes;

    @Column(name = "base_fee")
    private Integer baseFee;

    @Column(name = "add_unit_minutes")
    private Integer addUnitMinutes;

    @Column(name = "add_unit_fee")
    private Integer addUnitFee;

    @Column(name = "day_ticket_hours")
    private Integer dayTicketHours;

    @Column(name = "day_ticket_fee")
    private Integer dayTicketFee;

    @Column(name = "monthly_fee")
    private Integer monthlyFee;

    // 영업시간(예: "00:00-23:59")
    @Column(name = "weekday_hours", length = 50)
    private String weekdayHours;

    @Column(name = "saturday_hours", length = 50)
    private String saturdayHours;

    @Column(name = "holiday_hours", length = 50)
    private String holidayHours;

    @Builder
    private Parking(
            String name,
            String description,
            String address,
            Point location,
            String roadAddress,
            String lotAddress,
            String managementNo,
            String parkingType,
            Integer capacity,
            Boolean isFree,
            Boolean hasAccessibleSpace,
            String phone,
            String operator,
            String note,
            String paymentMethods,
            Integer baseMinutes,
            Integer baseFee,
            Integer addUnitMinutes,
            Integer addUnitFee,
            Integer dayTicketHours,
            Integer dayTicketFee,
            Integer monthlyFee,
            String weekdayHours,
            String saturdayHours,
            String holidayHours) {
        super(name, description, address, location);
        this.roadAddress = roadAddress;
        this.lotAddress = lotAddress;
        this.managementNo = managementNo;
        this.parkingType = parkingType;
        this.capacity = capacity;
        this.isFree = isFree;
        this.hasAccessibleSpace = hasAccessibleSpace;
        this.phone = phone;
        this.operator = operator;
        this.note = note;
        this.paymentMethods = paymentMethods;
        this.baseMinutes = baseMinutes;
        this.baseFee = baseFee;
        this.addUnitMinutes = addUnitMinutes;
        this.addUnitFee = addUnitFee;
        this.dayTicketHours = dayTicketHours;
        this.dayTicketFee = dayTicketFee;
        this.monthlyFee = monthlyFee;
        this.weekdayHours = weekdayHours;
        this.saturdayHours = saturdayHours;
        this.holidayHours = holidayHours;
    }

    @Override
    public PlaceType getPlaceType() {
        return PlaceType.PARKING;
    }
}
