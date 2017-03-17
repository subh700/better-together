/*
 * Copyright (C) 2017 The Better Together Toolkit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package ac.robinson.bettertogether;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.github.rubensousa.gravitysnaphelper.GravitySnapHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ac.robinson.bettertogether.api.messaging.BroadcastMessage;
import ac.robinson.bettertogether.api.messaging.PluginIntent;
import ac.robinson.bettertogether.host.Plugin;
import ac.robinson.bettertogether.host.PluginActivity;
import ac.robinson.bettertogether.host.PluginAdapter;
import ac.robinson.bettertogether.host.PluginClickListener;
import ac.robinson.bettertogether.host.PluginFinder;
import ac.robinson.bettertogether.hotspot.BaseHotspotActivity;
import ac.robinson.bettertogether.hotspot.ConnectionOptions;
import ac.robinson.bettertogether.hotspot.HotspotManagerService;

import static android.view.animation.AnimationUtils.loadAnimation;

public class PluginHostActivity extends BaseHotspotActivity implements PluginClickListener {

	private static final String TAG = "PluginHostActivity";

	public static final String EXTRA_HOTSPOT_URL = "plugin_package";

	private RecyclerView mPluginActivityView;
	private PluginActivityAdapter mPluginActivityViewAdapter;
	private RecyclerView mPluginView;
	private PluginAdapter mPluginViewAdapter;

	private LinearLayout mFooter;
	private TextView mFooterTextOpen;
	private TextView mFooterTextClosed;

	private int mPluginTheme;
	private boolean mIsConnectionHost;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();
		if (!intent.hasExtra(EXTRA_HOTSPOT_URL)) {
			Log.e(TAG, "NO PLUGIN SPECIFIED"); // TODO: fix this - show a Toast?
			finish();
			return;
		}

		String requestedHotspotUrl = intent.getStringExtra(EXTRA_HOTSPOT_URL);

		if (TextUtils.isEmpty(requestedHotspotUrl)) {
			Log.e(TAG, "NO PLUGIN SPECIFIED"); // TODO: fix this - show a Toast?
			finish();
			return;
		}

		if (savedInstanceState != null) {
			mIsConnectionHost = savedInstanceState.getBoolean("mIsConnectionHost");
		}

		setHotspotUrl(requestedHotspotUrl);
		mPluginViewAdapter = new PluginAdapter(PluginHostActivity.this, PluginHostActivity.this);
		mPluginActivityViewAdapter = new PluginActivityAdapter();

		updatePluginList(true);

		setContentView(R.layout.activity_plugin_host);
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		mPluginActivityView = (RecyclerView) findViewById(R.id.plugin_activities_view);
		mPluginActivityView.setLayoutManager(new GridLayoutManager(PluginHostActivity.this, 2, GridLayoutManager.VERTICAL,
				false));
		mPluginActivityView.setHasFixedSize(true);
		mPluginActivityView.setAdapter(mPluginActivityViewAdapter);
		new GravitySnapHelper(Gravity.START, false, mPluginActivityViewAdapter).attachToRecyclerView(mPluginActivityView);

		mPluginView = (RecyclerView) findViewById(R.id.plugin_view);
		mPluginView.setLayoutManager(new LinearLayoutManager(PluginHostActivity.this, LinearLayoutManager.HORIZONTAL, false));
		mPluginView.setHasFixedSize(true);
		mPluginView.setAdapter(mPluginViewAdapter);
		new GravitySnapHelper(Gravity.START, false, mPluginViewAdapter).attachToRecyclerView(mPluginView);

		mFooter = (LinearLayout) findViewById(R.id.footer);
		mFooterTextOpen = (TextView) findViewById(R.id.footer_text_open);
		mFooterTextClosed = (TextView) findViewById(R.id.footer_text_closed);

		updateToolbarColours(mPluginTheme);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putBoolean("mIsConnectionHost", mIsConnectionHost);
		super.onSaveInstanceState(outState);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_add, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_add_device: // every mode activity can help others join the group
				showQRDialog(getHotspotUrl());
				BroadcastMessage qrMessage = new BroadcastMessage(BroadcastMessage.TYPE_DEFAULT, HotspotManagerService
						.SYSTEM_BROADCAST_EVENT_SHOW_QR_CODE);
				qrMessage.setSystemMessage();
				sendBroadcastMessage(qrMessage);
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void updatePluginList(boolean warnAboutMissingPlugin) {
		Map<String, Plugin> plugins = PluginFinder.getValidPlugins(PluginHostActivity.this, null);
		mPluginViewAdapter.clearPlugins();
		mPluginActivityViewAdapter.clearActivities();

		final String currentPluginPackage = ConnectionOptions.getPackageFromHotspotUrl(getHotspotUrl());
		boolean currentPluginFound = false;

		for (Plugin plugin : plugins.values()) {
			if (plugin.getIcon(PluginHostActivity.this) != null) {
				if (plugin.getPackageName().equals(currentPluginPackage)) {
					// note: this filtering here (rather than using getValidPlugins() with a specific package) is what enables
					// inbuilt plugins to be filtered like this when their packages do not exist as separate apps
					currentPluginFound = true;
					for (PluginActivity activity : plugin.getActivities()) {
						mPluginActivityViewAdapter.addActivity(activity);
					}
					mPluginTheme = plugin.getTheme(); // bit hacky, but we don't have layout objects yet
					if (plugin.hasTheme()) {
						setTheme(mPluginTheme);
					}
					setTitle(plugin.getFilteredPluginLabel(PluginHostActivity.this));
				} else {
					mPluginViewAdapter.addPlugin(plugin);
				}
			} else {
				Log.w(TAG, "Error loading icon for " + plugin.getPackageName());
			}
		}

		if (warnAboutMissingPlugin && !currentPluginFound) {
			AlertDialog.Builder builder = new AlertDialog.Builder(PluginHostActivity.this);
			builder.setTitle(R.string.title_plugin_not_found);
			builder.setMessage(R.string.message_plugin_not_found);
			builder.setNegativeButton(R.string.button_cancel, null);
			builder.setPositiveButton(R.string.button_play_store, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					try {
						Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(PluginIntent.MARKET_PACKAGE_QUERY +
								currentPluginPackage));
						intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
						startActivity(intent);
					} catch (Exception ignored) {
						// TODO: show error toast?
					}
				}
			});
			builder.show();
		}
	}

	private void updateToolbarColours(int pluginTheme) {
		// TODO: would be better to use plugin.hasTheme() here, but would require holding plugin in memory for no other reason
		if (pluginTheme == 0) {
			// fall back to default theme colours - could set these in XML, but then the theme wouldn't override them
			Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
			if (toolbar != null) {
				Resources resources = getResources();
				//noinspection deprecation (we're targeting an SDK version before getColor(int, Theme) is available)
				toolbar.setBackgroundColor(resources.getColor(R.color.bettertogether_primary));
				//noinspection deprecation (we're targeting an SDK version before getColor(int, Theme) is available)
				toolbar.setTitleTextColor(resources.getColor(R.color.bettertogether_text));
			}
		} else {
			mFooterTextOpen.setBackgroundColor(BetterTogetherUtils.getThemeColour(PluginHostActivity.this, pluginTheme, R.attr
					.colorPrimary));
			mFooterTextClosed.setBackgroundColor(BetterTogetherUtils.getThemeColour(PluginHostActivity.this, pluginTheme, R.attr
					.colorPrimary));
			mPluginView.setBackgroundColor(BetterTogetherUtils.getThemeColour(PluginHostActivity.this, pluginTheme, R.attr
					.colorButtonNormal));
		}
	}

	@Override
	public void onBroadcastMessageReceived(BroadcastMessage message) {
		// nothing to do in this activity
	}

	@Override
	public void onSystemMessageReceived(int type, String data) {
		super.onSystemMessageReceived(type, data);
		switch (type) {
			case HotspotManagerService.EVENT_DEVICE_CONNECTED:
				// device connection updates in this activity tell us whether we are the client or server
				mIsConnectionHost = HotspotManagerService.ROLE_SERVER.equals(data);
				break;

			case HotspotManagerService.EVENT_REMOTE_CLIENT_ERROR: // TODO: if this was the last client, re-show QR code?
			case HotspotManagerService.EVENT_SETTINGS_PERMISSION_ERROR: // TODO: anything to do here?
			case HotspotManagerService.EVENT_CONNECTION_INVALID_URL: // TODO: can this happen here?
			case HotspotManagerService.EVENT_CONNECTION_STATUS_UPDATE:
				break;

			case HotspotManagerService.EVENT_LOCAL_CLIENT_ERROR:
			case HotspotManagerService.EVENT_DEVICE_DISCONNECTED:
				if (!isFinishing()) {
					Intent intent = new Intent(PluginHostActivity.this, ConnectionSetupActivity.class);
					intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					intent.putExtra(ConnectionSetupActivity.RECONNECT_EXISTING_HOTSPOT, getHotspotUrl());
					startActivity(intent);
					finish(); // TODO: on slower devices do we need to wait until activity is definitely connected before this?
					Log.d(TAG, "Local client error - re-showing connection activity");
					// our connection to the server failed - will attempt to reconnect automatically, but close current plugin
				}
				break;
			default:
				break;
		}
	}

	@Override
	protected void pluginUpdated(String pluginPackage) {
		super.pluginUpdated(pluginPackage);
		String currentPluginPackage = ConnectionOptions.getPackageFromHotspotUrl(getHotspotUrl());
		if (pluginPackage.equals(currentPluginPackage)) {
			// must relaunch activity to change theme - no override because we don't get package updates for inbuilt plugins
			launchPluginAndFinish(getHotspotUrl(), false);
		} else {
			updatePluginList(false);
		}
	}

	@Override
	public void onClick(Plugin plugin) {
		if (plugin == null) {
			launchGetPluginsActivity();
		} else {
			// TODO: send plugin switch action to other clients?
			ConnectionOptions currentConnectionOptions = ConnectionOptions.fromHotspotUrl(getHotspotUrl());
			if (currentConnectionOptions != null) {
				currentConnectionOptions.mPluginPackage = plugin.getPackageName();
				launchPluginAndFinish(currentConnectionOptions.getHotspotUrl(), plugin.isInbuiltPlugin());
			} else {
				// TODO: is there anything we can do?
			}
		}
	}

	public void handleClick(View view) {
		switch (view.getId()) {
			case R.id.footer_text_closed:
				mFooter.setVisibility(View.VISIBLE);
				Animation pluginInAnimation = loadAnimation(PluginHostActivity.this, R.anim.slide_in_bottom);
				pluginInAnimation.setAnimationListener(new Animation.AnimationListener() {
					@Override
					public void onAnimationStart(Animation animation) {
					}

					@Override
					public void onAnimationEnd(Animation animation) {
						RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams
								.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
						lp.addRule(RelativeLayout.BELOW, R.id.toolbar);
						lp.addRule(RelativeLayout.ABOVE, R.id.footer);
						mPluginActivityView.setLayoutParams(lp);
					}

					@Override
					public void onAnimationRepeat(Animation animation) {
					}
				});
				mFooter.startAnimation(pluginInAnimation);
				break;

			case R.id.footer_text_open:
				Animation pluginOutAnimation = loadAnimation(PluginHostActivity.this, R.anim.slide_out_bottom);
				pluginOutAnimation.setAnimationListener(new Animation.AnimationListener() {
					@Override
					public void onAnimationStart(Animation animation) {
					}

					@Override
					public void onAnimationEnd(Animation animation) {
						mFooter.setVisibility(View.GONE);
						RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams
								.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
						lp.addRule(RelativeLayout.BELOW, R.id.toolbar);
						lp.addRule(RelativeLayout.ABOVE, R.id.footer_text_closed);
						mPluginActivityView.setLayoutParams(lp);
					}

					@Override
					public void onAnimationRepeat(Animation animation) {
					}
				});
				mFooter.startAnimation(pluginOutAnimation);
				break;

			default:
				break;
		}
	}

	private class PluginActivityAdapter extends RecyclerView.Adapter<PluginActivityAdapter.PluginActivityViewHolder> implements
			GravitySnapHelper.SnapListener {
		private List<PluginActivity> mActivities;

		PluginActivityAdapter() {
			mActivities = new ArrayList<>();
		}

		void clearActivities() {
			mActivities.clear();
			notifyDataSetChanged();
		}

		void addActivity(PluginActivity activity) {
			mActivities.add(0, activity);
			notifyDataSetChanged();
		}

		@Override
		public PluginActivityViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			return new PluginActivityViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.list_activities,
					parent, false));
		}

		@Override
		public void onBindViewHolder(PluginActivityViewHolder holder, int position) {
			PluginActivity activity = mActivities.get(position);
			holder.mTextView.setText(activity.getLabel(PluginHostActivity.this));
			holder.mTextView.setCompoundDrawablesWithIntrinsicBounds(null, activity.getIcon(PluginHostActivity.this), null,
					null);
		}

		@Override
		public int getItemCount() {
			return mActivities.size();
		}

		@Override
		public void onSnap(int position) {
		}

		class PluginActivityViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
			final TextView mTextView;

			PluginActivityViewHolder(View itemView) {
				super(itemView);
				itemView.setOnClickListener(this);
				mTextView = (TextView) itemView.findViewById(R.id.activity_label);
			}

			@Override
			public void onClick(View v) {
				PluginActivity activity = mActivities.get(getAdapterPosition());
				Intent intent = new Intent(PluginIntent.ACTION_LAUNCH_PLUGIN);
				intent.setClassName(activity.getPackageName(), activity.getActivityName());
				Log.d(TAG, "Launching plugin activity: " + activity.getPackageName());
				try {
					startActivity(intent);
				} catch (Exception ignored) {
					// TODO: plugin not present - anything we can do?
				}
			}
		}
	}

	private void showQRDialog(final String hotspotUrl) {
		// suppress because we don't have (and can't get) a root view for the AlertDialog
		@SuppressLint("InflateParams") View dialogLayout = LayoutInflater.from(PluginHostActivity.this).inflate(R.layout
				.dialog_qr_code, null);

		AlertDialog.Builder builder = new AlertDialog.Builder(PluginHostActivity.this);
		builder.setPositiveButton(R.string.button_done, null);
		final AlertDialog qrDialog = builder.create();
		qrDialog.setView(dialogLayout);
		qrDialog.setOnShowListener(new DialogInterface.OnShowListener() {
			@Override
			public void onShow(DialogInterface d) {
				ImageView imageView = (ImageView) qrDialog.findViewById(R.id.dialog_qr_image);
				Bitmap qrCode = BetterTogetherUtils.generateQrCode(hotspotUrl);
				if (imageView != null && qrCode != null) {
					imageView.setImageBitmap(qrCode);

					// TODO: improve this - have a popup dialog from the toolbar?
					DisplayMetrics displayMetrics = new DisplayMetrics();
					getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
					FrameLayout.LayoutParams layoutParams;
					if (displayMetrics.heightPixels > displayMetrics.widthPixels) {
						float viewWidth = (float) displayMetrics.widthPixels * 0.8f; // imageView.getWidth();
						layoutParams = new FrameLayout.LayoutParams(Math.round(viewWidth), Math.round(viewWidth * (float) qrCode
								.getHeight() / (float) qrCode.getWidth()));
					} else {
						float viewHeight = (float) displayMetrics.heightPixels * 0.8f; //imageView.getHeight();
						layoutParams = new FrameLayout.LayoutParams(Math.round(viewHeight * (float) qrCode.getWidth() / (float)
								qrCode.getHeight()), Math.round(viewHeight));
					}
					layoutParams.gravity = Gravity.CENTER;
					imageView.setLayoutParams(layoutParams);
				}
			}
		});

		qrDialog.show();
	}

	@Override
	public void onBackPressed() {
		if (mIsConnectionHost) {
			AlertDialog.Builder builder = new AlertDialog.Builder(PluginHostActivity.this);
			builder.setTitle(R.string.title_confirm_exit);
			builder.setMessage(R.string.hint_confirm_exit);
			builder.setPositiveButton(R.string.button_exit, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					finish();
				}
			});
			builder.setNegativeButton(R.string.button_cancel, null);
			builder.show();
		} else {
			super.onBackPressed();
		}
	}
}
