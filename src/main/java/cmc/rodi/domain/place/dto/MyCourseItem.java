package cmc.rodi.domain.place.dto;

import cmc.rodi.domain.place.entity.ApprovalStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 내가 등록한 코스 목록의 아이템(스펙 015). 화면에 쓰는 값만 담는다 — 좌표·연습유형·주행거리는 이 화면이 쓰지 않는다.
 *
 * <p>JPQL 생성자 표현식으로 직접 만든다(엔티티를 통째로 로드하지 않는다).
 */
public record MyCourseItem(
        @Schema(description = "코스 id") Long courseId,
        @Schema(description = "코스명", example = "압구정로데오역") String name,
        @Schema(description = "승인 상태") ApprovalStatus approvalStatus,
        @Schema(description = "등록 일시") LocalDateTime createdAt) {}
