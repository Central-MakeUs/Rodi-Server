package cmc.rodi.domain.member.service;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 정렬 기준(스펙 007·019) — 회원의 {@code sort_tags} 조회·저장. 저장은 클라가 카테고리를 풀어 보낸 연습유형 리스트를 전체 교체한다. 조회는
 * place 도메인의 목록·검색 정렬이 인증된 요청에서 쓴다(비로그인은 이 값을 읽지 않고 거리순).
 */
@Service
@RequiredArgsConstructor
public class MemberSortService {

    private final MemberRepository memberRepository;

    /** 회원의 저장된 정렬 기준. 없거나 미설정이면 빈 목록(정렬 없음). 목록·검색 정렬에서 인증된 요청에만 호출된다. */
    @Transactional(readOnly = true)
    public List<PracticeType> getSortTags(Long memberId) {
        return memberRepository.findById(memberId).map(Member::getSortTags).orElse(List.of());
    }

    /** 정렬 기준 전체 교체 저장. 빈 목록이면 해제(null). 없는 회원이면 404. */
    @Transactional
    public void updateSortTags(Long memberId, List<PracticeType> sortTags) {
        Member member =
                memberRepository
                        .findById(memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        member.updateSortTags(sortTags);
    }
}
