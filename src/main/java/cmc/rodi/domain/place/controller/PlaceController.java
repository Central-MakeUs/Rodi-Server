package cmc.rodi.domain.place.controller;

import cmc.rodi.domain.place.dto.PlaceCoordinateResponse;
import cmc.rodi.domain.place.service.PlaceQueryService;
import cmc.rodi.global.common.response.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 장소 API. 문서 스펙은 {@link PlaceControllerDocs}. */
@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
public class PlaceController implements PlaceControllerDocs {

    private final PlaceQueryService placeQueryService;

    @Override
    @GetMapping("/coordinates")
    public ApiResponse<List<PlaceCoordinateResponse>> getCoordinates() {
        return ApiResponse.success(placeQueryService.getAllCoordinates());
    }
}
