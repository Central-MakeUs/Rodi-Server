package cmc.rodi.domain.member.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 주로 타는 차종(단일 선택). 화면 라벨은 온보딩 폼으로 내려준다(스펙 019). */
@Getter
@RequiredArgsConstructor
public enum CarType {
    LIGHT("경차"),
    COMPACT("소형차"),
    MIDSIZE("중형차"),
    SEMI_LARGE("준대형"),
    LARGE("대형차"),
    SUV("SUV");

    private final String label;
}
