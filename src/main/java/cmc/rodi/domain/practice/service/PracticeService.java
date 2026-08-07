package cmc.rodi.domain.practice.service;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.entity.Place;
import cmc.rodi.domain.place.repository.PlaceRepository;
import cmc.rodi.domain.practice.dto.PracticeRegisterResponse;
import cmc.rodi.domain.practice.entity.MemberPractice;
import cmc.rodi.domain.practice.repository.MemberPracticeRepository;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 연습 목록 담기·제거. 방문 여부 변경은 후속 커밋에서 붙인다. */
@Service
@RequiredArgsConstructor
public class PracticeService {

    private final MemberPracticeRepository memberPracticeRepository;
    private final PlaceRepository placeRepository;
    private final MemberRepository memberRepository;

    /**
     * 연습 목록에 담기. 이미 담긴 장소면 <b>행을 새로 만들지 않고</b> 상태를 예정으로 되돌린다(재도전). 지난 연습 횟수는 그대로 유지된다. 코스·주차장 모두 담을
     * 수 있다.
     */
    @Transactional
    public PracticeRegisterResponse register(Long placeId, Long memberId) {
        MemberPractice practice =
                memberPracticeRepository.findByMemberIdAndPlaceId(memberId, placeId).orElse(null);
        if (practice != null) {
            practice.replan();
            return PracticeRegisterResponse.from(practice);
        }

        Place place =
                placeRepository
                        .findById(placeId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        Member member =
                memberRepository
                        .findById(memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        MemberPractice saved =
                memberPracticeRepository.save(
                        MemberPractice.builder().member(member).place(place).build());
        return PracticeRegisterResponse.from(saved);
    }
}
