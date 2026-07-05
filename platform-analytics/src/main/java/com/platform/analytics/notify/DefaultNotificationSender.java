package com.platform.analytics.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Real notification delivery: Slack/webhook via an HTTP POST of a {@code {"text":...}} payload,
 * email via {@link JavaMailSender}. Everything degrades to a logged warning rather than throwing,
 * so a mis-configured channel never breaks the pipeline. Secrets (webhook URLs, SMTP creds) come
 * from the destination value + Spring mail config — never logged.
 */
@Component
public class DefaultNotificationSender implements NotificationSender {

  private static final Logger log = LoggerFactory.getLogger(DefaultNotificationSender.class);

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper om;
  private final JavaMailSender mail;
  private final String mailFrom;

  public DefaultNotificationSender(
      ObjectMapper om,
      @Autowired(required = false) JavaMailSender mail,
      @Value("${platform.notify.mail-from:noreply@platform.local}") String mailFrom) {
    this.om = om;
    this.mail = mail;
    this.mailFrom = mailFrom;
  }

  @Override
  public void send(String channel, String destination, String subject, String body) {
    if (channel == null) {
      return;
    }
    switch (channel.toUpperCase()) {
      case "SLACK", "WEBHOOK" -> postWebhook(destination, subject, body);
      case "EMAIL" -> sendEmail(destination, subject, body);
      default -> log.warn("[Notify] unknown channel {}", channel);
    }
  }

  private void postWebhook(String url, String subject, String body) {
    try {
      String json = om.writeValueAsString(Map.of("text", subject + "\n" + body));
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(url))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json))
              .build();
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() / 100 != 2) {
        log.warn("[Notify] webhook POST returned {}", resp.statusCode());
      }
    } catch (Exception e) {
      log.warn("[Notify] webhook send failed: {}", e.getMessage());
    }
  }

  private void sendEmail(String to, String subject, String body) {
    if (mail == null) {
      log.warn("[Notify] email requested but no SMTP (JavaMailSender) is configured — skipping");
      return;
    }
    try {
      SimpleMailMessage msg = new SimpleMailMessage();
      msg.setFrom(mailFrom);
      msg.setTo(to);
      msg.setSubject(subject);
      msg.setText(body);
      mail.send(msg);
    } catch (Exception e) {
      log.warn("[Notify] email send failed: {}", e.getMessage());
    }
  }
}
