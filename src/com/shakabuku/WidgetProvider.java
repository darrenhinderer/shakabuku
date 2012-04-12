package com.shakabuku;

import java.lang.reflect.Method;
import java.util.List;

import android.app.ActivityManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

public class WidgetProvider extends AppWidgetProvider
{

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds)
	{
		// This called whenever the home screen wants a refresh (like device boot-up).
		for (int i = 0; i < appWidgetIds.length; i++)
		{
			Intent intent = new Intent(context, WidgetService.class);
			intent.setAction(WidgetService.ACTION_UPDATE_DISPLAY);
			intent.putExtra(WidgetService.EXTRA_WIDGET_ID, appWidgetIds[i]);
			context.startService(intent);
		}
	}

	public static class WidgetService extends IntentService
	{
		public static final String ACTION_UPDATE_DISPLAY = "display";
		public static final String ACTION_CLICK = "click";
		public static final String ACTION_INSTALL = "install";
		
		public static final String EXTRA_WIDGET_ID = "widget_id";
		public static final String EXTRA_COMPONENT_NAME = "componentName";
		
		private static final String TAG = WidgetService.class.getSimpleName();
		private static final String PREFS_NAME = "shakabuku";
		private static final String PREF_PREFIX_PACKAGE = "package_";
		private static final String PREF_PREFIX_CLASS = "class_";
		
		private Handler handler;

		public WidgetService()
		{
			super(TAG);
		}

		@Override
		public int onStartCommand(Intent intent, int flags, int startId)
		{
			handler = new Handler();
			return super.onStartCommand(intent, flags, startId);
		}

		@SuppressWarnings("deprecation")
		@Override
		protected void onHandleIntent(Intent intent)
		{
			String action = intent.getAction();
			int widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, -1);
			
			Log.d(TAG, "got intent action:" + action + " id:" + widgetId);
			
			if (action.equals(ACTION_INSTALL))
			{
				installWidget(intent, widgetId);
				return;
			}

			SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
			String packageName = prefs.getString(PREF_PREFIX_PACKAGE + widgetId, null);
			String className = prefs.getString(PREF_PREFIX_CLASS + widgetId, null);
			Log.d(TAG, "packageName:" + packageName + " className:" + className);
			
			if (packageName == null || className == null)
				return;
			
			Bitmap appIcon = getAppBitmap(packageName, className);
			
			if (intent.getAction().equals(ACTION_UPDATE_DISPLAY))
			{
				AppWidgetManager.getInstance(this).updateAppWidget(widgetId, buildUpdate(this, widgetId, appIcon));
			}
			else if (intent.getAction().equals(ACTION_CLICK))
			{
				// Show red eyes
				AppWidgetManager.getInstance(this).updateAppWidget(widgetId, buildRedEyes(this));
				
				// Show toast with UI thread
				handler.post(new Runnable()
				{
					public void run()
					{
						Toast.makeText(getApplicationContext(), "Shakabuku!", Toast.LENGTH_SHORT).show();
					}
				});

				ActivityManager am = ((ActivityManager)getSystemService(Context.ACTIVITY_SERVICE));
				
				
				if (Build.VERSION.SDK_INT >= 8)
				{
					try
					{
						Method killBackgroundProcesses = ActivityManager.class.getMethod("killBackgroundProcesses", String.class);
						killBackgroundProcesses.invoke(am, packageName);
					}
					catch(Exception e)
					{
						Log.e(TAG, "Couldn't kill the process", e);
					}
				}
				else
				{
					// Restart package restarts the application processes
					am.restartPackage(packageName);
				}

				// Briefly wait so the user can see red eyes
				try
				{
					Thread.sleep(1000);
				}
				catch (InterruptedException e)
				{
				}

				// Launch the app
				launchFromRecentTasks(am, packageName);
				
				// Back to white eyes
				AppWidgetManager.getInstance(this).updateAppWidget(widgetId, buildUpdate(this, widgetId, appIcon));
			}
		}

		public RemoteViews buildRedEyes(Context context)
		{
			RemoteViews views = new RemoteViews(getPackageName(), R.layout.widget);
			views.setImageViewResource(R.id.icon, R.drawable.voodoo_red);
			return views;
		}

		public RemoteViews buildUpdate(Context context, int widgetId, Bitmap appIcon)
		{
			RemoteViews views = new RemoteViews(getPackageName(), R.layout.widget);

			Intent future = new Intent(context, WidgetService.class);
			future.setAction(ACTION_CLICK);
			future.putExtra(EXTRA_WIDGET_ID, widgetId);
			PendingIntent pendingIntent = PendingIntent.getService(context, widgetId, future, 0);
			views.setOnClickPendingIntent(R.id.widget, pendingIntent);
			views.setImageViewResource(R.id.icon, R.drawable.voodoo);
			views.setImageViewBitmap(R.id.mini_icon, appIcon);
			return views;
		}
		
		private void installWidget(Intent intent, int widgetId)
		{
			ComponentName component = intent.getParcelableExtra(EXTRA_COMPONENT_NAME);
			
			SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
			prefs.edit().putString(PREF_PREFIX_PACKAGE  + widgetId, component.getPackageName()).commit();
			prefs.edit().putString(PREF_PREFIX_CLASS + widgetId, component.getClassName()).commit();
			
			Bitmap appIcon = getAppBitmap(component.getPackageName(), component.getClassName());
			AppWidgetManager.getInstance(this).updateAppWidget(widgetId, buildUpdate(this, widgetId, appIcon));
		}
		
		private Bitmap getAppBitmap(String packageName, String className)
		{
			try
			{
				BitmapDrawable appIcon = (BitmapDrawable)getPackageManager().getActivityIcon(new ComponentName(packageName, className));
				return appIcon.getBitmap();
			}
			catch (NameNotFoundException e1)
			{
			}
			return null;
		}
		
		private void launchFromRecentTasks(ActivityManager am, String packageName)
		{
			List<ActivityManager.RecentTaskInfo> recentTaskList = am.getRecentTasks(30, 0);

			for (int i = 0; i < recentTaskList.size(); i++)
			{
				ActivityManager.RecentTaskInfo recentTaskInfo = recentTaskList.get(i);
				if (packageName.equals(recentTaskInfo.baseIntent.getComponent().getPackageName()))
				{
					recentTaskInfo.baseIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					startActivity(recentTaskInfo.baseIntent);
					break;
				}
			}
		}
	}
}
