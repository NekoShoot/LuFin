package com.lufin.server.auth.controller;

import static com.lufin.server.common.constants.ErrorCode.*;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lufin.server.account.service.AccountService;
import com.lufin.server.auth.dto.LoginRequest;
import com.lufin.server.auth.dto.LoginWithAssetResponse;
import com.lufin.server.auth.dto.TokenResponse;
import com.lufin.server.auth.service.LoginService;
import com.lufin.server.auth.service.RefreshTokenService;
import com.lufin.server.classroom.dto.ClassCodeRequest;
import com.lufin.server.classroom.dto.LoginWithClassResponse;
import com.lufin.server.classroom.service.ClassroomCommandService;
import com.lufin.server.common.dto.ApiResponse;
import com.lufin.server.common.exception.BusinessException;
import com.lufin.server.dashboard.dto.BalanceDto;
import com.lufin.server.member.domain.Member;
import com.lufin.server.member.support.UserContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/lufin/auth")
public class AuthController {

	private final LoginService loginService;
	private final ClassroomCommandService classroomService;
	private final RefreshTokenService refreshTokenService;
	private final AccountService accountService;

	@PostMapping("/login")
	public ResponseEntity<ApiResponse<LoginWithAssetResponse>> login(@Valid @RequestBody LoginRequest request) {

		LoginWithAssetResponse response = loginService.login(request.email(), request.password());

		HttpHeaders headers = new HttpHeaders();
		headers.add("Cache-Control", "no-store");
		headers.add("Pragma", "no-cache");

		return ResponseEntity.ok().headers(headers).body(ApiResponse.success(response));
	}

	// [학생] 클래스 코드로 클래스 입장
	@PostMapping("/class")
	public ResponseEntity<ApiResponse<LoginWithClassResponse>> classLogin(
		@Valid @RequestBody ClassCodeRequest request) {

		Member currentMember = UserContext.get();
		LoginWithClassResponse response = classroomService.enrollClass(currentMember, request);

		HttpHeaders headers = new HttpHeaders();
		headers.add("Cache-Control", "no-store");
		headers.add("Pragma", "no-cache");

		return ResponseEntity.ok().headers(headers).body(ApiResponse.success(response));
	}

	@PostMapping("/refresh-token")
	public ResponseEntity<ApiResponse<TokenResponse>> refreshAccessToken(
		@RequestHeader("Authorization") String bearerToken) {

		if (!StringUtils.hasText(bearerToken) || !bearerToken.startsWith("Bearer ")) {
			return ResponseEntity.badRequest().body(ApiResponse.failure(INVALID_AUTH_HEADER));
		}

		try {
			TokenResponse response = refreshTokenService.reissueAccessToken(bearerToken);

			HttpHeaders headers = new HttpHeaders();
			headers.add("Cache-Control", "no-store");
			headers.add("Pragma", "no-cache");

			return ResponseEntity.ok().headers(headers).body(ApiResponse.success(response));
		} catch (BusinessException e) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(ApiResponse.failure(INVALID_TOKEN));
		}
	}

	@GetMapping("/my")
	public ResponseEntity<ApiResponse<BalanceDto.balanceDto>> getMyBalance(HttpServletRequest request
	) {
		Integer classId = (Integer)request.getAttribute("classId");
		Member member = UserContext.get();

		int balance = accountService.getCashBalance(member.getId(), classId != null ? classId : 0);
		return ResponseEntity.ok(ApiResponse.success(new BalanceDto.balanceDto(balance)));
	}
}
