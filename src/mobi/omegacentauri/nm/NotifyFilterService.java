package mobi.omegacentauri.nm;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Notification;
import android.app.NotificationManager;
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
		{"com.htc.sense.mms", "arp:novibrate", "novibrate"}, // testing
		{"com.htc.sense.mms", "arp:silent", "disable"}, // testing
		{"com.accuweather.android", "tornado warning", "siren"},
		{"com.accuweather.android", "tornado", "enable"},
		{"com.accuweather.android", "flood", "disable"},
		{"jp.co.johospace.jorte", "you have no events for today", "disable"},
		{"jp.co.johospace.jorte", "events for today", "novibrate"},
	};
	Map<String,ToneGenerator> tones = new HashMap<String,ToneGenerator>();
	Integer key = 1;
	
	 @Override
	 public void onCreate() {
		 super.onCreate();
		 Log.v("nm", "Created");
	 }
	
	private String notificationFilter(String pkg, Notification n) {
		StringBuilder data = new StringBuilder();

		if (n.tickerText != null) {
			data.append(n.tickerText);
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
                if (action.title != null) {
                    data.append("\n");
                    data.append(action.title);
                }
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
	
	synchronized private void removeTone(String key) {
		ToneGenerator tg = tones.get(key);
		if (tg == null)
			return;
		try {
			tg.stopTone();
		} catch(Exception e) {}
		tones.remove(key);

	}
    
    private void addTone(final String key, final int tone, final int length) {
        new Thread(new Runnable(){
			@Override
			public void run() {
				AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
				int oldVolume = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
				am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION), 0);
				ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, ToneGenerator.MAX_VOLUME);
				tg.startTone(tone, length);
				tones.put(key, tg);
				try {
					Thread.sleep(length);
				} catch (InterruptedException e) {
				}
				NotifyFilterService.this.removeTone(key);
				am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, oldVolume, 0);
			}}).start();
    }
    
	@Override
    public void onNotificationPosted(StatusBarNotification sbn) {
		String packageName = sbn.getPackageName();
		if (packageName == null || packageName.equals(getPackageName()))
			return;
    	Notification n = sbn.getNotification();
    	if (n == null)
    		return;
		String f = notificationFilter(packageName, n);
		Log.v("nm", sbn.getPackageName()+" "+f);
		if (f != null) {
			if (f == "disable") {
				cancelNotification(sbn.getKey());
			}
			else if (f == "novibrate") {
				cancelNotification(sbn.getKey());
				n.defaults &= ~Notification.DEFAULT_VIBRATE;
				n.vibrate = null;
				NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
				synchronized(key) {
					nm.notify(key++, n);
				}
			}
			else if (f == "vibrate") {
				cancelNotification(sbn.getKey());
				n.defaults |= Notification.DEFAULT_VIBRATE;
				n.vibrate = null;
				NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
				synchronized(key) {
					nm.notify(key++, n);
				}
			}
			else if (f == "siren") {
				addTone(sbn.getKey(), ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 30000);
			}
			return;
		}
    }

	@Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
		removeTone(sbn.getKey());
    }
}
