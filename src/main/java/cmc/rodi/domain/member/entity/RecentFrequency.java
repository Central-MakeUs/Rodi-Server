package cmc.rodi.domain.member.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Q2 최근 운전 빈도(단일 선택). 화면 라벨은 온보딩 폼으로 내려준다(스펙 019). */
@Getter
@RequiredArgsConstructor
public enum RecentFrequency {
    RARELY("거의 없음"),
    MONTHLY_1_2("월 1~2회"),
    WEEKLY_1("주 1회"),
    WEEKLY_2_3("주 2~3회"),
    WEEKLY_4_PLUS("주 4회 이상");

    private final String label;
}
