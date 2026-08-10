package cmc.rodi.domain.member.dto;

import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.member.entity.Member;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 다음 레벨까지의 진행도(마이페이지 게이지). 막대 길이는 {@code progressPercent}를 그대로 쓰면 된다.
 *
 * <p>최상위(Navigator)는 목표가 없어 {@code nextLevelKm}이 빠지고 진행률은 100 고정이다 — 그 뒤로도 누적 거리는 계속 쌓인다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LevelProgressResponse(
        @Schema(description = "누적 주행거리(km)") double totalDistanceKm,
        @Schema(description = "현재 레벨이 시작되는 거리(km)") double currentLevelStartKm,
        @Schema(description = "다음 레벨에 필요한 거리(km). 최상위면 생략") Double nextLevelKm,
        @Schema(description = "현재 구간 진행률(0~100, 내림)") int progressPercent) {

    public static LevelProgressResponse from(Member member) {
        Level level = member.getLevel();
        long start = level == null ? 0 : level.getStartMeters();
        Long next = level == null ? null : level.nextStartMeters();
        return new LevelProgressResponse(
                toKm(member.getTotalDistanceMeters()),
                toKm(start),
                next == null ? null : toKm(next),
                member.levelProgressPercent());
    }

    /** 소수 첫째 자리에서 <b>내림</b> — 진행률과 같은 방향으로, 실제보다 앞서 보이지 않게 한다. */
    private static double toKm(long meters) {
        return Math.floor(meters / 100.0) / 10.0;
    }
}
