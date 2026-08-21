package cmc.rodi.domain.practice.entity;

/** 연습 항목 상태. 담아둠 → 다녀옴 / 안 다녀옴. 전이는 자유롭다(안 갔다가 나중에 다녀올 수 있다). */
public enum PracticeStatus {
    PLANNED,
    VISITED,
    NOT_VISITED
}
