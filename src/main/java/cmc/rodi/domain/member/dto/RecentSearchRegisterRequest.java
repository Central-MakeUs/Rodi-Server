package cmc.rodi.domain.member.dto;

import cmc.rodi.domain.member.entity.RecentSearchType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 최근 검색어 등록 요청(스펙 008). 연관검색어(스펙 009)에서 선택한 지역/장소를 프론트가 등록한다. PLACE면 placeId 필수, REGION이면 placeId는
 * 무시된다. keyword는 트림해 보관한다.
 */
public record RecentSearchRegisterRequest(
        @Schema(description = "종류(REGION 지역명 / PLACE 장소명)") @NotNull RecentSearchType type,
        @Schema(description = "표시명(트림 후 1~100자)", example = "서울특별시 강남구") @NotBlank @Size(max = 100)
                String keyword,
        @Schema(description = "장소 id(PLACE 필수, REGION은 생략)") Long placeId) {

    public RecentSearchRegisterRequest {
        keyword = keyword == null ? null : keyword.trim();
    }

    /** PLACE는 placeId가 반드시 있어야 한다(REGION은 무관). type이 null이면 @NotNull이 먼저 잡는다. */
    @AssertTrue(message = "PLACE 타입은 placeId가 필요합니다")
    public boolean isPlaceIdConsistent() {
        return type != RecentSearchType.PLACE || placeId != null;
    }
}
