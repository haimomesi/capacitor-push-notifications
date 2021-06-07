package im.mantu.ionic.localpush;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

import im.mantu.ionic.pushnotifications.PushNotificationsPlugin;
import im.mantu.ionic.pushnotifications.R;

public class LocalPushNotificationService extends Service {

    public static final String PUSH_NOTIFICATION_ACTION_PERFORMED = "pushNotificationActionPerformed";
    private final String ACTIVITY_TO_START = "im.mantu.ionic.MainActivity";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(1, getNotification());
        }
        //
        if (intent.hasExtra("pushNotification")) {
            String pushNotification = intent.getStringExtra("pushNotification");
            try {
                JSONObject jsonObject = new JSONObject(pushNotification);
                if (jsonObject.has("data")) {
                    JSONObject data = (JSONObject) jsonObject.get("data");
                    String type = data.get("message_type").toString();
                    switch (type) {
                        case "Invitation":
                            handleInvite(data);
                            break;
                        case "P2P":
                            handleP2P(data);
                            break;
                        case "Voip":
                            handleVoip(data);
                            break;
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        //
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
        }
        return START_NOT_STICKY;
    }

    private void handleVoip(JSONObject jsonObject) {
        try {
            Map<String, String> data = LocalHelper.jsonToMap(jsonObject);
            RemoteMessage rm = new RemoteMessage.Builder("internal").clearData().setData(data).build();
            PushNotificationsPlugin.startCallNotificationService(this.getApplicationContext(), rm);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void handleP2P(JSONObject jsonObject) {
        String sender = "", companyId = "", branchId = "", jid = "";
        try {
            sender = jsonObject.getString("sender");
            companyId = jsonObject.getString("companyId");
            branchId = jsonObject.getString("branchId");
            jid = jsonObject.getString("jid");

        } catch (JSONException e) {
            e.printStackTrace();
        }
        try {
            Intent notificationIntent = new Intent(this, Class.forName(ACTIVITY_TO_START));
            notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            notificationIntent.setAction(PUSH_NOTIFICATION_ACTION_PERFORMED);
            notificationIntent.putExtra("companyId", companyId);
            notificationIntent.putExtra("branchId", branchId);
            notificationIntent.putExtra("jid", jid);
            notificationIntent.putExtra("google.message_id", "google.message_id");
            //"google.message_id"
            PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            Notification notification = new NotificationCompat.Builder(getApplicationContext(), "MessagesNotifications")
                    .setLargeIcon(BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.ic_notification))
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Beezz")
                    .setContentText("New message from " + sender)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            int id = Integer.parseInt(jid.substring(4, 9));
            NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Service.NOTIFICATION_SERVICE);
            notificationManager.notify(id, notification);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void handleInvite(JSONObject jsonObject) throws JSONException {
        String url = jsonObject.get("url").toString();
        inviteNotification(url);
    }

    private void inviteNotification(String url) {
        String openUrl = "mantu://pir?" + url + "#Intent;scheme=mantu;package=im.mantu.ionic;end";
        try {
            Intent notificationIntent = new Intent(this, Class.forName(ACTIVITY_TO_START));
            notificationIntent.setAction(Intent.ACTION_VIEW);
            notificationIntent.setData(Uri.parse(openUrl));
            notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, 0);
            Notification notification = new NotificationCompat.Builder(getApplicationContext(), "MessagesNotifications")
                    .setLargeIcon(BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.ic_notification))
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Invitation")
                    .setContentText("New invitation from Beezz")
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Service.NOTIFICATION_SERVICE);
            notificationManager.notify(0, notification);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private Notification getNotification() {
        return new Notification.Builder(this, "MessagesNotifications")
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Push Notification Service")
                .setPriority(Notification.PRIORITY_MAX)
                .build();
    }
}