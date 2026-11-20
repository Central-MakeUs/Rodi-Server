package cmc.rodi.domain.member.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Q1 실제 운전 기간(단일 선택). 화면 라벨은 온보딩 폼으로 내려준다(스펙 019). */
@Getter
@RequiredArgsConstructor
public enum DrivingPeriod {
    UNDER_1_MONTH("1개월 미만"),
    MONTHS_1_2("1~2개월"),
    MONTHS_3_5("3~5개월"),
    MONTHS_6_11("6~11개월"),
    YEARS_1_2("1~2년"),
    YEARS_3_9("3~9년"),
    OVER_10_YEARS("10년 이상");

    private final String label;

    /**
     * 이 기간을 고르면 후속 문항(Q2~Q4)을 건너뛰는지. 운전 경력이 충분하면 클라이언트가 바로 Navigator로 배정한다. 온보딩 폼의 {@code
     * skipsFollowUps} 표시가 이 값을 읽는다.
     */
    public boolean skipsFollowUps() {
        return this == YEARS_3_9 || this == OVER_10_YEARS;
    }
}
