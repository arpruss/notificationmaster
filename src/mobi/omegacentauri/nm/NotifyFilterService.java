package mobi.omegacentauri.nm;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.RemoteViews;

public class NotifyFilterService extends NotificationListenerService {
	Map<String, NotificationFilter[]> filters = new HashMap<String, NotificationFilter[]>();
	Map<String,ToneGenerator> tones = new HashMap<String,ToneGenerator>();
	Integer key = 1;

	@Override
	public void onCreate() {
		super.onCreate();
		Log.v("nm", "Created");

		filters.put("com.android.mms",
				new NotificationFilter[] { 
				new NotificationFilter(NotificationFilter.MATCH_SUBSTRING, "arp:siren", NotificationFilter.ACTION_SIREN) 
		});
		filters.put("com.android.gm",
				new NotificationFilter[] { 
				new NotificationFilter(NotificationFilter.MATCH_SUBSTRING, "^archived", NotificationFilter.ACTION_CANCEL) 
		});
		filters.put("com.htc.sense.mms",
				new NotificationFilter[] { 
				new NotificationFilter(NotificationFilter.MATCH_SUBSTRING, "arp:siren", NotificationFilter.ACTION_SIREN),
				new NotificationFilter(NotificationFilter.MATCH_SUBSTRING, "arp:sound", NotificationFilter.ACTION_SOUND),
				new NotificationFilter(NotificationFilter.MATCH_SUBSTRING, "arp:novibrate", NotificationFilter.ACTION_NOVIBRATE),
				new NotificationFilter(NotificationFilter.MATCH_SUBSTRING, "arp:cancel", NotificationFilter.ACTION_CANCEL)
		});
		filters.put("com.accuweather.android",
				new NotificationFilter[] { 
				new NotificationFilter(NotificationFilter.MATCH_SUBSTRING, "tornado warning", 
						NotificationFilter.ACTION_SIREN | NotificationFilter.ACTION_VIBRATE | NotificationFilter.ACTION_LIGHTS |
						NotificationFilter.ACTION_NOSOUND),
				new NotificationFilter(NotificationFilter.MATCH_SUBSTRING, "tornado", 
						NotificationFilter.ACTION_UNCHANGED),
				new NotificationFilter(NotificationFilter.MATCH_SUBSTRING, "flood", 
						NotificationFilter.ACTION_CANCEL),
				new NotificationFilter(NotificationFilter.MATCH_SUBSTRING, "heat advisory", 
						NotificationFilter.ACTION_CANCEL),
				new NotificationFilter(NotificationFilter.MATCH_ALWAYS, "", 
						NotificationFilter.ACTION_NOSOUND | NotificationFilter.ACTION_NOVIBRATE),						
		});
		filters.put("jp.co.johospace.jorte",
				new NotificationFilter[] { 
				new NotificationFilter(NotificationFilter.MATCH_SUBSTRING, 
						"you have no events for today", NotificationFilter.ACTION_CANCEL),
						new NotificationFilter(NotificationFilter.MATCH_SUBSTRING, 
								"events for today", NotificationFilter.ACTION_NOVIBRATE)
		});

	}

	private int getAction(String pkg, Notification n) {
		StringBuilder data = new StringBuilder();

		NotificationFilter[] filterArray = filters.get(pkg);
		if (filterArray == null) {
//			filterArray = new NotificationFilter[0]; // for debugging
			return NotificationFilter.ACTION_UNCHANGED;
		}

		if (n.tickerText != null) {
			data.append("\n");
			data.append(n.tickerText);
		}

		Bundle extras = n.extras;
		CharSequence s = (CharSequence) extras.get(Notification.EXTRA_TITLE);
		if (s != null) {
			data.append("\n");
			data.append(s.toString());
		}
		s = (CharSequence) extras.get(Notification.EXTRA_BIG_TEXT);
		if (s != null) {
			data.append("\n");
			data.append(s.toString());
		}
		else {
			s = (CharSequence) extras.get(Notification.EXTRA_TEXT);
			if (s != null) {
				data.append("\n");
				data.append(s.toString());
			}
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
		data.append("\n");

		String text = data.toString().replaceAll("[\\r\\n]+", "\n").replaceAll("[ \\t]+", " ");		
		
		for (NotificationFilter f : filterArray) 
			if (f.match(text))
				return f.action;
		
		return NotificationFilter.ACTION_UNCHANGED;
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
		int action = getAction(packageName, n);
		if (action == NotificationFilter.ACTION_UNCHANGED) {
			Log.v("nm", "notification unchanged");
			return;
		}
		if (0 != (action & NotificationFilter.ACTION_CANCEL)) {
			Log.v("nm", "notification canceled");
			cancelNotification(sbn.getKey());
			return;
		}
		boolean resend = false;
		if (0 != (action & NotificationFilter.ACTION_SIREN)) {
			addTone(sbn.getKey(), ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 30000);
		}
		if (0 != (action & NotificationFilter.ACTION_NOVIBRATE)) {
			n.defaults &= ~Notification.DEFAULT_VIBRATE;
			n.vibrate = null;
			resend = true;
		}
		if (0 != (action & NotificationFilter.ACTION_VIBRATE)) {
			n.defaults |= Notification.DEFAULT_VIBRATE;
			n.vibrate = null;
			resend = true;
		}
		if (0 != (action & NotificationFilter.ACTION_SOUND)) {
			n.defaults |= Notification.DEFAULT_SOUND;
			resend = true;
		}
		if (0 != (action & NotificationFilter.ACTION_NOSOUND)) {
			n.defaults &= ~Notification.DEFAULT_SOUND;
			resend = true;
		}
		if (0 != (action & NotificationFilter.ACTION_LIGHTS)) {
			n.defaults |= Notification.DEFAULT_LIGHTS;
			resend = true;
		}
		if (0 != (action & NotificationFilter.ACTION_NOLIGHTS)) {
			n.defaults &= ~Notification.DEFAULT_LIGHTS;
			resend = true;
		}
		if (resend) {
			cancelNotification(sbn.getKey());
			NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			synchronized(key) {
				nm.notify(key++, n);
			}
		}
	}

	@Override
	public void onNotificationRemoved(StatusBarNotification sbn) {
		removeTone(sbn.getKey());
	}

	static class NotificationFilter {
		static final int ACTION_SIREN = 0x1;
		static final int ACTION_CANCEL = 0x2;
		static final int ACTION_NOVIBRATE = 0x4;
		static final int ACTION_VIBRATE = 0x8;
		static final int ACTION_NOSOUND = 0x10;
		static final int ACTION_SOUND = 0x20;
		static final int ACTION_NOLIGHTS = 0x40;
		static final int ACTION_LIGHTS = 0x80;
		static final int ACTION_UNCHANGED = 0;

		static final int MATCH_ALWAYS = 0;
		static final int MATCH_SUBSTRING = 1;
		static final int MATCH_NO_SUBSTRING = 2;

		int matchMode;
		String matchText;
		int action;

		NotificationFilter(int matchMode, String matchText, int action) {
			this.matchMode = matchMode;
			this.matchText = matchText;
			this.action = action;
		}

		boolean match(String notificationText) {
			switch(matchMode) {
			case MATCH_ALWAYS:
				return true;
			case MATCH_SUBSTRING:
				return notificationText.toLowerCase().replace("^", "\n").contains(matchText.replace("^", "\n"));
			case MATCH_NO_SUBSTRING:
				return !notificationText.toLowerCase().replace("^", "\n").contains(matchText.replace("^", "\n"));
			default:
				return false;
			}
		}
	}
}
