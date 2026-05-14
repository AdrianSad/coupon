package com.example.coupons.infrastructure.web;

import com.example.coupons.application.CreateCouponUseCase;
import com.example.coupons.application.RedeemCouponUseCase;
import com.example.coupons.application.command.CreateCouponCommand;
import com.example.coupons.application.command.RedeemCouponCommand;
import com.example.coupons.domain.model.Coupon;
import com.example.coupons.infrastructure.web.dto.CouponResponse;
import com.example.coupons.infrastructure.web.dto.CreateCouponRequest;
import com.example.coupons.infrastructure.web.dto.RedeemCouponRequest;
import com.example.coupons.infrastructure.web.dto.RedemptionResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/coupons")
public class CouponController {

    private final CreateCouponUseCase createCoupon;
    private final RedeemCouponUseCase redeemCoupon;
    private final ClientIpResolver ipResolver;

    public CouponController(CreateCouponUseCase createCoupon,
                            RedeemCouponUseCase redeemCoupon,
                            ClientIpResolver ipResolver) {
        this.createCoupon = createCoupon;
        this.redeemCoupon = redeemCoupon;
        this.ipResolver = ipResolver;
    }

    @PostMapping
    public ResponseEntity<CouponResponse> create(@RequestBody @Valid CreateCouponRequest request) {
        Coupon created = createCoupon.create(new CreateCouponCommand(request.code(), request.maxUses(), request.country()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{code}")
                .buildAndExpand(created.code().normalized())
                .toUri();
        return ResponseEntity.created(location).body(CouponResponse.from(created));
    }

    @PostMapping("/{code}/redeem")
    public ResponseEntity<RedemptionResponse> redeem(@PathVariable String code,
                                                     @RequestBody @Valid RedeemCouponRequest request,
                                                     HttpServletRequest httpRequest) {
        String clientIp = ipResolver.resolve(httpRequest);
        var result = redeemCoupon.redeem(new RedeemCouponCommand(code, request.userId(), clientIp));
        return ResponseEntity.status(HttpStatus.OK).body(RedemptionResponse.from(result));
    }
}
