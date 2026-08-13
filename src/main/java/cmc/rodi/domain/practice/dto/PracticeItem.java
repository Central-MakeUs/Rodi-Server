package cmc.rodi.domain.practice.dto;

import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.Place;
import cmc.rodi.domain.practice.entity.MemberPractice;
import cmc.rodi.domain.practice.entity.PracticeStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 내 연습 목록 아이템. 화면에 쓰는 값(장소명·연습유형·연습 횟수·활동일·후기 작성 여부)과 동작에 필요한 식별자만 담는다. 미방문 사유는 수집 목적이라 응답에 내리지
 * 않는다.
 *
 * <p>{@code lastActivityAt}은 목록 정렬 기준과 같은 값이라 <b>항상 채워진다</b> — 담기만 한 항목도 카드에 표시할 날짜가 있어야 한다. 방문 여부는
 * {@code status}로 구분한다.
 */
public record PracticeItem(
        @Schema(description = "연습 항목 id(방문 기록·사유 제출에 사용)") Long practiceId,
        @Schema(description = "장소 id(코스 상세 이동에 사용)") Long placeId,
        @Schema(description = "장소명", example = "한강 코스") String placeName,
        @Schema(description = "연습 유형(코스=태그들, 주차장=[PARKING])") List<PracticeType> practiceTypes,
        @Schema(description = "상태") PracticeStatus status,
        @Schema(description = "다녀온 횟수") int visitCount,
        @Schema(description = "마지막 활동 시각 — 다녀왔으면 마지막 방문 시각, 아니면 담은 시각")
                LocalDateTime lastActivityAt,
        @Schema(description = "이 장소에 내가 후기를 썼는지(후기 쓰기 버튼 노출 판단)") @JsonProperty("hasReview")
                boolean hasReview) {

    public static PracticeItem of(MemberPractice practice, boolean hasReview) {
        Place place = practice.getPlace();
        return new PracticeItem(
                practice.getId(),
                place.getId(),
                place.getName(),
                practiceTypesOf(place),
                practice.getStatus(),
                practice.getVisitCount(),
                practice.recentActivityAt(),
                hasReview);
    }

    /** 코스는 등록된 태그, 주차장은 항상 주차(장소 목록·상세와 동일 표기). */
    private static List<PracticeType> practiceTypesOf(Place place) {
        return place instanceof Course course
                ? List.copyOf(course.getTags())
                : List.of(PracticeType.PARKING);
    }
}
