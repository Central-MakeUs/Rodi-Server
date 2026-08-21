package cmc.rodi.domain.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 후기 작성 결과. 방금 만든 후기 id만 돌려준다(목록 갱신·수정 진입용). */
public record ReviewCreateResponse(@Schema(description = "생성된 후기 id") Long reviewId) {}
