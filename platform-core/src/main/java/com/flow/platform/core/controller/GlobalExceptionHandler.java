/*
 * Copyright 2017 flow.ci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.flow.platform.core.controller;

import com.flow.platform.core.exception.FlowException;
import com.flow.platform.core.response.ResponseError;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * @author yang
 */
@Log4j2
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FlowException.class)
    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    @ResponseBody
    public ResponseError handleFlowException(HttpServletRequest request, FlowException e) {
        return new ResponseError(e.getMessage());
    }

    @ExceptionHandler(Throwable.class)
    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    @ResponseBody
    public ResponseError handleFatalException(HttpServletRequest request, Throwable e) {
        log.error(e);
        return new ResponseError(e.getMessage());
    }
}
