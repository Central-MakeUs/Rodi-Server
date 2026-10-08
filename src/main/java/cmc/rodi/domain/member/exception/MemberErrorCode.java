package cmc.rodi.domain.member.exception;

import cmc.rodi.global.common.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ResponseCode {
    WITHDRAWAL_NOT_RECOVERABLE(HttpStatus.NOT_FOUND, "MEMBER_404_1", "복구할 탈퇴 대기 계정이 없습니다."),
    NICKNAME_POOL_EXHAUSTED(
            HttpStatus.INTERNAL_SERVER_ERROR, "MEMBER_500_1", "배정 가능한 닉네임이 소진되었습니다."),
    ALREADY_ONBOARDED(HttpStatus.CONFLICT, "MEMBER_409_2", "이미 온보딩을 완료한 회원입니다."),
    SELF_BLOCK_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "MEMBER_400_1", "자기 자신은 차단할 수 없습니다."),
    // 닉네임(스펙 020) — 사용 가능 여부 확인 API의 reason으로도 같은 코드를 준다
    NICKNAME_INVALID_LENGTH(HttpStatus.BAD_REQUEST, "MEMBER_400_2", "닉네임은 2~10자로 입력해주세요."),
    NICKNAME_INVALID_CHARACTER(
            HttpStatus.BAD_REQUEST,
            "MEMBER_400_3",
            "닉네임은 한글·영문·숫자·_만 사용할 수 있어요. (_는 처음·끝·연속 사용 불가)"),
    NICKNAME_PROHIBITED_WORD(HttpStatus.BAD_REQUEST, "MEMBER_400_4", "사용할 수 없는 단어가 포함되어 있어요."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "MEMBER_409_3", "이미 사용 중인 닉네임이에요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
