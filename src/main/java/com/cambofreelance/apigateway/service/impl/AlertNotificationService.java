package com.cambofreelance.apigateway.service.impl;

import com.cambofreelance.apigateway.constants.Constants;
import com.cambofreelance.apigateway.models.AlertHistory;
import com.cambofreelance.apigateway.models.Incident;
import com.cambofreelance.apigateway.models.NotificationChannel;
import com.cambofreelance.apigateway.repositories.NotificationChannelRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Predicate;

@Service
@Slf4j
@RequiredArgsConstructor
public class AlertNotificationService {

    private static final List<String> SEV_ORDER = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final NotificationChannelRepository channelRepository;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    // ── Public dispatch entry-points ───────────────────────────────────────────

    public void notifyOpen(Incident incident) {
        dispatch(incident, AlertHistory.ACTION_OPENED, NotificationChannel::isOnOpen);
    }

    public void notifyAcknowledge(Incident incident) {
        dispatch(incident, AlertHistory.ACTION_ACKNOWLEDGED, NotificationChannel::isOnAcknowledge);
    }

    public void notifyResolve(Incident incident) {
        dispatch(incident, AlertHistory.ACTION_RESOLVED, NotificationChannel::isOnResolve);
    }

    // ── Internal dispatch ──────────────────────────────────────────────────────

    private void dispatch(Incident incident, String action,
                          Predicate<NotificationChannel> triggerFilter) {
        channelRepository.findAllByEnabledTrueAndStatus(Constants.STATUS_ACTIVE)
            .filter(triggerFilter::test)
            .filter(ch -> meetsMinSeverity(ch.getMinSeverity(), incident.getSeverity()))
            .subscribe(
                ch -> sendSafe(ch, incident, action),
                e  -> log.error("Failed to load notification channels: {}", e.getMessage())
            );
    }

    private void sendSafe(NotificationChannel channel, Incident incident, String action) {
        try {
            switch (channel.getType()) {
                case NotificationChannel.TYPE_EMAIL    -> sendEmail(channel, incident, action);
                case NotificationChannel.TYPE_TELEGRAM -> sendTelegram(channel, incident, action);
                case NotificationChannel.TYPE_SLACK    -> sendSlack(channel, incident, action);
                default -> log.warn("Unknown notification type: {}", channel.getType());
            }
        } catch (Exception e) {
            log.error("[{}] Failed to notify channel [{}]: {}", channel.getType(), channel.getName(), e.getMessage());
        }
    }

    // ── Email ──────────────────────────────────────────────────────────────────

