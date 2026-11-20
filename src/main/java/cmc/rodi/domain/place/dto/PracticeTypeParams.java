package cmc.rodi.domain.place.dto;

import cmc.rodi.domain.member.entity.PracticeType;
import java.util.List;
import java.util.Objects;

/** 목록·검색·마커의 {@code practiceTypes} 필터 파라미터 정규화(스펙 019). */
public final class PracticeTypeParams {

    private PracticeTypeParams() {}

    /**
     * 파라미터를 중복 없는 불변 목록으로 만든다. 생략({@code null})과 빈 값({@code practiceTypes=}은 원소 {@code null}로
     * 바인딩된다)은 빈 목록 — 필터 없음이다.
     */
    public static List<PracticeType> normalize(List<PracticeType> practiceTypes) {
        if (practiceTypes == null) {
            return List.of();
        }
        return practiceTypes.stream().filter(Objects::nonNull).distinct().toList();
    }
}
