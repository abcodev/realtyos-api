package realestate.server.application.user.interfaces;

import realestate.server.application.common.response.ApiResponse;
import realestate.server.application.common.web.auth.CurrentUser;
import realestate.server.application.user.application.UserService;
import realestate.server.application.user.interfaces.dto.UserSyncTypeReqDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/user")
@Tag(name = "회원", description = "회원 관련 API")
public class UserController {

    private final UserService userService;

    @PostMapping("/sync-type")
    @Operation(summary = "유저 타입 동기화", description = "비회원으로 광고 제거 후 회원가입(로그인) 시 유저 타입을 동기화합니다.")
    public ApiResponse<Void> syncUserType(
            @CurrentUser Long userId,
            @RequestBody UserSyncTypeReqDto dto) {
        userService.syncUserType(userId, dto.userType());
        return ApiResponse.empty();
    }

}
