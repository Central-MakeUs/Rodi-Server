package cmc.rodi.domain.member.dto;

import cmc.rodi.domain.member.entity.PracticeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 홈 정렬 필터 저장 요청(스펙 007). 클라가 선택한 카테고리를 연습유형으로 풀어 전송한다(전체 교체). 빈 배열이면 필터 없음(전체 노출)으로 지운다. 무효 enum 값은
 * 역직렬화 단계에서 400.
 */
public record FilterTagsRequest(
        @Schema(
                        description = "선택한 연습유형 목록(전체 교체, 빈 배열이면 필터 해제)",
                        example = "[\"U_TURN\",\"INTERSECTION\",\"PARKING\"]")
                @NotNull
                List<PracticeType> filterTags) {}
