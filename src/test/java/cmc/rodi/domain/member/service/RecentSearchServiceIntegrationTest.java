package cmc.rodi.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import cmc.rodi.domain.member.dto.RecentSearchRegisterRequest;
import cmc.rodi.domain.member.dto.RecentSearchResponse;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.entity.RecentSearchType;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.support.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** 최근 검색어 재설계(스펙 008) 서비스 — 등록(REGION/PLACE)·중복 갱신·부분 유니크·상한 15·조회·삭제를 실 DB로 검증. */
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class RecentSearchServiceIntegrationTest {

    @Autowired RecentSearchService recentSearchService;
    @Autowired MemberRepository memberRepository;

    private Long member() {
        return memberRepository.save(Member.createBySocial("recent@test.com")).getId();
    }

    private static RecentSearchRegisterRequest region(String name) {
        return new RecentSearchRegisterRequest(RecentSearchType.REGION, name, null);
    }

    private static RecentSearchRegisterRequest place(String name, Long placeId) {
        return new RecentSearchRegisterRequest(RecentSearchType.PLACE, name, placeId);
    }

    @Test
    @DisplayName("등록·조회: 최신순 + type·keyword·placeId")
    void 등록_조회() {
        Long m = member();
        recentSearchService.register(m, region("서울특별시 강남구"));
        recentSearchService.register(m, place("강남 운전연습 코스", 101L));

        List<RecentSearchResponse> recent = recentSearchService.getRecent(m);
        assertThat(recent)
                .extracting(RecentSearchResponse::keyword)
                .containsExactly("강남 운전연습 코스", "서울특별시 강남구"); // 최신이 앞

        RecentSearchResponse place = recent.get(0);
        assertThat(place.type()).isEqualTo(RecentSearchType.PLACE);
        assertThat(place.placeId()).isEqualTo(101L);
        RecentSearchResponse region = recent.get(1);
        assertThat(region.type()).isEqualTo(RecentSearchType.REGION);
        assertThat(region.placeId()).isNull();
    }

    @Test
    @DisplayName("지역 중복: 같은 이름 재등록은 새 행 없이 최신 갱신(맨 앞)")
    void 지역_중복_갱신() {
        Long m = member();
        recentSearchService.register(m, region("서울특별시 강남구"));
        recentSearchService.register(m, region("부산광역시 해운대구"));
        recentSearchService.register(m, region("서울특별시 강남구")); // 재선택

        assertThat(recentSearchService.getRecent(m))
                .extracting(RecentSearchResponse::keyword)
                .containsExactly("서울특별시 강남구", "부산광역시 해운대구");
    }

    @Test
    @DisplayName("장소 중복: 같은 placeId 재등록은 최신 갱신, 이름 같아도 placeId 다르면 별개")
    void 장소_중복_판정() {
        Long m = member();
        recentSearchService.register(m, place("강남 코스", 101L));
        recentSearchService.register(m, place("강남 코스", 202L)); // 이름 같고 placeId 다름 → 별개
        recentSearchService.register(m, place("강남 코스", 101L)); // 101 재선택 → 갱신(맨 앞)

        List<RecentSearchResponse> recent = recentSearchService.getRecent(m);
        assertThat(recent).hasSize(2); // 101, 202 두 개
        assertThat(recent.get(0).placeId()).isEqualTo(101L); // 재선택으로 맨 앞
        assertThat(recent).extracting(RecentSearchResponse::placeId).containsExactly(101L, 202L);
    }

    @Test
    @DisplayName("상한 15개: 16번째 등록 시 가장 오래된 것 제거")
    void 상한_초과_제거() {
        Long m = member();
        for (int i = 1; i <= 16; i++) {
            recentSearchService.register(m, region("지역" + i));
        }

        List<RecentSearchResponse> recent = recentSearchService.getRecent(m);
        assertThat(recent).hasSize(15);
        assertThat(recent).extracting(RecentSearchResponse::keyword).doesNotContain("지역1");
        assertThat(recent.get(0).keyword()).isEqualTo("지역16");
    }

    @Test
    @DisplayName("개별 삭제: 본인 것만 지우고, 타인·없는 id는 no-op")
    void 개별_삭제() {
        Long m = member();
        Long other = member();
        recentSearchService.register(m, region("서울특별시 강남구"));
        recentSearchService.register(other, region("부산광역시 해운대구"));

        Long myId = recentSearchService.getRecent(m).get(0).id();
        recentSearchService.delete(m, myId);
        assertThat(recentSearchService.getRecent(m)).isEmpty();

        // 타인 소유 id를 m이 지우려 해도 no-op
        Long otherId = recentSearchService.getRecent(other).get(0).id();
        recentSearchService.delete(m, otherId);
        assertThat(recentSearchService.getRecent(other)).hasSize(1);
    }

    @Test
    @DisplayName("전체 삭제: 회원의 모든 항목 제거")
    void 전체_삭제() {
        Long m = member();
        recentSearchService.register(m, region("서울특별시 강남구"));
        recentSearchService.register(m, place("강남 코스", 101L));

        recentSearchService.deleteAll(m);
        assertThat(recentSearchService.getRecent(m)).isEmpty();
    }
}
