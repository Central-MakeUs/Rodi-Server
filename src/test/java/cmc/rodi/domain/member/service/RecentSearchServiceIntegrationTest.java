package cmc.rodi.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import cmc.rodi.domain.member.dto.RecentSearchResponse;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.support.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** 최근 검색어(스펙 008) 서비스 — upsert 중복갱신·상한 15 eviction·최신순 조회·개별/전체 삭제를 실 DB로 검증. */
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class RecentSearchServiceIntegrationTest {

    @Autowired RecentSearchService recentSearchService;
    @Autowired MemberRepository memberRepository;

    private Long member() {
        return memberRepository.save(Member.createBySocial("recent@test.com")).getId();
    }

    @Test
    @DisplayName("기록·조회: 최신순으로 반환")
    void 기록_조회() {
        Long m = member();
        recentSearchService.record(m, "강남");
        recentSearchService.record(m, "한강");
        recentSearchService.record(m, "판교");

        assertThat(recentSearchService.getRecent(m))
                .extracting(RecentSearchResponse::keyword)
                .containsExactly("판교", "한강", "강남"); // 최신이 앞
    }

    @Test
    @DisplayName("중복 검색: 새 행 추가 없이 최신으로 갱신(맨 앞 이동)")
    void 중복_갱신() {
        Long m = member();
        recentSearchService.record(m, "강남");
        recentSearchService.record(m, "한강");
        recentSearchService.record(m, "강남"); // 재검색

        assertThat(recentSearchService.getRecent(m))
                .extracting(RecentSearchResponse::keyword)
                .containsExactly("강남", "한강"); // 중복 없이 강남이 맨 앞
    }

    @Test
    @DisplayName("상한 15개: 16번째 기록 시 가장 오래된 것 제거")
    void 상한_초과_제거() {
        Long m = member();
        for (int i = 1; i <= 16; i++) {
            recentSearchService.record(m, "키워드" + i);
        }

        List<RecentSearchResponse> recent = recentSearchService.getRecent(m);
        assertThat(recent).hasSize(15);
        assertThat(recent).extracting(RecentSearchResponse::keyword).doesNotContain("키워드1");
        assertThat(recent.get(0).keyword()).isEqualTo("키워드16"); // 최신
    }

    @Test
    @DisplayName("개별 삭제: 본인 것만 지우고, 타인·없는 id는 no-op")
    void 개별_삭제() {
        Long m = member();
        Long other = member();
        recentSearchService.record(m, "강남");
        recentSearchService.record(m, "한강");
        recentSearchService.record(other, "판교");

        Long gangnamId =
                recentSearchService.getRecent(m).stream()
                        .filter(r -> r.keyword().equals("강남"))
                        .findFirst()
                        .orElseThrow()
                        .id();

        recentSearchService.delete(m, gangnamId);
        assertThat(recentSearchService.getRecent(m))
                .extracting(RecentSearchResponse::keyword)
                .containsExactly("한강");

        // 타인 소유 id를 m이 지우려 해도 no-op (other의 판교는 그대로)
        Long pangyoId = recentSearchService.getRecent(other).get(0).id();
        recentSearchService.delete(m, pangyoId);
        assertThat(recentSearchService.getRecent(other))
                .extracting(RecentSearchResponse::keyword)
                .containsExactly("판교");
    }

    @Test
    @DisplayName("전체 삭제: 회원의 모든 검색어 제거")
    void 전체_삭제() {
        Long m = member();
        recentSearchService.record(m, "강남");
        recentSearchService.record(m, "한강");

        recentSearchService.deleteAll(m);
        assertThat(recentSearchService.getRecent(m)).isEmpty();
    }
}
