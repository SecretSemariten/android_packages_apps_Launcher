/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.Dialog;
import android.app.ISearchManager;
import android.app.IWallpaperService;
import android.app.SearchManager;
import android.app.StatusBarManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Intent.ShortcutIconResource;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.LiveFolders;
import android.provider.Settings;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.TextKeyListener;
import static android.util.Log.*;
import android.util.SparseArray;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewStub;
import android.view.WindowManager;
import android.view.View.OnLongClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.ScaleAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.SlidingDrawer;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.LinearLayout;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Canvas;
import android.view.HapticFeedbackConstants;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedList;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.DataInputStream;

import com.android.launcher.DockBar.DockBarListener;
import com.android.launcher.SliderView.OnTriggerListener;

/**
 * Default launcher application.
 */
public final class Launcher extends Activity implements View.OnClickListener, OnLongClickListener {
    static final String LOG_TAG = "Launcher";
    static final boolean LOGD = false;

    private static final boolean PROFILE_STARTUP = false;
    private static final boolean PROFILE_DRAWER = false;
    private static final boolean PROFILE_ROTATE = false;
    private static final boolean DEBUG_USER_INTERFACE = false;

    private static final int MENU_GROUP_ADD = 1;
    private static final int MENU_ADD = Menu.FIRST + 1;
    private static final int MENU_WALLPAPER_SETTINGS = MENU_ADD + 1;
    private static final int MENU_SEARCH = MENU_WALLPAPER_SETTINGS + 1;
    private static final int MENU_NOTIFICATIONS = MENU_SEARCH + 1;
    private static final int MENU_SETTINGS = MENU_NOTIFICATIONS + 1;
    private static final int MENU_ALMOSTNEXUS = MENU_SETTINGS + 1;

    private static final int REQUEST_CREATE_SHORTCUT = 1;
    private static final int REQUEST_CREATE_LIVE_FOLDER = 4;
    private static final int REQUEST_CREATE_APPWIDGET = 5;
    private static final int REQUEST_PICK_APPLICATION = 6;
    private static final int REQUEST_PICK_SHORTCUT = 7;
    private static final int REQUEST_PICK_LIVE_FOLDER = 8;
    private static final int REQUEST_PICK_APPWIDGET = 9;
    private static final int REQUEST_UPDATE_ALMOSTNEXUS = 10;

    static final String EXTRA_SHORTCUT_DUPLICATE = "duplicate";

    static final String EXTRA_CUSTOM_WIDGET = "custom_widget";
    static final String SEARCH_WIDGET = "search_widget";

    static final int WALLPAPER_SCREENS_SPAN = 2;
    static int SCREEN_COUNT = 3;
    static int DEFAULT_SCREN = 1;
    static final int NUMBER_CELLS_X = 4;
    static final int NUMBER_CELLS_Y = 4;

    private static final int DIALOG_CREATE_SHORTCUT = 1;
    static final int DIALOG_RENAME_FOLDER = 2;

    private static final String PREFERENCES = "launcher.preferences";
    private static final String ALMOSTNEXUS_PREFERENCES = "launcher.preferences.almostnexus";

    // Type: int
    private static final String RUNTIME_STATE_CURRENT_SCREEN = "launcher.current_screen";
    // Type: boolean
    private static final String RUNTIME_STATE_ALL_APPS_FOLDER = "launcher.all_apps_folder";
    // Type: long
    private static final String RUNTIME_STATE_USER_FOLDERS = "launcher.user_folder";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_SCREEN = "launcher.add_screen";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_CELL_X = "launcher.add_cellX";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_CELL_Y = "launcher.add_cellY";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_SPAN_X = "launcher.add_spanX";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_SPAN_Y = "launcher.add_spanY";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_COUNT_X = "launcher.add_countX";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_COUNT_Y = "launcher.add_countY";
    // Type: int[]
    private static final String RUNTIME_STATE_PENDING_ADD_OCCUPIED_CELLS = "launcher.add_occupied_cells";
    // Type: boolean
    private static final String RUNTIME_STATE_PENDING_FOLDER_RENAME = "launcher.rename_folder";
    // Type: long
    private static final String RUNTIME_STATE_PENDING_FOLDER_RENAME_ID = "launcher.rename_folder_id";
    // Type: boolean
    private static final String RUNTIME_STATE_DOCKBAR = "launcher.dockbar";

    private static final LauncherModel sModel = new LauncherModel();

    private static Bitmap sWallpaper;

    private static final Object sLock = new Object();
    private static int sScreen = DEFAULT_SCREN;

    private static WallpaperIntentReceiver sWallpaperReceiver;

    private final BroadcastReceiver mApplicationsReceiver = new ApplicationsIntentReceiver();
    private final ContentObserver mObserver = new FavoritesChangeObserver();
    private final ContentObserver mWidgetObserver = new AppWidgetResetObserver();

    private LayoutInflater mInflater;

    private DragLayer mDragLayer;
    private Workspace mWorkspace;

    private AppWidgetManager mAppWidgetManager;
    private LauncherAppWidgetHost mAppWidgetHost;

    static final int APPWIDGET_HOST_ID = 1024;

    private CellLayout.CellInfo mAddItemCellInfo;
    private CellLayout.CellInfo mMenuAddInfo;
    private final int[] mCellCoordinates = new int[2];
    private FolderInfo mFolderInfo;

    //private SlidingDrawer mDrawer;
    //private ViewGroup allApps;
    private TransitionDrawable mHandleIcon;
    //private HandleView mHandleView;
    private SliderView mHandleView;
    //private AllAppsGridView mAllAppsGrid;
    //private AllAppsSlidingView mAllAppsGrid;
    private View mAllAppsGrid;
    private boolean mDesktopLocked = true;
    private Bundle mSavedState;

    private SpannableStringBuilder mDefaultKeySsb = null;

    private boolean mDestroyed;
    
    private boolean mIsNewIntent;

    private boolean mRestoring;
    private boolean mWaitingForResult;
    private boolean mLocaleChanged;

    private Bundle mSavedInstanceState;

    private DesktopBinder mBinder;

    private ImageView mPreviousView;
    private ImageView mNextView;
    private MiniLauncher mMiniLauncher;
    private boolean allAppsOpen=false;
    private boolean allAppsAnimating=false;
    private boolean allowDrawerAnimations=true;
    private boolean newDrawer=true;
    private boolean newPreviews=true;
    private boolean homePreviews=true;
	private boolean fullScreenPreviews=true;
    private boolean showingPreviews=false;
	private boolean hideStatusBar=false;
	private boolean showDots=true;
	private boolean showDockBar=true;
	private boolean mShouldHideStatusbaronFocus=false;
	private DockBar mDockBar;
	private boolean autoCloseDockbar;
	private ActionButton mLAB;
	private ActionButton mRAB;
	private boolean showLAB=true;
	private boolean showRAB=true;
	private boolean tintActionIcons=true;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mInflater = getLayoutInflater();

        mAppWidgetManager = AppWidgetManager.getInstance(this);

        mAppWidgetHost = new LauncherAppWidgetHost(this, APPWIDGET_HOST_ID);
        mAppWidgetHost.startListening();

        if (PROFILE_STARTUP) {
            android.os.Debug.startMethodTracing("/sdcard/launcher");
        }

        checkForLocaleChange();
        setWallpaperDimension();
        newDrawer=AlmostNexusSettingsHelper.getDrawerNew(Launcher.this);
        setContentView(R.layout.launcher);
        setupViews();

        registerIntentReceivers();
        registerContentObservers();

        if (PROFILE_STARTUP) {
            android.os.Debug.stopMethodTracing();
        }

        if (!mRestoring) {
            startLoaders();
        }

        mSavedState = savedInstanceState;
        restoreState(mSavedState);

