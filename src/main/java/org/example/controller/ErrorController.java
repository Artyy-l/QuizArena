package org.example.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class ErrorController implements org.springframework.boot.web.servlet.error.ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        
        if (status != null) {
            Integer statusCode;
            try {
                statusCode = Integer.valueOf(status.toString());
            } catch (NumberFormatException e) {
                model.addAttribute("errorMessage", "Неизвестная ошибка");
                model.addAttribute("errorCode", "Unknown");
                return "error";
            }
            
            if (statusCode == 404) {
                model.addAttribute("errorMessage", "Страница не найдена");
                model.addAttribute("errorCode", "404");
            } else if (statusCode == 500) {
                model.addAttribute("errorMessage", "Внутренняя ошибка сервера");
                model.addAttribute("errorCode", "500");
            } else {
                model.addAttribute("errorMessage", "Произошла ошибка");
                model.addAttribute("errorCode", statusCode.toString());
            }
        } else {
            model.addAttribute("errorMessage", "Неизвестная ошибка");
            model.addAttribute("errorCode", "Unknown");
        }
        
        return "error";
    }
}
