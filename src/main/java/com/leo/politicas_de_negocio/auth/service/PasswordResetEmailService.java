package com.leo.politicas_de_negocio.auth.service;

import com.leo.politicas_de_negocio.shared.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetEmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public PasswordResetEmailService(JavaMailSender mailSender,
                                     @Value("${app.mail.from:${spring.mail.username:}}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendResetLink(String toEmail, String resetLink) {
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No esta configurado el correo saliente");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Recuperacion de contrasena");
        message.setText(buildMessage(resetLink));
        mailSender.send(message);
    }

    private String buildMessage(String resetLink) {
        return "Recibimos una solicitud para restablecer tu contrasena.\n\n"
                + "Usa este enlace durante los proximos 15 minutos:\n"
                + resetLink + "\n\n"
                + "Si no solicitaste este cambio, puedes ignorar este mensaje.";
    }
}