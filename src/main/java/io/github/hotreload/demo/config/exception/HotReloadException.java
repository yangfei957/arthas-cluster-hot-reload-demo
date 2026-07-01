package io.github.hotreload.demo.config.exception;

/**
 * 热重载业务异常。
 * <p>
 * 用于表达参数校验、任务状态、节点状态等可直接返回给页面的业务错误。
 */
public class HotReloadException extends RuntimeException {

    private static final long serialVersionUID = 6249180737314784994L;

    /**
     * 使用错误消息构造异常。
     *
     * @param message 错误消息
     */
    public HotReloadException(String message) {
        super(message);
    }

    /**
     * 使用错误消息和原始异常构造异常。
     *
     * @param message 错误消息
     * @param cause   原始异常
     */
    public HotReloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
