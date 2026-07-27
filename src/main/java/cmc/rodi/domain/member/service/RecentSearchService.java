package cmc.rodi.domain.member.service;

import cmc.rodi.domain.member.dto.RecentSearchResponse;
import cmc.rodi.domain.member.repository.RecentSearchRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최근 검색어(스펙 008) — 저장·조회·삭제. 저장은 검색(로그인 회원, 첫 페이지)에서 호출되며 upsert로 중복 없이 최신 갱신하고 상한(15) 초과분을 정리한다.
 * 최적화(검색 응답에서 분리)는 후속 과제라 지금은 단순 동기 처리.
 */
@Service
@RequiredArgsConstructor
public class RecentSearchService {

    /** 회원당 보관 상한. 초과 시 오래된 것부터 제거. */
    static final int MAX_RECENT = 15;

    private final RecentSearchRepository recentSearchRepository;

    /** 검색어 기록. 같은 키워드는 최신으로 갱신(맨 앞), 상한 초과분은 제거. keyword는 호출부(검색)에서 트림·검증된 값. */
    @Transactional
    public void record(Long memberId, String keyword) {
        recentSearchRepository.upsert(memberId, keyword);
        recentSearchRepository.deleteBeyondCap(memberId, MAX_RECENT);
    }

    /** 회원의 최근 검색어를 최신순으로 반환(상한 이내). 없으면 빈 목록. */
    @Transactional(readOnly = true)
    public List<RecentSearchResponse> getRecent(Long memberId) {
        return recentSearchRepository
                .findByMemberIdOrderBySearchedAtDescIdDesc(memberId, PageRequest.of(0, MAX_RECENT))
                .stream()
                .map(RecentSearchResponse::from)
                .toList();
    }

    /** 개별 삭제(본인 것만, 없거나 타인 것이면 no-op). */
    @Transactional
    public void delete(Long memberId, Long id) {
        recentSearchRepository.deleteByIdAndMemberId(id, memberId);
    }

    /** 전체 삭제(없어도 no-op). */
    @Transactional
    public void deleteAll(Long memberId) {
        recentSearchRepository.deleteByMemberId(memberId);
    }
}
