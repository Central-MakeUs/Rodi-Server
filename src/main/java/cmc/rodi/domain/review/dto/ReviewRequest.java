package cmc.rodi.domain.review.dto;

import cmc.rodi.domain.review.entity.Congestion;
import cmc.rodi.domain.review.entity.Difficulty;
import cmc.rodi.domain.review.entity.PracticeMethod;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 후기 작성·수정 공용 요청. 수정은 전체 교체(PUT)라 작성과 같은 바디를 쓴다. 작성 당시 회원 레벨은 서버가 채우므로 요청에 없다. */
public record ReviewRequest(
        @Schema(description = "추천 여부", example = "true") @NotNull @JsonProperty("isRecommended")
                Boolean recommended,
        @Schema(description = "체감 난이도") @NotNull Difficulty difficulty,
        @Schema(description = "혼잡도") @NotNull Congestion congestion,
        @Schema(description = "연습 방법") @NotNull PracticeMethod practiceMethod,
        @Schema(description = "후기 내용(1~1000자)", example = "차선이 넓고 신호가 단순해서 처음 도로 나갈 때 딱이었어요.")
                @NotBlank
                @Size(max = 1000)
                String content,
        @Schema(description = "주의사항(선택, 길이 제한 없음)", example = "주말 오후엔 자전거 통행이 많습니다.")
                String caution) {}