    private void sendEmail(NotificationChannel channel, Incident incident, String action) {
        Mono.fromCallable(() -> {
            Map<String, Object> cfg = parseConfig(channel.getConfig());

            Object recipientsObj = cfg.get("recipients");
            if (!(recipientsObj instanceof List<?> recipientList) || recipientList.isEmpty()) {
                log.warn("[EMAIL] Channel [{}] has no recipients configured", channel.getName());
                return null;
            }

            JavaMailSenderImpl sender = buildMailSender(cfg);
            if (sender == null) return null;

            String from    = strVal(cfg, "from", "noreply@cambofreelance.com");
            String subject = "[%s] %s: %s".formatted(incident.getSeverity(), action, incident.getTitle());
            String body    = buildHtmlBody(incident, action);

            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(recipientList.stream().map(Object::toString).toArray(String[]::new));
            helper.setSubject(subject);
            helper.setText(body, true);
            sender.send(msg);

            log.info("[EMAIL] Sent '{}' notification for incident #{} to {} recipient(s)",
                action, incident.getId(), recipientList.size());
            return null;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .subscribe(
            ignored -> {},
            e -> log.error("[EMAIL] Send failed for channel [{}]: {}", channel.getName(), e.getMessage())
        );
    }

    /** Builds a JavaMailSenderImpl from config keys: host, port, username, password, tls. */
    private JavaMailSenderImpl buildMailSender(Map<String, Object> cfg) {
        String host = strVal(cfg, "host", "");
        if (host.isBlank()) {
            log.warn("[EMAIL] SMTP host is not configured in channel config");
            return null;
        }
        int port = intVal(cfg, "port", 587);
        boolean tls = boolVal(cfg, "tls", true);

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(strVal(cfg, "username", ""));
        sender.setPassword(strVal(cfg, "password", ""));
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth",                String.valueOf(!strVal(cfg, "username", "").isBlank()));
        props.put("mail.smtp.starttls.enable",     String.valueOf(tls));
        props.put("mail.smtp.starttls.required",   String.valueOf(tls));
        props.put("mail.smtp.connectiontimeout",   "5000");
        props.put("mail.smtp.timeout",             "5000");
        return sender;
    }

    // ── Telegram ───────────────────────────────────────────────────────────────

    private void sendTelegram(NotificationChannel channel, Incident incident, String action) {
        try {
            Map<String, Object> cfg     = parseConfig(channel.getConfig());
            String botToken = String.valueOf(cfg.getOrDefault("bot_token", ""));
            String chatId   = String.valueOf(cfg.getOrDefault("chat_id", ""));

            if (botToken.isBlank() || chatId.isBlank()) {
                log.warn("Telegram channel [{}] missing bot_token or chat_id", channel.getName());
                return;
            }

            String text = buildTelegramText(incident, action);
            String url  = "https://api.telegram.org/bot" + botToken + "/sendMessage";

            webClientBuilder.build()
                .post().uri(url)
                .bodyValue(Map.of("chat_id", chatId, "text", text, "parse_mode", "HTML"))
                .retrieve()
                .bodyToMono(String.class)
                .subscribe(
                    r  -> log.info("[TELEGRAM] Sent '{}' notification for incident #{}", action, incident.getId()),
                    e  -> log.error("[TELEGRAM] Send failed for channel [{}]: {}", channel.getName(), e.getMessage())
                );
        } catch (Exception e) {
            log.error("[TELEGRAM] Config parse failed for channel [{}]: {}", channel.getName(), e.getMessage());
        }
    }

    // ── Slack ──────────────────────────────────────────────────────────────────

    private void sendSlack(NotificationChannel channel, Incident incident, String action) {
        try {
            Map<String, Object> cfg        = parseConfig(channel.getConfig());
            String webhookUrl = String.valueOf(cfg.getOrDefault("webhook_url", ""));

            if (webhookUrl.isBlank()) {
                log.warn("Slack channel [{}] missing webhook_url", channel.getName());
                return;
            }

            Map<String, Object> payload = buildSlackPayload(incident, action);

            webClientBuilder.build()
                .post().uri(webhookUrl)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .subscribe(
                    r  -> log.info("[SLACK] Sent '{}' notification for incident #{}", action, incident.getId()),
                    e  -> log.error("[SLACK] Send failed for channel [{}]: {}", channel.getName(), e.getMessage())
                );
        } catch (Exception e) {
            log.error("[SLACK] Config parse failed for channel [{}]: {}", channel.getName(), e.getMessage());
        }
    }

    // ── Test dispatch (used by admin test endpoint) ────────────────────────────

    public Mono<String> testChannel(NotificationChannel channel) {
        Incident sample = Incident.builder()
            .id(0L)
            .title("Test Notification")
            .description("This is a test notification from CamboFreelance API Gateway.")
            .severity(Incident.SEV_HIGH)
            .type(Incident.TYPE_ERROR)
            .status(Incident.STATUS_OPEN)
            .build();
        return Mono.fromCallable(() -> {
            sendSafe(channel, sample, AlertHistory.ACTION_OPENED);
            return "Test notification dispatched to channel: " + channel.getName();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ── Message builders ───────────────────────────────────────────────────────

    private String buildHtmlBody(Incident incident, String action) {
        return """
            <html><body style="font-family:Arial,sans-serif;color:#333">
            <h2 style="color:%s">%s — Incident #%d</h2>
            <table style="border-collapse:collapse;width:100%%">
              <tr><td style="padding:4px 8px;font-weight:bold">Title</td><td>%s</td></tr>
              <tr><td style="padding:4px 8px;font-weight:bold">Severity</td><td>%s</td></tr>
              <tr><td style="padding:4px 8px;font-weight:bold">Status</td><td>%s</td></tr>
              <tr><td style="padding:4px 8px;font-weight:bold">Type</td><td>%s</td></tr>
              <tr><td style="padding:4px 8px;font-weight:bold">Affected Service</td><td>%s</td></tr>
              <tr><td style="padding:4px 8px;font-weight:bold">Description</td><td>%s</td></tr>
              <tr><td style="padding:4px 8px;font-weight:bold">Opened At</td><td>%s</td></tr>
            </table>
            </body></html>
            """.formatted(
                severityColor(incident.getSeverity()), action, incident.getId(),
                safe(incident.getTitle()),
                safe(incident.getSeverity()),
                safe(incident.getStatus()),
                safe(incident.getType()),
                safe(incident.getAffectedService()),
                safe(incident.getDescription()),
                incident.getOpenedAt() != null ? incident.getOpenedAt().toString() : "—"
            );
    }

    private String buildTelegramText(Incident incident, String action) {
        return """
            <b>%s</b> — Incident #%d
            <b>Title:</b> %s
            <b>Severity:</b> %s | <b>Status:</b> %s
            <b>Type:</b> %s | <b>Service:</b> %s
            <b>Description:</b> %s
            """.formatted(
                action, incident.getId(),
                safe(incident.getTitle()),
                safe(incident.getSeverity()), safe(incident.getStatus()),
                safe(incident.getType()), safe(incident.getAffectedService()),
                safe(incident.getDescription())
            );
    }

    private Map<String, Object> buildSlackPayload(Incident incident, String action) {
        String color = switch (incident.getSeverity() != null ? incident.getSeverity() : "") {
            case Incident.SEV_CRITICAL -> "#FF0000";
            case Incident.SEV_HIGH     -> "#FF8C00";
            case Incident.SEV_MEDIUM   -> "#FFA500";
            default                    -> "#36A64F";
        };

        return Map.of("attachments", List.of(Map.of(
            "color", color,
            "title", action + " — Incident #" + incident.getId() + ": " + safe(incident.getTitle()),
            "fields", List.of(
                slackField("Severity", safe(incident.getSeverity()), true),
                slackField("Status",   safe(incident.getStatus()),   true),
                slackField("Type",     safe(incident.getType()),     true),
                slackField("Service",  safe(incident.getAffectedService()), true),
                slackField("Description", safe(incident.getDescription()), false)
            ),
            "footer", "CamboFreelance API Gateway",
            "ts", System.currentTimeMillis() / 1000
        )));
    }

    private Map<String, Object> slackField(String title, String value, boolean shortField) {
        return Map.of("title", title, "value", value, "short", shortField);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private boolean meetsMinSeverity(String minSeverity, String incidentSeverity) {
        if (minSeverity == null) return true;
        int minIdx = SEV_ORDER.indexOf(minSeverity.toUpperCase());
        int incIdx = SEV_ORDER.indexOf(incidentSeverity != null ? incidentSeverity.toUpperCase() : "HIGH");
        return incIdx >= minIdx;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String config) throws Exception {
        return objectMapper.readValue(config, new TypeReference<Map<String, Object>>() {});
    }

    private String safe(String s) {
        return s != null ? s : "—";
    }

    private String strVal(Map<String, Object> cfg, String key, String def) {
        Object v = cfg.get(key);
        return (v != null && !v.toString().isBlank()) ? v.toString() : def;
    }

    private int intVal(Map<String, Object> cfg, String key, int def) {
        try { return ((Number) cfg.getOrDefault(key, def)).intValue(); } catch (Exception e) { return def; }
    }

    private boolean boolVal(Map<String, Object> cfg, String key, boolean def) {
        Object v = cfg.get(key);
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(v.toString());
    }

    private String severityColor(String severity) {
        if (severity == null) return "#888";
        return switch (severity) {
            case Incident.SEV_CRITICAL -> "#CC0000";
            case Incident.SEV_HIGH     -> "#E65C00";
            case Incident.SEV_MEDIUM   -> "#CC8800";
            default                    -> "#2E7D32";
        };
    }
}
