package com.test.common;

import com.test.util.ResultVO;
import com.test.util.ResultVOUtil;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理普通运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResultVO handleRuntimeException(RuntimeException e) {
        return ResultVOUtil.fail(e.getMessage());
    }

    /**
     * 处理参数校验异常，后面如果你加 @Valid 会用到
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResultVO handleValidException(MethodArgumentNotValidException e) {
        String msg = "参数校验失败";

        if (e.getBindingResult().getFieldError() != null) {
            msg = e.getBindingResult().getFieldError().getDefaultMessage();
        }

        return ResultVOUtil.fail(msg);
    }

    /**
     * 兜底异常
     */
    @ExceptionHandler(Exception.class)
    public ResultVO handleException(Exception e) {
        e.printStackTrace();
        return ResultVOUtil.fail("系统异常，请稍后再试");
    }
}