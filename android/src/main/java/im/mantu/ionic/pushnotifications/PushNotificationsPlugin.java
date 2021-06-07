package im.mantu.ionic.pushnotifications;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import androidx.core.content.ContextCompat;

import com.getcapacitor.Bridge;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginHandle;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.RemoteMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

@CapacitorPlugin(name = "PushNotifications", permissions = @Permission(strings = {}, alias = "receive"))
public class PushNotificationsPlugin extends Plugin {

    public static Bridge staticBridge = null;
    public static RemoteMessage lastMessage = null;
    public NotificationManager notificationManager;
    public MessagingService firebaseMessagingService;
    private NotificationChannelManager notificationChannelManager;

    private static final String EVENT_TOKEN_CHANGE = "registration";
    private static final String EVENT_TOKEN_ERROR = "registrationError";

    public void load() {
        notificationManager = (NotificationManager) getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
        if (!BuildConfig.FLAVOR.equals("internalPush"))
            firebaseMessagingService = new MessagingService();

        staticBridge = this.bridge;

        if (lastMessage != null) {
            fireNotification(lastMessage);
            lastMessage = null;
        }

        notificationChannelManager = new NotificationChannelManager(getActivity(), notificationManager);

        Intent intent = staticBridge.getActivity().getIntent();
        if (intent != null) {
            handleOnNewIntent(intent);
        }
    }

    @Override
    protected void handleOnNewIntent(Intent data) {
        super.handleOnNewIntent(data);
        Bundle bundle = data.getExtras();
        if (bundle != null && bundle.containsKey("google.message_id")) {
            JSObject notificationJson = new JSObject();
            JSObject dataObject = new JSObject();
            for (String key : bundle.keySet()) {
                if (key.equals("google.message_id")) {
                    notificationJson.put("id", bundle.get(key));
                } else {
                    Object value = bundle.get(key);
                    String valueStr = (value != null) ? value.toString() : null;
                    dataObject.put(key, valueStr);
                }
            }
            notificationJson.put("data", dataObject);
            JSObject actionJson = new JSObject();
            actionJson.put("actionId", "tap");
            actionJson.put("notification", notificationJson);
            notifyListeners("pushNotificationActionPerformed", actionJson, true);
        } else if (data.getAction() != null && (data.getAction().equals("answer") || data.getAction().equals("decline"))) {
            JSObject dataJson = new JSObject();
            PushNotificationsPlugin pushPlugin = PushNotificationsPlugin.getPushNotificationsInstance();
            String sender = data.getStringExtra(CallingNotificationService.DATA_SENDER);
            String companyId = data.getStringExtra(CallingNotificationService.DATA_COMPANY_ID);
            String branchId = data.getStringExtra(CallingNotificationService.DATA_BRANCH_ID);
            String jid = data.getStringExtra(CallingNotificationService.DATA_JID);
            String hasVideo = data.getStringExtra(CallingNotificationService.DATA_HAS_VIDEO);
            String callUUID = data.getStringExtra(CallingNotificationService.DATA_CALL_UUID);
            JSObject notificationJson = new JSObject()
                    .put("sender", sender)
                    .put("companyId", companyId)
                    .put("branchId", branchId)
                    .put("jid", jid)
                    .put("hasVideo", hasVideo)
                    .put("action", data.getAction())
                    .put("callUUID", callUUID);
            dataJson.put("data", notificationJson);
            if (pushPlugin != null) {
                pushPlugin.notifyIonic(dataJson);
            }
        }
        this.getActivity()
                .getApplicationContext()
                .stopService(new Intent(this.getActivity().getApplicationContext(), CallingNotificationService.class));
    }

    @PluginMethod
    public void register(PluginCall call) {
        if (!BuildConfig.FLAVOR.equals("internalPush")) {
            FirebaseMessaging.getInstance().setAutoInitEnabled(true);
            FirebaseInstanceId
                    .getInstance()
                    .getInstanceId()
                    .addOnSuccessListener(
                            getActivity(),
                            instanceIdResult -> sendToken(instanceIdResult.getToken())
                    );
            FirebaseInstanceId
                    .getInstance()
                    .getInstanceId()
                    .addOnFailureListener(
                            e -> sendError(e.getLocalizedMessage())
                    );
        }
        call.resolve();
    }

    @PluginMethod
    public void getDeliveredNotifications(PluginCall call) {
        JSArray notifications = new JSArray();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StatusBarNotification[] activeNotifications = notificationManager.getActiveNotifications();

            for (StatusBarNotification notif : activeNotifications) {
                JSObject jsNotif = new JSObject();

                jsNotif.put("id", notif.getId());

                Notification notification = notif.getNotification();
                if (notification != null) {
                    jsNotif.put("title", notification.extras.getCharSequence(Notification.EXTRA_TITLE));
                    jsNotif.put("body", notification.extras.getCharSequence(Notification.EXTRA_TEXT));
                    jsNotif.put("group", notification.getGroup());
                    jsNotif.put("groupSummary", 0 != (notification.flags & Notification.FLAG_GROUP_SUMMARY));

                    JSObject extras = new JSObject();

                    for (String key : notification.extras.keySet()) {
                        extras.put(key, notification.extras.get(key));
                    }

                    jsNotif.put("data", extras);
                }

                notifications.put(jsNotif);
            }
        }

