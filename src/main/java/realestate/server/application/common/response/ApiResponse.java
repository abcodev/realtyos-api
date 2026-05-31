package realestate.server.application.common.response;

import lombok.Getter;

@Getter
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final String message;
    private final String code;

    private ApiResponse(boolean success, T data, String message, String code) {
        this.success = success;
        this.data = data;
        this.message = message;
        this.code = code;
    }

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static ApiResponse<Void> empty() {
        return new ApiResponse<>(true, null, null, null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return of(data);
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(false, null, message, code);
    }
}