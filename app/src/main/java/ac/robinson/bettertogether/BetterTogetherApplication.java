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

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;

import java.lang.ref.WeakReference;

import ac.robinson.bettertogether.hotspot.HotspotManagerService;

public class BetterTogetherApplication extends Application {

	WeakReference<Activity> mFrontActivity;

	@Override
	public void onCreate() {
		super.onCreate();
		startService(new Intent(getBaseContext(), HotspotManagerService.class));
		registerActivityLifecycleCallbacks(mActivityLifecycleCallbacks);
	}

	private ActivityLifecycleCallbacks mActivityLifecycleCallbacks = new ActivityLifecycleCallbacks() {
		@Override
		public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
		}

		@Override
		public void onActivityStarted(Activity activity) {
		}

		@Override
		public void onActivityResumed(Activity activity) {
			if (mFrontActivity != null) {
				mFrontActivity.clear();
			}
			mFrontActivity = new WeakReference<>(activity);
		}

		@Override
		public void onActivityPaused(Activity activity) {
		}

		@Override
		public void onActivityStopped(Activity activity) {
		}

		@Override
		public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
		}

		@Override
		public void onActivityDestroyed(Activity activity) {
			if (mFrontActivity != null && mFrontActivity.get() == activity) {
				mFrontActivity.clear();
			}
		}
	};

	public Activity getActiveActivity() {
		if (mFrontActivity != null) {
			return mFrontActivity.get();
		}
		return null;
	}
}
