package mobi.omegacentauri.nm;

import java.lang.reflect.Field;
import java.util.List;

import android.app.Notification;
import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.RemoteViews;

public class NotifyFilterService extends NotificationListenerService {
	static String[][] notificationFilter = {
		{"com.android.mms", "arp:siren", "siren"}, // testing
		{"com.htc.sense.mms", "arp:siren", "siren"}, // testing
		{"com.htc.sense.mms", "arp:silent", "disable"}, // testing
		{"com.accuweather.android", "tornado warning", "siren"},
		{"com.accuweather.android", "tornado", "enable"},
		{"com.accuweather.android", "flood", "disable"},
		{"jp.co.johospace.jorte", "you have no events for today", "disable"},
		{"jp.co.johospace.jorte", "events for today", "novibrate"},
	};

	
	 @Override
	 public void onCreate() {
		 super.onCreate();
		 Log.v("nm", "Created");
	 }
	
	private String notificationFilter(String pkg, Notification n) {
		StringBuilder data = new StringBuilder();

		if (n.tickerText != null) {
			data.append(n.tickerText);
			data.append("\n");
		}

		RemoteViews v = n.contentView;

		if (v != null) {
			Field mActions = null;
			try {
				mActions = v.getClass().getDeclaredField("mActions");
			}
			catch(Exception e) {
				try {
					mActions = v.getClass().getSuperclass().getDeclaredField("mActions");
				}
				catch(Exception ee) {
				}
			}
			if (mActions != null) {
				try {
					mActions.setAccessible(true);
					for (Object action: (List<Object>) mActions.get(v)) {
						try {
							Class actionClass = action.getClass();
							Field type = actionClass.getDeclaredField("type");
							type.setAccessible(true);
							int t = (Integer)type.get(action);
							if (t == 9 || t == 10) {
								Field value = actionClass.getDeclaredField("value");
								value.setAccessible(true);
								data.append("\n");
								data.append(value.get(action).toString());
							}
						}
						catch(Exception ee) {}
					}
				}
				catch(Exception e) {
				}
			}
		}
		
		if (n.actions != null) {
			for (Notification.Action action : n.actions) {
				data.append(action.title);
				data.append("\n");
			}
		}
		
		String text = data.toString().toLowerCase();
		
		Log.v("nm","examining notification: "+text);
		
		for (String[] line : notificationFilter){
			if (line[0].equals(pkg) && text.contains(line[1]))
				return line[2];
		} 
		return null;
	}	
	
	private void siren() {
		new Thread(new Runnable(){

			@Override
			public void run() {
				Log.v("nm","xmisc: siren");
				AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
				am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION), 0);
				am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
				final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, ToneGenerator.MAX_VOLUME);
				tg.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 30000);
				try {
					Thread.sleep(30000);
				} catch (InterruptedException e) {
					return;
				}
				tg.stopTone();
			}}).start();
	}	
	
	@Override
    public void onNotificationPosted(StatusBarNotification sbn) {
		String f = notificationFilter(sbn.getPackageName(), sbn.getNotification());
		Log.v("nm", sbn.getPackageName()+" "+f);
		if (f != null) {
			if (f == "disable") {
				cancelNotification(sbn.getKey());
			}
			else if (f == "novibrate") {
				sbn.getNotification().vibrate = new long[0];
			}
			else if (f == "siren") {
				siren();
			}
			return;
		}
    }
}
