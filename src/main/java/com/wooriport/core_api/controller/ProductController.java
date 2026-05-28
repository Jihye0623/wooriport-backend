package com.wooriport.core_api.controller;

import com.wooriport.core_api.base.dto.product.ProductListResponseDto;
import com.wooriport.core_api.base.dto.response.ResponseDTO;
import com.wooriport.core_api.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Products", description = "투자 상품 카탈로그")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(
            summary = "전체 상품 조회",
            description = "soft delete 되지 않은 모든 상품을 productType / institution / name 순으로 반환합니다."
    )
    @GetMapping
    public ResponseEntity<ResponseDTO<ProductListResponseDto>> getProducts() {
        return ResponseEntity.ok(ResponseDTO.success(200, "상품 조회 성공",
                productService.getAllProducts()));
    }
}
