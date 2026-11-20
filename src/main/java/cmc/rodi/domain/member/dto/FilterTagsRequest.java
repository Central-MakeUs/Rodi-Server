package cmc.rodi.domain.member.dto;

import cmc.rodi.domain.member.entity.PracticeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 옛 경로 {@code PUT /members/me/filter-tags}의 요청(스펙 007). 새 경로의 {@link SortTagsRequest}와 같은 값이며, 앱이 새
 * 경로로 바뀌면 별칭과 함께 지운다(스펙 019). 무효 enum 값은 역직렬화 단계에서 400.
 */
public record FilterTagsRequest(
        @Schema(
                        description = "정렬 기준 연습유형 목록(전체 교체, 빈 배열이면 해제)",
                        example = "[\"U_TURN\",\"INTERSECTION\",\"PARKING\"]")
                @NotNull
                List<@NotNull PracticeType> filterTags) {}
