package realestate.server.application.user.interfaces;

import realestate.server.application.common.response.ApiResponse;
import realestate.server.application.common.web.auth.CurrentUser;
import realestate.server.application.user.application.UserSettingService;
import realestate.server.application.user.interfaces.dto.LocationConsentSettingReqDto;
import realestate.server.application.user.interfaces.dto.PushNotificationSettingReqDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user/setting")
@Tag(name = "회원 설정", description = "회원 설정 관련 API")
public class UserSettingController {

    private final UserSettingService userSettingService;

    @GetMapping("/notification")
    @Operation(summary = "알림 설정 조회", description = "유저의 현재 푸시 수신 여부와 브리핑 알림 시간을 조회합니다.")
    public ApiResponse<Map<String, Object>> getNotificationSettings(
        @CurrentUser Long userId) {
        var settings = userSettingService.getNotificationSettings(userId);
        return ApiResponse.of(settings);
    }

    @PatchMapping("/push-notification")
    @Operation(summary = "푸시 알림 수신 설정", description = "유저의 푸시 알림 수신 여부를 설정합니다.")
    public ApiResponse<Void> updatePushNotification(
            @CurrentUser Long userId,
            @RequestBody @Valid PushNotificationSettingReqDto dto) {
        userSettingService.updatePushNotification(userId, dto.getPushEnabled());
        return ApiResponse.empty();
    }

    @PatchMapping("/location-consent")
    @Operation(summary = "위치 이용 동의 설정", description = "유저의 위치 이용 동의 여부를 설정합니다.")
    public ApiResponse<Void> updateLocationConsent(
            @CurrentUser Long userId,
            @RequestBody @Valid LocationConsentSettingReqDto dto) {
        userSettingService.updateLocationConsent(userId, dto.getLocationConsentEnabled());
        return ApiResponse.empty();
    }

}