        // For handling default keys
        mDefaultKeySsb = new SpannableStringBuilder();
        Selection.setSelection(mDefaultKeySsb, 0);
        if(!AlmostNexusSettingsHelper.getDesktopRotation(this)){
        	this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        }
    }

    private void checkForLocaleChange() {
        final LocaleConfiguration localeConfiguration = new LocaleConfiguration();
        readConfiguration(this, localeConfiguration);
        
        final Configuration configuration = getResources().getConfiguration();

        final String previousLocale = localeConfiguration.locale;
        final String locale = configuration.locale.toString();

        final int previousMcc = localeConfiguration.mcc;
        final int mcc = configuration.mcc;

        final int previousMnc = localeConfiguration.mnc;
        final int mnc = configuration.mnc;

        mLocaleChanged = !locale.equals(previousLocale) || mcc != previousMcc || mnc != previousMnc;

        if (mLocaleChanged) {
            localeConfiguration.locale = locale;
            localeConfiguration.mcc = mcc;
            localeConfiguration.mnc = mnc;

            writeConfiguration(this, localeConfiguration);
        }
    }

    private static class LocaleConfiguration {
        public String locale;
        public int mcc = -1;
        public int mnc = -1;
    }
    
    private static void readConfiguration(Context context, LocaleConfiguration configuration) {
        DataInputStream in = null;
        try {
            in = new DataInputStream(context.openFileInput(PREFERENCES));
            configuration.locale = in.readUTF();
            configuration.mcc = in.readInt();
            configuration.mnc = in.readInt();
        } catch (FileNotFoundException e) {
            // Ignore
        } catch (IOException e) {
            // Ignore
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    private static void writeConfiguration(Context context, LocaleConfiguration configuration) {
        DataOutputStream out = null;
        try {
            out = new DataOutputStream(context.openFileOutput(PREFERENCES, MODE_PRIVATE));
            out.writeUTF(configuration.locale);
            out.writeInt(configuration.mcc);
            out.writeInt(configuration.mnc);
            out.flush();
        } catch (FileNotFoundException e) {
            // Ignore
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            context.getFileStreamPath(PREFERENCES).delete();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    static int getScreen() {
        synchronized (sLock) {
            return sScreen;
        }
    }

    static void setScreen(int screen) {
        synchronized (sLock) {
            sScreen = screen;
        }
    }

    private void startLoaders() {
        boolean loadApplications = sModel.loadApplications(true, this, mLocaleChanged);
        sModel.loadUserItems(!mLocaleChanged, this, mLocaleChanged, loadApplications);

        mRestoring = false;
    }

    private void setWallpaperDimension() {
        IBinder binder = ServiceManager.getService(WALLPAPER_SERVICE);
        IWallpaperService wallpaperService = IWallpaperService.Stub.asInterface(binder);

        Display display = getWindowManager().getDefaultDisplay();
        boolean isPortrait = display.getWidth() < display.getHeight();

        final int width = isPortrait ? display.getWidth() : display.getHeight();
        final int height = isPortrait ? display.getHeight() : display.getWidth();
        try {
            wallpaperService.setDimensionHints(width * WALLPAPER_SCREENS_SPAN, height);
        } catch (RemoteException e) {
            // System is dead!
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        mWaitingForResult = false;

        // The pattern used here is that a user PICKs a specific application,
        // which, depending on the target, might need to CREATE the actual target.

        // For example, the user would PICK_SHORTCUT for "Music playlist", and we
        // launch over to the Music app to actually CREATE_SHORTCUT.

        if (resultCode == RESULT_OK && mAddItemCellInfo != null) {
            switch (requestCode) {
                case REQUEST_PICK_APPLICATION:
                    completeAddApplication(this, data, mAddItemCellInfo, !mDesktopLocked);
                    break;
                case REQUEST_PICK_SHORTCUT:
                    processShortcut(data, REQUEST_PICK_APPLICATION, REQUEST_CREATE_SHORTCUT);
                    break;
                case REQUEST_CREATE_SHORTCUT:
                    completeAddShortcut(data, mAddItemCellInfo, !mDesktopLocked);
                    break;
                case REQUEST_PICK_LIVE_FOLDER:
                    addLiveFolder(data);
                    break;
                case REQUEST_CREATE_LIVE_FOLDER:
                    completeAddLiveFolder(data, mAddItemCellInfo, !mDesktopLocked);
                    break;
                case REQUEST_PICK_APPWIDGET:
                    addAppWidget(data);
                    break;
                case REQUEST_CREATE_APPWIDGET:
                    completeAddAppWidget(data, mAddItemCellInfo, !mDesktopLocked);
                    break;
            }
        } else if ((requestCode == REQUEST_PICK_APPWIDGET ||
                requestCode == REQUEST_CREATE_APPWIDGET) && resultCode == RESULT_CANCELED &&
                data != null) {
            // Clean up the appWidgetId if we canceled
            int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            if (appWidgetId != -1) {
                mAppWidgetHost.deleteAppWidgetId(appWidgetId);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(AlmostNexusSettingsHelper.getDesktopRotation(this)){
	        this.setRequestedOrientation(
	        		Settings.System.getInt(this.getContentResolver(), "launcher_orientation", 1) == 0 ?
	        				ActivityInfo.SCREEN_ORIENTATION_NOSENSOR : ActivityInfo.SCREEN_ORIENTATION_USER);
        }else{
        	this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        }
        if (mRestoring) {
            startLoaders();
        }
        
        // If this was a new intent (i.e., the mIsNewIntent flag got set to true by
        // onNewIntent), then close the search dialog if needed, because it probably
        // came from the user pressing 'home' (rather than, for example, pressing 'back').
        if (mIsNewIntent) {
            // Post to a handler so that this happens after the search dialog tries to open
            // itself again.
            mWorkspace.post(new Runnable() {
                public void run() {
                    ISearchManager searchManagerService = ISearchManager.Stub.asInterface(
                            ServiceManager.getService(Context.SEARCH_SERVICE));
                    try {
                        searchManagerService.stopSearch();
                    } catch (RemoteException e) {
                        e(LOG_TAG, "error stopping search", e);
                    }    
                }
            });
        }
        
        //TODO:ADW Change columns after rotating phone
        //newDrawer=AlmostNexusSettingsHelper.getDrawerNew(Launcher.this);
        int ori = getResources().getConfiguration().orientation;
		if(ori==Configuration.ORIENTATION_PORTRAIT){
			if(newDrawer){
				((AllAppsSlidingView) mAllAppsGrid).setNumColumns(AlmostNexusSettingsHelper.getColumnsPortrait(Launcher.this));
				((AllAppsSlidingView) mAllAppsGrid).setNumRows(AlmostNexusSettingsHelper.getRowsPortrait(Launcher.this));
			}else{
				((AllAppsGridView) mAllAppsGrid).setNumColumns(AlmostNexusSettingsHelper.getColumnsPortrait(Launcher.this));
			}
		}else {
			if(newDrawer){
				((AllAppsSlidingView) mAllAppsGrid).setNumColumns(AlmostNexusSettingsHelper.getColumnsLandscape(Launcher.this));
				((AllAppsSlidingView) mAllAppsGrid).setNumRows(AlmostNexusSettingsHelper.getRowsLandscape(Launcher.this));
			}else{
				((AllAppsGridView) mAllAppsGrid).setNumColumns(AlmostNexusSettingsHelper.getColumnsLandscape(Launcher.this));
			}
		}
		allowDrawerAnimations=AlmostNexusSettingsHelper.getDrawerAnimated(Launcher.this);
		newPreviews=AlmostNexusSettingsHelper.getNewPreviews(this);
		homePreviews=AlmostNexusSettingsHelper.getPreviewsHome(this);
		fullScreenPreviews=AlmostNexusSettingsHelper.getFullScreenPreviews(this);
		hideStatusBar=AlmostNexusSettingsHelper.getHideStatusbar(this);
		showDots=AlmostNexusSettingsHelper.getUIDots(this);
		showDockBar=AlmostNexusSettingsHelper.getUIDockbar(this);
		autoCloseDockbar=AlmostNexusSettingsHelper.getUICloseDockbar(this);
		showLAB=AlmostNexusSettingsHelper.getUILAB(this);
		showRAB=AlmostNexusSettingsHelper.getUIRAB(this);
		
		boolean tint=AlmostNexusSettingsHelper.getUITint(this);
		if(tint!=tintActionIcons){
			tintActionIcons=tint;
			mRAB.updateIcon();
			mLAB.updateIcon();
		}
		if(newDrawer){
			((AllAppsSlidingView) mAllAppsGrid).setForceOpaque(AlmostNexusSettingsHelper.getDrawerFast(Launcher.this));
		}else{
			((AllAppsGridView) mAllAppsGrid).setForceOpaque(AlmostNexusSettingsHelper.getDrawerFast(Launcher.this));
		}
		if(!mIsNewIntent)
			updateUIConfiguration();
        mIsNewIntent = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        //dismissPreviews();
        closeDrawer(false);
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        // Flag any binder to stop early before switching
        if (mBinder != null) {
            mBinder.mTerminate = true;
        }

        if (PROFILE_ROTATE) {
            android.os.Debug.startMethodTracing("/sdcard/launcher-rotate");
        }
        return null;
    }

    private boolean acceptFilter() {
        final InputMethodManager inputManager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        return !inputManager.isFullscreenMode();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
    	boolean handled = super.onKeyDown(keyCode, event);
        if (!handled && acceptFilter() && keyCode != KeyEvent.KEYCODE_ENTER) {
            boolean gotKey = TextKeyListener.getInstance().onKeyDown(mWorkspace, mDefaultKeySsb,
                    keyCode, event);
            if (gotKey && mDefaultKeySsb != null && mDefaultKeySsb.length() > 0) {
                // something usable has been typed - start a search
                // the typed text will be retrieved and cleared by
                // showSearchDialog()
                // If there are multiple keystrokes before the search dialog takes focus,
                // onSearchRequested() will be called for every keystroke,
                // but it is idempotent, so it's fine.
                return onSearchRequested();
            }
        }

        return handled;
    }

    private String getTypedText() {
        return mDefaultKeySsb.toString();
    }

    private void clearTypedText() {
        mDefaultKeySsb.clear();
        mDefaultKeySsb.clearSpans();
        Selection.setSelection(mDefaultKeySsb, 0);
    }

    /**
     * Restores the previous state, if it exists.
     *
     * @param savedState The previous state.
     */
    private void restoreState(Bundle savedState) {
        if (savedState == null) {
            return;
        }

        final int currentScreen = savedState.getInt(RUNTIME_STATE_CURRENT_SCREEN, -1);
        if (currentScreen > -1) {
            mWorkspace.setCurrentScreen(currentScreen);
        }

        final int addScreen = savedState.getInt(RUNTIME_STATE_PENDING_ADD_SCREEN, -1);
        if (addScreen > -1) {
            mAddItemCellInfo = new CellLayout.CellInfo();
            final CellLayout.CellInfo addItemCellInfo = mAddItemCellInfo;
            addItemCellInfo.valid = true;
            addItemCellInfo.screen = addScreen;
            addItemCellInfo.cellX = savedState.getInt(RUNTIME_STATE_PENDING_ADD_CELL_X);
            addItemCellInfo.cellY = savedState.getInt(RUNTIME_STATE_PENDING_ADD_CELL_Y);
            addItemCellInfo.spanX = savedState.getInt(RUNTIME_STATE_PENDING_ADD_SPAN_X);
            addItemCellInfo.spanY = savedState.getInt(RUNTIME_STATE_PENDING_ADD_SPAN_Y);
            addItemCellInfo.findVacantCellsFromOccupied(
                    savedState.getBooleanArray(RUNTIME_STATE_PENDING_ADD_OCCUPIED_CELLS),
                    savedState.getInt(RUNTIME_STATE_PENDING_ADD_COUNT_X),
                    savedState.getInt(RUNTIME_STATE_PENDING_ADD_COUNT_Y));
            mRestoring = true;
        }

        boolean renameFolder = savedState.getBoolean(RUNTIME_STATE_PENDING_FOLDER_RENAME, false);
        if (renameFolder) {
            long id = savedState.getLong(RUNTIME_STATE_PENDING_FOLDER_RENAME_ID);
            mFolderInfo = sModel.getFolderById(this, id);
            mRestoring = true;
        }
    }

    /**
     * Finds all the views we need and configure them properly.
     */
    private void setupViews() {
        mDragLayer = (DragLayer) findViewById(R.id.drag_layer);
        final DragLayer dragLayer = mDragLayer;

        mWorkspace = (Workspace) dragLayer.findViewById(R.id.workspace);
        final Workspace workspace = mWorkspace;

        ViewStub tmp=(ViewStub)dragLayer.findViewById(R.id.stub_drawer);
        //mAllAppsGrid = (AllAppsSlidingView) dragLayer.findViewById(R.id.all_apps_view);
        if(newDrawer){
        	tmp.setLayoutResource(R.layout.new_drawer);
        }else{
        	tmp.setLayoutResource(R.layout.old_drawer);
        }
        mAllAppsGrid = tmp.inflate();
        final View grid = mAllAppsGrid;

        final DeleteZone deleteZone = (DeleteZone) dragLayer.findViewById(R.id.delete_zone);

        //mHandleView = (HandleView) dragLayer.findViewById(R.id.all_apps);
        mHandleView = (SliderView) dragLayer.findViewById(R.id.all_apps);
        mHandleView.setLauncher(this);
        mHandleView.setOnTriggerListener(new OnTriggerListener() {
			//@Override
			public void onTrigger(View v, int whichHandle) {
				mDockBar.open();
			}
			//@Override
			public void onGrabbedStateChange(View v, boolean grabbedState) {
				// TODO Auto-generated method stub
			}
		});
        //BY ADW
        mHandleView.setOnClickListener(this);
        mHandleIcon = (TransitionDrawable) mHandleView.getDrawable();
        mHandleIcon.setCrossFadeEnabled(true);

        //drawer.lock();
        //final DrawerManager drawerManager = new DrawerManager();
        //drawer.setOnDrawerOpenListener(drawerManager);
        //drawer.setOnDrawerCloseListener(drawerManager);
        //drawer.setOnDrawerScrollListener(drawerManager);

        //grid.setTextFilterEnabled(false);
        if(newDrawer){
        	((AllAppsSlidingView)grid).setDragger(dragLayer);
        	((AllAppsSlidingView)grid).setLauncher(this);
        }else{
        	((AllAppsGridView)grid).setDragger(dragLayer);
        	((AllAppsGridView)grid).setLauncher(this);
        }

        workspace.setOnLongClickListener(this);
        workspace.setDragger(dragLayer);
        workspace.setLauncher(this);
        loadWallpaper();

        deleteZone.setLauncher(this);
        deleteZone.setDragController(dragLayer);
        deleteZone.setHandle(mHandleView);

        dragLayer.setIgnoredDropTarget(grid);
        dragLayer.setDragScoller(workspace);
        //dragLayer.setDragListener(deleteZone);
        dragLayer.addDragListener(deleteZone);
        
        mMiniLauncher = (MiniLauncher) dragLayer.findViewById(R.id.mini_content);
        mMiniLauncher.setLauncher(this);
        mMiniLauncher.setOnLongClickListener(this);
        //miniToolbar.setDragger(dragLayer);
        dragLayer.addDragListener(mMiniLauncher);
        
        mLAB = (ActionButton) dragLayer.findViewById(R.id.btn_lab);
        mLAB.setLauncher(this);
        dragLayer.addDragListener(mLAB);
        mRAB = (ActionButton) dragLayer.findViewById(R.id.btn_rab);
        mRAB.setLauncher(this);
        dragLayer.addDragListener(mRAB);
        mLAB.setOnClickListener(this);
        mRAB.setOnClickListener(this);

	//ADDED BY ADW -2010-03-11
	    
	mPreviousView = (ImageView)findViewById(R.id.btn_scroll_left);
	// Register the onClick listener with the implementation above
	/*mPreviousView.setOnClickListener(new android.widget.Button.OnClickListener() {
	public void onClick(View v) {
	    mWorkspace.scrollLeft();
	}
	});*/
	mNextView = (ImageView)findViewById(R.id.btn_scroll_right);
	// Register the onClick listener with the implementation above
	/*mNextView.setOnClickListener(new android.widget.Button.OnClickListener() {
	public void onClick(View v) {
	    mWorkspace.scrollRight();
	}
	});*/
    mPreviousView.setOnLongClickListener(this);
    mNextView.setOnLongClickListener(this);

	Drawable previous = mPreviousView.getDrawable();
	Drawable next = mNextView.getDrawable();
	mWorkspace.setIndicators(previous, next);
	//TODO:ADW add a listener to the dockbar to show/hide the app-drawer-button and the dots
	mDockBar=(DockBar)findViewById(R.id.dockbar);
	mDockBar.setDockBarListener(new DockBarListener() {
		//TODO:ADW handle previous states before hiding/showing
		public void onOpen() {
			mHandleView.setVisibility(View.GONE);
			mLAB.setVisibility(View.GONE);
			mRAB.setVisibility(View.GONE);
			if(mNextView.getVisibility()==View.VISIBLE){
				mNextView.setVisibility(View.INVISIBLE);
				mPreviousView.setVisibility(View.INVISIBLE);
			}
		}
		public void onClose() {
			mHandleView.setVisibility(View.VISIBLE);
	    	mRAB.setVisibility(showRAB?View.VISIBLE:View.GONE);
	    	mLAB.setVisibility(showLAB?View.VISIBLE:View.GONE);
			if(showDots && !isAllAppsVisible()){
				mNextView.setVisibility(View.VISIBLE);
				mPreviousView.setVisibility(View.VISIBLE);
			}
			
		}
	});
    }

    /**
     * Creates a view representing a shortcut.
     *
     * @param info The data structure describing the shortcut.
     *
     * @return A View inflated from R.layout.application.
     */
    View createShortcut(ApplicationInfo info) {
        return createShortcut(R.layout.application,
                (ViewGroup) mWorkspace.getChildAt(mWorkspace.getCurrentScreen()), info);
    }

    /**
     * Creates a view representing a shortcut inflated from the specified resource.
     *
     * @param layoutResId The id of the XML layout used to create the shortcut.
     * @param parent The group the shortcut belongs to.
     * @param info The data structure describing the shortcut.
     *
     * @return A View inflated from layoutResId.
     */
    View createShortcut(int layoutResId, ViewGroup parent, ApplicationInfo info) {
        TextView favorite = (TextView) mInflater.inflate(layoutResId, parent, false);

        if (!info.filtered) {
            info.icon = Utilities.createIconThumbnail(info.icon, this);
            info.filtered = true;
        }

        favorite.setCompoundDrawablesWithIntrinsicBounds(null, info.icon, null, null);
        favorite.setText(info.title);
        favorite.setTag(info);
        favorite.setOnClickListener(this);

        return favorite;
    }
    /**
     * Creates a view representing a shortcut inflated from the specified resource.
     *
     * @param layoutResId The id of the XML layout used to create the shortcut.
     * @param parent The group the shortcut belongs to.
     * @param info The data structure describing the shortcut.
     *
     * @return A View inflated from layoutResId.
     */
    View createSmallShortcut(int layoutResId, ViewGroup parent, ApplicationInfo info) {
        ImageView favorite = (ImageView) mInflater.inflate(layoutResId, parent, false);

        if (!info.filtered) {
            info.icon = Utilities.createIconThumbnail(info.icon, this);
            info.filtered = true;
        }
        favorite.setImageDrawable(Utilities.drawReflection(info.icon, this));
        favorite.setTag(info);
        favorite.setOnClickListener(this);
        return favorite;
    }   
    View createSmallFolder(int layoutResId, ViewGroup parent, UserFolderInfo info) {
        ImageView favorite = (ImageView) mInflater.inflate(layoutResId, parent, false);

        final Resources resources = getResources();
        Drawable d = resources.getDrawable(R.drawable.ic_launcher_folder);
        //d = Utilities.createIconThumbnail(d, this);
        d=Utilities.drawReflection(d, this);
        favorite.setImageDrawable(d);
        favorite.setTag(info);
        favorite.setOnClickListener(this);
        return favorite;
    }
    View createSmallLiveFolder(int layoutResId, ViewGroup parent, LiveFolderInfo info) {
        ImageView favorite = (ImageView) mInflater.inflate(layoutResId, parent, false);

        //LiveFolderIcon icon = (LiveFolderIcon)
        //LayoutInflater.from(this).inflate(layoutResId, parent, false);

        final Resources resources = getResources();
        Drawable d = info.icon;
        if (d == null) {
        	d = Utilities.createIconThumbnail(
            resources.getDrawable(R.drawable.ic_launcher_folder), this);
        	info.filtered = true;
        }
        d=Utilities.drawReflection(d, this);
        favorite.setImageDrawable(d);
        favorite.setTag(info);
        favorite.setOnClickListener(this);
        return favorite;
    }
    Drawable createSmallActionButtonIcon(ItemInfo info){
        Drawable d = null;
    	final Resources resources = getResources();
        if(info instanceof ApplicationInfo){
            if (!((ApplicationInfo)info).filtered) {
            	((ApplicationInfo)info).icon = Utilities.createIconThumbnail(((ApplicationInfo)info).icon, this);
            	((ApplicationInfo)info).filtered = true;
            }
            d=((ApplicationInfo)info).icon;
        }else if(info instanceof LiveFolderInfo){
        	d=((LiveFolderInfo)info).icon;
            if (d == null) {
            	d = Utilities.createIconThumbnail(
                resources.getDrawable(R.drawable.ic_launcher_folder), this);
            	((LiveFolderInfo)info).filtered = true;
            }        	
        }else if(info instanceof UserFolderInfo){
        	d = resources.getDrawable(R.drawable.ic_launcher_folder);
        }
        if (d == null) {
        	d = Utilities.createIconThumbnail(
            resources.getDrawable(R.drawable.ic_launcher_shortcut), this);
        }
        d=Utilities.scaledDrawable(d, this,tintActionIcons);
    	
    	return d;
    }
    /**
     * Add an application shortcut to the workspace.
     *
     * @param data The intent describing the application.
     * @param cellInfo The position on screen where to create the shortcut.
     */
    void completeAddApplication(Context context, Intent data, CellLayout.CellInfo cellInfo,
            boolean insertAtFirst) {
        cellInfo.screen = mWorkspace.getCurrentScreen();
        if (!findSingleSlot(cellInfo)) return;

        final ApplicationInfo info = infoFromApplicationIntent(context, data);
        if (info != null) {
            mWorkspace.addApplicationShortcut(info, cellInfo, insertAtFirst);
        }
    }

    private static ApplicationInfo infoFromApplicationIntent(Context context, Intent data) {
        ComponentName component = data.getComponent();
        PackageManager packageManager = context.getPackageManager();
        ActivityInfo activityInfo = null;
        try {
            activityInfo = packageManager.getActivityInfo(component, 0 /* no flags */);
        } catch (NameNotFoundException e) {
            e(LOG_TAG, "Couldn't find ActivityInfo for selected application", e);
        }

        if (activityInfo != null) {
            ApplicationInfo itemInfo = new ApplicationInfo();

            itemInfo.title = activityInfo.loadLabel(packageManager);
            if (itemInfo.title == null) {
                itemInfo.title = activityInfo.name;
            }

            itemInfo.setActivity(component, Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            itemInfo.icon = activityInfo.loadIcon(packageManager);
            itemInfo.container = ItemInfo.NO_ID;

            return itemInfo;
        }

        return null;
    }

    /**
     * Add a shortcut to the workspace.
     *
     * @param data The intent describing the shortcut.
     * @param cellInfo The position on screen where to create the shortcut.
     * @param insertAtFirst
     */
    private void completeAddShortcut(Intent data, CellLayout.CellInfo cellInfo,
            boolean insertAtFirst) {
        cellInfo.screen = mWorkspace.getCurrentScreen();
        if (!findSingleSlot(cellInfo)) return;

        final ApplicationInfo info = addShortcut(this, data, cellInfo, false);

        if (!mRestoring) {
            sModel.addDesktopItem(info);

            final View view = createShortcut(info);
            mWorkspace.addInCurrentScreen(view, cellInfo.cellX, cellInfo.cellY, 1, 1, insertAtFirst);
        } else if (sModel.isDesktopLoaded()) {
            sModel.addDesktopItem(info);
        }
    }


    /**
     * Add a widget to the workspace.
     *
     * @param data The intent describing the appWidgetId.
     * @param cellInfo The position on screen where to create the widget.
     */
    private void completeAddAppWidget(Intent data, CellLayout.CellInfo cellInfo,
            boolean insertAtFirst) {

        Bundle extras = data.getExtras();
        int appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);

        if (LOGD) d(LOG_TAG, "dumping extras content="+extras.toString());

        AppWidgetProviderInfo appWidgetInfo = mAppWidgetManager.getAppWidgetInfo(appWidgetId);

        // Calculate the grid spans needed to fit this widget
        CellLayout layout = (CellLayout) mWorkspace.getChildAt(cellInfo.screen);
        int[] spans = layout.rectToCell(appWidgetInfo.minWidth, appWidgetInfo.minHeight);

        // Try finding open space on Launcher screen
        final int[] xy = mCellCoordinates;
        if (!findSlot(cellInfo, xy, spans[0], spans[1])) {
            if (appWidgetId != -1) mAppWidgetHost.deleteAppWidgetId(appWidgetId);
            return;
        }

        // Build Launcher-specific widget info and save to database
        LauncherAppWidgetInfo launcherInfo = new LauncherAppWidgetInfo(appWidgetId);
        launcherInfo.spanX = spans[0];
        launcherInfo.spanY = spans[1];

        LauncherModel.addItemToDatabase(this, launcherInfo,
                LauncherSettings.Favorites.CONTAINER_DESKTOP,
                mWorkspace.getCurrentScreen(), xy[0], xy[1], false);

        if (!mRestoring) {
            sModel.addDesktopAppWidget(launcherInfo);

            // Perform actual inflation because we're live
            launcherInfo.hostView = mAppWidgetHost.createView(this, appWidgetId, appWidgetInfo);

            launcherInfo.hostView.setAppWidget(appWidgetId, appWidgetInfo);
            launcherInfo.hostView.setTag(launcherInfo);

            mWorkspace.addInCurrentScreen(launcherInfo.hostView, xy[0], xy[1],
                    launcherInfo.spanX, launcherInfo.spanY, insertAtFirst);
        } else if (sModel.isDesktopLoaded()) {
            sModel.addDesktopAppWidget(launcherInfo);
        }
    }

    public LauncherAppWidgetHost getAppWidgetHost() {
        return mAppWidgetHost;
    }

    static ApplicationInfo addShortcut(Context context, Intent data,
            CellLayout.CellInfo cellInfo, boolean notify) {

        final ApplicationInfo info = infoFromShortcutIntent(context, data);
        LauncherModel.addItemToDatabase(context, info, LauncherSettings.Favorites.CONTAINER_DESKTOP,
                cellInfo.screen, cellInfo.cellX, cellInfo.cellY, notify);

        return info;
    }

    private static ApplicationInfo infoFromShortcutIntent(Context context, Intent data) {
        Intent intent = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
        String name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
        Bitmap bitmap = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON);

        Drawable icon = null;
        boolean filtered = false;
        boolean customIcon = false;
        ShortcutIconResource iconResource = null;

        if (bitmap != null) {
            icon = new FastBitmapDrawable(Utilities.createBitmapThumbnail(bitmap, context));
            filtered = true;
            customIcon = true;
        } else {
            Parcelable extra = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE);
            if (extra != null && extra instanceof ShortcutIconResource) {
                try {
                    iconResource = (ShortcutIconResource) extra;
                    final PackageManager packageManager = context.getPackageManager();
                    Resources resources = packageManager.getResourcesForApplication(
                            iconResource.packageName);
                    final int id = resources.getIdentifier(iconResource.resourceName, null, null);
                    icon = resources.getDrawable(id);
                } catch (Exception e) {
                    w(LOG_TAG, "Could not load shortcut icon: " + extra);
                }
            }
        }

        if (icon == null) {
            icon = context.getPackageManager().getDefaultActivityIcon();
        }

        final ApplicationInfo info = new ApplicationInfo();
        info.icon = icon;
        info.filtered = filtered;
        info.title = name;
        info.intent = intent;
        info.customIcon = customIcon;
        info.iconResource = iconResource;

        return info;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // Close the menu
        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            getWindow().closeAllPanels();
            
            // Set this flag so that onResume knows to close the search dialog if it's open,
            // because this was a new intent (thus a press of 'home' or some such) rather than
            // for example onResume being called when the user pressed the 'back' button.
            mIsNewIntent = true;

            try {
                dismissDialog(DIALOG_CREATE_SHORTCUT);
                // Unlock the workspace if the dialog was showing
                mWorkspace.unlock();
            } catch (Exception e) {
                // An exception is thrown if the dialog is not visible, which is fine
            }

            try {
                dismissDialog(DIALOG_RENAME_FOLDER);
                // Unlock the workspace if the dialog was showing
                mWorkspace.unlock();
            } catch (Exception e) {
                // An exception is thrown if the dialog is not visible, which is fine
            }

            if ((intent.getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) !=
                    Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) {

                if(!homePreviews){
	            	if (!mWorkspace.isDefaultScreenShowing()) {
	                    mWorkspace.moveToDefaultScreen();
	                }else{
	                	if(!showingPreviews){
	                		showPreviews(mHandleView, 0, mWorkspace.mHomeScreens);
	                	}else{
	                		dismissPreviews();
	                	}
	                }
                }else{
                	if(!showingPreviews){
                		showPreviews(mHandleView, 0, mWorkspace.mHomeScreens);
                	}else{
                		dismissPreviews();
                	}
                }
                closeDrawer();

                final View v = getWindow().peekDecorView();
                if (v != null && v.getWindowToken() != null) {
                    InputMethodManager imm = (InputMethodManager)getSystemService(
                            INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            } else {
                closeDrawer(false);
            }
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        // NOTE: Do NOT do this. Ever. This is a terrible and horrifying hack.
        //
        // Home loads the content of the workspace on a background thread. This means that
        // a previously focused view will be, after orientation change, added to the view
        // hierarchy at an undeterminate time in the future. If we were to invoke
        // super.onRestoreInstanceState() here, the focus restoration would fail because the
        // view to focus does not exist yet.
        //
        // However, not invoking super.onRestoreInstanceState() is equally bad. In such a case,
        // panels would not be restored properly. For instance, if the menu is open then the
        // user changes the orientation, the menu would not be opened in the new orientation.
        //
        // To solve both issues Home messes up with the internal state of the bundle to remove
        // the properties it does not want to see restored at this moment. After invoking
        // super.onRestoreInstanceState(), it removes the panels state.
        //
        // Later, when the workspace is done loading, Home calls super.onRestoreInstanceState()
        // again to restore focus and other view properties. It will not, however, restore
        // the panels since at this point the panels' state has been removed from the bundle.
        //
        // This is a bad example, do not do this.
        //
        // If you are curious on how this code was put together, take a look at the following
        // in Android's source code:
        // - Activity.onRestoreInstanceState()
        // - PhoneWindow.restoreHierarchyState()
        // - PhoneWindow.DecorView.onAttachedToWindow()
        //
        // The source code of these various methods shows what states should be kept to
        // achieve what we want here.

        Bundle windowState = savedInstanceState.getBundle("android:viewHierarchyState");
        SparseArray<Parcelable> savedStates = null;
        int focusedViewId = View.NO_ID;

        if (windowState != null) {
            savedStates = windowState.getSparseParcelableArray("android:views");
            windowState.remove("android:views");
            focusedViewId = windowState.getInt("android:focusedViewId", View.NO_ID);
            windowState.remove("android:focusedViewId");
        }

        super.onRestoreInstanceState(savedInstanceState);

        if (windowState != null) {
            windowState.putSparseParcelableArray("android:views", savedStates);
            windowState.putInt("android:focusedViewId", focusedViewId);
            windowState.remove("android:Panels");
        }

        mSavedInstanceState = savedInstanceState;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(RUNTIME_STATE_CURRENT_SCREEN, mWorkspace.getCurrentScreen());

        final ArrayList<Folder> folders = mWorkspace.getOpenFolders();
        if (folders.size() > 0) {
            final int count = folders.size();
            long[] ids = new long[count];
            for (int i = 0; i < count; i++) {
                final FolderInfo info = folders.get(i).getInfo();
                ids[i] = info.id;
            }
            outState.putLongArray(RUNTIME_STATE_USER_FOLDERS, ids);
        }

        final boolean isConfigurationChange = getChangingConfigurations() != 0;

        // When the drawer is opened and we are saving the state because of a
        // configuration change
        if (allAppsOpen && isConfigurationChange) {
            outState.putBoolean(RUNTIME_STATE_ALL_APPS_FOLDER, true);
        }
        if(mDockBar.isOpen()){
        	outState.putBoolean(RUNTIME_STATE_DOCKBAR, true);
        }

        if (mAddItemCellInfo != null && mAddItemCellInfo.valid && mWaitingForResult) {
            final CellLayout.CellInfo addItemCellInfo = mAddItemCellInfo;
            final CellLayout layout = (CellLayout) mWorkspace.getChildAt(addItemCellInfo.screen);

            outState.putInt(RUNTIME_STATE_PENDING_ADD_SCREEN, addItemCellInfo.screen);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_CELL_X, addItemCellInfo.cellX);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_CELL_Y, addItemCellInfo.cellY);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_SPAN_X, addItemCellInfo.spanX);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_SPAN_Y, addItemCellInfo.spanY);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_COUNT_X, layout.getCountX());
            outState.putInt(RUNTIME_STATE_PENDING_ADD_COUNT_Y, layout.getCountY());
            outState.putBooleanArray(RUNTIME_STATE_PENDING_ADD_OCCUPIED_CELLS,
                   layout.getOccupiedCells());
        }

        if (mFolderInfo != null && mWaitingForResult) {
            outState.putBoolean(RUNTIME_STATE_PENDING_FOLDER_RENAME, true);
            outState.putLong(RUNTIME_STATE_PENDING_FOLDER_RENAME_ID, mFolderInfo.id);
        }
    }

    @Override
    public void onDestroy() {
        mDestroyed = true;

        super.onDestroy();

        try {
            mAppWidgetHost.stopListening();
        } catch (NullPointerException ex) {
            w(LOG_TAG, "problem while stopping AppWidgetHost during Launcher destruction", ex);
        }

        TextKeyListener.getInstance().release();

        //mAllAppsGrid.clearTextFilter();
        if(newDrawer){
        	((AllAppsSlidingView)mAllAppsGrid).setAdapter(null);
        }else{
        	((AllAppsGridView)mAllAppsGrid).setAdapter(null);
        }
        sModel.unbind();
        sModel.abortLoaders();

        getContentResolver().unregisterContentObserver(mObserver);
        getContentResolver().unregisterContentObserver(mWidgetObserver);

        dismissPreviews();
        unregisterReceiver(mApplicationsReceiver);
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        if (requestCode >= 0) mWaitingForResult = true;
        super.startActivityForResult(intent, requestCode);
    }

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery,
            Bundle appSearchData, boolean globalSearch) {

        closeDrawer(false);

        // Slide the search widget to the top, if it's on the current screen,
        // otherwise show the search dialog immediately.
        Search searchWidget = mWorkspace.findSearchWidgetOnCurrentScreen();
        if (searchWidget == null) {
            showSearchDialog(initialQuery, selectInitialQuery, appSearchData, globalSearch);
        } else {
            searchWidget.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
            // show the currently typed text in the search widget while sliding
            searchWidget.setQuery(getTypedText());
        }
    }

    /**
     * Show the search dialog immediately, without changing the search widget.
     *
     * @see Activity#startSearch(String, boolean, android.os.Bundle, boolean)
     */
    void showSearchDialog(String initialQuery, boolean selectInitialQuery,
            Bundle appSearchData, boolean globalSearch) {

        if (initialQuery == null) {
            // Use any text typed in the launcher as the initial query
            initialQuery = getTypedText();
            clearTypedText();
        }
        if (appSearchData == null) {
            appSearchData = new Bundle();
            appSearchData.putString(SearchManager.SOURCE, "launcher-search");
        }

        final SearchManager searchManager =
                (SearchManager) getSystemService(Context.SEARCH_SERVICE);

        final Search searchWidget = mWorkspace.findSearchWidgetOnCurrentScreen();
        if (searchWidget != null) {
            // This gets called when the user leaves the search dialog to go back to
            // the Launcher.
            searchManager.setOnCancelListener(new SearchManager.OnCancelListener() {
                public void onCancel() {
                    searchManager.setOnCancelListener(null);
                    stopSearch();
                }
            });
        }

        searchManager.startSearch(initialQuery, selectInitialQuery, getComponentName(),
            appSearchData, globalSearch);
    }

    /**
     * Cancel search dialog if it is open.
     */
    void stopSearch() {
        // Close search dialog
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchManager.stopSearch();
        // Restore search widget to its normal position
        Search searchWidget = mWorkspace.findSearchWidgetOnCurrentScreen();
        if (searchWidget != null) {
            searchWidget.stopSearch(false);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mDesktopLocked && mSavedInstanceState == null) return false;

        super.onCreateOptionsMenu(menu);
        menu.add(MENU_GROUP_ADD, MENU_ADD, 0, R.string.menu_add)
                .setIcon(android.R.drawable.ic_menu_add)
                .setAlphabeticShortcut('A');
        menu.add(0, MENU_WALLPAPER_SETTINGS, 0, R.string.menu_wallpaper)
                 .setIcon(android.R.drawable.ic_menu_gallery)
                 .setAlphabeticShortcut('W');
        menu.add(0, MENU_SEARCH, 0, R.string.menu_search)
                .setIcon(android.R.drawable.ic_search_category_default)
                .setAlphabeticShortcut(SearchManager.MENU_KEY);
        menu.add(0, MENU_NOTIFICATIONS, 0, R.string.menu_notifications)
                .setIcon(com.android.internal.R.drawable.ic_menu_notifications)
                .setAlphabeticShortcut('N');

        final Intent settings = new Intent(android.provider.Settings.ACTION_SETTINGS);
        settings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        menu.add(0, MENU_SETTINGS, 0, R.string.menu_settings)
                .setIcon(android.R.drawable.ic_menu_preferences).setAlphabeticShortcut('P')
                .setIntent(settings);
        //ADW: add custom settings
        menu.add(0, MENU_ALMOSTNEXUS, 0, "ADWSettings")
        .setIcon(com.android.internal.R.drawable.ic_menu_preferences)
        .setAlphabeticShortcut('X');
        
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        // We can't trust the view state here since views we may not be done binding.
        // Get the vacancy state from the model instead.
        mMenuAddInfo = mWorkspace.findAllVacantCellsFromModel();
        menu.setGroupEnabled(MENU_GROUP_ADD, mMenuAddInfo != null && mMenuAddInfo.valid);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ADD:
                addItems();
                return true;
            case MENU_WALLPAPER_SETTINGS:
                startWallpaper();
                return true;
            case MENU_SEARCH:
                onSearchRequested();
                return true;
            case MENU_NOTIFICATIONS:
                showNotifications();
                return true;
            case MENU_ALMOSTNEXUS:
                showCustomConfig();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Indicates that we want global search for this activity by setting the globalSearch
     * argument for {@link #startSearch} to true.
     */

    @Override
    public boolean onSearchRequested() {
        startSearch(null, false, null, true);
        return true;
    }

    private void addItems() {
        showAddDialog(mMenuAddInfo);
    }

    private void removeShortcutsForPackage(String packageName) {
        if (packageName != null && packageName.length() > 0) {
            mWorkspace.removeShortcutsForPackage(packageName);
        }
    }

    private void updateShortcutsForPackage(String packageName) {
        if (packageName != null && packageName.length() > 0) {
            mWorkspace.updateShortcutsForPackage(packageName);
        }
    }

    void addAppWidget(Intent data) {
        // TODO: catch bad widget exception when sent
        int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);

        String customWidget = data.getStringExtra(EXTRA_CUSTOM_WIDGET);
        if (SEARCH_WIDGET.equals(customWidget)) {
            // We don't need this any more, since this isn't a real app widget.
            mAppWidgetHost.deleteAppWidgetId(appWidgetId);
            // add the search widget
            addSearch();
        } else {
            AppWidgetProviderInfo appWidget = mAppWidgetManager.getAppWidgetInfo(appWidgetId);

            if (appWidget.configure != null) {
                // Launch over to configure widget, if needed
                Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
                intent.setComponent(appWidget.configure);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);

                startActivityForResult(intent, REQUEST_CREATE_APPWIDGET);
            } else {
                // Otherwise just add it
                onActivityResult(REQUEST_CREATE_APPWIDGET, Activity.RESULT_OK, data);
            }
        }
    }

    void addSearch() {
        final Widget info = Widget.makeSearch();
        final CellLayout.CellInfo cellInfo = mAddItemCellInfo;

        final int[] xy = mCellCoordinates;
        final int spanX = info.spanX;
        final int spanY = info.spanY;

        if (!findSlot(cellInfo, xy, spanX, spanY)) return;

        sModel.addDesktopItem(info);
        LauncherModel.addItemToDatabase(this, info, LauncherSettings.Favorites.CONTAINER_DESKTOP,
        mWorkspace.getCurrentScreen(), xy[0], xy[1], false);

        final View view = mInflater.inflate(info.layoutResource, null);
        view.setTag(info);
        Search search = (Search) view.findViewById(R.id.widget_search);
        search.setLauncher(this);

        mWorkspace.addInCurrentScreen(view, xy[0], xy[1], info.spanX, spanY);
    }

    void processShortcut(Intent intent, int requestCodeApplication, int requestCodeShortcut) {
        // Handle case where user selected "Applications"
        String applicationName = getResources().getString(R.string.group_applications);
        String shortcutName = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);

        if (applicationName != null && applicationName.equals(shortcutName)) {
            Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

            Intent pickIntent = new Intent(Intent.ACTION_PICK_ACTIVITY);
            pickIntent.putExtra(Intent.EXTRA_INTENT, mainIntent);
            startActivityForResult(pickIntent, requestCodeApplication);
        } else {
            startActivityForResult(intent, requestCodeShortcut);
        }
    }

    void addLiveFolder(Intent intent) {
        // Handle case where user selected "Folder"
        String folderName = getResources().getString(R.string.group_folder);
        String shortcutName = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);

        if (folderName != null && folderName.equals(shortcutName)) {
            addFolder(!mDesktopLocked);
        } else {
            startActivityForResult(intent, REQUEST_CREATE_LIVE_FOLDER);
        }
    }

    void addFolder(boolean insertAtFirst) {
        UserFolderInfo folderInfo = new UserFolderInfo();
        folderInfo.title = getText(R.string.folder_name);

        CellLayout.CellInfo cellInfo = mAddItemCellInfo;
        cellInfo.screen = mWorkspace.getCurrentScreen();
        if (!findSingleSlot(cellInfo)) return;

        // Update the model
        LauncherModel.addItemToDatabase(this, folderInfo, LauncherSettings.Favorites.CONTAINER_DESKTOP,
                mWorkspace.getCurrentScreen(), cellInfo.cellX, cellInfo.cellY, false);
        sModel.addDesktopItem(folderInfo);
        sModel.addFolder(folderInfo);

        // Create the view
        FolderIcon newFolder = FolderIcon.fromXml(R.layout.folder_icon, this,
                (ViewGroup) mWorkspace.getChildAt(mWorkspace.getCurrentScreen()), folderInfo);
        mWorkspace.addInCurrentScreen(newFolder,
                cellInfo.cellX, cellInfo.cellY, 1, 1, insertAtFirst);
    }

    private void completeAddLiveFolder(Intent data, CellLayout.CellInfo cellInfo,
            boolean insertAtFirst) {
        cellInfo.screen = mWorkspace.getCurrentScreen();
        if (!findSingleSlot(cellInfo)) return;

        final LiveFolderInfo info = addLiveFolder(this, data, cellInfo, false);

        if (!mRestoring) {
            sModel.addDesktopItem(info);

            final View view = LiveFolderIcon.fromXml(R.layout.live_folder_icon, this,
                (ViewGroup) mWorkspace.getChildAt(mWorkspace.getCurrentScreen()), info);
            mWorkspace.addInCurrentScreen(view, cellInfo.cellX, cellInfo.cellY, 1, 1, insertAtFirst);
        } else if (sModel.isDesktopLoaded()) {
            sModel.addDesktopItem(info);
        }
    }

    static LiveFolderInfo addLiveFolder(Context context, Intent data,
            CellLayout.CellInfo cellInfo, boolean notify) {

        Intent baseIntent = data.getParcelableExtra(LiveFolders.EXTRA_LIVE_FOLDER_BASE_INTENT);
        String name = data.getStringExtra(LiveFolders.EXTRA_LIVE_FOLDER_NAME);

        Drawable icon = null;
        boolean filtered = false;
        Intent.ShortcutIconResource iconResource = null;

        Parcelable extra = data.getParcelableExtra(LiveFolders.EXTRA_LIVE_FOLDER_ICON);
        if (extra != null && extra instanceof Intent.ShortcutIconResource) {
            try {
                iconResource = (Intent.ShortcutIconResource) extra;
                final PackageManager packageManager = context.getPackageManager();
                Resources resources = packageManager.getResourcesForApplication(
                        iconResource.packageName);
                final int id = resources.getIdentifier(iconResource.resourceName, null, null);
                icon = resources.getDrawable(id);
            } catch (Exception e) {
                w(LOG_TAG, "Could not load live folder icon: " + extra);
            }
        }

        if (icon == null) {
            icon = context.getResources().getDrawable(R.drawable.ic_launcher_folder);
        }

        final LiveFolderInfo info = new LiveFolderInfo();
        info.icon = icon;
        info.filtered = filtered;
        info.title = name;
        info.iconResource = iconResource;
        info.uri = data.getData();
        info.baseIntent = baseIntent;
        info.displayMode = data.getIntExtra(LiveFolders.EXTRA_LIVE_FOLDER_DISPLAY_MODE,
                LiveFolders.DISPLAY_MODE_GRID);

        LauncherModel.addItemToDatabase(context, info, LauncherSettings.Favorites.CONTAINER_DESKTOP,
                cellInfo.screen, cellInfo.cellX, cellInfo.cellY, notify);
        sModel.addFolder(info);

        return info;
    }

    private boolean findSingleSlot(CellLayout.CellInfo cellInfo) {
        final int[] xy = new int[2];
        if (findSlot(cellInfo, xy, 1, 1)) {
            cellInfo.cellX = xy[0];
            cellInfo.cellY = xy[1];
            return true;
        }
        return false;
    }

    private boolean findSlot(CellLayout.CellInfo cellInfo, int[] xy, int spanX, int spanY) {
        if (!cellInfo.findCellForSpan(xy, spanX, spanY)) {
            boolean[] occupied = mSavedState != null ?
                    mSavedState.getBooleanArray(RUNTIME_STATE_PENDING_ADD_OCCUPIED_CELLS) : null;
            cellInfo = mWorkspace.findAllVacantCells(occupied);
            if (!cellInfo.findCellForSpan(xy, spanX, spanY)) {
                Toast.makeText(this, getString(R.string.out_of_space), Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        return true;
    }

    private void showNotifications() {
        final StatusBarManager statusBar = (StatusBarManager) getSystemService(STATUS_BAR_SERVICE);
        if (statusBar != null) {
        	if(hideStatusBar){
        		fullScreen(false);
        		mShouldHideStatusbaronFocus=true;
        	}
        	statusBar.expand();
        }
    }

    private void startWallpaper() {
        final Intent pickWallpaper = new Intent(Intent.ACTION_SET_WALLPAPER);
        startActivity(Intent.createChooser(pickWallpaper, getString(R.string.chooser_wallpaper)));
    }

    /**
     * Registers various intent receivers. The current implementation registers
     * only a wallpaper intent receiver to let other applications change the
     * wallpaper.
     */
    private void registerIntentReceivers() {
        if (sWallpaperReceiver == null) {
            final Application application = getApplication();

            sWallpaperReceiver = new WallpaperIntentReceiver(application, this);

            IntentFilter filter = new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED);
            application.registerReceiver(sWallpaperReceiver, filter);
        } else {
            sWallpaperReceiver.setLauncher(this);
        }

        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(mApplicationsReceiver, filter);
    }

    /**
     * Registers various content observers. The current implementation registers
     * only a favorites observer to keep track of the favorites applications.
     */
    private void registerContentObservers() {
        ContentResolver resolver = getContentResolver();
        resolver.registerContentObserver(LauncherSettings.Favorites.CONTENT_URI, true, mObserver);
        resolver.registerContentObserver(LauncherProvider.CONTENT_APPWIDGET_RESET_URI,
                true, mWidgetObserver);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
    	dismissPreviews();
    	if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_BACK:
                    mWorkspace.dispatchKeyEvent(event);
                    if (allAppsOpen) {
                        closeDrawer();
                    } else {
                        closeFolder();
                    }
                    return true;
                case KeyEvent.KEYCODE_HOME:
                    return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    private void closeDrawer() {
        closeDrawer(true);
    }

    private void closeDrawer(boolean animated) {
    	if (allAppsOpen) {
            if (animated) {
                closeAllApps(true);
            } else {
                closeAllApps(false);
            }
            //TODO: ADW-check if this is working
            if (mAllAppsGrid.hasFocus()) {
                mWorkspace.getChildAt(mWorkspace.getCurrentScreen()).requestFocus();
            }
        }
    }

    private void closeFolder() {
        Folder folder = mWorkspace.getOpenFolder();
        if (folder != null) {
            closeFolder(folder);
        }
    }

    void closeFolder(Folder folder) {
        folder.getInfo().opened = false;
        ViewGroup parent = (ViewGroup) folder.getParent();
        if (parent != null) {
            parent.removeView(folder);
        }
        folder.onClose();
    }

    /**
     * When the notification that favorites have changed is received, requests
     * a favorites list refresh.
     */
    private void onFavoritesChanged() {
        mDesktopLocked = true;
        //BY ADW
        //mDrawer.lock();
        sModel.loadUserItems(false, this, false, false);
    }

    /**
     * Re-listen when widgets are reset.
     */
    private void onAppWidgetReset() {
        mAppWidgetHost.startListening();
    }

    void onDesktopItemsLoaded(ArrayList<ItemInfo> shortcuts,
            ArrayList<LauncherAppWidgetInfo> appWidgets) {
        if (mDestroyed) {
            if (LauncherModel.DEBUG_LOADERS) {
                d(LauncherModel.LOG_TAG, "  ------> destroyed, ignoring desktop items");
            }
            return;
        }
        bindDesktopItems(shortcuts, appWidgets);
    }

    /**
     * Refreshes the shortcuts shown on the workspace.
     */
    private void bindDesktopItems(ArrayList<ItemInfo> shortcuts,
            ArrayList<LauncherAppWidgetInfo> appWidgets) {
        
    	final ApplicationsAdapter drawerAdapter = sModel.getApplicationsAdapter();
        if (shortcuts == null || appWidgets == null || drawerAdapter == null) {
            if (LauncherModel.DEBUG_LOADERS) d(LauncherModel.LOG_TAG, "  ------> a source is null");            
            return;
        }

        final Workspace workspace = mWorkspace;
        int count = workspace.getChildCount();
        for (int i = 0; i < count; i++) {
            ((ViewGroup) workspace.getChildAt(i)).removeAllViewsInLayout();
        }
        //ADW remove the dockbar items too!!!
        mMiniLauncher.removeAllViewsInLayout();
        if (DEBUG_USER_INTERFACE) {
            android.widget.Button finishButton = new android.widget.Button(this);
            finishButton.setText("Finish");
            workspace.addInScreen(finishButton, 1, 0, 0, 1, 1);

            finishButton.setOnClickListener(new android.widget.Button.OnClickListener() {
                public void onClick(View v) {
                    finish();
                }
            });
        }

        // Flag any old binder to terminate early
        if (mBinder != null) {
            mBinder.mTerminate = true;
        }

        mBinder = new DesktopBinder(this, shortcuts, appWidgets, drawerAdapter);
        mBinder.startBindingItems();
    }

    private void bindItems(Launcher.DesktopBinder binder,
            ArrayList<ItemInfo> shortcuts, int start, int count) {

        final Workspace workspace = mWorkspace;
        final boolean desktopLocked = mDesktopLocked;
        final MiniLauncher miniLauncher=(MiniLauncher) mDragLayer.findViewById(R.id.mini_content);
        final int end = Math.min(start + DesktopBinder.ITEMS_COUNT, count);
        int i = start;

        for ( ; i < end; i++) {
            final ItemInfo item = shortcuts.get(i);
            switch ((int)item.container) {
            case LauncherSettings.Favorites.CONTAINER_LAB:
            	mLAB.UpdateLaunchInfo(item);
            	break;
            case LauncherSettings.Favorites.CONTAINER_RAB:
            	mRAB.UpdateLaunchInfo(item);
            	break;
            case LauncherSettings.Favorites.CONTAINER_DOCKBAR:
				miniLauncher.addItemInDockBar(item);
				break;
			default:
	            switch (item.itemType) {
	                case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
	                case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
	                    final View shortcut = createShortcut((ApplicationInfo) item);
        				workspace.addInScreen(shortcut, item.screen, item.cellX, item.cellY, 1, 1,
	                            !desktopLocked);
	                    break;
	                case LauncherSettings.Favorites.ITEM_TYPE_USER_FOLDER:
	                    final FolderIcon newFolder = FolderIcon.fromXml(R.layout.folder_icon, this,
	                            (ViewGroup) workspace.getChildAt(workspace.getCurrentScreen()),
	                            (UserFolderInfo) item);
	                    workspace.addInScreen(newFolder, item.screen, item.cellX, item.cellY, 1, 1,
	                            !desktopLocked);
	                    break;
	                case LauncherSettings.Favorites.ITEM_TYPE_LIVE_FOLDER:
	                    final FolderIcon newLiveFolder = LiveFolderIcon.fromXml(
	                            R.layout.live_folder_icon, this,
	                            (ViewGroup) workspace.getChildAt(workspace.getCurrentScreen()),
	                            (LiveFolderInfo) item);
	                    workspace.addInScreen(newLiveFolder, item.screen, item.cellX, item.cellY, 1, 1,
	                            !desktopLocked);
	                    break;
	                case LauncherSettings.Favorites.ITEM_TYPE_WIDGET_SEARCH:
	                    final int screen = workspace.getCurrentScreen();
	                    final View view = mInflater.inflate(R.layout.widget_search,
	                            (ViewGroup) workspace.getChildAt(screen), false);
	
	                    Search search = (Search) view.findViewById(R.id.widget_search);
	                    search.setLauncher(this);
	
	                    final Widget widget = (Widget) item;
	                    view.setTag(widget);
	
	                    workspace.addWidget(view, widget, !desktopLocked);
	                    break;
	            }
				break;
			}
        }

        workspace.requestLayout();

        if (end >= count) {
            finishBindDesktopItems();
            binder.startBindingDrawer();
        } else {
            binder.obtainMessage(DesktopBinder.MESSAGE_BIND_ITEMS, i, count).sendToTarget();
        }
    }

    private void finishBindDesktopItems() {
        if (mSavedState != null) {
            if (!mWorkspace.hasFocus()) {
                mWorkspace.getChildAt(mWorkspace.getCurrentScreen()).requestFocus();
            }

            final long[] userFolders = mSavedState.getLongArray(RUNTIME_STATE_USER_FOLDERS);
            if (userFolders != null) {
                for (long folderId : userFolders) {
                    final FolderInfo info = sModel.findFolderById(folderId);
                    if (info != null) {
                        openFolder(info);
                    }
                }
                final Folder openFolder = mWorkspace.getOpenFolder();
                if (openFolder != null) {
                    openFolder.requestFocus();
                }
            }

            //ADW Removed to not reopen on rotation
            final boolean allApps = mSavedState.getBoolean(RUNTIME_STATE_ALL_APPS_FOLDER, false);
            if (allApps) {
            	showAllApps(false);
            }
            
            final boolean dockOpen=mSavedState.getBoolean(RUNTIME_STATE_DOCKBAR, false);
            if(dockOpen){
            	mDockBar.open();
            }
            mSavedState = null;
        }

        if (mSavedInstanceState != null) {
            super.onRestoreInstanceState(mSavedInstanceState);
            mSavedInstanceState = null;
        }

        //TODO: ADW-check if this works
        if (allAppsOpen && !mAllAppsGrid.hasFocus()) {
            mAllAppsGrid.requestFocus();
        }

        mDesktopLocked = false;
        //BY ADW
        //mDrawer.unlock();
    }

    private void bindDrawer(Launcher.DesktopBinder binder,
        ApplicationsAdapter drawerAdapter) {
       
        if(newDrawer){
        	((AllAppsSlidingView)mAllAppsGrid).setAdapter(drawerAdapter);
        }else{
        	((AllAppsGridView)mAllAppsGrid).setAdapter(drawerAdapter);
        }
        
    	binder.startBindingAppWidgetsWhenIdle();
    }

    private void bindAppWidgets(Launcher.DesktopBinder binder,
            LinkedList<LauncherAppWidgetInfo> appWidgets) {

        final Workspace workspace = mWorkspace;
        final boolean desktopLocked = mDesktopLocked;

        if (!appWidgets.isEmpty()) {
            final LauncherAppWidgetInfo item = appWidgets.removeFirst();

            final int appWidgetId = item.appWidgetId;
            final AppWidgetProviderInfo appWidgetInfo = mAppWidgetManager.getAppWidgetInfo(appWidgetId);
            item.hostView = mAppWidgetHost.createView(this, appWidgetId, appWidgetInfo);

            if (LOGD) {
                d(LOG_TAG, String.format("about to setAppWidget for id=%d, info=%s",
                       appWidgetId, appWidgetInfo));
            }

            item.hostView.setAppWidget(appWidgetId, appWidgetInfo);
            item.hostView.setTag(item);

            workspace.addInScreen(item.hostView, item.screen, item.cellX,
                    item.cellY, item.spanX, item.spanY, !desktopLocked);

            workspace.requestLayout();
        }

        if (appWidgets.isEmpty()) {
            if (PROFILE_ROTATE) {
                android.os.Debug.stopMethodTracing();
            }
        } else {
            binder.obtainMessage(DesktopBinder.MESSAGE_BIND_APPWIDGETS).sendToTarget();
        }
    }

    DragController getDragController() {
        return mDragLayer;
    }

    /**
     * Launches the intent referred by the clicked shortcut.
     *
     * @param v The view representing the clicked shortcut.
     */
    public void onClick(View v) {
    	Object tag = v.getTag();
        //TODO:ADW Check whether to display a toast if clicked mLAB or mRAB withount binding
    	if(tag instanceof ItemInfo && tag==null && v instanceof ActionButton){
    		Toast t=Toast.makeText(this, "No application defined yet, drop something here! :)", Toast.LENGTH_SHORT);
    		return;
    	}
    	if (tag instanceof ApplicationInfo) {
            // Open shortcut
        	ApplicationInfo info=(ApplicationInfo) tag;
            final Intent intent = info.intent;
            startActivitySafely(intent);
            //Close dockbar if setting says so
            if(info.container==LauncherSettings.Favorites.CONTAINER_DOCKBAR && isDockBarOpen() && autoCloseDockbar){
            	mDockBar.close();
            }
        } else if (tag instanceof FolderInfo) {
        	FolderInfo info=(FolderInfo) tag;
            handleFolderClick(info);
            if(info.container==LauncherSettings.Favorites.CONTAINER_DOCKBAR && isDockBarOpen() && autoCloseDockbar){
            	mDockBar.close();
            }
        }else if (v == mHandleView) {
            if (isAllAppsVisible()) {
                closeAllApps(true);
            } else {
                showAllApps(true);
            }
        }
    }

    void startActivitySafely(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            e(LOG_TAG, "Launcher does not have the permission to launch " + intent +
                    ". Make sure to create a MAIN intent-filter for the corresponding activity " +
                    "or use the exported attribute for this activity.", e);
        }
    }

    private void handleFolderClick(FolderInfo folderInfo) {
        //TODO:ADW check whether folder is in desktop or dockbar
    	if (!folderInfo.opened) {
            // Close any open folder
            closeFolder();
            // Open the requested folder
            openFolder(folderInfo);
        } else {
            // Find the open folder...
            Folder openFolder = mWorkspace.getFolderForTag(folderInfo);
            int folderScreen;
            if (openFolder != null) {
                folderScreen = mWorkspace.getScreenForView(openFolder);
                // .. and close it
                closeFolder(openFolder);
                if (folderScreen != mWorkspace.getCurrentScreen()) {
                    // Close any folder open on the current screen
                    closeFolder();
                    // Pull the folder onto this screen
                    openFolder(folderInfo);
                }
            }
        }
    }

    private void loadWallpaper() {
        // The first time the application is started, we load the wallpaper from
        // the ApplicationContext
        if (sWallpaper == null) {
            final Drawable drawable = getWallpaper();
            if (drawable instanceof BitmapDrawable) {
                sWallpaper = ((BitmapDrawable) drawable).getBitmap();
            } else {
                throw new IllegalStateException("The wallpaper must be a BitmapDrawable.");
            }
        }
        mWorkspace.loadWallpaper(sWallpaper);
    }

    /**
     * Opens the user fodler described by the specified tag. The opening of the folder
     * is animated relative to the specified View. If the View is null, no animation
     * is played.
     *
     * @param folderInfo The FolderInfo describing the folder to open.
     */
    private void openFolder(FolderInfo folderInfo) {
        Folder openFolder;

        if (folderInfo instanceof UserFolderInfo) {
            openFolder = UserFolder.fromXml(this);
        } else if (folderInfo instanceof LiveFolderInfo) {
            openFolder = com.android.launcher.LiveFolder.fromXml(this, folderInfo);
        } else {
            return;
        }

        openFolder.setDragger(mDragLayer);
        openFolder.setLauncher(this);

        openFolder.bind(folderInfo);
        folderInfo.opened = true;
        if(folderInfo.container==LauncherSettings.Favorites.CONTAINER_DOCKBAR){
        	mWorkspace.addInScreen(openFolder, mWorkspace.getCurrentScreen(), 0, 0, 4, 4);
        }else{
        	mWorkspace.addInScreen(openFolder, folderInfo.screen, 0, 0, 4, 4);
        }
        openFolder.onOpen();
    }

    /**
     * Returns true if the workspace is being loaded. When the workspace is loading,
     * no user interaction should be allowed to avoid any conflict.
     *
     * @return True if the workspace is locked, false otherwise.
     */
    boolean isWorkspaceLocked() {
        return mDesktopLocked;
    }

    public boolean onLongClick(View v) {
	// BY ADW //
    	switch (v.getId()) {
            case R.id.btn_scroll_left:
		mWorkspace.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
		    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
		showPreviousPreview(v);
                return true;
            case R.id.btn_scroll_right:
		mWorkspace.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
		    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
		showNextPreview(v);
                return true;
        }
	// EOF ADW
        if (mDesktopLocked) {
            return false;
        }

        if (!(v instanceof CellLayout)) {
            v = (View) v.getParent();
        }

        CellLayout.CellInfo cellInfo = (CellLayout.CellInfo) v.getTag();

        // This happens when long clicking an item with the dpad/trackball
        if (cellInfo == null) {
            return true;
        }

        if (mWorkspace.allowLongPress()) {
            if (cellInfo.cell == null) {
                if (cellInfo.valid) {
                    // User long pressed on empty space
                    mWorkspace.setAllowLongPress(false);
                    showAddDialog(cellInfo);
                }
            } else {
                if (!(cellInfo.cell instanceof Folder)) {
                    // User long pressed on an item
                    mWorkspace.startDrag(cellInfo);
                }
            }
        }
        return true;
    }
//BY ADW    
    @SuppressWarnings({"unchecked"})
    
    public void previousScreen(View v) {
	    mWorkspace.scrollLeft();
	}
    public void nextScreen(View v) {
	    mWorkspace.scrollRight();
	}
    private void showAllApps(boolean animated){
		if(!allAppsOpen){
			allAppsOpen=true;
	        mWorkspace.lock();
	        mDesktopLocked=true;
	        mWorkspace.invalidate();			
			//allApps.setVisibility(View.VISIBLE);
			mAllAppsGrid.setVisibility(View.VISIBLE);
			if(animated && allowDrawerAnimations){
				Animation animation = AnimationUtils.loadAnimation(this,R.anim.apps_fade_in);
				animation.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
					public void onAnimationStart(Animation animation) {
						// TODO Auto-generated method stub
					}
					public void onAnimationRepeat(Animation animation) {
						// TODO Auto-generated method stub
					}
					public void onAnimationEnd(Animation animation) {
						// TODO Auto-generated method stub
						allAppsAnimating=false;
					}
				});
				allAppsAnimating=true;
				mAllAppsGrid.startAnimation(animation);
			}else{

			}
			mHandleIcon.startTransition(150);
    	    mPreviousView.setVisibility(View.GONE);
    	    mNextView.setVisibility(View.GONE);			
		}

    }
    private void closeAllApps(boolean animated){		
		if(allAppsOpen){
			allAppsOpen=false;
	        mWorkspace.unlock();
	        mDesktopLocked=false;
	        mWorkspace.invalidate();			
			if(animated && allowDrawerAnimations){
				Animation animation = AnimationUtils.loadAnimation(this,R.anim.apps_fade_out);
				animation.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
					
					
					public void onAnimationStart(Animation animation) {
						// TODO Auto-generated method stub
					}
					
					
					public void onAnimationRepeat(Animation animation) {
						// TODO Auto-generated method stub
					}
					
					
					public void onAnimationEnd(Animation animation) {
						// TODO Auto-generated method stub
						//allApps.setVisibility(View.GONE);
						mAllAppsGrid.setVisibility(View.GONE);
						allAppsAnimating=false;
			            if(newDrawer){
			            	((AllAppsSlidingView)mAllAppsGrid).setSelection(0);
			            }else{
			            	((AllAppsGridView)mAllAppsGrid).setSelection(0);
			            }
			            //mAllAppsGrid.clearTextFilter();	
					}
				});
				allAppsAnimating=true;
				mAllAppsGrid.startAnimation(animation);
			}else{
				//allApps.setVisibility(View.GONE);
				mAllAppsGrid.setVisibility(View.GONE);
	            //mWorkspace.mDrawerBounds.setEmpty();
			}
			mHandleIcon.resetTransition();
			if(!isDockBarOpen() && showDots){
				mPreviousView.setVisibility(View.VISIBLE);
	    	    mNextView.setVisibility(View.VISIBLE);
			}else{
				mPreviousView.setVisibility(View.GONE);
	    	    mNextView.setVisibility(View.GONE);
			}
		}    	
    }
    protected Bitmap getBlurredBg(){
        Workspace workspace = mWorkspace;
    	return workspace.getWallpaperSection();
    }
    protected boolean isPreviewing(){
    	return showingPreviews;
    }
    protected boolean isFullScreenPreviewing(){
    	return showingPreviews && fullScreenPreviews;
    }
    private void updateUIConfiguration(){
    	fullScreen(hideStatusBar);
    	if(!mDockBar.isOpen()){
	    	mNextView.setVisibility(showDots?View.VISIBLE:View.GONE);
	    	mPreviousView.setVisibility(showDots?View.VISIBLE:View.GONE);
	    	mRAB.setVisibility(showRAB?View.VISIBLE:View.GONE);
	    	mLAB.setVisibility(showLAB?View.VISIBLE:View.GONE);
	    	mHandleView.setSlidingEnabled(showDockBar);
    	}
    }
    private void zoom(){
    	//TODO: ADW Maybe for the future... :)
    	/*mWorkspace.lock();
    	ScaleAnimation an=new ScaleAnimation(1, .1f, 1, .1f);
    	an.setDuration(500);
    	for(int i=0;i<mWorkspace.getChildCount();i++){
    		mWorkspace.getChildAt(i).startAnimation(an);
    	}*/
    }
    private void fullScreen(boolean enable){
    	if(enable){
	    	// go full screen
	    	WindowManager.LayoutParams attrs = getWindow().getAttributes();
	    	attrs.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
	    	getWindow().setAttributes(attrs);
    	}else{
	    	// go non-full screen
	    	WindowManager.LayoutParams attrs = getWindow().getAttributes();
	    	attrs.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN);
	    	getWindow().setAttributes(attrs);
    	}
    }
    private void hideDesktop(boolean enable){
    	if(enable){
	    	mHandleView.setVisibility(View.INVISIBLE);
	    	mNextView.setVisibility(View.INVISIBLE);
	    	mPreviousView.setVisibility(View.INVISIBLE);
    		mRAB.setVisibility(View.INVISIBLE);
    		mLAB.setVisibility(View.INVISIBLE);
	        if(mDockBar.isOpen()){
	        	mDockBar.setVisibility(View.INVISIBLE);
	        }    		
    	}else{
	        if(mDockBar.isOpen()){
	        	mDockBar.setVisibility(View.VISIBLE);
	        }else{
		    	mHandleView.setVisibility(View.VISIBLE);
		    	if(showDots){
			    	mNextView.setVisibility(View.VISIBLE);
			    	mPreviousView.setVisibility(View.VISIBLE);
		    	}
		    	if(showRAB){
		    		mRAB.setVisibility(View.VISIBLE);
		    	}
		    	if(showLAB){
		    		mLAB.setVisibility(View.VISIBLE);
		    	}
	        }
    	}
    }
    private void dismissPreviews(){
    	dismissPreview(mNextView);
    	dismissPreview(mPreviousView);
    	dismissPreview(mHandleView);
    }
    private void dismissPreview(final View v) {
        //final PopupWindow window = (PopupWindow) v.getTag();
    	final PreviewsPopupWindow window = (PreviewsPopupWindow) v.getTag();
        if (window != null) {
            //fullScreen(!hideStatusBar);
            hideDesktop(false);
            window.setOnDismissListener(new PreviewsPopupWindow.OnDismissListener() {
                public void onDismiss() {
                    ViewGroup group = (ViewGroup) v.getTag(R.id.workspace);
                    int count = group.getChildCount();
                    for (int i = 0; i < count; i++) {
                        ((ImageView) group.getChildAt(i)).setImageDrawable(null);
                    }
                    ArrayList<Bitmap> bitmaps = (ArrayList<Bitmap>) v.getTag(R.id.icon);
                    for (Bitmap bitmap : bitmaps) bitmap.recycle();

                    v.setTag(R.id.workspace, null);
                    v.setTag(R.id.icon, null);
                    window.setOnDismissListener(null);
                }
            });
            window.dismiss();
            showingPreviews=false;
            mWorkspace.unlock();
            mWorkspace.invalidate();
            mDesktopLocked=false;
        }
        v.setTag(null);
    }

    private void showPreviousPreview(View anchor) {
        int current = mWorkspace.getCurrentScreen();
        if(newPreviews){
	        if (current <= 0) return;
	        showPreviews(anchor, 0, mWorkspace.getCurrentScreen());
        }else{
        	showPreviews(anchor, 0, mWorkspace.getChildCount());
        }
    }

    private void showNextPreview(View anchor) {
        int current = mWorkspace.getCurrentScreen();
        if(newPreviews){
	        if (current >= mWorkspace.getChildCount() - 1) return;
	        showPreviews(anchor, mWorkspace.getCurrentScreen()+1, mWorkspace.getChildCount());
        }else{
        	showPreviews(anchor, 0, mWorkspace.getChildCount());
        }
    }

    private void showPreviews(final View anchor, int start, int end) {
        //check first if it's already open
        final PreviewsPopupWindow window = (PreviewsPopupWindow) anchor.getTag();
        if (window != null) return;
        showingPreviews=true;
    	Resources resources = getResources();

        Workspace workspace = mWorkspace;
        CellLayout cell = ((CellLayout) workspace.getChildAt(start));
        float max;
        ViewGroup preview;
        if(newPreviews){
        	max = 3;
            preview= new PreviewsHolder(this);
        }else{
        	max = workspace.getChildCount();
            preview = new LinearLayout(this);
        }
        
        Rect r = new Rect();
        resources.getDrawable(R.drawable.preview_background).getPadding(r);
        int extraW = (int) ((r.left + r.right) * max);
        int extraH = r.top + r.bottom;

        int aW = cell.getWidth() - extraW;
        float w = aW / max;

        int width = cell.getWidth();
        int height = cell.getHeight();
        int x = cell.getLeftPadding();
        int y = cell.getTopPadding();
        //width -= (x + cell.getRightPadding());
        //height -= (y + cell.getBottomPadding());

        float scale = w / width;

        int count = end - start;

        final float sWidth = width * scale;
        float sHeight = height * scale;


        PreviewTouchHandler handler = new PreviewTouchHandler(anchor);
        ArrayList<Bitmap> bitmaps = new ArrayList<Bitmap>(count);

        for (int i = start; i < end; i++) {
            ImageView image = new ImageView(this);
            cell = (CellLayout) workspace.getChildAt(i);

            Bitmap bitmap = Bitmap.createBitmap((int) sWidth, (int) sHeight,
                    Bitmap.Config.ARGB_8888);
            
            Canvas c = new Canvas(bitmap);
            c.scale(scale, scale);
            c.translate(-cell.getLeftPadding(), -cell.getTopPadding());
            cell.dispatchDraw(c);

            image.setBackgroundDrawable(resources.getDrawable(R.drawable.preview_background));
            image.setImageBitmap(bitmap);
            image.setTag(i);
            image.setOnClickListener(handler);
            image.setOnFocusChangeListener(handler);
            image.setFocusable(true);
            if (i == mWorkspace.getCurrentScreen()) image.requestFocus();

            preview.addView(image,
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);

            bitmaps.add(bitmap);            
        }
       
        PreviewsPopupWindow p = new PreviewsPopupWindow(this);
        p.setContentView(preview);
        if(newPreviews){
	        p.setWidth(width);
	        p.setHeight(height);
	        p.setAnimationStyle(R.style.AnimationPreview);
        }else{
        	p.setWidth((int) (sWidth * count + extraW));
        	p.setHeight((int) (sHeight + extraH));
            p.setAnimationStyle(R.style.AnimationPreview);
        }
        p.setOutsideTouchable(true);
        p.setFocusable(true);
        p.setBackgroundDrawable(new ColorDrawable(0));
        if(newPreviews){
        	p.showAtLocation(anchor, Gravity.BOTTOM, 0, 0);
        }else{
        	p.showAsDropDown(anchor, 0, 0);
        }
        p.setOnDismissListener(new PreviewsPopupWindow.OnDismissListener() {
            public void onDismiss() {
                dismissPreview(anchor);
            }
        });

        anchor.setTag(p);
        anchor.setTag(R.id.workspace, preview);
        anchor.setTag(R.id.icon, bitmaps);
        if(fullScreenPreviews){
	        hideDesktop(true);
	        mWorkspace.lock();
	        mDesktopLocked=true;
	        mWorkspace.invalidate();
        }
    }

    class PreviewTouchHandler implements View.OnClickListener, Runnable, View.OnFocusChangeListener {
        private final View mAnchor;

        public PreviewTouchHandler(View anchor) {
            mAnchor = anchor;
        }

        public void onClick(View v) {
            mWorkspace.snapToScreen((Integer) v.getTag());
            v.post(this);
        }

        public void run() {
            dismissPreview(mAnchor);            
        }

        public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus) {
                mWorkspace.snapToScreen((Integer) v.getTag());
            }
        }
    }
    boolean isAllAppsVisible() {
        //return mAllAppsGrid.isOpaque();
    	return allAppsOpen;
    }

    boolean isAllAppsOpaque() {
        //return true;
    	return mAllAppsGrid.isOpaque() && !allAppsAnimating;
    }
    
    private void showCustomConfig(){
    	Intent launchPreferencesIntent = new Intent().setClass(this, MyLauncherSettings.class);
        startActivityForResult(launchPreferencesIntent,MENU_ALMOSTNEXUS);    	   	
    }
    protected boolean isDockBarOpen(){
    	return mDockBar.isOpen();
    }
    protected int getTrashPadding(){
		return mDockBar.getSize();
    }
