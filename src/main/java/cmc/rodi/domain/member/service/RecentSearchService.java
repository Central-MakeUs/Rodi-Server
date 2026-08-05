package cmc.rodi.domain.member.service;

import cmc.rodi.domain.member.dto.RecentSearchRegisterRequest;
import cmc.rodi.domain.member.dto.RecentSearchResponse;
import cmc.rodi.domain.member.entity.RecentSearchType;
import cmc.rodi.domain.member.repository.RecentSearchRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최근 검색어(스펙 008) — 등록·조회·삭제. 연관검색어(스펙 009)에서 선택한 지역/장소를 등록하며, 종류별 upsert로 중복 없이 최신 갱신하고 상한(15) 초과분을
 * 정리한다.
 */
@Service
@RequiredArgsConstructor
public class RecentSearchService {

    /** 회원당 보관 상한. 초과 시 오래된 것부터 제거. */
    static final int MAX_RECENT = 15;

    private final RecentSearchRepository recentSearchRepository;

    /** 선택 항목 등록. REGION=이름, PLACE=placeId 기준으로 중복 없이 최신 갱신 후 상한 초과분 제거. */
    @Transactional
    public void register(Long memberId, RecentSearchRegisterRequest request) {
        if (request.type() == RecentSearchType.REGION) {
            recentSearchRepository.upsertRegion(memberId, request.keyword());
        } else {
            recentSearchRepository.upsertPlace(memberId, request.keyword(), request.placeId());
        }
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
