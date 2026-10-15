package cmc.rodi.domain.member.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Q3 도로 주행 경험(복수 선택). 화면 라벨은 온보딩 폼으로 내려준다(스펙 019). */
@Getter
@RequiredArgsConstructor
public enum RoadExperience {
    NONE("없음"),
    ACCOMPANIED("동승 연습"),
    PROFESSIONAL_TRAINING("전문 도로 연수"),
    SOLO("혼자 연습");

    private final String label;

    /**
     * 단독으로만 고를 수 있는 선택지인지('없음'). 다른 경험과 함께 오면 모순이라 제출 검증에서 거부한다(스펙 019).
     *
     * <p>온보딩 폼의 배타 표시도 이 값을 읽어, 검증과 화면이 같은 출처를 본다.
     */
    public boolean isExclusive() {
        return this == NONE;
    }
}
