package im.mantu.ionic.localpush;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class LocalPushReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.hasExtra("pushNotification")) {
            String sharedText = intent.getStringExtra("pushNotification");
            if (sharedText != null) {
                Log.i(LocalPushReceiver.class.getSimpleName(), sharedText);
                intent = new Intent(context, LocalPushNotificationService.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("pushNotification", sharedText);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent);
                } else {
                    context.startService(intent);
                }
            }
        }
    }
}