//EOF ADW
    static LauncherModel getModel() {
        return sModel;
    }

    void closeAllApplications() {
    	closeAllApps(true);
    }

    View getDrawerHandle() {
        return mHandleView;
    }

    boolean isDrawerDown() {
    	return !allAppsOpen;
        //return !mDrawer.isMoving() && !mDrawer.isOpened();
    }

    boolean isDrawerUp() {
    	return allAppsOpen;
        //return mDrawer.isOpened() && !mDrawer.isMoving();
    }

    boolean isDrawerMoving() {
    	return false;
        //return mDrawer.isMoving();
    }

    Workspace getWorkspace() {
        return mWorkspace;
    }

    View getApplicationsGrid() {
        return mAllAppsGrid;
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_CREATE_SHORTCUT:
                return new CreateShortcut().createDialog();
            case DIALOG_RENAME_FOLDER:
                return new RenameFolder().createDialog();
        }

        return super.onCreateDialog(id);
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        switch (id) {
            case DIALOG_CREATE_SHORTCUT:
                break;
            case DIALOG_RENAME_FOLDER:
                if (mFolderInfo != null) {
                    EditText input = (EditText) dialog.findViewById(R.id.folder_name);
                    final CharSequence text = mFolderInfo.title;
                    input.setText(text);
                    input.setSelection(0, text.length());
                }
                break;
        }
    }

    void showRenameDialog(FolderInfo info) {
        mFolderInfo = info;
        mWaitingForResult = true;
        showDialog(DIALOG_RENAME_FOLDER);
    }

    private void showAddDialog(CellLayout.CellInfo cellInfo) {
        mAddItemCellInfo = cellInfo;
        mWaitingForResult = true;
        showDialog(DIALOG_CREATE_SHORTCUT);
    }

    private void pickShortcut(int requestCode, int title) {
        Bundle bundle = new Bundle();

        ArrayList<String> shortcutNames = new ArrayList<String>();
        shortcutNames.add(getString(R.string.group_applications));
        bundle.putStringArrayList(Intent.EXTRA_SHORTCUT_NAME, shortcutNames);

        ArrayList<ShortcutIconResource> shortcutIcons = new ArrayList<ShortcutIconResource>();
        shortcutIcons.add(ShortcutIconResource.fromContext(Launcher.this,
                        R.drawable.ic_launcher_application));
        bundle.putParcelableArrayList(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, shortcutIcons);

        Intent pickIntent = new Intent(Intent.ACTION_PICK_ACTIVITY);
        pickIntent.putExtra(Intent.EXTRA_INTENT, new Intent(Intent.ACTION_CREATE_SHORTCUT));
        pickIntent.putExtra(Intent.EXTRA_TITLE, getText(title));
        pickIntent.putExtras(bundle);

        startActivityForResult(pickIntent, requestCode);
    }

    private class RenameFolder {
        private EditText mInput;

        Dialog createDialog() {
            mWaitingForResult = true;
            final View layout = View.inflate(Launcher.this, R.layout.rename_folder, null);
            mInput = (EditText) layout.findViewById(R.id.folder_name);

            AlertDialog.Builder builder = new AlertDialog.Builder(Launcher.this);
            builder.setIcon(0);
            builder.setTitle(getString(R.string.rename_folder_title));
            builder.setCancelable(true);
            builder.setOnCancelListener(new Dialog.OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    cleanup();
                }
            });
            builder.setNegativeButton(getString(R.string.cancel_action),
                new Dialog.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        cleanup();
                    }
                }
            );
            builder.setPositiveButton(getString(R.string.rename_action),
                new Dialog.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        changeFolderName();
                    }
                }
            );
            builder.setView(layout);

            final AlertDialog dialog = builder.create();
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                public void onShow(DialogInterface dialog) {
                    mWorkspace.lock();
                }
            });

            return dialog;
        }

        private void changeFolderName() {
            final String name = mInput.getText().toString();
            if (!TextUtils.isEmpty(name)) {
                // Make sure we have the right folder info
                mFolderInfo = sModel.findFolderById(mFolderInfo.id);
                mFolderInfo.title = name;
                LauncherModel.updateItemInDatabase(Launcher.this, mFolderInfo);

                if (mDesktopLocked) {
                    //BY ADW
                	//mDrawer.lock();
                    sModel.loadUserItems(false, Launcher.this, false, false);
                } else {
                    final FolderIcon folderIcon = (FolderIcon)
                            mWorkspace.getViewForTag(mFolderInfo);
                    if (folderIcon != null) {
                        folderIcon.setText(name);
                        getWorkspace().requestLayout();
                    } else {
                        mDesktopLocked = true;
                        //BY ADW
                        //mDrawer.lock();
                        sModel.loadUserItems(false, Launcher.this, false, false);
                    }
                }
            }
            cleanup();
        }

        private void cleanup() {
            mWorkspace.unlock();
            dismissDialog(DIALOG_RENAME_FOLDER);
            mWaitingForResult = false;
            mFolderInfo = null;
        }
    }

    /**
     * Displays the shortcut creation dialog and launches, if necessary, the
     * appropriate activity.
     */
    private class CreateShortcut implements DialogInterface.OnClickListener,
            DialogInterface.OnCancelListener, DialogInterface.OnDismissListener,
            DialogInterface.OnShowListener {

        private AddAdapter mAdapter;

        Dialog createDialog() {
            mWaitingForResult = true;

            mAdapter = new AddAdapter(Launcher.this);

            final AlertDialog.Builder builder = new AlertDialog.Builder(Launcher.this);
            builder.setTitle(getString(R.string.menu_item_add_item));
            builder.setAdapter(mAdapter, this);

            builder.setInverseBackgroundForced(true);

            AlertDialog dialog = builder.create();
            dialog.setOnCancelListener(this);
            dialog.setOnDismissListener(this);
            dialog.setOnShowListener(this);

            return dialog;
        }

        public void onCancel(DialogInterface dialog) {
            mWaitingForResult = false;
            cleanup();
        }

        public void onDismiss(DialogInterface dialog) {
            mWorkspace.unlock();
        }

        private void cleanup() {
            mWorkspace.unlock();
            dismissDialog(DIALOG_CREATE_SHORTCUT);
        }

        /**
         * Handle the action clicked in the "Add to home" dialog.
         */
        public void onClick(DialogInterface dialog, int which) {
            Resources res = getResources();
            cleanup();

            switch (which) {
                case AddAdapter.ITEM_SHORTCUT: {
                    // Insert extra item to handle picking application
                    pickShortcut(REQUEST_PICK_SHORTCUT, R.string.title_select_shortcut);
                    break;
                }

                case AddAdapter.ITEM_APPWIDGET: {
                    int appWidgetId = Launcher.this.mAppWidgetHost.allocateAppWidgetId();

                    Intent pickIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
                    pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                    // add the search widget
                    ArrayList<AppWidgetProviderInfo> customInfo =
                            new ArrayList<AppWidgetProviderInfo>();
                    AppWidgetProviderInfo info = new AppWidgetProviderInfo();
                    info.provider = new ComponentName(getPackageName(), "XXX.YYY");
                    info.label = getString(R.string.group_search);
                    info.icon = R.drawable.ic_search_widget;
                    customInfo.add(info);
                    pickIntent.putParcelableArrayListExtra(
                            AppWidgetManager.EXTRA_CUSTOM_INFO, customInfo);
                    ArrayList<Bundle> customExtras = new ArrayList<Bundle>();
                    Bundle b = new Bundle();
                    b.putString(EXTRA_CUSTOM_WIDGET, SEARCH_WIDGET);
                    customExtras.add(b);
                    pickIntent.putParcelableArrayListExtra(
                            AppWidgetManager.EXTRA_CUSTOM_EXTRAS, customExtras);
                    // start the pick activity
                    startActivityForResult(pickIntent, REQUEST_PICK_APPWIDGET);
                    break;
                }

                case AddAdapter.ITEM_LIVE_FOLDER: {
                    // Insert extra item to handle inserting folder
                    Bundle bundle = new Bundle();

                    ArrayList<String> shortcutNames = new ArrayList<String>();
                    shortcutNames.add(res.getString(R.string.group_folder));
                    bundle.putStringArrayList(Intent.EXTRA_SHORTCUT_NAME, shortcutNames);

                    ArrayList<ShortcutIconResource> shortcutIcons =
                            new ArrayList<ShortcutIconResource>();
                    shortcutIcons.add(ShortcutIconResource.fromContext(Launcher.this,
                            R.drawable.ic_launcher_folder));
                    bundle.putParcelableArrayList(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, shortcutIcons);

                    Intent pickIntent = new Intent(Intent.ACTION_PICK_ACTIVITY);
                    pickIntent.putExtra(Intent.EXTRA_INTENT,
                            new Intent(LiveFolders.ACTION_CREATE_LIVE_FOLDER));
                    pickIntent.putExtra(Intent.EXTRA_TITLE,
                            getText(R.string.title_select_live_folder));
                    pickIntent.putExtras(bundle);

                    startActivityForResult(pickIntent, REQUEST_PICK_LIVE_FOLDER);
                    break;
                }

                case AddAdapter.ITEM_WALLPAPER: {
                    startWallpaper();
                    break;
                }
            }
        }

        public void onShow(DialogInterface dialog) {
            mWorkspace.lock();
        }
    }

    /**
     * Receives notifications when applications are added/removed.
     */
    private class ApplicationsIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final String packageName = intent.getData().getSchemeSpecificPart();
            final boolean replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);

            if (LauncherModel.DEBUG_LOADERS) {
                d(LauncherModel.LOG_TAG, "application intent received: " + action +
                        ", replacing=" + replacing);
                d(LauncherModel.LOG_TAG, "  --> " + intent.getData());
            }

            if (!Intent.ACTION_PACKAGE_CHANGED.equals(action)) {
                if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                    if (!replacing) {
                        removeShortcutsForPackage(packageName);
                        if (LauncherModel.DEBUG_LOADERS) {
                            d(LauncherModel.LOG_TAG, "  --> remove package");
                        }
                        sModel.removePackage(Launcher.this, packageName);
                    }
                    // else, we are replacing the package, so a PACKAGE_ADDED will be sent
                    // later, we will update the package at this time
                } else {
                    if (!replacing) {
                        if (LauncherModel.DEBUG_LOADERS) {
                            d(LauncherModel.LOG_TAG, "  --> add package");
                        }
                        sModel.addPackage(Launcher.this, packageName);
                    } else {
                        if (LauncherModel.DEBUG_LOADERS) {
                            d(LauncherModel.LOG_TAG, "  --> update package " + packageName);
                        }
                        sModel.updatePackage(Launcher.this, packageName);
                        updateShortcutsForPackage(packageName);
                    }
                }
                removeDialog(DIALOG_CREATE_SHORTCUT);
            } else {
                if (LauncherModel.DEBUG_LOADERS) {
                    d(LauncherModel.LOG_TAG, "  --> sync package " + packageName);
                }
                sModel.syncPackage(Launcher.this, packageName);
            }
        }
    }

    /**
     * Receives notifications whenever the user favorites have changed.
     */
    private class FavoritesChangeObserver extends ContentObserver {
        public FavoritesChangeObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            onFavoritesChanged();
        }
    }

    /**
     * Receives intents from other applications to change the wallpaper.
     */
    private static class WallpaperIntentReceiver extends BroadcastReceiver {
        private final Application mApplication;
        private WeakReference<Launcher> mLauncher;

        WallpaperIntentReceiver(Application application, Launcher launcher) {
            mApplication = application;
            setLauncher(launcher);
        }

        void setLauncher(Launcher launcher) {
            mLauncher = new WeakReference<Launcher>(launcher);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            // Load the wallpaper from the ApplicationContext and store it locally
            // until the Launcher Activity is ready to use it
            final Drawable drawable = mApplication.getWallpaper();
            if (drawable instanceof BitmapDrawable) {
                sWallpaper = ((BitmapDrawable) drawable).getBitmap();
            } else {
                throw new IllegalStateException("The wallpaper must be a BitmapDrawable.");
            }

            // If Launcher is alive, notify we have a new wallpaper
            if (mLauncher != null) {
                final Launcher launcher = mLauncher.get();
                if (launcher != null) {
                    launcher.loadWallpaper();
                }
            }
        }
    }

    /**
     * Receives notifications whenever the appwidgets are reset.
     */
    private class AppWidgetResetObserver extends ContentObserver {
        public AppWidgetResetObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            onAppWidgetReset();
        }
    }

    private static class DesktopBinder extends Handler implements MessageQueue.IdleHandler {
        static final int MESSAGE_BIND_ITEMS = 0x1;
        static final int MESSAGE_BIND_APPWIDGETS = 0x2;
        static final int MESSAGE_BIND_DRAWER = 0x3;

        // Number of items to bind in every pass
        static final int ITEMS_COUNT = 6;

        private final ArrayList<ItemInfo> mShortcuts;
        private final LinkedList<LauncherAppWidgetInfo> mAppWidgets;
        private final ApplicationsAdapter mDrawerAdapter;
        private final WeakReference<Launcher> mLauncher;

        public boolean mTerminate = false;

        DesktopBinder(Launcher launcher, ArrayList<ItemInfo> shortcuts,
                ArrayList<LauncherAppWidgetInfo> appWidgets,
                ApplicationsAdapter drawerAdapter) {

            mLauncher = new WeakReference<Launcher>(launcher);
            mShortcuts = shortcuts;
            mDrawerAdapter = drawerAdapter;

            // Sort widgets so active workspace is bound first
            final int currentScreen = launcher.mWorkspace.getCurrentScreen();
            final int size = appWidgets.size();
            mAppWidgets = new LinkedList<LauncherAppWidgetInfo>();

            for (int i = 0; i < size; i++) {
                LauncherAppWidgetInfo appWidgetInfo = appWidgets.get(i);
                if (appWidgetInfo.screen == currentScreen) {
                    mAppWidgets.addFirst(appWidgetInfo);
                } else {
                    mAppWidgets.addLast(appWidgetInfo);
                }
            }

            if (LauncherModel.DEBUG_LOADERS) {
                d(Launcher.LOG_TAG, "------> binding " + shortcuts.size() + " items");
                d(Launcher.LOG_TAG, "------> binding " + appWidgets.size() + " widgets");
            }
        }

        public void startBindingItems() {
            if (LauncherModel.DEBUG_LOADERS) d(Launcher.LOG_TAG, "------> start binding items");
            obtainMessage(MESSAGE_BIND_ITEMS, 0, mShortcuts.size()).sendToTarget();
        }

        public void startBindingDrawer() {
            obtainMessage(MESSAGE_BIND_DRAWER).sendToTarget();
        }

        public void startBindingAppWidgetsWhenIdle() {
            // Ask for notification when message queue becomes idle
            final MessageQueue messageQueue = Looper.myQueue();
            messageQueue.addIdleHandler(this);
        }

        public boolean queueIdle() {
            // Queue is idle, so start binding items
            startBindingAppWidgets();
            return false;
        }

        public void startBindingAppWidgets() {
            obtainMessage(MESSAGE_BIND_APPWIDGETS).sendToTarget();
        }

        @Override
        public void handleMessage(Message msg) {
            Launcher launcher = mLauncher.get();
            if (launcher == null || mTerminate) {
                return;
            }

            switch (msg.what) {
                case MESSAGE_BIND_ITEMS: {
                    launcher.bindItems(this, mShortcuts, msg.arg1, msg.arg2);
                    break;
                }
                case MESSAGE_BIND_DRAWER: {
                    launcher.bindDrawer(this, mDrawerAdapter);
                    break;
                }
                case MESSAGE_BIND_APPWIDGETS: {
                    launcher.bindAppWidgets(this, mAppWidgets);
                    break;
                }
            }
        }
    }

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		// TODO Auto-generated method stub
    	super.onWindowFocusChanged(hasFocus);
		if(mShouldHideStatusbaronFocus && hideStatusBar){
			fullScreen(true);
			mShouldHideStatusbaronFocus=false;
		}
	}
}
