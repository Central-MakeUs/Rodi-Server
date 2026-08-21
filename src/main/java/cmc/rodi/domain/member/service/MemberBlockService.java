package cmc.rodi.domain.member.service;

import cmc.rodi.domain.member.dto.BlockedMemberItem;
import cmc.rodi.domain.member.entity.MemberBlock;
import cmc.rodi.domain.member.exception.MemberErrorCode;
import cmc.rodi.domain.member.repository.MemberBlockRepository;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.global.common.pagination.CursorCodec;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 차단·해제(단방향, 멱등). 차단하면 차단한 쪽의 후기 목록에서만 상대 후기가 빠지고, 후기 요약 수치와 상대방 화면에는 영향이 없다. */
@Service
@RequiredArgsConstructor
public class MemberBlockService {

    /** 첫 페이지용 sentinel 커서(모든 차단보다 미래). null 커서 대신 써서 바인드 타입 문제를 피한다. */
    private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

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

    /** 내가 차단한 회원 목록(차단한 시각 최신순 커서). 탈퇴한 회원의 차단 행도 그대로 남아 닉네임만 null로 나온다. */
    @Transactional(readOnly = true)
    public CursorPage<BlockedMemberItem> getMyBlocks(Long blockerId, int size, String rawCursor) {
        boolean firstPage = rawCursor == null;
        CursorCodec.Cursor cursor = firstPage ? null : CursorCodec.decode(rawCursor);

        List<MemberBlock> rows =
                memberBlockRepository.findPage(
                        blockerId,
                        cursor == null ? FAR_FUTURE : parseCursorTime(cursor.sortValue()),
                        cursor == null ? Long.MAX_VALUE : cursor.id(),
                        PageRequest.of(0, size + 1));

        boolean hasNext = rows.size() > size;
        List<MemberBlock> page = hasNext ? rows.subList(0, size) : rows;
        List<BlockedMemberItem> items = page.stream().map(BlockedMemberItem::from).toList();
        String nextCursor = hasNext ? encodeCursor(page.get(page.size() - 1)) : null;

        if (!firstPage) {
            return CursorPage.next(items, hasNext, nextCursor);
        }
        return CursorPage.first(
                items, hasNext, nextCursor, memberBlockRepository.countByBlockerId(blockerId));
    }

    private static String encodeCursor(MemberBlock last) {
        return CursorCodec.encode(last.getCreatedAt().toString(), last.getId());
    }

    private static LocalDateTime parseCursorTime(String sortValue) {
        try {
            return LocalDateTime.parse(sortValue);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, e);
        }
    }

    /** 차단 해제(멱등). 차단 상태가 아니어도 성공으로 본다. */
    @Transactional
    public void unblock(Long blockerId, Long blockedId) {
        memberBlockRepository.deleteByBlockerIdAndBlockedId(blockerId, blockedId);
    }
}