        JSObject result = new JSObject();
        result.put("notifications", notifications);
        call.resolve(result);
    }

    @PluginMethod
    public void removeDeliveredNotifications(PluginCall call) {
        JSArray notifications = call.getArray("notifications");

        List<Integer> ids = new ArrayList<>();
        try {
            for (Object o : notifications.toList()) {
                if (o instanceof JSONObject) {
                    JSObject notif = JSObject.fromJSONObject((JSONObject) o);
                    Integer id = notif.getInteger("id");
                    ids.add(id);
                } else {
                    call.reject("Expected notifications to be a list of notification objects");
                }
            }
        } catch (JSONException e) {
            call.reject(e.getMessage());
        }

        for (int id : ids) {
            notificationManager.cancel(id);
        }

        call.resolve();
    }

    @PluginMethod
    public void removeAllDeliveredNotifications(PluginCall call) {
        notificationManager.cancelAll();
        call.resolve();
    }

    @PluginMethod
    public void createChannel(PluginCall call) {
        notificationChannelManager.createChannel(call);
    }

    @PluginMethod
    public void deleteChannel(PluginCall call) {
        notificationChannelManager.deleteChannel(call);
    }

    @PluginMethod
    public void listChannels(PluginCall call) {
        notificationChannelManager.listChannels(call);
    }

    public void sendToken(String token) {
        JSObject data = new JSObject();
        data.put("token", token);
        data.put("tokenType", 1);
        notifyListeners(EVENT_TOKEN_CHANGE, data, true);
    }

    public void sendError(String error) {
        JSObject data = new JSObject();
        data.put("error", error);
        notifyListeners(EVENT_TOKEN_ERROR, data, true);
    }

    public static void onNewToken(String newToken) {
        PushNotificationsPlugin pushPlugin = PushNotificationsPlugin.getPushNotificationsInstance();
        if (pushPlugin != null) {
            pushPlugin.sendToken(newToken);
        }
    }

    public static void startCallNotificationService(Context context, RemoteMessage remoteMessage) {
        Intent service = new Intent(context, CallingNotificationService.class);
        Map<String, String> data = remoteMessage.getData();

        String companyId = data.get("companyId");
        String sender = data.get("sender");
        String callUUID = data.get("callUUID");
        String branchId = data.get("branchId");
        String jid = data.get("jid");
        String hasVideo = data.get("hasVideo");
        String action = data.get("action");
        int id = Integer.parseInt(data.get("jid").replace("+", "").substring(4, 9));

        service.setAction(CallingNotificationService.ACTION_CALL);

        service.putExtra(CallingNotificationService.DATA_SENDER, sender);
        service.putExtra(CallingNotificationService.DATA_HAS_VIDEO, hasVideo);
        service.putExtra(CallingNotificationService.DATA_COMPANY_ID, companyId);
        service.putExtra(CallingNotificationService.DATA_CALL_UUID, callUUID);
        service.putExtra(CallingNotificationService.DATA_BRANCH_ID, branchId);
        service.putExtra(CallingNotificationService.DATA_JID, jid);
        service.putExtra(CallingNotificationService.DATA_ID, id);
        service.putExtra(CallingNotificationService.DATA_ACTION, action);

        if (staticBridge == null || !staticBridge.getApp().isActive()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(service);
            } else {
                context.startService(service);
            }
        } else {
            JSObject dataJson = new JSObject();
            JSObject notificationJson = new JSObject()
                    .put("sender", sender)
                    .put("companyId", companyId)
                    .put("branchId", branchId)
                    .put("jid", jid)
                    .put("hasVideo", hasVideo)
                    .put("action", action)
                    .put("callUUID", callUUID);
            dataJson.put("data", notificationJson);
            PushNotificationsPlugin pushPlugin = PushNotificationsPlugin.getPushNotificationsInstance();
            if (pushPlugin != null) {
                pushPlugin.notifyIonic(dataJson);
            }
        }
    }

    public static void sendRemoteMessage(RemoteMessage remoteMessage) {
        PushNotificationsPlugin pushPlugin = PushNotificationsPlugin.getPushNotificationsInstance();
        if (pushPlugin != null) {
            pushPlugin.fireNotification(remoteMessage);
        } else {
            lastMessage = remoteMessage;
        }
    }

    public void fireNotification(RemoteMessage remoteMessage) {
        JSObject remoteMessageData = new JSObject();

        JSObject data = new JSObject();
        remoteMessageData.put("id", remoteMessage.getMessageId());
        for (String key : remoteMessage.getData().keySet()) {
            Object value = remoteMessage.getData().get(key);
            data.put(key, value);
        }
        remoteMessageData.put("data", data);

        RemoteMessage.Notification notification = remoteMessage.getNotification();
        if (notification != null) {
            remoteMessageData.put("title", notification.getTitle());
            remoteMessageData.put("body", notification.getBody());
            remoteMessageData.put("click_action", notification.getClickAction());

            Uri link = notification.getLink();
            if (link != null) {
                remoteMessageData.put("link", link.toString());
            }
        }

        notifyListeners("pushNotificationReceived", remoteMessageData, true);
    }

    public static PushNotificationsPlugin getPushNotificationsInstance() {
        if (staticBridge != null && staticBridge.getWebView() != null) {
            PluginHandle handle = staticBridge.getPlugin("PushNotifications");
            if (handle == null) {
                return null;
            }
            return (PushNotificationsPlugin) handle.getInstance();
        }
        return null;
    }

    public void notifyIonic(JSObject actionJson) {
        notifyListeners("pushNotificationReceived", actionJson, true);
    }

    public void notifyIonicPerformed(JSObject actionJson) {
        notifyListeners("pushNotificationActionPerformed", actionJson, true);
    }
}
