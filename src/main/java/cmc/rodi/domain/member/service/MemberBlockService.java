package cmc.rodi.domain.member.service;

import cmc.rodi.domain.member.exception.MemberErrorCode;
import cmc.rodi.domain.member.repository.MemberBlockRepository;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 차단·해제(단방향, 멱등). 차단하면 차단한 쪽의 후기 목록에서만 상대 후기가 빠지고, 후기 요약 수치와 상대방 화면에는 영향이 없다. */
@Service
@RequiredArgsConstructor
public class MemberBlockService {

    private final MemberBlockRepository memberBlockRepository;
    private final MemberRepository memberRepository;

    /** 차단(멱등). 자기 자신은 차단할 수 없고, 없는 회원이면 404. */
    @Transactional
    public void block(Long blockerId, Long blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new BusinessException(MemberErrorCode.SELF_BLOCK_NOT_ALLOWED);
        }
        if (!memberRepository.existsById(blockedId)) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }
        // ON CONFLICT DO NOTHING — 재차단도 멱등
        memberBlockRepository.saveIfAbsent(blockerId, blockedId);
    }

    /** 차단 해제(멱등). 차단 상태가 아니어도 성공으로 본다. */
    @Transactional
    public void unblock(Long blockerId, Long blockedId) {
        memberBlockRepository.deleteByBlockerIdAndBlockedId(blockerId, blockedId);
    }
}
