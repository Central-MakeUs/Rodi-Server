package cmc.rodi.domain.place.service;

import cmc.rodi.domain.place.dto.PlaceCoordinateResponse;
import cmc.rodi.domain.place.repository.PlaceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 장소 조회. 현재는 지도 마커용 전체 좌표 목록을 제공한다. */
@Service
@RequiredArgsConstructor
public class PlaceQueryService {

    private final PlaceRepository placeRepository;

    /** 전체 place의 간단 좌표(마커용). 필터 없이 모두 반환한다. */
    @Transactional(readOnly = true)
    public List<PlaceCoordinateResponse> getAllCoordinates() {
        return placeRepository.findAll().stream().map(PlaceCoordinateResponse::from).toList();
    }
}
