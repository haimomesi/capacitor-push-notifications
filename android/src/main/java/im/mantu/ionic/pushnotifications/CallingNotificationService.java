package im.mantu.ionic.pushnotifications;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.RemoteViews;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.getcapacitor.BridgeActivity;

public class CallingNotificationService extends Service {

    public static final String ACTION_CALL = "ACTION_CALL";

    public static final String DATA_SENDER = "DATA_SENDER";
    public static final String DATA_HAS_VIDEO = "DATA_HAS_VIDEO";
    public static final String DATA_CALL_UUID = "DATA_CALL_UUID";
    public static final String DATA_COMPANY_ID = "DATA_COMPANY_ID";
    public static final String DATA_BRANCH_ID = "DATA_BRANCH_ID";
    public static final String DATA_JID = "DATA_JID";
    public static final String DATA_ACTION = "DATA_ACTION";

    public static final String DATA_ID = "DATA_ID";
    private MediaPlayer player;

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (intent != null) {
            final String intentAction = intent.getAction();
            if (ACTION_CALL.equals(intentAction) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                final String sender = intent.getStringExtra(DATA_SENDER);
                final String hasVideo = intent.getStringExtra(DATA_HAS_VIDEO);
                final String callUUID = intent.getStringExtra(DATA_CALL_UUID);
                final String companyId = intent.getStringExtra(DATA_COMPANY_ID);
                final String branchId = intent.getStringExtra(DATA_BRANCH_ID);
                final String jid = intent.getStringExtra(DATA_JID);
                final String action = intent.getStringExtra(DATA_ACTION);
                final int id = intent.getIntExtra(DATA_ID, 0);
                startForeground(id, callNotification(sender, hasVideo, callUUID, companyId, branchId, jid, action));
                playRingtone();

                try {
                    final Handler handler = new Handler(Looper.getMainLooper());
                    handler.postDelayed(
                        () -> {
                            if (player != null && player.isPlaying()) {
                                popupMissedCall(id + 1, sender);
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) stopForeground(Service.STOP_FOREGROUND_REMOVE);
                        },
                        60000
                    );
                } catch (IllegalStateException ex) {
                    ex.printStackTrace();
                }
            }
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRingtone();
    }

    private Notification callNotification(
        String sender,
        String hasVideo,
        String callUUID,
        String companyId,
        String branchId,
        String jid,
        String action
    ) {
        // check if video
        try {
            String activityToStart = "im.mantu.ionic.MainActivity";
            String video = (hasVideo.equals("true")) ? " video " : " ";
            // set the custom notification layout with all data
            RemoteViews customView = new RemoteViews("im.mantu.ionic", R.layout.custom_call_notification);
            customView.setTextViewText(R.id.callType, "Incoming" + video + "call");
            customView.setTextViewText(R.id.name, sender);
            // set notification pending intent
            Intent notificationIntent = new Intent(this.getApplicationContext(), Class.forName(activityToStart));
            PendingIntent pNotificationIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            // set answer pending intent
            Intent intentAnswer = new Intent(this.getApplicationContext(), Class.forName(activityToStart));
            intentAnswer.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intentAnswer.setAction("answer");
            intentAnswer.putExtra(DATA_SENDER, sender);
            intentAnswer.putExtra(DATA_HAS_VIDEO, hasVideo);
            intentAnswer.putExtra(DATA_CALL_UUID, callUUID);
            intentAnswer.putExtra(DATA_COMPANY_ID, companyId);
            intentAnswer.putExtra(DATA_BRANCH_ID, branchId);
            intentAnswer.putExtra(DATA_JID, jid);
            intentAnswer.putExtra(DATA_ACTION, action);
            PendingIntent pIntentAnswer = PendingIntent.getActivity(
                this.getApplicationContext(),
                0,
                intentAnswer,
                PendingIntent.FLAG_CANCEL_CURRENT
            );
            // set decline pending intent
            Intent intentDecline = new Intent(this.getApplicationContext(), Class.forName(activityToStart));
            intentDecline.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intentDecline.setAction("decline");
            intentDecline.putExtra(DATA_SENDER, sender);
            intentDecline.putExtra(DATA_HAS_VIDEO, hasVideo);
            intentDecline.putExtra(DATA_CALL_UUID, callUUID);
            intentDecline.putExtra(DATA_COMPANY_ID, companyId);
            intentDecline.putExtra(DATA_BRANCH_ID, branchId);
            intentDecline.putExtra(DATA_JID, jid);
            intentDecline.putExtra(DATA_ACTION, action);
            PendingIntent pIntentDecline = PendingIntent.getActivity(
                this.getApplicationContext(),
                0,
                intentDecline,
                PendingIntent.FLAG_CANCEL_CURRENT
            );
            // set buttons click
            customView.setOnClickPendingIntent(R.id.btnAnswer, pIntentAnswer);
            customView.setOnClickPendingIntent(R.id.btnDecline, pIntentDecline);
            // build notification
            NotificationCompat.Builder notification = new NotificationCompat.Builder(this.getApplicationContext(), "CallsNotifications")
                .setContentTitle("Beezz")
                .setTicker("Call_STATUS")
                .setContentText("IncomingCall")
                .setSmallIcon(R.drawable.ic_notification)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_LIGHTS)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVibrate(null)
                .setOngoing(true)
                .setFullScreenIntent(pNotificationIntent, true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
            notification
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(customView)
                .setCustomBigContentView(customView);
            return notification.build();
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private void playRingtone() {
        player = MediaPlayer.create(this, R.raw.incoming);
        player.setVolume(.8f, .8f);
        player.setLooping(true);
        player.start();
    }

    private void stopRingtone() {
        player.release();
        player = null;
    }

    private void popupMissedCall(int id, String sender) {
        try {
            String activityToStart = "im.mantu.ionic.MainActivity";
            Intent notificationIntent = new Intent(this, Class.forName(activityToStart));
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent pNotificationIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            NotificationCompat.Builder notification = new NotificationCompat.Builder(this, "MessagesNotifications")
                .setContentTitle("Beezz")
                .setTicker("Call_STATUS")
                .setContentText("Missed call from " + sender)
                .setSmallIcon(R.drawable.ic_notification)
                .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
            Notification n = notification.build();
            n.contentIntent = pNotificationIntent;
            NotificationManager notificationManager = (NotificationManager) getSystemService(Service.NOTIFICATION_SERVICE);
            notificationManager.notify(id, n);
        } catch (ClassNotFoundException ignored) {}
    }
}
