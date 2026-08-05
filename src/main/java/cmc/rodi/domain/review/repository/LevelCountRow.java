package cmc.rodi.domain.review.repository;

import cmc.rodi.domain.member.entity.Level;

/** 레벨별 후기 수 집계 행. */
public interface LevelCountRow {
    Level getLevel();

    long getCnt();
}
