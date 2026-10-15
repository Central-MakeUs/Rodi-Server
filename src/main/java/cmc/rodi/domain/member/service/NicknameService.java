package cmc.rodi.domain.member.service;

import cmc.rodi.domain.member.dto.NicknameAvailabilityResponse;
import cmc.rodi.domain.member.exception.MemberErrorCode;
import cmc.rodi.domain.member.policy.NicknamePolicy;
import cmc.rodi.domain.member.policy.NicknameViolation;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.global.exception.BusinessException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 닉네임 판정(스펙 020). 형식·금칙어({@link NicknamePolicy}) 뒤에 중복을 본다. 사용 가능 여부 확인과 닉네임 변경이 같은 판정과 사유 코드를 쓰도록
 * 여기로 모은다.
 *
 * <p>중복은 영문 대소문자를 무시하고 본인은 제외한다 — 자기 닉네임을 다시 내거나 대소문자만 바꾸는 건 중복이 아니다.
 */
@Service
@RequiredArgsConstructor
public class NicknameService {

    private final NicknamePolicy nicknamePolicy;
    private final MemberRepository memberRepository;

    /** 저장하지 않고 판정만 한다. 규칙 위반도 사유와 함께 정상 응답으로 돌려준다. */
    @Transactional(readOnly = true)
    public NicknameAvailabilityResponse checkAvailability(Long memberId, String rawNickname) {
        String nickname = nicknamePolicy.normalize(rawNickname);
        return findViolation(memberId, nickname)
                .map(reason -> NicknameAvailabilityResponse.unavailable(nickname, reason))
                .orElseGet(() -> NicknameAvailabilityResponse.available(nickname));
    }

    /** 변경용 판정. 통과하면 정규화된 닉네임을, 아니면 사유 코드로 예외를 던진다. */
    @Transactional(readOnly = true)
    public String validate(Long memberId, String rawNickname) {
        String nickname = nicknamePolicy.normalize(rawNickname);
        findViolation(memberId, nickname)
                .ifPresent(
                        reason -> {
                            throw new BusinessException(reason);
                        });
        return nickname;
    }

    private Optional<MemberErrorCode> findViolation(Long memberId, String nickname) {
        Optional<NicknameViolation> violation = nicknamePolicy.check(nickname);
        if (violation.isPresent()) {
            return violation.map(NicknameService::toErrorCode);
        }
        if (memberRepository.existsByNicknameIgnoreCaseExcluding(nickname, memberId)) {
            return Optional.of(MemberErrorCode.NICKNAME_DUPLICATED);
        }
        return Optional.empty();
    }

    private static MemberErrorCode toErrorCode(NicknameViolation violation) {
        return switch (violation) {
            case INVALID_LENGTH -> MemberErrorCode.NICKNAME_INVALID_LENGTH;
            case INVALID_CHARACTER -> MemberErrorCode.NICKNAME_INVALID_CHARACTER;
            case PROHIBITED_WORD -> MemberErrorCode.NICKNAME_PROHIBITED_WORD;
        };
    }
}
