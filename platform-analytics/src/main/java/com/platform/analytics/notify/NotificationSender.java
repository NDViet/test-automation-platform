package com.platform.analytics.notify;

/**
 * Sends a notification to a channel/destination. Implementations degrade gracefully when
 * unconfigured.
 */
public interface NotificationSender {

  /**
   * @param channel SLACK | WEBHOOK | EMAIL
   * @param destination webhook URL or email address
   * @param subject short summary (used as email subject / message prefix)
   * @param body the message text
   */
  void send(String channel, String destination, String subject, String body);
}
