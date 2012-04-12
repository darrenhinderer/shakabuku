package com.shakabuku;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.shakabuku.WidgetProvider.WidgetService;

public class Configuration extends Activity
{
	private Button pickApp;
	private int widgetId;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.configuration);
		pickApp = (Button)findViewById(R.id.button_pick_activity);
		
		// Assume canceled until app is chosen.
		setResult(RESULT_CANCELED);
		
		widgetId = getIntent().getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
		pickApp.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				pickApp.setEnabled(false);
				pickApp.setText(Configuration.this.getString(R.string.apps_loading));
				
				Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
				mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

				Intent pickIntent = new Intent(Intent.ACTION_PICK_ACTIVITY);
				pickIntent.putExtra(Intent.EXTRA_INTENT, mainIntent);
				startActivityForResult(pickIntent, 0);
			}
		});
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		if (data != null)
		{
			Intent widgetService = new Intent(this, WidgetService.class);
			widgetService.setAction(WidgetService.ACTION_INSTALL);
			widgetService.putExtra(WidgetService.EXTRA_COMPONENT_NAME, data.getComponent());
			widgetService.putExtra(WidgetService.EXTRA_WIDGET_ID, widgetId);
			startService(widgetService);

			Intent resultValue = new Intent();
			resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
			setResult(RESULT_OK, resultValue);
			finish();
		}
		pickApp.setEnabled(true);
		pickApp.setText(getString(R.string.pick_app));
	}
}