package cmc.rodi.domain.place.service;

import cmc.rodi.domain.place.repository.BookmarkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 북마크 조회. 다른 도메인(마이페이지)에서 저장 수·저장 목록을 얻는 진입점. */
@Service
@RequiredArgsConstructor
public class BookmarkQueryService {

    private final BookmarkRepository bookmarkRepository;

    /** 회원이 저장한 장소 수. 마이페이지 조회·저장 목록 totalCount에 쓰인다. */
    @Transactional(readOnly = true)
    public long countByMember(Long memberId) {
        return bookmarkRepository.countByMemberId(memberId);
    }
}
