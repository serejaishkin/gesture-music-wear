package com.example.gesturemusicwear;

import android.service.notification.NotificationListenerService;

/**
 * Empty on purpose. Once the user grants notification access to this component,
 * MediaSessionManager.getActiveSessions(componentName) stops throwing SecurityException
 * and PlayerControl can talk to the real player (Yandex Music etc.).
 */
public class MediaListenerService extends NotificationListenerService {
}
