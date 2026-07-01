package io.github.hotreload.demo.config.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 * <p>
 * Demo 直接返回异常文本，便于学习和调试热重载失败原因；生产项目可以替换为统一响应体。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理热重载业务异常。
     *
     * @param e 热重载业务异常
     * @return HTTP 400 和异常消息
     */
    @ExceptionHandler(HotReloadException.class)
    public ResponseEntity<String> handleHotReloadException(HotReloadException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    /**
     * 处理未捕获异常。
     *
     * @param e 未捕获异常
     * @return HTTP 500 和异常消息
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        log.error("请求处理失败", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
    }
}
